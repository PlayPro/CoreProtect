package net.coreprotect.consumer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.bukkit.Bukkit;
import org.bukkit.block.BlockState;
import org.bukkit.inventory.ItemStack;

import net.coreprotect.CoreProtect;
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.consumer.process.Process;
import net.coreprotect.language.Phrase;
import net.coreprotect.utility.Chat;
import net.coreprotect.utility.Color;
import net.coreprotect.utility.ErrorReporter;

public class Consumer extends Process implements Runnable, Thread.UncaughtExceptionHandler {

    public enum OperationStartResult {
        STARTED,
        PURGE_RUNNING,
        ROLLBACK_RUNNING,
        RELOAD_RUNNING,
        PERSISTENCE_HALTED,
        INTERRUPTED
    }

    private static Thread consumerThread = null;
    private static final ReentrantReadWriteLock databaseLifecycle = new ReentrantReadWriteLock(true);
    private static final Object rollbackPurgeGate = new Object();
    private static long pendingRollbackPublications = 0;
    private static volatile boolean backgroundPurgeRunning = false;
    private static volatile boolean backgroundPurgePausesPersistence = false;
    private static volatile boolean databaseReloadPaused = false;
    private static volatile boolean databaseReloadRunning = false;
    private static boolean databaseReloadBlockedForShutdown = false;
    private static CompletableFuture<Void> databaseReloadShutdownSignal = new CompletableFuture<>();
    public static volatile int currentConsumer = 0;
    public static volatile boolean isPaused = false;
    public static volatile boolean transacting = false;
    public static volatile boolean interrupt = false;
    protected static volatile boolean pausedSuccess = false;
    private static volatile boolean persistenceHalted = false;

    public static ConcurrentHashMap<Integer, ArrayList<Object[]>> consumer = new ConcurrentHashMap<>(4, 0.75f, 2);
    // public static ConcurrentHashMap<Integer, Integer[]> consumer_id = new ConcurrentHashMap<>();
    public static Map<Integer, Integer[]> consumer_id = Collections.synchronizedMap(new HashMap<>());
    public static ConcurrentHashMap<Integer, Map<Integer, String[]>> consumerUsers = new ConcurrentHashMap<>(4, 0.75f, 2);
    @Deprecated
    public static ConcurrentHashMap<Integer, Map<Integer, String>> consumerStrings = new ConcurrentHashMap<>(4, 0.75f, 2);
    @Deprecated
    public static ConcurrentHashMap<Integer, Map<Integer, Object[]>> consumerSigns = new ConcurrentHashMap<>(4, 0.75f, 2);
    @Deprecated
    public static ConcurrentHashMap<Integer, Map<Integer, ItemStack[]>> consumerContainers = new ConcurrentHashMap<>(4, 0.75f, 2);
    @Deprecated
    public static ConcurrentHashMap<Integer, Map<Integer, Object>> consumerInventories = new ConcurrentHashMap<>(4, 0.75f, 2);
    @Deprecated
    public static ConcurrentHashMap<Integer, Map<Integer, List<BlockState>>> consumerBlockList = new ConcurrentHashMap<>(4, 0.75f, 2);
    @Deprecated
    public static ConcurrentHashMap<Integer, Map<Integer, List<Object[]>>> consumerObjectArrayList = new ConcurrentHashMap<>(4, 0.75f, 2);
    @Deprecated
    public static ConcurrentHashMap<Integer, Map<Integer, List<Object>>> consumerObjectList = new ConcurrentHashMap<>(4, 0.75f, 2);

    public static ConcurrentHashMap<Integer, Map<Integer, Object>> consumerObjects = new ConcurrentHashMap<>(4, 0.75f, 2);
    // ^merge maps into single object based map

    private static void errorDelay() {
        try {
            Thread.sleep(30000); // 30 seconds
        }
        catch (Exception e) {
            ErrorReporter.report(e);
        }
    }

    protected static long reserveConsumer() {
        return reserveConsumers(1);
    }

    protected static long reserveConsumers(int count) {
        synchronized (Consumer.consumer_id) {
            int consumer = Consumer.currentConsumer;
            Integer[] state = Consumer.consumer_id.get(consumer);
            int id = state[0];
            Consumer.consumer_id.put(consumer, new Integer[] { id + count, state[1] + count });
            return ((long) consumer << 32) | (id & 0xffffffffL);
        }
    }

