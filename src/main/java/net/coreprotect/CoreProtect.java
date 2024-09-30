package net.coreprotect;

import java.io.File;
import java.util.Iterator;
import java.util.Map.Entry;

import org.bstats.bukkit.MetricsLite;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.java.JavaPlugin;

import net.coreprotect.command.CommandHandler;
import net.coreprotect.command.TabHandler;
import net.coreprotect.config.Config;
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.consumer.Consumer;
import net.coreprotect.consumer.process.Process;
import net.coreprotect.language.Language;
import net.coreprotect.language.Phrase;
import net.coreprotect.listener.ListenerHandler;
import net.coreprotect.listener.player.PlayerQuitListener;
import net.coreprotect.paper.PaperAdapter;
import net.coreprotect.thread.CacheHandler;
import net.coreprotect.thread.NetworkHandler;
import net.coreprotect.thread.Scheduler;
import net.coreprotect.utility.Chat;
import net.coreprotect.utility.Color;
import net.coreprotect.utility.Teleport;
import net.coreprotect.utility.Util;

public final class CoreProtect extends JavaPlugin {

    private static CoreProtect instance;

    /**
     * Get the instance of CoreProtect
     *
     * @return This CoreProtect instance
     */
    public static CoreProtect getInstance() {
        return instance;
    }

    private CoreProtectAPI api = new CoreProtectAPI();

    /**
     * Get the CoreProtect API
     *
     * @return The CoreProtect API
     */
    public CoreProtectAPI getAPI() {
        return api;
    }

