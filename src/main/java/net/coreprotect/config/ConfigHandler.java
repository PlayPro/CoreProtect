package net.coreprotect.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import java.util.concurrent.atomic.AtomicLong;

import net.coreprotect.CoreProtect;
import net.coreprotect.command.parser.MaterialParser;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.config.Configurator;
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
import net.coreprotect.utility.Chat;
import net.coreprotect.utility.Color;
import net.coreprotect.utility.ErrorReporter;
import net.coreprotect.utility.SystemUtils;
import net.coreprotect.utility.VersionUtils;

public class ConfigHandler extends Queue {

    public enum CacheType {
        MATERIALS, BLOCKDATA, ART, ENTITIES, WORLDS
    }

    public static int SERVER_VERSION = 0;
    public static final int EDITION_VERSION = 2;
    public static final String EDITION_BRANCH = VersionUtils.getBranch();
    public static final String EDITION_NAME = VersionUtils.getPluginName();
    public static final String COMMUNITY_EDITION = "ClickHouse Edition";
    public static final String JAVA_VERSION = "11.0";
    public static final String MINECRAFT_VERSION = "1.16.5";
    public static final String PATCH_VERSION = "24.0";
    public static final String LATEST_VERSION = "26.2";
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
    public static final boolean READ_ONLY = Boolean.getBoolean("net.earthmc.coreprotect-clickhouse.database.read-only");

    public static final String BLACKLIST_COMMENT_SEPARATOR = ";";
    public static final String BLACKLIST_FILTER_SEPARATOR = "@";
    public static final String BLACKLIST_FILENAME = "blacklist.txt";

    public static HikariDataSource hikariDataSource = null;
    public static final SystemUtils.ProcessorInfo processorInfo = SystemUtils.getProcessorInfo();
    public static final boolean isSpigot = true;
    public static final boolean isPaper = true;
    public static final boolean isFolia = VersionUtils.isFolia();
    public static volatile boolean serverRunning = false;
    public static volatile boolean converterRunning = false;
    public static volatile boolean purgeRunning = false;
    public static volatile boolean migrationRunning = false;
    public static volatile boolean pauseConsumer = false;
    public static volatile boolean worldeditEnabled = false;
    public static volatile boolean databaseReachable = true;
    public static final AtomicInteger MAX_WORLD_ID = new AtomicInteger();
    public static final AtomicInteger MAX_MATERIAL_ID = new AtomicInteger();
    public static final AtomicInteger MAX_BLOCKDATA_ID = new AtomicInteger();
    public static final AtomicInteger MAX_ENTITY_ID = new AtomicInteger();
    public static final AtomicInteger MAX_ART_ID = new AtomicInteger();
    public static final AtomicLong autoPurgeRowsPurged = new AtomicLong(0);

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
    public static Map<String, HashSet<String>> FilteredBlacklist = syncMap();
    public static Map<String, Integer> loggingChest = syncMap();
    public static Map<String, Integer> loggingItem = syncMap();
    public static ConcurrentHashMap<String, List<ItemStack[]>> oldContainer = new ConcurrentHashMap<>();
    public static ConcurrentHashMap<String, Set<String>> oldContainerViewers = new ConcurrentHashMap<>();
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
    public static ConcurrentHashMap<String, ConcurrentHashMap<String, Long>> dispenserNoChange = new ConcurrentHashMap<>();
    public static ConcurrentHashMap<String, Object[]> dispenserPending = new ConcurrentHashMap<>();
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
    public static Map<String, List<String>> lookupFlist = syncMap();
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

    public static void checkPlayers(Connection connection) {
        ConfigHandler.playerIdCache.clear();
        ConfigHandler.playerIdCacheReversed.clear();
        for (Player player : Bukkit.getServer().getOnlinePlayers()) {
            if (ConfigHandler.playerIdCache.get(player.getName().toLowerCase(Locale.ROOT)) == null) {
                UserStatement.loadId(connection, player.getName(), player.getUniqueId().toString());
            }
        }
    }