    protected static void completeReservation(long reservation, int count) {
        int consumer = (int) (reservation >>> 32);
        synchronized (Consumer.consumer_id) {
            Integer[] state = Consumer.consumer_id.get(consumer);
            if (count <= 0 || state == null || state[1] < count) {
                throw new IllegalStateException("Invalid consumer reservation completion");
            }
            Consumer.consumer_id.put(consumer, new Integer[] { state[0], state[1] - count });
        }
    }

    public static int getConsumerSize(int id) {
        if (id == 0 || id == 1) {
            return Consumer.consumer.get(id).size();
        }

        return 0;
    }

    public static void initialize() {
        synchronized (rollbackPurgeGate) {
            persistenceHalted = false;
            pendingRollbackPublications = 0;
            databaseReloadBlockedForShutdown = false;
            databaseReloadShutdownSignal.complete(null);
            databaseReloadShutdownSignal = new CompletableFuture<>();
        }
        databaseReloadPaused = false;
        databaseReloadRunning = false;
        backgroundPurgeRunning = false;
        backgroundPurgePausesPersistence = false;
        Consumer.consumer.put(0, new ArrayList<>());
        Consumer.consumer.put(1, new ArrayList<>());
        Consumer.consumer_id.put(0, new Integer[] { 0, 0 });
        Consumer.consumer_id.put(1, new Integer[] { 0, 0 });
        Consumer.consumerUsers.put(0, new HashMap<>());
        Consumer.consumerUsers.put(1, new HashMap<>());
        Consumer.consumerObjects.put(0, new HashMap<>());
        Consumer.consumerObjects.put(1, new HashMap<>());
        Consumer.consumerStrings.put(0, new HashMap<>());
        Consumer.consumerStrings.put(1, new HashMap<>());
        Consumer.consumerSigns.put(0, new HashMap<>());
        Consumer.consumerSigns.put(1, new HashMap<>());
        Consumer.consumerInventories.put(0, new HashMap<>());
        Consumer.consumerInventories.put(1, new HashMap<>());
        Consumer.consumerBlockList.put(0, new HashMap<>());
        Consumer.consumerBlockList.put(1, new HashMap<>());
        Consumer.consumerObjectArrayList.put(0, new HashMap<>());
        Consumer.consumerObjectArrayList.put(1, new HashMap<>());
        Consumer.consumerObjectList.put(0, new HashMap<>());
        Consumer.consumerObjectList.put(1, new HashMap<>());
        Consumer.consumerContainers.put(0, new HashMap<>());
        Consumer.consumerContainers.put(1, new HashMap<>());
    }

    public static boolean isRunning() {
        return consumerThread != null && consumerThread.isAlive();
    }

    public static boolean isPersistenceHalted() {
        return persistenceHalted;
    }

    public static void lockDatabaseAccess() {
        databaseLifecycle.readLock().lock();
    }

    public static void unlockDatabaseAccess() {
        databaseLifecycle.readLock().unlock();
    }

    public static void lockDatabaseMaintenance() {
        databaseLifecycle.writeLock().lock();
    }

    public static void lockDatabaseMaintenanceInterruptibly() throws InterruptedException {
        databaseLifecycle.writeLock().lockInterruptibly();
    }

    public static void unlockDatabaseMaintenance() {
        databaseLifecycle.writeLock().unlock();
    }

    public static boolean isDatabaseReloadBlocked() {
        return databaseReloadPaused && !databaseLifecycle.isWriteLockedByCurrentThread();
    }

    public static boolean isDatabaseReloadPaused() {
        return databaseReloadPaused;
    }

    public static boolean isDatabaseReloadRunning() {
        return databaseReloadRunning;
    }

    public static boolean isBackgroundPurgeRunning() {
        return backgroundPurgeRunning;
    }

    public static void requireDatabaseReload() {
        synchronized (rollbackPurgeGate) {
            databaseReloadPaused = true;
        }
    }

    public static OperationStartResult beginDatabaseReload() {
        synchronized (rollbackPurgeGate) {
            if (databaseReloadBlockedForShutdown) {
                return OperationStartResult.INTERRUPTED;
            }
            if (persistenceHalted) {
                return OperationStartResult.PERSISTENCE_HALTED;
            }
            if (databaseReloadRunning) {
                return OperationStartResult.RELOAD_RUNNING;
            }
            if (ConfigHandler.purgeRunning || backgroundPurgeRunning) {
                return OperationStartResult.PURGE_RUNNING;
            }
            if (!ConfigHandler.activeRollbacks.isEmpty() || pendingRollbackPublications > 0) {
                return OperationStartResult.ROLLBACK_RUNNING;
            }
            databaseReloadRunning = true;
            databaseReloadPaused = true;
        }
        return OperationStartResult.STARTED;
    }

