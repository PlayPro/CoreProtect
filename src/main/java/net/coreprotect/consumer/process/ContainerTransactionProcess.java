package net.coreprotect.consumer.process;

import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
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
                String transactingChestId = location.getWorld().getUID().toString() + "." + location.getBlockX() + "." + location.getBlockY() + "." + location.getBlockZ();
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
                    else if (loggingChestId.startsWith("#hopper")) {
                        List<Object> transactingChest = ConfigHandler.transactingChest.get(transactingChestId);
                        if (force_size == 0 && ConfigHandler.oldContainer.getOrDefault(loggingChestId, Collections.synchronizedList(new ArrayList<>())).size() == 1 && transactingChest != null && transactingChest.isEmpty()) {
                            int loopCount = ConfigHandler.loggingChest.getOrDefault(loggingChestId, 0);
                            int maxInventorySize = (99 * 54);
                            try {
                                Inventory checkInventory = (Inventory) inventory;
                                maxInventorySize = checkInventory.getSize() * checkInventory.getMaxStackSize();
                            }
                            catch (Exception e) {
                                // use default of 5,346
                            }

                            if (loopCount > maxInventorySize) {
                                ItemStack[] destinationContents = null;
                                ItemStack movedItem = null;

                                String hopperPush = "#hopper-push." + location.getBlockX() + "." + location.getBlockY() + "." + location.getBlockZ();
                                Object[] hopperPushData = ConfigHandler.hopperSuccess.remove(hopperPush);
                                if (hopperPushData != null) {
                                    destinationContents = (ItemStack[]) hopperPushData[0];
                                    movedItem = (ItemStack) hopperPushData[1];
                                }

                                if (destinationContents != null) {
                                    Set<ItemStack> movedItems = new HashSet<>();
                                    Object[] lastAbort = ConfigHandler.hopperAbort.get(hopperPush);
                                    if (lastAbort != null && Arrays.equals(destinationContents, (ItemStack[]) lastAbort[1])) {
                                        ((Set<?>) lastAbort[0]).forEach(itemStack -> movedItems.add((ItemStack) itemStack));
                                    }
                                    movedItems.add(movedItem);
                                    ConfigHandler.hopperAbort.put(hopperPush, new Object[] { movedItems, destinationContents });
                                }
                            }
                        }
                    }
                }
                inventories.remove(id);
            }
        }
    }
}
