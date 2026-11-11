package net.coreprotect.listener.entity;

import org.bukkit.Location;
import org.bukkit.entity.minecart.ExplosiveMinecart;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityTeleportEvent;
import org.bukkit.event.entity.EntityPortalEvent;

import net.coreprotect.utility.EntitySpawnTracking;

public final class TrackedEntityTeleportListener implements Listener {

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityTeleport(EntityTeleportEvent event) {
        if (!changesWorld(event)) {
            return;
        }

        VehicleDestroyListener.cancelTntRemoval(event.getEntity());
        EntitySpawnTracking.handleTeleport(event.getEntity(), event.getTo());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityPortal(EntityPortalEvent event) {
        if (event.getEntity() instanceof ExplosiveMinecart && changesWorld(event)) {
            VehicleDestroyListener.cancelTntRemoval(event.getEntity());
        }
    }

    private static boolean changesWorld(EntityTeleportEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        return from.getWorld() != null && to != null && to.getWorld() != null && !from.getWorld().getUID().equals(to.getWorld().getUID());
    }
}
