# Database Migration (SQLite ↔ MySQL)

CoreProtect 23.0+ includes the ability to migrate your database between SQLite and MySQL without losing any data.

> **Note:** Database migration functionality is exclusive to CoreProtect 23.0+ Patreon builds for Patron supporters.

## Overview

The `/co migrate-db` command allows you to seamlessly transfer all your CoreProtect data from SQLite to MySQL or vice versa. This is particularly useful when:

* Starting with SQLite and wanting to upgrade to MySQL for better performance
* Moving from MySQL back to SQLite for simpler server setups
* Transferring data between different server configurations

## Command Usage

| Command | Parameters | Description |
| --- | --- | --- |
| `/co migrate-db` | `<sqlite|mysql>` | Migrate to the specified database type |

**Examples:**

* `/co migrate-db mysql` - Migrate from SQLite to MySQL
* `/co migrate-db sqlite` - Migrate from MySQL to SQLite

> **Console Only:** This command can only be executed from the server console, not from in-game.

---

## Migration Process

### Step 1: Preparation

**Before starting the migration:**

1. Ensure your server is running with your current CoreProtect database
2. Configure your new database in `config.yml` (ensure `use-mysql` is updated correctly)
3. **Important:** Do NOT restart your server or use `/co reload` after modifying the config

### Step 2: Execute Migration

#### 1. **Run the migration command from your server console:**
   ```
   co migrate-db <mysql|sqlite>
   ```

#### 2. **Monitor the progress:**
   * The migration will display detailed progress information
   * Large databases may take considerable time to complete
   * Progress bars and speed indicators will show current status

#### 3. **Do not interrupt the process:**
   * Avoid restarting your server during migration
   * Let the process complete naturally

### Step 3: Post-Migration

**After successful completion:**

1. **Automatic switchover:** CoreProtect will automatically begin using the new database
2. **Verify configuration:** Ensure `use-mysql` is set correctly in your `config.yml` as per step 1
3. **Test functionality:** Perform basic CoreProtect operations to verify everything works
4. **Clean up:** Once satisfied, you may manually delete your old database files

---

## Important Considerations

### Migration Safety

* **Non-destructive process:** The migration does not modify or delete your existing database
* **Interrupted migration:** If something goes wrong, simply delete the new database and continue using the old one
* **Data verification:** The process includes automatic verification to ensure data integrity

### Performance & Requirements

* **Processing time:** Large databases with millions of records may take hours to migrate
* **Resource usage:** The migration process is resource-intensive and may impact server performance
* **Parallel processing:** SQLite to MySQL migrations use optimized parallel processing for better speed

### Restrictions & Limitations

* **Console only:** Cannot be executed from in-game chat
* **Interruption handling:** If interrupted, the target database must be manually wiped before restarting
* **Patreon exclusive:** Only available in CoreProtect 23.0+ Patreon builds

---

## Troubleshooting

### Common Issues

**Migration won't start:**

* Verify you're using a CoreProtect 23.0+ Patreon build
* Ensure you're not trying to migrate to the same database type
* Check that no other CoreProtect operations are running

**Migration interrupted:**

* Manually delete/wipe the target database
* Verify server stability before restarting migration
* Consider migrating during low-activity periods

**Performance issues:**

* Monitor server resources during migration
* Consider temporarily reducing server activity
* Large tables may cause temporary slowdowns

**Data verification failures:**

* Check database connectivity and permissions
* Review server logs for specific error messages
* Ensure sufficient disk space on target database

### Getting Help

If you encounter issues with database migration:

1. Check server logs for detailed error messages
2. Verify database permissions and connectivity
3. Ensure adequate system resources (RAM, CPU, disk space)
4. Contact support through the CoreProtect Discord with specific error details

---

## Technical Details

The migration process includes several advanced features:

* Batch processing with dynamic sizing based on performance
* Automatic retry mechanisms for temporary failures
* Data integrity verification comparing source and target records
* Progress tracking with estimated completion times
* Graceful error handling with detailed logging

The migration handles all CoreProtect data types including:

* Block changes and rollbacks
* Container transactions
* Player interactions and sessions
* Chat messages and commands
* User data and statistics
* All metadata and configuration data
