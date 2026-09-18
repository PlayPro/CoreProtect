package net.coreprotect.listener.player.inspector;

import net.coreprotect.config.ConfigHandler;
import net.coreprotect.database.Database;
import net.coreprotect.language.Phrase;
import net.coreprotect.utility.Chat;
import net.coreprotect.utility.Color;
import net.coreprotect.utility.LookupThrottle;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class BaseInspector {

    private static final int LOOKUP_THREADS = 2;
    private static final int LOOKUP_QUEUE_SIZE = 64;
    private static final long LOOKUP_THREAD_IDLE_SECONDS = 30L;
    private static final long SHUTDOWN_WAIT_SECONDS = 5L;
    private static final AtomicInteger lookupThreadCount = new AtomicInteger();
    private static final ThreadPoolExecutor lookupExecutor = createLookupExecutor();

    private static ThreadPoolExecutor createLookupExecutor() {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(LOOKUP_THREADS, LOOKUP_THREADS, LOOKUP_THREAD_IDLE_SECONDS, TimeUnit.SECONDS, new ArrayBlockingQueue<>(LOOKUP_QUEUE_SIZE), runnable -> {
            Thread thread = new Thread(runnable, "CoreProtect-Inspector-" + lookupThreadCount.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        });
        executor.allowCoreThreadTimeOut(true);
        return executor;
    }

    protected void startInspection(Player player, Runnable inspection) {
        try {
            acquireInspection(player);
        } catch (InspectionException e) {
            Chat.sendMessage(player, e.getMessage());
            return;
        }

        runLookup(player, () -> {
            try {
                inspection.run();
            } finally {
                LookupThrottle.release(player.getName());
            }
        });
    }

    /**
     * Runs a lookup on the shared inspector threads. The caller already holds the player's throttle slot and the lookup
     * releases it, so the slot is only released here when the queue is full.
     */
    public static void runLookup(Player player, Runnable lookup) {
        try {
            lookupExecutor.execute(lookup);
        } catch (RejectedExecutionException e) {
            LookupThrottle.release(player.getName());
            Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.DATABASE_BUSY));
        }
    }

    public static void shutdown() {
        lookupExecutor.shutdown();
        try {
            if (!lookupExecutor.awaitTermination(SHUTDOWN_WAIT_SECONDS, TimeUnit.SECONDS)) {
                lookupExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            lookupExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private void acquireInspection(Player player) throws InspectionException {
        if (ConfigHandler.converterRunning) {
            throw new InspectionException(Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.UPGRADE_IN_PROGRESS));
        }

        if (ConfigHandler.purgeRunning) {
            throw new InspectionException(Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.PURGE_IN_PROGRESS));
        }

        if (!LookupThrottle.tryAcquire(player.getName(), 100)) {
            throw new InspectionException(Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.DATABASE_BUSY));
        }
    }

    protected Connection getDatabaseConnection(Player player) throws Exception {
        Connection connection = Database.getConnection(true);
        if (connection == null) {
            throw new InspectionException(Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.DATABASE_BUSY));
        }

        return connection;
    }

    public static class InspectionException extends Exception {
        private static final long serialVersionUID = 1L;

        public InspectionException(String message) {
            super(message);
        }
    }
}
