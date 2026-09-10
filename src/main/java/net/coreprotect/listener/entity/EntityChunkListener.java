package net.coreprotect.listener.entity;

import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.world.EntitiesUnloadEvent;

import net.coreprotect.listener.player.InventoryChangeListener;
import net.coreprotect.utility.EntitySpawnTracking;

public final class EntityChunkListener implements Listener {

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        for (Entity entity : event.getEntities()) {
            EntitySpawnTracking.handleLoad(entity);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntitiesUnload(EntitiesUnloadEvent event) {
        for (Entity entity : event.getEntities()) {
            InventoryChangeListener.flushEntityContainer(entity);
            EntitySpawnTracking.handleUnload(entity);
        }
    }
}
