package net.coreprotect.database.rollback;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;

import org.bukkit.DyeColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Banner;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
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
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import net.coreprotect.CoreProtect;
import net.coreprotect.bukkit.BukkitAdapter;
import net.coreprotect.consumer.Queue;
import net.coreprotect.listener.player.InventoryChangeListener;
import net.coreprotect.model.BlockGroup;
import net.coreprotect.model.PendingBlockChange;
import net.coreprotect.paper.PaperAdapter;
import net.coreprotect.thread.CacheHandler;
import net.coreprotect.thread.Scheduler;
import net.coreprotect.utility.BlockUtils;
import net.coreprotect.utility.ChestTool;
import net.coreprotect.utility.EntityUtils;
import net.coreprotect.utility.ItemUtils;
import net.coreprotect.utility.Util;
import net.coreprotect.utility.entity.HangingUtil;
import net.coreprotect.utility.ErrorReporter;

public class RollbackBlockHandler extends Queue {

    public static boolean processBlockChange(World bukkitWorld, Block block, Object[] row, int rollbackType, boolean clearInventories, Map<Block, PendingBlockChange> chunkChanges, boolean countBlock, Material oldTypeMaterial, Material pendingChangeType, BlockData pendingChangeData, RollbackCounters counters, BlockData rawBlockData, Material changeType, boolean changeBlock, BlockData changeBlockData, ArrayList<Object> meta, BlockData blockData, String rowUser, Material rowType, int rowX, int rowY, int rowZ, int rowTypeRaw, int rowData, int rowAction, int rowWorldId, String blockDataString) {
        return processBlockChange(bukkitWorld, block, row, rollbackType, clearInventories, chunkChanges, countBlock, oldTypeMaterial, pendingChangeType, pendingChangeData, counters, rawBlockData,
                changeType, changeBlock, changeBlockData, meta, blockData, rowUser, rowType, rowX, rowY, rowZ, rowTypeRaw, rowData, rowAction, rowWorldId, blockDataString, null).counted();
    }

