package net.coreprotect.listener.entity;

import org.bukkit.block.Block;
import org.bukkit.entity.Hanging;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.hanging.HangingPlaceEvent;

import net.coreprotect.config.Config;
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.consumer.Queue;
import net.coreprotect.utility.entity.HangingUtil;

public final class HangingPlaceListener implements Listener {

    @EventHandler(priority = EventPriority.HIGHEST)
    protected void onInspectHangingPlace(HangingPlaceEvent event) {
        Player player = event.getPlayer();
        if (player != null && HangingUtil.isSupportedDecoration(event.getEntity())
                && Boolean.TRUE.equals(ConfigHandler.inspecting.get(player.getName()))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    protected void onHangingPlace(HangingPlaceEvent event) {
        if (event.isCancelled() || event.getPlayer() == null) {
            return;
        }
        Hanging entity = event.getEntity();
        Player player = event.getPlayer();

        if (HangingUtil.isSupportedDecoration(entity)) {
            Block blockEvent = entity.getLocation().getBlock();
            if (Config.getConfig(blockEvent.getWorld()).BLOCK_PLACE) {
                Queue.queueBlockPlace(player.getName(), blockEvent.getState(), blockEvent.getType(), null, HangingUtil.getMaterial(entity), HangingUtil.getArtId(entity), 1,
                        HangingUtil.getFacingData(entity));
            }
        }
    }
}
