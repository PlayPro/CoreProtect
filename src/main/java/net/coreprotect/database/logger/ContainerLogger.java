package net.coreprotect.database.logger;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.inventory.ItemStack;

import net.coreprotect.bukkit.BukkitAdapter;
import net.coreprotect.config.Config;
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.consumer.Queue;
import net.coreprotect.database.Database;
import net.coreprotect.database.ConsumerWriteBatch;
import net.coreprotect.database.statement.ContainerStatement;
import net.coreprotect.database.statement.UserStatement;
import net.coreprotect.event.CoreProtectPreLogEvent;
import net.coreprotect.model.entity.EntityContainerTransaction;
import net.coreprotect.model.entity.EntitySpawnIdentity;
import net.coreprotect.model.item.ItemTransactionActions;
import net.coreprotect.thread.CacheHandler;
import net.coreprotect.utility.BlockUtils;
import net.coreprotect.utility.HopperTransactionUtils;
import net.coreprotect.utility.ItemUtils;
import net.coreprotect.utility.MaterialUtils;
import net.coreprotect.utility.WorldUtils;
import net.coreprotect.utility.serialize.ItemMetaHandler;

public class ContainerLogger {

    private static final int CONTAINER_DUPLICATE_THRESHOLD_DROPPER = 512;
    private static final int CONTAINER_DUPLICATE_THRESHOLD_DISPENSER = 256;
    private static final int CONTAINER_DUPLICATE_WINDOW_SECONDS = 1200;

    private ContainerLogger() {
        throw new IllegalStateException("Database class");
    }

    public static void log(ConsumerWriteBatch preparedStmtContainer, ConsumerWriteBatch preparedStmtItems, int batchCount, String player, Material type, Object container, Location location) {
        log(preparedStmtContainer, preparedStmtItems, batchCount, player, type, container, location, null);
    }

    public static void log(ConsumerWriteBatch preparedStmtContainer, ConsumerWriteBatch preparedStmtItems, int batchCount, String player, Material type, Object container, Location location, java.util.function.Consumer<PreparedTransaction> capture) {
        try {
            ItemStack[] contents = null;
            String faceData = null;

            if (BukkitAdapter.ADAPTER.isItemFrame(type)) {
                contents = (ItemStack[]) ((Object[]) container)[1];
                faceData = ((BlockFace) ((Object[]) container)[2]).name();
            }
            else if (type == Material.JUKEBOX || type == Material.ARMOR_STAND) {
                contents = (ItemStack[]) ((Object[]) container)[1];
            }
            else if (container instanceof ItemStack[]) {
                contents = (ItemStack[]) container;
            }

            if (contents == null) {
                return;
            }

            String loggingContainerId = HopperTransactionUtils.getLoggingId(player, type, location);
            String transactingChestId = HopperTransactionUtils.getTransactionId(player, type, location);
            List<ItemStack[]> oldList = ConfigHandler.oldContainer.get(loggingContainerId);
            if (oldList == null || oldList.isEmpty()) {
                return;
            }
            ItemStack[] oi1 = oldList.get(0);
            ItemStack[] oldInventory = ItemUtils.getContainerState(oi1);
            ItemStack[] newInventory = ItemUtils.getContainerState(contents);
            if (oldInventory == null || newInventory == null) {
                return;
            }
            boolean duplicateSuppression = Config.getConfig(location.getWorld()).DUPLICATE_SUPPRESSION;

            // Check if this is a dispenser with no actual changes
            if (duplicateSuppression && "#dispenser".equals(player) && Queue.getForceContainerSize(loggingContainerId) == 0 && ItemUtils.compareContainers(oldInventory, newInventory)) {
                recordDispenserUnchanged(location);
                if (capture != null) {
                    capture.accept(new PreparedTransaction(type, faceData, location, new ItemStack[0], new ItemStack[0]));
                }
                oldList.remove(0);
                ConfigHandler.oldContainer.put(loggingContainerId, oldList);
                HopperTransactionUtils.consumeSnapshot(transactingChestId, loggingContainerId);
                return;
            }

            if (duplicateSuppression && "#dispenser".equals(player)) {
                String locationKey = HopperTransactionUtils.getTransactionId(location);
                ConfigHandler.dispenserPending.remove(locationKey);
                ConfigHandler.dispenserNoChange.remove(locationKey);
            }

            ItemStack[] forceState = Queue.peekForceContainer(loggingContainerId);
            if (forceState != null) {
                newInventory = ItemUtils.getContainerState(forceState);
            }
            else if (type != Material.ENDER_CHEST) {
                long snapshotMark = HopperTransactionUtils.peekSnapshotMark(transactingChestId, loggingContainerId);
                newInventory = HopperTransactionUtils.applyPendingChanges(newInventory, transactingChestId, snapshotMark);
            }

            if (duplicateSuppression && shouldSuppressContainerDuplicate(player, location, oldInventory, newInventory)) {
                if (forceState != null) {
                    Queue.pollForceContainer(loggingContainerId);
                }
                if (capture != null) {
                    capture.accept(new PreparedTransaction(type, faceData, location, new ItemStack[0], new ItemStack[0]));
                }
                oldList.remove(0);
                ConfigHandler.oldContainer.put(loggingContainerId, oldList);
                if (type != Material.ENDER_CHEST) {
                    HopperTransactionUtils.consumeSnapshot(transactingChestId, loggingContainerId);
                }
                if ("#hopper".equals(player)) {
                    String hopperPush = HopperTransactionUtils.getHopperPushId(location);
                    ConfigHandler.hopperSuccess.remove(hopperPush);
                    ConfigHandler.hopperAbort.remove(hopperPush);
                }
                return;
            }

            subtractSharedItems(oldInventory, newInventory);
            ItemUtils.mergeItems(type, oldInventory);
            ItemUtils.mergeItems(type, newInventory);

            PreparedTransaction prepared = capture == null ? null : new PreparedTransaction(type, faceData, location, oldInventory, newInventory);
            if (prepared != null) {
                capture.accept(prepared);
            }
            else {
                logContents(preparedStmtContainer, preparedStmtItems, batchCount, player, type, faceData, location, oldInventory, newInventory, null, true);
            }

            if (forceState != null) {
                Queue.pollForceContainer(loggingContainerId);
            }
            oldList.remove(0);
            ConfigHandler.oldContainer.put(loggingContainerId, oldList);
            if (type != Material.ENDER_CHEST) {
                HopperTransactionUtils.consumeSnapshot(transactingChestId, loggingContainerId);
            }
            if (prepared != null) {
                prepared.log(preparedStmtContainer, preparedStmtItems, batchCount, player, true);
            }
        }
        catch (Exception e) {
            Database.handleWriteFailure(e);
        }
    }

