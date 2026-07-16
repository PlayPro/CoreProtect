package net.coreprotect.database.clickhouse;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.StringJoiner;

public final class ClickHouseSchema {

    static final int VERSION = 1;

    private static final String INTEGER_CODEC = " CODEC(Delta, ZSTD(3))";
    private static final String VALUE_CODEC = " CODEC(ZSTD(3))";
    private static final String PURGEABLE_FAMILIES = purgeableFamilies();
    private static final String EVENT_PARTITION_KEY = "if(family IN (" + PURGEABLE_FAMILIES + "),toYYYYMM(toDateTime(time,'UTC')),0)";
    private static final String EVENT_SORTING_KEY = "(dataset_id,family,wid,x,z,if(family IN ('database_lock','user','version'),0,time),rowid)";
    private static final String[][] EVENT_DATA_SKIPPING_INDEX_DEFINITIONS = {
            { "producer_sequence_idx", "producer_sequence", "minmax", "1" },
            { "rowid_idx", "rowid", "bloom_filter(0.01)", "1" },
            { "entity_uuid_idx", "uuid", "bloom_filter(0.01)", "1" },
            { "entity_kill_rowid_idx", "kill_rowid", "bloom_filter(0.01)", "1" }
    };
    private static final String[][] STORAGE_METADATA_COLUMN_DEFINITIONS = {
            { "dataset_id", "UUID" + VALUE_CODEC },
            { "producer_id", "UUID" + VALUE_CODEC },
            { "schema_version", "UInt32" + INTEGER_CODEC },
            { "created_at", "DateTime64(3, 'UTC')" + INTEGER_CODEC }
    };
    private static final String[][] WRITER_REGISTRATION_COLUMN_DEFINITIONS = {
            { "dataset_id", "UUID" },
            { "producer_id", "UUID" },
            { "writer_id", "UUID" },
            { "registration_order", "UInt64 DEFAULT generateSnowflakeID()" },
            { "registered_at", "DateTime64(3, 'UTC')" }
    };
    private static final String[][] RETENTION_HIGH_WATER_COLUMN_DEFINITIONS = {
            { "dataset_id", "UUID" + VALUE_CODEC },
            { "producer_id", "UUID" + VALUE_CODEC },
            { "producer_sequence", "UInt64" + INTEGER_CODEC },
            { "family", "LowCardinality(String)" + VALUE_CODEC },
            { "rowid", "UInt64" + INTEGER_CODEC },
            { "recorded_at", "DateTime64(3, 'UTC')" + INTEGER_CODEC }
    };
    private static final String[][] EVENT_COLUMN_DEFINITIONS = {
            { "dataset_id", "UUID" + VALUE_CODEC },
            { "producer_id", "UUID" + VALUE_CODEC },
            { "producer_sequence", "UInt64" + INTEGER_CODEC },
            { "batch_id", "UUID" + VALUE_CODEC },
            { "batch_ordinal", "UInt32" + INTEGER_CODEC },
            { "family", "LowCardinality(String)" + VALUE_CODEC },
            { "rowid", "UInt64" + INTEGER_CODEC },
            { "time", "UInt32" + INTEGER_CODEC },
            { "user_id", "Nullable(UInt32)" + VALUE_CODEC },
            { "wid", "UInt32" + INTEGER_CODEC },
            { "x", "Int32" + INTEGER_CODEC },
            { "y", "Nullable(Int32)" + VALUE_CODEC },
            { "z", "Int32" + INTEGER_CODEC },
            { "type", "Nullable(UInt32)" + VALUE_CODEC },
            { "data", "Nullable(Int64)" + VALUE_CODEC },
            { "payload", "Nullable(String)" + VALUE_CODEC },
            { "meta", "Nullable(String)" + VALUE_CODEC },
            { "blockdata", "Nullable(String)" + VALUE_CODEC },
            { "action", "Nullable(UInt8)" + VALUE_CODEC },
            { "rolled_back", "Nullable(UInt8)" + VALUE_CODEC },
            { "amount", "Nullable(Int32)" + VALUE_CODEC },
            { "metadata", "Nullable(String)" + VALUE_CODEC },
            { "entity_spawn_rowid", "Nullable(UInt64)" + VALUE_CODEC },
            { "id", "Nullable(UInt32)" + VALUE_CODEC },
            { "name", "Nullable(String)" + VALUE_CODEC },
            { "text", "Nullable(String)" + VALUE_CODEC },
            { "message", "Nullable(String)" + VALUE_CODEC },
            { "status", "Nullable(UInt8)" + VALUE_CODEC },
            { "database_lock_time", "Nullable(UInt32)" + VALUE_CODEC },
            { "version", "Nullable(String)" + VALUE_CODEC },
            { "block_rowid", "Nullable(UInt64)" + VALUE_CODEC },
            { "kill_rowid", "Nullable(UInt64)" + VALUE_CODEC },
            { "block_rowid_present", "Nullable(UInt8)" + VALUE_CODEC },
            { "kill_rowid_present", "Nullable(UInt8)" + VALUE_CODEC },
            { "uuid", "Nullable(String)" + VALUE_CODEC },
            { "user_name", "Nullable(String)" + VALUE_CODEC },
            { "current_wid", "Nullable(UInt32)" + VALUE_CODEC },
            { "origin_x", "Nullable(Float64)" + VALUE_CODEC },
            { "origin_y", "Nullable(Float64)" + VALUE_CODEC },
            { "origin_z", "Nullable(Float64)" + VALUE_CODEC },
            { "current_x", "Nullable(Float64)" + VALUE_CODEC },
            { "current_y", "Nullable(Float64)" + VALUE_CODEC },
            { "current_z", "Nullable(Float64)" + VALUE_CODEC },
            { "yaw", "Nullable(Float32)" + VALUE_CODEC },
            { "pitch", "Nullable(Float32)" + VALUE_CODEC },
            { "entity_data", "Nullable(String)" + VALUE_CODEC },
            { "entity_data_present", "Nullable(UInt8)" + VALUE_CODEC },
            { "removed", "Nullable(UInt8)" + VALUE_CODEC },
            { "color", "Nullable(UInt32)" + VALUE_CODEC },
            { "color_secondary", "Nullable(UInt32)" + VALUE_CODEC },
            { "sign_data", "Nullable(UInt8)" + VALUE_CODEC },
            { "waxed", "Nullable(UInt8)" + VALUE_CODEC },
            { "face", "Nullable(UInt8)" + VALUE_CODEC },
            { "line_1", "Nullable(String)" + VALUE_CODEC },
            { "line_2", "Nullable(String)" + VALUE_CODEC },
            { "line_3", "Nullable(String)" + VALUE_CODEC },
            { "line_4", "Nullable(String)" + VALUE_CODEC },
            { "line_5", "Nullable(String)" + VALUE_CODEC },
            { "line_6", "Nullable(String)" + VALUE_CODEC },
            { "line_7", "Nullable(String)" + VALUE_CODEC },
            { "line_8", "Nullable(String)" + VALUE_CODEC }
    };

