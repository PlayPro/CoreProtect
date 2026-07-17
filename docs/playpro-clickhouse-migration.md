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

## Suggested migration flow

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

