package net.coreprotect.config;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;

import org.bukkit.Bukkit;
import org.bukkit.World;

import net.coreprotect.CoreProtect;
import net.coreprotect.language.Language;
import net.coreprotect.thread.Scheduler;

public class Config extends Language {

    private static final Map<String, String[]> HEADERS = new HashMap<>();
    private static final Map<String, String> DEFAULT_VALUES = new LinkedHashMap<>();
    private static final Map<String, Config> CONFIG_BY_WORLD_NAME = new HashMap<>();
    private static final String DEFAULT_FILE_HEADER = "# CoreProtect Config";
    public static final String LINE_SEPARATOR = "\n";

    private static final Config GLOBAL = new Config();
    private final HashMap<String, String> config;
    private Config defaults;

    public String DONATION_KEY;
    public String PREFIX;
    public String MYSQL_HOST;
    public String MYSQL_DATABASE;
    public String MYSQL_USERNAME;
    public String MYSQL_PASSWORD;
    public String LANGUAGE;
    public boolean ENABLE_SSL;
    public boolean DISABLE_WAL;
    public boolean HOVER_EVENTS;
    public boolean DATABASE_LOCK;
    public boolean LOG_CANCELLED_CHAT;
    public boolean HOPPER_FILTER_META;
    public boolean EXCLUDE_TNT;
    public boolean NETWORK_DEBUG;
    public boolean MYSQL;
    public boolean CHECK_UPDATES;
    public boolean API_ENABLED;
    public boolean VERBOSE;
    public boolean ROLLBACK_ITEMS;
    public boolean ROLLBACK_ENTITIES;
    public boolean SKIP_GENERIC_DATA;
    public boolean BLOCK_PLACE;
    public boolean BLOCK_BREAK;
    public boolean NATURAL_BREAK;
    public boolean BLOCK_MOVEMENT;
    public boolean PISTONS;
    public boolean BLOCK_BURN;
    public boolean BLOCK_IGNITE;
    public boolean EXPLOSIONS;
    public boolean ENTITY_CHANGE;
    public boolean ENTITY_KILLS;
    public boolean SIGN_TEXT;
    public boolean BUCKETS;
    public boolean LEAF_DECAY;
    public boolean TREE_GROWTH;
    public boolean MUSHROOM_GROWTH;
    public boolean VINE_GROWTH;
    public boolean SCULK_SPREAD;
    public boolean PORTALS;
    public boolean WATER_FLOW;
    public boolean LAVA_FLOW;
    public boolean LIQUID_TRACKING;
    public boolean ITEM_TRANSACTIONS;
    public boolean ITEM_DROPS;
    public boolean ITEM_PICKUPS;
    public boolean HOPPER_TRANSACTIONS;
    public boolean PLAYER_INTERACTIONS;
    public boolean PLAYER_MESSAGES;
    public boolean PLAYER_COMMANDS;
    public boolean PLAYER_SESSIONS;
    public boolean UNKNOWN_LOGGING;
    public boolean USERNAME_CHANGES;
    public boolean WORLDEDIT;
    public int MAXIMUM_POOL_SIZE;
    public int MYSQL_PORT;
    public int DEFAULT_RADIUS;
    public int MAX_RADIUS;

