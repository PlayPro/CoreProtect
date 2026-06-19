package net.coreprotect.listener.player;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerShearEntityEvent;

import net.coreprotect.listener.entity.SulfurCubeShearLogger;

public final class PlayerShearEntityListener implements Listener {

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    protected void onPlayerShearEntity(PlayerShearEntityEvent event) {
        SulfurCubeShearLogger.logDrops(event.getEntity(), event.getPlayer().getName(), SulfurCubeShearLogger.getDrops(event));
    }
}