    public static void recordDispenserUnchanged(Location location) {
        String key = HopperTransactionUtils.getTransactionId(location);
        Object[] pendingEvent = ConfigHandler.dispenserPending.remove(key);
        if (pendingEvent != null) {
            ConfigHandler.dispenserNoChange.computeIfAbsent(key, unused -> new ConcurrentHashMap<>())
                    .put((String) pendingEvent[0], System.currentTimeMillis());
        }
    }

    private static void logContents(ConsumerWriteBatch containerBatch, ConsumerWriteBatch itemBatch, int batchCount, String user, Material type, String faceData, Location location, ItemStack[] removed, ItemStack[] added, Integer preparedTime, boolean clearHopper) {
        if (type != Material.ENDER_CHEST) {
            logTransaction(containerBatch, batchCount, user, type, faceData, removed, ItemTransactionActions.REMOVE, location, preparedTime, clearHopper);
            logTransaction(containerBatch, batchCount, user, type, faceData, added, ItemTransactionActions.ADD, location, preparedTime, clearHopper);
        }
        else {
            ItemLogger.logTransaction(itemBatch, batchCount, 0, user, location, removed, ItemTransactionActions.REMOVE_ENDER, preparedTime);
            ItemLogger.logTransaction(itemBatch, batchCount, 0, user, location, added, ItemTransactionActions.ADD_ENDER, preparedTime);
        }
    }

    public static PreparedTransaction prepareSpecifiedTransaction(Location location, Material type, BlockFace face, ItemStack[] before, ItemStack[] after) {
        ItemStack[] removed = ItemUtils.getContainerState(before);
        ItemStack[] added = ItemUtils.getContainerState(after);
        subtractSharedItems(removed, added);
        return new PreparedTransaction(type, face == null ? null : face.name(), location, removed, added);
    }

    public static final class PreparedTransaction {
        private final Material type;
        private final String faceData;
        private final Location location;
        private final int time;
        private final ItemStack[] removed;
        private final ItemStack[] added;

        private PreparedTransaction(Material type, String faceData, Location location, ItemStack[] removed, ItemStack[] added) {
            this.type = type;
            this.faceData = faceData;
            this.location = location.clone();
            this.time = (int) (System.currentTimeMillis() / 1000L);
            this.removed = ItemUtils.getContainerState(removed);
            this.added = ItemUtils.getContainerState(added);
        }

