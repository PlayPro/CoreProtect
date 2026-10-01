package net.coreprotect.listener.entity;

import java.util.Locale;

import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingBreakEvent;

import net.coreprotect.config.Config;
import net.coreprotect.consumer.Queue;
import net.coreprotect.listener.player.EntityInteractionListener;
import net.coreprotect.utility.EntitySpawnTracking;
import net.coreprotect.utility.EntityUtils;

public final class CushionBreakListener extends Queue implements Listener {

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHangingBreak(HangingBreakEvent event) {
        if (!event.isCancelled()) {
            Entity remover = event instanceof HangingBreakByEntityEvent ? ((HangingBreakByEntityEvent) event).getRemover() : null;
            logBreak(event.getEntity(), remover, event.getCause().name());
        }
    }

    public static void logBreak(Entity entity, Entity remover, String cause) {
        if (!EntitySpawnTracking.isCushion(entity) || EntitySpawnTracking.isCoreProtectRemoval(entity.getUniqueId())) {
            return;
        }

        EntityInteractionListener.flushPendingInteractions(entity);
        if (Config.getConfig(entity.getWorld()).ENTITY_KILLS) {
            String user = EntityUtils.getEntityUser(remover);
            if (user == null) {
                user = "#" + cause.toLowerCase(Locale.ROOT);
            }
            Queue.queueEntityKill(user, entity.getLocation(), EntitySpawnTracking.serializeKillData(entity), entity.getType());
        }

        if (EntitySpawnTracking.isTrackedOrPendingIdentity(entity)) {
            Queue.queueEntitySpawnRemoved(entity);
            EntitySpawnTracking.clearTracking(entity.getUniqueId());
        }
    }
}
