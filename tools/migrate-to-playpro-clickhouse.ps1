<#
.SYNOPSIS
Automates migration from this fork's ClickHouse schema to the official
PlayPro/CoreProtect ClickHouse schema.

.EXAMPLE
powershell -ExecutionPolicy Bypass -File tools\migrate-to-playpro-clickhouse.ps1 `
  -ClickHouseHost ds92143.craft-hosting.ru `
  -Username default `
  -Password "secret" `
  -SourceDatabase kostya `
  -TargetDatabase coreprotect_playpro
#>

[CmdletBinding()]
param(
    [string] $ClickHouseHost = "127.0.0.1",
    [int] $Port = 8123,
    [string] $Username = "default",
    [string] $Password = "",
    [switch] $Tls,
    [string] $SourceDatabase = "kostya",
    [string] $TargetDatabase = "coreprotect_playpro",
    [string] $SourcePrefix = "co_",
    [string] $TargetPrefix = "co_",
    [switch] $SkipSchemaBootstrap,
    [switch] $DryRun,
    [string] $RenderedSqlPath = ""
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$scriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$migrationTemplate = Join-Path $scriptRoot "playpro-clickhouse-migration.sql"

function Write-Step([string] $Message) {
    Write-Host ""
    Write-Host "==> $Message" -ForegroundColor Cyan
}

function Write-Ok([string] $Message) {
    Write-Host "OK  $Message" -ForegroundColor Green
}

function Fail([string] $Message) {
    throw $Message
}

function Assert-Identifier([string] $Value, [string] $Name, [switch] $AllowEmpty) {
    if ($AllowEmpty -and $Value -eq "") {
        return
    }
    if ($Value -notmatch '^[A-Za-z_][A-Za-z0-9_]*$') {
        Fail "$Name must be a simple ClickHouse identifier. Got: '$Value'"
    }
}

function Quote-Identifier([string] $Value) {
    $tick = [char]96
    return "$tick$Value$tick"
}

function Qualified-Table([string] $Database, [string] $Prefix, [string] $Suffix) {
    return "$(Quote-Identifier $Database).$(Quote-Identifier ($Prefix + $Suffix))"
}

function ClickHouse-Url() {
    $scheme = "http"
    if ($Tls) {
        $scheme = "https"
    }
    $query = @(
        "user=$([uri]::EscapeDataString($Username))",
        "wait_end_of_query=1",
        "output_format_pretty_color=0"
    )
    if ($Password -ne "") {
        $query += "password=$([uri]::EscapeDataString($Password))"
    }
    return "${scheme}://${ClickHouseHost}:${Port}/?$($query -join '&')"
}

function Invoke-ClickHouse([string] $Sql, [string] $Label = "query") {
    if ($DryRun) {
        Write-Host "DRY RUN: $Label"
        return ""
    }

    try {
        return Invoke-RestMethod -Method Post -Uri (ClickHouse-Url) -Body $Sql -ContentType "text/plain; charset=utf-8"
    }
    catch {
        $detail = $_.Exception.Message
        if ($_.ErrorDetails -and $_.ErrorDetails.Message) {
            $detail = $_.ErrorDetails.Message
        }
        Fail "ClickHouse failed while executing $Label.`n$detail`nSQL:`n$Sql"
    }
}

function Split-SqlStatements([string] $Sql) {
    $items = New-Object System.Collections.Generic.List[string]
    $start = 0
    $quote = $false
    for ($i = 0; $i -lt $Sql.Length; $i++) {
        $c = $Sql[$i]
        if ($c -eq "'") {
            if ($quote -and ($i + 1) -lt $Sql.Length -and $Sql[$i + 1] -eq "'") {
                $i++
                continue
            }
            $quote = -not $quote
            continue
        }
        if (-not $quote -and $c -eq ";") {
            $statement = $Sql.Substring($start, $i - $start).Trim()
            if ($statement.Length -gt 0) {
                $items.Add($statement)
            }
            $start = $i + 1
        }
    }
    $tail = $Sql.Substring($start).Trim()
    if ($tail.Length -gt 0) {
        $items.Add($tail)
    }
    return $items
}

function Require-ClickHouse-Version() {
    Write-Step "Checking ClickHouse version"
    $version = (Invoke-ClickHouse "SELECT version()" "version check").Trim()
    if ($version -notmatch '^(\d+)\.(\d+)') {
        Fail "Could not parse ClickHouse version: $version"
    }
    $major = [int] $Matches[1]
    $minor = [int] $Matches[2]
    if ($major -lt 25 -or ($major -eq 25 -and $minor -lt 6)) {
        Fail "Official PlayPro/CoreProtect ClickHouse requires ClickHouse 25.6+. Server returned $version."
    }
    Write-Ok "ClickHouse $version"
}

