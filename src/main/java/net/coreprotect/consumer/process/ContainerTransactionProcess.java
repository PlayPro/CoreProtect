package net.coreprotect.consumer.process;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import net.coreprotect.config.ConfigHandler;
import net.coreprotect.consumer.Consumer;
import net.coreprotect.consumer.QueuePayload;
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
        if (object instanceof ContainerLogger.PreparedTransaction) {
            ((ContainerLogger.PreparedTransaction) object).log(preparedStmtContainer, preparedStmtItems, batchCount, user);
            return;
        }
        if (!(object instanceof Location)) {
            return;
        }

        Location location = (Location) object;
        Object inventory = QueuePayload.detail(Consumer.consumerObjects.get(processId).get(id));
        if (inventory == null) {
            return;
        }

        String transactingChestId = HopperTransactionUtils.getTransactionId(user, type, location);
        String viewerGroupId = HopperTransactionUtils.getViewerGroupId(user, type, location);
        String loggingChestId = HopperTransactionUtils.getLoggingId(user, type, location);
        HopperTransactionUtils.synchronizeTransaction(transactingChestId,
                () -> processTransaction(preparedStmtContainer, preparedStmtItems, batchCount, processId, id, type, forceData, user, inventory, location, transactingChestId, viewerGroupId, loggingChestId));
    }

    private static void processTransaction(ConsumerWriteBatch preparedStmtContainer, ConsumerWriteBatch preparedStmtItems, int batchCount, int processId, int id, Material type, int forceData, String user, Object inventory,
            Location location, String transactingChestId, String loggingChestIdSuffix, String loggingChestId) {
        if (ConfigHandler.loggingChest.get(loggingChestId) == null) {
            return;
        }

        int current_chest = ConfigHandler.loggingChest.get(loggingChestId);
        if (ConfigHandler.oldContainer.get(loggingChestId) == null) {
            clearContainerTransaction(transactingChestId, loggingChestIdSuffix, loggingChestId);
            return;
        }
        int force_size = Queue.getForceContainerSize(loggingChestId);
        if (current_chest == forceData || force_size > 0) { // This prevents client side chest sorting mods from messing things up.
            try {
                if (ConfigHandler.databaseType.isColumnar()) {
                    ContainerLogger.log(preparedStmtContainer, preparedStmtItems, batchCount, user, type, inventory, location,
                            prepared -> Consumer.consumerObjects.get(processId).put(id, prepared));
                }
                else {
                    ContainerLogger.log(preparedStmtContainer, preparedStmtItems, batchCount, user, type, inventory, location);
                }
            }
            finally {
                List<ItemStack[]> old = ConfigHandler.oldContainer.get(loggingChestId);
                if (old == null || old.isEmpty()) {
                    clearContainerTransaction(transactingChestId, loggingChestIdSuffix, loggingChestId);
                }
            }
        }
        else if (loggingChestId.startsWith("#hopper")) {
            if (force_size == 0 && ConfigHandler.oldContainer.getOrDefault(loggingChestId, Collections.synchronizedList(new ArrayList<>())).size() == 1
                    && HopperTransactionUtils.pendingDeltaCount(transactingChestId) == 0) {
                int loopCount = ConfigHandler.loggingChest.getOrDefault(loggingChestId, 0);
                int maxInventorySize = 99 * (inventory instanceof ItemStack[] ? ((ItemStack[]) inventory).length : 54);

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

    private static void clearContainerTransaction(String transactionId, String locationSuffix, String loggingId) {
        ConfigHandler.oldContainer.remove(loggingId);
        ConfigHandler.removeOldContainerViewer(locationSuffix, loggingId);
        ConfigHandler.loggingChest.remove(loggingId);
        Queue.removeForceContainer(loggingId);
        HopperTransactionUtils.removeOwner(transactionId, loggingId);
    }

    static void discard(int processId, int failedIndex, Material type, int forceData, String user, Location location) {
        if (location.getWorld() == null) {
            return;
        }
        String transactionId = HopperTransactionUtils.getTransactionId(user, type, location);
        String locationSuffix = HopperTransactionUtils.getViewerGroupId(user, type, location);
        String loggingId = HopperTransactionUtils.getLoggingId(user, type, location);
        HopperTransactionUtils.synchronizeTransaction(transactionId, () -> {
            List<ItemStack[]> old = ConfigHandler.oldContainer.get(loggingId);
            if (old == null || old.isEmpty()) {
                clearContainerTransaction(transactionId, locationSuffix, loggingId);
                return;
            }
            Integer generation = ConfigHandler.loggingChest.get(loggingId);
            int forceSize = Queue.getForceContainerSize(loggingId);
            int snapshotIndex = 0;
            List<Object[]> queued = Consumer.consumer.get(processId);
            for (int index = 0; index < failedIndex; index++) {
                if (Process.inventoryTransactionGeneration(processId, queued.get(index), Process.CONTAINER_TRANSACTION, loggingId) != null) {
                    snapshotIndex++;
                }
            }
            if (snapshotIndex >= forceSize) {
                if (generation == null || generation != forceData) {
                    return;
                }
                snapshotIndex = forceSize;
            }
            if (snapshotIndex < old.size()) {
                old.remove(snapshotIndex);
                if (snapshotIndex < forceSize) {
                    Queue.pollForceContainer(loggingId, snapshotIndex);
                }
                if (type != Material.ENDER_CHEST) {
                    HopperTransactionUtils.consumeSnapshot(transactionId, loggingId, snapshotIndex);
                }
            }
            if (old.isEmpty()) {
                clearContainerTransaction(transactionId, locationSuffix, loggingId);
            }
        });
    }
}
