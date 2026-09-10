package net.coreprotect.database.rollback;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Powerable;
import org.bukkit.block.data.type.Jukebox;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import net.coreprotect.CoreProtect;
import net.coreprotect.bukkit.BukkitAdapter;
import net.coreprotect.config.Config;
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.listener.player.InventoryChangeListener;
import net.coreprotect.model.BlockGroup;
import net.coreprotect.model.PendingBlockChange;
import net.coreprotect.model.item.ItemTransactionActions;
import net.coreprotect.thread.Scheduler;
import net.coreprotect.utility.BlockUtils;
import net.coreprotect.utility.ItemUtils;
import net.coreprotect.utility.MaterialUtils;
import net.coreprotect.utility.Teleport;
import net.coreprotect.utility.ErrorReporter;

public class RollbackProcessor {

    private static void normalizeRollbackBlockData(BlockData blockData) {
        if (blockData instanceof Powerable && blockData.getMaterial() == Material.NOTE_BLOCK) {
            ((Powerable) blockData).setPowered(false);
        }
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
     * @return True if successful, false if there was an error
     */
    public static boolean processChunk(int finalChunkX, int finalChunkZ, long chunkKey, ArrayList<Object[]> blockList, ArrayList<Object[]> itemList, int rollbackType, int preview, String finalUserString, Player finalUser, World bukkitRollbackWorld, boolean inventoryRollback, RollbackBlockDataCache blockDataCache) {
        RollbackCounters counters = new RollbackCounters();

        try {
            boolean clearInventories = Config.getGlobal().ROLLBACK_ITEMS;
            ArrayList<Object[]> data = blockList != null ? blockList : new ArrayList<>();
            ArrayList<Object[]> itemData = itemList != null ? itemList : new ArrayList<>();
            Map<Block, PendingBlockChange> chunkChanges = new LinkedHashMap<>();
            loadChunk(bukkitRollbackWorld, finalChunkX, finalChunkZ, inventoryRollback);

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

                String rowUser = ConfigHandler.playerIdCacheReversed.get((Integer) row[2]);
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
                    if (rowAction != 3) { // entity kill
                        Block block = bukkitRollbackWorld.getBlockAt(rowX, rowY, rowZ);
                        if (preview == 2) {
                            Material blockType = block.getType();
                            if (!BukkitAdapter.ADAPTER.isItemFrame(blockType) && !blockType.equals(Material.PAINTING) && !blockType.equals(Material.ARMOR_STAND) && !blockType.equals(Material.END_CRYSTAL)) {
                                BlockUtils.prepareTypeAndData(chunkChanges, block, blockType, block.getBlockData(), true);
                                counters.addBlocks(1);
                            }
                        }
                        else {
                            if ((!BukkitAdapter.ADAPTER.isItemFrame(rowType)) && (rowType != Material.PAINTING) && (rowType != Material.ARMOR_STAND) && (rowType != Material.END_CRYSTAL)) {
                                BlockUtils.prepareTypeAndData(chunkChanges, block, rowType, blockData, true);
                                counters.addBlocks(1);
                            }
                        }
                    }
                    else {
                        counters.addEntities(1);
                    }
                }
                else if (rowAction == 3) { // entity kill
                    counters.addEntities(RollbackEntityHandler.processEntity(bukkitRollbackWorld, oldTypeRaw, rowTypeRaw, rowData, rowAction, rowRolledBack, rowX, rowY, rowZ, rowWorldId, rowUser));
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

                    if ((rowType == pendingChangeType) && ((!BukkitAdapter.ADAPTER.isItemFrame(oldTypeMaterial)) && (oldTypeMaterial != Material.PAINTING) && (oldTypeMaterial != Material.ARMOR_STAND)) && (oldTypeMaterial != Material.END_CRYSTAL)) {
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

                    if (RollbackBlockHandler.processBlockChange(bukkitRollbackWorld, block, row, rollbackType, clearInventories, chunkChanges, countBlock, oldTypeMaterial, pendingChangeType, pendingChangeData, counters, rawBlockData, changeType, changeBlock, changeBlockData, meta != null ? new ArrayList<>(meta) : null, blockData, rowUser, rowType, rowX, rowY, rowZ, rowTypeRaw, rowData, rowAction, rowWorldId, BlockUtils.byteDataToString((byte[]) row[13], rowTypeRaw)) && countBlock) {
                        counters.addBlocks(1);
                    }
                }
            }
            data.clear();

            // Apply cached block changes
            RollbackBlockHandler.applyBlockChanges(chunkChanges, preview, finalUser instanceof Player ? (Player) finalUser : null);

            // Process container items
            Map<Player, List<Integer>> sortPlayers = new HashMap<>();
            Object container = null;
            Material containerType = null;
            boolean containerInit = false;
            int lastX = 0;
            int lastY = 0;
            int lastZ = 0;
            int lastWorldId = 0;
            String lastFace = "";

            for (Object[] row : itemData) {
                int rowX = (Integer) row[3];
                int rowY = (Integer) row[4];
                int rowZ = (Integer) row[5];
                int rowTypeRaw = (Integer) row[6];
                int rowData = (Integer) row[7];
                int rowAction = (Integer) row[8];
                int rowRolledBack = MaterialUtils.rolledBack((Integer) row[9], false);
                int rowWorldId = (Integer) row[10];
                int rowAmount = (Integer) row[11];
                byte[] rowMetadata = (byte[]) row[12];
                Material rowType = MaterialUtils.getType(rowTypeRaw);

                int rolledBackInventory = MaterialUtils.rolledBack((Integer) row[9], true);
                if (rowType != null) {
                    if (inventoryRollback && ((rollbackType == 0 && rolledBackInventory == 0) || (rollbackType == 1 && rolledBackInventory == 1))) {
                        Material inventoryItem = ItemUtils.inventoryItemFilter(rowType, ((Integer) row[14] == 0));
                        if (inventoryItem == null) {
                            continue;
                        }

                        int rowUserId = (Integer) row[2];
                        String rowUser = ConfigHandler.playerIdCacheReversed.get(rowUserId);
                        if (rowUser == null) {
                            continue;
                        }

                        String uuid = ConfigHandler.uuidCache.get(rowUser.toLowerCase(Locale.ROOT));
                        if (uuid == null) {
                            continue;
                        }

                        Player player = Bukkit.getServer().getPlayer(UUID.fromString(uuid));
                        if (player == null) {
                            continue;
                        }

                        int inventoryAction = ItemTransactionActions.getInventoryActionId(rowAction);
                        int action = rollbackType == 0 ? (inventoryAction ^ 1) : inventoryAction;
                        ItemStack itemstack = new ItemStack(inventoryItem, rowAmount);
                        Object[] populatedStack = RollbackItemHandler.populateItemStack(itemstack, rowMetadata);
                        if (rowAction == ItemTransactionActions.REMOVE_ENDER || rowAction == ItemTransactionActions.ADD_ENDER) {
                            RollbackUtil.modifyContainerItems(containerType, player.getEnderChest(), (Integer) populatedStack[0], ((ItemStack) populatedStack[2]).clone(), action ^ 1);
                        }
                        int modifiedArmor = RollbackUtil.modifyContainerItems(containerType, player.getInventory(), (Integer) populatedStack[0], (ItemStack) populatedStack[2], action);
                        if (modifiedArmor > -1) {
                            List<Integer> currentSortList = sortPlayers.getOrDefault(player, new ArrayList<>());
                            if (!currentSortList.contains(modifiedArmor)) {
                                currentSortList.add(modifiedArmor);
                            }
                            sortPlayers.put(player, currentSortList);
                        }

                        counters.addItems(rowAmount);
                        continue; // remove this for merged rollbacks in future? (be sure to re-enable chunk sorting)
                    }

                    if (inventoryRollback || rowAction > 1) {
                        continue; // skip inventory & ender chest transactions
                    }

                    if ((rollbackType == 0 && rowRolledBack == 0) || (rollbackType == 1 && rowRolledBack == 1)) {
                        ItemStack itemstack = new ItemStack(rowType, rowAmount);
                        Object[] populatedStack = RollbackItemHandler.populateItemStack(itemstack, rowMetadata);
                        String faceData = (String) populatedStack[1];

                        if (!containerInit || rowX != lastX || rowY != lastY || rowZ != lastZ || rowWorldId != lastWorldId || !faceData.equals(lastFace)) {
                            container = null; // container patch 2.14.0
                            Block block = bukkitRollbackWorld.getBlockAt(rowX, rowY, rowZ);

                            if (BlockGroup.CONTAINERS.contains(block.getType())) {
                                BlockState blockState = block.getState();
                                if (blockState instanceof Jukebox) {
                                    container = blockState;
                                }
                                else {
                                    container = BlockUtils.getContainerInventory(blockState, false);
                                    if (container instanceof Inventory) {
                                        InventoryChangeListener.flushPendingContainer((Inventory) container, block.getLocation());
                                    }
                                }

                                containerType = block.getType();
                            }
                            else if (BlockGroup.CONTAINERS.contains(Material.ARMOR_STAND) || BlockGroup.CONTAINERS.contains(Material.ITEM_FRAME)) {
                                for (Entity entity : block.getChunk().getEntities()) {
                                    if (entity.getLocation().getBlockX() == rowX && entity.getLocation().getBlockY() == rowY && entity.getLocation().getBlockZ() == rowZ) {
                                        if (entity instanceof ArmorStand) {
                                            container = ItemUtils.getEntityEquipment((LivingEntity) entity);
                                            containerType = Material.ARMOR_STAND;
                                        }
                                        else if (entity instanceof ItemFrame) {
                                            container = entity;
                                            containerType = Material.ITEM_FRAME;
                                            if (faceData.length() > 0 && (BlockFace.valueOf(faceData) == ((ItemFrame) entity).getFacing())) {
                                                break;
                                            }
                                        }
                                    }
                                }
                            }

                            lastX = rowX;
                            lastY = rowY;
                            lastZ = rowZ;
                            lastWorldId = rowWorldId;
                            lastFace = faceData;
                        }

                        if (container != null) {
                            int action = 0;
                            if (rollbackType == 0 && rowAction == 0) {
                                action = 1;
                            }

                            if (rollbackType == 1 && rowAction == 1) {
                                action = 1;
                            }

                            int slot = (Integer) populatedStack[0];
                            itemstack = (ItemStack) populatedStack[2];

                            RollbackUtil.modifyContainerItems(containerType, container, slot, itemstack, action);
                            counters.addItems(rowAmount);
                        }
                        containerInit = true;
                    }
                }
            }
            itemData.clear();

            for (Entry<Player, List<Integer>> sortEntry : sortPlayers.entrySet()) {
                RollbackItemHandler.sortContainerItems(sortEntry.getKey().getInventory(), sortEntry.getValue());
            }
            sortPlayers.clear();

            updateRollbackHash(finalUserString, counters, 1);

            // Teleport players out of danger if they're within this chunk
            if (preview == 0) {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    Location playerLocation = player.getLocation();
                    String playerWorld = playerLocation.getWorld().getName();
                    int chunkX = playerLocation.getBlockX() >> 4;
                    int chunkZ = playerLocation.getBlockZ() >> 4;

                    if (bukkitRollbackWorld.getName().equals(playerWorld) && chunkX == finalChunkX && chunkZ == finalChunkZ) {
                        Scheduler.runTask(CoreProtect.getInstance(), () -> Teleport.performSafeTeleport(player, playerLocation, false), player);
                    }
                }
            }

            return true;
        }
        catch (Exception e) {
            ErrorReporter.report(e);
            updateRollbackHash(finalUserString, counters, 2);
            return false;
        }
    }

    private static void updateRollbackHash(String finalUserString, RollbackCounters counters, int status) {
        int[] rollbackHashData = ConfigHandler.rollbackHash.get(finalUserString);
        int itemCount = rollbackHashData[0] + counters.getItems();
        int blockCount = rollbackHashData[1] + counters.getBlocks();
        int entityCount = rollbackHashData[2] + counters.getEntities();
        int scannedWorlds = rollbackHashData[4] + 1;
        ConfigHandler.rollbackHash.put(finalUserString, new int[] { itemCount, blockCount, entityCount, status, scannedWorlds });
    }

    private static void loadChunk(World world, int chunkX, int chunkZ, boolean inventoryRollback) {
        if (inventoryRollback || world == null) {
            return;
        }

        if (!world.isChunkLoaded(chunkX, chunkZ)) {
            world.getChunkAt(chunkX, chunkZ);
        }
    }

}
