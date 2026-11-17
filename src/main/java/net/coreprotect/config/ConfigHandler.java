package net.coreprotect.config;

import java.io.BufferedReader;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import net.coreprotect.CoreProtect;
import net.coreprotect.bukkit.BukkitAdapter;
import net.coreprotect.consumer.Consumer;
import net.coreprotect.consumer.Queue;
import net.coreprotect.consumer.process.Process;
import net.coreprotect.database.ConsumerWriteBatch;
import net.coreprotect.database.ConsumerWriteBatch.ReferenceKind;
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
import net.coreprotect.model.lookup.LookupCursor;
import net.coreprotect.model.lookup.LookupOutputMode;
import net.coreprotect.model.lookup.LookupRollbackState;
import net.coreprotect.paper.PaperAdapter;
import net.coreprotect.patch.Patch;
import net.coreprotect.spigot.SpigotAdapter;
import net.coreprotect.thread.Scheduler;
import net.coreprotect.utility.Chat;
import net.coreprotect.utility.Color;
import net.coreprotect.utility.EntitySpawnTracking;
import net.coreprotect.utility.ErrorReporter;
import net.coreprotect.utility.SystemUtils;
import net.coreprotect.utility.VersionUtils;

public class ConfigHandler extends Queue {

    public enum CacheType {
        MATERIALS(
                ReferenceKind.MATERIAL
        ),
        BLOCKDATA(
                ReferenceKind.BLOCK_DATA
        ),
        ART(
                ReferenceKind.ART
        ),
        ENTITIES(
                ReferenceKind.ENTITY
        ),
        WORLDS(
                ReferenceKind.WORLD
        );

        private final ReferenceKind kind;

        CacheType(ReferenceKind kind) {
            this.kind = kind;
        }
    }

    public static int SERVER_VERSION = 0;
    public static final int EDITION_VERSION = 2;
    public static final String EDITION_BRANCH = VersionUtils.getBranch();
    public static final String EDITION_NAME = VersionUtils.getPluginName();
    public static final String COMMUNITY_EDITION = "Community Edition";
    public static final String JAVA_VERSION = "11.0";
    public static final String MINECRAFT_VERSION = "1.16.5";
    public static final String PATCH_VERSION = "25.0";
    public static final String LATEST_VERSION = "26.3";
    private static final String DEFAULT_SQLITE_DATABASE = "database.db";
    private static final Pattern TABLE_PREFIX_PATTERN = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    public static String path = "plugins/CoreProtect/";
    public static String sqlite = DEFAULT_SQLITE_DATABASE;
    public static String duckdb = "database.duckdb";
    public static String duckdbMemoryLimit = "512MB";
    public static String duckdbMaxTempDirectorySize = "10GB";
    public static int duckdbThreads = 3;
    public static String host = "127.0.0.1";
    public static int port = 3306;
    public static String database = "database";
    public static String username = "root";
    public static String password = "";
    public static String prefix = "co_";
    public static String prefixConfig = "co_";
    public static int maximumPoolSize = 10;
    public static DatabaseType databaseType = DatabaseType.SQLITE;

    public static String getDescendingEventOrder() {
        return databaseType.isClickHouse() ? "time DESC,rowid DESC" : "rowid DESC";
    }

    public static String getAscendingEventOrder() {
        return databaseType.isClickHouse() ? "time ASC,rowid ASC" : "rowid ASC";
    }

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

