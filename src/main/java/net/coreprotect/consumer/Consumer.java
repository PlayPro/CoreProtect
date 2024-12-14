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

public class Consumer extends Process implements Runnable, Thread.UncaughtExceptionHandler {

    private static Thread consumerThread = null;
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
            e.printStackTrace();
        }
    }

    protected static int newConsumerId(int consumer) {
        int id = Consumer.consumer_id.get(consumer)[0];
        Consumer.consumer_id.put(consumer, new Integer[] { id + 1, 1 });
        return id;
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

    private static void pauseConsumer(int process_id) {
        try {
            while ((ConfigHandler.serverRunning || ConfigHandler.converterRunning || ConfigHandler.migrationRunning) && (Consumer.isPaused || ConfigHandler.pauseConsumer || ConfigHandler.purgeRunning || Consumer.consumer_id.get(process_id)[1] == 1)) {
                pausedSuccess = true;
                Thread.sleep(100);
            }
        }
        catch (Exception e) {
            e.printStackTrace();
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
                if (currentConsumer == 0) {
                    currentConsumer = 1;
                }
                else {
                    process_id = 1;
                    currentConsumer = 0;
                }
                Thread.sleep(500);
                pauseConsumer(process_id);
                Process.processConsumer(process_id, lastRun);
            }
            catch (Exception e) {
                e.printStackTrace();
                errorDelay();
            }
        }
    }

    @Override
    public void uncaughtException(Thread thread, Throwable e) {
        e.printStackTrace();
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
