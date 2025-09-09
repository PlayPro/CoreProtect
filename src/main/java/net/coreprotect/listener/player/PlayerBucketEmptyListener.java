package net.coreprotect.listener.player;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import net.coreprotect.config.Config;
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.consumer.Queue;
import net.coreprotect.thread.CacheHandler;
import net.coreprotect.utility.WorldUtils;

public final class PlayerBucketEmptyListener extends Queue implements Listener {

    @EventHandler(priority = EventPriority.HIGHEST)
    protected void onPlayerBucketEmpty(org.bukkit.event.player.PlayerBucketEmptyEvent event) {
        String player = event.getPlayer().getName();
        World world = event.getPlayer().getWorld();
        int inspect = 0;

        if (ConfigHandler.inspecting.get(player) != null) {
            if (ConfigHandler.inspecting.get(player)) {
                inspect = 1;
                event.setCancelled(true);
            }
        }

        if (!event.isCancelled() && Config.getConfig(world).BUCKETS && inspect == 0) {
            Block block = event.getBlockClicked();
            BlockData blockData = block.getBlockData();
            Material type = Material.WATER;
            if (event.getBucket().equals(Material.LAVA_BUCKET)) {
                type = Material.LAVA;
            }

            boolean getRelative = true;
            if (blockData instanceof Waterlogged) {
                if (type.equals(Material.WATER)) {
                    boolean isWaterlogged = ((Waterlogged) blockData).isWaterlogged();
                    if (!isWaterlogged) {
                        getRelative = false;
                    }
                }
            }
            if (getRelative) {
                block = block.getRelative(event.getBlockFace());
                blockData = block.getBlockData();
            }

            BlockState blockState = block.getState();
            int worldId = WorldUtils.getWorldId(block.getWorld().getName());
            int unixTimestamp = (int) (System.currentTimeMillis() / 1000L);

            if (type.equals(Material.WATER)) {
                if (blockData instanceof Waterlogged) {
                    blockState = null;
                }
            }

            CacheHandler.lookupCache.put("" + block.getX() + "." + block.getY() + "." + block.getZ() + "." + worldId + "", new Object[] { unixTimestamp, player, type });
            queueBlockPlace(player, block.getState(), block.getType(), blockState, type, 1, 1, null);
        }
    }
}