    static BlockReplayResult processBlockChange(World bukkitWorld, Block block, Object[] row, int rollbackType, boolean clearInventories, Map<Block, PendingBlockChange> chunkChanges, boolean countBlock, Material oldTypeMaterial, Material pendingChangeType, BlockData pendingChangeData, RollbackCounters counters, BlockData rawBlockData, Material changeType, boolean changeBlock, BlockData changeBlockData, ArrayList<Object> meta, BlockData blockData, String rowUser, Material rowType, int rowX, int rowY, int rowZ, int rowTypeRaw, int rowData, int rowAction, int rowWorldId, String blockDataString, EntitySpawnRollbackHandler.Context context) {
        int unixtimestamp = (int) (System.currentTimeMillis() / 1000L);

        try {
            if (!changeBlock && isCommandBlock(rowType)) {
                return replayCommandMetadata(block, meta, countBlock);
            }
            if (changeBlock) {
                if (rowType == Material.AIR && oldTypeMaterial == Material.ARMOR_STAND) {
                    boolean clearEquipment = clearInventories && BlockGroup.CONTAINERS.contains(Material.ARMOR_STAND);
                    for (Entity entity : block.getChunk().getEntities()) {
                        if (!(entity instanceof ArmorStand)) {
                            continue;
                        }
                        Location location = entity.getLocation();
                        if (location.getBlockX() != rowX || location.getBlockY() != rowY || location.getBlockZ() != rowZ) {
                            continue;
                        }
                        if (clearEquipment) {
                            EntityEquipment equipment = ItemUtils.getEntityEquipment((ArmorStand) entity);
                            if (equipment != null) {
                                equipment.clear();
                            }
                        }
                        entity.remove();
                    }
                    return BlockReplayResult.applied(countBlock);
                }

                /* If modifying the head of a piston, update the base piston block to prevent it from being destroyed */
                BlockData effectiveSourceData = pendingChangeData != null ? pendingChangeData : changeBlockData;
                if (effectiveSourceData instanceof PistonHead) {
                    PistonHead pistonHead = (PistonHead) effectiveSourceData;
                    BlockFace facing = pistonHead.getFacing();
                    Material pistonType = pistonHead.getType() == TechnicalPiston.Type.STICKY ? Material.STICKY_PISTON : Material.PISTON;
                    updatePartner(bukkitWorld, rowX, rowY, rowZ, facing.getOppositeFace(), chunkChanges,
                            current -> retractMatchingPiston(current, pistonType, facing), false, counters, context);
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
                        return BlockReplayResult.applied(countBlock);
                    }

                    return BlockReplayResult.REJECTED;
                }

                if ((rowType == Material.AIR) && ((BukkitAdapter.ADAPTER.isItemFrame(oldTypeMaterial)) || (oldTypeMaterial == Material.PAINTING))) {
                    if (!HangingUtil.removeHanging(block.getState(), oldTypeMaterial, blockDataString)) {
                        return BlockReplayResult.REJECTED;
                    }
                }
                else if ((BukkitAdapter.ADAPTER.isItemFrame(rowType)) || (rowType == Material.PAINTING)) {
                    if (!restoreHanging(context, block, row, rowType, blockDataString, rowData, rollbackType, countBlock)) {
                        return BlockReplayResult.REJECTED;
                    }
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

                    return BlockReplayResult.applied(countBlock);
                }
                else if ((rowType == Material.AIR) && ((oldTypeMaterial == Material.SNOW))) {
                    BlockUtils.prepareTypeAndData(chunkChanges, block, rowType, blockData, true);
                    return BlockReplayResult.applied(countBlock);
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
                    if (clearInventories && BlockGroup.CONTAINERS.contains(changeType)) {
                        Inventory inventory = BlockUtils.getContainerInventory(block.getState(), false);
                        if (inventory != null) {
                            InventoryChangeListener.flushPendingContainer(inventory, block.getLocation());
                            inventory.clear();
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
                        BlockData removeBlockData = effectiveSourceData;
                        boolean physics = requiresPhysics(removeBlockData) || requiresPhysics(changeType);
                        if ((changeType == Material.NETHER_PORTAL) || removeBlockData instanceof MultipleFacing || removeBlockData instanceof Snow || removeBlockData instanceof Stairs || removeBlockData instanceof RedstoneWire || removeBlockData instanceof Chest) {
                            physics = true;
                        }
                        else if (rowType == Material.AIR && removeBlockData instanceof Bisected && !(removeBlockData instanceof TrapDoor)
                                && !BukkitAdapter.ADAPTER.isSingleBlockPitcherCrop(removeBlockData)) {
                            Bisected bisected = (Bisected) removeBlockData;
                            Material type = bisected.getMaterial();
                            Half half = bisected.getHalf();
                            updatePartner(bukkitWorld, rowX, rowY, rowZ, half == Half.TOP ? BlockFace.DOWN : BlockFace.UP, chunkChanges,
                                    current -> current instanceof Bisected && current.getMaterial() == type && ((Bisected) current).getHalf() != half
                                            ? BlockUtils.createBlockData(Material.AIR) : null, countBlock, counters, context);
                        }
                        else if (rowType == Material.AIR && removeBlockData instanceof Bed) {
                            Bed bed = (Bed) removeBlockData;
                            if (bed.getPart() == Part.FOOT) {
                                Material type = bed.getMaterial();
                                BlockFace facing = bed.getFacing();
                                updatePartner(bukkitWorld, rowX, rowY, rowZ, facing, chunkChanges,
                                        current -> isMatchingBedHead(current, type, facing) ? BlockUtils.createBlockData(Material.AIR) : null,
                                        false, counters, context);
                            }
                        }

                        BlockUtils.queueTypeAndData(chunkChanges, block, rowType, null, physics);
                    }

                    return BlockReplayResult.applied(countBlock);
                }
                else if ((rowType == Material.SPAWNER)) {
                    try {
                        BlockUtils.prepareTypeAndData(chunkChanges, block, rowType, blockData, false);
                        CreatureSpawner mobSpawner = (CreatureSpawner) block.getState();
                        mobSpawner.setSpawnedType(EntityUtils.getSpawnerType(rowData));
                        mobSpawner.update();

                        return BlockReplayResult.applied(countBlock);
                    }
                    catch (Exception e) {
                        ErrorReporter.report(e);
                        return BlockReplayResult.REJECTED;
                    }
                }
                else if (BlockUtils.isSkull(rowType)) {
                    BlockUtils.prepareTypeAndData(chunkChanges, block, rowType, blockData, false);
                    if (rowData > 0) {
                        Queue.queueSkullUpdate(rowUser, block.getState(), rowData);
                    }

                    return BlockReplayResult.applied(countBlock);
                }
                else if (BukkitAdapter.ADAPTER.isSign(rowType)) {// sign
                    BlockUtils.prepareTypeAndData(chunkChanges, block, rowType, blockData, false);
                    Queue.queueSignUpdate((Integer) row[2], block.getState(), rollbackType, (Integer) row[1]);

                    return BlockReplayResult.applied(countBlock);
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
                    return BlockReplayResult.APPLIED;
                }
                else if (isCommandBlock(rowType)) {
                    BlockUtils.prepareTypeAndData(chunkChanges, block, rowType, blockData, false);
                    return replayCommandMetadata(block, meta, countBlock);
                }
                else if ((rowType == Material.WATER)) {
                    if (bukkitWorld.getEnvironment() == World.Environment.NETHER) {
                        return BlockReplayResult.REJECTED;
                    }

                    if (pendingChangeData instanceof Waterlogged) {
                        Waterlogged waterlogged = (Waterlogged) pendingChangeData;
                        waterlogged.setWaterlogged(true);
                        BlockUtils.prepareTypeAndData(chunkChanges, block, null, waterlogged, false);
                    }
                    else {
                        BlockUtils.prepareTypeAndData(chunkChanges, block, rowType, blockData, false);
                    }

                    return BlockReplayResult.applied(countBlock);
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
                    chunkChanges.remove(block);
                    return BlockReplayResult.APPLIED;
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
                    chunkChanges.remove(block);
                    return BlockReplayResult.APPLIED;
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
                    return BlockReplayResult.APPLIED;
                }
                else if (rowType != changeType && (BlockGroup.CONTAINERS.contains(rowType) || BlockGroup.CONTAINERS.contains(changeType))) {
                    block.setType(Material.AIR); // Clear existing container to prevent errors

                    boolean isChest = (blockData instanceof Chest);
                    BlockUtils.prepareTypeAndData(chunkChanges, block, rowType, blockData, (isChest));
                    if (isChest) {
                        ChestTool.updateDoubleChest(block, blockData, false);
                    }

                    return BlockReplayResult.applied(countBlock);
                }
                else if (BlockGroup.UPDATE_STATE.contains(rowType) || rowType.name().contains("CANDLE")) {
                    BlockUtils.prepareTypeAndData(chunkChanges, block, rowType, blockData, true);
                    ChestTool.updateDoubleChest(block, blockData, true);
                    return BlockReplayResult.applied(countBlock);
                }
                else if (rowType != Material.AIR && rawBlockData instanceof Bisected && !(rawBlockData instanceof Stairs || rawBlockData instanceof TrapDoor)
                        && !BukkitAdapter.ADAPTER.isSingleBlockPitcherCrop(rawBlockData)) {
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
                    return BlockReplayResult.APPLIED;
                }
                else if (rowType != Material.AIR && rawBlockData instanceof Bed) {
                    Bed bed = (Bed) rawBlockData;
                    if (bed.getPart() == Part.FOOT) {
                        Bed bedData = (Bed) rawBlockData.clone();
                        bedData.setPart(Part.HEAD);
                        updatePartner(bukkitWorld, rowX, rowY, rowZ, bed.getFacing(), chunkChanges,
                                current -> current != null && BlockUtils.isAir(current.getMaterial()) ? bedData : null,
                                countBlock, counters, context);
                    }

                    BlockUtils.prepareTypeAndData(chunkChanges, block, rowType, blockData, true);
                    return BlockReplayResult.applied(countBlock);
                }
                else {
                    boolean physics = needsRollbackPhysics(rowType, oldTypeMaterial, changeType, pendingChangeType, blockData, rawBlockData, pendingChangeData, meta);
                    if (physics) {
                        BlockUtils.prepareTypeAndData(chunkChanges, block, rowType, blockData, true);
                    }
                    else {
                        BlockUtils.queueTypeAndData(chunkChanges, block, rowType, blockData, false);
                    }
                    return BlockReplayResult.applied(countBlock);
                }
            }
        }
        catch (Exception e) {
            ErrorReporter.report(e);
            return BlockReplayResult.REJECTED;
        }

        if ((rowType != Material.AIR) && changeBlock) {
            if (rowUser != null && !rowUser.isEmpty()) {
                CacheHandler.lookupCache.put(rowX + "." + rowY + "." + rowZ + "." + rowWorldId, new Object[] { unixtimestamp, rowUser, rowType });
            }
        }

        return BlockReplayResult.applied(countBlock);
    }

