package net.coreprotect.paper.listener;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPlaceEvent;

import io.papermc.paper.event.entity.EntityBreakByEntityEvent;
import io.papermc.paper.event.entity.EntityBreakEvent;
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.listener.entity.CushionBreakListener;
import net.coreprotect.listener.entity.HangingBreakByEntityListener;
import net.coreprotect.utility.EntitySpawnTracking;

public final class CushionListener implements Listener {

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityPlace(EntityPlaceEvent event) {
        Player player = event.getPlayer();
        if (EntitySpawnTracking.isCushion(event.getEntity()) && player != null
                && Boolean.TRUE.equals(ConfigHandler.inspecting.get(player.getName()))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityInspect(EntityBreakEvent event) {
        if (!EntitySpawnTracking.isCushion(event.getEntity()) || !(event instanceof EntityBreakByEntityEvent)) {
            return;
        }

        Entity remover = ((EntityBreakByEntityEvent) event).getRemover();
        if (!(remover instanceof Player) || !Boolean.TRUE.equals(ConfigHandler.inspecting.get(remover.getName()))) {
            return;
        }

        Entity entity = event.getEntity();
        event.setCancelled(true);
        HangingBreakByEntityListener.inspectEntity(entity.getLocation().getBlock().getState(), (Player) remover, entity.getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityBreak(EntityBreakEvent event) {
        if (event.isCancelled()) {
            return;
        }
        Entity remover = null;
        if (event instanceof EntityBreakByEntityEvent) {
            EntityBreakByEntityEvent entityEvent = (EntityBreakByEntityEvent) event;
            remover = entityEvent.getDamageSource().getCausingEntity();
            if (remover == null) {
                remover = entityEvent.getRemover();
            }
        }
        CushionBreakListener.logBreak(event.getEntity(), remover, event.getCause().name());
    }
}
