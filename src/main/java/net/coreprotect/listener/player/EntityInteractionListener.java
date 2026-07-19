package net.coreprotect.listener.player;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerLeashEntityEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerShearEntityEvent;

import net.coreprotect.CoreProtect;
import net.coreprotect.config.Config;
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.consumer.Queue;
import net.coreprotect.model.entity.EntityInteraction;
import net.coreprotect.model.entity.EntityInteractionAction;
import net.coreprotect.model.entity.EntityInteractionOrigin;
import net.coreprotect.thread.Scheduler;
import net.coreprotect.utility.EntitySpawnTracking;

public final class EntityInteractionListener extends Queue implements Listener {

    private static final int MAX_PENDING_INTERACTIONS = 4096;
    private static final long STALE_INTERACTION_NANOS = 1_000_000_000L;
    private static final Map<InteractionKey, PendingInteraction> pendingInteractions = new LinkedHashMap<>();
    private static long nextCleanup;

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (event instanceof PlayerArmorStandManipulateEvent) {
            return;
        }

        Player player = event.getPlayer();
        Entity entity = event.getRightClicked();
        if (!shouldLog(player, entity)) {
            return;
        }

        capture(player, entity, EntityInteractionAction.GENERIC, isLeashed(entity));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerShearEntity(PlayerShearEntityEvent event) {
        Player player = event.getPlayer();
        Entity entity = event.getEntity();
        if (shouldLog(player, entity)) {
            capture(player, entity, EntityInteractionAction.SHEAR, false);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerLeashEntity(PlayerLeashEntityEvent event) {
        Player player = event.getPlayer();
        Entity entity = event.getEntity();
        if (shouldLog(player, entity)) {
            capture(player, entity, EntityInteractionAction.LEASH, false);
        }
    }

    private void capture(Player player, Entity entity, EntityInteractionAction action, boolean unleashCandidate) {
        InteractionKey key = new InteractionKey(player.getUniqueId(), entity.getUniqueId());
        int eventTime = (int) (System.currentTimeMillis() / 1000L);
        Location currentLocation = entity.getLocation();
        EntityInteractionOrigin origin = EntitySpawnTracking.getOrCreateInteractionOrigin(entity);
        if (origin == null || currentLocation.getWorld() == null) {
            return;
        }
        EntityInteraction snapshot = new EntityInteraction(entity.getUniqueId(), entity.getType(), origin, currentLocation, action, null, eventTime);
        PendingInteraction interaction;
        boolean scheduleFlush = false;
        long now = System.nanoTime();
        flushStaleInteractions(now);

        synchronized (pendingInteractions) {
            interaction = pendingInteractions.get(key);
            if (interaction == null) {
                if (pendingInteractions.size() >= MAX_PENDING_INTERACTIONS) {
                    return;
                }

                interaction = new PendingInteraction(player.getName(), entity, snapshot, unleashCandidate, now);
                pendingInteractions.put(key, interaction);
                scheduleFlush = true;
            }
            else {
                interaction.merge(action, unleashCandidate);
            }
        }

        if (scheduleFlush) {
            PendingInteraction scheduledInteraction = interaction;
            try {
                EntitySpawnTracking.beginDatabaseIdentityPromotion(entity);
                Scheduler.scheduleSyncDelayedTask(CoreProtect.getInstance(), () -> flush(key, scheduledInteraction), () -> discard(key, scheduledInteraction), entity, 1);
            }
            catch (RuntimeException e) {
                discard(key, scheduledInteraction);
            }
        }
    }

    public static void flushPendingInteractions(Entity entity) {
        if (entity == null) {
            return;
        }

        List<PendingInteraction> interactions = new ArrayList<>();
        UUID entityId = entity.getUniqueId();
        synchronized (pendingInteractions) {
            Iterator<Map.Entry<InteractionKey, PendingInteraction>> iterator = pendingInteractions.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<InteractionKey, PendingInteraction> entry = iterator.next();
                if (entry.getKey().entityId.equals(entityId)) {
                    interactions.add(entry.getValue());
                    iterator.remove();
                }
            }
        }

        for (PendingInteraction interaction : interactions) {
            interaction.flush(false);
        }
    }

    private static void flush(InteractionKey key, PendingInteraction interaction) {
        synchronized (pendingInteractions) {
            if (!pendingInteractions.remove(key, interaction)) {
                return;
            }
        }

        interaction.flush(true);
    }

    private static void discard(InteractionKey key, PendingInteraction interaction) {
        synchronized (pendingInteractions) {
            pendingInteractions.remove(key, interaction);
        }
    }

    private static void flushStaleInteractions(long now) {
        synchronized (pendingInteractions) {
            if (now < nextCleanup) {
                return;
            }

            nextCleanup = now + STALE_INTERACTION_NANOS;
            Iterator<PendingInteraction> iterator = pendingInteractions.values().iterator();
            while (iterator.hasNext()) {
                PendingInteraction interaction = iterator.next();
                if (now - interaction.createdAt >= STALE_INTERACTION_NANOS) {
                    iterator.remove();
                    interaction.flush(false);
                }
            }
        }
    }

    private static boolean shouldLog(Player player, Entity entity) {
        return !Boolean.TRUE.equals(ConfigHandler.inspecting.get(player.getName()))
                && Config.getConfig(player.getWorld()).PLAYER_INTERACTIONS
                && !(entity instanceof Player)
                && !(entity instanceof ArmorStand)
                && !(entity instanceof ItemFrame);
    }

    private static boolean isLeashed(Entity entity) {
        if (!(entity instanceof LivingEntity)) {
            return false;
        }

        return ((LivingEntity) entity).isLeashed();
    }

    private static final class InteractionKey {

        private final UUID playerId;
        private final UUID entityId;

        private InteractionKey(UUID playerId, UUID entityId) {
            this.playerId = playerId;
            this.entityId = entityId;
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) {
                return true;
            }
            if (!(object instanceof InteractionKey)) {
                return false;
            }

            InteractionKey other = (InteractionKey) object;
            return playerId.equals(other.playerId) && entityId.equals(other.entityId);
        }

        @Override
        public int hashCode() {
            return 31 * playerId.hashCode() + entityId.hashCode();
        }
    }

    private static final class PendingInteraction {

        private final String user;
        private final Entity entity;
        private final EntityInteraction interaction;
        private final long createdAt;
        private EntityInteractionAction action;
        private boolean unleashCandidate;

        private PendingInteraction(String user, Entity entity, EntityInteraction interaction, boolean unleashCandidate, long createdAt) {
            this.user = user;
            this.entity = entity;
            this.interaction = interaction;
            this.action = interaction.getAction();
            this.unleashCandidate = unleashCandidate;
            this.createdAt = createdAt;
        }

        private void merge(EntityInteractionAction action, boolean unleashCandidate) {
            if (action != EntityInteractionAction.GENERIC) {
                this.action = action;
            }
            this.unleashCandidate |= unleashCandidate;
        }

        private void flush(boolean resolveUnleash) {
            EntityInteractionAction resolvedAction = action;
            if (resolvedAction == EntityInteractionAction.GENERIC && resolveUnleash && unleashCandidate && entity.isValid() && !isLeashed(entity)) {
                resolvedAction = EntityInteractionAction.UNLEASH;
            }
            Queue.queueEntityInteraction(user, interaction.withAction(resolvedAction));
        }
    }
}
