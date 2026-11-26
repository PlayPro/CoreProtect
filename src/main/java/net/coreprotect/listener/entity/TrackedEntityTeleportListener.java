package net.coreprotect.listener.entity;

import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.entity.EntityPortalExitEvent;
import org.bukkit.event.entity.EntityTeleportEvent;

import net.coreprotect.utility.EntitySpawnTracking;

public final class TrackedEntityTeleportListener implements Listener {

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityTeleport(EntityTeleportEvent event) {
        if (event.isCancelled() || !changesWorld(event)) {
            return;
        }

        VehicleDestroyListener.cancelTntRemoval(event.getEntity());
        EntitySpawnTracking.handleTeleport(event.getEntity(), event.getTo());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityPortal(EntityPortalEvent event) {
        onEntityTeleport(event);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityPortalExit(EntityPortalExitEvent event) {
        onEntityTeleport(event);
    }

    private static boolean changesWorld(EntityTeleportEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        return from != null && from.getWorld() != null && to != null && to.getWorld() != null && !from.getWorld().getUID().equals(to.getWorld().getUID());
    }
}
