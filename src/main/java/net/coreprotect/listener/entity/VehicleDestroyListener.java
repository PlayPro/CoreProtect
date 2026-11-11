package net.coreprotect.listener.entity;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Vehicle;
import org.bukkit.entity.minecart.ExplosiveMinecart;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.vehicle.VehicleDestroyEvent;

import net.coreprotect.CoreProtect;
import net.coreprotect.config.Config;
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.consumer.Queue;
import net.coreprotect.listener.player.EntityInteractionListener;
import net.coreprotect.listener.player.InventoryChangeListener;
import net.coreprotect.thread.Scheduler;
import net.coreprotect.utility.EntitySpawnTracking;
import net.coreprotect.utility.EntityUtils;

public final class VehicleDestroyListener extends Queue implements Listener {

    private static final Map<UUID, PendingTntRemoval> pendingTntRemovals = new ConcurrentHashMap<>();

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onVehicleDestroy(VehicleDestroyEvent event) {
        Vehicle vehicle = event.getVehicle();
        if (EntitySpawnTracking.isCoreProtectRemoval(vehicle.getUniqueId())) {
            return;
        }

        String user = EntitySpawnTracking.isPlacedEntity(vehicle) && Config.getConfig(vehicle.getWorld()).ENTITY_KILLS ? getUser(event) : null;
        if (vehicle instanceof ExplosiveMinecart) {
            deferTntRemoval(vehicle, user);
            return;
        }

        logDestruction(vehicle, user);
    }

    private static void logDestruction(Vehicle vehicle, String user) {
        InventoryChangeListener.flushEntityContainer(vehicle);

        if (EntitySpawnTracking.isPlacedEntity(vehicle) && Config.getConfig(vehicle.getWorld()).ENTITY_KILLS) {
            if (user != null && !user.isEmpty()) {
                Queue.queueEntityKill(user, vehicle.getLocation(), EntitySpawnTracking.serializeKillData(vehicle), vehicle.getType());
            }
        }

        if (EntitySpawnTracking.isTrackedOrPendingIdentity(vehicle)) {
            EntityInteractionListener.flushPendingInteractions(vehicle);
            Queue.queueEntitySpawnRemoved(vehicle);
            EntitySpawnTracking.clearTracking(vehicle.getUniqueId());
        }
    }

    private static void deferTntRemoval(Vehicle vehicle, String user) {
        UUID uuid = vehicle.getUniqueId();
        PendingTntRemoval pending = pendingTntRemovals.get(uuid);
        if (pending != null) {
            if (!pending.completed) {
                pending.user = user;
            }
            return;
        }

        PendingTntRemoval removal = new PendingTntRemoval(vehicle, user);
        pendingTntRemovals.put(uuid, removal);
        try {
            Scheduler.scheduleSyncDelayedTask(CoreProtect.getInstance(), removal::completeIfDead,
                    () -> pendingTntRemovals.remove(uuid, removal), vehicle, 0);
        }
        catch (RuntimeException e) {
            pendingTntRemovals.remove(uuid, removal);
            throw e;
        }
    }

    public static boolean completeTntRemoval(Vehicle vehicle) {
        PendingTntRemoval removal = pendingTntRemovals.get(vehicle.getUniqueId());
        if (removal == null) {
            return false;
        }
        if (!removal.completed) {
            removal.completed = true;
            logDestruction(vehicle, removal.user);
        }
        return true;
    }

    static void logTntExplosion(ExplosiveMinecart vehicle) {
        if (EntitySpawnTracking.isCoreProtectRemoval(vehicle.getUniqueId())) {
            return;
        }
        deferTntRemoval(vehicle, "#tnt");
        completeTntRemoval(vehicle);
    }

    public static void cancelTntRemoval(Entity entity) {
        pendingTntRemovals.remove(entity.getUniqueId());
    }

    public static void flushPendingRemovalsForShutdown() {
        if (!ConfigHandler.isFolia) {
            pendingTntRemovals.values().forEach(PendingTntRemoval::completeIfDead);
        }
    }

    private static final class PendingTntRemoval {
        private final Vehicle vehicle;
        private String user;
        private boolean completed;

        private PendingTntRemoval(Vehicle vehicle, String user) {
            this.vehicle = vehicle;
            this.user = user;
        }

        private void completeIfDead() {
            if (pendingTntRemovals.remove(vehicle.getUniqueId(), this) && !completed && vehicle.isDead()) {
                logDestruction(vehicle, user);
            }
        }
    }

    private static String getUser(VehicleDestroyEvent event) {
        Entity attacker = event.getAttacker();
        EntityDamageEvent damage = event.getVehicle().getLastDamageCause();
        if (attacker == null && damage instanceof EntityDamageByEntityEvent) {
            attacker = ((EntityDamageByEntityEvent) damage).getDamager();
        }

        String user = EntityUtils.getEntityUser(attacker);
        if (user != null || damage == null) {
            return user;
        }

        switch (damage.getCause()) {
            case FIRE:
            case FIRE_TICK:
                return "#fire";
            case LAVA:
                return "#lava";
            case BLOCK_EXPLOSION:
            case ENTITY_EXPLOSION:
                return "#explosion";
            case MAGIC:
                return "#magic";
            case WITHER:
                return "#wither_effect";
            default:
                return "#" + damage.getCause().name().toLowerCase(Locale.ROOT);
        }
    }
}