    static {
        DEFAULT_VALUES.put("donation-key", "");
        DEFAULT_VALUES.put("use-mysql", "false");
        DEFAULT_VALUES.put("table-prefix", "co_");
        DEFAULT_VALUES.put("mysql-host", "127.0.0.1");
        DEFAULT_VALUES.put("mysql-port", "3306");
        DEFAULT_VALUES.put("mysql-database", "database");
        DEFAULT_VALUES.put("mysql-username", "root");
        DEFAULT_VALUES.put("mysql-password", "");
        DEFAULT_VALUES.put("language", "en");
        DEFAULT_VALUES.put("check-updates", "true");
        DEFAULT_VALUES.put("api-enabled", "true");
        DEFAULT_VALUES.put("verbose", "true");
        DEFAULT_VALUES.put("default-radius", "10");
        DEFAULT_VALUES.put("max-radius", "100");
        DEFAULT_VALUES.put("rollback-items", "true");
        DEFAULT_VALUES.put("rollback-entities", "true");
        DEFAULT_VALUES.put("skip-generic-data", "true");
        DEFAULT_VALUES.put("block-place", "true");
        DEFAULT_VALUES.put("block-break", "true");
        DEFAULT_VALUES.put("natural-break", "true");
        DEFAULT_VALUES.put("block-movement", "true");
        DEFAULT_VALUES.put("pistons", "true");
        DEFAULT_VALUES.put("block-burn", "true");
        DEFAULT_VALUES.put("block-ignite", "true");
        DEFAULT_VALUES.put("explosions", "true");
        DEFAULT_VALUES.put("entity-change", "true");
        DEFAULT_VALUES.put("entity-kills", "true");
        DEFAULT_VALUES.put("sign-text", "true");
        DEFAULT_VALUES.put("buckets", "true");
        DEFAULT_VALUES.put("leaf-decay", "true");
        DEFAULT_VALUES.put("tree-growth", "true");
        DEFAULT_VALUES.put("mushroom-growth", "true");
        DEFAULT_VALUES.put("vine-growth", "true");
        DEFAULT_VALUES.put("sculk-spread", "true");
        DEFAULT_VALUES.put("portals", "true");
        DEFAULT_VALUES.put("water-flow", "true");
        DEFAULT_VALUES.put("lava-flow", "true");
        DEFAULT_VALUES.put("liquid-tracking", "true");
        DEFAULT_VALUES.put("item-transactions", "true");
        DEFAULT_VALUES.put("item-drops", "true");
        DEFAULT_VALUES.put("item-pickups", "true");
        DEFAULT_VALUES.put("hopper-transactions", "true");
        DEFAULT_VALUES.put("player-interactions", "true");
        DEFAULT_VALUES.put("player-messages", "true");
        DEFAULT_VALUES.put("player-commands", "true");
        DEFAULT_VALUES.put("player-sessions", "true");
        DEFAULT_VALUES.put("username-changes", "true");
        DEFAULT_VALUES.put("worldedit", "true");

        HEADERS.put("donation-key", new String[] { "# CoreProtect is donationware. Obtain a donation key from coreprotect.net/donate/" });
        HEADERS.put("use-mysql", new String[] { "# MySQL is optional and not required.", "# If you prefer to use MySQL, enable the following and fill out the fields." });
        HEADERS.put("language", new String[] { "# If modified, will automatically attempt to translate languages phrases.", "# List of language codes: https://coreprotect.net/languages/" });
        HEADERS.put("check-updates", new String[] { "# If enabled, CoreProtect will check for updates when your server starts up.", "# If an update is available, you'll be notified via your server console.", });
        HEADERS.put("api-enabled", new String[] { "# If enabled, other plugins will be able to utilize the CoreProtect API.", });
        HEADERS.put("verbose", new String[] { "# If enabled, extra data is displayed during rollbacks and restores.", "# Can be manually triggered by adding \"#verbose\" to your rollback command." });
        HEADERS.put("default-radius", new String[] { "# If no radius is specified in a rollback or restore, this value will be", "# used as the radius. Set to \"0\" to disable automatically adding a radius." });
        HEADERS.put("max-radius", new String[] { "# The maximum radius that can be used in a command. Set to \"0\" to disable.", "# To run a rollback or restore without a radius, you can use \"r:#global\"." });
        HEADERS.put("rollback-items", new String[] { "# If enabled, items taken from containers (etc) will be included in rollbacks." });
        HEADERS.put("rollback-entities", new String[] { "# If enabled, entities, such as killed animals, will be included in rollbacks." });
        HEADERS.put("skip-generic-data", new String[] { "# If enabled, generic data, like zombies burning in daylight, won't be logged." });
        HEADERS.put("block-place", new String[] { "# Logs blocks placed by players." });
        HEADERS.put("block-break", new String[] { "# Logs blocks broken by players." });
        HEADERS.put("natural-break", new String[] { "# Logs blocks that break off of other blocks; for example, a sign or torch", "# falling off of a dirt block that a player breaks. This is required for", "# beds/doors to properly rollback." });
        HEADERS.put("block-movement", new String[] { "# Properly track block movement, such as sand or gravel falling." });
        HEADERS.put("pistons", new String[] { "# Properly track blocks moved by pistons." });
        HEADERS.put("block-burn", new String[] { "# Logs blocks that burn up in a fire." });
        HEADERS.put("block-ignite", new String[] { "# Logs when a block naturally ignites, such as from fire spreading." });
        HEADERS.put("explosions", new String[] { "# Logs explosions, such as TNT and Creepers." });
        HEADERS.put("entity-change", new String[] { "# Track when an entity changes a block, such as an Enderman destroying blocks." });
        HEADERS.put("entity-kills", new String[] { "# Logs killed entities, such as killed cows and enderman." });
        HEADERS.put("sign-text", new String[] { "# Logs text on signs. If disabled, signs will be blank when rolled back." });
        HEADERS.put("buckets", new String[] { "# Logs lava and water sources placed/removed by players who are using buckets." });
        HEADERS.put("leaf-decay", new String[] { "# Logs natural tree leaf decay." });
        HEADERS.put("tree-growth", new String[] { "# Logs tree growth. Trees are linked to the player who planted the sapling." });
        HEADERS.put("mushroom-growth", new String[] { "# Logs mushroom growth." });
        HEADERS.put("vine-growth", new String[] { "# Logs natural vine growth." });
        HEADERS.put("sculk-spread", new String[] { "# Logs the spread of sculk blocks from sculk catalysts." });
        HEADERS.put("portals", new String[] { "# Logs when portals such as Nether portals generate naturally." });
        HEADERS.put("water-flow", new String[] { "# Logs water flow. If water destroys other blocks, such as torches,", "# this allows it to be properly rolled back." });
        HEADERS.put("lava-flow", new String[] { "# Logs lava flow. If lava destroys other blocks, such as torches,", "# this allows it to be properly rolled back." });
        HEADERS.put("liquid-tracking", new String[] { "# Allows liquid to be properly tracked and linked to players.", "# For example, if a player places water which flows and destroys torches,", "# it can all be properly restored by rolling back that single player." });
        HEADERS.put("item-transactions", new String[] { "# Track item transactions, such as when a player takes items from", "# a chest, furnace, or dispenser." });
        HEADERS.put("item-drops", new String[] { "# Logs items dropped by players." });
        HEADERS.put("item-pickups", new String[] { "# Logs items picked up by players." });
        HEADERS.put("hopper-transactions", new String[] { "# Track all hopper transactions, such as when a hopper removes items from a", "# chest, furnace, or dispenser." });
        HEADERS.put("player-interactions", new String[] { "# Track player interactions, such as when a player opens a door, presses", "# a button, or opens a chest. Player interactions can't be rolled back." });
        HEADERS.put("player-messages", new String[] { "# Logs messages that players send in the chat." });
        HEADERS.put("player-commands", new String[] { "# Logs all commands used by players." });
        HEADERS.put("player-sessions", new String[] { "# Logs the logins and logouts of players." });
        HEADERS.put("username-changes", new String[] { "# Logs when a player changes their Minecraft username." });
        HEADERS.put("worldedit", new String[] { "# Logs changes made via the plugin \"WorldEdit\" if it's in use on your server." });
    }

