package net.coreprotect.database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import net.coreprotect.config.Config;
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.config.DatabaseType;
import net.coreprotect.consumer.Consumer;
import net.coreprotect.consumer.Queue;
import net.coreprotect.consumer.process.Process;
import net.coreprotect.language.Phrase;
import net.coreprotect.model.BlockGroup;
import net.coreprotect.utility.Chat;
import net.coreprotect.utility.Color;
import net.coreprotect.utility.Util;

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

    public static void beginTransaction(Statement statement) {
        Consumer.transacting = true;

        try {
            if (Config.getGlobal().DB_TYPE == DatabaseType.SQLITE) {
                statement.executeUpdate("BEGIN TRANSACTION");
            } else {
                statement.executeUpdate("START TRANSACTION");
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void commitTransaction(Statement statement) throws Exception {
        int count = 0;

        while (true) {
            try {
                if (Config.getGlobal().DB_TYPE == DatabaseType.SQLITE) {
                    statement.executeUpdate("COMMIT TRANSACTION");
                } else {
                    statement.executeUpdate("COMMIT");
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

    public static void performCheckpoint(Statement statement) throws SQLException {
        if (Config.getGlobal().DB_TYPE == DatabaseType.SQLITE) {
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
        return (Config.getGlobal().DB_TYPE == DatabaseType.SQLITE && ConfigHandler.SERVER_VERSION >= 20);
    }

    public static void containerBreakCheck(String user, Material type, Object container, ItemStack[] contents, Location location) {
        if (BlockGroup.CONTAINERS.contains(type) && !BlockGroup.SHULKER_BOXES.contains(type)) {
            if (Config.getConfig(location.getWorld()).ITEM_TRANSACTIONS) {
                try {
                    if (contents == null) {
                        contents = Util.getContainerContents(type, container, location);
                    }
                    if (contents != null) {
                        List<ItemStack[]> forceList = new ArrayList<>();
                        forceList.add(Util.getContainerState(contents));
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
        // Previously 250ms; long consumer commit time may be due to batching
        // TODO: Investigate, potentially remove batching for SQLite connections
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
            DatabaseType dbType = Config.getGlobal().DB_TYPE;
            if (dbType != DatabaseType.SQLITE) {
                try {
                    connection = ConfigHandler.hikariDataSource.getConnection();
                    ConfigHandler.databaseReachable = true;
                }
                catch (Exception e) {
                    ConfigHandler.databaseReachable = false;
                    if (dbType == DatabaseType.MYSQL) {
                        Chat.sendConsoleMessage(Color.RED + "[CoreProtect] " + Phrase.build(Phrase.MYSQL_UNAVAILABLE));
                    } else if (dbType == DatabaseType.PGSQL){
                        Chat.sendConsoleMessage(Color.RED + "[CoreProtect] " + Phrase.build(Phrase.PGSQL_UNAVAILABLE));
                    }
                    e.printStackTrace();
                }
            } else {
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
            int rolledBack = Util.toggleRolledBack(rb, (table == 2 || table == 3 || table == 4)); // co_item, co_container, co_block
            if (table == 1 || table == 3) {
                statement.executeUpdate("UPDATE " + StatementUtils.getTableName("container") + " SET rolled_back='" + rolledBack + "' WHERE rowid='" + id + "'");
            }
            else if (table == 2) {
                statement.executeUpdate("UPDATE " + StatementUtils.getTableName("item") + " SET rolled_back='" + rolledBack + "' WHERE rowid='" + id + "'");
            }
            else {
                statement.executeUpdate("UPDATE " + StatementUtils.getTableName("block") + " SET rolled_back='" + rolledBack + "' WHERE rowid='" + id + "'");
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static PreparedStatement prepareStatement(Connection connection, int type, boolean keys) {
        PreparedStatement preparedStatement = null;
        try {
            String signInsert = "INSERT INTO " + StatementUtils.getTableName("sign") + " (time, \"user\", wid, x, y, z, action, color, color_secondary, data, waxed, face, line_1, line_2, line_3, line_4, line_5, line_6, line_7, line_8) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
            String blockInsert = "INSERT INTO " + StatementUtils.getTableName("block") + " (time, \"user\", wid, x, y, z, type, data, meta, blockdata, action, rolled_back) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
            String skullInsert = "INSERT INTO " + StatementUtils.getTableName("skull") + " (time, owner) VALUES (?, ?)";
            String containerInsert = "INSERT INTO " + StatementUtils.getTableName("container") + " (time, \"user\", wid, x, y, z, type, data, amount, metadata, action, rolled_back) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
            String itemInsert = "INSERT INTO " + StatementUtils.getTableName("item") + " (time, \"user\", wid, x, y, z, type, data, amount, action, rolled_back) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
            String worldInsert = "INSERT INTO " + StatementUtils.getTableName("world") + " (id, world) VALUES (?, ?)";
            String chatInsert = "INSERT INTO " + StatementUtils.getTableName("chat") + " (time, \"user\", wid, x, y, z, message) VALUES (?, ?, ?, ?, ?, ?, ?)";
            String commandInsert = "INSERT INTO " + StatementUtils.getTableName("command") + " (time, \"user\", wid, x, y, z, message) VALUES (?, ?, ?, ?, ?, ?, ?)";
            String sessionInsert = "INSERT INTO " + StatementUtils.getTableName("session") + " (time, \"user\", wid, x, y, z, action) VALUES (?, ?, ?, ?, ?, ?, ?)";
            String entityInsert = "INSERT INTO " + StatementUtils.getTableName("entity") + " (time, data) VALUES (?, ?)";
            String materialInsert = "INSERT INTO " + StatementUtils.getTableName("material_map") + " (id, material) VALUES (?, ?)";
            String artInsert = "INSERT INTO " + StatementUtils.getTableName("art_map") + " (id, art) VALUES (?, ?)";
            String entityMapInsert = "INSERT INTO " + StatementUtils.getTableName("entity_map") + " (id, entity) VALUES (?, ?)";
            String blockdataInsert = "INSERT INTO " + StatementUtils.getTableName("blockdata_map") + " (id, data) VALUES (?, ?)";

            switch (type) {
                case SIGN:
                    preparedStatement = prepareStatement(connection, signInsert, keys);
                    break;
                case BLOCK:
                    preparedStatement = prepareStatement(connection, blockInsert, keys);
                    break;
                case SKULL:
                    preparedStatement = prepareStatement(connection, skullInsert, keys);
                    break;
                case CONTAINER:
                    preparedStatement = prepareStatement(connection, containerInsert, keys);
                    break;
                case ITEM:
                    preparedStatement = prepareStatement(connection, itemInsert, keys);
                    break;
                case WORLD:
                    preparedStatement = prepareStatement(connection, worldInsert, keys);
                    break;
                case CHAT:
                    preparedStatement = prepareStatement(connection, chatInsert, keys);
                    break;
                case COMMAND:
                    preparedStatement = prepareStatement(connection, commandInsert, keys);
                    break;
                case SESSION:
                    preparedStatement = prepareStatement(connection, sessionInsert, keys);
                    break;
                case ENTITY:
                    preparedStatement = prepareStatement(connection, entityInsert, keys);
                    break;
                case MATERIAL:
                    preparedStatement = prepareStatement(connection, materialInsert, keys);
                    break;
                case ART:
                    preparedStatement = prepareStatement(connection, artInsert, keys);
                    break;
                case ENTITY_MAP:
                    preparedStatement = prepareStatement(connection, entityMapInsert, keys);
                    break;
                case BLOCKDATA:
                    preparedStatement = prepareStatement(connection, blockdataInsert, keys);
                    break;
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
            if (Config.getGlobal().DB_TYPE == DatabaseType.SQLITE) {
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

    public static void createDatabaseTables(String prefix, boolean purge) {
        ConfigHandler.databaseTables.clear();
        ConfigHandler.databaseTables.addAll(Arrays.asList("art_map", "block", "chat", "command", "container", "item", "database_lock", "entity", "entity_map", "material_map", "blockdata_map", "session", "sign", "skull", "user", "username_log", "version", "world"));
        DatabaseType dbType = Config.getGlobal().DB_TYPE;
        if (dbType == DatabaseType.PGSQL) {
            boolean success = false;
            try (Connection connection = Database.getConnection(true, true, true, 0)) {
                if (connection != null) {
                    Statement statement = connection.createStatement();
                    statement.executeUpdate("create table if not exists \"" + prefix + "art_map\" (\"rowid\" bigserial primary key not null, \"id\" integer not null, \"art\" varchar(255) not null)");
                    statement.executeUpdate("create index if not exists \"" + prefix + "art_map_id_index\" on \"" + prefix + "art_map\" (\"id\")");

                    statement.executeUpdate("create table if not exists \"" + prefix + "block\" (\"rowid\" bigserial primary key not null, \"time\" integer not null, \"user\" integer not null, \"wid\" integer not null, \"x\" integer not null, \"y\" smallint not null, \"z\" integer not null, \"type\" integer not null, \"data\" integer not null, \"meta\" bytea null, \"blockdata\" bytea null, \"action\" smallint not null, \"rolled_back\" smallint not null)");
                    statement.executeUpdate("create index if not exists \"" + prefix + "block_wid_x_z_time_index\" on \"" + prefix + "block\" (\"wid\", \"x\", \"z\", \"time\")");
                    statement.executeUpdate("create index if not exists \"" + prefix + "block_user_time_index\" on \"" + prefix + "block\" (\"user\", \"time\")");
                    statement.executeUpdate("create index if not exists \"" + prefix + "block_type_time_index\" on \"" + prefix + "block\" (\"type\", \"time\")");

                    statement.executeUpdate("create table if not exists \"" + prefix + "chat\" (\"rowid\" bigserial primary key not null, \"time\" integer not null, \"user\" integer not null, \"wid\" integer not null, \"x\" integer not null, \"y\" smallint not null, \"z\" integer not null, \"message\" text not null)");
                    statement.executeUpdate("create index if not exists \"" + prefix + "chat_time_index\" on \"" + prefix + "chat\" (\"time\")");
                    statement.executeUpdate("create index if not exists \"" + prefix + "chat_user_time_index\" on \"" + prefix + "chat\" (\"user\", \"time\")");
                    statement.executeUpdate("create index if not exists \"" + prefix + "chat_wid_x_z_time_index\" on \"" + prefix + "chat\" (\"wid\", \"x\", \"z\", \"time\")");

                    statement.executeUpdate("create table if not exists \"" + prefix + "command\" (\"rowid\" bigserial primary key not null, \"time\" integer not null, \"user\" integer not null, \"wid\" integer not null, \"x\" integer not null, \"y\" smallint not null, \"z\" integer not null, \"message\" text not null)");
                    statement.executeUpdate("create index if not exists \"" + prefix + "command_time_index\" on \"" + prefix + "command\" (\"time\")");
                    statement.executeUpdate("create index if not exists \"" + prefix + "command_user_time_index\" on \"" + prefix + "command\" (\"user\", \"time\")");
                    statement.executeUpdate("create index if not exists \"" + prefix + "command_wid_x_z_time_index\" on \"" + prefix + "command\" (\"wid\", \"x\", \"z\", \"time\")");

                    statement.executeUpdate("create table if not exists \"" + prefix + "container\" (\"rowid\" bigserial primary key not null, \"time\" integer not null, \"user\" integer not null, \"wid\" integer not null, \"x\" integer not null, \"y\" smallint not null, \"z\" integer not null, \"type\" integer not null, \"data\" integer not null, \"amount\" integer not null, \"metadata\" bytea null, \"action\" smallint not null, \"rolled_back\" smallint not null)");
                    statement.executeUpdate("create index if not exists \"" + prefix + "container_wid_x_z_time_index\" on \"" + prefix + "container\" (\"wid\", \"x\", \"z\", \"time\")");
                    statement.executeUpdate("create index if not exists \"" + prefix + "container_user_time_index\" on \"" + prefix + "container\" (\"user\", \"time\")");
                    statement.executeUpdate("create index if not exists \"" + prefix + "container_type_time_index\" on \"" + prefix + "container\" (\"type\", \"time\")");

                    statement.executeUpdate("create table if not exists \"" + prefix + "item\" (\"rowid\" bigserial primary key not null, \"time\" integer not null, \"user\" integer not null, \"wid\" integer not null, \"x\" integer not null, \"y\" smallint not null, \"z\" integer not null, \"type\" integer not null, \"data\" bytea null, \"amount\" integer not null, \"action\" smallint not null, \"rolled_back\" smallint not null)");
                    statement.executeUpdate("create index if not exists \"" + prefix + "item_wid_x_z_time_index\" on \"" + prefix + "item\" (\"wid\", \"x\", \"z\", \"time\")");
                    statement.executeUpdate("create index if not exists \"" + prefix + "item_user_time_index\" on \"" + prefix + "item\" (\"user\", \"time\")");
                    statement.executeUpdate("create index if not exists \"" + prefix + "item_type_time_index\" on \"" + prefix + "item\" (\"type\", \"time\")");

                    statement.executeUpdate("create table if not exists \"" + prefix + "database_lock\" (\"rowid\" bigserial primary key not null, \"status\" smallint not null, \"time\" integer not null)");
                    statement.executeUpdate("create table if not exists \"" + prefix + "entity\" (\"rowid\" bigserial primary key not null, \"time\" integer not null, \"data\" bytea not null)");

                    statement.executeUpdate("create table if not exists \"" + prefix + "entity_map\" (\"rowid\" bigserial primary key not null, \"id\" integer not null, \"entity\" varchar(255) not null)");

                    statement.executeUpdate("create index if not exists \"" + prefix + "entity_map_id_index\" on \"" + prefix + "entity_map\" (\"id\")");

                    statement.executeUpdate("create table if not exists \"" + prefix + "material_map\" (\"rowid\" bigserial primary key not null, \"id\" integer not null, \"material\" varchar(255) not null)");
                    statement.executeUpdate("create index if not exists \"" + prefix + "material_map_id_index\" on \"" + prefix + "material_map\" (\"id\")");

                    statement.executeUpdate("create table if not exists \"" + prefix + "blockdata_map\" (\"rowid\" bigserial primary key not null, \"id\" integer not null, \"data\" varchar(255) not null)");
                    statement.executeUpdate("create index if not exists \"" + prefix + "blockdata_map_id_index\" on \"" + prefix + "blockdata_map\" (\"id\")");

                    statement.executeUpdate("create table if not exists \"" + prefix + "session\" (\"rowid\" bigserial primary key not null, \"time\" integer not null, \"user\" integer not null, \"wid\" integer not null, \"x\" integer not null, \"y\" smallint not null, \"z\" integer not null, \"action\" smallint not null)");
                    statement.executeUpdate("create index if not exists \"" + prefix + "session_wid_x_y_time_index\" on \"" + prefix + "session\" (\"wid\", \"x\", \"y\", \"time\")");
                    statement.executeUpdate("create index if not exists \"" + prefix + "session_action_time_index\" on \"" + prefix + "session\" (\"action\", \"time\")");
                    statement.executeUpdate("create index if not exists \"" + prefix + "session_user_time_index\" on \"" + prefix + "session\" (\"user\", \"time\")");
                    statement.executeUpdate("create index if not exists \"" + prefix + "session_time_index\" on \"" + prefix + "session\" (\"time\")");

                    statement.executeUpdate("create table if not exists \"" + prefix + "sign\" (\"rowid\" bigserial primary key not null, \"time\" integer not null, \"user\" integer not null, \"wid\" integer not null, \"x\" integer not null, \"y\" smallint not null, \"z\" integer not null, \"action\" smallint not null, \"color\" integer not null, \"color_secondary\" integer not null, \"data\" smallint not null, \"waxed\" smallint not null, \"face\" smallint not null, \"line_1\" varchar(100) null, \"line_2\" varchar(100) null, \"line_3\" varchar(100) null, \"line_4\" varchar(100) null, \"line_5\" varchar(100) null, \"line_6\" varchar(100) null, \"line_7\" varchar(100) null, \"line_8\" varchar(100) null)");
                    statement.executeUpdate("create index if not exists \"" + prefix + "sign_wid_x_z_time_index\" on \"" + prefix + "sign\" (\"wid\", \"x\", \"z\", \"time\")");
                    statement.executeUpdate("create index if not exists \"" + prefix + "sign_user_time_index\" on \"" + prefix + "sign\" (\"user\", \"time\")");
                    statement.executeUpdate("create index if not exists \"" + prefix + "sign_time_index\" on \"" + prefix + "sign\" (\"time\")");

                    statement.executeUpdate("create table if not exists \"" + prefix + "skull\" (\"rowid\" bigserial primary key not null, \"time\" integer not null, \"owner\" varchar(64) not null)");

                    statement.executeUpdate("create table if not exists \"" + prefix + "user\" (\"rowid\" bigserial primary key not null, \"time\" integer not null, \"user\" varchar(255) not null, \"uuid\" uuid null)");
                    statement.executeUpdate("create index if not exists \"" + prefix + "user_user_index\" on \"" + prefix + "user\" (\"user\")");
                    statement.executeUpdate("create index if not exists \"" + prefix + "user_uuid_index\" on \"" + prefix + "user\" (\"uuid\")");

                    statement.executeUpdate("create table if not exists \"" + prefix + "username_log\" (\"rowid\" bigserial primary key not null, \"time\" integer not null, \"uuid\" uuid not null, \"user\" varchar(100) not null)");
                    statement.executeUpdate("create index if not exists \"" + prefix + "username_log_uuid_user_index\" on \"username_log\" (\"uuid\", \"user\")");

                    statement.executeUpdate("create table if not exists \"" + prefix + "version\" (\"rowid\" bigserial primary key not null, \"time\" integer not null, \"version\" varchar(16) not null)");

                    statement.executeUpdate("create table if not exists \"" + prefix + "world\" (\"rowid\" bigserial primary key not null, \"id\" integer not null, \"world\" varchar(255) not null)");
                    statement.executeUpdate("create index if not exists \"" + prefix + "world_id_index\" on \"" + prefix + "world\" (\"id\")");
                    if (!purge) {
                        initializeTables(prefix, statement);
                    }
                    statement.close();
                    success = true;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            if (!success) {
                Config.getGlobal().DB_TYPE = DatabaseType.SQLITE;
            }
        } else if (dbType == DatabaseType.MYSQL) {
            boolean success = false;
            try (Connection connection = Database.getConnection(true, true, true, 0)) {
                if (connection != null) {
                    String index = "";
                    Statement statement = connection.createStatement();
                    index = ", INDEX(id)";
                    statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + prefix + "art_map(rowid int NOT NULL AUTO_INCREMENT,PRIMARY KEY(rowid),id int,art varchar(255)" + index + ") ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4");
                    index = ", INDEX(wid,x,z,time), INDEX(user,time), INDEX(type,time)";
                    statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + prefix + "block(rowid bigint NOT NULL AUTO_INCREMENT,PRIMARY KEY(rowid), time int, user int, wid int, x int, y int, z int, type int, data int, meta mediumblob, blockdata blob, action tinyint, rolled_back tinyint" + index + ") ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4");
                    index = ", INDEX(time), INDEX(user,time), INDEX(wid,x,z,time)";
                    statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + prefix + "chat(rowid int NOT NULL AUTO_INCREMENT,PRIMARY KEY(rowid),time int, user int, wid int, x int, y int (3), z int, message varchar(16000)" + index + ") ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4");
                    index = ", INDEX(time), INDEX(user,time), INDEX(wid,x,z,time)";
                    statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + prefix + "command(rowid int NOT NULL AUTO_INCREMENT,PRIMARY KEY(rowid),time int, user int, wid int, x int, y int (3), z int, message varchar(16000)" + index + ") ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4");
                    index = ", INDEX(wid,x,z,time), INDEX(user,time), INDEX(type,time)";
                    statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + prefix + "container(rowid int NOT NULL AUTO_INCREMENT,PRIMARY KEY(rowid), time int, user int, wid int, x int, y int, z int, type int, data int, amount int, metadata blob, action tinyint, rolled_back tinyint" + index + ") ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4");
                    index = ", INDEX(wid,x,z,time), INDEX(user,time), INDEX(type,time)";
                    statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + prefix + "item(rowid int NOT NULL AUTO_INCREMENT,PRIMARY KEY(rowid), time int, user int, wid int, x int, y int, z int, type int, data blob, amount int, action tinyint, rolled_back tinyint" + index + ") ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4");
                    statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + prefix + "database_lock(rowid int NOT NULL AUTO_INCREMENT,PRIMARY KEY(rowid),status tinyint,time int) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4");
                    statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + prefix + "entity(rowid int NOT NULL AUTO_INCREMENT,PRIMARY KEY(rowid), time int, data blob) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4");
                    index = ", INDEX(id)";
                    statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + prefix + "entity_map(rowid int NOT NULL AUTO_INCREMENT,PRIMARY KEY(rowid),id int,entity varchar(255)" + index + ") ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4");
                    index = ", INDEX(id)";
                    statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + prefix + "material_map(rowid int NOT NULL AUTO_INCREMENT,PRIMARY KEY(rowid),id int,material varchar(255)" + index + ") ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4");
                    index = ", INDEX(id)";
                    statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + prefix + "blockdata_map(rowid int NOT NULL AUTO_INCREMENT,PRIMARY KEY(rowid),id int,data varchar(255)" + index + ") ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4");
                    index = ", INDEX(wid,x,z,time), INDEX(action,time), INDEX(user,time), INDEX(time)";
                    statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + prefix + "session(rowid int NOT NULL AUTO_INCREMENT,PRIMARY KEY(rowid),time int, user int, wid int, x int, y int (3), z int, action tinyint" + index + ") ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4");
                    index = ", INDEX(wid,x,z,time), INDEX(user,time), INDEX(time)";
                    statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + prefix + "sign(rowid int NOT NULL AUTO_INCREMENT,PRIMARY KEY(rowid),time int, user int, wid int, x int, y int, z int, action tinyint, color int, color_secondary int, data tinyint, waxed tinyint, face tinyint, line_1 varchar(100), line_2 varchar(100), line_3 varchar(100), line_4 varchar(100), line_5 varchar(100), line_6 varchar(100), line_7 varchar(100), line_8 varchar(100)" + index + ") ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4");
                    statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + prefix + "skull(rowid int NOT NULL AUTO_INCREMENT,PRIMARY KEY(rowid), time int, owner varchar(64)) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4");
                    index = ", INDEX(user), INDEX(uuid)";
                    statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + prefix + "user(rowid int NOT NULL AUTO_INCREMENT,PRIMARY KEY(rowid),time int,user varchar(100),uuid varchar(64)" + index + ") ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4");
                    index = ", INDEX(uuid,user)";
                    statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + prefix + "username_log(rowid int NOT NULL AUTO_INCREMENT,PRIMARY KEY(rowid),time int,uuid varchar(64),user varchar(100)" + index + ") ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4");
                    statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + prefix + "version(rowid int NOT NULL AUTO_INCREMENT,PRIMARY KEY(rowid),time int,version varchar(16)) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4");
                    index = ", INDEX(id)";
                    statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + prefix + "world(rowid int NOT NULL AUTO_INCREMENT,PRIMARY KEY(rowid),id int,world varchar(255)" + index + ") ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4");
                    if (!purge) {
                        initializeTables(prefix, statement);
                    }
                    statement.close();
                    success = true;
                }
            }
            catch (Exception e) {
                e.printStackTrace();
            }
            if (!success) {
                Config.getGlobal().DB_TYPE = DatabaseType.SQLITE;
            }
        } else {
            try (Connection connection = Database.getConnection(true, 0)) {
                Statement statement = connection.createStatement();
                List<String> tableData = new ArrayList<>();
                List<String> indexData = new ArrayList<>();
                String attachDatabase = "";

                if (purge) {
                    String query = "ATTACH DATABASE '" + ConfigHandler.path + ConfigHandler.sqlite + ".tmp' AS tmp_db";
                    PreparedStatement preparedStmt = connection.prepareStatement(query);
                    preparedStmt.execute();
                    preparedStmt.close();
                    attachDatabase = "tmp_db.";
                }

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
                    statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + prefix + "skull (id INTEGER PRIMARY KEY ASC, time INTEGER, owner TEXT);");
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
                try {
                    if (!indexData.contains("art_map_id_index")) {
                        statement.executeUpdate("CREATE INDEX IF NOT EXISTS " + attachDatabase + "art_map_id_index ON " + ConfigHandler.prefix + "art_map(id);");
                    }
                    if (!indexData.contains("block_index")) {
                        statement.executeUpdate("CREATE INDEX IF NOT EXISTS " + attachDatabase + "block_index ON " + ConfigHandler.prefix + "block(wid,x,z,time);");
                    }
                    if (!indexData.contains("block_user_index")) {
                        statement.executeUpdate("CREATE INDEX IF NOT EXISTS " + attachDatabase + "block_user_index ON " + ConfigHandler.prefix + "block(user,time);");
                    }
                    if (!indexData.contains("block_type_index")) {
                        statement.executeUpdate("CREATE INDEX IF NOT EXISTS " + attachDatabase + "block_type_index ON " + ConfigHandler.prefix + "block(type,time);");
                    }
                    if (!indexData.contains("blockdata_map_id_index")) {
                        statement.executeUpdate("CREATE INDEX IF NOT EXISTS " + attachDatabase + "blockdata_map_id_index ON " + ConfigHandler.prefix + "blockdata_map(id);");
                    }
                    if (!indexData.contains("chat_index")) {
                        statement.executeUpdate("CREATE INDEX IF NOT EXISTS " + attachDatabase + "chat_index ON " + ConfigHandler.prefix + "chat(time);");
                    }
                    if (!indexData.contains("chat_user_index")) {
                        statement.executeUpdate("CREATE INDEX IF NOT EXISTS " + attachDatabase + "chat_user_index ON " + ConfigHandler.prefix + "chat(user,time);");
                    }
                    if (!indexData.contains("chat_wid_index")) {
                        statement.executeUpdate("CREATE INDEX IF NOT EXISTS " + attachDatabase + "chat_wid_index ON " + ConfigHandler.prefix + "chat(wid,x,z,time);");
                    }
                    if (!indexData.contains("command_index")) {
                        statement.executeUpdate("CREATE INDEX IF NOT EXISTS " + attachDatabase + "command_index ON " + ConfigHandler.prefix + "command(time);");
                    }
                    if (!indexData.contains("command_user_index")) {
                        statement.executeUpdate("CREATE INDEX IF NOT EXISTS " + attachDatabase + "command_user_index ON " + ConfigHandler.prefix + "command(user,time);");
                    }
                    if (!indexData.contains("command_wid_index")) {
                        statement.executeUpdate("CREATE INDEX IF NOT EXISTS " + attachDatabase + "command_wid_index ON " + ConfigHandler.prefix + "command(wid,x,z,time);");
                    }
                    if (!indexData.contains("container_index")) {
                        statement.executeUpdate("CREATE INDEX IF NOT EXISTS " + attachDatabase + "container_index ON " + ConfigHandler.prefix + "container(wid,x,z,time);");
                    }
                    if (!indexData.contains("container_user_index")) {
                        statement.executeUpdate("CREATE INDEX IF NOT EXISTS " + attachDatabase + "container_user_index ON " + ConfigHandler.prefix + "container(user,time);");
                    }
                    if (!indexData.contains("container_type_index")) {
                        statement.executeUpdate("CREATE INDEX IF NOT EXISTS " + attachDatabase + "container_type_index ON " + ConfigHandler.prefix + "container(type,time);");
                    }
                    if (!indexData.contains("item_index")) {
                        statement.executeUpdate("CREATE INDEX IF NOT EXISTS " + attachDatabase + "item_index ON " + ConfigHandler.prefix + "item(wid,x,z,time);");
                    }
                    if (!indexData.contains("item_user_index")) {
                        statement.executeUpdate("CREATE INDEX IF NOT EXISTS " + attachDatabase + "item_user_index ON " + ConfigHandler.prefix + "item(user,time);");
                    }
                    if (!indexData.contains("item_type_index")) {
                        statement.executeUpdate("CREATE INDEX IF NOT EXISTS " + attachDatabase + "item_type_index ON " + ConfigHandler.prefix + "item(type,time);");
                    }
                    if (!indexData.contains("entity_map_id_index")) {
                        statement.executeUpdate("CREATE INDEX IF NOT EXISTS " + attachDatabase + "entity_map_id_index ON " + ConfigHandler.prefix + "entity_map(id);");
                    }
                    if (!indexData.contains("material_map_id_index")) {
                        statement.executeUpdate("CREATE INDEX IF NOT EXISTS " + attachDatabase + "material_map_id_index ON " + ConfigHandler.prefix + "material_map(id);");
                    }
                    if (!indexData.contains("session_index")) {
                        statement.executeUpdate("CREATE INDEX IF NOT EXISTS " + attachDatabase + "session_index ON " + ConfigHandler.prefix + "session(wid,x,z,time);");
                    }
                    if (!indexData.contains("session_action_index")) {
                        statement.executeUpdate("CREATE INDEX IF NOT EXISTS " + attachDatabase + "session_action_index ON " + ConfigHandler.prefix + "session(action,time);");
                    }
                    if (!indexData.contains("session_user_index")) {
                        statement.executeUpdate("CREATE INDEX IF NOT EXISTS " + attachDatabase + "session_user_index ON " + ConfigHandler.prefix + "session(user,time);");
                    }
                    if (!indexData.contains("session_time_index")) {
                        statement.executeUpdate("CREATE INDEX IF NOT EXISTS " + attachDatabase + "session_time_index ON " + ConfigHandler.prefix + "session(time);");
                    }
                    if (!indexData.contains("sign_index")) {
                        statement.executeUpdate("CREATE INDEX IF NOT EXISTS " + attachDatabase + "sign_index ON " + ConfigHandler.prefix + "sign(wid,x,z,time);");
                    }
                    if (!indexData.contains("sign_user_index")) {
                        statement.executeUpdate("CREATE INDEX IF NOT EXISTS " + attachDatabase + "sign_user_index ON " + ConfigHandler.prefix + "sign(user,time);");
                    }
                    if (!indexData.contains("sign_time_index")) {
                        statement.executeUpdate("CREATE INDEX IF NOT EXISTS " + attachDatabase + "sign_time_index ON " + ConfigHandler.prefix + "sign(time);");
                    }
                    if (!indexData.contains("user_index")) {
                        statement.executeUpdate("CREATE INDEX IF NOT EXISTS " + attachDatabase + "user_index ON " + ConfigHandler.prefix + "user(user);");
                    }
                    if (!indexData.contains("uuid_index")) {
                        statement.executeUpdate("CREATE INDEX IF NOT EXISTS " + attachDatabase + "uuid_index ON " + ConfigHandler.prefix + "user(uuid);");
                    }
                    if (!indexData.contains("username_log_uuid_index")) {
                        statement.executeUpdate("CREATE INDEX IF NOT EXISTS " + attachDatabase + "username_log_uuid_index ON " + ConfigHandler.prefix + "username_log(uuid,user);");
                    }
                    if (!indexData.contains("world_id_index")) {
                        statement.executeUpdate("CREATE INDEX IF NOT EXISTS " + attachDatabase + "world_id_index ON " + ConfigHandler.prefix + "world(id);");
                    }
                }
                catch (Exception e) {
                    Chat.console(Phrase.build(Phrase.DATABASE_INDEX_ERROR));
                    if (purge) {
                        e.printStackTrace();
                    }
                }
                if (!purge) {
                    initializeTables(prefix, statement);
                }
                statement.close();
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

}
