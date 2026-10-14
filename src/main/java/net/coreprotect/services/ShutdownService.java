package net.coreprotect.services;

import java.util.Iterator;
import java.util.Map.Entry;

import org.bukkit.Location;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import net.coreprotect.command.PurgeCommand;
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.consumer.Consumer;
import net.coreprotect.consumer.process.Process;
import net.coreprotect.language.Phrase;
import net.coreprotect.listener.player.EntityInteractionListener;
import net.coreprotect.listener.player.InventoryChangeListener;
import net.coreprotect.listener.player.PlayerQuitListener;
import net.coreprotect.listener.player.inspector.BaseInspector;
import net.coreprotect.paper.PaperAdapter;
import net.coreprotect.thread.CacheHandler;
import net.coreprotect.utility.Chat;
import net.coreprotect.utility.Extensions;
import net.coreprotect.utility.EntitySpawnTracking;
import net.coreprotect.utility.Teleport;
import net.coreprotect.utility.ErrorReporter;
import net.coreprotect.utility.VersionUtils;

/**
 * Service responsible for handling plugin shutdown operations
 */
public class ShutdownService {

    private static final long ALERT_INTERVAL_MS = 30 * 1000; // 30 seconds
    private static final long MAX_SHUTDOWN_WAIT_MS = 15 * 60 * 1000; // 15 minutes
    private static final long DB_UNREACHABLE_TIMEOUT_MS = 5 * 60 * 1000; // 5 minutes

    private ShutdownService() {
        throw new IllegalStateException("Utility class");
    }

    /**
     * Safely shuts down the plugin, ensuring all data is saved
     *
     * @param plugin
     *            The CoreProtect plugin instance
     */
    public static void safeShutdown(Plugin plugin) {
        try {
            boolean loggingComplete;
            ConfigHandler.shutdownDrainRunning = true;
            try {
                Consumer.blockDatabaseReloadForShutdown();
                if (ConfigHandler.worldeditEnabled) {
                    VersionUtils.unloadWorldEdit();
                }
                Extensions.stopBackgroundService();
                PluginInitializationService.disableMetrics();

                // Log disconnections of online players if server is stopping
                if (ConfigHandler.serverRunning && PaperAdapter.ADAPTER.isStopping(plugin.getServer())) {
                    for (Player player : plugin.getServer().getOnlinePlayers()) {
                        PlayerQuitListener.queuePlayerQuit(player);
                    }
                }

                // Revert any teleport blocks if not using Folia
                if (!ConfigHandler.isFolia) {
                    revertTeleportBlocks();
                }

                if (ConfigHandler.serverRunning) {
                    EntitySpawnTracking.queueLoadedLocationsForShutdown();
                }

                EntityInteractionListener.flushPendingInteractions();
                InventoryChangeListener.flushPendingTransactionsForShutdown();

                long shutdownTime = System.currentTimeMillis();
                PurgeCommand.cancelForShutdown();
                waitForMaintenanceCompletion(shutdownTime);
                ConfigHandler.serverRunning = false;
                long nextAlertTime = System.currentTimeMillis() + ALERT_INTERVAL_MS;

                if (ConfigHandler.converterRunning) {
                    Chat.console(Phrase.build(Phrase.FINISHING_CONVERSION));
                }
                else {
                    Chat.console(Phrase.build(Phrase.FINISHING_LOGGING));
                }

                loggingComplete = waitForPendingOperations(shutdownTime, nextAlertTime);
            }
            finally {
                ConfigHandler.shutdownDrainRunning = false;
            }

            CacheHandler.stopThread(2000L);
            BaseInspector.shutdown();
            ConfigHandler.performDisable();
            if (loggingComplete) {
                Chat.console(Phrase.build(Phrase.DISABLE_SUCCESS, "CoreProtect v" + plugin.getDescription().getVersion()));
            }
        }
        catch (Exception e) {
            ErrorReporter.report(e);
        }
    }

    /**
     * Waits for pending operations (consumer tasks or conversions) to complete
     *
     * @param shutdownTime
     *            The time when shutdown began
     * @param nextAlertTime
     *            The time for the next status message
     */
    static boolean waitForPendingOperations(long shutdownTime, long nextAlertTime) throws InterruptedException {
        while (Consumer.isRunning() || ConfigHandler.converterRunning || ConfigHandler.purgeRunning || ConfigHandler.migrationRunning
                || Consumer.isDatabaseReloadRunning() || Consumer.isBackgroundPurgeRunning() || PurgeCommand.isPurgeWorkerRunning()) {
            long currentTime = System.currentTimeMillis();

            if (!ConfigHandler.databaseReachable && (currentTime - shutdownTime) >= DB_UNREACHABLE_TIMEOUT_MS) {
                Chat.console(Phrase.build(Phrase.DATABASE_UNREACHABLE));
                return false;
            }
            if ((currentTime - shutdownTime) >= MAX_SHUTDOWN_WAIT_MS) {
                Chat.console(Phrase.build(Phrase.LOGGING_TIME_LIMIT));
                return false;
            }
            if (currentTime >= nextAlertTime) {
                if (!ConfigHandler.converterRunning) {
                    int consumerId = (Consumer.currentConsumer == 1) ? 1 : 0;
                    int consumerCount = Consumer.getConsumerSize(consumerId) + Process.getCurrentConsumerSize();
                    Chat.console(Phrase.build(Phrase.LOGGING_ITEMS, String.format("%,d", consumerCount)));
                }
                nextAlertTime = currentTime + ALERT_INTERVAL_MS;
            }
            Thread.sleep(100);
        }
        if (Consumer.isPersistenceHalted()) {
            return false;
        }
        if (Consumer.hasPendingConsumerData()) {
            Chat.console(Phrase.build(Phrase.DATABASE_QUEUE_LOSS));
            return false;
        }
        return true;
    }

    static void waitForMaintenanceCompletion(long shutdownTime) throws InterruptedException {
        while (ConfigHandler.purgeRunning || Consumer.isBackgroundPurgeRunning() || PurgeCommand.isPurgeWorkerRunning()
                || Consumer.isDatabaseReloadRunning()) {
            if ((System.currentTimeMillis() - shutdownTime) >= MAX_SHUTDOWN_WAIT_MS) {
                return;
            }
            Thread.sleep(100);
        }
    }

    /**
     * Reverts any blocks that were temporarily changed during player teleports
     */
    private static void revertTeleportBlocks() {
        Iterator<Entry<Location, BlockData>> iterator = Teleport.revertBlocks.entrySet().iterator();
        while (iterator.hasNext()) {
            Entry<Location, BlockData> entry = iterator.next();
            entry.getKey().getBlock().setBlockData(entry.getValue());
            iterator.remove();
        }
    }
}
