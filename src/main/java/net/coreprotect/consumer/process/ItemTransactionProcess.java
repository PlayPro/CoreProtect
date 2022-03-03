package net.coreprotect.consumer.process;

import java.sql.PreparedStatement;
import java.util.Locale;

import org.bukkit.Location;

import net.coreprotect.config.ConfigHandler;
import net.coreprotect.consumer.Queue;
import net.coreprotect.database.logger.ItemLogger;

class ItemTransactionProcess extends Queue {

    static void process(PreparedStatement preparedStmt, int batchCount, int processId, int id, int forceData, int time, int offset, String user, Object object) {
        if (object instanceof Location) {
            Location location = (Location) object;
            String loggingItemId = user.toLowerCase(Locale.ROOT) + "." + location.getBlockX() + "." + location.getBlockY() + "." + location.getBlockZ();

            if (ConfigHandler.loggingItem.get(loggingItemId) != null) {
                int current_chest = ConfigHandler.loggingItem.get(loggingItemId);
                if (ConfigHandler.itemsPickup.get(loggingItemId) == null && ConfigHandler.itemsDrop.get(loggingItemId) == null && ConfigHandler.itemsThrown.get(loggingItemId) == null && ConfigHandler.itemsShot.get(loggingItemId) == null && ConfigHandler.itemsBreak.get(loggingItemId) == null && ConfigHandler.itemsDestroy.get(loggingItemId) == null && ConfigHandler.itemsCreate.get(loggingItemId) == null && ConfigHandler.itemsSell.get(loggingItemId) == null && ConfigHandler.itemsBuy.get(loggingItemId) == null) {
                    return;
                }
                if (current_chest == forceData) {
                    int currentTime = (int) (System.currentTimeMillis() / 1000L);
                    if (currentTime > time) {
                        ItemLogger.log(preparedStmt, batchCount, location, offset, user);
                        ConfigHandler.itemsPickup.remove(loggingItemId);
                        ConfigHandler.itemsDrop.remove(loggingItemId);
                        ConfigHandler.itemsThrown.remove(loggingItemId);
                        ConfigHandler.itemsShot.remove(loggingItemId);
                        ConfigHandler.itemsBreak.remove(loggingItemId);
                        ConfigHandler.itemsDestroy.remove(loggingItemId);
                        ConfigHandler.itemsCreate.remove(loggingItemId);
                        ConfigHandler.itemsSell.remove(loggingItemId);
                        ConfigHandler.itemsBuy.remove(loggingItemId);
                        ConfigHandler.loggingItem.remove(loggingItemId);
                    }
                    else {
                        Queue.queueItemTransaction(user, location, time, offset, forceData);
                    }
                }
            }
        }
    }
}