    private static IdentifierCache loadIdentifierCache(Statement statement, String table, String valueColumn) throws SQLException {
        Map<String, Integer> values = new HashMap<>();
        Map<Integer, String> reversed = new HashMap<>();
        int maximumId = 0;
        try (ResultSet resultSet = statement.executeQuery("SELECT id," + valueColumn + " FROM " + ConfigHandler.prefix + table)) {
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

    private static final class IdentifierStore {
        private final ReferenceKind kind;
        private final Supplier<Map<String, Integer>> values;
        private final Supplier<Map<Integer, String>> reversed;
        private final IntSupplier maximum;
        private final IntConsumer maximumSetter;

        private IdentifierStore(ReferenceKind kind, Supplier<Map<String, Integer>> values, Supplier<Map<Integer, String>> reversed, IntSupplier maximum, IntConsumer maximumSetter) {
            this.kind = kind;
            this.values = values;
            this.reversed = reversed;
            this.maximum = maximum;
            this.maximumSetter = maximumSetter;
        }

        private boolean store(int id, String value) {
            if (!canStore(id, value)) {
                return false;
            }
            Map<String, Integer> names = values.get();
            Map<Integer, String> identifiers = reversed.get();
            names.put(value, id);
            identifiers.put(id, value);
            maximumSetter.accept(Math.max(maximum.getAsInt(), id));
            return true;
        }

        private boolean canStore(int id, String value) {
            if (id < 1) {
                return false;
            }
            Map<String, Integer> names = values.get();
            Map<Integer, String> identifiers = reversed.get();
            Integer storedId = names.get(value);
            String storedValue = identifiers.get(id);
            if ((storedId != null && storedId != id) || (storedValue != null && !storedValue.equals(value))) {
                throw new IllegalStateException("CoreProtect " + kind.name().toLowerCase(Locale.ROOT) + " identifier " + id + "='" + value + "' conflicts with cached " + storedId + "='" + storedValue + "'");
            }
            return true;
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
    private static final Map<ReferenceKind, IdentifierStore> IDENTIFIER_STORES = identifierStores();
    public static Map<String, int[]> rollbackHash = syncMap();
    public static Map<String, Boolean> inspecting = syncMap();
    public static volatile Map<String, Boolean> blacklist = new ConcurrentHashMap<>();
    public static volatile Map<String, HashSet<String>> FilteredBlacklist = new ConcurrentHashMap<>();
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
    public static Map<String, LookupCursor> lookupCursor = syncMap();
    public static Map<String, String> uuidCache = syncMap();
    public static Map<String, String> uuidCacheReversed = syncMap();
    public static Map<String, Integer> playerIdCache = syncMap();
    public static Map<Integer, String> playerIdCacheReversed = syncMap();
    public static Map<String, List<Object>> lastRollback = syncMap();
    public static Map<String, Boolean> activeRollbacks = syncMap();
    public static Map<String, Object[]> entityBlockMapper = new ConcurrentHashMap<>();
    public static ConcurrentHashMap<UUID, ConcurrentHashMap<Long, Long>> populatedChunks = new ConcurrentHashMap<>();
    public static ConcurrentHashMap<String, String> language = new ConcurrentHashMap<>();
    public static List<String> databaseTables = new ArrayList<>();

    private static final class PlayerSnapshot {
        private final String name;
        private final String uuid;

        private PlayerSnapshot(String name, String uuid) {
            this.name = name;
            this.uuid = uuid;
        }
    }

    private static final class ServerStateSnapshot {
        private final List<PlayerSnapshot> onlinePlayers;
        private final List<String> worldNames;

        private ServerStateSnapshot(List<PlayerSnapshot> onlinePlayers, List<String> worldNames) {
            this.onlinePlayers = onlinePlayers;
            this.worldNames = worldNames;
        }
    }

    public static void checkPlayers(Connection connection) {
        checkPlayers(connection, supplyOnPrimaryThread(ConfigHandler::captureOnlinePlayers));
    }

    private static void checkPlayers(Connection connection, List<PlayerSnapshot> onlinePlayers) {
        ConfigHandler.playerIdCache.clear();
        ConfigHandler.playerIdCacheReversed.clear();
        if (ConfigHandler.databaseType.isClickHouse()) {
            if (onlinePlayers.isEmpty()) {
                return;
            }
            try (ConsumerWriteBatch batch = Database.openConsumerWriteBatch(connection)) {
                batch.begin();
                int time = (int) (System.currentTimeMillis() / 1_000L);
                for (PlayerSnapshot player : onlinePlayers) {
                    batch.recordUsername(player.name, player.uuid, 0, time);
                }
                if (!batch.commit()) {
                    throw new IllegalStateException("Unable to initialize ClickHouse player records");
                }
            }
            catch (Exception exception) {
                throw new IllegalStateException("Unable to initialize ClickHouse player records", exception);
            }
            return;
        }
        for (PlayerSnapshot player : onlinePlayers) {
            if (ConfigHandler.playerIdCache.get(player.name.toLowerCase(Locale.ROOT)) == null) {
                UserStatement.loadId(connection, player.name, player.uuid);
            }
        }
    }

    private static List<PlayerSnapshot> captureOnlinePlayers() {
        List<PlayerSnapshot> players = new ArrayList<>();
        for (Player player : Bukkit.getServer().getOnlinePlayers()) {
            players.add(new PlayerSnapshot(player.getName(), player.getUniqueId().toString()));
        }
        return players;
    }

    private static List<String> captureWorldNames() {
        List<String> worldNames = new ArrayList<>();
        for (World world : Bukkit.getServer().getWorlds()) {
            worldNames.add(world.getName());
        }
        return worldNames;
    }

    private static ServerStateSnapshot captureServerStateSnapshot() {
        return new ServerStateSnapshot(captureOnlinePlayers(), captureWorldNames());
    }

    private static <T> T supplyOnPrimaryThread(Supplier<T> supplier) {
        if (Bukkit.isPrimaryThread()) {
            return supplier.get();
        }
        final CompletableFuture<T> complete = new CompletableFuture<>();
        final CompletableFuture<Void> shutdown = Consumer.databaseReloadShutdownSignal();
        Scheduler.runTask(CoreProtect.getInstance(), () -> {
            if (shutdown.isDone()) {
                return;
            }
            try {
                complete.complete(supplier.get());
            }
            catch (Throwable throwable) {
                if (throwable instanceof ThreadDeath) {
                    throw (ThreadDeath) throwable;
                }
                complete.completeExceptionally(throwable);
            }
        });
        CompletableFuture.anyOf(complete, shutdown).join();
        if (!complete.isDone()) {
            complete.cancel(false);
            throw new CancellationException("Configuration reload cancelled for shutdown");
        }
        return complete.join();
    }

    private static void runOnPrimaryThread(Runnable task) {
        supplyOnPrimaryThread(() -> {
            task.run();
            return null;
        });
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

    private static String stripWhitespace(String value) {
        StringBuilder sb = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (!Character.isWhitespace(c)) {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    public static boolean isBlacklisted(String user) {
        if (user == null || user.isEmpty()) {
            return false;
        }
        return ConfigHandler.blacklist.containsKey(user.toLowerCase(Locale.ROOT));
    }

    public static boolean isBlacklisted(String user, String object) {
        if (isBlacklisted(user) || isObjectBlacklisted(object)) {
            return true;
        }
        return isFilterBlacklisted(user, object);
    }

    private static boolean isObjectBlacklisted(String object) {
        if (object == null || object.isEmpty()) {
            return false;
        }
        String normalized = object.toLowerCase(Locale.ROOT);
        if (ConfigHandler.blacklist.containsKey(normalized)) {
            return true;
        }
        String alias = objectAlias(normalized);
        return alias != null && ConfigHandler.blacklist.containsKey(alias);
    }

    public static boolean isFilterBlacklisted(String user, String object) {
        if (user == null || user.isEmpty() || object == null || object.isEmpty()) {
            return false;
        }
        String normalizedUser = user.toLowerCase(Locale.ROOT);
        String normalizedObject = object.toLowerCase(Locale.ROOT);
        if (containsFilteredUser(normalizedObject, normalizedUser)) {
            return true;
        }
        String alias = objectAlias(normalizedObject);
        return alias != null && containsFilteredUser(alias, normalizedUser);
    }

    private static boolean containsFilteredUser(String objectKey, String normalizedUser) {
        HashSet<String> blUserSet = FilteredBlacklist.get(objectKey);
        return blUserSet != null && blUserSet.contains(normalizedUser);
    }

    private static String objectAlias(String normalizedObject) {
        if (normalizedObject.startsWith("/")) {
            return null;
        }
        if (normalizedObject.startsWith("minecraft:")) {
            return normalizedObject.substring("minecraft:".length());
        }
        if (!normalizedObject.contains(":")) {
            return "minecraft:" + normalizedObject;
        }
        return null;
    }

    public static boolean isCommandBlacklisted(String user, String message) {
        if (isBlacklisted(user)) {
            return true;
        }
        if (message == null || message.isEmpty()) {
            return false;
        }
        String trimmed = message.trim();
        if (trimmed.isEmpty()) {
            return false;
        }
        int end = 0;
        while (end < trimmed.length() && !Character.isWhitespace(trimmed.charAt(end))) {
            end++;
        }
        String token = trimmed.substring(0, end).toLowerCase(Locale.ROOT);
        if (isExactOrFilteredBlacklisted(user, token)) {
            return true;
        }
        int slashEnd = 0;
        while (slashEnd < token.length() && token.charAt(slashEnd) == '/') {
            slashEnd++;
        }
        if (slashEnd == token.length()) {
            return false;
        }
        String bareWithNamespace = token.substring(slashEnd);
        if (slashEnd != 1 && isExactOrFilteredBlacklisted(user, "/" + bareWithNamespace)) {
            return true;
        }
        if (slashEnd > 0 && isExactOrFilteredBlacklisted(user, bareWithNamespace)) {
            return true;
        }
        if (slashEnd == 0 && isExactOrFilteredBlacklisted(user, "/" + bareWithNamespace)) {
            return true;
        }
        int colon = bareWithNamespace.indexOf(':');
        if (colon >= 0 && colon + 1 < bareWithNamespace.length()) {
            String bareCommand = bareWithNamespace.substring(colon + 1);
            if (isExactOrFilteredBlacklisted(user, "/" + bareCommand)
                    || isExactOrFilteredBlacklisted(user, bareCommand)
                    || (slashEnd >= 2 && (isExactOrFilteredBlacklisted(user, "//" + bareCommand)
                            || (slashEnd > 2 && isExactOrFilteredBlacklisted(user, token.substring(0, slashEnd) + bareCommand))))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isExactOrFilteredBlacklisted(String user, String key) {
        if (ConfigHandler.blacklist.containsKey(key)) {
            return true;
        }
        if (user == null || user.isEmpty()) {
            return false;
        }
        return containsFilteredUser(key, user.toLowerCase(Locale.ROOT));
    }

    private static void loadBlacklist() {
        Map<String, Boolean> loadedBlacklist = new ConcurrentHashMap<>();
        Map<String, HashSet<String>> loadedFiltered = new ConcurrentHashMap<>();
        try {
            File file = new File(ConfigHandler.path, BLACKLIST_FILENAME);
            if (file.exists() && file.length() > 0) {
                try (BufferedReader blfile = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
                    boolean firstLine = true;
                    String blLine;
                    while ((blLine = blfile.readLine()) != null) {
                        if (firstLine) {
                            firstLine = false;
                            if (!blLine.isEmpty() && blLine.charAt(0) == '\uFEFF') {
                                blLine = blLine.substring(1);
                            }
                        }
                        int commentIndex = blLine.indexOf(BLACKLIST_COMMENT_SEPARATOR);
                        if (commentIndex >= 0) {
                            blLine = blLine.substring(0, commentIndex);
                        }
                        blLine = stripWhitespace(blLine).toLowerCase(Locale.ROOT);
                        if (blLine.isEmpty()) {
                            continue;
                        }
                        String[] blSplit = blLine.split(BLACKLIST_FILTER_SEPARATOR, 2);
                        if (blSplit.length == 1) {
                            loadedBlacklist.put(blLine, true);
                        }
                        else if (blSplit.length == 2 && !blSplit[0].isEmpty() && !blSplit[1].isEmpty()) {
                            loadedFiltered.computeIfAbsent(blSplit[0], k -> new HashSet<>()).add(blSplit[1]);
                        }
                    }
                }
            }
        }
        catch (Exception e) {
            ErrorReporter.report(e);
            return;
        }
        ConfigHandler.blacklist = loadedBlacklist;
        ConfigHandler.FilteredBlacklist = loadedFiltered;
    }

    private static void loadConfig() throws Exception {
        File configFolder = new File(ConfigHandler.path);
        File configFile = new File(configFolder, ConfigFile.CONFIG);
        File[] existingFiles = configFolder.listFiles();
        duckDBFallbackAllowed = !configFile.exists() && (!configFolder.exists() || (existingFiles != null && existingFiles.length == 0));

        Config.init();
        ConfigFile.init(ConfigFile.LANGUAGE); // load user phrases
        ConfigFile.init(ConfigFile.LANGUAGE_CACHE); // load translation cache

        Config global = Config.getGlobal();
        if (global.hasOption("database-type")) {
            ConfigHandler.databaseType = DatabaseType.parse(global.DATABASE_TYPE, global.MYSQL);
            if (global.hasOption("use-mysql") && global.MYSQL != ConfigHandler.databaseType.isMySQL()) {
                Chat.sendConsoleMessage(Color.YELLOW + "[CoreProtect] " + Phrase.build(Phrase.DATABASE_TYPE_OVERRIDE));
            }
        }
        else {
            ConfigHandler.databaseType = global.MYSQL ? DatabaseType.MYSQL : DatabaseType.SQLITE;
        }
        global.MYSQL = ConfigHandler.databaseType.isMySQL();

        ConfigHandler.prefixConfig = global.PREFIX;
        // Embedded databases use the fixed prefix expected by maintenance operations.
        if (ConfigHandler.databaseType.isEmbedded()) {
            global.PREFIX = "co_";
        }
        else if (global.PREFIX == null || !TABLE_PREFIX_PATTERN.matcher(global.PREFIX).matches()) {
            throw new IllegalArgumentException("Invalid table-prefix \"" + global.PREFIX + "\"; must match [A-Za-z_][A-Za-z0-9_]*");
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

        // Optional custom SQLite database filename (hidden option) inside the CoreProtect data folder.
        String sqliteDatabase = global.SQLITE_DATABASE.trim();
        ConfigHandler.sqlite = sqliteDatabase.isEmpty() ? DEFAULT_SQLITE_DATABASE : validateSqliteDatabaseFilename(sqliteDatabase);

        ConfigHandler.loadBlacklist(); // Load the blacklist file if it exists.
    }

    private static String validateSqliteDatabaseFilename(String sqliteDatabase) {
        if (sqliteDatabase.indexOf('/') >= 0 || sqliteDatabase.indexOf('\\') >= 0 || sqliteDatabase.indexOf('\0') >= 0
                || ".".equals(sqliteDatabase) || "..".equals(sqliteDatabase)) {
            throw new IllegalArgumentException("Invalid sqlite-database filename \"" + sqliteDatabase + "\"");
        }
        try {
            Path baseDir = Paths.get(ConfigHandler.path).toAbsolutePath().normalize();
            Path candidate = Paths.get(sqliteDatabase);
            if (candidate.isAbsolute() || candidate.getRoot() != null || candidate.getNameCount() != 1) {
                throw new IllegalArgumentException("Invalid sqlite-database filename \"" + sqliteDatabase + "\"");
            }
            Path resolved = baseDir.resolve(candidate).normalize();
            if (!baseDir.equals(resolved.getParent())) {
                throw new IllegalArgumentException("Invalid sqlite-database filename \"" + sqliteDatabase + "\"");
            }
            return sqliteDatabase;
        }
        catch (InvalidPathException exception) {
            throw new IllegalArgumentException("Invalid sqlite-database filename \"" + sqliteDatabase + "\"", exception);
        }
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
            }
            else {
                initializeDatabase();
            }
            if (!migrationActivation && Database.hasIncompleteMigrationMarker()) {
                throw new IllegalStateException("Database contains an incomplete CoreProtect migration and cannot be activated");
            }
            ConfigHandler.databaseReachable = true;
        }
        catch (Exception exception) {
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
                Config global = Config.getGlobal();
                ClickHouseJdbcConfig config = new ClickHouseJdbcConfig(global.CLICKHOUSE_HOST, global.CLICKHOUSE_PORT, global.CLICKHOUSE_DATABASE, global.CLICKHOUSE_USERNAME, global.CLICKHOUSE_PASSWORD, global.CLICKHOUSE_TLS);
                Database.initializeClickHouse(config, ConfigHandler.prefix);
            }
            catch (Exception exception) {
                throw new IllegalStateException("Failed to initialize ClickHouse", exception);
            }
            return;
        }

        if (ConfigHandler.databaseType.isEmbedded()) {
            try {
                if (ConfigHandler.databaseType.isDuckDB() && duckDBFallbackAllowed) {
                    try {
                        DuckDBNativeSupport.verifyAvailable();
                    }
                    catch (Throwable failure) {
                        if (!DuckDBNativeSupport.isNativeUnavailable(failure)) {
                            throw new IllegalStateException("Unable to verify DuckDB on this system. For native-library extraction or loading errors, check free space and permissions in the JVM temporary directory. " + "To select another directory, add -Djava.io.tmpdir=/absolute/path/to/cache before -jar and fully restart the server. " + "The directory must already exist, have sufficient space, and permit writing and native-library loading.", failure);
                        }
                        DatabaseConfigWriter.persistDatabaseType(DatabaseType.SQLITE);
                        ConfigHandler.databaseType = DatabaseType.SQLITE;
                        Config global = Config.getGlobal();
                        global.DATABASE_TYPE = DatabaseType.SQLITE.name().toLowerCase(Locale.ROOT);
                        global.MYSQL = false;
                        Chat.sendConsoleMessage(Color.YELLOW + "[CoreProtect] " + Phrase.build(Phrase.DATABASE_FALLBACK, DatabaseType.DUCKDB.getDisplayName(), DatabaseType.SQLITE.getDisplayName()));
                    }
                    finally {
                        duckDBFallbackAllowed = false;
                    }
                }

                Class.forName(ConfigHandler.databaseType.isDuckDB() ? "org.duckdb.DuckDBDriver" : "org.sqlite.JDBC");
            }
            catch (Exception e) {
                throw new IllegalStateException("Failed to initialize " + ConfigHandler.databaseType.getDisplayName(), e);
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
        }
        catch (Exception e) {
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
        }
        catch (Exception e) {
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
        }
        catch (Exception e) {
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
        }
        catch (Exception e) {
            ErrorReporter.report(e);
            return false;
        }
    }

    public static boolean loadTypes(Statement statement) {
        boolean loaded = loadMaterials(statement);
        loaded &= loadBlockdata(statement);
        loaded &= loadArt(statement);
        loaded &= loadEntities(statement);
        if (loaded && databaseType.isClickHouse() && !Config.getGlobal().DATABASE_LOCK) {
            for (EntityType type : EntityType.values()) {
                if (EntitySpawnTracking.isPlacedEntityType(type)) {
                    resolveIdentifierId(CacheType.ENTITIES, type.name().toLowerCase(Locale.ROOT), true);
                }
            }
        }
        return loaded;
    }

    /**
     * Reloads an identifier cache when multi-server cache refresh is permitted.
     *
     * @param type
     *            The type of cache to reload
     * @param name
     *            The name to look up after reload
     * @return The ID if found after reload, or -1 if not found
     */
    public static synchronized int reloadAndGetId(CacheType type, String name) {
        if (Config.getGlobal().DATABASE_LOCK || !reloadIdentifierCache(type.kind)) {
            return -1;
        }
        return identifierStore(type.kind).values.get().getOrDefault(name, -1);
    }

    public static int resolveIdentifierId(CacheType type, String value, boolean create) {
        IdentifierStore store = identifierStore(type.kind);
        Integer cached = store.values.get().get(value);
        return cached != null ? cached : resolveMissingIdentifierId(store, value, create);
    }

    private static int resolveMissingIdentifierId(IdentifierStore store, String value, boolean create) {
        final int id;
        final boolean clickHouse;
        synchronized (ConfigHandler.class) {
            Integer cached = store.values.get().get(value);
            if (cached != null) {
                return cached;
            }

            clickHouse = databaseType.isClickHouse();
            if (clickHouse) {
                int foundId;
                try {
                    foundId = Database.findClickHouseIdentifierId(store.kind, value);
                }
                catch (Exception exception) {
                    throw new IllegalStateException("Unable to resolve ClickHouse " + store.kind.name().toLowerCase(Locale.ROOT) + " identifier for " + value, exception);
                }
                if (foundId > 0) {
                    store.store(foundId, value);
                    return foundId;
                }
            }
            else if (create && !Config.getGlobal().DATABASE_LOCK && reloadIdentifierCache(store.kind)) {
                cached = store.values.get().get(value);
                if (cached != null) {
                    return cached;
                }
            }
            if (!create) {
                return -1;
            }

            id = allocateIdentifierId(store, value, store.maximum.getAsInt());
            if (id == -1 || !store.canStore(id, value)) {
                if (clickHouse) {
                    throw new IllegalStateException("Unable to resolve ClickHouse " + store.kind.name().toLowerCase(Locale.ROOT) + " identifier for " + value);
                }
                return -1;
            }
            if (clickHouse) {
                if (!queueIdentifier(store.kind, id, value)) {
                    throw new IllegalStateException("Database persistence halted before the ClickHouse identifier could be queued");
                }
            }
            store.store(id, value);
        }
        if (!clickHouse) {
            queueIdentifier(store.kind, id, value);
        }
        return id;
    }

    private static boolean queueIdentifier(ReferenceKind kind, int id, String value) {
        switch (kind) {
            case ART:
                return tryQueueIdentifierInsert(Process.ART_INSERT, id, value);
            case BLOCK_DATA:
                return tryQueueIdentifierInsert(Process.BLOCKDATA_INSERT, id, value);
            case ENTITY:
                return tryQueueIdentifierInsert(Process.ENTITY_INSERT, id, value);
            case MATERIAL:
                return tryQueueIdentifierInsert(Process.MATERIAL_INSERT, id, value);
            case WORLD:
                return tryQueueIdentifierInsert(Process.WORLD_INSERT, id, value);
            default:
                throw new IllegalArgumentException("Unsupported identifier cache: " + kind);
        }
    }

    public static synchronized void uncacheIdentifier(ReferenceKind kind, int id, String value) {
        IdentifierStore store = identifierStore(kind);
        store.values.get().remove(value, Integer.valueOf(id));
        store.reversed.get().remove(Integer.valueOf(id), value);
    }

    public static String getIdentifierValue(CacheType type, int id) {
        IdentifierStore store = identifierStore(type.kind);
        String value = store.reversed.get().get(id);
        if (value != null || id < 1 || !databaseType.isClickHouse()) {
            return value;
        }
        return loadMissingIdentifierValue(store, id);
    }

    private static synchronized String loadMissingIdentifierValue(IdentifierStore store, int id) {
        String value = store.reversed.get().get(id);
        if (value != null) {
            return value;
        }

        try {
            value = Database.findClickHouseIdentifierValue(store.kind, id);
        }
        catch (Exception exception) {
            throw new IllegalStateException("Unable to resolve ClickHouse " + store.kind.name().toLowerCase(Locale.ROOT) + " identifier " + id, exception);
        }
        if (value != null) {
            store.store(id, value);
        }
        return value;
    }

    private static int allocateIdentifierId(IdentifierStore store, String value, int currentMaximum) {
        if (!ConfigHandler.databaseType.isClickHouse()) {
            return currentMaximum + 1;
        }

        try {
            return Database.nextClickHouseIdentifierId(store.kind, value, currentMaximum);
        }
        catch (Exception exception) {
            throw new IllegalStateException("Unable to allocate ClickHouse " + store.kind.name().toLowerCase(Locale.ROOT) + " identifier for " + value, exception);
        }
    }

    private static IdentifierStore identifierStore(ReferenceKind kind) {
        IdentifierStore store = IDENTIFIER_STORES.get(kind);
        if (store == null) {
            throw new IllegalArgumentException("Unsupported identifier cache: " + kind);
        }
        return store;
    }

    private static Map<ReferenceKind, IdentifierStore> identifierStores() {
        Map<ReferenceKind, IdentifierStore> stores = new EnumMap<>(ReferenceKind.class);
        stores.put(ReferenceKind.MATERIAL, new IdentifierStore(ReferenceKind.MATERIAL, () -> materials, () -> materialsReversed, () -> materialId, id -> materialId = id));
        stores.put(ReferenceKind.BLOCK_DATA, new IdentifierStore(ReferenceKind.BLOCK_DATA, () -> blockdata, () -> blockdataReversed, () -> blockdataId, id -> blockdataId = id));
        stores.put(ReferenceKind.ART, new IdentifierStore(ReferenceKind.ART, () -> art, () -> artReversed, () -> artId, id -> artId = id));
        stores.put(ReferenceKind.ENTITY, new IdentifierStore(ReferenceKind.ENTITY, () -> entities, () -> entitiesReversed, () -> entityId, id -> entityId = id));
        stores.put(ReferenceKind.WORLD, new IdentifierStore(ReferenceKind.WORLD, () -> worlds, () -> worldsReversed, () -> worldId, id -> worldId = id));
        return stores;
    }

    private static boolean reloadIdentifierCache(ReferenceKind kind) {
        try (Connection connection = Database.getConnection(true)) {
            if (connection == null) {
                return false;
            }
            try (Statement statement = connection.createStatement()) {
                switch (kind) {
                    case MATERIAL:
                        return loadMaterials(statement);
                    case BLOCK_DATA:
                        return loadBlockdata(statement);
                    case ART:
                        return loadArt(statement);
                    case ENTITY:
                        return loadEntities(statement);
                    case WORLD:
                        return loadWorldsFromDatabase(statement);
                    default:
                        return false;
                }
            }
        }
        catch (Exception e) {
            ErrorReporter.report(e);
            return false;
        }
    }

    private static boolean loadWorldsFromDatabase(Statement statement) {
        try {
            IdentifierCache cache = loadIdentifierCache(statement, "world", "world");
            ConfigHandler.worlds = syncMap(cache.values);
            ConfigHandler.worldsReversed = syncMap(cache.reversed);
            worldId = cache.maximumId;
            return true;
        }
        catch (Exception e) {
            ErrorReporter.report(e);
            return false;
        }
    }

    public static boolean loadWorlds(Statement statement) {
        if (Bukkit.isPrimaryThread()) {
            return loadWorlds(statement, captureWorldNames());
        }
        return loadWorldsFromDatabase(statement);
    }

    private static boolean loadWorlds(Statement statement, List<String> worldNames) {
        try {
            IdentifierCache cache = loadIdentifierCache(statement, "world", "world");
            Map<Integer, String> queuedWorlds = new HashMap<>();
            for (String worldname : worldNames) {
                if (!cache.values.containsKey(worldname)) {
                    int id = allocateIdentifierId(identifierStore(ReferenceKind.WORLD), worldname, cache.maximumId);
                    if (id == -1) {
                        return false;
                    }
                    cache.maximumId = Math.max(cache.maximumId, id);
                    cache.values.put(worldname, id);
                    cache.reversed.put(id, worldname);
                    queuedWorlds.put(id, worldname);
                }
            }
            boolean clickHouse = databaseType.isClickHouse();
            if (clickHouse) {
                if (!queueIdentifiers(ReferenceKind.WORLD, queuedWorlds)) {
                    throw new IllegalStateException("Database persistence halted before the ClickHouse world identifiers could be queued");
                }
            }
            ConfigHandler.worlds = syncMap(cache.values);
            ConfigHandler.worldsReversed = syncMap(cache.reversed);
            worldId = cache.maximumId;
            if (!clickHouse) {
                queueIdentifiers(ReferenceKind.WORLD, queuedWorlds);
            }
            return true;
        }
        catch (Exception e) {
            if (databaseType.isClickHouse() && e instanceof IllegalStateException) {
                throw (IllegalStateException) e;
            }
            ErrorReporter.report(e);
            return false;
        }
    }

    private static boolean queueIdentifiers(ReferenceKind kind, Map<Integer, String> identifiers) {
        for (Map.Entry<Integer, String> identifier : identifiers.entrySet()) {
            if (!queueIdentifier(kind, identifier.getKey(), identifier.getValue())) {
                return false;
            }
        }
        return true;
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
                    String query = "SELECT * FROM " + ConfigHandler.prefix + "database_lock WHERE rowid='1' AND (status='" + Database.DATABASE_LOCK_MIGRATION_INCOMPLETE + "' OR (status='" + Database.DATABASE_LOCK_ACTIVE + "' AND time >= '" + checkTime + "')) LIMIT 1";
                    try (ResultSet rs = statement.executeQuery(query)) {
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
                    }
                }
            }
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
        catch (Exception e) {
            ErrorReporter.report(e);
            return false;
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
        catch (CancellationException e) {
            throw e;
        }
        catch (Exception e) {
            ErrorReporter.report(e);
            return false;
        }

        try (Connection connection = Database.getConnection(true, 0)) {
            Statement statement = connection.createStatement();
            ServerStateSnapshot snapshot = supplyOnPrimaryThread(ConfigHandler::captureServerStateSnapshot);

            ConfigHandler.checkPlayers(connection, snapshot.onlinePlayers);
            boolean worldsLoaded = ConfigHandler.loadWorlds(statement, snapshot.worldNames); // Load world ID's into memory.
            boolean typesLoaded = ConfigHandler.loadTypes(statement); // Load material ID's into memory.
            if (ConfigHandler.databaseType.isColumnar() && (!worldsLoaded || !typesLoaded)) {
                throw new IllegalStateException("Unable to initialize columnar database identifier caches");
            }

            // Initialize WorldEdit logging
            runOnPrimaryThread(() -> {
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
            });

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
        }
        catch (CancellationException e) {
            throw e;
        }
        catch (Exception e) {
            ErrorReporter.report(e);
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
