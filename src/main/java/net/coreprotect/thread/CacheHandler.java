package net.coreprotect.thread;

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Location;

import net.coreprotect.config.ConfigHandler;

public class CacheHandler implements Runnable {

    public static Map<String, Object[]> lookupCache = Collections.synchronizedMap(new HashMap<>());
    public static Map<String, Object[]> breakCache = Collections.synchronizedMap(new HashMap<>());
    public static Map<String, Object[]> interactCache = Collections.synchronizedMap(new HashMap<>());
    public static Map<String, Object[]> entityCache = Collections.synchronizedMap(new HashMap<>());
    public static ConcurrentHashMap<String, Object[]> pistonCache = new ConcurrentHashMap<>(16, 0.75f, 2);
    public static ConcurrentHashMap<String, Object[]> spreadCache = new ConcurrentHashMap<>(16, 0.75f, 2);
    public static ConcurrentHashMap<Location, Object[]> redstoneCache = new ConcurrentHashMap<>(16, 0.75f, 2);

    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Override
    public void run() {
        while (ConfigHandler.serverRunning) {
            try {
                for (int id = 0; id < 9; id++) {
                    Thread.sleep(1000);
                    int scanTime = 30;
                    Map cache = CacheHandler.lookupCache;

                    switch (id) {
                        case 1:
                            cache = CacheHandler.breakCache;
                            break;
                        case 2:
                            cache = CacheHandler.pistonCache;
                            scanTime = 900; // 15 minutes
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
                        case 8:
                            // Clean up dispenserNoChange cache
                            cleanupDispenserCache();
                            continue;
                    }

                    int timestamp = (int) (System.currentTimeMillis() / 1000L) - scanTime;
                    Iterator<Entry> iterator = cache.entrySet().iterator();
                    while (iterator.hasNext()) {
                        try {
                            Map.Entry entry = iterator.next();
                            Object[] data = (Object[]) entry.getValue();
                            int time = (data[0] instanceof Long) ? (int) ((long) data[0] / 1000L) : (int) data[0];

                            if (time < timestamp) {
                                try {
                                    iterator.remove();
                                }
                                catch (Exception e) {
                                }
                            }
                        }
                        catch (Exception e) {
                            break;
                        }
                    }
                }
            }
            catch (Exception e) {
                e.printStackTrace();
            }
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
            e.printStackTrace();
        }
    }
}