function Require-Source-Tables() {
    Write-Step "Checking source fork tables in $SourceDatabase"
    $expected = @(
        "art_map", "block", "chat", "command", "container", "entity_container",
        "entity_interaction", "item", "entity", "entity_spawn", "entity_map",
        "material_map", "blockdata_map", "session", "sign", "skull", "user",
        "username_log", "world"
    )
    $quoted = ($expected | ForEach-Object { "'$($SourcePrefix + $_)'" }) -join ","
    $sql = "SELECT name FROM system.tables WHERE database='$SourceDatabase' AND name IN ($quoted) ORDER BY name FORMAT TabSeparated"
    $foundText = Invoke-ClickHouse $sql "source table check"
    $found = @($foundText -split "`n" | Where-Object { $_.Trim() -ne "" } | ForEach-Object { $_.Trim() })
    $missing = @()
    foreach ($suffix in $expected) {
        $name = $SourcePrefix + $suffix
        if ($found -notcontains $name) {
            $missing += $name
        }
    }
    if ($missing.Count -gt 0) {
        Fail "Source database is missing required tables: $($missing -join ', ')"
    }
    Write-Ok "Found $($expected.Count) source tables"
}

function New-PlayProSchemaSql() {
    $storage = Qualified-Table $TargetDatabase $TargetPrefix "storage_metadata"
    $writer = Qualified-Table $TargetDatabase $TargetPrefix "writer_registration"
    $highWater = Qualified-Table $TargetDatabase $TargetPrefix "retention_high_water"
    $events = Qualified-Table $TargetDatabase $TargetPrefix "event_data"
    $db = Quote-Identifier $TargetDatabase

    return @"
CREATE DATABASE IF NOT EXISTS $db ENGINE = Atomic;

CREATE TABLE IF NOT EXISTS $storage (
    dataset_id UUID CODEC(ZSTD(3)),
    producer_id UUID CODEC(ZSTD(3)),
    schema_version UInt32 CODEC(Delta, ZSTD(3)),
    created_at DateTime64(3, 'UTC') CODEC(Delta, ZSTD(3))
) ENGINE = MergeTree
ORDER BY tuple()
SETTINGS fsync_after_insert=1,fsync_part_directory=1;

CREATE TABLE IF NOT EXISTS $writer (
    dataset_id UUID,
    producer_id UUID,
    writer_id UUID,
    registration_order UInt64 DEFAULT generateSnowflakeID(),
    registered_at DateTime64(3, 'UTC')
) ENGINE = MergeTree
ORDER BY (registration_order,writer_id)
SETTINGS fsync_after_insert=1,fsync_part_directory=1;

CREATE TABLE IF NOT EXISTS $highWater (
    dataset_id UUID CODEC(ZSTD(3)),
    producer_id UUID CODEC(ZSTD(3)),
    producer_sequence UInt64 CODEC(Delta, ZSTD(3)),
    family LowCardinality(String) CODEC(ZSTD(3)),
    rowid UInt64 CODEC(Delta, ZSTD(3)),
    recorded_at DateTime64(3, 'UTC') CODEC(Delta, ZSTD(3))
) ENGINE = MergeTree
ORDER BY (dataset_id,family,producer_sequence,rowid)
SETTINGS fsync_after_insert=1,fsync_part_directory=1,non_replicated_deduplication_window=1000;

CREATE TABLE IF NOT EXISTS $events (
    dataset_id UUID CODEC(ZSTD(3)),
    producer_id UUID CODEC(ZSTD(3)),
    producer_sequence UInt64 CODEC(Delta, ZSTD(3)),
    batch_id UUID CODEC(ZSTD(3)),
    batch_ordinal UInt32 CODEC(Delta, ZSTD(3)),
    family LowCardinality(String) CODEC(ZSTD(3)),
    rowid UInt64 CODEC(Delta, ZSTD(3)),
    time UInt32 CODEC(Delta, ZSTD(3)),
    user_id Nullable(UInt32) CODEC(ZSTD(3)),
    wid UInt32 CODEC(Delta, ZSTD(3)),
    x Int32 CODEC(Delta, ZSTD(3)),
    y Nullable(Int32) CODEC(ZSTD(3)),
    z Int32 CODEC(Delta, ZSTD(3)),
    type Nullable(UInt32) CODEC(ZSTD(3)),
    data Nullable(Int64) CODEC(ZSTD(3)),
    payload Nullable(String) CODEC(ZSTD(3)),
    meta Nullable(String) CODEC(ZSTD(3)),
    blockdata Nullable(String) CODEC(ZSTD(3)),
    action Nullable(UInt8) CODEC(ZSTD(3)),
    rolled_back Nullable(UInt8) CODEC(ZSTD(3)),
    amount Nullable(Int32) CODEC(ZSTD(3)),
    metadata Nullable(String) CODEC(ZSTD(3)),
    entity_spawn_rowid Nullable(UInt64) CODEC(ZSTD(3)),
    id Nullable(UInt32) CODEC(ZSTD(3)),
    name Nullable(String) CODEC(ZSTD(3)),
    text Nullable(String) CODEC(ZSTD(3)),
    message Nullable(String) CODEC(ZSTD(3)),
    status Nullable(UInt8) CODEC(ZSTD(3)),
    database_lock_time Nullable(UInt32) CODEC(ZSTD(3)),
    version Nullable(String) CODEC(ZSTD(3)),
    block_rowid Nullable(UInt64) CODEC(ZSTD(3)),
    kill_rowid Nullable(UInt64) CODEC(ZSTD(3)),
    block_rowid_present Nullable(UInt8) CODEC(ZSTD(3)),
    kill_rowid_present Nullable(UInt8) CODEC(ZSTD(3)),
    uuid Nullable(String) CODEC(ZSTD(3)),
    user_name Nullable(String) CODEC(ZSTD(3)),
    current_wid Nullable(UInt32) CODEC(ZSTD(3)),
    origin_x Nullable(Float64) CODEC(ZSTD(3)),
    origin_y Nullable(Float64) CODEC(ZSTD(3)),
    origin_z Nullable(Float64) CODEC(ZSTD(3)),
    current_x Nullable(Float64) CODEC(ZSTD(3)),
    current_y Nullable(Float64) CODEC(ZSTD(3)),
    current_z Nullable(Float64) CODEC(ZSTD(3)),
    yaw Nullable(Float32) CODEC(ZSTD(3)),
    pitch Nullable(Float32) CODEC(ZSTD(3)),
    entity_data Nullable(String) CODEC(ZSTD(3)),
    entity_data_present Nullable(UInt8) CODEC(ZSTD(3)),
    removed Nullable(UInt8) CODEC(ZSTD(3)),
    color Nullable(UInt32) CODEC(ZSTD(3)),
    color_secondary Nullable(UInt32) CODEC(ZSTD(3)),
    sign_data Nullable(UInt8) CODEC(ZSTD(3)),
    waxed Nullable(UInt8) CODEC(ZSTD(3)),
    face Nullable(UInt8) CODEC(ZSTD(3)),
    line_1 Nullable(String) CODEC(ZSTD(3)),
    line_2 Nullable(String) CODEC(ZSTD(3)),
    line_3 Nullable(String) CODEC(ZSTD(3)),
    line_4 Nullable(String) CODEC(ZSTD(3)),
    line_5 Nullable(String) CODEC(ZSTD(3)),
    line_6 Nullable(String) CODEC(ZSTD(3)),
    line_7 Nullable(String) CODEC(ZSTD(3)),
    line_8 Nullable(String) CODEC(ZSTD(3)),
    INDEX producer_sequence_idx producer_sequence TYPE minmax GRANULARITY 1,
    INDEX rowid_idx rowid TYPE bloom_filter(0.01) GRANULARITY 1,
    INDEX entity_uuid_idx uuid TYPE bloom_filter(0.01) GRANULARITY 1,
    INDEX entity_kill_rowid_idx kill_rowid TYPE bloom_filter(0.01) GRANULARITY 1
) ENGINE = CoalescingMergeTree
PARTITION BY if(family IN ('block','chat','command','container','entity_container','entity_interaction','item','entity','session','sign','skull'),toYYYYMM(toDateTime(time,'UTC')),0)
ORDER BY (dataset_id,family,wid,x,z,if(family IN ('database_lock','user','version'),0,time),rowid)
SETTINGS fsync_after_insert=1,fsync_part_directory=1,non_replicated_deduplication_window=1000;

INSERT INTO $storage (dataset_id,producer_id,schema_version,created_at)
SELECT generateUUIDv4(), generateUUIDv4(), 1, now64(3, 'UTC')
WHERE (SELECT count() FROM $storage) = 0;
"@
}

