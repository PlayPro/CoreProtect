package net.coreprotect.database.rollback;

import java.util.ArrayList;
import java.util.Map;
import java.util.Map.Entry;

import org.bukkit.DyeColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Banner;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.CommandBlock;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.block.banner.Pattern;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.Bisected.Half;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.MultipleFacing;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.block.data.type.Bed;
import org.bukkit.block.data.type.Bed.Part;
import org.bukkit.block.data.type.Chest;
import org.bukkit.block.data.type.Door;
import org.bukkit.block.data.type.Door.Hinge;
import org.bukkit.block.data.type.Piston;
import org.bukkit.block.data.type.PistonHead;
import org.bukkit.block.data.type.RedstoneWire;
import org.bukkit.block.data.type.Snow;
import org.bukkit.block.data.type.Stairs;
import org.bukkit.block.data.type.TechnicalPiston;
import org.bukkit.block.data.type.TrapDoor;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import net.coreprotect.bukkit.BukkitAdapter;
import net.coreprotect.consumer.Queue;
import net.coreprotect.listener.player.InventoryChangeListener;
import net.coreprotect.model.BlockGroup;
import net.coreprotect.model.PendingBlockChange;
import net.coreprotect.paper.PaperAdapter;
import net.coreprotect.thread.CacheHandler;
import net.coreprotect.utility.BlockUtils;
import net.coreprotect.utility.ChestTool;
import net.coreprotect.utility.EntityUtils;
import net.coreprotect.utility.ItemUtils;
import net.coreprotect.utility.Util;
import net.coreprotect.utility.entity.HangingUtil;
import net.coreprotect.utility.ErrorReporter;

public class RollbackBlockHandler extends Queue {