    public static void addOldContainerViewer(String locationSuffix, String loggingId) {
        ConfigHandler.oldContainerViewers.compute(locationSuffix, (key, viewers) -> {
            if (viewers == null) {
                viewers = ConcurrentHashMap.newKeySet();
            }
            viewers.add(loggingId);
            return viewers;
        });
    }

    public static void removeOldContainerViewer(String locationSuffix, String loggingId) {
        ConfigHandler.oldContainerViewers.computeIfPresent(locationSuffix, (key, viewers) -> {
            viewers.remove(loggingId);
            return viewers.isEmpty() ? null : viewers;
        });
    }

    public static boolean isBlacklisted(String user) {
        return ConfigHandler.blacklist.containsKey(user.toLowerCase(Locale.ROOT));
    }

    public static boolean isBlacklisted(String user, String object) {
        if (ConfigHandler.blacklist.containsKey(object) || ConfigHandler.blacklist.containsKey(user.toLowerCase(Locale.ROOT))) {
            return true;
        }
        return isFilterBlacklisted(user, object);
    }

    public static boolean isFilterBlacklisted(String user, String object) {
        HashSet<String> blUserSet = FilteredBlacklist.get(object);
        if (blUserSet == null) {
            return false;
        }
        return blUserSet.contains(user.toLowerCase(Locale.ROOT));
    }

    private static void loadBlacklist() {
        final CoreProtect plugin = CoreProtect.getInstance();

        ConfigHandler.blacklist.clear();

        final Path blacklistPath = plugin.getDataPath().resolve("blacklist.txt");
        if (!Files.exists(blacklistPath)) {
            return;
        }

        try (Stream<String> lines = Files.lines(blacklistPath, StandardCharsets.UTF_8)) {
            lines.forEach(line -> {
                line = line.replace(" ", "").toLowerCase(Locale.ROOT).split(BLACKLIST_COMMENT_SEPARATOR)[0];
                if (!line.isEmpty()) {
                    final String[] split = line.split(BLACKLIST_FILTER_SEPARATOR);
                    if (split.length == 1) {
                        ConfigHandler.blacklist.put(line, true);
                    } else {
                        ConfigHandler.FilteredBlacklist.computeIfAbsent(split[0], k -> new HashSet<>()).add(split[1]);
                    }
                }
            });
        } catch (IOException e) {
            plugin.getSLF4JLogger().warn("Failed to read blacklist.txt file", e);
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
            CoreProtect.getInstance().getSLF4JLogger().warn("An unexpected error occurred while loading the config", e);
        }
    }

    public static void loadDatabase() {
        // close old pool when we reload the database, e.g. in purge command
        Database.closeConnection();

        if (!Config.getGlobal().MYSQL) {
            throw new IllegalStateException("SQLite databases are not supported.");
        }
        else {
            Configurator.setLevel("com.clickhouse.jdbc.ClickHouseDriver", Level.OFF); // disable "v2 driver" spam
            Configurator.setLevel("com.clickhouse.client.api.Client", Level.OFF); // stop logging debug messages at info

            HikariConfig config = new HikariConfig();
            config.setDriverClassName("com.clickhouse.jdbc.ClickHouseDriver");

            config.setJdbcUrl("jdbc:clickhouse://" + ConfigHandler.host + ":" + ConfigHandler.port + "/" + ConfigHandler.database);
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
            config.addDataSourceProperty("useSSL", String.valueOf(Config.getGlobal().ENABLE_SSL));

            ConfigHandler.hikariDataSource = new HikariDataSource(config);
        }

        Database.createDatabaseTables(ConfigHandler.prefix, false, null, Config.getGlobal().MYSQL, false);
    }

