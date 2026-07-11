package net.coreprotect.listener.entity;

import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityRemoveEvent;

import net.coreprotect.consumer.Queue;
import net.coreprotect.listener.player.InventoryChangeListener;
import net.coreprotect.utility.EntitySpawnTracking;

public final class TrackedEntityRemoveListener implements Listener {

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityRemove(EntityRemoveEvent event) {
        Entity entity = event.getEntity();
        switch (event.getCause()) {
            case UNLOAD:
            case DEATH:
            case HIT:
                return;
            case PLUGIN:
                if (EntitySpawnTracking.isCoreProtectRemoval(entity.getUniqueId())) {
                    return;
                }
                break;
            default:
                break;
        }

        if (EntitySpawnTracking.isTracked(entity)) {
            InventoryChangeListener.flushEntityContainer(entity);
            Queue.queueEntitySpawnRemoved(entity.getUniqueId(), entity.getLocation());
            EntitySpawnTracking.forget(entity.getUniqueId());
        }
    }
}