    @Override
    public void onEnable() {
        instance = this;
        ConfigHandler.path = this.getDataFolder().getPath() + File.separator;
        Language.loadPhrases();

        boolean start = performVersionChecks();
        if (start) {
            try {
                Consumer.initialize(); // Prepare consumer (keep this here)
                new ListenerHandler(this);
                getCommand("coreprotect").setExecutor(CommandHandler.getInstance());
                getCommand("coreprotect").setTabCompleter(new TabHandler());
                getCommand("core").setExecutor(CommandHandler.getInstance());
                getCommand("core").setTabCompleter(new TabHandler());
                getCommand("co").setExecutor(CommandHandler.getInstance());
                getCommand("co").setTabCompleter(new TabHandler());

                boolean exists = (new File(ConfigHandler.path)).exists();
                if (!exists) {
                    new File(ConfigHandler.path).mkdir();
                }
                start = ConfigHandler.performInitialization(true); // Perform any necessary initialization
            }
            catch (Exception e) {
                e.printStackTrace();
                start = false;
            }
        }

        if (start) {
            PluginDescriptionFile pluginDescription = this.getDescription();
            Util.sendConsoleComponentStartup(Bukkit.getServer().getConsoleSender(), Phrase.build(Phrase.ENABLE_SUCCESS, ConfigHandler.EDITION_NAME));
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

            Scheduler.scheduleSyncDelayedTask(this, () -> {
                try {
                    Thread networkHandler = new Thread(new NetworkHandler(true, true));
                    networkHandler.start();
                }
                catch (Exception e) {
                    e.printStackTrace();
                }
            }, 0);

            Thread cacheCleanUpThread = new Thread(new CacheHandler());
            cacheCleanUpThread.start();

            Consumer.startConsumer();

            // Enabling bStats
            try {
                new MetricsLite(this, 2876);
            }
            catch (Exception e) {
                // Failed to connect to bStats server or something else went wrong.
            }
        }
        else {
            Chat.console(Phrase.build(Phrase.ENABLE_FAILED, ConfigHandler.EDITION_NAME));
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        safeShutdown(this);
    }

    private static boolean performVersionChecks() {
        try {
            String[] bukkitVersion = Bukkit.getServer().getBukkitVersion().split("[-.]");
            if (Util.newVersion(bukkitVersion[0] + "." + bukkitVersion[1], ConfigHandler.MINECRAFT_VERSION)) {
                Chat.console(Phrase.build(Phrase.VERSION_REQUIRED, "Minecraft", ConfigHandler.MINECRAFT_VERSION));
                return false;
            }
            if (Util.newVersion(ConfigHandler.LATEST_VERSION, bukkitVersion[0] + "." + bukkitVersion[1]) && Util.isBranch("master")) {
                Chat.console(Phrase.build(Phrase.VERSION_INCOMPATIBLE, "Minecraft", bukkitVersion[0] + "." + bukkitVersion[1]));
                return false;
            }
            String[] javaVersion = (System.getProperty("java.version").replaceAll("[^0-9.]", "") + ".0").split("\\.");
            if (Util.newVersion(javaVersion[0] + "." + javaVersion[1], ConfigHandler.JAVA_VERSION)) {
                Chat.console(Phrase.build(Phrase.VERSION_REQUIRED, "Java", ConfigHandler.JAVA_VERSION));
                return false;
            }

            if (ConfigHandler.EDITION_BRANCH.length() == 0) {
                Chat.sendConsoleMessage(Color.RED + "[CoreProtect] " + Phrase.build(Phrase.INVALID_BRANCH_1));
                Chat.sendConsoleMessage(Color.GREY + "[CoreProtect] " + Phrase.build(Phrase.INVALID_BRANCH_2));
                Chat.sendConsoleMessage(Color.GREY + "[CoreProtect] " + Phrase.build(Phrase.INVALID_BRANCH_3));
                return false;
            }

            ConfigHandler.SERVER_VERSION = Integer.parseInt(bukkitVersion[1]);
        }
        catch (Exception e) {
            e.printStackTrace();
            return false;
        }

        return true;
    }

    private static void safeShutdown(CoreProtect plugin) {
        try {
            /* if server is stopping, log disconnections of online players */
            if (ConfigHandler.serverRunning && PaperAdapter.ADAPTER.isStopping(plugin.getServer())) {
                for (Player player : plugin.getServer().getOnlinePlayers()) {
                    PlayerQuitListener.queuePlayerQuit(player);
                }
            }

            if (!ConfigHandler.isFolia) {
                Iterator<Entry<Location, BlockData>> iterator = Teleport.revertBlocks.entrySet().iterator();
                while (iterator.hasNext()) {
                    Entry<Location, BlockData> entry = iterator.next();
                    entry.getKey().getBlock().setBlockData(entry.getValue());
                    iterator.remove();
                }
            }

            ConfigHandler.serverRunning = false;
            long shutdownTime = System.currentTimeMillis();
            long alertTime = shutdownTime + (10 * 1000);
            if (ConfigHandler.converterRunning) {
                Chat.console(Phrase.build(Phrase.FINISHING_CONVERSION));
            }
            else {
                Chat.console(Phrase.build(Phrase.FINISHING_LOGGING));
            }

            if (ConfigHandler.migrationRunning) {
                ConfigHandler.purgeRunning = false;
            }
            while ((Consumer.isRunning() || ConfigHandler.converterRunning) && !ConfigHandler.purgeRunning) {
                long time = System.currentTimeMillis();
                if (time >= alertTime) {
                    if (!ConfigHandler.converterRunning) {
                        int consumerId = (Consumer.currentConsumer == 1) ? 1 : 0;
                        int consumerCount = Consumer.getConsumerSize(consumerId) + Process.getCurrentConsumerSize();
                        Chat.console(Phrase.build(Phrase.LOGGING_ITEMS, String.format("%,d", consumerCount)));
                    }
                    alertTime = alertTime + (30 * 1000);
                }
                else if (!ConfigHandler.databaseReachable && (time - shutdownTime) >= (5 * 60 * 1000)) {
                    Chat.console(Phrase.build(Phrase.DATABASE_UNREACHABLE));
                    break;
                }
                else if ((time - shutdownTime) >= (15 * 60 * 1000)) {
                    Chat.console(Phrase.build(Phrase.LOGGING_TIME_LIMIT));
                    break;
                }

                Thread.sleep(100);
            }

            ConfigHandler.performDisable();
            Chat.console(Phrase.build(Phrase.DISABLE_SUCCESS, "CoreProtect v" + plugin.getDescription().getVersion()));
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

}