        public void log(ConsumerWriteBatch containerBatch, ConsumerWriteBatch itemBatch, int batchCount, String user) {
            log(containerBatch, itemBatch, batchCount, user, false);
        }

        private void log(ConsumerWriteBatch containerBatch, ConsumerWriteBatch itemBatch, int batchCount, String user, boolean clearHopper) {
            logContents(containerBatch, itemBatch, batchCount, user, type, faceData, location.clone(), ItemUtils.getContainerState(removed), ItemUtils.getContainerState(added), time, clearHopper);
        }
    }

    public static void logEntity(ConsumerWriteBatch preparedStmtContainer, int batchCount, String player, EntitySpawnIdentity identity, EntityContainerTransaction transaction) throws Exception {
        if (identity == null || transaction == null || ConfigHandler.isBlacklisted(player)) {
            return;
        }

        ItemStack[] oldInventory = transaction.getOldContents();
        ItemStack[] newInventory = transaction.getNewContents();
        Location currentLocation = transaction.getCurrentLocation();
        if (oldInventory == null || newInventory == null || currentLocation == null || currentLocation.getWorld() == null || ItemUtils.compareContainers(oldInventory, newInventory)) {
            return;
        }

        subtractSharedItems(oldInventory, newInventory);
        ItemUtils.mergeItems(Material.CHEST, oldInventory);
        ItemUtils.mergeItems(Material.CHEST, newInventory);
        logEntityTransaction(preparedStmtContainer, batchCount, player, identity, currentLocation, oldInventory, ItemTransactionActions.REMOVE);
        logEntityTransaction(preparedStmtContainer, batchCount, player, identity, currentLocation, newInventory, ItemTransactionActions.ADD);
    }

    protected static void logTransaction(ConsumerWriteBatch preparedStmt, int batchCount, String user, Material type, String faceData, ItemStack[] items, int action, Location location) {
        logTransaction(preparedStmt, batchCount, user, type, faceData, items, action, location, null, true);
    }

    private static void logTransaction(ConsumerWriteBatch preparedStmt, int batchCount, String user, Material type, String faceData, ItemStack[] items, int action, Location location, Integer preparedTime, boolean clearHopper) {
        try {
            if (ConfigHandler.isBlacklisted(user)) {
                return;
            }
            boolean success = false;
            for (int slot = 0; slot < items.length; slot++) {
                ItemStack item = items[slot];
                if (item == null || item.getAmount() <= 0 || BlockUtils.isAir(item.getType()) || ConfigHandler.isFilterBlacklisted(user, item.getType().getKey().toString())) {
                    continue;
                }

                CoreProtectPreLogEvent event = LoggerPreLog.fire(user, location, CoreProtectPreLogEvent.Action.CONTAINER_TRANSACTION, action, item.getType(), null, null);
                if (event == null || ConfigHandler.isFilterBlacklisted(event.getUser(), item.getType().getKey().toString())) {
                    continue;
                }

                List<List<Map<String, Object>>> metadata = ItemMetaHandler.serialize(item, type, faceData, slot);
                if (metadata.isEmpty()) {
                    metadata = null;
                }
                int userId = UserStatement.getId(preparedStmt, event.getUser(), true);
                Location eventLocation = event.getLocation();
                int wid = WorldUtils.getWorldId(eventLocation.getWorld().getName());
                int time = preparedTime == null ? (int) (System.currentTimeMillis() / 1000L) : preparedTime;
                int x = eventLocation.getBlockX();
                int y = eventLocation.getBlockY();
                int z = eventLocation.getBlockZ();
                int typeId = MaterialUtils.getBlockId(item.getType().name(), true);
                ContainerStatement.insert(preparedStmt, batchCount, time, userId, wid, x, y, z, typeId, 0, item.getAmount(), metadata, action, 0);
                success = true;
            }

            if (clearHopper && success && user.equals("#hopper")) {
                String hopperPush = HopperTransactionUtils.getHopperPushId(location);
                ConfigHandler.hopperSuccess.remove(hopperPush);
                ConfigHandler.hopperAbort.remove(hopperPush);
            }
        }
        catch (Exception e) {
            Database.handleWriteFailure(e);
        }
    }

