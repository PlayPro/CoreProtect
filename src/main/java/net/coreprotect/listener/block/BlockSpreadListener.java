package net.coreprotect.listener.block;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockSpreadEvent;

import net.coreprotect.config.Config;
import net.coreprotect.consumer.Queue;
import net.coreprotect.model.BlockGroup;
import net.coreprotect.thread.CacheHandler;

public final class BlockSpreadListener extends Queue implements Listener {

    @EventHandler(priority = EventPriority.MONITOR)
    protected void onBlockSpread(BlockSpreadEvent event) {
        // mushrooms, fire

        /* To-do: Improve configuration
         *
         * # Track when a block changes states, such as from natural block growth.
         * block-change: true
         *
         */

        if (!event.isCancelled() && Config.getConfig(event.getBlock().getWorld()).VINE_GROWTH) {
            BlockState blockstate = event.getNewState();
            Material type = blockstate.getType();
            if (!BlockGroup.VINES.contains(type) && !BlockGroup.AMETHYST.contains(type) && type != Material.CHORUS_FLOWER && type != Material.BAMBOO) {
                return;
            }

            Block block = event.getBlock();
            Location location = block.getLocation();
            int timestamp = (int) (System.currentTimeMillis() / 1000L);
            Object[] cacheData = CacheHandler.spreadCache.get(location);
            CacheHandler.spreadCache.put(location, new Object[] { timestamp, type });
            if (cacheData != null && ((Material) cacheData[1]) == type) {
                return;
            }

            if (BlockGroup.VINES.contains(type)) {
                queueBlockPlace("#vine", block.getState(), block.getType(), null, type, -1, 0, blockstate.getBlockData().getAsString());
            }
            else if (BlockGroup.AMETHYST.contains(type)) {
                queueBlockPlace("#amethyst", block.getState(), block.getType(), block.getState(), type, -1, 0, blockstate.getBlockData().getAsString());
            }
            else if (type.equals(Material.CHORUS_FLOWER)) {
                Block sourceBlock = event.getSource();
                Queue.queueBlockPlaceDelayed("#chorus", sourceBlock.getLocation(), sourceBlock.getType(), null, sourceBlock.getState(), 0);
                Queue.queueBlockPlaceDelayed("#chorus", block.getLocation(), block.getType(), null, block.getState(), 0);
            }
            else if (type.equals(Material.BAMBOO)) {
                Block sourceBlock = event.getSource();
                Location below = sourceBlock.getLocation().clone();
                below.setY(below.getY() - 2);
                for (int i = 0; i < 2; i++) {
                    if (below.getY() >= 0) {
                        Block belowBlock = below.getBlock();
                        if (belowBlock.getType().equals(Material.BAMBOO)) {
                            Queue.queueBlockPlaceDelayed("#bamboo", belowBlock.getLocation(), belowBlock.getType(), null, belowBlock.getState(), 0);
                        }
                    }
                    below.setY(below.getY() + 1);
                }

                Queue.queueBlockPlaceDelayed("#bamboo", sourceBlock.getLocation(), sourceBlock.getType(), null, sourceBlock.getState(), 0);
                Queue.queueBlockPlaceDelayed("#bamboo", block.getLocation(), block.getType(), null, block.getState(), 0);
            }
        }
    }

}
