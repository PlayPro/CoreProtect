package net.coreprotect.listener.player.inspector;

import java.sql.Connection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.bukkit.entity.Player;

import net.coreprotect.config.ConfigHandler;
import net.coreprotect.database.Database;
import net.coreprotect.language.Phrase;
import net.coreprotect.utility.Chat;
import net.coreprotect.utility.Color;
import net.coreprotect.utility.LookupThrottle;

public abstract class BaseInspector {

    private static final int LOOKUP_THREADS = 2;
    private static final int LOOKUP_QUEUE_SIZE = 64;
    private static final long LOOKUP_THREAD_IDLE_SECONDS = 30L;
    private static final long SHUTDOWN_WAIT_SECONDS = 5L;
    private static final long INTERRUPT_WAIT_SECONDS = 2L;
    private static final AtomicInteger lookupThreadCount = new AtomicInteger();
    private static final Set<InspectorLookup> activeLookups = ConcurrentHashMap.newKeySet();
    private static volatile ThreadPoolExecutor lookupExecutor;

    /**
     * Creates the inspector threads for this enable. Each enable gets a new executor because a disable shuts the
     * previous one down.
     */
    public static void startLookups() {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(LOOKUP_THREADS, LOOKUP_THREADS, LOOKUP_THREAD_IDLE_SECONDS, TimeUnit.SECONDS, new ArrayBlockingQueue<>(LOOKUP_QUEUE_SIZE), runnable -> {
            Thread thread = new Thread(runnable, "CoreProtect-Inspector-" + lookupThreadCount.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        });
        executor.allowCoreThreadTimeOut(true);
        lookupExecutor = executor;
    }

    protected void startInspection(Player player, Runnable inspection) {
        try {
            acquireInspection(player);
        }
        catch (InspectionException e) {
            Chat.sendMessage(player, e.getMessage());
            return;
        }

        runLookup(player, () -> {
            try {
                inspection.run();
            }
            finally {
                LookupThrottle.release(player.getName());
            }
        });
    }

    /**
     * Runs a lookup on the shared inspector threads. The caller holds the player's throttle slot and the lookup
     * releases it, so it is only released here when the lookup is rejected or discarded at shutdown.
     */
    public static void runLookup(Player player, Runnable lookup) {
        ThreadPoolExecutor executor = lookupExecutor;
        try {
            if (executor == null) {
                throw new RejectedExecutionException();
            }
            executor.execute(new InspectorLookup(player.getName(), lookup));
        }
        catch (RejectedExecutionException e) {
            LookupThrottle.release(player.getName());
            Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.DATABASE_BUSY));
        }
        catch (RuntimeException | Error e) {
            LookupThrottle.release(player.getName());
            throw e;
        }
    }

    /**
     * Lets queued lookups finish for a few seconds, then discards the rest and interrupts the running ones. Lookups
     * that are still running after the interrupt are logged by thread and player.
     */
    public static void shutdown() {
        ThreadPoolExecutor executor = lookupExecutor;
        if (executor == null) {
            return;
        }

        executor.shutdown();
        try {
            if (executor.awaitTermination(SHUTDOWN_WAIT_SECONDS, TimeUnit.SECONDS)) {
                return;
            }

            discardQueued(executor.shutdownNow());
            if (executor.awaitTermination(INTERRUPT_WAIT_SECONDS, TimeUnit.SECONDS)) {
                return;
            }
        }
        catch (InterruptedException e) {
            discardQueued(executor.shutdownNow());
            Thread.currentThread().interrupt();
        }

        for (InspectorLookup lookup : activeLookups) {
            Thread thread = lookup.thread;
            if (thread != null && thread.isAlive()) {
                StackTraceElement[] stack = thread.getStackTrace();
                String location = stack.length > 0 ? " at " + stack[0] : "";
                Chat.console("Inspector lookup for " + lookup.playerName + " is still running on " + thread.getName() + location + " after disable.");
            }
        }
    }

    private static void discardQueued(List<Runnable> queued) {
        for (Runnable runnable : queued) {
            if (runnable instanceof InspectorLookup) {
                LookupThrottle.release(((InspectorLookup) runnable).playerName);
            }
        }
    }

    private static final class InspectorLookup implements Runnable {
        private final String playerName;
        private final Runnable lookup;
        private volatile Thread thread;

        private InspectorLookup(String playerName, Runnable lookup) {
            this.playerName = playerName;
            this.lookup = lookup;
        }

        @Override
        public void run() {
            thread = Thread.currentThread();
            activeLookups.add(this);
            try {
                lookup.run();
            }
            finally {
                activeLookups.remove(this);
            }
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
