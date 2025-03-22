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
import net.coreprotect.utility.MaterialUtils;

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
        SQL_QUERIES.put(SIGN, "INSERT INTO %sprefix%sign (time, user, wid, x, y, z, action, color, color_secondary, data, waxed, face, line_1, line_2, line_3, line_4, line_5, line_6, line_7, line_8) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
        SQL_QUERIES.put(BLOCK, "INSERT INTO %sprefix%block (time, user, wid, x, y, z, type, data, meta, blockdata, action, rolled_back) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
        SQL_QUERIES.put(SKULL, "INSERT INTO %sprefix%skull (time, owner, skin) VALUES (?, ?, ?)");
        SQL_QUERIES.put(CONTAINER, "INSERT INTO %sprefix%container (time, user, wid, x, y, z, type, data, amount, metadata, action, rolled_back) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
        SQL_QUERIES.put(ITEM, "INSERT INTO %sprefix%item (time, user, wid, x, y, z, type, data, amount, action, rolled_back) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
        SQL_QUERIES.put(WORLD, "INSERT INTO %sprefix%world (id, world) VALUES (?, ?)");
        SQL_QUERIES.put(CHAT, "INSERT INTO %sprefix%chat (time, user, wid, x, y, z, message) VALUES (?, ?, ?, ?, ?, ?, ?)");
        SQL_QUERIES.put(COMMAND, "INSERT INTO %sprefix%command (time, user, wid, x, y, z, message) VALUES (?, ?, ?, ?, ?, ?, ?)");
        SQL_QUERIES.put(SESSION, "INSERT INTO %sprefix%session (time, user, wid, x, y, z, action) VALUES (?, ?, ?, ?, ?, ?, ?)");
        SQL_QUERIES.put(ENTITY, "INSERT INTO %sprefix%entity (time, data) VALUES (?, ?)");
        SQL_QUERIES.put(MATERIAL, "INSERT INTO %sprefix%material_map (id, material) VALUES (?, ?)");
        SQL_QUERIES.put(ART, "INSERT INTO %sprefix%art_map (id, art) VALUES (?, ?)");
        SQL_QUERIES.put(ENTITY_MAP, "INSERT INTO %sprefix%entity_map (id, entity) VALUES (?, ?)");
        SQL_QUERIES.put(BLOCKDATA, "INSERT INTO %sprefix%blockdata_map (id, data) VALUES (?, ?)");
    }

    public static void beginTransaction(Statement statement, boolean isMySQL) {
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
            e.printStackTrace();
        }
    }

    public static void commitTransaction(Statement statement, boolean isMySQL) throws Exception {
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
                    e.printStackTrace();
                }
            }

            Consumer.transacting = false;
            Consumer.interrupt = false;
            return;
        }
    }

    public static void performCheckpoint(Statement statement, boolean isMySQL) throws SQLException {
        if (!isMySQL) {
            statement.executeUpdate("PRAGMA wal_checkpoint(TRUNCATE)");
        }
    }

    public static void setMultiInt(PreparedStatement statement, int value, int count) {
        try {
            for (int i = 1; i <= count; i++) {
                statement.setInt(i, value);
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static boolean hasReturningKeys() {
        return (!Config.getGlobal().MYSQL && ConfigHandler.SERVER_VERSION >= 20);
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
                    e.printStackTrace();
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
                    e.printStackTrace();
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
            e.printStackTrace();
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
            e.printStackTrace();
        }
    }

    public static void performUpdate(Statement statement, long id, int rb, int table) {
        try {
            int rolledBack = MaterialUtils.toggleRolledBack(rb, (table == 2 || table == 3 || table == 4)); // co_item, co_container, co_block
            if (table == 1 || table == 3) {
                statement.executeUpdate("UPDATE " + ConfigHandler.prefix + "container SET rolled_back='" + rolledBack + "' WHERE rowid='" + id + "'");
            }
            else if (table == 2) {
                statement.executeUpdate("UPDATE " + ConfigHandler.prefix + "item SET rolled_back='" + rolledBack + "' WHERE rowid='" + id + "'");
            }
            else {
                statement.executeUpdate("UPDATE " + ConfigHandler.prefix + "block SET rolled_back='" + rolledBack + "' WHERE rowid='" + id + "'");
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static PreparedStatement prepareStatement(Connection connection, int type, boolean keys) {
        PreparedStatement preparedStatement = null;
        try {
            String query = SQL_QUERIES.get(type);
            if (query != null) {
                query = query.replace("%sprefix%", ConfigHandler.prefix);
                preparedStatement = prepareStatement(connection, query, keys);
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        return preparedStatement;
    }

    private static PreparedStatement prepareStatement(Connection connection, String query, boolean keys) {
        PreparedStatement preparedStatement = null;
        try {
            if (keys) {
                if (hasReturningKeys()) {
                    preparedStatement = connection.prepareStatement(query + " RETURNING rowid");
                }
                else {
                    preparedStatement = connection.prepareStatement(query, Statement.RETURN_GENERATED_KEYS);
                }
            }
            else {
                preparedStatement = connection.prepareStatement(query);
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        return preparedStatement;
    }

    private static void initializeTables(String prefix, Statement statement) {
        try {
            if (!Config.getGlobal().MYSQL) {
                if (!Config.getGlobal().DISABLE_WAL) {
                    statement.executeUpdate("PRAGMA journal_mode=WAL;");
                }
                else {
                    statement.executeUpdate("PRAGMA journal_mode=DELETE;");
                }
            }

            boolean lockInitialized = false;
            String query = "SELECT rowid as id FROM " + prefix + "database_lock WHERE rowid='1' LIMIT 1";
            ResultSet rs = statement.executeQuery(query);
            while (rs.next()) {
                lockInitialized = true;
            }
            rs.close();

            if (!lockInitialized) {
                int unixtimestamp = (int) (System.currentTimeMillis() / 1000L);
                statement.executeUpdate("INSERT INTO " + prefix + "database_lock (rowid, status, time) VALUES ('1', '0', '" + unixtimestamp + "')");
                Process.lastLockUpdate = 0;
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static final List<String> DATABASE_TABLES = Arrays.asList("art_map", "block", "chat", "command", "container", "item", "database_lock", "entity", "entity_map", "material_map", "blockdata_map", "session", "sign", "skull", "user", "username_log", "version", "world");

    public static void createDatabaseTables(String prefix, boolean forcePrefix, Connection forceConnection, boolean mySQL, boolean purge) {
        ConfigHandler.databaseTables.clear();
        ConfigHandler.databaseTables.addAll(DATABASE_TABLES);

        if (mySQL) {
            createMySQLTables(prefix, forceConnection, purge);
        }
        else {
            createSQLiteTables(prefix, forcePrefix, forceConnection, purge);
        }
    }

    private static void createMySQLTables(String prefix, Connection forceConnection, boolean purge) {
        boolean success = false;
        try (Connection connection = (forceConnection != null ? forceConnection : Database.getConnection(true, true, true, 0))) {
            if (connection != null) {
                Statement statement = connection.createStatement();
                createMySQLTableStructures(prefix, statement);
                if (!purge && forceConnection == null) {
                    initializeTables(prefix, statement);
                }
                statement.close();
                success = true;
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        if (!success && forceConnection == null) {
            Config.getGlobal().MYSQL = false;
        }
    }

    private static void createMySQLTableStructures(String prefix, Statement statement) throws SQLException {
        String index = "";

        // Art map
        index = ", INDEX(id)";
        statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + prefix + "art_map(rowid int NOT NULL AUTO_INCREMENT,PRIMARY KEY(rowid),id int,art varchar(255)" + index + ") ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4");

        // Block
        index = ", INDEX(wid,x,z,time), INDEX(user,time), INDEX(type,time)";
        statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + prefix + "block(rowid bigint NOT NULL AUTO_INCREMENT,PRIMARY KEY(rowid), time int, user int, wid int, x int, y int, z int, type int, data int, meta mediumblob, blockdata blob, action tinyint, rolled_back tinyint" + index + ") ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4");

        // Chat
        index = ", INDEX(time), INDEX(user,time), INDEX(wid,x,z,time)";
        statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + prefix + "chat(rowid int NOT NULL AUTO_INCREMENT,PRIMARY KEY(rowid),time int, user int, wid int, x int, y int (3), z int, message varchar(16000)" + index + ") ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4");

        // Command
        index = ", INDEX(time), INDEX(user,time), INDEX(wid,x,z,time)";
        statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + prefix + "command(rowid int NOT NULL AUTO_INCREMENT,PRIMARY KEY(rowid),time int, user int, wid int, x int, y int (3), z int, message varchar(16000)" + index + ") ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4");

        // Container
        index = ", INDEX(wid,x,z,time), INDEX(user,time), INDEX(type,time)";
        statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + prefix + "container(rowid int NOT NULL AUTO_INCREMENT,PRIMARY KEY(rowid), time int, user int, wid int, x int, y int, z int, type int, data int, amount int, metadata blob, action tinyint, rolled_back tinyint" + index + ") ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4");

        // Item
        index = ", INDEX(wid,x,z,time), INDEX(user,time), INDEX(type,time)";
        statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + prefix + "item(rowid int NOT NULL AUTO_INCREMENT,PRIMARY KEY(rowid), time int, user int, wid int, x int, y int, z int, type int, data blob, amount int, action tinyint, rolled_back tinyint" + index + ") ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4");

        // Database lock
        statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + prefix + "database_lock(rowid int NOT NULL AUTO_INCREMENT,PRIMARY KEY(rowid),status tinyint,time int) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4");

        // Entity
        statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + prefix + "entity(rowid int NOT NULL AUTO_INCREMENT,PRIMARY KEY(rowid), time int, data blob) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4");

        // Entity map
        index = ", INDEX(id)";
        statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + prefix + "entity_map(rowid int NOT NULL AUTO_INCREMENT,PRIMARY KEY(rowid),id int,entity varchar(255)" + index + ") ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4");

        // Material map
        index = ", INDEX(id)";
        statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + prefix + "material_map(rowid int NOT NULL AUTO_INCREMENT,PRIMARY KEY(rowid),id int,material varchar(255)" + index + ") ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4");

        // Blockdata map
        index = ", INDEX(id)";
        statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + prefix + "blockdata_map(rowid int NOT NULL AUTO_INCREMENT,PRIMARY KEY(rowid),id int,data varchar(255)" + index + ") ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4");

        // Session
        index = ", INDEX(wid,x,z,time), INDEX(action,time), INDEX(user,time), INDEX(time)";
        statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + prefix + "session(rowid int NOT NULL AUTO_INCREMENT,PRIMARY KEY(rowid),time int, user int, wid int, x int, y int (3), z int, action tinyint" + index + ") ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4");

        // Sign
        index = ", INDEX(wid,x,z,time), INDEX(user,time), INDEX(time)";
        statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + prefix + "sign(rowid int NOT NULL AUTO_INCREMENT,PRIMARY KEY(rowid),time int, user int, wid int, x int, y int, z int, action tinyint, color int, color_secondary int, data tinyint, waxed tinyint, face tinyint, line_1 varchar(100), line_2 varchar(100), line_3 varchar(100), line_4 varchar(100), line_5 varchar(100), line_6 varchar(100), line_7 varchar(100), line_8 varchar(100)" + index + ") ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4");

        // Skull
        statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + prefix + "skull(rowid int NOT NULL AUTO_INCREMENT,PRIMARY KEY(rowid), time int, owner varchar(255), skin varchar(255)) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4");

        // User
        index = ", INDEX(user), INDEX(uuid)";
        statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + prefix + "user(rowid int NOT NULL AUTO_INCREMENT,PRIMARY KEY(rowid),time int,user varchar(100),uuid varchar(64)" + index + ") ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4");

        // Username log
        index = ", INDEX(uuid,user)";
        statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + prefix + "username_log(rowid int NOT NULL AUTO_INCREMENT,PRIMARY KEY(rowid),time int,uuid varchar(64),user varchar(100)" + index + ") ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4");

        // Version
        statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + prefix + "version(rowid int NOT NULL AUTO_INCREMENT,PRIMARY KEY(rowid),time int,version varchar(16)) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4");

        // World
        index = ", INDEX(id)";
        statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + prefix + "world(rowid int NOT NULL AUTO_INCREMENT,PRIMARY KEY(rowid),id int,world varchar(255)" + index + ") ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4");
    }

    private static void createSQLiteTables(String prefix, boolean forcePrefix, Connection forceConnection, boolean purge) {
        try (Connection connection = (forceConnection != null ? forceConnection : Database.getConnection(true, 0))) {
            Statement statement = connection.createStatement();
            List<String> tableData = new ArrayList<>();
            List<String> indexData = new ArrayList<>();
            String attachDatabase = "";

            if (purge && forceConnection == null) {
                String query = "ATTACH DATABASE '" + ConfigHandler.path + ConfigHandler.sqlite + ".tmp' AS tmp_db";
                PreparedStatement preparedStmt = connection.prepareStatement(query);
                preparedStmt.execute();
                preparedStmt.close();
                attachDatabase = "tmp_db.";
            }

            identifyExistingTablesAndIndexes(statement, attachDatabase, tableData, indexData);
            createSQLiteTableStructures(prefix, statement, tableData);
            createSQLiteIndexes(forcePrefix == true ? prefix : ConfigHandler.prefix, statement, indexData, attachDatabase, purge);

            if (!purge && forceConnection == null) {
                initializeTables(prefix, statement);
            }
            statement.close();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void identifyExistingTablesAndIndexes(Statement statement, String attachDatabase, List<String> tableData, List<String> indexData) throws SQLException {
        String query = "SELECT type,name FROM " + attachDatabase + "sqlite_master WHERE type='table' OR type='index';";
        ResultSet rs = statement.executeQuery(query);
        while (rs.next()) {
            String type = rs.getString("type");
            if (type.equalsIgnoreCase("table")) {
                tableData.add(rs.getString("name"));
            }
            else if (type.equalsIgnoreCase("index")) {
                indexData.add(rs.getString("name"));
            }
        }
        rs.close();
    }

    private static void createSQLiteTableStructures(String prefix, Statement statement, List<String> tableData) throws SQLException {
        if (!tableData.contains(prefix + "art_map")) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + prefix + "art_map (id INTEGER, art TEXT);");
        }
        if (!tableData.contains(prefix + "block")) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + prefix + "block (time INTEGER, user INTEGER, wid INTEGER, x INTEGER, y INTEGER, z INTEGER, type INTEGER, data INTEGER, meta BLOB, blockdata BLOB, action INTEGER, rolled_back INTEGER);");
        }
        if (!tableData.contains(prefix + "chat")) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + prefix + "chat (time INTEGER, user INTEGER, wid INTEGER, x INTEGER, y INTEGER, z INTEGER, message TEXT);");
        }
        if (!tableData.contains(prefix + "command")) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + prefix + "command (time INTEGER, user INTEGER, wid INTEGER, x INTEGER, y INTEGER, z INTEGER, message TEXT);");
        }
        if (!tableData.contains(prefix + "container")) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + prefix + "container (time INTEGER, user INTEGER, wid INTEGER, x INTEGER, y INTEGER, z INTEGER, type INTEGER, data INTEGER, amount INTEGER, metadata BLOB, action INTEGER, rolled_back INTEGER);");
        }
        if (!tableData.contains(prefix + "item")) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + prefix + "item (time INTEGER, user INTEGER, wid INTEGER, x INTEGER, y INTEGER, z INTEGER, type INTEGER, data BLOB, amount INTEGER, action INTEGER, rolled_back INTEGER);");
        }
        if (!tableData.contains(prefix + "database_lock")) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + prefix + "database_lock (status INTEGER, time INTEGER);");
        }
        if (!tableData.contains(prefix + "entity")) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + prefix + "entity (id INTEGER PRIMARY KEY ASC, time INTEGER, data BLOB);");
        }
        if (!tableData.contains(prefix + "entity_map")) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + prefix + "entity_map (id INTEGER, entity TEXT);");
        }
        if (!tableData.contains(prefix + "material_map")) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + prefix + "material_map (id INTEGER, material TEXT);");
        }
        if (!tableData.contains(prefix + "blockdata_map")) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + prefix + "blockdata_map (id INTEGER, data TEXT);");
        }
        if (!tableData.contains(prefix + "session")) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + prefix + "session (time INTEGER, user INTEGER, wid INTEGER, x INTEGER, y INTEGER, z INTEGER, action INTEGER);");
        }
        if (!tableData.contains(prefix + "sign")) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + prefix + "sign (time INTEGER, user INTEGER, wid INTEGER, x INTEGER, y INTEGER, z INTEGER, action INTEGER, color INTEGER, color_secondary INTEGER, data INTEGER, waxed INTEGER, face INTEGER, line_1 TEXT, line_2 TEXT, line_3 TEXT, line_4 TEXT, line_5 TEXT, line_6 TEXT, line_7 TEXT, line_8 TEXT);");
        }
        if (!tableData.contains(prefix + "skull")) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + prefix + "skull (id INTEGER PRIMARY KEY ASC, time INTEGER, owner TEXT, skin TEXT);");
        }
        if (!tableData.contains(prefix + "user")) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + prefix + "user (id INTEGER PRIMARY KEY ASC, time INTEGER, user TEXT, uuid TEXT);");
        }
        if (!tableData.contains(prefix + "username_log")) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + prefix + "username_log (id INTEGER PRIMARY KEY ASC, time INTEGER, uuid TEXT, user TEXT);");
        }
        if (!tableData.contains(prefix + "version")) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + prefix + "version (time INTEGER, version TEXT);");
        }
        if (!tableData.contains(prefix + "world")) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + prefix + "world (id INTEGER, world TEXT);");
        }
    }

    private static void createSQLiteIndexes(String prefix, Statement statement, List<String> indexData, String attachDatabase, boolean purge) {
        try {
            createSQLiteIndex(statement, indexData, attachDatabase, "art_map_id_index", prefix + "art_map(id)");
            createSQLiteIndex(statement, indexData, attachDatabase, "block_index", prefix + "block(wid,x,z,time)");
            createSQLiteIndex(statement, indexData, attachDatabase, "block_user_index", prefix + "block(user,time)");
            createSQLiteIndex(statement, indexData, attachDatabase, "block_type_index", prefix + "block(type,time)");
            createSQLiteIndex(statement, indexData, attachDatabase, "blockdata_map_id_index", prefix + "blockdata_map(id)");
            createSQLiteIndex(statement, indexData, attachDatabase, "chat_index", prefix + "chat(time)");
            createSQLiteIndex(statement, indexData, attachDatabase, "chat_user_index", prefix + "chat(user,time)");
            createSQLiteIndex(statement, indexData, attachDatabase, "chat_wid_index", prefix + "chat(wid,x,z,time)");
            createSQLiteIndex(statement, indexData, attachDatabase, "command_index", prefix + "command(time)");
            createSQLiteIndex(statement, indexData, attachDatabase, "command_user_index", prefix + "command(user,time)");
            createSQLiteIndex(statement, indexData, attachDatabase, "command_wid_index", prefix + "command(wid,x,z,time)");
            createSQLiteIndex(statement, indexData, attachDatabase, "container_index", prefix + "container(wid,x,z,time)");
            createSQLiteIndex(statement, indexData, attachDatabase, "container_user_index", prefix + "container(user,time)");
            createSQLiteIndex(statement, indexData, attachDatabase, "container_type_index", prefix + "container(type,time)");
            createSQLiteIndex(statement, indexData, attachDatabase, "item_index", prefix + "item(wid,x,z,time)");
            createSQLiteIndex(statement, indexData, attachDatabase, "item_user_index", prefix + "item(user,time)");
            createSQLiteIndex(statement, indexData, attachDatabase, "item_type_index", prefix + "item(type,time)");
            createSQLiteIndex(statement, indexData, attachDatabase, "entity_map_id_index", prefix + "entity_map(id)");
            createSQLiteIndex(statement, indexData, attachDatabase, "material_map_id_index", prefix + "material_map(id)");
            createSQLiteIndex(statement, indexData, attachDatabase, "session_index", prefix + "session(wid,x,z,time)");
            createSQLiteIndex(statement, indexData, attachDatabase, "session_action_index", prefix + "session(action,time)");
            createSQLiteIndex(statement, indexData, attachDatabase, "session_user_index", prefix + "session(user,time)");
            createSQLiteIndex(statement, indexData, attachDatabase, "session_time_index", prefix + "session(time)");
            createSQLiteIndex(statement, indexData, attachDatabase, "sign_index", prefix + "sign(wid,x,z,time)");
            createSQLiteIndex(statement, indexData, attachDatabase, "sign_user_index", prefix + "sign(user,time)");
            createSQLiteIndex(statement, indexData, attachDatabase, "sign_time_index", prefix + "sign(time)");
            createSQLiteIndex(statement, indexData, attachDatabase, "user_index", prefix + "user(user)");
            createSQLiteIndex(statement, indexData, attachDatabase, "uuid_index", prefix + "user(uuid)");
            createSQLiteIndex(statement, indexData, attachDatabase, "username_log_uuid_index", prefix + "username_log(uuid,user)");
            createSQLiteIndex(statement, indexData, attachDatabase, "world_id_index", prefix + "world(id)");
        }
        catch (Exception e) {
            Chat.console(Phrase.build(Phrase.DATABASE_INDEX_ERROR));
            if (purge) {
                e.printStackTrace();
            }
        }
    }

    private static void createSQLiteIndex(Statement statement, List<String> indexData, String attachDatabase, String indexName, String indexColumns) throws SQLException {
        if (!indexData.contains(indexName)) {
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS " + attachDatabase + indexName + " ON " + indexColumns + ";");
        }
    }

}
