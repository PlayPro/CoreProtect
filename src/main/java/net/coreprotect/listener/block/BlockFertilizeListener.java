package net.coreprotect.listener.block;

import java.util.List;
import java.util.Locale;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockFertilizeEvent;

import net.coreprotect.config.Config;
import net.coreprotect.consumer.Queue;
import net.coreprotect.thread.CacheHandler;

public final class BlockFertilizeListener extends Queue implements Listener {

    @EventHandler(priority = EventPriority.MONITOR)
    protected void onBlockFertilize(BlockFertilizeEvent event) {
        if (event.isCancelled()) {
            return;
        }

        Block block = event.getBlock();
        if (!Config.getConfig(block.getWorld()).BLOCK_PLACE) {
            return;
        }

        Location location = block.getLocation();
        List<BlockState> blocks = event.getBlocks();

        if (Tag.SAPLINGS.isTagged(block.getType()) && (!Config.getConfig(location.getWorld()).TREE_GROWTH || (blocks.size() == 1 && blocks.get(0).getLocation().equals(location)))) {
            return;
        }
        if (block.getType().name().toLowerCase(Locale.ROOT).contains("mushroom") && (!Config.getConfig(location.getWorld()).MUSHROOM_GROWTH || (blocks.size() == 1 && blocks.get(0).getLocation().equals(location)))) {
            return;
        }
        if (block.getType() == Material.AIR && blocks.size() > 1 && Tag.LOGS.isTagged(blocks.get(1).getType()) && !Config.getConfig(location.getWorld()).TREE_GROWTH) {
            return;
        }

        String user = "#bonemeal";
        Player player = event.getPlayer();
        if (player != null) {
            user = player.getName();
        }
        else {
            Object[] data = CacheHandler.redstoneCache.get(location);
            if (data != null) {
                long newTime = System.currentTimeMillis();
                long oldTime = (long) data[0];
                if ((newTime - oldTime) < 50) { // check that within same tick
                    user = (String) data[1];
                }

                CacheHandler.redstoneCache.remove(location);
            }
        }

        for (BlockState newBlock : blocks) {
            Queue.queueBlockPlace(user, newBlock, newBlock.getType(), newBlock.getBlock().getState(), newBlock.getType(), -1, 0, newBlock.getBlockData().getAsString());
        }
    }

}