function Bootstrap-TargetSchema() {
    if ($SkipSchemaBootstrap) {
        Write-Step "Skipping target schema bootstrap"
        return
    }

    Write-Step "Creating official PlayPro ClickHouse physical schema in $TargetDatabase"
    $schemaSql = New-PlayProSchemaSql
    foreach ($statement in Split-SqlStatements $schemaSql) {
        Invoke-ClickHouse $statement "schema bootstrap" | Out-Null
    }
    Write-Ok "Target schema exists"
}

function Require-TargetDatabaseEngine() {
    Write-Step "Checking target database engine"
    $sql = "SELECT engine,toString(uuid) FROM system.databases WHERE name='$TargetDatabase' FORMAT TabSeparated"
    $result = (Invoke-ClickHouse $sql "target database engine check").Trim()
    if ($result -eq "") {
        Fail "Target database $TargetDatabase does not exist."
    }
    $parts = $result -split "`t"
    if ($parts.Count -lt 2) {
        Fail "Could not read target database engine/UUID: $result"
    }
    $engine = $parts[0]
    $uuid = $parts[1]
    if ($uuid -eq "00000000-0000-0000-0000-000000000000") {
        Fail "Target database $TargetDatabase uses engine $engine without persistent UUID-backed table identities. Create it as ENGINE = Atomic."
    }
    Write-Ok "Target database engine: $engine, uuid: $uuid"
}