    private void readValues() {
        this.ENABLE_SSL = this.getBoolean("enable-ssl", false);
        this.DISABLE_WAL = this.getBoolean("disable-wal", false);
        this.HOVER_EVENTS = this.getBoolean("hover-events", true);
        this.DATABASE_LOCK = this.getBoolean("database-lock", true);
        this.LOG_CANCELLED_CHAT = this.getBoolean("log-cancelled-chat", true);
        this.HOPPER_FILTER_META = this.getBoolean("hopper-filter-meta", false);
        this.EXCLUDE_TNT = this.getBoolean("exclude-tnt", false);
        this.NETWORK_DEBUG = this.getBoolean("network-debug", false);
        this.UNKNOWN_LOGGING = this.getBoolean("unknown-logging", false);
        this.MAXIMUM_POOL_SIZE = this.getInt("maximum-pool-size", 10);
        this.DONATION_KEY = this.getString("donation-key");
        this.MYSQL = this.getBoolean("use-mysql");
        this.PREFIX = this.getString("table-prefix");
        this.MYSQL_HOST = this.getString("mysql-host");
        this.MYSQL_PORT = this.getInt("mysql-port");
        this.MYSQL_DATABASE = this.getString("mysql-database");
        this.MYSQL_USERNAME = this.getString("mysql-username");
        this.MYSQL_PASSWORD = this.getString("mysql-password");
        this.LANGUAGE = this.getString("language");
        this.CHECK_UPDATES = this.getBoolean("check-updates");
        this.API_ENABLED = this.getBoolean("api-enabled");
        this.VERBOSE = this.getBoolean("verbose");
        this.DEFAULT_RADIUS = this.getInt("default-radius");
        this.MAX_RADIUS = this.getInt("max-radius");
        this.ROLLBACK_ITEMS = this.getBoolean("rollback-items");
        this.ROLLBACK_ENTITIES = this.getBoolean("rollback-entities");
        this.SKIP_GENERIC_DATA = this.getBoolean("skip-generic-data");
        this.BLOCK_PLACE = this.getBoolean("block-place");
        this.BLOCK_BREAK = this.getBoolean("block-break");
        this.NATURAL_BREAK = this.getBoolean("natural-break");
        this.BLOCK_MOVEMENT = this.getBoolean("block-movement");
        this.PISTONS = this.getBoolean("pistons");
        this.BLOCK_BURN = this.getBoolean("block-burn");
        this.BLOCK_IGNITE = this.getBoolean("block-ignite");
        this.EXPLOSIONS = this.getBoolean("explosions");
        this.ENTITY_CHANGE = this.getBoolean("entity-change");
        this.ENTITY_KILLS = this.getBoolean("entity-kills");
        this.SIGN_TEXT = this.getBoolean("sign-text");
        this.BUCKETS = this.getBoolean("buckets");
        this.LEAF_DECAY = this.getBoolean("leaf-decay");
        this.TREE_GROWTH = this.getBoolean("tree-growth");
        this.MUSHROOM_GROWTH = this.getBoolean("mushroom-growth");
        this.VINE_GROWTH = this.getBoolean("vine-growth");
        this.SCULK_SPREAD = this.getBoolean("sculk-spread");
        this.PORTALS = this.getBoolean("portals");
        this.WATER_FLOW = this.getBoolean("water-flow");
        this.LAVA_FLOW = this.getBoolean("lava-flow");
        this.LIQUID_TRACKING = this.getBoolean("liquid-tracking");
        this.ITEM_TRANSACTIONS = this.getBoolean("item-transactions");
        this.ITEM_DROPS = this.getBoolean("item-drops");
        this.ITEM_PICKUPS = this.getBoolean("item-pickups");
        this.HOPPER_TRANSACTIONS = this.getBoolean("hopper-transactions");
        this.PLAYER_INTERACTIONS = this.getBoolean("player-interactions");
        this.PLAYER_MESSAGES = this.getBoolean("player-messages");
        this.PLAYER_COMMANDS = this.getBoolean("player-commands");
        this.PLAYER_SESSIONS = this.getBoolean("player-sessions");
        this.USERNAME_CHANGES = this.getBoolean("username-changes");
        this.WORLDEDIT = this.getBoolean("worldedit");
    }

