package net.coreprotect.database.rollback;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Powerable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import net.coreprotect.CoreProtect;
import net.coreprotect.bukkit.BukkitAdapter;
import net.coreprotect.config.Config;
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.consumer.Queue;
import net.coreprotect.consumer.process.Process;
import net.coreprotect.database.rollback.RollbackUtil.ContainerReplayResult;
import net.coreprotect.listener.player.InventoryChangeListener;
import net.coreprotect.model.PendingBlockChange;
import net.coreprotect.model.action.LookupActions;
import net.coreprotect.model.rollback.RollbackUpdateTargets;
import net.coreprotect.model.item.ItemTransactionActions;
import net.coreprotect.model.item.InventorySources;
import net.coreprotect.paper.PaperAdapter;
import net.coreprotect.thread.Scheduler;
import net.coreprotect.utility.BlockUtils;
import net.coreprotect.utility.ItemUtils;
import net.coreprotect.utility.MaterialUtils;
import net.coreprotect.utility.Teleport;
import net.coreprotect.utility.ErrorReporter;

public class RollbackProcessor extends Queue {

    private static void normalizeRollbackBlockData(BlockData blockData) {
        if (blockData instanceof Powerable && blockData.getMaterial() == Material.NOTE_BLOCK) {
            ((Powerable) blockData).setPowered(false);
        }
    }

    private static boolean isEntityPlacementMaterial(Material material) {
        return BukkitAdapter.ADAPTER.isItemFrame(material) || material == Material.PAINTING
                || material == Material.ARMOR_STAND || material == Material.END_CRYSTAL;
    }

