package net.coreprotect.database;

import java.io.File;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Properties;

import org.duckdb.DuckDBConnection;

import net.coreprotect.config.ConfigHandler;
import net.coreprotect.consumer.process.Process;

final class DuckDBDatabase {

    private static DuckDBConnection rootConnection;

    private DuckDBDatabase() {
        throw new IllegalStateException("Database class");
    }

    static synchronized void open() throws Exception {
        if (rootConnection != null && !rootConnection.isClosed()) {
            return;
        }

        Class.forName("org.duckdb.DuckDBDriver");
        File databaseFile = new File(ConfigHandler.path, ConfigHandler.duckdb);
        File parent = databaseFile.getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }

        Properties properties = new Properties();
        properties.setProperty("memory_limit", ConfigHandler.duckdbMemoryLimit);
        properties.setProperty("threads", Integer.toString(ConfigHandler.duckdbThreads));
        properties.setProperty("temp_directory", databaseFile.getAbsolutePath() + ".tmp");
        properties.setProperty("max_temp_directory_size", ConfigHandler.duckdbMaxTempDirectorySize);
        properties.setProperty("enable_external_access", "false");
        properties.setProperty("allow_unsigned_extensions", "false");
        properties.setProperty("allow_community_extensions", "false");
        properties.setProperty("autoload_known_extensions", "false");
        properties.setProperty("autoinstall_known_extensions", "false");
        rootConnection = (DuckDBConnection) java.sql.DriverManager.getConnection("jdbc:duckdb:" + databaseFile.getAbsolutePath(), properties);
    }

    static synchronized Connection getConnection() throws Exception {
        open();
        return rootConnection.duplicate();
    }

    static synchronized void close() throws Exception {
        DuckDBConnection connection = rootConnection;
        rootConnection = null;
        if (connection != null) {
            connection.close();
        }
    }

    static void createTables(String prefix, Connection forceConnection, boolean purge) throws Exception {
        Connection connection = forceConnection != null ? forceConnection : getConnection();
        try {
            try (Statement statement = connection.createStatement()) {
                for (String table : Database.DATABASE_TABLES) {
                    if (!table.equals("database_lock")) {
                        statement.executeUpdate("CREATE SEQUENCE IF NOT EXISTS " + sequence(prefix, table) + " START 1");
                    }
                }

                statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + prefix + "art_map (" + rowId(prefix, "art_map") + ", id INTEGER, art VARCHAR)");
                statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + prefix + "block (" + rowId(prefix, "block") + ", time INTEGER, \"user\" INTEGER, wid INTEGER, x INTEGER, y INTEGER, z INTEGER, type INTEGER, data INTEGER, meta BLOB, blockdata BLOB, action TINYINT, rolled_back TINYINT)");
                statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + prefix + "chat (" + rowId(prefix, "chat") + ", time INTEGER, \"user\" INTEGER, wid INTEGER, x INTEGER, y INTEGER, z INTEGER, message VARCHAR)");
                statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + prefix + "command (" + rowId(prefix, "command") + ", time INTEGER, \"user\" INTEGER, wid INTEGER, x INTEGER, y INTEGER, z INTEGER, message VARCHAR)");
                statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + prefix + "container (" + rowId(prefix, "container") + ", time INTEGER, \"user\" INTEGER, wid INTEGER, x INTEGER, y INTEGER, z INTEGER, type INTEGER, data INTEGER, amount INTEGER, metadata BLOB, action TINYINT, rolled_back TINYINT)");
                statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + prefix + "entity_container (" + rowId(prefix, "entity_container") + ", time INTEGER, \"user\" INTEGER, entity_spawn_rowid INTEGER NOT NULL, wid INTEGER, x INTEGER, y INTEGER, z INTEGER, type INTEGER, data INTEGER, amount INTEGER, metadata BLOB, action TINYINT, rolled_back TINYINT)");
                statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + prefix + "entity_interaction (" + rowId(prefix, "entity_interaction") + ", time INTEGER, \"user\" INTEGER, entity_spawn_rowid INTEGER NOT NULL, wid INTEGER, x INTEGER, y INTEGER, z INTEGER, type INTEGER, action TINYINT, metadata BLOB, rolled_back TINYINT)");
                statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + prefix + "item (" + rowId(prefix, "item") + ", time INTEGER, \"user\" INTEGER, wid INTEGER, x INTEGER, y INTEGER, z INTEGER, type INTEGER, data BLOB, amount INTEGER, action TINYINT, rolled_back TINYINT)");
                statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + prefix + "database_lock (rowid INTEGER PRIMARY KEY, status TINYINT, time INTEGER)");
                statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + prefix + "entity (" + rowId(prefix, "entity") + ", time INTEGER, data BLOB)");
                statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + prefix + "entity_spawn (" + rowId(prefix, "entity_spawn") + ", time INTEGER, block_rowid BIGINT, kill_rowid INTEGER, uuid VARCHAR UNIQUE, wid INTEGER, current_wid INTEGER, origin_x DOUBLE, origin_y DOUBLE, origin_z DOUBLE, x DOUBLE, y DOUBLE, z DOUBLE, yaw FLOAT, pitch FLOAT, data BLOB, removed TINYINT, UNIQUE(kill_rowid))");
                statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + prefix + "entity_map (" + rowId(prefix, "entity_map") + ", id INTEGER, entity VARCHAR)");
                statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + prefix + "material_map (" + rowId(prefix, "material_map") + ", id INTEGER, material VARCHAR)");
                statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + prefix + "blockdata_map (" + rowId(prefix, "blockdata_map") + ", id INTEGER, data VARCHAR)");
                statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + prefix + "session (" + rowId(prefix, "session") + ", time INTEGER, \"user\" INTEGER, wid INTEGER, x INTEGER, y INTEGER, z INTEGER, action TINYINT)");
                statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + prefix + "sign (" + rowId(prefix, "sign") + ", time INTEGER, \"user\" INTEGER, wid INTEGER, x INTEGER, y INTEGER, z INTEGER, action TINYINT, color INTEGER, color_secondary INTEGER, data TINYINT, waxed TINYINT, face TINYINT, line_1 VARCHAR, line_2 VARCHAR, line_3 VARCHAR, line_4 VARCHAR, line_5 VARCHAR, line_6 VARCHAR, line_7 VARCHAR, line_8 VARCHAR)");
                statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + prefix + "skull (" + rowId(prefix, "skull") + ", time INTEGER, owner VARCHAR, skin VARCHAR)");
                statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + prefix + "user (" + rowId(prefix, "user") + ", time INTEGER, \"user\" VARCHAR, uuid VARCHAR)");
                statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + prefix + "username_log (" + rowId(prefix, "username_log") + ", time INTEGER, uuid VARCHAR, \"user\" VARCHAR)");
                statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + prefix + "version (" + rowId(prefix, "version") + ", time INTEGER, version VARCHAR)");
                statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + prefix + "world (" + rowId(prefix, "world") + ", id INTEGER, world VARCHAR)");

                if (!purge) {
                    initialize(prefix, statement);
                }
            }
        }
        finally {
            if (forceConnection == null) {
                connection.close();
            }
        }
    }

    private static void initialize(String prefix, Statement statement) throws Exception {
        try (ResultSet resultSet = statement.executeQuery("SELECT 1 FROM " + prefix + "database_lock WHERE rowid=1 LIMIT 1")) {
            if (!resultSet.next()) {
                int timestamp = (int) (System.currentTimeMillis() / 1000L);
                statement.executeUpdate("INSERT INTO " + prefix + "database_lock (rowid,status,time) VALUES (1,0," + timestamp + ")");
                Process.lastLockUpdate = 0;
            }
        }
    }

    private static String rowId(String prefix, String table) {
        String type = table.equals("block") ? "BIGINT" : "INTEGER";
        return "rowid " + type + " NOT NULL DEFAULT nextval('" + sequence(prefix, table) + "')";
    }

    private static String sequence(String prefix, String table) {
        return prefix + table + "_rowid_seq";
    }
}