    public static void loadMaterials(Statement statement) {
        try (final ResultSet rs = statement.executeQuery("SELECT id,material FROM " + ConfigHandler.prefix + "material_map")) {
            ConfigHandler.materials.clear();
            ConfigHandler.materialsReversed.clear();

            while (rs.next()) {
                int id = rs.getInt("id");
                String material = rs.getString("material");
                ConfigHandler.materials.put(material, id);
                ConfigHandler.materialsReversed.put(id, material);
                MAX_MATERIAL_ID.updateAndGet(curr -> Math.max(id, curr));
            }
        } catch (Exception e) {
            ErrorReporter.report(e);
        }
    }

    public static void loadBlockdata(Statement statement) {
        try (final ResultSet rs = statement.executeQuery("SELECT id,data FROM " + ConfigHandler.prefix + "blockdata_map")) {
            ConfigHandler.blockdata.clear();
            ConfigHandler.blockdataReversed.clear();

            while (rs.next()) {
                int id = rs.getInt("id");
                String data = rs.getString("data");
                ConfigHandler.blockdata.put(data, id);
                ConfigHandler.blockdataReversed.put(id, data);
                MAX_BLOCKDATA_ID.updateAndGet(curr -> Math.max(id, curr));
            }
        } catch (Exception e) {
            ErrorReporter.report(e);
        }
    }

    public static void loadArt(Statement statement) {
        try (final ResultSet rs = statement.executeQuery("SELECT id,art FROM " + ConfigHandler.prefix + "art_map")) {
            ConfigHandler.art.clear();
            ConfigHandler.artReversed.clear();

            while (rs.next()) {
                int id = rs.getInt("id");
                String art = rs.getString("art");
                ConfigHandler.art.put(art, id);
                ConfigHandler.artReversed.put(id, art);
                MAX_ART_ID.updateAndGet(curr -> Math.max(id, curr));
            }
        } catch (Exception e) {
            ErrorReporter.report(e);
        }
    }

    public static void loadEntities(Statement statement) {
        try (final ResultSet rs = statement.executeQuery("SELECT id,entity FROM " + ConfigHandler.prefix + "entity_map")) {
            ConfigHandler.entities.clear();
            ConfigHandler.entitiesReversed.clear();

            while (rs.next()) {
                 int id = rs.getInt("id");
                 String entity = rs.getString("entity");
                 ConfigHandler.entities.put(entity, id);
                 ConfigHandler.entitiesReversed.put(id, entity);
                 MAX_ENTITY_ID.updateAndGet(curr -> Math.max(id, curr));
            }
        } catch (SQLException e) {
            CoreProtect.getInstance().getSLF4JLogger().warn("Failed to load type tables from database", e);
        }
    }

    public static void loadTypes(Statement statement) {
        loadMaterials(statement);
        loadBlockdata(statement);
        loadArt(statement);
        loadEntities(statement);
    }

    /**
     * Unified method to reload cache from database when DATABASE_LOCK is false (multi-server setup)
     * 
     * @param type
     *            The type of cache to reload
     * @param name
     *            The name to look up after reload
     * @return The ID if found after reload, or -1 if not found
     */
    public static int reloadAndGetId(CacheType type, String name) {
        // Only reload if DATABASE_LOCK is false (multi-server setup)
        if (Config.getGlobal().DATABASE_LOCK) {
            return -1;
        }

        try (Connection connection = Database.getConnection(true)) {
            if (connection != null) {
                Statement statement = connection.createStatement();

                // Reload appropriate cache based on type
                switch (type) {
                    case MATERIALS:
                        loadMaterials(statement);
                        statement.close();
                        return materials.getOrDefault(name, -1);
                    case BLOCKDATA:
                        loadBlockdata(statement);
                        statement.close();
                        return blockdata.getOrDefault(name, -1);
                    case ART:
                        loadArt(statement);
                        statement.close();
                        return art.getOrDefault(name, -1);
                    case ENTITIES:
                        loadEntities(statement);
                        statement.close();
                        return entities.getOrDefault(name, -1);
                    case WORLDS:
                        loadWorlds(statement);
                        statement.close();
                        return worlds.getOrDefault(name, -1);
                    default:
                        statement.close();
                        return -1;
                }
            }
        }
        catch (Exception e) {
            ErrorReporter.report(e);
        }

        return -1;
    }

