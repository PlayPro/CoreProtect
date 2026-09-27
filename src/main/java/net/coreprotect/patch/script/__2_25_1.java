package net.coreprotect.patch.script;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import net.coreprotect.config.Config;
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.database.clickhouse.ClickHouseIdentifiers;
import net.coreprotect.database.clickhouse.ClickHouseJdbc;
import net.coreprotect.database.clickhouse.ClickHouseJdbcConfig;
import net.coreprotect.database.clickhouse.ClickHouseSchema;
import net.coreprotect.database.clickhouse.ClickHouseLookupIndex;
import net.coreprotect.utility.ErrorReporter;

public class __2_25_1 {

    private static final String COPY_READ_SETTINGS = "max_threads=1,read_overflow_mode='throw',group_by_overflow_mode='throw',"
            + "sort_overflow_mode='throw',result_overflow_mode='throw',timeout_overflow_mode='throw'";

    protected static boolean patchClickHouse(Statement statement) {
        try {
            Config config = Config.getGlobal();
            ClickHouseJdbcConfig jdbcConfig = new ClickHouseJdbcConfig(config.CLICKHOUSE_HOST, config.CLICKHOUSE_PORT,
                    config.CLICKHOUSE_DATABASE, config.CLICKHOUSE_USERNAME, config.CLICKHOUSE_PASSWORD, config.CLICKHOUSE_TLS);
            try (Connection connection = ClickHouseJdbc.openPatchConnection(jdbcConfig)) {
                upgradeClickHouseSchema(connection, config.CLICKHOUSE_DATABASE, ConfigHandler.prefix, null);
            }
            return true;
        }
        catch (Exception e) {
            ErrorReporter.report(e);
            return false;
        }
    }

