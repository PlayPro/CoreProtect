package net.coreprotect.listener.block;

import java.util.Set;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.FaceAttachable;
import org.bukkit.block.data.MultipleFacing;
import org.bukkit.block.data.Rail;
import org.bukkit.block.data.Rail.Shape;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.block.data.type.Bed;
import org.bukkit.block.data.type.Bell;
import org.bukkit.block.data.type.Lantern;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

import net.coreprotect.bukkit.BukkitAdapter;
import net.coreprotect.config.Config;
import net.coreprotect.consumer.Queue;
import net.coreprotect.database.Database;
import net.coreprotect.model.BlockGroup;
import net.coreprotect.model.SignState;
import net.coreprotect.model.action.SignActions;
import net.coreprotect.paper.PaperAdapter;
import net.coreprotect.thread.CacheHandler;
import net.coreprotect.utility.BlockUtils;
import net.coreprotect.utility.WorldUtils;
import net.coreprotect.utility.ErrorReporter;

public final class BlockBreakListener extends Queue implements Listener {

    static boolean isAttached(Block block, Block scanBlock, int scanMin) {
        return isAttached(block, scanBlock, scanBlock.getBlockData(), scanMin);
    }

    static boolean isAttached(Block block, Block scanBlock, BlockData blockData, int scanMin) {
        if (blockData instanceof Bell) {
            Bell bell = (Bell) blockData;
            switch (bell.getAttachment()) {
                case SINGLE_WALL:
                    return scanMin < BlockUtil.TOP && scanBlock.getRelative(bell.getFacing()).getLocation().equals(block.getLocation());
                case FLOOR:
                    return scanMin == BlockUtil.TOP;
                case CEILING:
                    return scanMin == BlockUtil.BOTTOM;
                default:
                    return false;
            }
        }
        if (blockData instanceof Rail && scanMin < BlockUtil.TOP) {
            Shape shape = ((Rail) blockData).getShape();
            return (scanMin == 1 && shape == Shape.ASCENDING_WEST)
                    || (scanMin == 2 && shape == Shape.ASCENDING_EAST)
                    || (scanMin == 3 && shape == Shape.ASCENDING_NORTH)
                    || (scanMin == 4 && shape == Shape.ASCENDING_SOUTH);
        }
        if (blockData instanceof Directional && !(blockData instanceof Bisected || blockData instanceof FaceAttachable) && scanMin < BlockUtil.TOP) {
            Directional directional = (Directional) blockData;
            BlockFace blockFace = directional.getFacing();
            if (blockData instanceof Bed) {
                if (block.getType() != scanBlock.getType()) {
                    return false;
                }
                blockFace = ((Bed) blockData).getPart() == Bed.Part.FOOT ? blockFace.getOppositeFace() : blockFace;
            }
            return scanBlock.getRelative(blockFace.getOppositeFace()).getLocation().equals(block.getLocation());
        }
        else if (blockData instanceof MultipleFacing) {
            MultipleFacing multipleFacing = (MultipleFacing) blockData;
            for (BlockFace blockFace : multipleFacing.getFaces()) {
                boolean adjacent = scanBlock.getRelative(blockFace).getLocation().equals(block.getLocation());
                if (adjacent) {
                    return true;
                }
            }

            return false;
        }
        else if (blockData instanceof Lantern) {
            boolean scan = false;
            switch (scanMin) {
                case BlockUtil.TOP:
                    scan = !((Lantern) blockData).isHanging();
                    break;
                case BlockUtil.BOTTOM:
                    scan = ((Lantern) blockData).isHanging();
                    break;
                default:
                    break;
            }

            return scan;
        }
        else if (!BukkitAdapter.ADAPTER.isAttached(block, scanBlock, blockData, scanMin)
                || !PaperAdapter.ADAPTER.isAttached(block, scanBlock, blockData, scanMin)) {
            return false;
        }

        return true;
    }

    private static boolean isPiston(Material type) {
        return type == Material.PISTON || type == Material.STICKY_PISTON;
    }

    protected static void processBlockBreak(Player player, String user, Block block, boolean logBreak, int skipScan) {
        processBlockBreak(player, user, block, logBreak, skipScan, null);
    }