    private static boolean isCommandBlock(Material type) {
        return type == Material.COMMAND_BLOCK || type == Material.REPEATING_COMMAND_BLOCK || type == Material.CHAIN_COMMAND_BLOCK;
    }

    private static BlockReplayResult replayCommandMetadata(Block block, List<Object> metadata, boolean countBlock) {
        if (metadata != null) {
            for (int index = metadata.size() - 1; index >= 0; index--) {
                Object value = metadata.get(index);
                if (value instanceof String) {
                    String command = (String) value;
                    BlockState state = block.getState();
                    if (!(state instanceof CommandBlock)) {
                        return BlockReplayResult.REJECTED;
                    }
                    CommandBlock commandBlock = (CommandBlock) state;
                    if (!command.equals(commandBlock.getCommand())) {
                        commandBlock.setCommand(command);
                        return commandBlock.update() ? BlockReplayResult.COUNTED : BlockReplayResult.REJECTED;
                    }
                    break;
                }
            }
        }
        return BlockReplayResult.applied(countBlock);
    }

    private static boolean restoreHanging(EntitySpawnRollbackHandler.Context context, Block block, Object[] row, Material type,
            String blockData, int rowData, int rollbackType, boolean countBlock) {
        if (context == null) {
            return HangingUtil.spawnHanging(block.getState(), type, blockData, rowData);
        }
        AtomicBoolean started = new AtomicBoolean();
        CompletableFuture<Boolean> placement = HangingUtil.restoreHanging(block.getState(), type, blockData, rowData,
                () -> RollbackProcessor.beginDeferredMutation(context, started));
        if (!placement.isDone()) {
            RollbackProcessor.publishDeferredChange(context, started, placement, row, rollbackType, countBlock, false);
            return false;
        }
        try {
            return placement.join();
        }
        finally {
            if (started.get()) {
                context.endMutation();
            }
        }
    }