    public static final List<String> EVENT_COLUMNS = eventColumns();
    public static final List<String> EVENT_COLUMN_TYPES = eventColumnTypes();
    static final int PHYSICAL_TABLE_COUNT = 4;

    private ClickHouseSchema() {
        throw new IllegalStateException("Schema class");
    }

    public static List<String> createStatements(String database, String prefix) {
        Names names = new Names(database, prefix);
        List<String> statements = new ArrayList<>();
        statements.add(createStorageMetadata(names));
        statements.add(createWriterRegistration(names));
        statements.add(createRetentionHighWater(names));
        statements.add(createEventData(names));
        addCompatibilityViews(statements, names);
        return Collections.unmodifiableList(statements);
    }

    static void validatePhysicalSchema(Connection connection, String database, String prefix) throws SQLException {
        Names names = new Names(database, prefix);
        validateTable(connection, database, names.rawTable("storage_metadata"), "MergeTree", "tuple()", "", STORAGE_METADATA_COLUMN_DEFINITIONS,
                "fsync_after_insert=1", "fsync_part_directory=1");
        validateTable(connection, database, names.rawTable("writer_registration"), "MergeTree", "(registration_order,writer_id)", "", WRITER_REGISTRATION_COLUMN_DEFINITIONS,
                "fsync_after_insert=1", "fsync_part_directory=1");
        validateTable(connection, database, names.rawTable("retention_high_water"), "MergeTree", "(dataset_id,family,producer_sequence,rowid)", "", RETENTION_HIGH_WATER_COLUMN_DEFINITIONS,
                "fsync_after_insert=1", "fsync_part_directory=1");
        validateTable(connection, database, names.rawTable("event_data"), "CoalescingMergeTree", EVENT_SORTING_KEY, EVENT_PARTITION_KEY, EVENT_COLUMN_DEFINITIONS,
                "fsync_after_insert=1", "fsync_part_directory=1", "non_replicated_deduplication_window=1000");
        validateDataSkippingIndexes(connection, database, names.rawTable("event_data"), EVENT_DATA_SKIPPING_INDEX_DEFINITIONS);
    }

