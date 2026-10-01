package net.coreprotect.listener.entity;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPlaceEvent;

import net.coreprotect.config.Config;
import net.coreprotect.consumer.Queue;
import net.coreprotect.utility.EntitySpawnTracking;

public final class EntityPlaceListener extends Queue implements Listener {

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityPlace(EntityPlaceEvent event) {
        logPlacement(event.getEntity(), event.getPlayer());
    }

    public static void logPlacement(Entity entity, Player player) {
        if (!EntitySpawnTracking.isPlacedEntity(entity) || EntitySpawnTracking.isTracked(entity) || !Config.getConfig(entity.getWorld()).ENTITY_SPAWNS) {
            return;
        }

        String user = player == null ? "#dispenser" : player.getName();
        EntitySpawnTracking.track(entity);
        Queue.queueEntitySpawnLog(user, entity.getUniqueId(), entity.getType(), entity.getLocation());
    }
}