    enum BlockReplayResult {
        REJECTED, APPLIED, COUNTED;

        static BlockReplayResult applied(boolean count) {
            return count ? COUNTED : APPLIED;
        }

        boolean applied() {
            return this != REJECTED;
        }

        boolean counted() {
            return this == COUNTED;
        }
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
        if (type == Material.WATER || type == Material.LAVA || type == Material.FIRE || type == Material.TNT || type == Material.NETHER_PORTAL || type == Material.MOVING_PISTON || type == Material.SCAFFOLDING) {
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
        applyBlockChanges(chunkChanges, preview, user, block -> { });
    }

    static void applyBlockChanges(Map<Block, PendingBlockChange> chunkChanges, int preview, Player user, Consumer<Block> onApplied) {
        if (preview > 0) {
            if (user != null) {
                for (Entry<Block, PendingBlockChange> chunkChange : chunkChanges.entrySet()) {
                    Util.sendBlockChange(user, chunkChange.getKey().getLocation(), chunkChange.getValue().blockData());
                }
            }
        }
        else {
            applyBlockChanges(chunkChanges, true, true, onApplied);
            applyBlockChanges(chunkChanges, true, false, onApplied);
            applyBlockChanges(chunkChanges, false, false, onApplied);
            applyBlockChanges(chunkChanges, false, true, onApplied);
        }
        chunkChanges.clear();
    }

