package net.coreprotect.config;

import java.io.File;
import java.io.RandomAccessFile;
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
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

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
import net.coreprotect.database.ConsumerWriteBatch;
import net.coreprotect.database.Database;
import net.coreprotect.database.DatabaseType;
import net.coreprotect.database.DuckDBNativeSupport;
import net.coreprotect.database.clickhouse.ClickHouseDatabase;
import net.coreprotect.database.clickhouse.ClickHouseJdbcConfig;
import net.coreprotect.database.statement.UserStatement;
import net.coreprotect.language.Phrase;
import net.coreprotect.listener.ListenerHandler;
import net.coreprotect.model.BlockGroup;
import net.coreprotect.model.action.EntityActionFilter;
import net.coreprotect.model.lookup.LookupOutputMode;
import net.coreprotect.model.lookup.LookupRollbackState;
import net.coreprotect.paper.PaperAdapter;
import net.coreprotect.patch.Patch;
import net.coreprotect.spigot.SpigotAdapter;
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
    public static final String COMMUNITY_EDITION = "Community Edition";
    public static final String JAVA_VERSION = "11.0";
    public static final String MINECRAFT_VERSION = "1.16.5";
    public static final String PATCH_VERSION = "24.0";
    public static final String LATEST_VERSION = "26.2";
    public static String path = "plugins/CoreProtect/";
    public static String sqlite = "database.db";
    public static String duckdb = "database.duckdb";
    public static String duckdbMemoryLimit = "512MB";
    public static String duckdbMaxTempDirectorySize = "10GB";
    public static int duckdbThreads = 2;
    public static String host = "127.0.0.1";
    public static int port = 3306;
    public static String database = "database";
    public static String username = "root";
    public static String password = "";
    public static String prefix = "co_";
    public static String prefixConfig = "co_";
    public static int maximumPoolSize = 10;
    public static DatabaseType databaseType = DatabaseType.SQLITE;

    public static final String BLACKLIST_COMMENT_SEPARATOR = ";";
    public static final String BLACKLIST_FILTER_SEPARATOR = "@";
    public static final String BLACKLIST_FILENAME = "blacklist.txt";

    public static HikariDataSource hikariDataSource = null;
    public static final SystemUtils.ProcessorInfo processorInfo = SystemUtils.getProcessorInfo();
    public static final boolean isSpigot = VersionUtils.isSpigot();
    public static final boolean isPaper = VersionUtils.isPaper();
    public static final boolean isFolia = VersionUtils.isFolia();
    public static volatile boolean serverRunning = false;
    public static volatile boolean shutdownDrainRunning = false;
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
    public static final AtomicLong autoPurgeRowsPurged = new AtomicLong(0);
    private static boolean duckDBFallbackAllowed = false;

    private static <K, V> Map<K, V> syncMap() {
        return Collections.synchronizedMap(new HashMap<>());
    }

    private static <K, V> Map<K, V> syncMap(Map<K, V> values) {
        return Collections.synchronizedMap(new HashMap<>(values));
    }

    private static IdentifierCache loadIdentifierCache(Statement statement, String table, String valueColumn)
            throws SQLException {
        Map<String, Integer> values = new HashMap<>();
        Map<Integer, String> reversed = new HashMap<>();
        int maximumId = 0;
        try (ResultSet resultSet = statement
                .executeQuery("SELECT id," + valueColumn + " FROM " + ConfigHandler.prefix + table)) {
            while (resultSet.next()) {
                int id = resultSet.getInt("id");
                String value = resultSet.getString(valueColumn);
                values.put(value, id);
                reversed.put(id, value);
                maximumId = Math.max(maximumId, id);
            }
        }
        return new IdentifierCache(values, reversed, maximumId);
    }

    private static final class IdentifierCache {

        private final Map<String, Integer> values;
        private final Map<Integer, String> reversed;
        private int maximumId;

        private IdentifierCache(Map<String, Integer> values, Map<Integer, String> reversed, int maximumId) {
            this.values = values;
            this.reversed = reversed;
            this.maximumId = maximumId;
        }
    }

    public static volatile Map<String, Integer> worlds = syncMap();
    public static volatile Map<Integer, String> worldsReversed = syncMap();
    public static volatile Map<String, Integer> materials = syncMap();
    public static volatile Map<Integer, String> materialsReversed = syncMap();
    public static volatile Map<String, Integer> blockdata = syncMap();
    public static volatile Map<Integer, String> blockdataReversed = syncMap();
    public static volatile Map<String, Integer> entities = syncMap();
    public static volatile Map<Integer, String> entitiesReversed = syncMap();
    public static volatile Map<String, Integer> art = syncMap();
    public static volatile Map<Integer, String> artReversed = syncMap();
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
    public static Map<String, LookupOutputMode> lookupOutputMode = syncMap();
    public static Map<String, LookupRollbackState> lookupRollbackState = syncMap();
    public static Map<String, String> lookupCommand = syncMap();
    public static Map<String, Integer> lookupEntityContainer = syncMap();
    public static Map<String, Integer> lookupEntityInteraction = syncMap();
    public static Map<String, List<Object>> lookupBlist = syncMap();
    public static Map<String, Map<Object, Boolean>> lookupElist = syncMap();
    public static Map<String, List<String>> lookupEUserlist = syncMap();
    public static Map<String, List<String>> lookupUlist = syncMap();
    public static Map<String, List<Integer>> lookupAlist = syncMap();
    public static Map<String, EntityActionFilter> lookupEntityActionFilter = syncMap();
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
        if (ConfigHandler.databaseType.isClickHouse()) {
            if (Bukkit.getServer().getOnlinePlayers().isEmpty()) {
                return;
            }
            try (ConsumerWriteBatch batch = Database.openConsumerWriteBatch(connection)) {
                batch.begin();
                for (Player player : Bukkit.getServer().getOnlinePlayers()) {
                    batch.resolveUserId(player.getName(), player.getUniqueId().toString());
                }
                if (!batch.commit()) {
                    throw new IllegalStateException("Unable to initialize ClickHouse player records");
                }
            } catch (Exception exception) {
                throw new IllegalStateException("Unable to initialize ClickHouse player records", exception);
            }
            return;
        }
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
        if (ConfigHandler.blacklist.containsKey(object)
                || ConfigHandler.blacklist.containsKey(user.toLowerCase(Locale.ROOT))) {
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
        try {
            ConfigHandler.blacklist.clear();
            ConfigHandler.FilteredBlacklist.clear();

            File file = new File(ConfigHandler.path, BLACKLIST_FILENAME);
            if (!file.exists()) {
                return;
            }
            try (RandomAccessFile blfile = new RandomAccessFile(file, "r")) {
                if (blfile.length() == 0) {
                    return;
                }
                String blLine;
                while ((blLine = blfile.readLine()) != null) {
                    blLine = blLine.replace(" ", "").toLowerCase(Locale.ROOT).split(BLACKLIST_COMMENT_SEPARATOR)[0];
                    if (blLine.isEmpty()) {
                        continue;
                    }
                    String[] blSplit = blLine.split(BLACKLIST_FILTER_SEPARATOR);
                    if (blSplit.length == 1) {
                        ConfigHandler.blacklist.put(blLine, true);
                    } else {
                        ConfigHandler.FilteredBlacklist.computeIfAbsent(blSplit[0], k -> new HashSet<>())
                                .add(blSplit[1]);
                    }
                }
            }
        } catch (Exception e) {
            ErrorReporter.report(e);
        }
    }

    private static void loadConfig() throws Exception {
        File configFolder = new File(ConfigHandler.path);
        File configFile = new File(configFolder, ConfigFile.CONFIG);
        File[] existingFiles = configFolder.listFiles();
        duckDBFallbackAllowed = !configFile.exists()
                && (!configFolder.exists() || (existingFiles != null && existingFiles.length == 0));

        Config.init();
        ConfigFile.init(ConfigFile.LANGUAGE); // load user phrases
        ConfigFile.init(ConfigFile.LANGUAGE_CACHE); // load translation cache

        Config global = Config.getGlobal();
        if (global.hasOption("database-type")) {
            ConfigHandler.databaseType = DatabaseType.parse(global.DATABASE_TYPE, global.MYSQL);
            if (global.hasOption("use-mysql") && global.MYSQL != ConfigHandler.databaseType.isMySQL()) {
                Chat.sendConsoleMessage(Color.YELLOW + "[CoreProtect] " + Phrase.build(Phrase.DATABASE_TYPE_OVERRIDE));
            }
        } else {
            ConfigHandler.databaseType = global.MYSQL ? DatabaseType.MYSQL : DatabaseType.SQLITE;
        }
        global.MYSQL = ConfigHandler.databaseType.isMySQL();

        ConfigHandler.prefixConfig = global.PREFIX;
        // Embedded databases use the fixed prefix expected by maintenance operations.
        if (ConfigHandler.databaseType.isEmbedded()) {
            global.PREFIX = "co_";
        }

        ConfigHandler.host = global.MYSQL_HOST;
        ConfigHandler.port = global.MYSQL_PORT;
        ConfigHandler.database = global.MYSQL_DATABASE;
        ConfigHandler.username = global.MYSQL_USERNAME;
        ConfigHandler.password = global.MYSQL_PASSWORD;
        ConfigHandler.maximumPoolSize = global.MAXIMUM_POOL_SIZE;
        ConfigHandler.duckdbMemoryLimit = global.DUCKDB_MEMORY_LIMIT;
        ConfigHandler.duckdbThreads = Math.max(1, global.DUCKDB_THREADS);
        ConfigHandler.duckdbMaxTempDirectorySize = global.DUCKDB_MAX_TEMP_DIRECTORY_SIZE;
        ConfigHandler.prefix = global.PREFIX;

        ConfigHandler.loadBlacklist(); // Load the blacklist file if it exists.
    }

    public static void loadDatabase() {
        loadDatabase(null, false);
    }

    public static void loadMigrationDatabase(ClickHouseDatabase preparedClickHouse) {
        if (preparedClickHouse == null) {
            throw new IllegalArgumentException("Prepared ClickHouse database cannot be null");
        }
        loadDatabase(preparedClickHouse, true);
    }

    private static void loadDatabase(ClickHouseDatabase preparedClickHouse, boolean migrationActivation) {
        if (preparedClickHouse != null && !ConfigHandler.databaseType.isClickHouse()) {
            throw new IllegalArgumentException("A prepared ClickHouse database can only activate a ClickHouse target");
        }
        ConfigHandler.databaseReachable = false;
        Database.closeConnection();
        try {
            if (preparedClickHouse != null) {
                Database.installClickHouseDatabase(preparedClickHouse);
            } else {
                initializeDatabase();
            }
            if (!migrationActivation && Database.hasIncompleteMigrationMarker()) {
                throw new IllegalStateException(
                        "Database contains an incomplete CoreProtect migration and cannot be activated");
            }
            ConfigHandler.databaseReachable = true;
        } catch (Exception exception) {
            Database.closeConnection();
            ConfigHandler.databaseReachable = false;
            if (exception instanceof RuntimeException) {
                throw (RuntimeException) exception;
            }
            throw new IllegalStateException("Failed to initialize database", exception);
        }
    }

    private static void initializeDatabase() {
        if (ConfigHandler.databaseType.isClickHouse()) {
            try {
                if (!Config.getGlobal().DATABASE_LOCK) {
                    throw new IllegalStateException("ClickHouse requires database-lock to remain enabled");
                }
                Config global = Config.getGlobal();
                ClickHouseJdbcConfig config = new ClickHouseJdbcConfig(global.CLICKHOUSE_HOST, global.CLICKHOUSE_PORT,
                        global.CLICKHOUSE_DATABASE, global.CLICKHOUSE_USERNAME, global.CLICKHOUSE_PASSWORD,
                        global.CLICKHOUSE_TLS);
                Database.initializeClickHouse(config, ConfigHandler.prefix);
            } catch (Exception exception) {
                throw new IllegalStateException("Failed to initialize ClickHouse", exception);
            }
            return;
        }

        if (ConfigHandler.databaseType.isEmbedded()) {
            try {
                File tempFile = File.createTempFile("CoreProtect_" + System.currentTimeMillis(), ".tmp");
                tempFile.setExecutable(true);

                boolean canExecute = false;
                try {
                    canExecute = tempFile.canExecute();
                } catch (Exception exception) {
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

                if (ConfigHandler.databaseType.isDuckDB() && duckDBFallbackAllowed) {
                    try {
                        DuckDBNativeSupport.verifyAvailable();
                    } catch (Throwable failure) {
                        if (!DuckDBNativeSupport.isNativeUnavailable(failure)) {
                            throw new IllegalStateException("Unable to verify DuckDB on this system", failure);
                        }
                        DatabaseConfigWriter.persistDatabaseType(DatabaseType.SQLITE);
                        ConfigHandler.databaseType = DatabaseType.SQLITE;
                        Config global = Config.getGlobal();
                        global.DATABASE_TYPE = DatabaseType.SQLITE.name().toLowerCase(Locale.ROOT);
                        global.MYSQL = false;
                        Chat.sendConsoleMessage(Color.YELLOW + "[CoreProtect] " + Phrase.build(Phrase.DATABASE_FALLBACK,
                                DatabaseType.DUCKDB.getDisplayName(), DatabaseType.SQLITE.getDisplayName()));
                    } finally {
                        duckDBFallbackAllowed = false;
                    }
                }

                Class.forName(ConfigHandler.databaseType.isDuckDB() ? "org.duckdb.DuckDBDriver" : "org.sqlite.JDBC");
            } catch (Exception e) {
                ErrorReporter.report(e);
            }
        } else {
            HikariConfig config = new HikariConfig();
            try {
                Class.forName("com.mysql.cj.jdbc.Driver");
                config.setDriverClassName("com.mysql.cj.jdbc.Driver");
            } catch (Exception e) {
                config.setDriverClassName("com.mysql.jdbc.Driver");
            }

            config.setJdbcUrl(
                    "jdbc:mysql://" + ConfigHandler.host + ":" + ConfigHandler.port + "/" + ConfigHandler.database);
            config.setUsername(ConfigHandler.username);
            config.setPassword(ConfigHandler.password);
            config.setMaximumPoolSize(ConfigHandler.maximumPoolSize);
            config.setConnectionTimeout(10000);
            // Keep connection age below short limits used by some managed MySQL hosts.
            config.setMaxLifetime(60000);
            config.setKeepaliveTime(0);
            config.addDataSourceProperty("characterEncoding", "UTF-8");
            config.addDataSourceProperty("connectTimeout", "10000");
            /* https://github.com/brettwooldridge/HikariCP/wiki/MySQL-Configuration */
            /*
             * https://cdn.oreillystatic.com/en/assets/1/event/21/Connector_J%20Performance%
             * 20Gems%20Presentation.pdf
             */
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

        Database.createDatabaseTables(ConfigHandler.prefix, false, null, ConfigHandler.databaseType, false);
    }

    public static boolean loadMaterials(Statement statement) {
        try {
            IdentifierCache cache = loadIdentifierCache(statement, "material_map", "material");
            ConfigHandler.materials = syncMap(cache.values);
            ConfigHandler.materialsReversed = syncMap(cache.reversed);
            materialId = cache.maximumId;
            return true;
        } catch (Exception e) {
            ErrorReporter.report(e);
            return false;
        }
    }

    public static boolean loadBlockdata(Statement statement) {
        try {
            IdentifierCache cache = loadIdentifierCache(statement, "blockdata_map", "data");
            ConfigHandler.blockdata = syncMap(cache.values);
            ConfigHandler.blockdataReversed = syncMap(cache.reversed);
            blockdataId = cache.maximumId;
            return true;
        } catch (Exception e) {
            ErrorReporter.report(e);
            return false;
        }
    }

    public static boolean loadArt(Statement statement) {
        try {
            IdentifierCache cache = loadIdentifierCache(statement, "art_map", "art");
            ConfigHandler.art = syncMap(cache.values);
            ConfigHandler.artReversed = syncMap(cache.reversed);
            artId = cache.maximumId;
            return true;
        } catch (Exception e) {
            ErrorReporter.report(e);
            return false;
        }
    }

    public static boolean loadEntities(Statement statement) {
        try {
            IdentifierCache cache = loadIdentifierCache(statement, "entity_map", "entity");
            ConfigHandler.entities = syncMap(cache.values);
            ConfigHandler.entitiesReversed = syncMap(cache.reversed);
            entityId = cache.maximumId;
            return true;
        } catch (Exception e) {
            ErrorReporter.report(e);
            return false;
        }
    }

    public static boolean loadTypes(Statement statement) {
        boolean loaded = loadMaterials(statement);
        loaded &= loadBlockdata(statement);
        loaded &= loadArt(statement);
        loaded &= loadEntities(statement);
        return loaded;
    }

    /**
     * Reloads an identifier cache when multi-server cache refresh is permitted.
     * 
     * @param type
     *             The type of cache to reload
     * @param name
     *             The name to look up after reload
     * @return The ID if found after reload, or -1 if not found
     */
    public static synchronized int reloadAndGetId(CacheType type, String name) {
        if (Config.getGlobal().DATABASE_LOCK) {
            return -1;
        }

        if (!reloadIdentifierCache(type)) {
            return -1;
        }

        switch (type) {
            case MATERIALS:
                return materials.getOrDefault(name, -1);
            case BLOCKDATA:
                return blockdata.getOrDefault(name, -1);
            case ART:
                return art.getOrDefault(name, -1);
            case ENTITIES:
                return entities.getOrDefault(name, -1);
            case WORLDS:
                return worlds.getOrDefault(name, -1);
            default:
                return -1;
        }
    }

    private static boolean reloadIdentifierCache(CacheType type) {
        try (Connection connection = Database.getConnection(true)) {
            if (connection == null) {
                return false;
            }
            try (Statement statement = connection.createStatement()) {
                switch (type) {
                    case MATERIALS:
                        return loadMaterials(statement);
                    case BLOCKDATA:
                        return loadBlockdata(statement);
                    case ART:
                        return loadArt(statement);
                    case ENTITIES:
                        return loadEntities(statement);
                    case WORLDS:
                        return loadWorlds(statement);
                    default:
                        return false;
                }
            }
        } catch (Exception e) {
            ErrorReporter.report(e);
            return false;
        }
    }

    public static boolean loadWorlds(Statement statement) {
        try {
            IdentifierCache cache = loadIdentifierCache(statement, "world", "world");
            List<World> worlds = Bukkit.getServer().getWorlds();
            Map<Integer, String> queuedWorlds = new HashMap<>();
            for (World world : worlds) {
                String worldname = world.getName();
                if (!cache.values.containsKey(worldname)) {
                    int id = ++cache.maximumId;
                    cache.values.put(worldname, id);
                    cache.reversed.put(id, worldname);
                    queuedWorlds.put(id, worldname);
                }
            }
            ConfigHandler.worlds = syncMap(cache.values);
            ConfigHandler.worldsReversed = syncMap(cache.reversed);
            worldId = cache.maximumId;
            for (Map.Entry<Integer, String> world : queuedWorlds.entrySet()) {
                Queue.queueWorldInsert(world.getKey(), world.getValue());
            }
            return true;
        } catch (Exception e) {
            ErrorReporter.report(e);
            return false;
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
                    String query = "SELECT * FROM " + ConfigHandler.prefix
                            + "database_lock WHERE rowid='1' AND (status='"
                            + Database.DATABASE_LOCK_MIGRATION_INCOMPLETE
                            + "' OR (status='" + Database.DATABASE_LOCK_ACTIVE + "' AND time >= '" + checkTime
                            + "')) LIMIT 1";
                    ResultSet rs = statement.executeQuery(query);
                    while (rs.next()) {
                        if (unixtimestamp < waitTime) {
                            if (!lockMessage) {
                                Chat.sendConsoleMessage("[CoreProtect] " + Phrase.build(Phrase.DATABASE_LOCKED_1));
                                lockMessage = true;
                            }
                            Thread.sleep(1000);
                        } else {
                            Chat.sendConsoleMessage(
                                    Color.RED + "[CoreProtect] " + Phrase.build(Phrase.DATABASE_LOCKED_2));
                            Chat.sendConsoleMessage(
                                    Color.GREY + "[CoreProtect] " + Phrase.build(Phrase.DATABASE_LOCKED_3));
                            Chat.sendConsoleMessage(
                                    Color.GREY + "[CoreProtect] " + Phrase.build(Phrase.DATABASE_LOCKED_4));
                            return false;
                        }

                        locked = true;
                    }
                    rs.close();
                }
            }
        } catch (Exception e) {
            ErrorReporter.report(e);
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
        } catch (CancellationException e) {
            return false;
        } catch (Exception e) {
            ErrorReporter.report(e);
            return false;
        }

        try (Connection connection = Database.getConnection(true, 0)) {
            Statement statement = connection.createStatement();

            ConfigHandler.checkPlayers(connection);
            boolean worldsLoaded = ConfigHandler.loadWorlds(statement); // Load world ID's into memory.
            boolean typesLoaded = ConfigHandler.loadTypes(statement); // Load material ID's into memory.
            if (ConfigHandler.databaseType.isColumnar() && (!worldsLoaded || !typesLoaded)) {
                throw new IllegalStateException("Unable to initialize columnar database identifier caches");
            }

            // Initialize WorldEdit logging
            if (VersionUtils.checkWorldEdit()) {
                PluginManager pluginManager = Bukkit.getServer().getPluginManager();
                Plugin worldEditPlugin = pluginManager.getPlugin("WorldEdit");
                if (worldEditPlugin != null && worldEditPlugin.isEnabled()) {
                    VersionUtils.loadWorldEdit();
                }
            } else if (ConfigHandler.worldeditEnabled) {
                VersionUtils.unloadWorldEdit();
            }

            if (startup) {
                ConfigHandler.serverRunning = true; // Set as running before patching
            }
            boolean validVersion = Patch.versionCheck(statement); // Minor upgrades & version check
            boolean databaseLock = true;
            if (startup) {
                // Check that database isn't already in use
                databaseLock = ConfigHandler.checkDatabaseLock(statement);
            }

            statement.close();

            return validVersion && databaseLock;
        } catch (Exception e) {
            ErrorReporter.report(e);
        }

        return false;
    }

    public static void performDisable() {
        try {
            Database.closeConnection();
            ListenerHandler.unregisterNetworking(); // Unregister channels for networking API
        } catch (Exception e) {
            ErrorReporter.report(e);
        }
    }

}
