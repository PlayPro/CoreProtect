package net.coreprotect.listener.player;

import net.coreprotect.config.Config;
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.consumer.Queue;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerBucketFillEvent;

public final class PlayerBucketFillListener extends Queue implements Listener {

    @EventHandler(priority = EventPriority.HIGHEST)
    protected void onPlayerBucketFill(PlayerBucketFillEvent event) {
        String player = event.getPlayer().getName();
        Block block = event.getBlockClicked();
        World world = block.getWorld();
        Material type = block.getType();

        int inspect = 0;
        if (ConfigHandler.inspecting.get(player) != null) {
            if (ConfigHandler.inspecting.get(player)) {
                inspect = 1;
                event.setCancelled(true);
            }
        }

        if (!event.isCancelled() && Config.getConfig(world).BUCKETS && inspect == 0) {
            BlockData blockData = block.getBlockData();
            if (blockData instanceof Waterlogged) {
                Waterlogged waterlogged = (Waterlogged) blockData;
                if (waterlogged.isWaterlogged()) {
                    type = Material.WATER;
                }
            }

            Queue.queueBlockBreak(player, block.getState(), type, block.getBlockData().getAsString(), 0);
        }
    }
}
