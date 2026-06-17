package net.coreprotect.database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import net.coreprotect.config.Config;
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.consumer.Consumer;
import net.coreprotect.consumer.Queue;
import net.coreprotect.consumer.process.Process;
import net.coreprotect.language.Phrase;
import net.coreprotect.model.BlockGroup;
import net.coreprotect.utility.Chat;
import net.coreprotect.utility.Color;
import net.coreprotect.utility.ItemUtils;
import net.coreprotect.utility.ErrorReporter;

public class Database extends Queue {

    public static final int SIGN = 0;
    public static final int BLOCK = 1;
    public static final int SKULL = 2;
    public static final int CONTAINER = 3;
    public static final int WORLD = 4;
    public static final int CHAT = 5;
    public static final int COMMAND = 6;
    public static final int SESSION = 7;
    public static final int ENTITY = 8;
    public static final int MATERIAL = 9;
    public static final int ART = 10;
    public static final int ENTITY_MAP = 11;
    public static final int BLOCKDATA = 12;
    public static final int ITEM = 13;

    private static final Map<Integer, String> SQL_QUERIES = new HashMap<>();

    static {
        // Initialize SQL queries for different table types
        SQL_QUERIES.put(SIGN, "INSERT INTO %sprefix%sign (time, user, wid, x, y, z, action, color, color_secondary, data, waxed, face, line_1, line_2, line_3, line_4, line_5, line_6, line_7, line_8, rowid) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
        SQL_QUERIES.put(BLOCK, "INSERT INTO %sprefix%block (time, user, wid, x, y, z, type, data, meta, blockdata, action, rolled_back, rowid) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
        SQL_QUERIES.put(SKULL, "INSERT INTO %sprefix%skull (time, owner, skin, rowid) VALUES (?, ?, ?, ?)");
        SQL_QUERIES.put(CONTAINER, "INSERT INTO %sprefix%container (time, user, wid, x, y, z, type, data, amount, metadata, action, rolled_back, rowid) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
        SQL_QUERIES.put(ITEM, "INSERT INTO %sprefix%item (time, user, wid, x, y, z, type, data, amount, action, rolled_back, rowid) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
        SQL_QUERIES.put(WORLD, "INSERT INTO %sprefix%world (id, world, rowid) VALUES (?, ?, ?)");
        SQL_QUERIES.put(CHAT, "INSERT INTO %sprefix%chat (time, user, wid, x, y, z, message, cancelled, rowid) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)");
        SQL_QUERIES.put(COMMAND, "INSERT INTO %sprefix%command (time, user, wid, x, y, z, message, cancelled, rowid) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)");
        SQL_QUERIES.put(SESSION, "INSERT INTO %sprefix%session (time, user, wid, x, y, z, action, rowid) VALUES (?, ?, ?, ?, ?, ?, ?, ?)");
        SQL_QUERIES.put(ENTITY, "INSERT INTO %sprefix%entity (time, data, rowid) VALUES (?, ?, ?)");
        SQL_QUERIES.put(MATERIAL, "INSERT INTO %sprefix%material_map (id, material, rowid) VALUES (?, ?, ?)");
        SQL_QUERIES.put(ART, "INSERT INTO %sprefix%art_map (id, art, rowid) VALUES (?, ?, ?)");
        SQL_QUERIES.put(ENTITY_MAP, "INSERT INTO %sprefix%entity_map (id, entity, rowid) VALUES (?, ?, ?)");
        SQL_QUERIES.put(BLOCKDATA, "INSERT INTO %sprefix%blockdata_map (id, data, rowid) VALUES (?, ?, ?)");
    }

    public static void beginTransaction(Statement statement, boolean isMySQL) {
        if (true) return; // CH - transactions don't exist
        Consumer.transacting = true;

        try {
            if (isMySQL) {
                statement.executeUpdate("START TRANSACTION");
            }
            else {
                statement.executeUpdate("BEGIN TRANSACTION");
            }
        }
        catch (Exception e) {
            ErrorReporter.report(e);
        }
    }

    public static void commitTransaction(Statement statement, boolean isMySQL) throws Exception {
        if (true) return; // CH - transactions don't exist
        int count = 0;

        while (true) {
            try {
                if (isMySQL) {
                    statement.executeUpdate("COMMIT");
                }
                else {
                    statement.executeUpdate("COMMIT TRANSACTION");
                }
            }
            catch (Exception e) {
                if (e.getMessage().startsWith("[SQLITE_BUSY]") && count < 30) {
                    Thread.sleep(1000);
                    count++;

                    continue;
                }
                else {
                    ErrorReporter.report(e);
                }
            }

            Consumer.transacting = false;
            Consumer.interrupt = false;
            return;
        }
    }

    public static void setMultiInt(PreparedStatement statement, int value, int count) {
        try {
            for (int i = 1; i <= count; i++) {
                statement.setInt(i, value);
            }
        }
        catch (Exception e) {
            ErrorReporter.report(e);
        }
    }

    public static boolean hasReturningKeys() {
        return false;
    }

    public static void containerBreakCheck(String user, Material type, Object container, ItemStack[] contents, Location location) {
        if (BlockGroup.CONTAINERS.contains(type) && !BlockGroup.SHULKER_BOXES.contains(type)) {
            if (Config.getConfig(location.getWorld()).ITEM_TRANSACTIONS) {
                try {
                    if (contents == null) {
                        contents = ItemUtils.getContainerContents(type, container, location);
                    }
                    if (contents != null) {
                        List<ItemStack[]> forceList = new ArrayList<>();
                        forceList.add(ItemUtils.getContainerState(contents));
                        ConfigHandler.forceContainer.put(user.toLowerCase(Locale.ROOT) + "." + location.getBlockX() + "." + location.getBlockY() + "." + location.getBlockZ(), forceList);
                        Queue.queueContainerBreak(user, location, type, contents);
                    }
                }
                catch (Exception e) {
                    ErrorReporter.report(e);
                }
            }
        }
    }

    public static Connection getConnection(boolean onlyCheckTransacting) {
        // Previously 250ms; long consumer commit time may be due to batching (investigate removing batching for SQLite connections)
        return getConnection(false, false, onlyCheckTransacting, 1000);
    }

    public static Connection getConnection(boolean force, int waitTime) {
        return getConnection(force, false, false, waitTime);
    }

    public static Connection getConnection(boolean force, boolean startup, boolean onlyCheckTransacting, int waitTime) {
        Connection connection = null;
        try {
            if (!force && (ConfigHandler.converterRunning || ConfigHandler.purgeRunning)) {
                return connection;
            }
            if (Config.getGlobal().MYSQL) {
                try {
                    connection = ConfigHandler.hikariDataSource.getConnection();
                    ConfigHandler.databaseReachable = true;
                }
                catch (Exception e) {
                    ConfigHandler.databaseReachable = false;
                    Chat.sendConsoleMessage(Color.RED + "[CoreProtect] " + Phrase.build(Phrase.MYSQL_UNAVAILABLE));
                    ErrorReporter.report(e);
                }
            }
            else {
                if (Consumer.transacting && onlyCheckTransacting) {
                    Consumer.interrupt = true;
                }

                long startTime = System.nanoTime();
                while (Consumer.isPaused && !force && (Consumer.transacting || !onlyCheckTransacting)) {
                    Thread.sleep(1);
                    long pauseTime = (System.nanoTime() - startTime) / 1000000;

                    if (pauseTime >= waitTime) {
                        return connection;
                    }
                }

                String database = "jdbc:sqlite:" + ConfigHandler.path + ConfigHandler.sqlite + "";
                connection = DriverManager.getConnection(database);

                ConfigHandler.databaseReachable = true;
            }
        }
        catch (Exception e) {
            ErrorReporter.report(e);
        }

        return connection;
    }

    public static void closeConnection() {
        try {
            if (ConfigHandler.hikariDataSource != null) {
                ConfigHandler.hikariDataSource.close();
                ConfigHandler.hikariDataSource = null;
            }
        }
        catch (Exception e) {
            ErrorReporter.report(e);
        }
    }

    @Deprecated(since = "clickhouse")
    public static PreparedStatement prepareStatement(Connection connection, int type, boolean keys) {
        if (keys) {
            throw new UnsupportedOperationException("Returning keys is not supported in clickhouse, FIXME!");
        }

        return prepareStatement(connection, type);
    }

    public static PreparedStatement prepareStatement(Connection connection, int type) {
        PreparedStatement preparedStatement = null;
        try {
            String query = SQL_QUERIES.get(type);
            if (query != null) {
                query = query.replace("%sprefix%", ConfigHandler.prefix);
                preparedStatement = connection.prepareStatement(query);
            }
        }
        catch (Exception e) {
            ErrorReporter.report(e);
        }

        return preparedStatement;
    }

    private static void initializeTables(String prefix, Statement statement) {
        try {
            boolean lockInitialized = false;
            String query = "SELECT rowid as id FROM " + prefix + "database_lock WHERE rowid='1' LIMIT 1";
            ResultSet rs = statement.executeQuery(query);
            while (rs.next()) {
                lockInitialized = true;
            }
            rs.close();

            if (!lockInitialized) {
                int unixtimestamp = (int) (System.currentTimeMillis() / 1000L);
                statement.executeUpdate("INSERT INTO " + prefix + "database_lock (status, time, rowid) VALUES ('1', '0', '" + unixtimestamp + "')");
                Process.lastLockUpdate = 0;
            }
        }
        catch (Exception e) {
            ErrorReporter.report(e);
        }
    }

    private static final List<String> DATABASE_TABLES = Arrays.asList("art_map", "block", "chat", "command", "container", "item", "database_lock", "entity", "entity_map", "material_map", "blockdata_map", "session", "sign", "skull", "user", "username_log", "version", "world");

    public static void createDatabaseTables(String prefix, boolean forcePrefix, Connection forceConnection, boolean mySQL, boolean purge) {
        ConfigHandler.databaseTables.clear();
        ConfigHandler.databaseTables.addAll(DATABASE_TABLES);

        createMySQLTables(prefix, forceConnection, purge);
    }

    private static void createMySQLTables(String prefix, Connection forceConnection, boolean purge) {
        boolean success = false;
        try (Connection connection = (forceConnection != null ? forceConnection : Database.getConnection(true, true, true, 0))) {
            if (connection != null) {
                Statement statement = connection.createStatement();
                createTableStructures(prefix, statement);
                if (!purge && forceConnection == null) {
                    initializeTables(prefix, statement);
                }
                statement.close();
                success = true;
            }
        }
        catch (Exception e) {
            ErrorReporter.report(e);
        }
        if (!success && forceConnection == null) {
            throw new IllegalStateException("Failed to create default database structure, see error(s) above for details.");
        }
    }

    private static void createTableStructures(String prefix, Statement statement) throws SQLException {
        String orderBy;
        final String partitionBy = "PARTITION BY " + Config.getGlobal().PARTITIONING;

        // Art map
        orderBy = "ORDER BY rowid";
        statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + prefix + "art_map(rowid UInt64, id UInt32, art LowCardinality(String)) ENGINE = MergeTree " + orderBy);

        // Block
        orderBy = "ORDER BY (wid, y, x, z, time, user, type)";
        statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + prefix + "block(rowid UInt64, time UInt32, user UInt32, wid UInt32, x Int32, y Int16, z Int32, type UInt32, data Int64, meta JSON, blockdata LowCardinality(String), action UInt8, rolled_back UInt8, version UInt8) ENGINE = ReplacingMergeTree(version) " + orderBy + partitionBy);

        // Chat
        orderBy = "ORDER BY (user, time, wid, x, z)";
        statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + prefix + "chat(rowid UInt64, time UInt32, user UInt32, wid UInt32, x Int32, y Int16, z Int32, message String, cancelled Bool) ENGINE = MergeTree " + orderBy);

        // Command
        orderBy = "ORDER BY (user, time, wid, x, z)";
        statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + prefix + "command(rowid UInt64, time UInt32, user UInt32, wid UInt32, x Int32, y Int16, z Int32, message String, cancelled Bool) ENGINE = MergeTree " + orderBy);

        // Container
        orderBy = "ORDER BY (wid, y, x, z, time, user, type)";
        statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + prefix + "container(rowid UInt64, time UInt32, user UInt32, wid UInt32, x Int32, y Int16, z Int32, type UInt32, data UInt32, amount UInt32, metadata JSON, action UInt8, rolled_back UInt8, version UInt8) ENGINE = ReplacingMergeTree(version) " + orderBy + partitionBy);