    public static boolean processBlockChange(World bukkitWorld, Block block, Object[] row, int rollbackType, boolean clearInventories, Map<Block, PendingBlockChange> chunkChanges, boolean countBlock, Material oldTypeMaterial, Material pendingChangeType, BlockData pendingChangeData, RollbackCounters counters, BlockData rawBlockData, Material changeType, boolean changeBlock, BlockData changeBlockData, ArrayList<Object> meta, BlockData blockData, String rowUser, Material rowType, int rowX, int rowY, int rowZ, int rowTypeRaw, int rowData, int rowAction, int rowWorldId, String blockDataString) {
        int unixtimestamp = (int) (System.currentTimeMillis() / 1000L);

        try {
            if (changeBlock) {
                /* If modifying the head of a piston, update the base piston block to prevent it from being destroyed */
                if (changeBlockData instanceof PistonHead) {
                    PistonHead pistonHead = (PistonHead) changeBlockData;
                    Block pistonBlock = block.getRelative(pistonHead.getFacing().getOppositeFace());
                    BlockData pistonData = pistonBlock.getBlockData();
                    if (pistonData instanceof Piston) {
                        Piston piston = (Piston) pistonData;
                        piston.setExtended(false);
                        pistonBlock.setBlockData(piston, false);
                    }
                }
                else if (rowType == Material.MOVING_PISTON && blockData instanceof TechnicalPiston && !(blockData instanceof PistonHead)) {
                    TechnicalPiston technicalPiston = (TechnicalPiston) blockData;
                    rowType = (technicalPiston.getType() == org.bukkit.block.data.type.TechnicalPiston.Type.STICKY ? Material.STICKY_PISTON : Material.PISTON);
                    blockData = rowType.createBlockData();
                    ((Piston) blockData).setFacing(technicalPiston.getFacing());
                }

                if (rowType == null) {
                    BlockData customBlockData = blockData != null ? blockData : rawBlockData;
                    if (customBlockData != null) {
                        BlockUtils.prepareTypeAndData(chunkChanges, block, null, customBlockData, true);
                        return countBlock;
                    }

                    return false;
                }

                if ((rowType == Material.AIR) && ((BukkitAdapter.ADAPTER.isItemFrame(oldTypeMaterial)) || (oldTypeMaterial == Material.PAINTING))) {
                    HangingUtil.removeHanging(block.getState(), blockDataString);
                }
                else if ((BukkitAdapter.ADAPTER.isItemFrame(rowType)) || (rowType == Material.PAINTING)) {
                    HangingUtil.spawnHanging(block.getState(), rowType, blockDataString, rowData);
                }
                else if ((rowType == Material.ARMOR_STAND)) {
                    Location location1 = block.getLocation();
                    location1.setX(location1.getX() + 0.50);
                    location1.setZ(location1.getZ() + 0.50);
                    location1.setYaw(rowData);
                    boolean exists = false;

                    for (Entity entity : block.getChunk().getEntities()) {
                        if (entity instanceof ArmorStand) {
                            if (entity.getLocation().getBlockX() == location1.getBlockX() && entity.getLocation().getBlockY() == location1.getBlockY() && entity.getLocation().getBlockZ() == location1.getBlockZ()) {
                                exists = true;
                            }
                        }
                    }

                    if (!exists) {
                        Entity entity = block.getLocation().getWorld().spawnEntity(location1, EntityType.ARMOR_STAND);
                        PaperAdapter.ADAPTER.teleportAsync(entity, location1);
                    }
                }
                else if ((rowType == Material.END_CRYSTAL)) {
                    Location location1 = block.getLocation();
                    location1.setX(location1.getX() + 0.50);
                    location1.setZ(location1.getZ() + 0.50);
                    boolean exists = false;

                    for (Entity entity : block.getChunk().getEntities()) {
                        if (entity instanceof EnderCrystal) {
                            if (entity.getLocation().getBlockX() == location1.getBlockX() && entity.getLocation().getBlockY() == location1.getBlockY() && entity.getLocation().getBlockZ() == location1.getBlockZ()) {
                                exists = true;
                            }
                        }
                    }

                    if (!exists) {
                        Entity entity = block.getLocation().getWorld().spawnEntity(location1, BukkitAdapter.ADAPTER.getEntityType(Material.END_CRYSTAL));
                        EnderCrystal enderCrystal = (EnderCrystal) entity;
                        enderCrystal.setShowingBottom((rowData != 0));
                        PaperAdapter.ADAPTER.teleportAsync(entity, location1);
                    }
                }
                else if ((rowType == Material.AIR) && ((oldTypeMaterial == Material.WATER))) {
                    if (pendingChangeData instanceof Waterlogged) {
                        Waterlogged waterlogged = (Waterlogged) pendingChangeData;
                        waterlogged.setWaterlogged(false);
                        BlockUtils.prepareTypeAndData(chunkChanges, block, null, waterlogged, false);
                    }
                    else {
                        BlockUtils.prepareTypeAndData(chunkChanges, block, rowType, blockData, true);
                    }

                    return countBlock;
                }
                else if ((rowType == Material.AIR) && ((oldTypeMaterial == Material.SNOW))) {
                    BlockUtils.prepareTypeAndData(chunkChanges, block, rowType, blockData, true);
                    return countBlock;
                }
                else if ((rowType == Material.AIR) && ((oldTypeMaterial == Material.END_CRYSTAL))) {
                    for (Entity entity : block.getChunk().getEntities()) {
                        if (entity instanceof EnderCrystal) {
                            if (entity.getLocation().getBlockX() == rowX && entity.getLocation().getBlockY() == rowY && entity.getLocation().getBlockZ() == rowZ) {
                                entity.remove();
                            }
                        }
                    }
                }
                else if (rollbackType == 0 && rowAction == 0 && (rowType == Material.AIR)) {
                    // broke block ID #0
                }
                else if ((rowType == Material.AIR) || (rowType == Material.TNT)) {
                    if (clearInventories) {
                        if (BlockGroup.CONTAINERS.contains(changeType)) {
                            Inventory inventory = BlockUtils.getContainerInventory(block.getState(), false);
                            if (inventory != null) {
                                InventoryChangeListener.flushPendingContainer(inventory, block.getLocation());
                                inventory.clear();
                            }
                        }
                        else if (BlockGroup.CONTAINERS.contains(Material.ARMOR_STAND)) {
                            if ((oldTypeMaterial == Material.ARMOR_STAND)) {
                                for (Entity entity : block.getChunk().getEntities()) {
                                    if (entity instanceof ArmorStand) {
                                        Location entityLocation = entity.getLocation();
                                        entityLocation.setY(entityLocation.getY() + 0.99);

                                        if (entityLocation.getBlockX() == rowX && entityLocation.getBlockY() == rowY && entityLocation.getBlockZ() == rowZ) {
                                            ItemUtils.getEntityEquipment((ArmorStand) entity).clear();

                                            entityLocation.setY(entityLocation.getY() - 1.99);
                                            PaperAdapter.ADAPTER.teleportAsync(entity, entityLocation);
                                            entity.remove();
                                        }
                                    }
                                }
                            }
                        }
                    }

                    boolean remove = true;
                    if ((rowType == Material.AIR)) {
                        if (pendingChangeData instanceof Waterlogged) {
                            Waterlogged waterlogged = (Waterlogged) pendingChangeData;
                            if (waterlogged.isWaterlogged()) {
                                BlockUtils.prepareTypeAndData(chunkChanges, block, Material.WATER, Material.WATER.createBlockData(), true);
                                remove = false;
                            }
                        }
                        else if ((pendingChangeType == Material.WATER)) {
                            if (rawBlockData instanceof Waterlogged) {
                                Waterlogged waterlogged = (Waterlogged) rawBlockData;
                                if (waterlogged.isWaterlogged()) {
                                    remove = false;
                                }
                            }
                        }
                    }

                    if (remove) {
                        BlockData removeBlockData = pendingChangeData != null ? pendingChangeData : changeBlockData;
                        boolean physics = requiresPhysics(changeType) || requiresPhysics(removeBlockData);
                        if ((changeType == Material.NETHER_PORTAL) || removeBlockData instanceof MultipleFacing || removeBlockData instanceof Snow || removeBlockData instanceof Stairs || removeBlockData instanceof RedstoneWire || removeBlockData instanceof Chest) {
                            physics = true;
                        }
                        else if (removeBlockData instanceof Bisected && !(removeBlockData instanceof TrapDoor)) {
                            Bisected bisected = (Bisected) removeBlockData;
                            Location bisectLocation = block.getLocation().clone();
                            if (bisected.getHalf() == Half.TOP) {
                                bisectLocation.setY(bisectLocation.getY() - 1);
                            }
                            else {
                                bisectLocation.setY(bisectLocation.getY() + 1);
                            }

                            int worldMaxHeight = bukkitWorld.getMaxHeight();
                            int worldMinHeight = BukkitAdapter.ADAPTER.getMinHeight(bukkitWorld);
                            if (bisectLocation.getBlockY() >= worldMinHeight && bisectLocation.getBlockY() < worldMaxHeight) {
                                Block bisectBlock = block.getWorld().getBlockAt(bisectLocation);
                                BlockUtils.prepareTypeAndData(chunkChanges, bisectBlock, rowType, null, false);

                                if (countBlock) {
                                    counters.addBlocks(1);
                                }
                            }
                        }
                        else if (removeBlockData instanceof Bed) {
                            Bed bed = (Bed) removeBlockData;
                            if (bed.getPart() == Part.FOOT) {
                                Block adjacentBlock = block.getRelative(bed.getFacing());
                                BlockUtils.prepareTypeAndData(chunkChanges, adjacentBlock, rowType, null, false);
                            }
                        }

                        BlockUtils.queueTypeAndData(chunkChanges, block, rowType, null, physics);
                    }

                    return countBlock;
                }
                else if ((rowType == Material.SPAWNER)) {
                    try {
                        BlockUtils.prepareTypeAndData(chunkChanges, block, rowType, blockData, false);
                        CreatureSpawner mobSpawner = (CreatureSpawner) block.getState();
                        mobSpawner.setSpawnedType(EntityUtils.getSpawnerType(rowData));
                        mobSpawner.update();

                        return countBlock;
                    }
                    catch (Exception e) {
                        // e.printStackTrace();
                    }
                }
                else if ((rowType == Material.SKELETON_SKULL) || (rowType == Material.SKELETON_WALL_SKULL) || (rowType == Material.WITHER_SKELETON_SKULL) || (rowType == Material.WITHER_SKELETON_WALL_SKULL) || (rowType == Material.ZOMBIE_HEAD) || (rowType == Material.ZOMBIE_WALL_HEAD) || (rowType == Material.PLAYER_HEAD) || (rowType == Material.PLAYER_WALL_HEAD) || (rowType == Material.CREEPER_HEAD) || (rowType == Material.CREEPER_WALL_HEAD) || (rowType == Material.DRAGON_HEAD) || (rowType == Material.DRAGON_WALL_HEAD)) { // skull
                    BlockUtils.prepareTypeAndData(chunkChanges, block, rowType, blockData, false);
                    if (rowData > 0) {
                        Queue.queueSkullUpdate(rowUser, block.getState(), rowData);
                    }

                    return countBlock;
                }
                else if (BukkitAdapter.ADAPTER.isSign(rowType)) {// sign
                    BlockUtils.prepareTypeAndData(chunkChanges, block, rowType, blockData, false);
                    Queue.queueSignUpdate(rowUser, block.getState(), rollbackType, (Integer) row[1]);

                    return countBlock;
                }
                else if (BlockGroup.SHULKER_BOXES.contains(rowType)) {
                    BlockUtils.prepareTypeAndData(chunkChanges, block, rowType, blockData, false);
                    if (countBlock) {
                        counters.addBlocks(1);
                    }
                    if (meta != null) {
                        Inventory inventory = BlockUtils.getContainerInventory(block.getState(), false);
                        InventoryChangeListener.flushPendingContainer(inventory, block.getLocation());
                        for (Object value : meta) {
                            ItemStack item = ItemUtils.unserializeItemStackLegacy(value);
                            if (item != null) {
                                RollbackUtil.modifyContainerItems(rowType, inventory, 0, item, 1);
                            }
                        }
                    }
                    return false;
                }
                else if (rowType == Material.COMMAND_BLOCK || rowType == Material.REPEATING_COMMAND_BLOCK || rowType == Material.CHAIN_COMMAND_BLOCK) { // command block
                    BlockUtils.prepareTypeAndData(chunkChanges, block, rowType, blockData, false);
                    if (countBlock) {
                        counters.addBlocks(1);
                    }

                    if (meta != null) {
                        CommandBlock commandBlock = (CommandBlock) block.getState();
                        for (Object value : meta) {
                            if (value instanceof String) {
                                String string = (String) value;
                                commandBlock.setCommand(string);
                                commandBlock.update();
                            }
                        }
                    }
                    return false;
                }
                else if ((rowType == Material.WATER)) {
                    if (bukkitWorld.getEnvironment() == World.Environment.NETHER) {
                        return false;
                    }

                    if (pendingChangeData instanceof Waterlogged) {
                        Waterlogged waterlogged = (Waterlogged) pendingChangeData;
                        waterlogged.setWaterlogged(true);
                        BlockUtils.prepareTypeAndData(chunkChanges, block, null, waterlogged, false);
                    }
                    else {
                        BlockUtils.prepareTypeAndData(chunkChanges, block, rowType, blockData, false);
                    }

                    return countBlock;
                }
                else if ((rowType == Material.NETHER_PORTAL) && rowAction == 0) {
                    BlockUtils.prepareTypeAndData(chunkChanges, block, Material.FIRE, null, true);
                }
                else if (blockData == null && rowData > 0 && (rowType == Material.IRON_DOOR || BlockGroup.DOORS.contains(rowType))) {
                    if (countBlock) {
                        counters.addBlocks(1);
                    }

                    block.setType(rowType, false);
                    Door door = (Door) block.getBlockData();
                    if (rowData >= 8) {
                        door.setHalf(Half.TOP);
                        rowData = rowData - 8;
                    }
                    else {
                        door.setHalf(Half.BOTTOM);
                    }
                    if (rowData >= 4) {
                        door.setHinge(Hinge.RIGHT);
                        rowData = rowData - 4;
                    }
                    else {
                        door.setHinge(Hinge.LEFT);
                    }
                    BlockFace face = BlockFace.NORTH;

                    switch (rowData) {
                        case 0:
                            face = BlockFace.EAST;
                            break;
                        case 1:
                            face = BlockFace.SOUTH;
                            break;
                        case 2:
                            face = BlockFace.WEST;
                            break;
                    }

                    door.setFacing(face);
                    door.setOpen(false);
                    block.setBlockData(door, false);
                    return false;
                }
                else if (blockData == null && rowData > 0 && (rowType.name().endsWith("_BED"))) {
                    if (countBlock) {
                        counters.addBlocks(1);
                    }

                    block.setType(rowType, false);
                    Bed bed = (Bed) block.getBlockData();
                    BlockFace face = BlockFace.NORTH;

                    if (rowData > 4) {
                        bed.setPart(Part.HEAD);
                        rowData = rowData - 4;
                    }

                    switch (rowData) {
                        case 2:
                            face = BlockFace.WEST;
                            break;
                        case 3:
                            face = BlockFace.EAST;
                            break;
                        case 4:
                            face = BlockFace.SOUTH;
                            break;
                    }

                    bed.setFacing(face);
                    block.setBlockData(bed, false);
                    return false;
                }
                else if (rowType.name().endsWith("_BANNER")) {
                    BlockUtils.prepareTypeAndData(chunkChanges, block, rowType, blockData, false);
                    if (countBlock) {
                        counters.addBlocks(1);
                    }

                    if (meta != null) {
                        Banner banner = (Banner) block.getState();

                        for (Object value : meta) {
                            if (value instanceof DyeColor) {
                                banner.setBaseColor((DyeColor) value);
                            }
                            else if (value instanceof Map) {
                                @SuppressWarnings("unchecked")
                                Pattern pattern = new Pattern((Map<String, Object>) value);
                                banner.addPattern(pattern);
                            }
                        }

                        banner.update();
                    }
                    return false;
                }
                else if (rowType != changeType && (BlockGroup.CONTAINERS.contains(rowType) || BlockGroup.CONTAINERS.contains(changeType))) {
                    block.setType(Material.AIR); // Clear existing container to prevent errors

                    boolean isChest = (blockData instanceof Chest);
                    BlockUtils.prepareTypeAndData(chunkChanges, block, rowType, blockData, (isChest));
                    if (isChest) {
                        ChestTool.updateDoubleChest(block, blockData, false);
                    }

                    return countBlock;
                }
                else if (BlockGroup.UPDATE_STATE.contains(rowType) || rowType.name().contains("CANDLE")) {
                    BlockUtils.prepareTypeAndData(chunkChanges, block, rowType, blockData, true);
                    ChestTool.updateDoubleChest(block, blockData, true);
                    return countBlock;
                }
                else if (rowType != Material.AIR && rawBlockData instanceof Bisected && !(rawBlockData instanceof Stairs || rawBlockData instanceof TrapDoor)) {
                    Bisected bisected = (Bisected) rawBlockData;
                    Bisected bisectData = (Bisected) rawBlockData.clone();
                    Location bisectLocation = block.getLocation().clone();
                    if (bisected.getHalf() == Half.TOP) {
                        bisectData.setHalf(Half.BOTTOM);
                        bisectLocation.setY(bisectLocation.getY() - 1);
                    }
                    else {
                        bisectData.setHalf(Half.TOP);
                        bisectLocation.setY(bisectLocation.getY() + 1);
                    }

                    int worldMaxHeight = bukkitWorld.getMaxHeight();
                    int worldMinHeight = BukkitAdapter.ADAPTER.getMinHeight(bukkitWorld);
                    if (bisectLocation.getBlockY() >= worldMinHeight && bisectLocation.getBlockY() < worldMaxHeight) {
                        Block bisectBlock = block.getWorld().getBlockAt(bisectLocation);
                        BlockUtils.prepareTypeAndData(chunkChanges, bisectBlock, rowType, bisectData, false);
                    }

                    BlockUtils.prepareTypeAndData(chunkChanges, block, rowType, blockData, false);
                    if (countBlock) {
                        counters.addBlocks(2);
                    }
                    return false;
                }
                else if (rowType != Material.AIR && rawBlockData instanceof Bed) {
                    Bed bed = (Bed) rawBlockData;
                    if (bed.getPart() == Part.FOOT) {
                        Block adjacentBlock = block.getRelative(bed.getFacing());
                        Bed bedData = (Bed) rawBlockData.clone();
                        bedData.setPart(Part.HEAD);
                        BlockUtils.prepareTypeAndData(chunkChanges, adjacentBlock, rowType, bedData, false);
                        if (countBlock) {
                            counters.addBlocks(1);
                        }
                    }

                    BlockUtils.prepareTypeAndData(chunkChanges, block, rowType, blockData, true);
                    return countBlock;
                }
                else {
                    boolean physics = needsRollbackPhysics(rowType, oldTypeMaterial, changeType, pendingChangeType, blockData, rawBlockData, pendingChangeData, meta);
                    if (physics) {
                        BlockUtils.prepareTypeAndData(chunkChanges, block, rowType, blockData, true);
                    }
                    else {
                        BlockUtils.queueTypeAndData(chunkChanges, block, rowType, blockData, false);
                    }
                    return countBlock;
                }
            }
        }
        catch (Exception e) {
            ErrorReporter.report(e);
        }

        if ((rowType != Material.AIR) && changeBlock) {
            if (rowUser.length() > 0) {
                CacheHandler.lookupCache.put(rowX + "." + rowY + "." + rowZ + "." + rowWorldId, new Object[] { unixtimestamp, rowUser, rowType });
            }
        }

        return countBlock;
    }

