package net.coreprotect.listener.player;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerBucketEmptyEvent;

import net.coreprotect.bukkit.BukkitAdapter;
import net.coreprotect.config.Config;
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.consumer.Queue;

public final class PlayerBucketEmptyListener implements Listener {

    @EventHandler(priority = EventPriority.LOWEST)
    protected void inspectBucketEmpty(PlayerBucketEmptyEvent event) {
        if (Boolean.TRUE.equals(ConfigHandler.inspecting.get(event.getPlayer().getName()))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    protected void onPlayerBucketEmpty(PlayerBucketEmptyEvent event) {
        Block block = event.getBlock();
        if (!event.isCancelled() && Config.getConfig(block.getWorld()).BUCKETS) {
            Material type = event.getBucket() == Material.LAVA_BUCKET ? Material.LAVA : BukkitAdapter.ADAPTER.getBucketContents(event.getBucket());
            if (type == null || type == Material.AIR) {
                type = Material.WATER;
            }
            BlockState blockState = block.getState();
            Queue.queueBlockPlaceValidate(event.getPlayer().getName(), blockState, block, blockState, type, 1, 1, null, 0, true);
        }
    }
}
