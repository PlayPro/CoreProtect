package net.coreprotect.consumer.process;

import java.util.ArrayList;
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
            ItemLogger.PreparedTransaction prepared;
            synchronized (Queue.class) {
                Integer generation = ConfigHandler.loggingItem.get(loggingItemId);
                if (generation == null || generation != forceData) {
                    return;
                }
                int currentTime = (int) (System.currentTimeMillis() / 1000L);
                if (currentTime <= time) {
                    Queue.queueItemTransaction(user, location, time, offset, forceData);
                    return;
                }
                prepared = ItemLogger.prepare(location, offset, user);
                Consumer.consumerObjects.get(processId).put(id, prepared);
                ConfigHandler.loggingItem.remove(loggingItemId, forceData);
            }
            prepared.log(preparedStmt, batchCount, user);
        }
    }

    static String getLoggingId(String user, Location location) {
        return ItemLogger.getLoggingId(user, location);
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