    private static boolean needsRollbackPhysics(Material rowType, Material oldTypeMaterial, Material changeType, Material pendingChangeType, BlockData blockData, BlockData rawBlockData, BlockData pendingChangeData, ArrayList<Object> meta) {
        if (meta != null) {
            return true;
        }

        BlockData targetData = blockData != null ? blockData : rawBlockData;
        if (requiresPhysics(rowType) || requiresPhysics(oldTypeMaterial) || requiresExistingBlockPhysics(changeType) || requiresExistingBlockPhysics(pendingChangeType)) {
            return true;
        }

        return requiresPhysics(targetData) || requiresPhysics(pendingChangeData);
    }

    private static boolean requiresExistingBlockPhysics(Material type) {
        return type != null && !BlockUtils.isAir(type) && requiresPhysics(type);
    }

    private static boolean requiresPhysics(Material type) {
        if (type == null || !type.isBlock()) {
            return true;
        }
        if (BlockUtils.isAir(type) || !type.isSolid()) {
            return true;
        }
        if (type == Material.WATER || type == Material.LAVA || type == Material.FIRE || type == Material.TNT || type == Material.NETHER_PORTAL || type == Material.MOVING_PISTON) {
            return true;
        }
        if (BukkitAdapter.ADAPTER.isItemFrame(type) || type == Material.PAINTING || type == Material.ARMOR_STAND || type == Material.END_CRYSTAL) {
            return true;
        }
        if (BlockGroup.CONTAINERS.contains(type) || BlockGroup.SHULKER_BOXES.contains(type) || BlockGroup.UPDATE_STATE.contains(type) || BlockGroup.DOORS.contains(type) || BlockGroup.BUTTONS.contains(type) || BlockGroup.PRESSURE_PLATES.contains(type) || BlockGroup.TRACK_ANY.contains(type) || BlockGroup.TRACK_TOP.contains(type) || BlockGroup.TRACK_TOP_BOTTOM.contains(type) || BlockGroup.TRACK_BOTTOM.contains(type) || BlockGroup.TRACK_SIDE.contains(type) || BlockGroup.DIRECTIONAL_BLOCKS.contains(type) || BlockGroup.INTERACT_BLOCKS.contains(type) || BlockGroup.VINES.contains(type) || BlockGroup.AMETHYST.contains(type) || BlockGroup.LIGHTABLES.contains(type) || BlockGroup.LANTERNS.contains(type) || BlockGroup.FIRE.contains(type)) {
            return true;
        }

        String name = type.name();
        return name.endsWith("_SIGN") || name.endsWith("_BANNER") || name.endsWith("_SKULL") || name.endsWith("_HEAD") || name.endsWith("_BED") || name.endsWith("_DOOR") || name.endsWith("_TRAPDOOR") || name.endsWith("_RAIL") || name.endsWith("_CANDLE") || name.endsWith("_CANDLE_CAKE") || name.endsWith("_FENCE_GATE") || name.endsWith("_PISTON") || name.endsWith("_CAULDRON") || name.equals("BEEHIVE") || name.equals("BEE_NEST") || name.endsWith("CAMPFIRE") || name.endsWith("COMMAND_BLOCK") || name.equals("STRUCTURE_BLOCK") || name.equals("JIGSAW") || name.equals("DECORATED_POT") || name.equals("CHISELED_BOOKSHELF") || name.equals("CRAFTER");
    }