        // Item
        orderBy = "ORDER BY (wid, y, x, z, time, user, type)";
        statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + prefix + "item(rowid UInt64, time UInt32, user UInt32, wid UInt32, x Int32, y Int16, z Int32, type UInt32, data JSON, amount UInt32, action UInt8, rolled_back UInt8, version UInt8) ENGINE = ReplacingMergeTree(version) " + orderBy + partitionBy);

        // Database lock
        orderBy = "ORDER BY rowid";
        statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + prefix + "database_lock(rowid UInt64, status UInt8, time UInt32) ENGINE = MergeTree " + orderBy);

        // Entity
        orderBy = "ORDER BY (rowid)";
        statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + prefix + "entity(rowid UInt64, time UInt32, data String) ENGINE = MergeTree " + orderBy + partitionBy);

        // Entity map
        orderBy = "ORDER BY rowid";
        statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + prefix + "entity_map(rowid UInt64, id UInt32, entity String) ENGINE = MergeTree " + orderBy);

        // Material map
        orderBy = "ORDER BY rowid";
        statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + prefix + "material_map(rowid UInt64, id UInt32, material LowCardinality(String)) ENGINE = MergeTree " + orderBy);

        // Blockdata map
        orderBy = "ORDER BY rowid";
        statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + prefix + "blockdata_map(rowid UInt64, id UInt32, data LowCardinality(String)) ENGINE = MergeTree " + orderBy);

        // Session
        orderBy = "ORDER BY (user, time, wid, x, z)";
        statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + prefix + "session(rowid UInt64, time UInt32, user UInt32, wid UInt32, x Int32, y Int16, z Int32, action Bool) ENGINE = MergeTree " + orderBy);

        // Sign
        orderBy = "ORDER BY (wid, x, z, time)";
        statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + prefix + "sign(rowid UInt64, time UInt32, user UInt32, wid UInt32, x Int32, y Int16, z Int32, action UInt8, color UInt32, color_secondary UInt32, data UInt8, waxed UInt8, face UInt8, line_1 String, line_2 String, line_3 String, line_4 String, line_5 String, line_6 String, line_7 String, line_8 String) ENGINE = MergeTree " + orderBy);

        // Skull
        orderBy = "ORDER BY rowid";
        statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + prefix + "skull(rowid UInt64, time UInt32, owner String, skin String) ENGINE = MergeTree " + orderBy);

        // User
        orderBy = "ORDER BY rowid";
        statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + prefix + "user(rowid UInt64, time UInt32, user String, uuid UUID) ENGINE = MergeTree " + orderBy);

        // Username log
        orderBy = "ORDER BY rowid";
        statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + prefix + "username_log(rowid UInt64, time UInt32, uuid UUID, user String) ENGINE = MergeTree " + orderBy);

        // Version
        orderBy = "ORDER BY rowid";
        statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + prefix + "version(rowid UInt64, time UInt32, version String) ENGINE = MergeTree " + orderBy);

        // World
        orderBy = "ORDER BY rowid";
        statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + prefix + "world(rowid UInt64, id UInt32, world LowCardinality(String)) ENGINE = MergeTree " + orderBy);
    }
}