    public static void upgradeClickHouseSchema(Connection connection, String database, String prefix, UUID owner) throws SQLException {
        String metadata = ClickHouseIdentifiers.qualified(database, prefix + "storage_metadata");
        int version = schemaVersion(connection, metadata);
        if (version == 0 || (version == ClickHouseSchema.VERSION && owner == null)) {
            return;
        }
        if ((version != 4 && version != ClickHouseSchema.VERSION) || owner == null) {
            throw new SQLException("ClickHouse schema " + version + " requires the 25.1 startup migration");
        }
        String sourceName = prefix + "event_data";
        String source = ClickHouseIdentifiers.qualified(database, sourceName);
        String lockName = prefix + "patch_25_1_lock";
        String lock = ClickHouseIdentifiers.qualified(database, lockName);
        String ownerPrefix = "coreprotect-25.1:" + owner + ":";
        String existingClaim = tableProperty(connection, database, lockName, "comment");
        if (version == ClickHouseSchema.VERSION && (existingClaim == null || !existingClaim.startsWith(ownerPrefix))) {
            return;
        }
        String claim = existingClaim == null ? ownerPrefix + tableProperty(connection, database, sourceName, "uuid") : existingClaim;
        if (!claim.startsWith(ownerPrefix)) {
            throw new SQLException("ClickHouse 25.1 migration is owned by another CoreProtect installation; resume the upgrade on that installation");
        }
        String originalUuid;
        try {
            originalUuid = UUID.fromString(claim.substring(ownerPrefix.length())).toString();
        }
        catch (IllegalArgumentException exception) {
            throw new SQLException("Invalid ClickHouse 25.1 migration ownership record", exception);
        }
        execute(connection, "CREATE TABLE IF NOT EXISTS " + lock + " (marker UInt8) ENGINE=TinyLog COMMENT '" + claim + "'");
        String actualClaim = tableProperty(connection, database, lockName, "comment");
        if (!claim.equals(actualClaim) || !actualClaim.startsWith(ownerPrefix)) {
            throw new SQLException("ClickHouse 25.1 migration is owned by another CoreProtect installation; resume the upgrade on that installation");
        }
        version = schemaVersion(connection, metadata);
        if (version != 4 && version != ClickHouseSchema.VERSION) {
            throw new SQLException("ClickHouse schema changed while acquiring the 25.1 migration lock");
        }
        if (!originalUuid.equals(tableProperty(connection, database, sourceName, "uuid"))) {
            throw new SQLException("ClickHouse 25.1 original table identity changed");
        }
        if (version == 4) {
            if (!hasLookupColumns(connection, database, sourceName)) {
                ClickHouseSchema.validateLegacyPhysicalSchema(connection, database, prefix);
            }
            requireIdleDatabase(connection, source);
            requireCompletedMutations(connection, database, sourceName);
            execute(connection, "ALTER TABLE " + source + " ADD CONSTRAINT IF NOT EXISTS coreprotect_25_1_fence CHECK 0");
            requireFence(connection, database, sourceName);
            awaitInserts(connection, database);
            for (String column : ClickHouseSchema.lookupColumnDefinitions()) {
                execute(connection, "ALTER TABLE " + source + " ADD COLUMN IF NOT EXISTS " + column);
            }
            boolean buildEntityIndex;
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT sum(data_compressed_bytes) FROM system.data_skipping_indices WHERE database=? AND table=? AND name='entity_spawn_rowid_idx'")) {
                statement.setString(1, database);
                statement.setString(2, sourceName);
                try (ResultSet result = statement.executeQuery()) {
                    buildEntityIndex = !result.next() || result.getLong(1) == 0;
                }
            }
            execute(connection, "ALTER TABLE " + source + " ADD INDEX IF NOT EXISTS entity_spawn_rowid_idx entity_spawn_rowid TYPE bloom_filter(0.01) GRANULARITY 1");
            if (buildEntityIndex) {
                execute(connection, "ALTER TABLE " + source + " MATERIALIZE INDEX entity_spawn_rowid_idx SETTINGS mutations_sync=2");
            }
            execute(connection, "ALTER TABLE " + source + " ADD CONSTRAINT IF NOT EXISTS coreprotect_write_version CHECK write_version=" + ClickHouseSchema.VERSION);
            ClickHouseSchema.validatePhysicalSchema(connection, database, prefix);
            execute(connection, "ALTER TABLE " + source + " DROP CONSTRAINT coreprotect_25_1_fence");
            ClickHouseLookupIndex.setReady(connection, source, owner, false);
            requireUniqueEvents(connection, source);
            try (Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery(
                    "SELECT 1 FROM " + source + " WHERE family='_batch_receipt' AND lookup_kind IN(1,2) LIMIT 1")) {
                if (rows.next()) {
                    execute(connection, "ALTER TABLE " + source + " DELETE WHERE family='_batch_receipt' AND lookup_kind IN(1,2) SETTINGS mutations_sync=2");
                }
            }
            copyMetadata(connection, source, "tuple(" + tableProperty(connection, database, sourceName, "sorting_key") + ")");
            ClickHouseLookupIndex.setReady(connection, source, owner, true);
        }
        ClickHouseSchema.validatePhysicalSchema(connection, database, prefix);
        for (String ddl : ClickHouseSchema.createStatements(database, prefix)) {
            if (ddl.startsWith("CREATE OR REPLACE VIEW ")) {
                execute(connection, ddl);
            }
        }
        execute(connection, "ALTER TABLE " + metadata + " UPDATE schema_version=" + ClickHouseSchema.VERSION
                + " WHERE schema_version=4 SETTINGS mutations_sync=2");
        if (schemaVersion(connection, metadata) != ClickHouseSchema.VERSION) {
            throw new SQLException("ClickHouse 25.1 schema version was not published");
        }
        execute(connection, "DROP TABLE " + lock);
    }

    private static int schemaVersion(Connection connection, String metadata) throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery("SELECT dataset_id,schema_version FROM " + metadata + " GROUP BY dataset_id,schema_version LIMIT 2")) {
            if (!result.next()) {
                return 0;
            }
            int version = result.getInt(2);
            if (result.next()) {
                throw new SQLException("ClickHouse storage metadata has conflicting identities or schema versions");
            }
            return version;
        }
    }

    private static boolean hasLookupColumns(Connection connection, String database, String table) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT 1 FROM system.columns WHERE database=? AND table=? AND name IN('lookup_kind','lookup_wid','lookup_x','lookup_z','write_version')")) {
            statement.setString(1, database);
            statement.setString(2, table);
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    private static String tableProperty(Connection connection, String database, String table, String property) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT " + property + " FROM system.tables WHERE database=? AND name=?")) {
            statement.setString(1, database);
            statement.setString(2, table);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getString(1) : null;
            }
        }
    }

    private static void requireFence(Connection connection, String database, String table) throws SQLException {
        String definition = tableProperty(connection, database, table, "create_table_query");
        if (definition == null || !normalizeDefinition(definition).contains("constraintcoreprotect_25_1_fencecheck0")) {
            throw new SQLException("ClickHouse 25.1 source table is not fenced: " + table);
        }
    }

    private static void requireIdleDatabase(Connection connection, String source) throws SQLException {
        String sql = "SELECT 1 FROM " + source + " FINAL WHERE family='database_lock' AND rowid=1"
                + " AND (status=2 OR (status=1 AND database_lock_time>=toUnixTimestamp(now())-15)) LIMIT 1";
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(sql)) {
            if (result.next()) {
                throw new SQLException("ClickHouse 25.1 migration requires other CoreProtect installations using this database and prefix to be stopped");
            }
        }
    }

    private static void requireCompletedMutations(Connection connection, String database, String table) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT 1 FROM system.mutations WHERE database=? AND table=? AND NOT is_done LIMIT 1")) {
            statement.setString(1, database);
            statement.setString(2, table);
            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) {
                    throw new SQLException("ClickHouse 25.1 migration requires unfinished mutations to complete first");
                }
            }
        }
    }

    private static void awaitInserts(Connection connection, String database) throws SQLException {
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.MINUTES.toNanos(5);
        try (PreparedStatement statement = connection.prepareStatement("SELECT count() FROM system.processes WHERE query_kind='Insert' AND (current_database=? OR position(query,?)>0)")) {
            statement.setString(1, database);
            statement.setString(2, database);
            while (true) {
                try (ResultSet result = statement.executeQuery()) {
                    if (result.next() && result.getLong(1) == 0) {
                        return;
                    }
                }
                if (System.nanoTime() >= deadline) {
                    throw new SQLException("ClickHouse inserts did not finish after the 25.1 write fence was installed");
                }
                try {
                    Thread.sleep(100);
                }
                catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new SQLException("Interrupted while fencing ClickHouse inserts", exception);
                }
            }
        }
    }

    private static void copyMetadata(Connection connection, String source, String key) throws SQLException {
        String columns = ClickHouseLookupIndex.METADATA_COLUMNS;
        String targets = columns.replace("wid,x,y,z,", "lookup_wid,lookup_x,y,lookup_z,");
        String families = "family IN(" + ClickHouseLookupIndex.FAMILIES + ")";
        String lower = null;
        int ranges = 0;
        java.util.logging.Logger logger = java.util.logging.Logger.getLogger("CoreProtect");
        logger.info("ClickHouse 25.1: building lookup metadata; existing event payloads are retained.");
        while (true) {
            String after = lower == null ? "" : " PREWHERE " + key + ">" + lower + " AND " + keyPrefix(lower, true);
            String upper = null;
            try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(
                    "SELECT toString(" + key + ") FROM " + source + after + " WHERE " + families
                            + " ORDER BY " + key + " LIMIT 1 OFFSET 1000000 SETTINGS " + COPY_READ_SETTINGS)) {
                if (result.next()) {
                    upper = result.getString(1);
                }
            }
            String range = after;
            if (upper != null) {
                range += (range.isEmpty() ? " PREWHERE " : " AND ") + key + "<=" + upper + " AND " + keyPrefix(upper, false);
            }
            String sourceRows = source + " FINAL" + range + " WHERE " + families;
            execute(connection, "INSERT INTO " + source + " (" + targets + ",family,wid,x,z,lookup_kind,write_version) SELECT " + columns
                    + ",'_batch_receipt',toYYYYMM(toDateTime(time,'UTC')),transform(family,[" + ClickHouseLookupIndex.FAMILIES
                    + "],[1,2,3,4,5],0),toInt32(ifNull(user_id,0)),1," + ClickHouseSchema.VERSION + " FROM " + sourceRows
                    + " SETTINGS " + COPY_READ_SETTINGS + ",max_insert_threads=1,max_block_size=8192,min_insert_block_size_rows=1000000,"
                    + "min_insert_block_size_bytes=0,max_bytes_before_external_sort=67108864,max_partitions_per_insert_block=0,optimize_on_insert=0,insert_deduplicate=0");
            ranges++;
            logger.info("ClickHouse 25.1: copied lookup metadata range " + ranges + ".");
            if (upper == null) {
                break;
            }
            lower = upper;
        }
        logger.info("ClickHouse 25.1: verifying lookup metadata.");
        String indexed = "(SELECT p.* REPLACE(" + ClickHouseLookupIndex.logicalFamily()
                + " AS family,p.lookup_wid AS wid,p.lookup_x AS x,p.lookup_z AS z) FROM " + source
                + " AS p WHERE p.family='_batch_receipt' AND p.lookup_kind=1)";
        if (!fingerprint(connection, source + " FINAL WHERE " + families, columns).equals(fingerprint(connection, indexed, columns))) {
            throw new SQLException("ClickHouse 25.1 lookup metadata verification failed; original data remains intact");
        }
    }

    private static String keyPrefix(String key, boolean lower) {
        String[] columns = {"family", "wid", "x", "z", "if(family IN ('database_lock','user','version'),0,time)", "rowid"};
        java.util.StringJoiner branches = new java.util.StringJoiner(" OR ", "(", ")");
        String equal = "";
        for (int index = 0; index < columns.length; index++) {
            String value = "tupleElement(" + key + "," + (index + 1) + ")";
            String operator = lower ? ">" : "<";
            if (index == columns.length - 1) {
                operator += "=";
            }
            branches.add("(" + equal + columns[index] + operator + value + ")");
            equal += columns[index] + "=" + value + " AND ";
        }
        return branches.toString();
    }

    private static void requireUniqueEvents(Connection connection, String table) throws SQLException {
        String sql = "SELECT family FROM " + table + " FINAL"
                + " WHERE family IN(" + ClickHouseLookupIndex.FAMILIES + ")"
                + " GROUP BY family HAVING count()!=groupBitmap(rowid) LIMIT 1 SETTINGS " + COPY_READ_SETTINGS + ",max_block_size=8192";
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(sql)) {
            if (result.next()) {
                throw new SQLException("ClickHouse 25.1 migration found conflicting event identities: " + result.getString(1));
            }
        }
    }

    private static Map<String, String> fingerprint(Connection connection, String source, String columns) throws SQLException {
        Map<String, String> result = new LinkedHashMap<>();
        String hash = "cityHash64(tuple(" + columns + "))";
        String sql = "SELECT family,count(),sum(" + hash + "),groupBitXor(" + hash + ") FROM " + source + " GROUP BY family ORDER BY family SETTINGS " + COPY_READ_SETTINGS + ",max_block_size=8192";
        try (Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery(sql)) {
            while (rows.next()) {
                result.put(rows.getString(1), rows.getString(2) + ":" + rows.getString(3) + ":" + rows.getString(4));
            }
        }
        return result;
    }

    private static void execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private static String normalizeDefinition(String value) {
        return value == null ? "" : value.replaceAll("[\\s`]", "").toLowerCase(Locale.ROOT);
    }

    protected static boolean patchDuckDB(Statement statement) {
        return true;
    }

    protected static boolean patch(Statement statement) {
        try {
            if (Config.getGlobal().MYSQL) {
                statement.executeUpdate("ALTER TABLE " + ConfigHandler.prefix + "sign MODIFY line_1 TEXT, MODIFY line_2 TEXT, MODIFY line_3 TEXT, MODIFY line_4 TEXT, MODIFY line_5 TEXT, MODIFY line_6 TEXT, MODIFY line_7 TEXT, MODIFY line_8 TEXT");
            }
            return true;
        }
        catch (Exception e) {
            ErrorReporter.report(e);
            return false;
        }
    }

}
