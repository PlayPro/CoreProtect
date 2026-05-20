# Automatic Purging

Automatic purging removes old CoreProtect data on a daily schedule, helping keep database growth under control without requiring manual `/co purge` runs.

> **Note:** Automatic purging is not enabled by default. This feature is exclusive to CoreProtect 24.0+ [Patreon builds](http://patreon.com/coreprotect).

## Configuration

To enable automatic purging, set `auto-purge` in `config.yml` to the amount of data you want to keep:

```yaml
auto-purge: 180d
```

This example keeps the most recent 180 days of CoreProtect data and automatically removes older data.

Supported values use the same style as CoreProtect command times, such as `30d`, `12w`, or `6mo`. The minimum automatic purge value is `30d`. Set `auto-purge: false` to disable automatic purging.

## Schedule

Automatic purging runs once per day using your server's local time. By default, it runs at midnight.

To change the daily runtime, manually add `auto-purge-time: "03:30"` to `config.yml`. Use 24-hour `HH:mm` server time.

After changing `auto-purge` or `auto-purge-time`, use `/co reload` or restart the server. Changes apply to the next scheduled run.

When automatic purging is enabled, CoreProtect logs the next scheduled run when the server starts and again after an automatic purge completes.

## How It Works

Automatic purging runs in the background and removes old data incrementally in small chunks, with short pauses between database work. The server can continue to be used normally while it runs.

Automatic purging uses the same CoreProtect data tables as manual purging, but it does not rebuild the SQLite database or optimize MySQL tables. It deletes old rows in place, which helps maintain the current database size over time but may not immediately reduce the database file size or table size on disk.

Only one automatic purge can run at a time. If the server shuts down, a manual purge starts, a database migration or conversion starts, or the consumer is manually paused, the automatic purge stops safely and can continue during the next scheduled run.

## Important Notes

* **Patreon exclusive:** Only available in CoreProtect 24.0+ [Patreon builds](http://patreon.com/coreprotect)
* **Not enabled by default:** Set `auto-purge` in `config.yml` to enable automatic purging
* **Background cleanup:** Automatic purging is designed to run while the server remains usable

## Existing Databases

If enabling automatic purging on a new database, no additional action is required.

If enabling automatic purging on an existing database, it is recommended to run a manual purge first using the same time value:

```text
/co purge t:180d
```

For MySQL, add `#optimize` if you want to reclaim disk space during the initial manual purge:

```text
/co purge t:180d #optimize
```

This lets the manual purge reduce the existing database size, while automatic purging helps keep the database from growing beyond the configured retention period afterward.

## Status

Use `/co status` to see how many rows have been automatically purged since the last server restart.

## Troubleshooting

**Automatic purging won't start:**

* Verify you're using a CoreProtect 24.0+ [Patreon build](http://patreon.com/coreprotect)
* Verify `auto-purge` is set to a valid value of at least `30d`
* Check the server console for automatic purge scheduling messages