    public static void init() throws IOException {
        parseConfig(loadFiles(ConfigFile.CONFIG));
        // pass variables to ConfigFile.parseConfig(ConfigFile.loadFiles());
    }

    public static Config getGlobal() {
        return GLOBAL;
    }

    // returns a world specific config if it exists, otherwise the global config
    public static Config getConfig(final World world) {
        return getConfig(world.getName());
    }

    public static Config getConfig(final String worldName) {
        Config ret = CONFIG_BY_WORLD_NAME.get(worldName);
        if (ret == null) {
            ret = CONFIG_BY_WORLD_NAME.getOrDefault(worldName, GLOBAL);
            CONFIG_BY_WORLD_NAME.put(worldName, ret);
        }
        return ret;
    }

    public Config() {
        this.config = new LinkedHashMap<>();
    }

    public void setDefaults(final Config defaults) {
        this.defaults = defaults;
    }

    private String get(final String key, final String dfl) {
        String configured = this.config.get(key);
        if (configured == null) {
            if (dfl != null) {
                return dfl;
            }
            if (this.defaults == null) {
                configured = DEFAULT_VALUES.get(key);
            }
            else {
                configured = this.defaults.config.getOrDefault(key, DEFAULT_VALUES.get(key));
            }
        }
        return configured;
    }

    private boolean getBoolean(final String key) {
        final String configured = this.get(key, null);
        return configured != null && configured.startsWith("t");
    }

    private boolean getBoolean(final String key, final boolean dfl) {
        final String configured = this.get(key, null);
        return configured == null ? dfl : configured.startsWith("t");
    }

    private int getInt(final String key) {
        return this.getInt(key, 0);
    }

    private int getInt(final String key, final int dfl) {
        String configured = this.get(key, null);

        if (configured == null) {
            return dfl;
        }

        configured = configured.replaceAll("[^0-9]", "");

        return configured.isEmpty() ? dfl : Integer.parseInt(configured);
    }

    private String getString(final String key) {
        final String configured = this.get(key, null);
        return configured == null ? "" : configured;
    }

    public void clearConfig() {
        this.config.clear();
    }

    public void loadDefaults() {
        this.clearConfig();
        this.readValues();
    }

