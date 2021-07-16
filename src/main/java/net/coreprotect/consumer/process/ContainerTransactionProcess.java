package net.coreprotect.consumer.process;

import java.sql.PreparedStatement;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import net.coreprotect.config.ConfigHandler;
import net.coreprotect.consumer.Consumer;
import net.coreprotect.database.logger.ContainerLogger;

class ContainerTransactionProcess {

    static void process(PreparedStatement preparedStmtContainer, PreparedStatement preparedStmtItems, int batchCount, int processId, int id, Material type, int forceData, String user, Object object) {
        if (object instanceof Location) {
            Location location = (Location) object;
            Map<Integer, Object> inventories = Consumer.consumerInventories.get(processId);
            if (inventories.get(id) != null) {
                Object inventory = inventories.get(id);
                String transactingChestId = location.getWorld().getUID() + "." + location.getBlockX() + "." + location.getBlockY() + "." + location.getBlockZ();
                String loggingChestId = user.toLowerCase(Locale.ROOT) + "." + location.getBlockX() + "." + location.getBlockY() + "." + location.getBlockZ();
                if (ConfigHandler.loggingChest.get(loggingChestId) != null) {
                    int current_chest = ConfigHandler.loggingChest.get(loggingChestId);
                    if (ConfigHandler.oldContainer.get(loggingChestId) == null) {
                        return;
                    }
                    int force_size = 0;
                    if (ConfigHandler.forceContainer.get(loggingChestId) != null) {
                        force_size = ConfigHandler.forceContainer.get(loggingChestId).size();
                    }
                    if (current_chest == forceData || force_size > 0) { // This prevents client side chest sorting mods from messing things up.
                        ContainerLogger.log(preparedStmtContainer, preparedStmtItems, batchCount, user, type, inventory, location);
                        List<ItemStack[]> old = ConfigHandler.oldContainer.get(loggingChestId);
                        if (old.size() == 0) {
                            ConfigHandler.oldContainer.remove(loggingChestId);
                            ConfigHandler.loggingChest.remove(loggingChestId);
                            ConfigHandler.transactingChest.remove(transactingChestId);
                        }
                    }
                }
                inventories.remove(id);
            }
        }
    }
}