    public static void loadWorlds(Statement statement) {
        try (final ResultSet rs = statement.executeQuery("SELECT id,world FROM " + ConfigHandler.prefix + "world")) {
            ConfigHandler.worlds.clear();
            ConfigHandler.worldsReversed.clear();
            MAX_WORLD_ID.set(0);

            while (rs.next()) {
                int id = rs.getInt("id");
                String world = rs.getString("world");
                ConfigHandler.worlds.put(world, id);
                ConfigHandler.worldsReversed.put(id, world);
                MAX_WORLD_ID.updateAndGet(curr -> Math.max(id, curr));
            }

            for (final World world : Bukkit.getServer().getWorlds()) {
                String worldname = world.getName();
                if (!ConfigHandler.worlds.containsKey(worldname)) {
                    int id = MAX_WORLD_ID.incrementAndGet();
                    ConfigHandler.worlds.put(worldname, id);
                    ConfigHandler.worldsReversed.put(id, worldname);
                    Queue.queueWorldInsert(id, worldname);
                }
            }
        }
        catch (SQLException e) {
            CoreProtect.getInstance().getSLF4JLogger().warn("Failed to load worlds from database", e);
        }
    }

    private static boolean checkDatabaseLock(Statement statement) {
        if (!Config.getGlobal().DATABASE_LOCK) {
            return true;
        }

        try {
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
                    } else {
                        Chat.sendConsoleMessage(Color.RED + "[CoreProtect] " + Phrase.build(Phrase.DATABASE_LOCKED_2));
                        Chat.sendConsoleMessage(Color.GREY + "[CoreProtect] " + Phrase.build(Phrase.DATABASE_LOCKED_3));
                        Chat.sendConsoleMessage(Color.GREY + "[CoreProtect] " + Phrase.build(Phrase.DATABASE_LOCKED_4));
                        return false;
                    }

                    locked = true;
                }
                rs.close();
            }
        } catch (SQLException e) {
            CoreProtect.getInstance().getSLF4JLogger().error("Failed to check database lock", e);
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        return true;
    }

    public static boolean performInitialization(boolean startup) {
        try {
            BukkitAdapter.loadAdapter();
            PaperAdapter.loadAdapter();
            BlockGroup.initialize();
            MaterialParser.loadConfiguredTags(CoreProtect.getInstance());

            ConfigHandler.loadConfig(); // Load (or create) the configuration file.
            ConfigHandler.loadDatabase(); // Initialize MySQL and create tables if necessary.

            if (startup) {
                ListenerHandler.registerNetworking(); // Register channels for networking API
            }
        }
        catch (Exception e) {
            CoreProtect.getInstance().getSLF4JLogger().error("An unexpected error occurred while initializing", e);
        }

        try (Connection connection = Database.getConnection(true, 0)) {
            Statement statement = connection.createStatement();

            ConfigHandler.checkPlayers(connection);
            ConfigHandler.loadWorlds(statement); // Load world ID's into memory.
            ConfigHandler.loadTypes(statement); // Load material ID's into memory.

            // Initialize WorldEdit logging
            if (VersionUtils.checkWorldEdit()) {
                PluginManager pluginManager = Bukkit.getServer().getPluginManager();
                Plugin worldEditPlugin = pluginManager.getPlugin("WorldEdit");
                if (worldEditPlugin != null && worldEditPlugin.isEnabled()) {
                    VersionUtils.loadWorldEdit();
                }
            }
            else if (ConfigHandler.worldeditEnabled) {
                VersionUtils.unloadWorldEdit();
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
        } catch (SQLException e) {
            CoreProtect.getInstance().getSLF4JLogger().error("An unexpected error occurred while loading types/worlds/players", e);
        }

        return false;
    }

    public static void performDisable() {
        try {
            Database.closeConnection();
            ListenerHandler.unregisterNetworking(); // Unregister channels for networking API
        }
        catch (Exception e) {
            ErrorReporter.report(e);
        }
    }

}