    /**
     * Process data for a specific chunk
     * 
     * @param finalChunkX
     *            The chunk X coordinate
     * @param finalChunkZ
     *            The chunk Z coordinate
     * @param chunkKey
     *            The chunk lookup key
     * @param blockList
     *            The list of block data to process
     * @param itemList
     *            The list of item data to process
     * @param rollbackType
     *            The rollback type (0=rollback, 1=restore)
     * @param preview
     *            Whether this is a preview (0=no, 1=yes-non-destructive, 2=yes-destructive)
     * @param finalUserString
     *            The username performing the rollback
     * @param finalUser
     *            The user performing the rollback
     * @param bukkitRollbackWorld
     *            The world to process
     * @param blockDataCache
     *            The rollback-scoped BlockData parse cache
     * @param inventoryContext
     *            If not null, player inventory changes are scheduled on each player's entity scheduler as mutations of this rollback context (Folia)
     * @param counters
     *            Counts world changes applied by this chunk
     * @return True if successful, false if there was an error
     */
    public static boolean processChunk(int finalChunkX, int finalChunkZ, long chunkKey, ArrayList<Object[]> blockList, ArrayList<Object[]> itemList, int rollbackType, int preview, String finalUserString, Player finalUser, World bukkitRollbackWorld, boolean inventoryRollback, RollbackBlockDataCache blockDataCache, EntitySpawnRollbackHandler.Context inventoryContext, RollbackCounters counters) {
        try {
            if (preview > 0 && finalUser == null) {
                return true;
            }

            boolean clearInventories = Config.getGlobal().ROLLBACK_ITEMS;
            ArrayList<Object[]> data = blockList != null ? blockList : new ArrayList<>();
            ArrayList<Object[]> itemData = itemList != null ? itemList : new ArrayList<>();
            Map<Block, PendingBlockChange> chunkChanges = new LinkedHashMap<>();
            loadChunk(bukkitRollbackWorld, finalChunkX, finalChunkZ, inventoryRollback);

            if (!data.isEmpty() && (Integer) data.get(0)[8] == LookupActions.SIGN) {
                if (preview == 0) {
                    RollbackSignHandler.processChunk(bukkitRollbackWorld, data, rollbackType, finalUserString, counters);
                }
                data.clear();
            }

            List<Object[]> appliedBlocks = new ArrayList<>();
            // Process blocks
            for (Object[] row : data) {
                int rowX = (Integer) row[3];
                int rowY = (Integer) row[4];
                int rowZ = (Integer) row[5];
                int rowTypeRaw = (Integer) row[6];
                int rowData = (Integer) row[7];
                int rowAction = (Integer) row[8];
                int rowRolledBack = MaterialUtils.rolledBack((Integer) row[9], false);
                int rowWorldId = (Integer) row[10];
                byte[] rowMeta = (byte[]) row[12];
                byte[] rowBlockData = (byte[]) row[13];
                String blockDataString = BlockUtils.byteDataToString(rowBlockData, rowTypeRaw);
                Material rowType = MaterialUtils.getType(rowTypeRaw);

                List<Object> meta = null;
                if (rowMeta != null) {
                    meta = RollbackUtil.deserializeMetadata(rowMeta);
                }

                BlockData blockData = blockDataCache.getParsedBlockData(blockDataString);
                BlockData rawBlockData = null;
                if (blockData != null) {
                    rawBlockData = blockData.clone();
                }
                if (rawBlockData == null) {
                    rawBlockData = blockDataCache.getDefaultBlockData(rowTypeRaw);
                }
                if (rowType == Material.NOTE_BLOCK) {
                    normalizeRollbackBlockData(blockData);
                    normalizeRollbackBlockData(rawBlockData);
                }

                String rowUser = ConfigHandler.playerIdCacheReversed.getOrDefault((Integer) row[2], "");
                int oldTypeRaw = rowTypeRaw;
                Material oldTypeMaterial = MaterialUtils.getType(oldTypeRaw);

                if (rowAction == 1 && rollbackType == 0) { // block placement
                    rowType = Material.AIR;
                    blockData = null;
                    rowTypeRaw = 0;
                }
                else if (rowAction == 0 && rollbackType == 1) { // block removal
                    rowType = Material.AIR;
                    blockData = null;
                    rowTypeRaw = 0;
                }
                else if (rowAction == 4 && rollbackType == 0) { // entity placement
                    rowType = null;
                    rowTypeRaw = 0;
                }
                else if (rowAction == 3 && rollbackType == 1) { // entity removal
                    rowType = null;
                    rowTypeRaw = 0;
                }
                if (preview > 0) {
                    if (rowAction == LookupActions.ENTITY_KILL) {
                        counters.addEntities(1);
                    }
                    else if (rowAction != LookupActions.ENTITY_SPAWN && !isEntityPlacementMaterial(oldTypeMaterial) && !isEntityPlacementMaterial(rowType)) {
                        Block block = bukkitRollbackWorld.getBlockAt(rowX, rowY, rowZ);
                        if (preview == 2) {
                            BlockUtils.prepareTypeAndData(chunkChanges, block, block.getType(), block.getBlockData(), true);
                        }
                        else {
                            BlockUtils.prepareTypeAndData(chunkChanges, block, rowType, blockData, true);
                        }
                        counters.addBlocks(1);
                    }
                }
                else if (rowAction == 3) { // entity kill
                    int entities = RollbackEntityHandler.processEntity(bukkitRollbackWorld, oldTypeRaw, rowTypeRaw, rowData, rowAction, rowRolledBack, rowX, rowY, rowZ, rowWorldId, rowUser);
                    counters.addEntities(entities);
                    if (entities > 0) {
                        appliedBlocks.add(row);
                    }
                }
                else {
                    Block block = bukkitRollbackWorld.getBlockAt(rowX, rowY, rowZ);

                    boolean changeBlock = true;
                    boolean countBlock = true;
                    Material changeType = block.getType();
                    BlockData changeBlockData = block.getBlockData();
                    PendingBlockChange pendingChange = chunkChanges.get(block);
                    BlockData pendingChangeData = pendingChange != null ? pendingChange.blockData() : null;
                    Material pendingChangeType = changeType;

                    if (pendingChangeData != null) {
                        pendingChangeType = pendingChangeData.getMaterial();
                    }
                    else {
                        pendingChangeData = changeBlockData;
                    }

                    if (rowRolledBack == 1 && rollbackType == 0) { // rollback
                        countBlock = false;
                    }

                    if (rowType == pendingChangeType && !isEntityPlacementMaterial(oldTypeMaterial)) {
                        // block is already changed!
                        BlockData checkData = rowType == Material.AIR ? blockData : rawBlockData;
                        if (checkData != null) {
                            if (checkData.getAsString().equals(pendingChangeData.getAsString()) || checkData instanceof org.bukkit.block.data.MultipleFacing || checkData instanceof org.bukkit.block.data.type.Stairs || checkData instanceof org.bukkit.block.data.type.RedstoneWire) {
                                if (rowType != Material.CHEST && rowType != Material.TRAPPED_CHEST && !BukkitAdapter.ADAPTER.isCopperChest(rowType)) { // always update double chests
                                    changeBlock = false;
                                }
                            }
                        }
                        else if (rowType == Material.AIR) {
                            changeBlock = false;
                        }

                        countBlock = false;
                    }
                    else if ((pendingChangeType != Material.AIR) && (pendingChangeType != Material.CAVE_AIR)) {
                        countBlock = true;
                    }

                    if ((pendingChangeType == Material.WATER) && (rowType != Material.AIR) && (rowType != Material.CAVE_AIR) && blockData != null) {
                        if (blockData instanceof org.bukkit.block.data.Waterlogged) {
                            if (Material.WATER.createBlockData().equals(pendingChangeData)) {
                                org.bukkit.block.data.Waterlogged waterlogged = (org.bukkit.block.data.Waterlogged) blockData;
                                waterlogged.setWaterlogged(true);
                            }
                        }
                    }

                    if (RollbackBlockHandler.processBlockChange(bukkitRollbackWorld, block, row, rollbackType, clearInventories, chunkChanges, countBlock, oldTypeMaterial, pendingChangeType, pendingChangeData, counters, rawBlockData, changeType, changeBlock, changeBlockData, meta != null ? new ArrayList<>(meta) : null, blockData, rowUser, rowType, rowX, rowY, rowZ, rowTypeRaw, rowData, rowAction, rowWorldId, blockDataString) && countBlock) {
                        counters.addBlocks(1);
                    }
                    if (oldTypeMaterial != null && rowType != null && rowRolledBack == rollbackType) {
                        appliedBlocks.add(row);
                    }
                }
            }
            data.clear();

            // Apply cached block changes
            RollbackBlockHandler.applyBlockChanges(chunkChanges, preview, finalUser instanceof Player ? (Player) finalUser : null);

            if (!appliedBlocks.isEmpty()) {
                Queue.queueRollbackUpdate(finalUserString, null, appliedBlocks, Process.ROLLBACK_UPDATE, rollbackType);
            }
            if (preview > 0) {
                return true;
            }

            if (inventoryRollback) {
                processInventoryRows(itemData, rollbackType, finalUserString, inventoryContext, counters);
            }
            else {
                counters.addItems(replayContainerRows(bukkitRollbackWorld, null, itemData, rollbackType, finalUserString,
                        () -> inventoryContext != null && inventoryContext.isCancelled()));
            }
            itemData.clear();

            // Teleport players out of danger if they're within this chunk
            if (preview == 0) {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (PaperAdapter.ADAPTER != null && !PaperAdapter.ADAPTER.isOwnedByCurrentRegion(player)) {
                        continue;
                    }
                    Location playerLocation = player.getLocation();
                    World playerWorld = playerLocation.getWorld();
                    if (playerWorld == null) {
                        continue;
                    }
                    int chunkX = playerLocation.getBlockX() >> 4;
                    int chunkZ = playerLocation.getBlockZ() >> 4;

                    if (bukkitRollbackWorld.getName().equals(playerWorld.getName()) && chunkX == finalChunkX && chunkZ == finalChunkZ) {
                        Scheduler.runTask(CoreProtect.getInstance(), () -> {
                            Location currentLocation = player.getLocation();
                            World currentWorld = currentLocation.getWorld();
                            if (currentWorld != null && bukkitRollbackWorld.getName().equals(currentWorld.getName())
                                    && (currentLocation.getBlockX() >> 4) == finalChunkX
                                    && (currentLocation.getBlockZ() >> 4) == finalChunkZ) {
                                Teleport.performSafeTeleport(player, currentLocation, false);
                            }
                        }, player);
                    }
                }
            }

            return true;
        }
        catch (Exception e) {
            ErrorReporter.report(e);
            return false;
        }
    }

    public static int replayContainerRows(World world, Block inspectedBlock, List<Object[]> rows, int rollbackType, String actor, BooleanSupplier cancelled) {
        Block block = inspectedBlock;
        RollbackUtil.ContainerItem previousItem = null;
        RollbackUtil.ContainerTarget target = null;
        List<Object[]> applied = new ArrayList<>();
        int modified = 0;
        try {
            for (Object[] row : rows) {
                if (cancelled.getAsBoolean()) {
                    break;
                }
                int rowAction = (Integer) row[8];
                int rowAmount = (Integer) row[11];
                Material material = MaterialUtils.getType((Integer) row[6]);
                if (material == null || rowAmount <= 0 || rowAction < 0 || rowAction > 1 || MaterialUtils.rolledBack((Integer) row[9], false) != rollbackType) {
                    continue;
                }
                if (inspectedBlock == null) {
                    int x = (Integer) row[3];
                    int y = (Integer) row[4];
                    int z = (Integer) row[5];
                    if (block == null || block.getX() != x || block.getY() != y || block.getZ() != z) {
                        block = world.getBlockAt(x, y, z);
                        previousItem = null;
                    }
                }
                RollbackUtil.ContainerItem item = RollbackUtil.decodeContainerItem(new ItemStack(material, rowAmount), (byte[]) row[12]);
                if (!item.sameTarget(previousItem)) {
                    target = RollbackUtil.resolveContainer(block, item);
                    if (target != null && target.container() instanceof Inventory) {
                        InventoryChangeListener.flushPendingContainer((Inventory) target.container(), block.getLocation());
                    }
                    previousItem = item;
                }
                if (target != null) {
                    int action = rowAction == rollbackType ? 1 : 0;
                    ContainerReplayResult result = RollbackUtil.replayContainerItems(target.type(), target.container(), item.slot(), item.stack(), action);
                    if (result.applied()) {
                        applied.add(row);
                        modified += result.modifiedAmount();
                    }
                }
            }
        }
        finally {
            if (!applied.isEmpty()) {
                Queue.queueRollbackUpdate(actor, null, applied, Process.CONTAINER_ROLLBACK_UPDATE, rollbackType);
            }
        }
        return modified;
    }

    private static void processInventoryRows(List<Object[]> rows, int rollbackType, String actor, EntitySpawnRollbackHandler.Context context, RollbackCounters counters) {
        Map<Player, List<InventoryRow>> inventoryRows = new LinkedHashMap<>();
        for (Object[] row : rows) {
            if (MaterialUtils.rolledBack((Integer) row[9], true) != rollbackType || (Integer) row[11] <= 0) {
                continue;
            }
            Material type = MaterialUtils.getType((Integer) row[6]);
            Material inventoryItem = type == null ? null : ItemUtils.inventoryItemFilter(type, (Integer) row[14] == InventorySources.BLOCK);
            String rowUser = ConfigHandler.playerIdCacheReversed.get((Integer) row[2]);
            if (inventoryItem == null || rowUser == null) {
                continue;
            }

            String uuid = ConfigHandler.uuidCache.get(rowUser.toLowerCase(Locale.ROOT));
            Player player = uuid == null || uuid.isEmpty() ? null : Bukkit.getServer().getPlayer(UUID.fromString(uuid));
            if (player != null) {
                inventoryRows.computeIfAbsent(player, key -> new ArrayList<>()).add(new InventoryRow(row, inventoryItem));
            }
        }

        for (Entry<Player, List<InventoryRow>> playerRows : inventoryRows.entrySet()) {
            Player player = playerRows.getKey();
            if (ConfigHandler.isFolia && context != null) {
                scheduleInventoryRows(context, actor, player, playerRows.getValue(), rollbackType);
            }
            else {
                counters.addItems(applyInventoryRows(actor, player, playerRows.getValue(), rollbackType, context));
            }
        }
    }

    private static int applyInventoryRows(String actor, Player player, List<InventoryRow> rows, int rollbackType, EntitySpawnRollbackHandler.Context context) {
        if (!player.isOnline()) {
            return 0;
        }

        List<Integer> sortSlots = new ArrayList<>();
        List<Object[]> applied = new ArrayList<>();
        int items = 0;
        try {
            for (InventoryRow inventoryRow : rows) {
                if (context != null && context.isCancelled()) {
                    break;
                }
                ContainerReplayResult result = applyInventoryRow(player, inventoryRow.row, inventoryRow.item, rollbackType);
                if (result != null && result.applied()) {
                    int modifiedArmor = result.modifiedArmorSlot();
                    if (modifiedArmor > -1 && !sortSlots.contains(modifiedArmor)) {
                        sortSlots.add(modifiedArmor);
                    }
                    applied.add(inventoryRow.row);
                    items += result.modifiedAmount();
                }
            }
            if (!sortSlots.isEmpty()) {
                RollbackItemHandler.sortContainerItems(player.getInventory(), sortSlots);
            }
        }
        finally {
            publishInventoryRows(actor, applied, rollbackType);
        }
        return items;
    }

    private static ContainerReplayResult applyInventoryRow(Player player, Object[] row, Material inventoryItem, int rollbackType) {
        int rowAction = (Integer) row[8];
        int inventoryAction = ItemTransactionActions.getInventoryActionId(rowAction);
        int action = rollbackType == 0 ? (inventoryAction ^ 1) : inventoryAction;
        ItemStack itemstack = new ItemStack(inventoryItem, (Integer) row[11]);
        RollbackUtil.ContainerItem item = RollbackUtil.decodeContainerItem(itemstack, (byte[]) row[12]);
        PlayerInventory inventory = player.getInventory();
        if (rowAction != ItemTransactionActions.REMOVE_ENDER && rowAction != ItemTransactionActions.ADD_ENDER) {
            return RollbackUtil.replayContainerItems(null, inventory, item.slot(), item.stack(), action);
        }

        Inventory ender = player.getEnderChest();
        Inventory source = action == 1 ? ender : inventory;
        Inventory destination = action == 1 ? inventory : ender;
        ItemStack[] sourceContents = ItemUtils.getContainerState(source instanceof PlayerInventory ? source.getContents() : source.getStorageContents());
        ContainerReplayResult removed = RollbackUtil.replayContainerItems(null, source, item.slot(), item.stack(), 0);
        if (!removed.applied() || removed.modifiedAmount() == 0) {
            return null;
        }

        ContainerReplayResult added = null;
        try {
            added = RollbackUtil.replayContainerItems(null, destination, item.slot(), item.stack(), 1);
            return added;
        }
        finally {
            if (added == null || !added.applied()) {
                if (source instanceof PlayerInventory) {
                    source.setContents(sourceContents);
                }
                else {
                    source.setStorageContents(sourceContents);
                }
            }
        }
    }

    private static void publishInventoryRows(String actor, List<Object[]> rows, int rollbackType) {
        Map<Integer, List<Object[]>> updates = new LinkedHashMap<>();
        for (Object[] row : rows) {
            updates.computeIfAbsent((Integer) row[14], key -> new ArrayList<>()).add(row);
        }
        for (Entry<Integer, List<Object[]>> update : updates.entrySet()) {
            int target = update.getKey();
            if (target == InventorySources.ENTITY_CONTAINER) {
                Queue.queueEntityContainerRollbackUpdate(actor, (Location) null, update.getValue(), rollbackType, true);
                continue;
            }
            int process;
            if (target == RollbackUpdateTargets.INVENTORY_ITEM) {
                process = Process.INVENTORY_ROLLBACK_UPDATE;
            }
            else if (target == RollbackUpdateTargets.CONTAINER) {
                process = Process.INVENTORY_CONTAINER_ROLLBACK_UPDATE;
            }
            else {
                process = Process.BLOCK_INVENTORY_ROLLBACK_UPDATE;
            }
            Queue.queueRollbackUpdate(actor, null, update.getValue(), process, rollbackType);
        }
    }

    private static void scheduleInventoryRows(EntitySpawnRollbackHandler.Context context, String actor, Player player, List<InventoryRow> rows, int rollbackType) {
        CompletableFuture<Boolean> completion = new CompletableFuture<>();
        Runnable task = () -> {
            // Nothing is changed once the rollback is cancelled, and cleanup waits for a started mutation to finish
            if (!context.beginMutation()) {
                completion.complete(false);
                return;
            }

            boolean succeeded = false;
            try {
                context.addItemCount(applyInventoryRows(actor, player, rows, rollbackType, context));
                succeeded = true;
            }
            catch (Exception e) {
                context.cancel();
                ErrorReporter.report(e);
            }
            finally {
                context.endMutation();
                completion.complete(succeeded);
            }
        };

        // A player who logged out is skipped, like an offline player on the inline path
        Runnable retired = () -> completion.complete(true);
        if (!PaperAdapter.ADAPTER.executeEntityTask(CoreProtect.getInstance(), player, task, retired)) {
            retired.run();
        }
        context.addPending(completion);
    }

    private static void loadChunk(World world, int chunkX, int chunkZ, boolean inventoryRollback) {
        if (inventoryRollback || world == null) {
            return;
        }

        if (!world.isChunkLoaded(chunkX, chunkZ)) {
            world.getChunkAt(chunkX, chunkZ);
        }
    }

    private static final class InventoryRow {
        private final Object[] row;
        private final Material item;

        private InventoryRow(Object[] row, Material item) {
            this.row = row;
            this.item = item;
        }
    }

}
