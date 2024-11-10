package net.coreprotect.config;

import java.io.File;
import java.io.RandomAccessFile;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import net.coreprotect.bukkit.BukkitAdapter;
import net.coreprotect.consumer.Queue;
import net.coreprotect.database.Database;
import net.coreprotect.database.statement.UserStatement;
import net.coreprotect.language.Phrase;
import net.coreprotect.listener.ListenerHandler;
import net.coreprotect.model.BlockGroup;
import net.coreprotect.paper.PaperAdapter;
import net.coreprotect.patch.Patch;
import net.coreprotect.spigot.SpigotAdapter;
import net.coreprotect.utility.Chat;
import net.coreprotect.utility.Color;
import net.coreprotect.utility.Util;
import oshi.hardware.CentralProcessor;

public class ConfigHandler extends Queue {
    public static int SERVER_VERSION = 0;
    public static final int EDITION_VERSION = 2;
    public static final String EDITION_BRANCH = Util.getBranch();
    public static final String EDITION_NAME = Util.getPluginName();
    public static final String COMMUNITY_EDITION = "Community Edition";
    public static final String JAVA_VERSION = "11.0";
    public static final String MINECRAFT_VERSION = "1.16";
    public static final String LATEST_VERSION = "1.21";
    public static String path = "plugins/CoreProtect/";
    public static String sqlite = "database.db";
    public static String host = "127.0.0.1";
    public static int port = 3306;
    public static String database = "database";
    public static String username = "root";
    public static String password = "";
    public static String prefix = "co_";
    public static String prefixConfig = "co_";
    public static int maximumPoolSize = 10;

    public static HikariDataSource hikariDataSource = null;
    public static final CentralProcessor processorInfo = Util.getProcessorInfo();
    public static final boolean isSpigot = Util.isSpigot();
    public static final boolean isPaper = Util.isPaper();
    public static final boolean isFolia = Util.isFolia();
    public static volatile boolean serverRunning = false;
    public static volatile boolean converterRunning = false;
    public static volatile boolean purgeRunning = false;
    public static volatile boolean migrationRunning = false;
    public static volatile boolean pauseConsumer = false;
    public static volatile boolean worldeditEnabled = false;
    public static volatile boolean databaseReachable = true;
    public static volatile int worldId = 0;
    public static volatile int materialId = 0;
    public static volatile int blockdataId = 0;
    public static volatile int entityId = 0;
    public static volatile int artId = 0;

    private static <K, V> Map<K, V> syncMap() {
        return Collections.synchronizedMap(new HashMap<>());
    }

    public static Map<String, Integer> worlds = syncMap();
    public static Map<Integer, String> worldsReversed = syncMap();
    public static Map<String, Integer> materials = syncMap();
    public static Map<Integer, String> materialsReversed = syncMap();
    public static Map<String, Integer> blockdata = syncMap();
    public static Map<Integer, String> blockdataReversed = syncMap();
    public static Map<String, Integer> entities = syncMap();
    public static Map<Integer, String> entitiesReversed = syncMap();
    public static Map<String, Integer> art = syncMap();
    public static Map<Integer, String> artReversed = syncMap();
    public static Map<String, int[]> rollbackHash = syncMap();
    public static Map<String, Boolean> inspecting = syncMap();
    public static Map<String, Boolean> blacklist = syncMap();
    public static Map<String, Integer> loggingChest = syncMap();
    public static Map<String, Integer> loggingItem = syncMap();
    public static ConcurrentHashMap<String, List<Object>> transactingChest = new ConcurrentHashMap<>();
    public static ConcurrentHashMap<String, List<ItemStack[]>> oldContainer = new ConcurrentHashMap<>();
    public static ConcurrentHashMap<String, List<ItemStack>> itemsPickup = new ConcurrentHashMap<>();
    public static ConcurrentHashMap<String, List<ItemStack>> itemsDrop = new ConcurrentHashMap<>();
    public static ConcurrentHashMap<String, List<ItemStack>> itemsThrown = new ConcurrentHashMap<>();
    public static ConcurrentHashMap<String, List<ItemStack>> itemsShot = new ConcurrentHashMap<>();
    public static ConcurrentHashMap<String, List<ItemStack>> itemsBreak = new ConcurrentHashMap<>();
    public static ConcurrentHashMap<String, List<ItemStack>> itemsDestroy = new ConcurrentHashMap<>();
    public static ConcurrentHashMap<String, List<ItemStack>> itemsCreate = new ConcurrentHashMap<>();
    public static ConcurrentHashMap<String, List<ItemStack>> itemsSell = new ConcurrentHashMap<>();
    public static ConcurrentHashMap<String, List<ItemStack>> itemsBuy = new ConcurrentHashMap<>();
    public static ConcurrentHashMap<String, Object[]> hopperAbort = new ConcurrentHashMap<>();
    public static ConcurrentHashMap<String, Object[]> hopperSuccess = new ConcurrentHashMap<>();
    public static Map<String, List<ItemStack[]>> forceContainer = syncMap();
    public static Map<String, Integer> lookupType = syncMap();
    public static Map<String, Object[]> lookupThrottle = syncMap();
    public static Map<String, Object[]> teleportThrottle = syncMap();
    public static Map<String, Integer> lookupPage = syncMap();
    public static Map<String, String> lookupCommand = syncMap();
    public static Map<String, List<Object>> lookupBlist = syncMap();
    public static Map<String, Map<Object, Boolean>> lookupElist = syncMap();
    public static Map<String, List<String>> lookupEUserlist = syncMap();
    public static Map<String, List<String>> lookupUlist = syncMap();
    public static Map<String, List<Integer>> lookupAlist = syncMap();
    public static Map<String, Integer[]> lookupRadius = syncMap();
    public static Map<String, String> lookupTime = syncMap();
    public static Map<String, Long[]> lookupRows = syncMap();
    public static Map<String, String> uuidCache = syncMap();
    public static Map<String, String> uuidCacheReversed = syncMap();
    public static Map<String, Integer> playerIdCache = syncMap();
    public static Map<Integer, String> playerIdCacheReversed = syncMap();
    public static Map<String, List<Object>> lastRollback = syncMap();
    public static Map<String, Boolean> activeRollbacks = syncMap();
    public static Map<String, Object[]> entityBlockMapper = new ConcurrentHashMap<>();
    public static ConcurrentHashMap<Long, Long> populatedChunks = new ConcurrentHashMap<>();
    public static ConcurrentHashMap<String, String> language = new ConcurrentHashMap<>();
    public static List<String> databaseTables = new ArrayList<>();

