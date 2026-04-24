package net.coreprotect.listener.block;

import java.util.Locale;

import org.bukkit.Location;
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
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;

import net.coreprotect.bukkit.BukkitAdapter;
import net.coreprotect.config.Config;
import net.coreprotect.consumer.Queue;
import net.coreprotect.model.BlockGroup;
import net.coreprotect.paper.PaperAdapter;
import net.coreprotect.thread.CacheHandler;
import net.coreprotect.utility.BlockUtils;
import net.coreprotect.utility.MaterialUtils;
import net.coreprotect.utility.WorldUtils;

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
            else if (MaterialUtils.listContains(BlockGroup.CONTAINERS, blockType) || MaterialUtils.listContains(BlockGroup.DIRECTIONAL_BLOCKS, blockType) || blockType.name().toUpperCase(Locale.ROOT).endsWith("_STAIRS")) {
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
                BlockData blockData = blockPlaced.getBlockData();
                Waterlogged waterlogged = BlockUtils.checkWaterlogged(blockData, blockReplaced);
                if (waterlogged != null) {
                    bBlockData = waterlogged.getAsString();
                    blockReplaced = null;
                }

                // fix for placed bisected blocks randomly returning the top or bottom of the block in this event
                BlockState blockState = blockPlaced.getState();
                if (blockState.getBlockData() instanceof Bisected && !(blockState.getBlockData() instanceof Stairs || blockState.getBlockData() instanceof TrapDoor)) {
                    if (((Bisected) blockState.getBlockData()).getHalf().equals(Half.TOP)) {
                        if (blockPlaced.getY() > BukkitAdapter.ADAPTER.getMinHeight(world)) {
                            blockState = blockPlaced.getWorld().getBlockAt(blockPlaced.getX(), blockPlaced.getY() - 1, blockPlaced.getZ()).getState();
                        }
                    }
                }

                if (blockType.hasGravity()) {
                    // FallingBlock listeners read lookupCache before the async consumer can populate it; record the placer synchronously so the fall and landing get attributed to the player instead of #gravity.
                    int worldId = WorldUtils.getWorldId(world.getName());
                    int timestamp = (int) (System.currentTimeMillis() / 1000L);
                    String coordKey = blockPlaced.getX() + "." + blockPlaced.getY() + "." + blockPlaced.getZ() + "." + worldId;
                    CacheHandler.lookupCache.put(coordKey, new Object[] { timestamp, player.getName(), blockType });
                }

                Queue.queueBlockPlace(player.getName(), blockState, blockPlaced.getType(), blockReplaced, null, -1, 0, bBlockData);

                if (BukkitAdapter.ADAPTER.isSign(blockType)) {
                    if (Config.getConfig(world).SIGN_TEXT) {
                        try {
                            Location location = blockState.getLocation();
                            Sign sign = (Sign) blockState;
                            String line1 = PaperAdapter.ADAPTER.getLine(sign, 0);
                            String line2 = PaperAdapter.ADAPTER.getLine(sign, 1);
                            String line3 = PaperAdapter.ADAPTER.getLine(sign, 2);
                            String line4 = PaperAdapter.ADAPTER.getLine(sign, 3);
                            String line5 = PaperAdapter.ADAPTER.getLine(sign, 4);
                            String line6 = PaperAdapter.ADAPTER.getLine(sign, 5);
                            String line7 = PaperAdapter.ADAPTER.getLine(sign, 6);
                            String line8 = PaperAdapter.ADAPTER.getLine(sign, 7);

                            boolean isFront = true;
                            int color = BukkitAdapter.ADAPTER.getColor(sign, isFront);
                            int colorSecondary = BukkitAdapter.ADAPTER.getColor(sign, !isFront);
                            boolean frontGlowing = BukkitAdapter.ADAPTER.isGlowing(sign, isFront);
                            boolean backGlowing = BukkitAdapter.ADAPTER.isGlowing(sign, !isFront);
                            boolean isWaxed = BukkitAdapter.ADAPTER.isWaxed(sign);

                            if (line1.length() > 0 || line2.length() > 0 || line3.length() > 0 || line4.length() > 0 || line5.length() > 0 || line6.length() > 0 || line7.length() > 0 || line8.length() > 0) {
                                Queue.queueSignText(player.getName(), location, 1, color, colorSecondary, frontGlowing, backGlowing, isWaxed, isFront, line1, line2, line3, line4, line5, line6, line7, line8, 0);
                            }
                        }
                        catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        }
    }

}