    static void processBlockBreak(Player player, String user, Block block, boolean logBreak, int skipScan, Set<Block> recordedBlocks) {
        Material type = block.getType();
        World world = block.getWorld();
        int x = block.getX();
        int y = block.getY();
        int z = block.getZ();

        Location[] locationMap = new Location[6];
        locationMap[0] = new Location(world, x + 1, y, z);
        locationMap[1] = new Location(world, x - 1, y, z);
        locationMap[2] = new Location(world, x, y, z + 1);
        locationMap[3] = new Location(world, x, y, z - 1);
        locationMap[4] = new Location(world, x, y + 1, z);
        locationMap[5] = new Location(world, x, y - 1, z);

        int scanMin = 1;
        int scanMax = 8;
        if (!Config.getConfig(world).NATURAL_BREAK) {
            scanMin = 7;
        }
        if (!logBreak) { // log base block breakage
            scanMax = 7;
        }
        while (scanMin < scanMax) {
            Block blockLog = block;
            boolean log = true;

            if (scanMin == skipScan) {
                scanMin++;
                continue;
            }

            if (scanMin < 7) {
                Location scanLocation = locationMap[scanMin - 1];
                Block scanBlock = world.getBlockAt(scanLocation);
                if (recordedBlocks != null && recordedBlocks.contains(scanBlock)) {
                    scanMin++;
                    continue;
                }
                Material scanType = scanBlock.getType();
                if (scanType == Material.SCAFFOLDING) {
                    if (scanMin == BlockUtil.TOP || (type == Material.SCAFFOLDING && scanMin < BlockUtil.TOP)) {
                        BlockFadeListener.trackScaffoldingBreak(user, world, scanLocation.getBlockX(), scanLocation.getBlockY(), scanLocation.getBlockZ());
                    }
                    scanMin++;
                    continue;
                }
                if (scanMin == BlockUtil.TOP && (scanType.hasGravity() || BukkitAdapter.ADAPTER.isSuspiciousBlock(scanType)) && Config.getConfig(world).BLOCK_MOVEMENT) {
                    // FallingBlock listeners read lookupCache to attribute the chain reaction; overwrite the cache up the gravity stack so falls record under whoever broke this block instead of #gravity or the original placer.
                    seedGravityFallAttribution(user, world, x, z, y + 1, scanType);
                }
                if (BlockGroup.TRACK_ANY.contains(scanType)) {
                    log = scanType == Material.PISTON_HEAD ? isPiston(type) : isAttached(block, scanBlock, scanMin);
                }
                else if (scanMin < BlockUtil.TOP) {
                    log = BlockGroup.TRACK_SIDE.contains(scanType) && isAttached(block, scanBlock, scanMin);
                }
                else if (BlockUtil.verticalBreakScan(player, user, block, scanBlock, scanType, scanMin, recordedBlocks)) {
                    log = false;
                }
                else {
                    Set<Material> tracked = scanMin == BlockUtil.TOP ? BlockGroup.TRACK_TOP : BlockGroup.TRACK_BOTTOM;
                    log = (tracked.contains(scanType) || BlockGroup.TRACK_TOP_BOTTOM.contains(scanType)) && isAttached(block, scanBlock, scanMin);
                }
                if (!log && type == Material.PISTON_HEAD && isPiston(scanType)) {
                    log = true;
                }
                if (log) {
                    blockLog = scanBlock;
                    if (recordedBlocks != null) {
                        recordedBlocks.add(blockLog);
                    }
                }
            }

            if (!log) {
                scanMin++;
                continue;
            }

            Material blockType = blockLog.getType();
            BlockState blockState = blockLog.getState();
            if (blockState instanceof Sign && Config.getConfig(world).SIGN_TEXT) {
                try {
                    Queue.queueSignText(user, blockState.getLocation(), SignActions.BREAK, SignState.capture((Sign) blockState), true, 5);
                }
                catch (Exception e) {
                    ErrorReporter.report(e);
                }
            }

            Database.containerBreakCheck(user, blockType, blockLog, null, blockLog.getLocation());
            Queue.queueBlockBreak(user, blockState, blockType, blockState.getBlockData().getAsString(), type, 0, scanMin);

            if (player != null && BlockUtils.iceBreakCheck(blockState, user, blockType)) {
                ItemStack handItem = player.getInventory().getItemInMainHand();
                if (!(player.getGameMode().equals(GameMode.CREATIVE)) && !(handItem != null && handItem.containsEnchantment(Enchantment.SILK_TOUCH))) {
                    Queue.queueBlockPlaceValidate(user, blockState, blockLog, null, Material.WATER, -1, 0, null, 0);
                }
            }

            BlockData blockDataB1 = blockState.getBlockData();
            if (blockDataB1 instanceof Waterlogged) {
                Waterlogged waterlogged = (Waterlogged) blockDataB1;
                if (waterlogged.isWaterlogged()) {
                    Queue.queueBlockPlace(user, blockState, blockLog.getType(), null, Material.WATER, -1, 0, null);
                }
            }

            scanMin++;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    protected void onBlockBreak(BlockBreakEvent event) {
        if (!event.isCancelled()) {
            String user = event.getPlayer().getName();
            Block block = event.getBlock();
            processBlockBreak(event.getPlayer(), user, block, Config.getConfig(block.getWorld()).BLOCK_BREAK, BlockUtil.NONE);
        }
    }

    private static void seedGravityFallAttribution(String user, World world, int x, int z, int startY, Material startType) {
        int worldId = WorldUtils.getWorldId(world.getName());
        int timestamp = (int) (System.currentTimeMillis() / 1000L);
        int worldMax = world.getMaxHeight();
        Material currentType = startType;
        int scanY = startY;
        while (scanY < worldMax) {
            String coordKey = x + "." + scanY + "." + z + "." + worldId;
            CacheHandler.lookupCache.put(coordKey, new Object[] { timestamp, user, currentType });
            scanY++;
            if (scanY >= worldMax) {
                break;
            }
            currentType = world.getBlockAt(x, scanY, z).getType();
            if (!currentType.hasGravity() && !BukkitAdapter.ADAPTER.isSuspiciousBlock(currentType)) {
                break;
            }
        }
    }

}
