# Database Migration

CoreProtect 23.0+ can migrate data between SQLite and MySQL while retaining the source history. CoreProtect 25.0+ adds DuckDB and ClickHouse as migration sources and destinations. Changing `database-type` by itself does not migrate any data.

> **Note:** Database migration is available only in [Patreon builds](https://patreon.com/coreprotect) for Patron supporters.

## Overview

Database migration can be used to move from SQLite or DuckDB to an external database, return to an embedded database for simpler operation, or switch between any of the supported storage engines. The active backend is always the source, and the command argument selects the destination.

## Command Usage

| Command | Parameters | Description |
| --- | --- | --- |
| `/co migrate-db` | `<sqlite|mysql|duckdb|clickhouse>` | Migrate the active database to the selected backend |

The command must be run from the server console, and the destination must differ from the active source.

For example:

```text
co migrate-db duckdb
co migrate-db clickhouse
```

## Before You Start

1. Stop the server and make a normal backup of the source database and `config.yml`.
2. Leave `database-type` set to the source backend and keep `database-lock: true`.
3. Configure the target while the server is stopped, then start the server with the source backend still selected. MySQL and ClickHouse connection settings must be loaded before the migration starts.
4. Use a target namespace that contains no CoreProtect data. Unrelated tables in the same external database are not affected.
5. Verify that the target has enough storage and that its account has permission to create and modify the required tables.
6. Run the migration during a quiet maintenance period, with no purge, rollback, reload, or other database operation in progress.

Prepare the target as follows:

| Target | Preparation |
| --- | --- |
| SQLite | Move or archive an existing `plugins/CoreProtect/database.db` if it contains CoreProtect data. CoreProtect creates the file and schema when needed. |
| DuckDB | `plugins/CoreProtect/database.duckdb` must be a new database file so its row ID sequences can be initialized from the source. |
| MySQL | Configure the `mysql-*` options and create the configured database and account. The selected table-prefix namespace must contain no CoreProtect data. |
| ClickHouse | Use ClickHouse 25.6 or newer, create the configured database with persistent UUID-backed table identities (`Atomic` is the normal self-hosted choice), create the account, configure the `clickhouse-*` options, and use a table-prefix namespace containing no CoreProtect data. |

`table-prefix` is shared by MySQL and ClickHouse. If the source is MySQL or ClickHouse, leave its prefix unchanged; an external target will use that same prefix. If the source is SQLite or DuckDB, you may select the external target prefix before starting the migration session. Embedded SQLite and DuckDB targets always use `co_`.

## During Migration

CoreProtect pauses database persistence, waits for active database work to finish, and copies data in bounded batches. The console shows a throttled copy progress bar with the number of successfully written records, overall percentage, current table, transfer rate, and estimated copy time remaining. It also reports each non-empty table as its copy completes. Verification, activation, and the automatic `config.yml` update follow the copy, so the ETA does not include those final stages.

New events logged while migration is running remain in the in-memory consumer queue. After successful activation, they are written to the target; if migration fails and the source is restored, persistence resumes there instead. Queued events are not stored for recovery after a process restart, and database-backed commands may report that the database is busy. Large migrations can take hours and use significant CPU, memory, disk, and network bandwidth, so keep server activity low and do not stop or restart the server until the command reports success or failure.

Do not run `/co reload`, start a purge or rollback, or otherwise change database configuration while migration is running. CoreProtect normally rejects these conflicting operations, but they should not be used as part of the migration procedure.

## Data Integrity

The migration copies all logical CoreProtect history and reference data, including rollback state and entity tracking. It preserves copied row IDs. MySQL, DuckDB, and ClickHouse targets also preserve allocator high-water marks; SQLite continues after the largest surviving row ID, matching its existing allocation behavior.

CoreProtect compares table statistics and every copied data row before activation. Database-lock metadata is initialized separately; ClickHouse targets also initialize their own current version row. For a ClickHouse source, the current logical version of each row is copied; superseded internal ClickHouse revisions are storage-engine history rather than separate CoreProtect records.

Source history rows are not deleted or replaced. Migration may update operational metadata such as database-lock heartbeats and allocator state.

## After a Successful Migration

1. CoreProtect atomically changes `database-type` in `config.yml` to the target before queued writes resume. If the legacy `use-mysql` option exists, it is updated at the same time (`true` only for MySQL). Comments and unrelated settings are preserved.
2. The running server immediately begins persisting queued and new events to the target. Keep the server in maintenance mode until the restart check below succeeds.
3. Review the completion message and server log, then test `/co status` and a representative lookup.
4. Restart once and verify that CoreProtect reconnects to the same target and can still read old and new records. No manual database selector edit is required.
5. Resume normal activity and test a newly logged change.
6. Keep the source backup until the migrated database has been verified. Archive or remove it only when you are satisfied that the migration is complete.

When either the source or target is ClickHouse, preserve `plugins/CoreProtect/.clickhouse-writer` with the installation so the retained ClickHouse dataset remains recoverable. Do not copy it to another active server or replace it after migration.

## Failure and Recovery

The command does not resume a partial migration. A failed or interrupted attempt can leave rows, schema objects, or allocator state in the target, so clean only the failed target namespace before retrying. For DuckDB, stop the server and remove the failed target file before starting over. Never clean or replace the source database as part of retrying.

If target activation or the atomic `config.yml` update fails after verification, CoreProtect restores the source when possible and does not resume writes on the target. The selector is changed only after the target has initialized successfully, so an interruption before that change restarts on the source, while an interruption after it restarts on the verified target. If neither the target nor source can be initialized, persistence is halted to avoid writing to an unknown database state. Correct the connection or configuration problem and restart the server; any events still queued only in memory cannot survive that restart.

## Troubleshooting

### Migration Does Not Start

* Confirm that the command is being run from the console on a CoreProtect 23.0+ Patreon build, or 25.0+ if DuckDB or ClickHouse is involved.
* Confirm that the selected target differs from the active source backend.
* Keep `database-lock` enabled and wait for any purge, rollback, reload, or previous migration to finish.
* Check that the target namespace contains no CoreProtect data. DuckDB must use a new database file.

### Connection or Schema Failure

* Confirm that the target settings were loaded while `database-type` still selected the source.
* Verify the target host, port, database, credentials, table prefix, and available storage.
* Confirm that the target account can create, read, insert, update or mutate, and delete or drop the required objects.
* For ClickHouse, confirm the server is version 25.6 or newer and that this installation owns the matching `.clickhouse-writer` identity.

### Verification Failure or Interruption

* Review the server log for the table and row reported by the failure.
* Keep the source database and backup intact.
* Clean the partial target namespace, use a new DuckDB file when applicable, and retry during a quieter maintenance period.

### Automatic Configuration Update Fails

Check that `plugins/CoreProtect/config.yml` is a regular, writable file on a filesystem that supports atomic replacement. CoreProtect reports the migration as failed and restores the source before persistence resumes. The verified target still contains the copied data, so clean only that failed target namespace before retrying; do not manually select it or delete the source.

## Getting Help

If a migration fails, keep both databases intact and review the complete error and surrounding server log. When asking for help in the [CoreProtect Discord](https://discord.gg/b4DZ4jy), include your CoreProtect version, source and destination database types, the complete migration error, and the relevant log excerpt.
