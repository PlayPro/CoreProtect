package net.coreprotect.consumer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.block.BlockState;
import org.bukkit.inventory.ItemStack;

import net.coreprotect.CoreProtect;
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.consumer.process.Process;
import net.coreprotect.utility.ErrorReporter;

public class Consumer extends Process implements Runnable, Thread.UncaughtExceptionHandler {

    public enum OperationStartResult {
        STARTED,
        PURGE_RUNNING,
        ROLLBACK_RUNNING
    }

    private static Thread consumerThread = null;
    private static final Object rollbackPurgeGate = new Object();
    private static long pendingRollbackPublications = 0;
    public static volatile int currentConsumer = 0;
    public static volatile boolean isPaused = false;
    public static volatile boolean transacting = false;
    public static volatile boolean interrupt = false;
    protected static volatile boolean pausedSuccess = false;

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

    public static OperationStartResult claimPurge() {
        synchronized (rollbackPurgeGate) {
            if (ConfigHandler.purgeRunning) {
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

    public static OperationStartResult claimRollback(String user) {
        synchronized (rollbackPurgeGate) {
            if (ConfigHandler.purgeRunning) {
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
                Process.processConsumer(process_id, lastRun);
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
