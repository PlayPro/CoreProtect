package net.coreprotect.consumer.process;

import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import net.coreprotect.config.ConfigHandler;
import net.coreprotect.consumer.Consumer;
import net.coreprotect.consumer.Queue;
import net.coreprotect.database.logger.ContainerLogger;
import net.coreprotect.utility.HopperTransactionUtils;

class ContainerTransactionProcess {

    static void process(PreparedStatement preparedStmtContainer, PreparedStatement preparedStmtItems, int batchCount, int processId, int id, Material type, int forceData, String user, Object object) {
        if (object instanceof Location) {
            Location location = (Location) object;
            Map<Integer, Object> inventories = Consumer.consumerInventories.get(processId);
            Object inventory = inventories.remove(id);
            if (inventory != null) {
                String transactingChestId = HopperTransactionUtils.getTransactionId(location);
                String loggingChestIdSuffix = HopperTransactionUtils.getLoggingIdSuffix(location);
                String loggingChestId = HopperTransactionUtils.getLoggingId(user, loggingChestIdSuffix);
                if (ConfigHandler.loggingChest.get(loggingChestId) != null) {
                    int current_chest = ConfigHandler.loggingChest.get(loggingChestId);
                    if (ConfigHandler.oldContainer.get(loggingChestId) == null) {
                        clearContainerTransaction(transactingChestId, loggingChestIdSuffix, loggingChestId);
                        return;
                    }
                    int force_size = Queue.getForceContainerSize(loggingChestId);
                    if (current_chest == forceData || force_size > 0) { // This prevents client side chest sorting mods from messing things up.
                        ContainerLogger.log(preparedStmtContainer, preparedStmtItems, batchCount, user, type, inventory, location);
                        List<ItemStack[]> old = ConfigHandler.oldContainer.get(loggingChestId);
                        if (old == null || old.isEmpty()) {
                            clearContainerTransaction(transactingChestId, loggingChestIdSuffix, loggingChestId);
                        }
                    }
                    else if (loggingChestId.startsWith("#hopper")) {
                        if (force_size == 0 && ConfigHandler.oldContainer.getOrDefault(loggingChestId, Collections.synchronizedList(new ArrayList<>())).size() == 1 && HopperTransactionUtils.pendingDeltaCount(transactingChestId) == 0) {
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

                                String hopperPush = HopperTransactionUtils.getHopperPushId(location);
                                Object[] hopperPushData = ConfigHandler.hopperSuccess.remove(hopperPush);
                                if (hopperPushData != null) {
                                    destinationContents = (ItemStack[]) hopperPushData[0];
                                    movedItem = (ItemStack) hopperPushData[1];
                                }

                                if (destinationContents != null) {
                                    Object[] lastAbort = ConfigHandler.hopperAbort.get(hopperPush);
                                    ConfigHandler.hopperAbort.put(hopperPush, HopperTransactionUtils.createAbortState(lastAbort, destinationContents, movedItem));
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private static void clearContainerTransaction(String transactionId, String locationSuffix, String loggingId) {
        ConfigHandler.oldContainer.remove(loggingId);
        ConfigHandler.removeOldContainerViewer(locationSuffix, loggingId);
        ConfigHandler.loggingChest.remove(loggingId);
        Queue.removeForceContainer(loggingId);
        HopperTransactionUtils.removeOwner(transactionId, loggingId);
    }
}
