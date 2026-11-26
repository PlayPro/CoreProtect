package net.coreprotect.listener.block;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.Bisected.Half;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Lightable;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.block.data.type.Stairs;
import org.bukkit.block.data.type.TrapDoor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockMultiPlaceEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;

import net.coreprotect.bukkit.BukkitAdapter;
import net.coreprotect.config.Config;
import net.coreprotect.consumer.Queue;
import net.coreprotect.model.BlockGroup;
import net.coreprotect.model.SignState;
import net.coreprotect.model.action.SignActions;
import net.coreprotect.thread.CacheHandler;
import net.coreprotect.utility.BlockUtils;
import net.coreprotect.utility.ErrorReporter;

public final class BlockPlaceListener extends Queue implements Listener {

    @EventHandler(priority = EventPriority.MONITOR)
    protected void onBlockPlace(BlockPlaceEvent event) {
        World world = event.getBlockPlaced().getWorld();
        if (!event.isCancelled() && Config.getConfig(world).BLOCK_PLACE) {
            Player player = event.getPlayer();
            Block blockPlaced = event.getBlockPlaced();
            String bBlockData = null;
            BlockState blockReplaced = event.getBlockReplacedState();
            Material blockType = blockPlaced.getType();
            boolean abort = false;

            if (blockType == Material.LECTERN && blockReplaced.getType() == Material.LECTERN) {
                // Logged by PlayerInteractListener; prevent a false lectern block-place record.
                abort = true;
            }
            else if (BlockGroup.CONTAINERS.contains(blockType) || BlockGroup.DIRECTIONAL_BLOCKS.contains(blockType) || blockType.name().endsWith("_STAIRS")) {
                BlockData blockData = blockPlaced.getBlockData();
                Waterlogged waterlogged = BlockUtils.checkWaterlogged(blockData, blockReplaced);
                if (waterlogged != null) {
                    bBlockData = waterlogged.getAsString();
                    blockReplaced = null;
                }
                Queue.queueBlockPlaceDelayed(player.getName(), blockPlaced.getLocation(), blockPlaced.getType(), bBlockData, blockReplaced, 0);
                abort = true;
            }
            else if (BlockGroup.FIRE.contains(blockType)) {
                ItemStack item = event.getItemInHand();
                Material itemType = item.getType();

                if (!BlockGroup.FIRE.contains(itemType)) {
                    abort = true;
                }
            }
            else if (BlockGroup.LIGHTABLES.contains(blockType) && blockType == blockReplaced.getType()) {
                // Lighting blocks is logged in BlockIgniteListener, extinguishing is logged in PlayerInteractListener
                BlockData blockPlacedData = blockPlaced.getBlockData();
                BlockData blockReplacedData = blockReplaced.getBlockData();
                if (blockPlacedData instanceof Lightable && blockReplacedData instanceof Lightable && ((Lightable) blockPlacedData).isLit() != ((Lightable) blockReplacedData).isLit()) {
                    abort = true;
                }
            }

            if (!abort) {
                BlockState blockState = blockPlaced.getState();
                BlockData blockData = blockState.getBlockData();
                if (blockData instanceof Bisected && !(blockData instanceof Stairs || blockData instanceof TrapDoor)
                        && ((Bisected) blockData).getHalf() == Half.TOP && blockPlaced.getY() > BukkitAdapter.ADAPTER.getMinHeight(world)) {
                    BlockState lower = world.getBlockAt(blockPlaced.getX(), blockPlaced.getY() - 1, blockPlaced.getZ()).getState();
                    BlockData lowerData = lower.getBlockData();
                    if (lower.getType() == blockType && lowerData instanceof Bisected && ((Bisected) lowerData).getHalf() == Half.BOTTOM) {
                        blockState = lower;
                        blockData = lowerData;
                        blockReplaced = null;
                        if (event instanceof BlockMultiPlaceEvent) {
                            for (BlockState replaced : ((BlockMultiPlaceEvent) event).getReplacedBlockStates()) {
                                if (replaced.getX() == lower.getX() && replaced.getY() == lower.getY() && replaced.getZ() == lower.getZ()) {
                                    blockReplaced = replaced;
                                    break;
                                }
                            }
                        }
                    }
                }

                Waterlogged waterlogged = blockReplaced == null ? null : BlockUtils.checkWaterlogged(blockData, blockReplaced);
                if (waterlogged != null) {
                    bBlockData = waterlogged.getAsString();
                    blockReplaced = null;
                }

                if (blockType.hasGravity() || BlockUtil.isFloatingScaffolding(blockData)) {
                    CacheHandler.recordBlockSource(world, blockState.getX(), blockState.getY(), blockState.getZ(), player.getName(), blockType);
                }

                Queue.queueBlockPlace(player.getName(), blockState, blockPlaced.getType(), blockReplaced, null, -1, 0, bBlockData);

                if (blockState instanceof Sign && Config.getConfig(world).SIGN_TEXT) {
                    try {
                        SignState sign = SignState.capture((Sign) blockState);
                        if (sign.hasText()) {
                            Queue.queueSignText(player.getName(), blockState.getLocation(), SignActions.PLACE, sign, true, 0);
                        }
                    }
                    catch (Exception e) {
                        ErrorReporter.report(e);
                    }
                }
            }
        }
    }

}