function Require-TargetReady() {
    Write-Step "Checking target schema and safety"
    $eventTable = $TargetPrefix + "event_data"
    $metadataTable = $TargetPrefix + "storage_metadata"
    $sql = "SELECT count() FROM system.tables WHERE database='$TargetDatabase' AND name IN ('$eventTable','$metadataTable') FORMAT TabSeparated"
    $tableCount = [int] ((Invoke-ClickHouse $sql "target schema check").Trim())
    if ($tableCount -ne 2) {
        Fail "Target schema is not ready. Missing $eventTable or $metadataTable in $TargetDatabase."
    }

    $identityCount = [int] ((Invoke-ClickHouse "SELECT count() FROM $(Qualified-Table $TargetDatabase $TargetPrefix "storage_metadata") FORMAT TabSeparated" "identity check").Trim())
    if ($identityCount -ne 1) {
        Fail "Target storage metadata must contain exactly one identity row. Found $identityCount."
    }

    $eventCount = [int64] ((Invoke-ClickHouse "SELECT count() FROM $(Qualified-Table $TargetDatabase $TargetPrefix "event_data") FORMAT TabSeparated" "target emptiness check").Trim())
    if ($eventCount -gt 0) {
        Fail "Target event table already has $eventCount rows. Use a fresh empty target database to avoid duplicate migration rows."
    }
    Write-Ok "Target is ready; current target event rows: $eventCount"
}

function Render-MigrationSql() {
    if (-not (Test-Path $migrationTemplate)) {
        Fail "Migration template not found: $migrationTemplate"
    }
    $sql = Get-Content $migrationTemplate -Raw
    $sql = $sql.Replace("kostya.co_", "$SourceDatabase.$SourcePrefix")
    $sql = $sql.Replace("coreprotect_playpro.co_", "$TargetDatabase.$TargetPrefix")
    $sql = $sql.Replace("CREATE DATABASE IF NOT EXISTS coreprotect_playpro ENGINE = Atomic;", "CREATE DATABASE IF NOT EXISTS $(Quote-Identifier $TargetDatabase) ENGINE = Atomic;")
    return $sql
}

function Run-Migration([string] $Sql) {
    Write-Step "Running migration"
    $index = 0
    foreach ($statement in Split-SqlStatements $Sql) {
        $index++
        $label = "migration statement $index"
        if ($statement -match '(?m)^INSERT INTO\s+([^\s]+)') {
            $label = "insert into $($Matches[1])"
        }
        elseif ($statement -match '(?m)^SELECT') {
            $label = "verification query $index"
        }
        $result = Invoke-ClickHouse $statement $label
        if ($result -and $result.Trim() -ne "") {
            Write-Host $result
        }
    }
    Write-Ok "Migration SQL completed"
}

function Main() {
    Assert-Identifier $SourceDatabase "SourceDatabase"
    Assert-Identifier $TargetDatabase "TargetDatabase"
    Assert-Identifier $SourcePrefix "SourcePrefix" -AllowEmpty
    Assert-Identifier $TargetPrefix "TargetPrefix" -AllowEmpty

    if ($SourceDatabase -eq $TargetDatabase -and $SourcePrefix -eq $TargetPrefix) {
        Fail "Source and target namespace are the same. This would conflict with the fork tables."
    }

    Write-Step "Rendering migration SQL"
    $migrationSql = Render-MigrationSql
    if ($RenderedSqlPath -ne "") {
        Set-Content -Path $RenderedSqlPath -Value $migrationSql
        Write-Ok "Rendered SQL written to $RenderedSqlPath"
    }

    if ($DryRun) {
        Write-Ok "Dry run finished. No ClickHouse queries were executed."
        return
    }

    Require-ClickHouse-Version
    Require-Source-Tables
    Bootstrap-TargetSchema
    Require-TargetDatabaseEngine
    Require-TargetReady
    Run-Migration $migrationSql

    Write-Step "Done"
    Write-Host "Now install the official PlayPro/CoreProtect jar and set database-type: clickhouse, clickhouse-database: $TargetDatabase, table-prefix: $TargetPrefix."
    Write-Host "Keep the old $SourceDatabase database until staging tests pass."
}

Main
