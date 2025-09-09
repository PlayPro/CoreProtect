package net.coreprotect.services;

import java.io.File;

import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.java.JavaPlugin;

import net.coreprotect.CoreProtect;
import net.coreprotect.command.CommandHandler;
import net.coreprotect.command.TabHandler;
import net.coreprotect.config.Config;
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.consumer.Consumer;
import net.coreprotect.language.Language;
import net.coreprotect.language.Phrase;
import net.coreprotect.listener.ListenerHandler;
import net.coreprotect.thread.CacheHandler;
import net.coreprotect.thread.NetworkHandler;
import net.coreprotect.thread.Scheduler;
import net.coreprotect.utility.Chat;
import net.coreprotect.utility.ChatUtils;

/**
 * Service responsible for plugin initialization tasks
 */
public class PluginInitializationService {

    private PluginInitializationService() {
        throw new IllegalStateException("Utility class");
    }

    /**
     * Initializes plugin components and configurations
     *
     * @param plugin
     *            The CoreProtect plugin instance
     * @return true if initialization was successful, false otherwise
     */
    public static boolean initializePlugin(CoreProtect plugin) {
        // Load language phrases
        Language.loadPhrases();

        // Perform version checks
        boolean start = VersionCheckService.performVersionChecks();
        if (!start) {
            return false;
        }

        try {
            // Initialize core components
            Consumer.initialize();
            new ListenerHandler(plugin);

            // Register commands
            registerCommands(plugin);

            // Ensure data directory exists
            createDataDirectory();

            // Initialize configuration
            start = ConfigHandler.performInitialization(true);
        }
        catch (Exception e) {
            e.printStackTrace();
            return false;
        }

        if (start) {
            // Display startup messages
            displayStartupMessages(plugin);

            // Start background services
            startBackgroundServices(plugin);

            // Start metrics
            enableMetrics(plugin);
        }

        return start;
    }

    /**
     * Registers plugin commands
     *
     * @param plugin
     *            The CoreProtect plugin instance
     */
    private static void registerCommands(JavaPlugin plugin) {
        plugin.getCommand("coreprotect").setExecutor(CommandHandler.getInstance());
        plugin.getCommand("coreprotect").setTabCompleter(new TabHandler());
        plugin.getCommand("core").setExecutor(CommandHandler.getInstance());
        plugin.getCommand("core").setTabCompleter(new TabHandler());
        plugin.getCommand("co").setExecutor(CommandHandler.getInstance());
        plugin.getCommand("co").setTabCompleter(new TabHandler());
    }

    /**
     * Creates the plugin data directory if it doesn't exist
     */
    private static void createDataDirectory() {
        boolean exists = (new File(ConfigHandler.path)).exists();
        if (!exists) {
            new File(ConfigHandler.path).mkdir();
        }
    }

    /**
     * Displays startup messages in the console
     *
     * @param plugin
     *            The CoreProtect plugin instance
     */
    private static void displayStartupMessages(JavaPlugin plugin) {
        PluginDescriptionFile pluginDescription = plugin.getDescription();
        ChatUtils.sendConsoleComponentStartup(Bukkit.getServer().getConsoleSender(), Phrase.build(Phrase.ENABLE_SUCCESS, ConfigHandler.EDITION_NAME));

        if (Config.getGlobal().MYSQL) {
            Chat.console(Phrase.build(Phrase.USING_MYSQL));
        }
        else {
            Chat.console(Phrase.build(Phrase.USING_SQLITE));
        }

        Chat.console("--------------------");
        Chat.console(Phrase.build(Phrase.ENJOY_COREPROTECT, pluginDescription.getName()));
        Chat.console(Phrase.build(Phrase.LINK_DISCORD, "www.coreprotect.net/discord/"));
        Chat.console("--------------------");
    }

    /**
     * Starts background services
     *
     * @param plugin
     *            The CoreProtect plugin instance
     */
    private static void startBackgroundServices(CoreProtect plugin) {
        // Start network handler
        Scheduler.scheduleSyncDelayedTask(plugin, () -> {
            try {
                Thread networkHandler = new Thread(new NetworkHandler(true, true));
                networkHandler.start();
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }, 0);

        // Start cache cleanup thread
        Thread cacheCleanUpThread = new Thread(new CacheHandler());
        cacheCleanUpThread.start();

        // Start consumer
        Consumer.startConsumer();
    }

    /**
     * Enables metrics reporting
     *
     * @param plugin
     *            The CoreProtect plugin instance
     */
    private static void enableMetrics(JavaPlugin plugin) {
        try {
            new Metrics(plugin, 2876);
        }
        catch (Exception e) {
            // Failed to connect to bStats server or something else went wrong
        }
    }
}
