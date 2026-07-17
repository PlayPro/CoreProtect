# Migrating this fork to official PlayPro/CoreProtect ClickHouse

Official PlayPro/CoreProtect now has its own ClickHouse backend. The safest
long-term path is to move this fork's old tables into the official columnar
schema, then run the official jar directly.

## What the migration does

The in-plugin migration keeps the same ClickHouse database and table prefix:

```yaml
database-type: clickhouse
clickhouse-database: kostya
table-prefix: co_
```

When you run the command, the plugin:

- waits for the consumer queue to drain;
- pauses CoreProtect logging;
- checks ClickHouse `25.6+`;
- checks that the current database is UUID-backed/Atomic;
- creates official PlayPro physical tables:
  - `co_storage_metadata`
  - `co_writer_registration`
  - `co_retention_high_water`
  - `co_event_data`
- renames the old fork tables from `co_*` to `co_migrate_*`;
- imports supported history into `co_event_data`;
- converts the fork's JSON/SNBT metadata to PlayPro's binary serialization;
- verifies every imported family row count;
- recreates and validates all official compatibility views, including every
  `Array(Int8)` binary column used by PlayPro's JDBC lookups;
- writes high-water rows so official PlayPro continues after imported row IDs;
- leaves logging paused so you can stop the server and swap jars.

The old data remains in the same database as `co_migrate_*`.

## Command

Run from console during maintenance with no players online:

```text
co migrate-playpro
```

For a non-default database or prefix:

```text
co migrate-playpro database:kostya prefix:co_ archive-prefix:co_migrate_
```

After success, stop the server immediately. Replace this fork jar with the
official PlayPro/CoreProtect jar and keep:

```yaml
database-type: clickhouse
clickhouse-database: kostya
table-prefix: co_
```

On the next startup, official PlayPro will create its compatibility views such
as `co_block`, `co_container`, and `co_item` over `co_event_data`.

## Repairing an already migrated database

If the migration was already run with an older build, temporarily start the
server with this fork jar and run from console with no players online:

```text
co repair-playpro-items database:coreprotect_art prefix:co_
```

The repair keeps `co_event_data` intact until a fully repaired replacement has
been copied. It then swaps the tables in one `RENAME TABLE` statement, retains
the previous table as `co_event_data_backup_repair_*`, recreates every official
view, and fails if any expected view or binary column type is incompatible.
After the success message, stop the server and put the official PlayPro jar
back.

## Required permissions

The ClickHouse user does not need permission to create a new database. It does
need permission in the existing database for:

- `CREATE TABLE`
- `RENAME TABLE`
- `INSERT SELECT`
- `SELECT`

If `RENAME TABLE` is not allowed, this in-place migration cannot keep the
official `co_` prefix because official PlayPro needs view names like `co_block`.

## Why the old tables are archived

The fork has physical tables named `co_block`, `co_container`, `co_item`, and so
on. Official PlayPro uses only a few physical tables and creates compatibility
views with those old names. Because a table and a view cannot share one name,
the old physical tables must be moved first:

```text
co_block     -> co_migrate_block
co_container -> co_migrate_container
co_item      -> co_migrate_item
```

Then official PlayPro can create `co_block`, `co_container`, and `co_item` as
views.

## Checks after switching to official PlayPro

Test on staging before production:

```text
/co status
/co lookup user:BADVS time:1d
/co lookup user:BADVS time:1d action:container
/co lookup user:BADVS time:1d action:spawn
/co near
/co rollback user:BADVS time:10m radius:3
/co restore user:BADVS time:10m radius:3
```

Also test double chests, hoppers, boats/chest boats, entity spawn, entity click,
item pickup/drop, block rollback, container rollback, `#count`, and global
lookup.

## Notes

- The command refuses to run if `co_event_data`, `co_writer_registration`, or
  `co_retention_high_water` already contains rows.
- Versioned fork tables are read with `FINAL` so imported rollback flags and
  entity state use the current logical values.
- The old `co_version` and `co_database_lock` tables are archived but not
  imported as normal history; official PlayPro owns those core rows itself.