    private static void checkPlayers(Connection connection) {
        ConfigHandler.playerIdCache.clear();
        ConfigHandler.playerIdCacheReversed.clear();
        for (Player player : Bukkit.getServer().getOnlinePlayers()) {
            if (ConfigHandler.playerIdCache.get(player.getName().toLowerCase(Locale.ROOT)) == null) {
                UserStatement.loadId(connection, player.getName(), player.getUniqueId().toString());
            }
        }
    }

    private static void loadBlacklist() {
        try {
            ConfigHandler.blacklist.clear();
            String blacklist = ConfigHandler.path + "blacklist.txt";
            boolean exists = (new File(blacklist)).exists();
            if (exists) {
                RandomAccessFile blfile = new RandomAccessFile(blacklist, "rw");
                long blc = blfile.length();
                if (blc > 0) {
                    while (blfile.getFilePointer() < blfile.length()) {
                        String blacklistUser = blfile.readLine().replaceAll(" ", "").toLowerCase(Locale.ROOT);
                        if (blacklistUser.length() > 0) {
                            ConfigHandler.blacklist.put(blacklistUser, true);
                        }
                    }
                }
                blfile.close();
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void loadConfig() {
        try {
            Config.init();
            ConfigFile.init(ConfigFile.LANGUAGE); // load user phrases
            ConfigFile.init(ConfigFile.LANGUAGE_CACHE); // load translation cache

            // Enforce "co_" table prefix if using SQLite.
            if (!Config.getGlobal().MYSQL) {
                ConfigHandler.prefixConfig = Config.getGlobal().PREFIX;
                Config.getGlobal().PREFIX = "co_";
            }

            ConfigHandler.host = Config.getGlobal().MYSQL_HOST;
            ConfigHandler.port = Config.getGlobal().MYSQL_PORT;
            ConfigHandler.database = Config.getGlobal().MYSQL_DATABASE;
            ConfigHandler.username = Config.getGlobal().MYSQL_USERNAME;
            ConfigHandler.password = Config.getGlobal().MYSQL_PASSWORD;
            ConfigHandler.maximumPoolSize = Config.getGlobal().MAXIMUM_POOL_SIZE;
            ConfigHandler.prefix = Config.getGlobal().PREFIX;

            ConfigHandler.loadBlacklist(); // Load the blacklist file if it exists.
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void loadDatabase() {
        // close old pool when we reload the database, e.g. in purge command
        Database.closeConnection();

        if (!Config.getGlobal().MYSQL) {
            try {
                File tempFile = File.createTempFile("CoreProtect_" + System.currentTimeMillis(), ".tmp");
                tempFile.setExecutable(true);

                boolean canExecute = false;
                try {
                    canExecute = tempFile.canExecute();
                }
                catch (Exception exception) {
                    // execute access denied by security manager
                }

                if (!canExecute) {
                    File tempFolder = new File("cache");
                    boolean exists = tempFolder.exists();
                    if (!exists) {
                        tempFolder.mkdir();
                    }
                    System.setProperty("java.io.tmpdir", "cache");
                }

                tempFile.delete();

                Class.forName("org.sqlite.JDBC");
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }
        else {
            HikariConfig config = new HikariConfig();
            try {
                Class.forName("com.mysql.cj.jdbc.Driver");
                config.setDriverClassName("com.mysql.cj.jdbc.Driver");
            }
            catch (Exception e) {
                config.setDriverClassName("com.mysql.jdbc.Driver");
            }

            config.setJdbcUrl("jdbc:mysql://" + ConfigHandler.host + ":" + ConfigHandler.port + "/" + ConfigHandler.database);
            config.setUsername(ConfigHandler.username);
            config.setPassword(ConfigHandler.password);
            config.setMaximumPoolSize(ConfigHandler.maximumPoolSize);
            config.setMaxLifetime(60000);
            config.addDataSourceProperty("characterEncoding", "UTF-8");
            config.addDataSourceProperty("connectionTimeout", "10000");
            /* https://github.com/brettwooldridge/HikariCP/wiki/MySQL-Configuration */
            /* https://cdn.oreillystatic.com/en/assets/1/event/21/Connector_J%20Performance%20Gems%20Presentation.pdf */
            config.addDataSourceProperty("cachePrepStmts", "true");
            config.addDataSourceProperty("prepStmtCacheSize", "250");
            config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
            config.addDataSourceProperty("useServerPrepStmts", "true");
            config.addDataSourceProperty("useLocalSessionState", "true");
            config.addDataSourceProperty("rewriteBatchedStatements", "true");
            config.addDataSourceProperty("cacheServerConfiguration", "true");
            config.addDataSourceProperty("maintainTimeStats", "false");
            /* Disable SSL to suppress the unverified server identity warning */
            config.addDataSourceProperty("allowPublicKeyRetrieval", "true");
            config.addDataSourceProperty("useSSL", Config.getGlobal().ENABLE_SSL);

            ConfigHandler.hikariDataSource = new HikariDataSource(config);
        }

        Database.createDatabaseTables(ConfigHandler.prefix, null, Config.getGlobal().MYSQL, false);
    }

    public static void loadTypes(Statement statement) {
        try {
            String query = "SELECT id,material FROM " + ConfigHandler.prefix + "material_map";
            ResultSet rs = statement.executeQuery(query);
            ConfigHandler.materials.clear();
            ConfigHandler.materialsReversed.clear();
            materialId = 0;

            while (rs.next()) {
                int id = rs.getInt("id");
                String material = rs.getString("material");
                ConfigHandler.materials.put(material, id);
                ConfigHandler.materialsReversed.put(id, material);
                if (id > materialId) {
                    materialId = id;
                }
            }
            rs.close();

            query = "SELECT id,data FROM " + ConfigHandler.prefix + "blockdata_map";
            rs = statement.executeQuery(query);
            ConfigHandler.blockdata.clear();
            ConfigHandler.blockdataReversed.clear();
            blockdataId = 0;

            while (rs.next()) {
                int id = rs.getInt("id");
                String data = rs.getString("data");
                ConfigHandler.blockdata.put(data, id);
                ConfigHandler.blockdataReversed.put(id, data);
                if (id > blockdataId) {
                    blockdataId = id;
                }
            }
            rs.close();

            query = "SELECT id,art FROM " + ConfigHandler.prefix + "art_map";
            rs = statement.executeQuery(query);
            ConfigHandler.art.clear();
            ConfigHandler.artReversed.clear();
            artId = 0;

            while (rs.next()) {
                int id = rs.getInt("id");
                String art = rs.getString("art");
                ConfigHandler.art.put(art, id);
                ConfigHandler.artReversed.put(id, art);
                if (id > artId) {
                    artId = id;
                }
            }
            rs.close();

            query = "SELECT id,entity FROM " + ConfigHandler.prefix + "entity_map";
            rs = statement.executeQuery(query);
            ConfigHandler.entities.clear();
            ConfigHandler.entitiesReversed.clear();
            entityId = 0;

            while (rs.next()) {
                int id = rs.getInt("id");
                String entity = rs.getString("entity");
                ConfigHandler.entities.put(entity, id);
                ConfigHandler.entitiesReversed.put(id, entity);
                if (id > entityId) {
                    entityId = id;
                }
            }
            rs.close();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void loadWorlds(Statement statement) {
        try {
            String query = "SELECT id,world FROM " + ConfigHandler.prefix + "world";
            ResultSet rs = statement.executeQuery(query);
            ConfigHandler.worlds.clear();
            ConfigHandler.worldsReversed.clear();
            worldId = 0;

            while (rs.next()) {
                int id = rs.getInt("id");
                String world = rs.getString("world");
                ConfigHandler.worlds.put(world, id);
                ConfigHandler.worldsReversed.put(id, world);
                if (id > worldId) {
                    worldId = id;
                }
            }
            rs.close();

            List<World> worlds = Bukkit.getServer().getWorlds();
            for (World world : worlds) {
                String worldname = world.getName();
                if (ConfigHandler.worlds.get(worldname) == null) {
                    int id = worldId + 1;
                    ConfigHandler.worlds.put(worldname, id);
                    ConfigHandler.worldsReversed.put(id, worldname);
                    worldId = id;
                    Queue.queueWorldInsert(id, worldname);
                }
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static boolean checkDatabaseLock(Statement statement) {
        try {
            if (Config.getGlobal().DATABASE_LOCK) {
                boolean locked = true;
                boolean lockMessage = false;
                int unixtimestamp = (int) (System.currentTimeMillis() / 1000L);
                int waitTime = unixtimestamp + 15;
                while (locked) {
                    locked = false;
                    unixtimestamp = (int) (System.currentTimeMillis() / 1000L);
                    int checkTime = unixtimestamp - 15;
                    String query = "SELECT * FROM " + ConfigHandler.prefix + "database_lock WHERE rowid='1' AND status='1' AND time >= '" + checkTime + "' LIMIT 1";
                    ResultSet rs = statement.executeQuery(query);
                    while (rs.next()) {
                        if (unixtimestamp < waitTime) {
                            if (!lockMessage) {
                                Chat.sendConsoleMessage("[CoreProtect] " + Phrase.build(Phrase.DATABASE_LOCKED_1));
                                lockMessage = true;
                            }
                            Thread.sleep(1000);
                        }
                        else {
                            Chat.sendConsoleMessage(Color.RED + "[CoreProtect] " + Phrase.build(Phrase.DATABASE_LOCKED_2));
                            Chat.sendConsoleMessage(Color.GREY + "[CoreProtect] " + Phrase.build(Phrase.DATABASE_LOCKED_3));
                            Chat.sendConsoleMessage(Color.GREY + "[CoreProtect] " + Phrase.build(Phrase.DATABASE_LOCKED_4));
                            return false;
                        }

                        locked = true;
                    }
                    rs.close();
                }
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        return true;
    }

    public static boolean performInitialization(boolean startup) {
        try {
            BukkitAdapter.loadAdapter();
            SpigotAdapter.loadAdapter();
            PaperAdapter.loadAdapter();
            BlockGroup.initialize();

            ConfigHandler.loadConfig(); // Load (or create) the configuration file.
            ConfigHandler.loadDatabase(); // Initialize MySQL and create tables if necessary.

            if (startup) {
                ListenerHandler.registerNetworking(); // Register channels for networking API
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        try (Connection connection = Database.getConnection(true, 0)) {
            Statement statement = connection.createStatement();

            ConfigHandler.checkPlayers(connection);
            ConfigHandler.loadWorlds(statement); // Load world ID's into memory.
            ConfigHandler.loadTypes(statement); // Load material ID's into memory.

            // Initialize WorldEdit logging
            if (Util.checkWorldEdit()) {
                PluginManager pluginManager = Bukkit.getServer().getPluginManager();
                Plugin worldEditPlugin = pluginManager.getPlugin("WorldEdit");
                if (worldEditPlugin != null && worldEditPlugin.isEnabled()) {
                    Util.loadWorldEdit();
                }
            }
            else if (ConfigHandler.worldeditEnabled) {
                Util.unloadWorldEdit();
            }

            ConfigHandler.serverRunning = true; // Set as running before patching
            boolean validVersion = Patch.versionCheck(statement); // Minor upgrades & version check
            boolean databaseLock = true;
            if (startup) {
                // Check that database isn't already in use
                databaseLock = ConfigHandler.checkDatabaseLock(statement);
            }

            statement.close();

            return validVersion && databaseLock;
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        return false;
    }

    public static void performDisable() {
        try {
            Database.closeConnection();
            ListenerHandler.unregisterNetworking(); // Unregister channels for networking API
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

}
