package net.coreprotect.consumer.process;

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
import net.coreprotect.database.ConsumerWriteBatch;
import net.coreprotect.database.logger.ContainerLogger;
import net.coreprotect.model.entity.EntityContainerTransaction;
import net.coreprotect.model.entity.EntitySpawnIdentity;
import net.coreprotect.utility.ErrorReporter;
import net.coreprotect.utility.HopperTransactionUtils;

class ContainerTransactionProcess {

    static boolean processEntity(ConsumerWriteBatch preparedStmtContainer, int batchCount, String user, Object object, EntitySpawnIdentity identity) throws Exception {
        if (!(object instanceof EntityContainerTransaction) || identity == null) {
            return false;
        }

        if (ConfigHandler.databaseType.isColumnar()) {
            preparedStmtContainer.executeAtomically("entity_container_transaction", () -> ContainerLogger.logEntity(preparedStmtContainer, batchCount, user, identity, (EntityContainerTransaction) object));
        }
        else {
            try {
                ContainerLogger.logEntity(preparedStmtContainer, batchCount, user, identity, (EntityContainerTransaction) object);
            }
            catch (Exception e) {
                ErrorReporter.report(e);
            }
        }
        return true;
    }

    static void process(ConsumerWriteBatch preparedStmtContainer, ConsumerWriteBatch preparedStmtItems, int batchCount, int processId, int id, Material type, int forceData, String user, Object object) {
        if (object instanceof Location) {
            Location location = (Location) object;
            Map<Integer, Object> inventories = Consumer.consumerInventories.get(processId);
            Object inventory = inventories.get(id);
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
