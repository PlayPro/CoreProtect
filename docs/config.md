# Configuration

The CoreProtect configuration file can be found within the CoreProtect folder, at `config.yml`.

## Database Storage

New installations use embedded DuckDB by default. Upgrading an existing SQLite or MySQL installation does not switch it to DuckDB: if `database-type` is absent, CoreProtect derives it from the existing legacy `use-mysql` setting and automatically writes the matching `sqlite` or `mysql` value. No manual change is required. Once present, `database-type` takes precedence over `use-mysql`, and changes take effect after `/co reload` or a restart.

| Value | Storage | Requirements |
| --- | --- | --- |
| `duckdb` | Embedded columnar database; the default for new installations | CoreProtect 25.0+ |
| `clickhouse` | External columnar database | CoreProtect 25.0+ and a reachable ClickHouse 25.6+ server |
| `sqlite` | Embedded legacy relational database | None beyond CoreProtect |
| `mysql` | External legacy relational database | A reachable MySQL server |

The server downloads the DuckDB JDBC driver automatically as a plugin library, while the ClickHouse JDBC driver is packaged inside CoreProtect; neither requires manual driver installation. DuckDB includes its database engine, while ClickHouse requires a separate server.

Changing `database-type` selects a separate dataset; it does not copy existing data. CoreProtect 23.0+ Patreon builds can migrate between SQLite and MySQL; any migration involving DuckDB or ClickHouse requires CoreProtect 25.0+. The target namespace must contain no CoreProtect data; DuckDB requires a new target file, and `database-lock` must remain enabled. See the [database migration guide](/database-migration/) for preparation and switchover instructions.

### DuckDB

The default settings are suitable for most servers:

```yaml
database-type: duckdb
duckdb-memory-limit: 512MB
duckdb-threads: 3
duckdb-max-temp-directory-size: 10GB
```

DuckDB stores its data in `plugins/CoreProtect/database.duckdb`; CoreProtect manages it directly, so no DuckDB service is needed. `duckdb-memory-limit` controls the buffer manager, although total native memory can be higher, and `duckdb-threads` limits query threads. `duckdb-max-temp-directory-size` caps spill data without preallocating space or limiting the database file itself. Queries that exceed both memory and available spill space can fail.

On a pristine installation where the CoreProtect data folder contains no existing files, CoreProtect verifies that DuckDB's native library can run before creating the database file. If the operating system, architecture, or native runtime is incompatible, CoreProtect records `database-type: sqlite` in the generated `config.yml`, logs a warning, and starts with SQLite instead. Existing or preconfigured installations and explicitly configured DuckDB databases never fall back automatically; an initialization error stops database startup so CoreProtect cannot silently open a separate empty history.

### ClickHouse

Configure the ClickHouse HTTP endpoint and credentials before selecting it:

```yaml
database-type: clickhouse
table-prefix: co_
clickhouse-host: 127.0.0.1
clickhouse-port: 8123
clickhouse-database: default
clickhouse-username: default
clickhouse-password: ""
clickhouse-tls: false
```

Create the configured database first with a database engine that provides persistent UUID-backed table identities. `Atomic` is the normal choice for self-hosted ClickHouse and is the default there; for example, `CREATE DATABASE coreprotect ENGINE = Atomic`. CoreProtect checks this capability before creating any tables, then creates and validates its prefixed tables and views. The account needs permission to read `system.databases` and `system.tables`, create and read/write the CoreProtect objects, run mutations and partition drops, and drop or truncate purge-target tables. Server-wide database-creation permission is not required; `#optimize` additionally requires table optimization permission.

Multiple active CoreProtect installations may share one ClickHouse database and prefix when `database-lock` is disabled. Configure every installation to use the same CoreProtect version, direct endpoint to the same physical ClickHouse server, and prefix, with a separate CoreProtect data directory; do not use a load balancer across independent nodes. Keep their system clocks synchronized; events recorded by different installations in the same second have an unspecified relative order. A shared prefix is one logical namespace: use globally unique world names unless same-named worlds are intentionally the same world, because their coordinate history is combined. Player attribution assumes that a username is not reassigned to a different account within that namespace and that a UUID-bearing login precedes UUID-less activity under a changed name. UUID-bearing observations preserve an account ID through ordinary username changes, but simultaneous username changes and username reuse are outside the base shared-writer contract. A server may continue displaying a peer's previously cached username until it observes that player locally or CoreProtect reloads. ClickHouse has no cross-server maintenance fence without Keeper, so every purge is rejected while `database-lock` is disabled. To purge a shared namespace, stop every installation sharing it, enable `database-lock` on the one installation that will run the purge, and restart or reload that installation before purging. Follow the same stop-all rule before a database migration; the migration command updates only the installation that runs it, so update the other configurations before restarting them. Replicated and distributed tables remain unsupported.

## Per-World Configuration

If you'd like to modify the logging settings for a specific world, simply do the following:

1. Copy the config.yml file to the name of the world (e.g. world_nether.yml)
2. In the new file, modify the logging settings as desired.
3. Either restart your server, or type "/co reload" in-game.

Secondary configuration files override the value specified in config.yml. If you leave an option out of a secondary configuration file, then the option specified in config.yml will be used.

#### Examples
* If you'd like to disable all logging for the End, copy the `config.yml` file to `world_the_end.yml` (matching the folder name for the world). Then, simply disable all logging options within the new file.
* If you just want to disable entity death logging in the Nether, but keep all other logging options the same, simply create a file named `world_nether.yml` containing the text "rollback-entities: false".

## Disabling Logging

To disable logging for specific users, blocks or commands, simply do the following:

1. In the CoreProtect plugin directory, create a file named `blacklist.txt`.
2. Enter the names of the users, commands, blocks, or entities you'd like to disable logging for (each entry on a new line).
3. Either restart your server, or type "/co reload" in-game.

The blacklist supports disabling logs for:

- Users, which includes Players and non-player users, such as "#creeper"
- Commands, such as `/help`
- Blocks, such as minecraft:stone. Only `block` actions are affected.
- Entities, such as minecraft:creeper, which will disable logging the death for that entity. *Note: renamed entities will be logged even if blacklisted.*
- Filters can also be specified for a particular user, by using the `@` symbol after the specific item, block, or entity namespaced ID. The format is `id@user`. This will filter all `block`, `kill`, `item` and `container` actions involving that particular block, item or mob, only when caused by the specified player or non-player user. 
- Items and container actions are only affected by filtered blacklist entries, not by generic item or block IDs.

*Please note that you must include the namespace (e.g. minecraft:) for blocks, entities and items.*

An example blacklist.txt file would look like this:

```text
Notch ; User
#tnt ; TNT explosions
/help ; Help command
minecraft:stone ; Stone blocks
minecraft:creeper ; Creeper entity
minecraft:shears@#dispenser ; Shears being dispensed
```


*Please note that to disable logging for blocks, CoreProtect v23+ is required.*
*To disable logging for entities or to use filtering, CoreProtect v24+ is required.*
