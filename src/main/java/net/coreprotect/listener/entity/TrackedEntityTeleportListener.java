package net.coreprotect.listener.entity;

import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityTeleportEvent;

import net.coreprotect.utility.EntitySpawnTracking;

public final class TrackedEntityTeleportListener implements Listener {

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityTeleport(EntityTeleportEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (from.getWorld() == null || to == null || to.getWorld() == null || from.getWorld().getUID().equals(to.getWorld().getUID())) {
            return;
        }

        EntitySpawnTracking.handleTeleport(event.getEntity(), to);
    }
}