    private static boolean requiresPhysics(BlockData blockData) {
        return blockData instanceof MultipleFacing || blockData instanceof Waterlogged || blockData instanceof Bisected || blockData instanceof Bed || blockData instanceof Chest || blockData instanceof RedstoneWire || blockData instanceof Snow || blockData instanceof Stairs || blockData instanceof TrapDoor || blockData instanceof Piston || blockData instanceof PistonHead || blockData instanceof TechnicalPiston || blockData instanceof org.bukkit.block.data.Rail;
    }

    /**
     * Apply all pending block changes to the world
     * 
     * @param chunkChanges
     *            Map of blocks to change
     * @param preview
     *            Whether this is a preview
     * @param user
     *            The user performing the rollback
     */
    public static void applyBlockChanges(Map<Block, PendingBlockChange> chunkChanges, int preview, Player user) {
        if (preview == 0 || user == null) {
            applyBlockChanges(chunkChanges, true, true);
            applyBlockChanges(chunkChanges, true, false);
            applyBlockChanges(chunkChanges, false, false);
            applyBlockChanges(chunkChanges, false, true);
            chunkChanges.clear();
            return;
        }

        for (Entry<Block, PendingBlockChange> chunkChange : chunkChanges.entrySet()) {
            Block changeBlock = chunkChange.getKey();
            PendingBlockChange change = chunkChange.getValue();
            BlockData changeBlockData = change.blockData();
            Util.sendBlockChange(user, changeBlock.getLocation(), changeBlockData);
        }
        chunkChanges.clear();
    }

    private static void applyBlockChanges(Map<Block, PendingBlockChange> chunkChanges, boolean airChange, boolean applyPhysics) {
        for (Entry<Block, PendingBlockChange> chunkChange : chunkChanges.entrySet()) {
            PendingBlockChange change = chunkChange.getValue();
            boolean changeToAir = isAirChange(change);
            if (changeToAir == airChange && change.applyPhysics() == applyPhysics) {
                BlockUtils.setTypeAndData(chunkChange.getKey(), null, change.blockData(), !changeToAir && applyPhysics);
            }
        }
    }

    private static boolean isAirChange(PendingBlockChange change) {
        BlockData blockData = change.blockData();
        return blockData != null && BlockUtils.isAir(blockData.getMaterial());
    }
}