    private static void logEntityTransaction(ConsumerWriteBatch preparedStmt, int batchCount, String user, EntitySpawnIdentity identity, Location currentLocation, ItemStack[] items, int action) throws Exception {
        for (int slot = 0; slot < items.length; slot++) {
            ItemStack item = items[slot];
            if (item == null || item.getAmount() <= 0 || BlockUtils.isAir(item.getType()) || ConfigHandler.isFilterBlacklisted(user, item.getType().getKey().toString())) {
                continue;
            }

            CoreProtectPreLogEvent event = LoggerPreLog.fire(user, currentLocation, CoreProtectPreLogEvent.Action.CONTAINER_TRANSACTION, action, item.getType(), null, null);
            if (event == null || ConfigHandler.isFilterBlacklisted(event.getUser(), item.getType().getKey().toString())) {
                continue;
            }

            List<List<Map<String, Object>>> metadata = ItemMetaHandler.serialize(item, Material.CHEST, null, slot);
            if (metadata.isEmpty()) {
                metadata = null;
            }
            int wid = identity.getOriginalWorldId();
            int x = identity.getOriginalX();
            int y = identity.getOriginalY();
            int z = identity.getOriginalZ();
            Location loggedLocation = event.getLocation();
            if (!LoggerPreLog.samePosition(currentLocation, loggedLocation)) {
                wid = WorldUtils.getWorldId(loggedLocation.getWorld().getName());
                x = loggedLocation.getBlockX();
                y = loggedLocation.getBlockY();
                z = loggedLocation.getBlockZ();
            }

            int userId = UserStatement.getId(preparedStmt, event.getUser(), true);
            int time = (int) (System.currentTimeMillis() / 1000L);
            int typeId = MaterialUtils.getBlockId(item.getType().name(), true);
            ContainerStatement.insertEntity(preparedStmt, batchCount, time, userId, identity.getRowId(), wid, x, y, z, typeId, 0, item.getAmount(), metadata, action, 0);
        }
    }

    private static void subtractSharedItems(ItemStack[] oldInventory, ItemStack[] newInventory) {
        for (ItemStack oldItem : oldInventory) {
            for (ItemStack newItem : newInventory) {
                if (oldItem == null || newItem == null || !oldItem.isSimilar(newItem) || BlockUtils.isAir(oldItem.getType())) {
                    continue;
                }

                int sharedAmount = Math.min(oldItem.getAmount(), newItem.getAmount());
                oldItem.setAmount(oldItem.getAmount() - sharedAmount);
                newItem.setAmount(newItem.getAmount() - sharedAmount);
            }
        }
    }

    private static boolean shouldSuppressContainerDuplicate(String user, Location location, ItemStack[] oldInventory, ItemStack[] newInventory) {
        if (!"#dropper".equals(user) && !"#dispenser".equals(user)) {
            return false;
        }

        long oldSignature = getContainerStateSignature(oldInventory);
        long newSignature = getContainerStateSignature(newInventory);
        if ("#dispenser".equals(user) && Long.compareUnsigned(oldSignature, newSignature) > 0) {
            long swap = oldSignature;
            oldSignature = newSignature;
            newSignature = swap;
        }

        int threshold = getContainerDuplicateThreshold(user);
        String signature = HopperTransactionUtils.getTransactionId(location) + "." + user + "."
                + Long.toUnsignedString(oldSignature, 16) + ">" + Long.toUnsignedString(newSignature, 16);
        return CacheHandler.shouldSuppressRepeat(CacheHandler.containerDuplicateCache, signature, threshold, CONTAINER_DUPLICATE_WINDOW_SECONDS);
    }

    private static int getContainerDuplicateThreshold(String user) {
        if ("#dispenser".equals(user)) {
            return CONTAINER_DUPLICATE_THRESHOLD_DISPENSER;
        }
        return CONTAINER_DUPLICATE_THRESHOLD_DROPPER;
    }

    static long getContainerStateSignature(ItemStack[] inventory) {
        if (inventory == null) {
            return 0L;
        }

        long signature = 0L;
        for (ItemStack item : inventory) {
            if (item == null || item.getAmount() <= 0 || BlockUtils.isAir(item.getType())) {
                continue;
            }

            int metadataHash = item.hasItemMeta() ? Objects.hashCode(item.getItemMeta()) : 0;
            long identity = (long) item.getType().ordinal() << Integer.SIZE | Integer.toUnsignedLong(metadataHash);
            signature += mixItemIdentity(identity) * item.getAmount();
        }
        return signature;
    }

    private static long mixItemIdentity(long value) {
        value = (value ^ (value >>> 33)) * 0xff51afd7ed558ccdL;
        value = (value ^ (value >>> 33)) * 0xc4ceb9fe1a85ec53L;
        return value ^ (value >>> 33);
    }

}
