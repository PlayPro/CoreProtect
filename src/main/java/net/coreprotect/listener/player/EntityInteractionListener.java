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
import net.coreprotect.utility.ErrorReporter;

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
            if (!ConfigHandler.serverRunning || ConfigHandler.shutdownDrainRunning) {
                return;
            }
            interaction = pendingInteractions.get(key);
            if (interaction == null) {
                if (pendingInteractions.size() >= MAX_PENDING_INTERACTIONS) {
                    return;
                }

                boolean promotion;
                try {
                    promotion = EntitySpawnTracking.beginDatabaseIdentityPromotion(entity);
                }
                catch (RuntimeException e) {
                    ErrorReporter.report(e);
                    return;
                }
                interaction = new PendingInteraction(player.getName(), entity, snapshot.withIdentityPromotion(promotion), unleashCandidate, now);
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
                Scheduler.scheduleSyncDelayedTask(CoreProtect.getInstance(),
                        () -> flush(key, scheduledInteraction),
                        () -> discard(key, scheduledInteraction), entity, 1);
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

        List<PendingFlush> interactions = new ArrayList<>();
        UUID entityId = entity.getUniqueId();
        synchronized (pendingInteractions) {
            Iterator<Map.Entry<InteractionKey, PendingInteraction>> iterator = pendingInteractions.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<InteractionKey, PendingInteraction> entry = iterator.next();
                if (entry.getKey().entityId.equals(entityId)) {
                    PendingFlush flush = entry.getValue().prepareFlush(false);
                    if (flush != null) {
                        interactions.add(flush);
                        iterator.remove();
                    }
                }
            }
        }

        for (PendingFlush interaction : interactions) {
            interaction.publish();
        }
    }

    public static void flushPendingInteractions() {
        List<PendingFlush> interactions = new ArrayList<>();
        synchronized (pendingInteractions) {
            Iterator<PendingInteraction> iterator = pendingInteractions.values().iterator();
            while (iterator.hasNext()) {
                PendingFlush flush = iterator.next().prepareFlush(false);
                if (flush != null) {
                    interactions.add(flush);
                    iterator.remove();
                }
            }
            nextCleanup = 0L;
        }

        for (PendingFlush interaction : interactions) {
            interaction.publish();
        }
    }

    private static void flush(InteractionKey key, PendingInteraction interaction) {
        PendingFlush flush;
        synchronized (pendingInteractions) {
            if (pendingInteractions.get(key) != interaction) {
                return;
            }
            flush = interaction.prepareFlush(true);
            if (flush == null) {
                return;
            }
            pendingInteractions.remove(key);
        }

        flush.publish();
    }

    private static void discard(InteractionKey key, PendingInteraction interaction) {
        boolean removed;
        synchronized (pendingInteractions) {
            removed = pendingInteractions.remove(key, interaction);
        }
        if (removed) {
            interaction.cancelPromotion();
        }
    }

    private static void flushStaleInteractions(long now) {
        List<PendingFlush> stale = new ArrayList<>();
        synchronized (pendingInteractions) {
            if (now < nextCleanup) {
                return;
            }

            nextCleanup = now + STALE_INTERACTION_NANOS;
            Iterator<PendingInteraction> iterator = pendingInteractions.values().iterator();
            while (iterator.hasNext()) {
                PendingInteraction interaction = iterator.next();
                if (now - interaction.createdAt >= STALE_INTERACTION_NANOS) {
                    PendingFlush flush = interaction.prepareFlush(false);
                    if (flush != null) {
                        iterator.remove();
                        stale.add(flush);
                    }
                }
            }
        }
        for (PendingFlush interaction : stale) {
            interaction.publish();
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

        private PendingFlush prepareFlush(boolean resolveUnleash) {
            try {
                EntityInteractionAction resolvedAction = action;
                if (resolvedAction == EntityInteractionAction.GENERIC
                        && resolveUnleash
                        && unleashCandidate
                        && entity.isValid()
                        && !isLeashed(entity)) {
                    resolvedAction = EntityInteractionAction.UNLEASH;
                }
                EntityInteraction queuedInteraction = interaction.withAction(resolvedAction);
                return new PendingFlush(user, queuedInteraction, reserveEntityInteractionQueue());
            }
            catch (RuntimeException e) {
                ErrorReporter.report(e);
                return null;
            }
        }

        private void cancelPromotion() {
            if (interaction.hasIdentityPromotion()) {
                EntitySpawnTracking.cancelDatabaseIdentityPromotion(interaction.getEntityUuid(), interaction.getCurrentLocation());
            }
        }
    }

    private static final class PendingFlush {

        private final String user;
        private final EntityInteraction interaction;
        private final long reservation;

        private PendingFlush(String user, EntityInteraction interaction, long reservation) {
            this.user = user;
            this.interaction = interaction;
            this.reservation = reservation;
        }

        private void publish() {
            try {
                queueReservedEntityInteraction(user, interaction, reservation);
            }
            catch (RuntimeException e) {
                ErrorReporter.report(e);
            }
        }
    }
}