    public static void blockDatabaseReloadForShutdown() {
        synchronized (rollbackPurgeGate) {
            databaseReloadBlockedForShutdown = true;
            databaseReloadShutdownSignal.complete(null);
        }
    }

    public static CompletableFuture<Void> databaseReloadShutdownSignal() {
        synchronized (rollbackPurgeGate) {
            return databaseReloadShutdownSignal.copy();
        }
    }

    public static void lockDatabaseReload() {
        databaseLifecycle.writeLock().lock();
    }

    public static boolean lockDatabaseReload(long timeoutMillis) throws InterruptedException {
        return databaseLifecycle.writeLock().tryLock(Math.max(0L, timeoutMillis), java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    public static void endDatabaseReload(boolean resumePersistence) {
        try {
            synchronized (rollbackPurgeGate) {
                databaseReloadRunning = false;
                if (resumePersistence) {
                    databaseReloadPaused = false;
                    if (!ConfigHandler.converterRunning) {
                        isPaused = false;
                    }
                }
            }
        }
        finally {
            if (databaseLifecycle.isWriteLockedByCurrentThread()) {
                databaseLifecycle.writeLock().unlock();
            }
        }
    }

    public static synchronized void haltPersistence() {
        synchronized (rollbackPurgeGate) {
            if (persistenceHalted) {
                return;
            }
            persistenceHalted = true;
        }
        ConfigHandler.databaseReachable = false;
        Chat.sendConsoleMessage(Color.RED + "[CoreProtect] " + Phrase.build(Phrase.DATABASE_PERSISTENCE_HALTED));
        Chat.sendConsoleMessage(Color.RED + "[CoreProtect] " + Phrase.build(Phrase.DATABASE_QUEUE_LOSS));
    }

    public static OperationStartResult claimPurge() {
        synchronized (rollbackPurgeGate) {
            if (persistenceHalted) {
                return OperationStartResult.PERSISTENCE_HALTED;
            }
            if (databaseReloadPaused) {
                return OperationStartResult.RELOAD_RUNNING;
            }
            if (ConfigHandler.purgeRunning || backgroundPurgeRunning) {
                return OperationStartResult.PURGE_RUNNING;
            }
            if (!ConfigHandler.activeRollbacks.isEmpty() || pendingRollbackPublications > 0) {
                return OperationStartResult.ROLLBACK_RUNNING;
            }
            ConfigHandler.purgeRunning = true;
            return OperationStartResult.STARTED;
        }
    }

    public static void releasePurge() {
        synchronized (rollbackPurgeGate) {
            ConfigHandler.purgeRunning = false;
        }
    }

    public static OperationStartResult claimBackgroundPurge() {
        boolean pausePersistence;
        synchronized (rollbackPurgeGate) {
            pausePersistence = ConfigHandler.databaseType.isClickHouse();
            OperationStartResult result = beginBackgroundPurge(pausePersistence);
            if (result != OperationStartResult.STARTED) {
                return result;
            }
        }
        return lockBackgroundPurge(pausePersistence);
    }

    public static OperationStartResult claimBackgroundPurge(boolean pausePersistence) {
        synchronized (rollbackPurgeGate) {
            OperationStartResult result = beginBackgroundPurge(pausePersistence);
            if (result != OperationStartResult.STARTED) {
                return result;
            }
        }
        return lockBackgroundPurge(pausePersistence);
    }

    private static OperationStartResult beginBackgroundPurge(boolean pausePersistence) {
        if (persistenceHalted) {
            return OperationStartResult.PERSISTENCE_HALTED;
        }
        if (databaseReloadPaused) {
            return OperationStartResult.RELOAD_RUNNING;
        }
        if (ConfigHandler.purgeRunning || backgroundPurgeRunning) {
            return OperationStartResult.PURGE_RUNNING;
        }
        if (!ConfigHandler.activeRollbacks.isEmpty() || pendingRollbackPublications > 0) {
            return OperationStartResult.ROLLBACK_RUNNING;
        }
        backgroundPurgeRunning = true;
        backgroundPurgePausesPersistence = pausePersistence;
        return OperationStartResult.STARTED;
    }

    private static OperationStartResult lockBackgroundPurge(boolean pausePersistence) {
        if (pausePersistence) {
            try {
                databaseLifecycle.writeLock().lockInterruptibly();
            }
            catch (InterruptedException exception) {
                synchronized (rollbackPurgeGate) {
                    backgroundPurgePausesPersistence = false;
                    backgroundPurgeRunning = false;
                }
                Thread.currentThread().interrupt();
                return OperationStartResult.INTERRUPTED;
            }
            synchronized (rollbackPurgeGate) {
                if (persistenceHalted) {
                    databaseLifecycle.writeLock().unlock();
                    backgroundPurgePausesPersistence = false;
                    backgroundPurgeRunning = false;
                    return OperationStartResult.PERSISTENCE_HALTED;
                }
            }
        }
        return OperationStartResult.STARTED;
    }

    public static void releaseBackgroundPurge() {
        synchronized (rollbackPurgeGate) {
            if (!backgroundPurgeRunning) {
                return;
            }
            if (backgroundPurgePausesPersistence) {
                databaseLifecycle.writeLock().unlock();
            }
            backgroundPurgePausesPersistence = false;
            backgroundPurgeRunning = false;
        }
    }

    public static OperationStartResult claimRollback(String user) {
        return claimRollback(user, true);
    }

    public static OperationStartResult claimRollback(String user, boolean requiresPersistence) {
        synchronized (rollbackPurgeGate) {
            if (requiresPersistence && persistenceHalted) {
                return OperationStartResult.PERSISTENCE_HALTED;
            }
            if (databaseReloadPaused) {
                return OperationStartResult.RELOAD_RUNNING;
            }
            if (ConfigHandler.purgeRunning || backgroundPurgeRunning) {
                return OperationStartResult.PURGE_RUNNING;
            }
            if (ConfigHandler.activeRollbacks.containsKey(user)) {
                return OperationStartResult.ROLLBACK_RUNNING;
            }
            ConfigHandler.activeRollbacks.put(user, true);
            return OperationStartResult.STARTED;
        }
    }

    public static void releaseRollback(String user) {
        synchronized (rollbackPurgeGate) {
            ConfigHandler.activeRollbacks.remove(user);
        }
    }

    public static void registerRollbackPublications(int count) {
        if (count <= 0) {
            return;
        }
        synchronized (rollbackPurgeGate) {
            pendingRollbackPublications += count;
        }
    }

    public static void completeRollbackPublications(int count) {
        if (count <= 0) {
            return;
        }
        synchronized (rollbackPurgeGate) {
            if (pendingRollbackPublications < count) {
                throw new IllegalStateException("Invalid rollback publication completion");
            }
            pendingRollbackPublications -= count;
        }
    }

    private static void pauseConsumer(int process_id) {
        try {
            while (Consumer.consumer_id.get(process_id)[1] > 0 || ((ConfigHandler.serverRunning || ConfigHandler.converterRunning || ConfigHandler.migrationRunning) && (Consumer.isPaused || ConfigHandler.pauseConsumer || ConfigHandler.purgeRunning))) {
                pausedSuccess = true;
                Thread.sleep(100);
            }
        }
        catch (Exception e) {
            ErrorReporter.report(e);
        }
        pausedSuccess = false;
    }

    @Override
    public void run() {
        boolean lastRun = false;

        while (ConfigHandler.serverRunning || ConfigHandler.converterRunning || !lastRun) {
            if (!ConfigHandler.serverRunning && !ConfigHandler.converterRunning) {
                lastRun = true;
            }
            if (persistenceHalted) {
                if (!lastRun) {
                    errorDelay();
                }
                continue;
            }
            try {
                int process_id = 0;
                synchronized (Consumer.consumer_id) {
                    if (currentConsumer == 0) {
                        currentConsumer = 1;
                    }
                    else {
                        process_id = 1;
                        currentConsumer = 0;
                    }
                }
                Thread.sleep(500);
                pauseConsumer(process_id);
                databaseLifecycle.readLock().lock();
                try {
                    if (!databaseReloadPaused && !isPaused && !persistenceHalted) {
                        Process.processConsumer(process_id, lastRun);
                    }
                }
                finally {
                    databaseLifecycle.readLock().unlock();
                }
            }
            catch (Exception e) {
                ErrorReporter.report(e);
                errorDelay();
            }
        }
    }

    @Override
    public void uncaughtException(Thread thread, Throwable e) {
        ErrorReporter.report(e);
        Bukkit.getPluginManager().disablePlugin(CoreProtect.getInstance());
    }

    public static void startConsumer() {
        if (!isRunning()) {
            consumerThread = new Thread(new Consumer());
            consumerThread.setUncaughtExceptionHandler(new Consumer());
            consumerThread.start();
        }
    }
}
