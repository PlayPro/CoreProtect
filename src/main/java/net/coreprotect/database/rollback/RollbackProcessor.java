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
import org.bukkit.block.data.type.Jukebox;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import net.coreprotect.bukkit.BukkitAdapter;
import net.coreprotect.config.Config;
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.database.logger.ItemLogger;
import net.coreprotect.model.BlockGroup;
import net.coreprotect.utility.BlockUtils;
import net.coreprotect.utility.ItemUtils;
import net.coreprotect.utility.MaterialUtils;
import net.coreprotect.utility.Teleport;
import net.coreprotect.utility.WorldUtils;

public class RollbackProcessor {

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
     * @return True if successful, false if there was an error
     */
    public static boolean processChunk(int finalChunkX, int finalChunkZ, long chunkKey, ArrayList<Object[]> blockList, ArrayList<Object[]> itemList, int rollbackType, int preview, String finalUserString, Player finalUser, World bukkitRollbackWorld, boolean inventoryRollback) {
        try {
            boolean clearInventories = Config.getGlobal().ROLLBACK_ITEMS;
            ArrayList<Object[]> data = blockList != null ? blockList : new ArrayList<>();
            ArrayList<Object[]> itemData = itemList != null ? itemList : new ArrayList<>();
            Map<Block, BlockData> chunkChanges = new LinkedHashMap<>();

            // Process blocks
            for (Object[] row : data) {
                int[] rollbackHashData = ConfigHandler.rollbackHash.get(finalUserString);
                int itemCount = rollbackHashData[0];
                int blockCount = rollbackHashData[1];
                int entityCount = rollbackHashData[2];
                int scannedWorlds = rollbackHashData[4];

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

                BlockData blockData = null;
                if (blockDataString != null && blockDataString.contains(":")) {
                    try {
                        blockData = Bukkit.getServer().createBlockData(blockDataString);
                    }
                    catch (Exception e) {
                        // corrupt BlockData, let the server automatically set the BlockData instead
                    }
                }

                BlockData rawBlockData = null;
                if (blockData != null) {
                    rawBlockData = blockData.clone();
                }
                if (rawBlockData == null && rowType != null && rowType.isBlock()) {
                    rawBlockData = BlockUtils.createBlockData(rowType);
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
                        String world = WorldUtils.getWorldName(rowWorldId);
                        if (world.length() == 0) {
                            continue;
                        }

                        World bukkitWorld = Bukkit.getServer().getWorld(world);
                        if (bukkitWorld == null) {
                            continue;
                        }

                        Block block = new Location(bukkitWorld, rowX, rowY, rowZ).getBlock();
                        if (preview == 2) {
                            Material blockType = block.getType();
                            if (!BukkitAdapter.ADAPTER.isItemFrame(blockType) && !blockType.equals(Material.PAINTING) && !blockType.equals(Material.ARMOR_STAND) && !blockType.equals(Material.END_CRYSTAL)) {
                                BlockUtils.prepareTypeAndData(chunkChanges, block, blockType, block.getBlockData(), true);
                                blockCount++;
                            }
                        }
                        else {
                            if ((!BukkitAdapter.ADAPTER.isItemFrame(rowType)) && (rowType != Material.PAINTING) && (rowType != Material.ARMOR_STAND) && (rowType != Material.END_CRYSTAL)) {
                                BlockUtils.prepareTypeAndData(chunkChanges, block, rowType, blockData, true);
                                blockCount++;
                            }
                        }
                    }
                    else {
                        entityCount++;
                    }
                }
                else if (rowAction == 3) { // entity kill
                    entityCount += RollbackEntityHandler.processEntity(row, rollbackType, finalUserString, oldTypeRaw, rowTypeRaw, rowData, rowAction, MaterialUtils.rolledBack((Integer) row[9], false), rowX, rowY, rowZ, rowWorldId, (Integer) row[2], rowUser);
                }
                else {
                    String world = WorldUtils.getWorldName(rowWorldId);
                    if (world.length() == 0) {
                        continue;
                    }

                    World bukkitWorld = Bukkit.getServer().getWorld(world);
                    if (bukkitWorld == null) {
                        continue;
                    }

                    Block block = bukkitWorld.getBlockAt(rowX, rowY, rowZ);
                    if (!bukkitWorld.isChunkLoaded(block.getChunk())) {
                        bukkitWorld.getChunkAt(block.getLocation());
                    }

                    boolean changeBlock = true;
                    boolean countBlock = true;
                    Material changeType = block.getType();
                    BlockData changeBlockData = block.getBlockData();
                    BlockData pendingChangeData = chunkChanges.get(block);
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
                                if (rowType != Material.CHEST && rowType != Material.TRAPPED_CHEST) { // always update double chests
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
                            if (Material.WATER.createBlockData().equals(block.getBlockData())) {
                                org.bukkit.block.data.Waterlogged waterlogged = (org.bukkit.block.data.Waterlogged) blockData;
                                waterlogged.setWaterlogged(true);
                            }
                        }
                    }

                    if (RollbackBlockHandler.processBlockChange(bukkitWorld, block, row, rollbackType, clearInventories, chunkChanges, countBlock, oldTypeMaterial, pendingChangeType, pendingChangeData, finalUserString, rawBlockData, changeType, changeBlock, changeBlockData, meta != null ? new ArrayList<>(meta) : null, blockData, rowUser, rowType, rowX, rowY, rowZ, rowTypeRaw, rowData, rowAction, rowWorldId, BlockUtils.byteDataToString((byte[]) row[13], rowTypeRaw)) && countBlock) {
                        blockCount++;
                    }
                }

                ConfigHandler.rollbackHash.put(finalUserString, new int[] { itemCount, blockCount, entityCount, 0, scannedWorlds });
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
                int[] rollbackHashData1 = ConfigHandler.rollbackHash.get(finalUserString);
                int itemCount1 = rollbackHashData1[0];
                int blockCount1 = rollbackHashData1[1];
                int entityCount1 = rollbackHashData1[2];
                int scannedWorlds = rollbackHashData1[4];
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
                        Material inventoryItem = ItemUtils.itemFilter(rowType, ((Integer) row[14] == 0));
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

                        int inventoryAction = 0;
                        if (rowAction == ItemLogger.ITEM_DROP || rowAction == ItemLogger.ITEM_PICKUP || rowAction == ItemLogger.ITEM_THROW || rowAction == ItemLogger.ITEM_SHOOT || rowAction == ItemLogger.ITEM_BREAK || rowAction == ItemLogger.ITEM_DESTROY || rowAction == ItemLogger.ITEM_CREATE || rowAction == ItemLogger.ITEM_SELL || rowAction == ItemLogger.ITEM_BUY) {
                            inventoryAction = ((rowAction == ItemLogger.ITEM_PICKUP || rowAction == ItemLogger.ITEM_CREATE || rowAction == ItemLogger.ITEM_BUY) ? 1 : 0);
                        }
                        else if (rowAction == ItemLogger.ITEM_REMOVE_ENDER || rowAction == ItemLogger.ITEM_ADD_ENDER) {
                            inventoryAction = (rowAction == ItemLogger.ITEM_REMOVE_ENDER ? 1 : 0);
                        }
                        else {
                            inventoryAction = (rowAction == ItemLogger.ITEM_REMOVE ? 1 : 0);
                        }

                        int action = rollbackType == 0 ? (inventoryAction ^ 1) : inventoryAction;
                        ItemStack itemstack = new ItemStack(inventoryItem, rowAmount);
                        Object[] populatedStack = RollbackItemHandler.populateItemStack(itemstack, rowMetadata);
                        if (rowAction == ItemLogger.ITEM_REMOVE_ENDER || rowAction == ItemLogger.ITEM_ADD_ENDER) {
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

                        itemCount1 = itemCount1 + rowAmount;
                        ConfigHandler.rollbackHash.put(finalUserString, new int[] { itemCount1, blockCount1, entityCount1, 0, scannedWorlds });
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
                            String world = WorldUtils.getWorldName(rowWorldId);
                            if (world.length() == 0) {
                                continue;
                            }

                            World bukkitWorld = Bukkit.getServer().getWorld(world);
                            if (bukkitWorld == null) {
                                continue;
                            }
                            Block block = bukkitWorld.getBlockAt(rowX, rowY, rowZ);
                            if (!bukkitWorld.isChunkLoaded(block.getChunk())) {
                                bukkitWorld.getChunkAt(block.getLocation());
                            }

                            if (BlockGroup.CONTAINERS.contains(block.getType())) {
                                BlockState blockState = block.getState();
                                if (blockState instanceof Jukebox) {
                                    container = blockState;
                                }
                                else {
                                    container = BlockUtils.getContainerInventory(blockState, false);
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
                            itemCount1 = itemCount1 + rowAmount;
                        }
                        containerInit = true;
                    }
                }

                ConfigHandler.rollbackHash.put(finalUserString, new int[] { itemCount1, blockCount1, entityCount1, 0, scannedWorlds });
            }
            itemData.clear();