    public void load(final InputStream in) throws IOException {
        // if we fail reading, we will not corrupt our current config.
        final Map<String, String> newConfig = new LinkedHashMap<>(this.config.size());
        ConfigFile.load(in, newConfig, false);

        this.clearConfig();
        this.config.putAll(newConfig);

        this.readValues();
    }

    private static Map<String, byte[]> loadFiles(String fileName) throws IOException {
        final CoreProtect plugin = CoreProtect.getInstance();
        final File configFolder = plugin.getDataFolder();
        if (!configFolder.exists()) {
            configFolder.mkdirs();
        }

        final Map<String, byte[]> map = new HashMap<>();
        final File globalFile = new File(configFolder, fileName);

        if (globalFile.exists()) {
            // we always add options to the global config
            final byte[] data = Files.readAllBytes(globalFile.toPath());
            map.put("config", data);

            // can't modify GLOBAL, we're likely off-main here
            final Config temp = new Config();
            temp.load(new ByteArrayInputStream(data));
            temp.addMissingOptions(globalFile);
        }
        else {
            final Config temp = new Config();
            temp.loadDefaults();
            temp.addMissingOptions(globalFile);
        }

        for (final File worldConfigFile : configFolder.listFiles((File file) -> file.getName().endsWith(".yml"))) {
            final String name = worldConfigFile.getName();
            if (name.equals(ConfigFile.CONFIG) || name.equals(ConfigFile.LANGUAGE)) {
                continue;
            }

            map.put(name.substring(0, name.length() - ".yml".length()), Files.readAllBytes(worldConfigFile.toPath()));
        }

        return map;
    }

    // this should only be called on the main thread
    private static void parseConfig(final Map<String, byte[]> data) {
        if (!Bukkit.isPrimaryThread()) {
            // we call reloads asynchronously
            // for now this solution is good enough to ensure we only modify on the main thread
            final CompletableFuture<Void> complete = new CompletableFuture<>();

            Scheduler.runTask(CoreProtect.getInstance(), () -> {
                try {
                    parseConfig(data);
                }
                catch (final Throwable thr) {
                    if (thr instanceof ThreadDeath) {
                        throw (ThreadDeath) thr;
                    }
                    complete.completeExceptionally(thr);
                    return;
                }
                complete.complete(null);
            });

            complete.join();
            return;
        }

        CONFIG_BY_WORLD_NAME.clear();

        // we need to load global first since it is used for config defaults
        final byte[] defaultData = data.get("config");
        if (defaultData != null) {
            try {
                GLOBAL.load(new ByteArrayInputStream(defaultData));
            }
            catch (final IOException ex) {
                throw new RuntimeException(ex); // shouldn't happen
            }
        }
        else {
            GLOBAL.loadDefaults();
        }

        for (final Map.Entry<String, byte[]> entry : data.entrySet()) {
            final String worldName = entry.getKey();
            if (worldName.equals("config")) {
                continue;
            }

            final byte[] fileData = entry.getValue();
            final Config config = new Config();
            config.setDefaults(GLOBAL);

            try {
                config.load(new ByteArrayInputStream(fileData));
            }
            catch (final IOException ex) {
                throw new RuntimeException(ex); // shouldn't happen
            }

            CONFIG_BY_WORLD_NAME.put(worldName, config);
        }
    }

    public void addMissingOptions(final File file) throws IOException {
        final boolean writeHeader = !file.exists() || file.length() == 0;
        try (final FileOutputStream fout = new FileOutputStream(file, true)) {
            OutputStreamWriter out = new OutputStreamWriter(new BufferedOutputStream(fout), StandardCharsets.UTF_8);
            if (writeHeader) {
                out.append(DEFAULT_FILE_HEADER);
                out.append(LINE_SEPARATOR);
            }

            for (final Map.Entry<String, String> entry : DEFAULT_VALUES.entrySet()) {
                final String key = entry.getKey();
                final String defaultValue = entry.getValue();

                final String configuredValue = this.config.get(key);

                if (configuredValue != null) {
                    continue;
                }

                final String[] header = HEADERS.get(key);

                if (header != null) {
                    out.append(LINE_SEPARATOR);
                    for (final String headerLine : header) {
                        out.append(headerLine);
                        out.append(LINE_SEPARATOR);
                    }
                }

                out.append(key);
                out.append(": ");
                out.append(defaultValue);
                out.append(LINE_SEPARATOR);
            }

            out.close();
        }
    }
}