    static void requireUnownedTablesEmpty(Connection connection, String database, String prefix) throws SQLException {
        Names names = new Names(database, prefix);
        for (String suffix : new String[] { "writer_registration", "retention_high_water", "event_data" }) {
            try (PreparedStatement statement = connection.prepareStatement("SELECT 1 FROM " + names.table(suffix) + " LIMIT 1"); ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    throw new SQLException("ClickHouse table " + names.rawTable(suffix) + " contains data without CoreProtect storage metadata");
                }
            }
        }
    }

    private static void validateTable(Connection connection, String database, String table, String expectedEngine, String expectedSortingKey, String expectedPartitionKey,
            String[][] expectedColumns, String... requiredSettings) throws SQLException {
        String tableQuery = "SELECT engine,sorting_key,partition_key,create_table_query FROM system.tables WHERE database=? AND name=? LIMIT 2";
        try (PreparedStatement statement = connection.prepareStatement(tableQuery)) {
            statement.setString(1, database);
            statement.setString(2, table);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new SQLException("ClickHouse schema table is missing: " + table);
                }
                String engine = resultSet.getString(1);
                String sortingKey = resultSet.getString(2);
                String partitionKey = resultSet.getString(3);
                String createQuery = normalizeSql(resultSet.getString(4));
                if (resultSet.next()) {
                    throw new SQLException("ClickHouse schema table is ambiguous: " + table);
                }
                if (!expectedEngine.equals(engine)
                        || !normalizeKey(expectedSortingKey).equals(normalizeKey(sortingKey))
                        || !normalizeExpression(expectedPartitionKey).equals(normalizeExpression(partitionKey))) {
                    throw new SQLException("ClickHouse table has an incompatible engine or key definition: " + table);
                }
                for (String setting : requiredSettings) {
                    if (!createQuery.contains(normalizeSql(setting))) {
                        throw new SQLException("ClickHouse table is missing required setting " + setting + ": " + table);
                    }
                }
            }
        }

        String columnQuery = "SELECT name,type,default_kind,default_expression FROM system.columns WHERE database=? AND table=? ORDER BY position";
        try (PreparedStatement statement = connection.prepareStatement(columnQuery)) {
            statement.setString(1, database);
            statement.setString(2, table);
            try (ResultSet resultSet = statement.executeQuery()) {
                for (String[] expectedColumn : expectedColumns) {
                    if (!resultSet.next()) {
                        throw new SQLException("ClickHouse table has missing columns: " + table);
                    }
                    String definition = expectedColumn[1];
                    String expectedDefault = defaultExpression(definition);
                    String defaultKind = resultSet.getString(3);
                    String defaultExpression = resultSet.getString(4);
                    boolean defaultMatches = expectedDefault == null
                            ? defaultKind == null || defaultKind.isEmpty()
                            : "DEFAULT".equalsIgnoreCase(defaultKind) && normalizeSql(expectedDefault).equals(normalizeSql(defaultExpression));
                    if (!expectedColumn[0].equals(resultSet.getString(1))
                            || !normalizeSql(columnType(definition)).equals(normalizeSql(resultSet.getString(2)))
                            || !defaultMatches) {
                        throw new SQLException("ClickHouse table has an incompatible column definition: " + table + "." + expectedColumn[0]);
                    }
                }
                if (resultSet.next()) {
                    throw new SQLException("ClickHouse table has unexpected columns: " + table);
                }
            }
        }
    }

    private static String createStorageMetadata(Names names) {
        return table(names.storageMetadata, columnDefinitions(STORAGE_METADATA_COLUMN_DEFINITIONS))
                + " ENGINE = MergeTree"
                + " ORDER BY tuple()"
                + " SETTINGS fsync_after_insert=1,fsync_part_directory=1";
    }

    private static String createWriterRegistration(Names names) {
        return table(names.writerRegistration, columnDefinitions(WRITER_REGISTRATION_COLUMN_DEFINITIONS))
                + " ENGINE = MergeTree"
                + " ORDER BY (registration_order,writer_id)"
                + " SETTINGS fsync_after_insert=1,fsync_part_directory=1";
    }

    private static String createRetentionHighWater(Names names) {
        return table(names.retentionHighWater, columnDefinitions(RETENTION_HIGH_WATER_COLUMN_DEFINITIONS))
                + " ENGINE = MergeTree"
                + " ORDER BY (dataset_id,family,producer_sequence,rowid)"
                + " SETTINGS fsync_after_insert=1,fsync_part_directory=1";
    }

    private static String createEventData(Names names) {
        String[] columns = eventColumnDefinitions();
        String[] tableElements = new String[columns.length + EVENT_DATA_SKIPPING_INDEX_DEFINITIONS.length];
        System.arraycopy(columns, 0, tableElements, 0, columns.length);
        for (int index = 0; index < EVENT_DATA_SKIPPING_INDEX_DEFINITIONS.length; index++) {
            String[] definition = EVENT_DATA_SKIPPING_INDEX_DEFINITIONS[index];
            tableElements[columns.length + index] = "INDEX " + definition[0] + " " + definition[1] + " TYPE " + definition[2] + " GRANULARITY " + definition[3];
        }
        return table(names.eventData, tableElements)
                + " ENGINE = CoalescingMergeTree"
                + " PARTITION BY " + EVENT_PARTITION_KEY
                + " ORDER BY " + EVENT_SORTING_KEY
                + " SETTINGS fsync_after_insert=1,fsync_part_directory=1,non_replicated_deduplication_window=1000";
    }

    private static void validateDataSkippingIndexes(Connection connection, String database, String table, String[][] expectedIndexes) throws SQLException {
        String indexQuery = "SELECT type_full,expr,granularity FROM system.data_skipping_indices WHERE database=? AND table=? AND name=? LIMIT 2";
        try (PreparedStatement statement = connection.prepareStatement(indexQuery)) {
            for (String[] expectedIndex : expectedIndexes) {
                statement.setString(1, database);
                statement.setString(2, table);
                statement.setString(3, expectedIndex[0]);
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (!resultSet.next()) {
                        throw new SQLException("ClickHouse table is missing required data-skipping index " + expectedIndex[0] + ": " + table);
                    }
                    boolean matches = normalizeSql(expectedIndex[2]).equals(normalizeSql(resultSet.getString(1)))
                            && normalizeExpression(expectedIndex[1]).equals(normalizeExpression(resultSet.getString(2)))
                            && Long.parseLong(expectedIndex[3]) == resultSet.getLong(3);
                    if (!matches || resultSet.next()) {
                        throw new SQLException("ClickHouse table has an incompatible data-skipping index " + expectedIndex[0] + ": " + table);
                    }
                }
            }
        }
    }

    private static void addCompatibilityViews(List<String> statements, Names names) {
        statements.add(view(names, ClickHouseFamily.ART_MAP, "e.rowid AS rowid,e.id AS id,e.name AS art"));
        statements.add(rollbackView(names, ClickHouseFamily.BLOCK, "e.rowid AS rowid,e.time AS time,e.user_id AS `user`,e.wid AS wid,e.x AS x,e.y AS y,e.z AS z,e.type AS type,e.data AS data," + binary("e.meta", "meta") + "," + binary("e.blockdata", "blockdata") + ",e.action AS action"));
        statements.add(view(names, ClickHouseFamily.CHAT, "e.rowid AS rowid,e.time AS time,e.user_id AS `user`,e.wid AS wid,e.x AS x,e.y AS y,e.z AS z,e.message AS message"));
        statements.add(view(names, ClickHouseFamily.COMMAND, "e.rowid AS rowid,e.time AS time,e.user_id AS `user`,e.wid AS wid,e.x AS x,e.y AS y,e.z AS z,e.message AS message"));
        statements.add(rollbackView(names, ClickHouseFamily.CONTAINER, "e.rowid AS rowid,e.time AS time,e.user_id AS `user`,e.wid AS wid,e.x AS x,e.y AS y,e.z AS z,e.type AS type,e.data AS data,e.amount AS amount," + binary("e.metadata", "metadata") + ",e.action AS action"));
        statements.add(rollbackView(names, ClickHouseFamily.ENTITY_CONTAINER, "e.rowid AS rowid,e.time AS time,e.user_id AS `user`,e.entity_spawn_rowid AS entity_spawn_rowid,e.wid AS wid,e.x AS x,e.y AS y,e.z AS z,e.type AS type,e.data AS data,e.amount AS amount," + binary("e.metadata", "metadata") + ",e.action AS action"));
        statements.add(view(names, ClickHouseFamily.ENTITY_INTERACTION, "e.rowid AS rowid,e.time AS time,e.user_id AS `user`,e.entity_spawn_rowid AS entity_spawn_rowid,e.wid AS wid,e.x AS x,e.y AS y,e.z AS z,e.type AS type,e.action AS action," + binary("e.metadata", "metadata") + ",e.rolled_back AS rolled_back"));
        statements.add(rollbackView(names, ClickHouseFamily.ITEM, "e.rowid AS rowid,e.time AS time,e.user_id AS `user`,e.wid AS wid,e.x AS x,e.y AS y,e.z AS z,e.type AS type," + binary("e.payload", "data") + ",e.amount AS amount,e.action AS action"));
        statements.add(currentView(names, ClickHouseFamily.DATABASE_LOCK, "e.rowid AS rowid,e.status AS status,e.database_lock_time AS time"));
        statements.add(view(names, ClickHouseFamily.ENTITY, "e.rowid AS rowid,e.time AS time," + binary("e.payload", "data")));
        statements.add(entitySpawnView(names));
        statements.add(view(names, ClickHouseFamily.ENTITY_MAP, "e.rowid AS rowid,e.id AS id,e.name AS entity"));
        statements.add(view(names, ClickHouseFamily.MATERIAL_MAP, "e.rowid AS rowid,e.id AS id,e.name AS material"));
        statements.add(view(names, ClickHouseFamily.BLOCKDATA_MAP, "e.rowid AS rowid,e.id AS id,e.text AS data"));
        statements.add(view(names, ClickHouseFamily.SESSION, "e.rowid AS rowid,e.time AS time,e.user_id AS `user`,e.wid AS wid,e.x AS x,e.y AS y,e.z AS z,e.action AS action"));
        statements.add(view(names, ClickHouseFamily.SIGN, "e.rowid AS rowid,e.time AS time,e.user_id AS `user`,e.wid AS wid,e.x AS x,e.y AS y,e.z AS z,e.action AS action,e.color AS color,e.color_secondary AS color_secondary,e.sign_data AS data,e.waxed AS waxed,e.face AS face,e.line_1 AS line_1,e.line_2 AS line_2,e.line_3 AS line_3,e.line_4 AS line_4,e.line_5 AS line_5,e.line_6 AS line_6,e.line_7 AS line_7,e.line_8 AS line_8"));
        statements.add(view(names, ClickHouseFamily.SKULL, "e.rowid AS rowid,e.time AS time,e.name AS owner,e.text AS skin"));
        statements.add(currentView(names, ClickHouseFamily.USER, "e.rowid AS rowid,e.time AS time,e.user_name AS `user`,e.uuid AS uuid"));
        statements.add(view(names, ClickHouseFamily.USERNAME_LOG, "e.rowid AS rowid,e.time AS time,e.uuid AS uuid,e.user_name AS `user`"));
        statements.add(currentView(names, ClickHouseFamily.VERSION, "e.rowid AS rowid,e.time AS time,e.version AS version"));
        statements.add(view(names, ClickHouseFamily.WORLD, "e.rowid AS rowid,e.id AS id,e.name AS world"));
    }

    private static String view(Names names, ClickHouseFamily family, String projection) {
        return "CREATE OR REPLACE VIEW " + names.table(family.getTableName())
                + " AS SELECT " + projection
                + " FROM " + events(names, family) + " AS e";
    }

    private static String currentView(Names names, ClickHouseFamily family, String projection) {
        return "CREATE OR REPLACE VIEW " + names.table(family.getTableName())
                + " AS SELECT " + projection
                + " FROM " + currentEvents(names, family) + " AS e";
    }

    private static String rollbackView(Names names, ClickHouseFamily family, String projection) {
        return currentView(names, family, projection + ",e.rolled_back AS rolled_back");
    }

    private static String entitySpawnView(Names names) {
        return "CREATE OR REPLACE VIEW " + names.table(ClickHouseFamily.ENTITY_SPAWN.getTableName())
                + " AS SELECT e.rowid AS rowid,e.time AS time"
                + ",if(e.block_rowid_present=1,e.block_rowid,NULL) AS block_rowid"
                + ",if(e.kill_rowid_present=1,e.kill_rowid,NULL) AS kill_rowid"
                + ",e.uuid AS uuid,e.wid AS wid,e.current_wid AS current_wid"
                + ",e.origin_x AS origin_x,e.origin_y AS origin_y,e.origin_z AS origin_z"
                + ",e.current_x AS x,e.current_y AS y,e.current_z AS z"
                + ",e.yaw AS yaw,e.pitch AS pitch," + binary("if(e.entity_data_present=1,e.entity_data,NULL)", "data") + ",e.removed AS removed"
                + " FROM " + currentEvents(names, ClickHouseFamily.ENTITY_SPAWN) + " AS e";
    }

    static String binary(String value, String alias) {
        String presentValue = "ifNull(" + value + ",'')";
        String bytes = "arrayMap(i -> reinterpretAsInt8(substring(" + presentValue + ",i,1)),range(1,length(" + presentValue + ")+1))";
        return "if(isNull(" + value + "),CAST([], 'Array(Int8)'),arrayConcat([toInt8(0)]," + bytes + ")) AS " + alias;
    }

    private static String events(Names names, ClickHouseFamily family) {
        return "(SELECT * FROM " + names.eventData + " WHERE family='" + family.getTableName() + "')";
    }

    private static String currentEvents(Names names, ClickHouseFamily family) {
        return "(SELECT * FROM " + names.eventData + " FINAL WHERE family='" + family.getTableName() + "')";
    }

    private static String table(String name, String... columns) {
        return "CREATE TABLE IF NOT EXISTS " + name + " (" + String.join(",", columns) + ")";
    }

    private static List<String> eventColumns() {
        List<String> columns = new ArrayList<>(EVENT_COLUMN_DEFINITIONS.length);
        for (String[] definition : EVENT_COLUMN_DEFINITIONS) {
            columns.add(definition[0]);
        }
        return Collections.unmodifiableList(columns);
    }

    private static List<String> eventColumnTypes() {
        return columnTypes(EVENT_COLUMN_DEFINITIONS);
    }

    private static String[] eventColumnDefinitions() {
        return columnDefinitions(EVENT_COLUMN_DEFINITIONS);
    }

    private static List<String> columnTypes(String[][] columns) {
        List<String> types = new ArrayList<>(columns.length);
        for (String[] column : columns) {
            types.add(columnType(column[1]));
        }
        return Collections.unmodifiableList(types);
    }

    private static String columnType(String definition) {
        int end = definition.length();
        int codec = definition.indexOf(" CODEC(");
        int defaultValue = definition.indexOf(" DEFAULT ");
        if (codec >= 0) {
            end = Math.min(end, codec);
        }
        if (defaultValue >= 0) {
            end = Math.min(end, defaultValue);
        }
        return definition.substring(0, end);
    }

    private static String defaultExpression(String definition) {
        int start = definition.indexOf(" DEFAULT ");
        return start < 0 ? null : definition.substring(start + " DEFAULT ".length());
    }

    private static String[] columnDefinitions(String[][] columns) {
        String[] definitions = new String[columns.length];
        for (int index = 0; index < columns.length; index++) {
            definitions[index] = columns[index][0] + " " + columns[index][1];
        }
        return definitions;
    }

    private static String purgeableFamilies() {
        StringJoiner families = new StringJoiner(",");
        for (ClickHouseFamily family : ClickHouseFamily.values()) {
            if (family.isPurgeable()) {
                families.add("'" + family.getTableName() + "'");
            }
        }
        return families.toString();
    }

    private static String normalizeKey(String value) {
        String normalized = normalizeExpression(value);
        if (normalized.startsWith("tuple(") && normalized.endsWith(")")) {
            normalized = normalized.substring("tuple".length());
        }
        if (normalized.startsWith("(") && normalized.endsWith(")")) {
            normalized = normalized.substring(1, normalized.length() - 1);
        }
        return normalized;
    }

    private static String normalizeExpression(String value) {
        String normalized = normalizeSql(value);
        int offset = 0;
        while ((offset = normalized.indexOf("if((", offset)) >= 0) {
            int open = offset + 3;
            int depth = 0;
            int close = -1;
            for (int index = open; index < normalized.length(); index++) {
                char character = normalized.charAt(index);
                if (character == '(') {
                    depth++;
                }
                else if (character == ')' && --depth == 0) {
                    close = index;
                    break;
                }
            }
            if (close < 0 || close + 1 >= normalized.length() || normalized.charAt(close + 1) != ',') {
                offset = open + 1;
                continue;
            }
            normalized = normalized.substring(0, open) + normalized.substring(open + 1, close) + normalized.substring(close + 1);
        }
        return normalized;
    }

    private static String normalizeSql(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        StringBuilder normalized = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (!Character.isWhitespace(character) && character != '`') {
                normalized.append(character);
            }
        }
        return normalized.toString().toLowerCase(Locale.ROOT);
    }

    private static final class Names {

        private final String database;
        private final String prefix;
        private final String eventData;
        private final String retentionHighWater;
        private final String storageMetadata;
        private final String writerRegistration;

        private Names(String database, String prefix) {
            this.database = ClickHouseIdentifiers.quote(database, "ClickHouse database");
            this.prefix = validatePrefix(prefix);
            eventData = table("event_data");
            retentionHighWater = table("retention_high_water");
            storageMetadata = table("storage_metadata");
            writerRegistration = table("writer_registration");
        }

        private String table(String suffix) {
            return database + "." + ClickHouseIdentifiers.quote(rawTable(suffix), "ClickHouse table");
        }

        private String rawTable(String suffix) {
            return prefix + suffix;
        }

        private static String validatePrefix(String prefix) {
            if (prefix == null) {
                throw new IllegalArgumentException("ClickHouse table prefix cannot be null");
            }
            return prefix.isEmpty() ? prefix : ClickHouseIdentifiers.requireIdentifier(prefix, "ClickHouse table prefix");
        }
    }
}
