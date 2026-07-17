package net.coreprotect.command;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import net.coreprotect.CoreProtect;
import net.coreprotect.config.Config;
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.consumer.Consumer;
import net.coreprotect.consumer.process.Process;
import net.coreprotect.database.Database;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public final class PlayProMigrationCommand {

    private static final String MIGRATION_RESOURCE = "/migration/playpro-clickhouse-migration.sql";
    private static final List<String> ARCHIVE_TABLES = List.of(
            "art_map", "block", "chat", "command", "container", "entity_container",
            "entity_interaction", "item", "database_lock", "entity", "entity_spawn",
            "entity_map", "material_map", "blockdata_map", "session", "sign", "skull",
            "user", "username_log", "version", "world");

    private PlayProMigrationCommand() {
        throw new IllegalStateException("Command class");
    }

    protected static void runCommand(CommandSender sender, boolean permission, String[] args) {
        if (!permission) {
            return;
        }
        if (sender instanceof Player) {
            error(sender, "This migration can only be executed from console.");
            return;
        }
        if (!Config.getGlobal().MYSQL) {
            error(sender, "This migration is only available for the ClickHouse storage backend.");
            return;
        }
        if (ConfigHandler.converterRunning || ConfigHandler.migrationRunning || ConfigHandler.purgeRunning) {
            error(sender, "Another database operation is already running.");
            return;
        }
        if (!ConfigHandler.activeRollbacks.isEmpty()) {
            error(sender, "A rollback/restore is currently active. Try again later.");
            return;
        }
        if (!Bukkit.getOnlinePlayers().isEmpty()) {
            error(sender, "Kick all players or run during maintenance before migrating.");
            return;
        }

        MigrationOptions options;
        try {
            options = MigrationOptions.parse(args);
        }
        catch (IllegalArgumentException e) {
            error(sender, e.getMessage());
            usage(sender);
            return;
        }

        if (options.archivePrefix.equals(options.livePrefix)) {
            error(sender, "Archive prefix cannot be the same as the live prefix.");
            return;
        }

        ConfigHandler.migrationRunning = true;
        Thread migrationThread = new Thread(() -> runMigration(sender, options));
        migrationThread.setName("CoreProtect PlayPro Migration");
        migrationThread.setUncaughtExceptionHandler((thread, throwable) -> {
            CoreProtect.getInstance().getSLF4JLogger().error("Unhandled PlayPro migration failure", throwable);
            ConfigHandler.pauseConsumer = false;
            ConfigHandler.migrationRunning = false;
            error(sender, "Migration failed unexpectedly. See console for details.");
        });
        migrationThread.start();
        ok(sender, "Started in-place PlayPro migration in " + options.database + ". Old tables will be archived as " + options.archivePrefix + "*.");
    }

    private static void runMigration(CommandSender sender, MigrationOptions options) {
        boolean success = false;
        try {
            waitForConsumerDrain(sender);
            ConfigHandler.pauseConsumer = true;
            ok(sender, "Logging is paused. Do not let players join until you switch to official PlayPro/CoreProtect.");

            try (Connection connection = Database.getConnection(true, 30000)) {
                if (connection == null) {
                    throw new SQLException("Unable to open ClickHouse connection");
                }
                requireClickHouseVersion(connection);
                requireSourceTables(connection, options);
                requireArchiveTablesFree(connection, options);
                requireTargetDatabaseEngine(connection, options);
                bootstrapTargetSchema(connection, options);
                requireTargetReady(connection, options);
                archiveSourceTables(connection, options);
                runMigrationSql(connection, options, sender);
            }

            success = true;
            ok(sender, "Migration completed successfully.");
            ok(sender, "Stop the server now, replace this jar with official PlayPro/CoreProtect, and keep clickhouse-database: " + options.database + ", table-prefix: " + options.livePrefix);
        }
        catch (Exception e) {
            CoreProtect.getInstance().getSLF4JLogger().error("PlayPro migration failed", e);
            error(sender, "Migration failed: " + e.getMessage());
        }
        finally {
            ConfigHandler.migrationRunning = false;
            if (!success) {
                ConfigHandler.pauseConsumer = false;
            }
        }
    }

    private static void waitForConsumerDrain(CommandSender sender) throws InterruptedException {
        ok(sender, "Waiting for the current consumer queue to drain...");
        long start = System.currentTimeMillis();
        while (Consumer.getConsumerSize(0) > 0 || Consumer.getConsumerSize(1) > 0 || Process.getCurrentConsumerSize() > 0) {
            if (System.currentTimeMillis() - start > 300000L) {
                throw new IllegalStateException("Timed out while waiting for the consumer queue to drain");
            }
            Thread.sleep(500L);
        }
    }

    private static void requireClickHouseVersion(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet resultSet = statement.executeQuery("SELECT version()")) {
            if (!resultSet.next()) {
                throw new SQLException("ClickHouse did not return a version");
            }
            String version = resultSet.getString(1);
            String[] parts = version.split("\\.", 3);
            int major = Integer.parseInt(parts[0]);
            int minor = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
            if (major < 25 || (major == 25 && minor < 6)) {
                throw new SQLException("Official PlayPro/CoreProtect requires ClickHouse 25.6+. Found " + version);
            }
        }
    }

    private static void requireSourceTables(Connection connection, MigrationOptions options) throws SQLException {
        List<String> missing = new ArrayList<>();
        for (String table : ARCHIVE_TABLES) {
            if (!tableExists(connection, options.database, options.livePrefix + table)) {
                missing.add(options.livePrefix + table);
            }
        }
        if (!missing.isEmpty()) {
            throw new SQLException("Source database is missing required tables: " + String.join(", ", missing));
        }
    }

    private static void requireArchiveTablesFree(Connection connection, MigrationOptions options) throws SQLException {
        List<String> existing = new ArrayList<>();
        for (String table : ARCHIVE_TABLES) {
            if (tableExists(connection, options.database, options.archivePrefix + table)) {
                existing.add(options.archivePrefix + table);
            }
        }
        if (!existing.isEmpty()) {
            throw new SQLException("Archive tables already exist: " + String.join(", ", existing));
        }
    }

    private static void bootstrapTargetSchema(Connection connection, MigrationOptions options) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            for (String sql : targetSchema(options)) {
                statement.execute(sql);
            }
        }
    }

    private static void requireTargetDatabaseEngine(Connection connection, MigrationOptions options) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT engine,toString(uuid) FROM system.databases WHERE name=? LIMIT 2")) {
            statement.setString(1, options.database);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new SQLException("Target database does not exist: " + options.database);
                }
                String engine = resultSet.getString(1);
                String uuid = resultSet.getString(2);
                if (resultSet.next()) {
                    throw new SQLException("Target database is ambiguous: " + options.database);
                }
                if ("00000000-0000-0000-0000-000000000000".equals(uuid)) {
                    throw new SQLException("Target database uses engine " + engine + " without persistent UUID-backed table identities. Use ENGINE = Atomic.");
                }
            }
        }
    }

    private static void requireTargetReady(Connection connection, MigrationOptions options) throws SQLException {
        if (!tableExists(connection, options.database, options.livePrefix + "storage_metadata")
                || !tableExists(connection, options.database, options.livePrefix + "event_data")) {
            throw new SQLException("Target PlayPro schema is missing physical tables");
        }

        long identityRows = count(connection, qualified(options.database, options.livePrefix + "storage_metadata"));
        if (identityRows != 1L) {
            throw new SQLException("Target storage metadata must contain exactly one row. Found " + identityRows);
        }

        long eventRows = count(connection, qualified(options.database, options.livePrefix + "event_data"));
        if (eventRows > 0L) {
            throw new SQLException("Target event_data already contains " + eventRows + " rows. Use a fresh target database.");
        }

        long writerRows = count(connection, qualified(options.database, options.livePrefix + "writer_registration"));
        if (writerRows > 0L) {
            throw new SQLException("Target writer_registration already contains " + writerRows + " rows. Stop official PlayPro before migrating.");
        }

        long highWaterRows = count(connection, qualified(options.database, options.livePrefix + "retention_high_water"));
        if (highWaterRows > 0L) {
            throw new SQLException("Target retention_high_water already contains " + highWaterRows + " rows. Use a fresh target schema.");
        }
    }

    private static void archiveSourceTables(Connection connection, MigrationOptions options) throws SQLException {
        String renameSql = ARCHIVE_TABLES.stream()
                .map(table -> qualified(options.database, options.livePrefix + table) + " TO " + qualified(options.database, options.archivePrefix + table))
                .collect(Collectors.joining(", "));
        try (Statement statement = connection.createStatement()) {
            statement.execute("RENAME TABLE " + renameSql);
        }
        for (String table : ARCHIVE_TABLES) {
            CoreProtect.getInstance().getSLF4JLogger().info("[PlayPro migration] Archived {} as {}",
                    qualified(options.database, options.livePrefix + table), qualified(options.database, options.archivePrefix + table));
        }
    }

    private static boolean tableExists(Connection connection, String database, String table) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT 1 FROM system.tables WHERE database=? AND name=? LIMIT 1")) {
            statement.setString(1, database);
            statement.setString(2, table);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    private static long count(Connection connection, String table) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet resultSet = statement.executeQuery("SELECT count() FROM " + table)) {
            if (!resultSet.next()) {
                throw new SQLException("ClickHouse did not return a count for " + table);
            }
            return resultSet.getLong(1);
        }
    }

    private static void runMigrationSql(Connection connection, MigrationOptions options, CommandSender sender) throws Exception {
        String sql = loadMigrationSql(options);
        int index = 0;
        for (String rawStatement : splitStatements(sql)) {
            String statementSql = stripLineComments(rawStatement).trim();
            if (statementSql.isEmpty()) {
                continue;
            }
            index++;
            try (Statement statement = connection.createStatement()) {
                boolean hasResultSet = statement.execute(statementSql);
                if (hasResultSet) {
                    reportResultSet(sender, statement.getResultSet());
                }
                else {
                    CoreProtect.getInstance().getSLF4JLogger().info("PlayPro migration statement {} completed.", index);
                }
            }
        }
    }

    private static void reportResultSet(CommandSender sender, ResultSet resultSet) throws SQLException {
        if (resultSet == null) {
            return;
        }
        int columns = resultSet.getMetaData().getColumnCount();
        int rows = 0;
        while (resultSet.next()) {
            rows++;
            List<String> values = new ArrayList<>(columns);
            for (int i = 1; i <= columns; i++) {
                values.add(String.valueOf(resultSet.getObject(i)));
            }
            String line = String.join(" | ", values);
            CoreProtect.getInstance().getSLF4JLogger().info("[PlayPro migration] {}", line);
            if (rows <= 25) {
                sender.sendMessage(Component.text(line, NamedTextColor.GRAY));
            }
        }
        resultSet.close();
    }

    private static String loadMigrationSql(MigrationOptions options) throws Exception {
        try (InputStream stream = PlayProMigrationCommand.class.getResourceAsStream(MIGRATION_RESOURCE)) {
            if (stream == null) {
                throw new IllegalStateException("Missing migration resource: " + MIGRATION_RESOURCE);
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String sql = reader.lines().collect(Collectors.joining("\n"));
                sql = sql.replace("kostya.co_", options.database + "." + options.archivePrefix);
                sql = sql.replace("coreprotect_playpro.co_", options.database + "." + options.livePrefix);
                sql = sql.replace("CREATE DATABASE IF NOT EXISTS coreprotect_playpro ENGINE = Atomic;", "");
                return sql;
            }
        }
    }

    private static List<String> splitStatements(String sql) {
        List<String> statements = new ArrayList<>();
        int start = 0;
        boolean quote = false;
        for (int i = 0; i < sql.length(); i++) {
            char c = sql.charAt(i);
            if (c == '\'') {
                if (quote && i + 1 < sql.length() && sql.charAt(i + 1) == '\'') {
                    i++;
                    continue;
                }
                quote = !quote;
            }
            else if (!quote && c == ';') {
                String statement = sql.substring(start, i).trim();
                if (!statement.isEmpty()) {
                    statements.add(statement);
                }
                start = i + 1;
            }
        }
        String tail = sql.substring(start).trim();
        if (!tail.isEmpty()) {
            statements.add(tail);
        }
        return statements;
    }

    private static String stripLineComments(String sql) {
        StringBuilder result = new StringBuilder(sql.length());
        for (String line : sql.split("\\R")) {
            String trimmed = line.trim();
            if (!trimmed.startsWith("--")) {
                result.append(line).append('\n');
            }
        }
        return result.toString();
    }

    private static List<String> targetSchema(MigrationOptions options) {
        String storage = qualified(options.database, options.livePrefix + "storage_metadata");
        String writer = qualified(options.database, options.livePrefix + "writer_registration");
        String highWater = qualified(options.database, options.livePrefix + "retention_high_water");
        String events = qualified(options.database, options.livePrefix + "event_data");

        return List.of(
                """
                CREATE TABLE IF NOT EXISTS %s (
                    dataset_id UUID CODEC(ZSTD(3)),
                    producer_id UUID CODEC(ZSTD(3)),
                    schema_version UInt32 CODEC(Delta, ZSTD(3)),
                    created_at DateTime64(3, 'UTC') CODEC(Delta, ZSTD(3))
                ) ENGINE = MergeTree
                ORDER BY tuple()
                SETTINGS fsync_after_insert=1,fsync_part_directory=1
                """.formatted(storage),
                """
                CREATE TABLE IF NOT EXISTS %s (
                    dataset_id UUID,
                    producer_id UUID,
                    writer_id UUID,
                    registration_order UInt64 DEFAULT generateSnowflakeID(),
                    registered_at DateTime64(3, 'UTC')
                ) ENGINE = MergeTree
                ORDER BY (registration_order,writer_id)
                SETTINGS fsync_after_insert=1,fsync_part_directory=1
                """.formatted(writer),
                """
                CREATE TABLE IF NOT EXISTS %s (
                    dataset_id UUID CODEC(ZSTD(3)),
                    producer_id UUID CODEC(ZSTD(3)),
                    producer_sequence UInt64 CODEC(Delta, ZSTD(3)),
                    family LowCardinality(String) CODEC(ZSTD(3)),
                    rowid UInt64 CODEC(Delta, ZSTD(3)),
                    recorded_at DateTime64(3, 'UTC') CODEC(Delta, ZSTD(3))
                ) ENGINE = MergeTree
                ORDER BY (dataset_id,family,producer_sequence,rowid)
                SETTINGS fsync_after_insert=1,fsync_part_directory=1,non_replicated_deduplication_window=1000
                """.formatted(highWater),
                eventDataTable(events),
                "INSERT INTO " + storage + " (dataset_id,producer_id,schema_version,created_at) "
                        + "SELECT generateUUIDv4(), generateUUIDv4(), 1, now64(3, 'UTC') WHERE (SELECT count() FROM " + storage + ") = 0");
    }

    private static String eventDataTable(String events) {
        return """
                CREATE TABLE IF NOT EXISTS %s (
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
                SETTINGS fsync_after_insert=1,fsync_part_directory=1,non_replicated_deduplication_window=1000
                """.formatted(events);
    }

    private static String qualified(String database, String table) {
        return quote(database) + "." + quote(table);
    }

    private static String quote(String identifier) {
        return "`" + identifier + "`";
    }

    private static void ok(CommandSender sender, String message) {
        sender.sendMessage(Component.text(message, NamedTextColor.GREEN));
        CoreProtect.getInstance().getSLF4JLogger().info("[PlayPro migration] {}", message);
    }

    private static void error(CommandSender sender, String message) {
        sender.sendMessage(Component.text(message, NamedTextColor.RED));
        CoreProtect.getInstance().getSLF4JLogger().warn("[PlayPro migration] {}", message);
    }

    private static void usage(CommandSender sender) {
        sender.sendMessage(Component.text("Usage: /co migrate-playpro [database:kostya] [prefix:co_] [archive-prefix:co_migrate_]", NamedTextColor.YELLOW));
    }

    private record MigrationOptions(String database, String livePrefix, String archivePrefix) {

        private static MigrationOptions parse(String[] commandArgs) {
            String database = ConfigHandler.database;
            String livePrefix = ConfigHandler.prefix;
            String archivePrefix = livePrefix + "migrate_";

            for (int i = 1; i < commandArgs.length; i++) {
                String arg = commandArgs[i].trim();
                if (arg.isEmpty()) {
                    continue;
                }
                String[] split = arg.split(":", 2);
                if (split.length != 2) {
                    throw new IllegalArgumentException("Invalid argument: " + arg);
                }
                String key = split[0].toLowerCase(Locale.ROOT);
                String value = split[1];
                switch (key) {
                    case "database" -> database = value;
                    case "prefix" -> {
                        livePrefix = value;
                        archivePrefix = value + "migrate_";
                    }
                    case "archive-prefix" -> archivePrefix = value;
                    default -> throw new IllegalArgumentException("Unknown argument: " + key);
                }
            }

            validateIdentifier(database, "database");
            validatePrefix(livePrefix, "prefix");
            validatePrefix(archivePrefix, "archive prefix");
            return new MigrationOptions(database, livePrefix, archivePrefix);
        }

        private static void validateIdentifier(String value, String name) {
            if (value == null || !value.matches("[A-Za-z_][A-Za-z0-9_]*")) {
                throw new IllegalArgumentException("Invalid " + name + ": " + value);
            }
        }

        private static void validatePrefix(String value, String name) {
            if (value == null) {
                throw new IllegalArgumentException("Invalid " + name + ": null");
            }
            if (!value.isEmpty() && !value.matches("[A-Za-z_][A-Za-z0-9_]*")) {
                throw new IllegalArgumentException("Invalid " + name + ": " + value);
            }
        }
    }
}