            for (Entry<Player, List<Integer>> sortEntry : sortPlayers.entrySet()) {
                RollbackItemHandler.sortContainerItems(sortEntry.getKey().getInventory(), sortEntry.getValue());
            }
            sortPlayers.clear();

            int[] rollbackHashData = ConfigHandler.rollbackHash.get(finalUserString);
            int itemCount = rollbackHashData[0];
            int blockCount = rollbackHashData[1];
            int entityCount = rollbackHashData[2];
            int scannedWorlds = rollbackHashData[4];
            ConfigHandler.rollbackHash.put(finalUserString, new int[] { itemCount, blockCount, entityCount, 1, (scannedWorlds + 1) });

            // Teleport players out of danger if they're within this chunk
            if (preview == 0) {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    Location playerLocation = player.getLocation();
                    String playerWorld = playerLocation.getWorld().getName();
                    int chunkX = playerLocation.getBlockX() >> 4;
                    int chunkZ = playerLocation.getBlockZ() >> 4;

                    if (bukkitRollbackWorld.getName().equals(playerWorld) && chunkX == finalChunkX && chunkZ == finalChunkZ) {
                        Teleport.performSafeTeleport(player, playerLocation, false);
                    }
                }
            }

            return true;
        }
        catch (Exception e) {
            e.printStackTrace();
            int[] rollbackHashData = ConfigHandler.rollbackHash.get(finalUserString);
            int itemCount = rollbackHashData[0];
            int blockCount = rollbackHashData[1];
            int entityCount = rollbackHashData[2];
            int scannedWorlds = rollbackHashData[4];

            ConfigHandler.rollbackHash.put(finalUserString, new int[] { itemCount, blockCount, entityCount, 2, (scannedWorlds + 1) });
            return false;
        }
    }
}
