package net.coreprotect.paper.listener;

import org.bukkit.entity.Entity;
import org.bukkit.entity.minecart.ExplosiveMinecart;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;

import net.coreprotect.listener.entity.VehicleDestroyListener;

public final class LegacyVehicleRemoveListener implements Listener {

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityRemove(EntityRemoveFromWorldEvent event) {
        Entity entity = event.getEntity();
        if (entity instanceof ExplosiveMinecart) {
            if (entity.isDead()) {
                VehicleDestroyListener.completeTntRemoval((ExplosiveMinecart) entity);
            }
            else {
                VehicleDestroyListener.cancelTntRemoval(entity);
            }
        }
    }
}
