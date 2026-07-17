# Migrating this fork to official PlayPro/CoreProtect ClickHouse

This repo should not stay the long-term upstream for a production server if the
official PlayPro/CoreProtect project now supports ClickHouse. The safer path is
to move the data into a fresh official PlayPro ClickHouse namespace and then
update from PlayPro directly.

## Important constraints

- Do not point the official PlayPro jar at the current `kostya` database with
  the `co_` prefix. This fork uses physical tables such as `co_block` and
  `co_container`; official PlayPro creates compatibility views with those names.
- Use a fresh ClickHouse database such as `coreprotect_playpro`.
- Keep the old `kostya` database untouched until the new server has been tested.
- Official PlayPro requires ClickHouse 25.6+ and an Atomic database.
- The official `/co migrate-db` command is Patreon-extension based. This fork's
  ClickHouse schema is not guaranteed to be accepted as an official source.

## Fully automated plugin flow

Install the migration build of this fork, start the server in maintenance mode
with no players online, then run from console:

```text
co migrate-playpro target:coreprotect_playpro
```

The plugin command:

- checks ClickHouse version `25.6+`;
- checks the old fork tables in the current database, normally `kostya`;
- creates `coreprotect_playpro` with the official PlayPro physical schema;
- refuses to write into a non-empty target;
- pauses CoreProtect logging while the final copy runs;
- migrates all supported logical tables into `co_event_data`;
- writes high-water marks so official PlayPro continues row IDs after the
  imported history;
- prints verification counts for every migrated family.

After the command succeeds, stop the server immediately, replace this fork jar
with the official PlayPro/CoreProtect jar, and set:

```yaml
database-type: clickhouse
table-prefix: co_
clickhouse-database: coreprotect_playpro
```

Then start the staging server and run the checks below.

## Standalone script flow

Run this from the repository root after the production Minecraft server is
stopped:

```powershell
powershell -ExecutionPolicy Bypass -File tools\migrate-to-playpro-clickhouse.ps1 `
  -ClickHouseHost ds92143.craft-hosting.ru `
  -Username default `
  -Password "YOUR_CLICKHOUSE_PASSWORD" `
  -SourceDatabase kostya `
  -TargetDatabase coreprotect_playpro
```

The script:

- checks ClickHouse version `25.6+`;
- checks the old fork tables in `kostya`;
- creates `coreprotect_playpro` with the official PlayPro physical schema;
- refuses to write into a non-empty target;
- migrates all supported logical tables into `co_event_data`;
- writes high-water marks so official PlayPro continues row IDs after the
  imported history;
- prints verification counts for every migrated family.

After the script succeeds, install the official PlayPro/CoreProtect jar and set:

```yaml
database-type: clickhouse
table-prefix: co_
clickhouse-database: coreprotect_playpro
```

Then start the staging server and run the checks below.

## Manual/staging flow

1. Stop the production server and take a ClickHouse backup/snapshot of `kostya`.
2. Create a fresh target database:

   ```sql
   CREATE DATABASE coreprotect_playpro ENGINE = Atomic;
   ```

3. Install the official PlayPro/CoreProtect jar on a staging server.
4. Configure it to use the new database:

   ```yaml
   database-type: clickhouse
   table-prefix: co_
   clickhouse-database: coreprotect_playpro
   ```

5. Start the staging server once so PlayPro creates the official schema and the
   `.clickhouse-writer` file. Stop the server before players can generate real
   logs.
6. Run `tools/playpro-clickhouse-migration.sql` against ClickHouse.
7. Start the staging server again and test:

   ```text
   /co status
   /co lookup user:BADVS time:1d
   /co lookup user:BADVS time:1d action:container
   /co lookup user:BADVS time:1d action:spawn
   /co near
   /co rollback user:BADVS time:10m radius:3
   /co restore user:BADVS time:10m radius:3
   ```

8. Test double chests, hoppers, boats/chest boats, entity spawn, entity click,
   item pickup/drop, block rollback, container rollback, `#count`, and global
   lookup.
9. If staging passes, switch production to the official jar and the new
   `coreprotect_playpro` database.

## Notes

- The SQL intentionally does not migrate old `co_version` or `co_database_lock`
  rows. The official plugin creates its own current version and lock rows and
  expects those families to contain only row ID `1`.
- Versioned fork tables are read with `FINAL` so the migrated data contains the
  current logical state for rollback flags and entity state.
- The old database remains available as an emergency fallback.
