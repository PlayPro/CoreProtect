package net.coreprotect.consumer.process;

import java.util.ArrayList;
import java.util.Locale;
import java.util.Map;

import org.bukkit.Location;

import net.coreprotect.config.ConfigHandler;
import net.coreprotect.consumer.Consumer;
import net.coreprotect.consumer.Queue;
import net.coreprotect.database.ConsumerWriteBatch;
import net.coreprotect.database.logger.ItemLogger;

class ItemTransactionProcess extends Queue {

    static void process(ConsumerWriteBatch preparedStmt, int batchCount, int processId, int id, int forceData, int time, int offset, String user, Object object) {
        if (object instanceof ItemLogger.PreparedTransaction) {
            ((ItemLogger.PreparedTransaction) object).log(preparedStmt, batchCount, user);
            return;
        }
        if (object instanceof Location) {
            Location location = (Location) object;
            String loggingItemId = getLoggingId(user, location);

            if (ConfigHandler.loggingItem.get(loggingItemId) != null) {
                int current_chest = ConfigHandler.loggingItem.get(loggingItemId);
                if (ConfigHandler.itemsPickup.get(loggingItemId) == null && ConfigHandler.itemsDrop.get(loggingItemId) == null && ConfigHandler.itemsThrown.get(loggingItemId) == null && ConfigHandler.itemsShot.get(loggingItemId) == null && ConfigHandler.itemsBreak.get(loggingItemId) == null && ConfigHandler.itemsDestroy.get(loggingItemId) == null && ConfigHandler.itemsCreate.get(loggingItemId) == null && ConfigHandler.itemsSell.get(loggingItemId) == null && ConfigHandler.itemsBuy.get(loggingItemId) == null) {
                    return;
                }
                if (current_chest == forceData) {
                    int currentTime = (int) (System.currentTimeMillis() / 1000L);
                    if (currentTime > time) {
                        ItemLogger.PreparedTransaction prepared = null;
                        if (ConfigHandler.databaseType.isColumnar()) {
                            prepared = ItemLogger.prepare(location, offset, user);
                            Consumer.consumerObjects.get(processId).put(id, prepared);
                        }
                        else {
                            ItemLogger.log(preparedStmt, batchCount, location, offset, user);
                        }
                        clearItemTransaction(loggingItemId);
                        if (prepared != null) {
                            prepared.log(preparedStmt, batchCount, user);
                        }
                    }
                    else {
                        Queue.queueItemTransaction(user, location, time, offset, forceData);
                    }
                }
            }
        }
    }

    static String getLoggingId(String user, Location location) {
        return user.toLowerCase(Locale.ROOT) + "." + location.getBlockX() + "." + location.getBlockY() + "." + location.getBlockZ();
    }

    static void discard(int forceData, String user, Location location) {
        String loggingId = getLoggingId(user, location);
        synchronized (Queue.class) {
            Integer generation = ConfigHandler.loggingItem.get(loggingId);
            if (generation == null || generation != forceData) {
                return;
            }
            Integer retainedGeneration = null;
            for (Map.Entry<Integer, ArrayList<Object[]>> queue : Consumer.consumer.entrySet()) {
                for (Object[] data : queue.getValue()) {
                    Integer candidate = Process.inventoryTransactionGeneration(queue.getKey(), data, Process.ITEM_TRANSACTION, loggingId);
                    if (candidate != null && (retainedGeneration == null || candidate > retainedGeneration)) {
                        retainedGeneration = candidate;
                    }
                }
            }
            if (retainedGeneration == null) {
                clearItemTransaction(loggingId);
            }
            else {
                ConfigHandler.loggingItem.put(loggingId, retainedGeneration);
            }
        }
    }

    private static void clearItemTransaction(String loggingId) {
        ConfigHandler.itemsPickup.remove(loggingId);
        ConfigHandler.itemsDrop.remove(loggingId);
        ConfigHandler.itemsThrown.remove(loggingId);
        ConfigHandler.itemsShot.remove(loggingId);
        ConfigHandler.itemsBreak.remove(loggingId);
        ConfigHandler.itemsDestroy.remove(loggingId);
        ConfigHandler.itemsCreate.remove(loggingId);
        ConfigHandler.itemsSell.remove(loggingId);
        ConfigHandler.itemsBuy.remove(loggingId);
        ConfigHandler.loggingItem.remove(loggingId);
    }
}