    private static void applyBlockChanges(Map<Block, PendingBlockChange> chunkChanges, boolean airChange, boolean applyPhysics, Consumer<Block> onApplied) {
        for (Entry<Block, PendingBlockChange> chunkChange : chunkChanges.entrySet()) {
            PendingBlockChange change = chunkChange.getValue();
            boolean changeToAir = isAirChange(change);
            if (changeToAir == airChange && change.applyPhysics() == applyPhysics) {
                BlockUtils.setTypeAndData(chunkChange.getKey(), null, change.blockData(), !changeToAir && applyPhysics);
                onApplied.accept(chunkChange.getKey());
            }
        }
    }

    private static BlockData retractMatchingPiston(BlockData current, Material type, BlockFace facing) {
        if (!(current instanceof Piston) || current.getMaterial() != type || ((Piston) current).getFacing() != facing || !((Piston) current).isExtended()) {
            return null;
        }
        Piston retracted = (Piston) current.clone();
        retracted.setExtended(false);
        return retracted;
    }

    private static boolean isMatchingBedHead(BlockData current, Material type, BlockFace facing) {
        return current instanceof Bed && current.getMaterial() == type && ((Bed) current).getPart() == Part.HEAD && ((Bed) current).getFacing() == facing;
    }

    private static void updatePartner(World world, int x, int y, int z, BlockFace offset, Map<Block, PendingBlockChange> chunkChanges,
            Function<BlockData, BlockData> transition, boolean countBlock, RollbackCounters counters, EntitySpawnRollbackHandler.Context context) {
        Location location = new Location(world, x + offset.getModX(), y + offset.getModY(), z + offset.getModZ());
        int chunkX = location.getBlockX() >> 4;
        int chunkZ = location.getBlockZ() >> 4;
        if (location.getBlockY() < BukkitAdapter.ADAPTER.getMinHeight(world) || location.getBlockY() >= world.getMaxHeight()) {
            return;
        }
        if (ownsPartner(world, chunkX, chunkZ)) {
            Block partner = world.getBlockAt(location);
            PendingBlockChange pending = chunkChanges.get(partner);
            BlockData current = pending != null ? pending.blockData() : partner.getBlockData();
            BlockData updated = transition.apply(current);
            if (updated != null) {
                BlockUtils.prepareTypeAndData(chunkChanges, partner, null, updated, false);
                if (countBlock) {
                    counters.addBlocks(1);
                }
            }
            return;
        }
        if (context == null || !world.isChunkLoaded(chunkX, chunkZ)) {
            return;
        }

        CompletableFuture<Boolean> completion = new CompletableFuture<>();
        context.addPending(completion);
        try {
            Scheduler.runTask(CoreProtect.getInstance(), () -> {
                if (!context.beginMutation()) {
                    completion.complete(false);
                    return;
                }
                try {
                    if (!ownsPartner(world, chunkX, chunkZ)) {
                        completion.complete(false);
                        return;
                    }
                    if (world.isChunkLoaded(chunkX, chunkZ)) {
                        Block partner = world.getBlockAt(location);
                        BlockData updated = transition.apply(partner.getBlockData());
                        if (updated != null) {
                            BlockUtils.setTypeAndData(partner, null, updated, false);
                            if (countBlock) {
                                RollbackCounters completed = new RollbackCounters();
                                completed.addBlocks(1);
                                context.addChunkCounts(completed);
                            }
                        }
                    }
                    completion.complete(true);
                }
                catch (RuntimeException e) {
                    ErrorReporter.report(e);
                    completion.complete(false);
                }
                finally {
                    context.endMutation();
                }
            }, location);
        }
        catch (RuntimeException e) {
            ErrorReporter.report(e);
            completion.complete(false);
        }
    }

    private static boolean ownsPartner(World world, int chunkX, int chunkZ) {
        return PaperAdapter.ADAPTER == null || PaperAdapter.ADAPTER.isOwnedByCurrentRegion(world, chunkX, chunkZ);
    }

    private static boolean isAirChange(PendingBlockChange change) {
        BlockData blockData = change.blockData();
        return blockData != null && BlockUtils.isAir(blockData.getMaterial());
    }
}
