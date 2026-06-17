package net.coreprotect.listener.entity;

import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.event.entity.EntityTransformEvent.TransformReason;

public final class EntityTransformListener implements Listener {

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    protected void onEntityTransform(EntityTransformEvent event) {
        Entity entity = event.getEntity();
        if (event.getTransformReason() != TransformReason.LIGHTNING || !(entity instanceof LivingEntity)) {
            return;
        }

        EntityDeathListener.logEntityDeath((LivingEntity) entity, "#lightning");
    }
}
