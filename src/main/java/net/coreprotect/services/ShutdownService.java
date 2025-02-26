package net.coreprotect.services;

import java.util.Iterator;
import java.util.Map.Entry;

import org.bukkit.Location;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import net.coreprotect.config.ConfigHandler;
import net.coreprotect.consumer.Consumer;
import net.coreprotect.consumer.process.Process;
import net.coreprotect.language.Phrase;
import net.coreprotect.listener.player.PlayerQuitListener;
import net.coreprotect.paper.PaperAdapter;
import net.coreprotect.utility.Chat;
import net.coreprotect.utility.Teleport;

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

            ConfigHandler.serverRunning = false;
            long shutdownTime = System.currentTimeMillis();
            long nextAlertTime = shutdownTime + ALERT_INTERVAL_MS;

            if (ConfigHandler.converterRunning) {
                Chat.console(Phrase.build(Phrase.FINISHING_CONVERSION));
            }
            else {
                Chat.console(Phrase.build(Phrase.FINISHING_LOGGING));
            }

            if (ConfigHandler.migrationRunning) {
                ConfigHandler.purgeRunning = false;
            }

            waitForPendingOperations(shutdownTime, nextAlertTime);

            ConfigHandler.performDisable();
            Chat.console(Phrase.build(Phrase.DISABLE_SUCCESS, "CoreProtect v" + plugin.getDescription().getVersion()));
        }
        catch (Exception e) {
            e.printStackTrace();
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
    private static void waitForPendingOperations(long shutdownTime, long nextAlertTime) throws InterruptedException {
        while ((Consumer.isRunning() || ConfigHandler.converterRunning) && !ConfigHandler.purgeRunning) {
            long currentTime = System.currentTimeMillis();

            if (currentTime >= nextAlertTime) {
                if (!ConfigHandler.converterRunning) {
                    int consumerId = (Consumer.currentConsumer == 1) ? 1 : 0;
                    int consumerCount = Consumer.getConsumerSize(consumerId) + Process.getCurrentConsumerSize();
                    Chat.console(Phrase.build(Phrase.LOGGING_ITEMS, String.format("%,d", consumerCount)));
                }
                nextAlertTime = currentTime + ALERT_INTERVAL_MS;
            }
            else if (!ConfigHandler.databaseReachable && (currentTime - shutdownTime) >= DB_UNREACHABLE_TIMEOUT_MS) {
                Chat.console(Phrase.build(Phrase.DATABASE_UNREACHABLE));
                break;
            }
            else if ((currentTime - shutdownTime) >= MAX_SHUTDOWN_WAIT_MS) {
                Chat.console(Phrase.build(Phrase.LOGGING_TIME_LIMIT));
                break;
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
