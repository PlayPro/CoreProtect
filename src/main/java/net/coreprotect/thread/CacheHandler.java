package net.coreprotect.thread;

import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.bukkit.Location;
import org.bukkit.World;

import net.coreprotect.config.ConfigHandler;
import net.coreprotect.utility.ErrorReporter;

public class CacheHandler implements Runnable {

    private static final long HOPPER_CACHE_TTL_MILLIS = 60_000L;
    private static final long THROTTLE_CACHE_TTL_MILLIS = 60_000L;

    public static Map<String, Object[]> lookupCache = new ConcurrentHashMap<>();
    public static Map<String, Object[]> breakCache = new ConcurrentHashMap<>();
    public static Map<String, Object[]> interactCache = new ConcurrentHashMap<>();
    public static Map<String, Object[]> entityCache = new ConcurrentHashMap<>();
    public static ConcurrentHashMap<String, Object[]> pistonCache = new ConcurrentHashMap<>();
    public static ConcurrentHashMap<String, Object[]> spreadCache = new ConcurrentHashMap<>();
    public static ConcurrentHashMap<String, int[]> containerDuplicateCache = new ConcurrentHashMap<>();
    public static ConcurrentHashMap<String, int[]> flowDuplicateCache = new ConcurrentHashMap<>();
    public static ConcurrentHashMap<String, int[]> bonemealDuplicateCache = new ConcurrentHashMap<>();
    public static ConcurrentHashMap<String, int[]> entityKillDuplicateCache = new ConcurrentHashMap<>();
    public static ConcurrentHashMap<String, Object[]> redstoneCache = new ConcurrentHashMap<>();
    public static ConcurrentHashMap<String, Object[]> fallingBlockSpawnCache = new ConcurrentHashMap<>();

    private static final Object LOCK = new Object();
    private static volatile Thread thread;
    private static volatile CacheHandler activeHandler;
    private volatile boolean cancelled;

    public static void startThread() {
        synchronized (LOCK) {
            Thread existing = thread;
            CacheHandler existingHandler = activeHandler;
            if (existing != null && existing.isAlive() && existingHandler != null && !existingHandler.cancelled) {
                return;
            }
            CacheHandler handler = new CacheHandler();
            Thread cacheThread = new Thread(handler, "CoreProtect-Cache");
            cacheThread.setDaemon(true);
            activeHandler = handler;
            thread = cacheThread;
            cacheThread.start();
        }
    }

    public static void stopThread(long timeoutMillis) {
        Thread cacheThread;
        CacheHandler handler;
        synchronized (LOCK) {
            cacheThread = thread;
            handler = activeHandler;
            thread = null;
            activeHandler = null;
        }
        if (handler != null) {
            handler.cancelled = true;
        }
        if (cacheThread == null) {
            return;
        }

        cacheThread.interrupt();
        long boundedTimeout = Math.max(0L, timeoutMillis);
        if (boundedTimeout == 0L) {
            return;
        }

        boolean callerInterrupted = Thread.interrupted();
        try {
            long deadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(boundedTimeout);
            while (cacheThread.isAlive()) {
                long remainingNanos = deadlineNanos - System.nanoTime();
                if (remainingNanos <= 0L) {
                    break;
                }
                long millis = TimeUnit.NANOSECONDS.toMillis(remainingNanos);
                int nanos = (int) (remainingNanos - TimeUnit.MILLISECONDS.toNanos(millis));
                try {
                    if (millis > 0L || nanos > 0) {
                        cacheThread.join(millis, nanos);
                    }
                }
                catch (InterruptedException e) {
                    callerInterrupted = true;
                    if (cacheThread.isAlive()) {
                        cacheThread.interrupt();
                    }
                }
            }
        }
        finally {
            if (callerInterrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public static String locationKey(Location location) {
        if (location == null || location.getWorld() == null) {
            return "";
        }
        return locationKey(location.getWorld(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    public static String locationKey(World world, int x, int y, int z) {
        return world.getUID() + ":" + x + ":" + y + ":" + z;
    }

    public static boolean shouldSuppressRepeat(ConcurrentHashMap<String, int[]> cache, String cacheId, int threshold, int resetWindowSeconds) {
        if (cache == null || cacheId == null || cacheId.isEmpty() || threshold <= 0) {
            return false;
        }

        final int timestamp = (int) (System.currentTimeMillis() / 1000L);
        final boolean[] suppress = new boolean[1];
        cache.compute(cacheId, (key, value) -> {
            if (value == null) {
                suppress[0] = false;
                return new int[] { timestamp, 1 };
            }

            int previousTime = value[0];
            int previousCount = value[1];
            int count = (timestamp - previousTime <= resetWindowSeconds) ? (previousCount + 1) : 1;
            if (count > (threshold + 1)) {
                count = threshold + 1;
            }

            value[0] = timestamp;
            value[1] = count;
            suppress[0] = count > threshold;
            return value;
        });

        return suppress[0];
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Override
    public void run() {
        try {
            while (ConfigHandler.serverRunning && !cancelled && !Thread.currentThread().isInterrupted()) {
                try {
                    for (int id = 0; id < 14; id++) {
                        Thread.sleep(1000);
                        if (!ConfigHandler.serverRunning || cancelled || Thread.currentThread().isInterrupted()) {
                            return;
                        }

                        if (id == 8) {
                            cleanupDispenserCache();
                            cleanupPopulatedChunks();
                            cleanupHopperCaches();
                            cleanupThrottleCaches();
                            continue;
                        }

                        if (id >= 9 && id <= 12) {
                            int scanTime;
                            ConcurrentHashMap<String, int[]> duplicateCache;
                            switch (id) {
                                case 9:
                                    duplicateCache = CacheHandler.containerDuplicateCache;
                                    scanTime = 1800; // 30 minutes
                                    break;
                                case 10:
                                    duplicateCache = CacheHandler.flowDuplicateCache;
                                    scanTime = 900; // 15 minutes
                                    break;
                                case 11:
                                    duplicateCache = CacheHandler.bonemealDuplicateCache;
                                    scanTime = 900; // 15 minutes
                                    break;
                                default:
                                    duplicateCache = CacheHandler.entityKillDuplicateCache;
                                    scanTime = 900; // 15 minutes
                                    break;
                            }
                            cleanupDuplicateCache(duplicateCache, scanTime);
                            continue;
                        }

                        int scanTime = 30;
                        Map cache = CacheHandler.lookupCache;

                        switch (id) {
                            case 1:
                                cache = CacheHandler.breakCache;
                                break;
                            case 2:
                                cache = CacheHandler.pistonCache;
                                break;
                            case 3:
                                cache = CacheHandler.spreadCache;
                                scanTime = 1800; // 30 minutes
                                break;
                            case 4:
                                cache = CacheHandler.interactCache;
                                scanTime = 5;
                                break;
                            case 5:
                                cache = CacheHandler.redstoneCache;
                                scanTime = 1;
                                break;
                            case 6:
                                cache = CacheHandler.entityCache;
                                scanTime = 3600; // 60 minutes
                                break;
                            case 7:
                                cache = ConfigHandler.entityBlockMapper;
                                scanTime = 5;
                                break;
                            case 13:
                                cache = CacheHandler.fallingBlockSpawnCache;
                                scanTime = 30; // 30 seconds
                                break;
                        }

                        int timestamp = (int) (System.currentTimeMillis() / 1000L) - scanTime;
                        cache.values().removeIf(value -> {
                            try {
                                Object[] data = (Object[]) value;
                                int time = (data[0] instanceof Long) ? (int) ((long) data[0] / 1000L) : (int) data[0];
                                return time < timestamp;
                            }
                            catch (Exception e) {
                                return true; // malformed entry
                            }
                        });
                    }
                }
                catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                catch (Exception e) {
                    ErrorReporter.report(e);
                }
            }
        }
        finally {
            synchronized (LOCK) {
                if (thread == Thread.currentThread()) {
                    thread = null;
                    activeHandler = null;
                }
            }
        }
    }

    public static void cleanupDuplicateCache(ConcurrentHashMap<String, int[]> cache, int scanTimeSeconds) {
        if (cache == null) {
            return;
        }
        int timestamp = (int) (System.currentTimeMillis() / 1000L) - scanTimeSeconds;
        cache.forEach((key, value) -> {
            if (isDuplicateExpired(value, timestamp)) {
                cache.computeIfPresent(key, (currentKey, currentValue) -> isDuplicateExpired(currentValue, timestamp) ? null : currentValue);
            }
        });
    }

    private static boolean isDuplicateExpired(int[] value, int cutoff) {
        return value.length == 0 || value[0] < cutoff;
    }

    /**
     * Removes populated chunk entries older than 240 seconds, the longest window BlockPlaceLogger checks them against
     */
    public static void cleanupPopulatedChunks() {
        long expired = (System.currentTimeMillis() / 1000L) - 240;
        for (UUID worldId : ConfigHandler.populatedChunks.keySet()) {
            ConfigHandler.populatedChunks.computeIfPresent(worldId, (key, chunks) -> {
                chunks.values().removeIf(populatedAt -> populatedAt == null || populatedAt < expired);
                return chunks.isEmpty() ? null : chunks;
            });
        }
    }

    public static void cleanupHopperCaches() {
        long currentTime = System.currentTimeMillis();
        evictExpiredHopperMap(ConfigHandler.hopperSuccess, currentTime);
        evictExpiredHopperMap(ConfigHandler.hopperAbort, currentTime);
    }

    private static void evictExpiredHopperMap(ConcurrentHashMap<String, Object[]> map, long currentTime) {
        map.values().removeIf(data -> {
            if (data == null || data.length < 3 || !(data[2] instanceof Long)) {
                return true;
            }
            return (currentTime - (Long) data[2]) > HOPPER_CACHE_TTL_MILLIS;
        });
    }

    public static void cleanupThrottleCaches() {
        long currentTime = System.currentTimeMillis();
        evictExpiredThrottleMap(ConfigHandler.lookupThrottle, currentTime);
        evictExpiredThrottleMap(ConfigHandler.teleportThrottle, currentTime);
    }

    private static void evictExpiredThrottleMap(Map<String, Object[]> throttleMap, long currentTime) {
        synchronized (throttleMap) {
            throttleMap.entrySet().removeIf(entry -> {
                Object[] data = entry.getValue();
                if (data == null || data.length < 2) {
                    return true;
                }
                if (Boolean.TRUE.equals(data[0])) {
                    return false;
                }
                if (!(data[1] instanceof Long)) {
                    return true;
                }
                return (currentTime - (Long) data[1]) > THROTTLE_CACHE_TTL_MILLIS;
            });
        }
    }

    /**
     * Cleans up the dispenserNoChange cache by removing entries older than 5 seconds
     */
    private void cleanupDispenserCache() {
        try {
            long currentTime = System.currentTimeMillis();
            long expiryTime = 5000; // 5 seconds

            // Clean up dispenserNoChange map (now a nested map)
            Iterator<Entry<String, ConcurrentHashMap<String, Long>>> locationIterator = ConfigHandler.dispenserNoChange.entrySet().iterator();
            while (locationIterator.hasNext()) {
                Entry<String, ConcurrentHashMap<String, Long>> locationEntry = locationIterator.next();
                ConcurrentHashMap<String, Long> eventMap = locationEntry.getValue();

                if (eventMap.isEmpty()) {
                    // Remove empty location entries
                    locationIterator.remove();
                    continue;
                }

                // Clean up expired events within this location
                Iterator<Entry<String, Long>> eventIterator = eventMap.entrySet().iterator();
                while (eventIterator.hasNext()) {
                    Entry<String, Long> eventEntry = eventIterator.next();
                    if ((currentTime - eventEntry.getValue()) > expiryTime) {
                        eventIterator.remove();
                    }
                }

                // If all events were removed, remove the location entry
                if (eventMap.isEmpty()) {
                    locationIterator.remove();
                }
            }

            // Clean up dispenserPending map
            Iterator<Entry<String, Object[]>> pendingIterator = ConfigHandler.dispenserPending.entrySet().iterator();
            while (pendingIterator.hasNext()) {
                Entry<String, Object[]> entry = pendingIterator.next();
                Object[] data = entry.getValue();
                if (data != null && data.length > 1) {
                    long timestamp = (long) data[1];
                    if ((currentTime - timestamp) > expiryTime) {
                        pendingIterator.remove();
                    }
                }
            }
        }
        catch (Exception e) {
            ErrorReporter.report(e);
        }
    }
}
