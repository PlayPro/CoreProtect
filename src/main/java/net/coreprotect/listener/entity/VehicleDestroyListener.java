package net.coreprotect.listener.entity;

import java.util.Locale;

import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Vehicle;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.vehicle.VehicleDestroyEvent;
import org.bukkit.projectiles.ProjectileSource;

import net.coreprotect.config.Config;
import net.coreprotect.consumer.Queue;
import net.coreprotect.listener.player.EntityInteractionListener;
import net.coreprotect.listener.player.InventoryChangeListener;
import net.coreprotect.utility.EntitySpawnTracking;

public final class VehicleDestroyListener extends Queue implements Listener {

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onVehicleDestroy(VehicleDestroyEvent event) {
        Vehicle vehicle = event.getVehicle();
        if (EntitySpawnTracking.isCoreProtectRemoval(vehicle.getUniqueId())) {
            return;
        }

        InventoryChangeListener.flushEntityContainer(vehicle);

        if (EntitySpawnTracking.isPlacedEntity(vehicle) && Config.getConfig(vehicle.getWorld()).ENTITY_KILLS) {
            String user = getUser(event);
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

    private static String getUser(VehicleDestroyEvent event) {
        Entity attacker = event.getAttacker();
        EntityDamageEvent damage = event.getVehicle().getLastDamageCause();
        if (attacker == null && damage instanceof EntityDamageByEntityEvent) {
            attacker = ((EntityDamageByEntityEvent) damage).getDamager();
        }

        String user = getEntityUser(attacker);
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

    private static String getEntityUser(Entity attacker) {
        if (attacker instanceof Player) {
            return attacker.getName();
        }
        if (attacker instanceof Projectile) {
            ProjectileSource shooter = ((Projectile) attacker).getShooter();
            if (shooter instanceof Player) {
                return ((Player) shooter).getName();
            }
            if (shooter instanceof Entity) {
                attacker = (Entity) shooter;
            }
        }
        if (attacker == null) {
            return null;
        }

        EntityType type = attacker.getType();
        return type == null ? null : "#" + type.name().toLowerCase(Locale.ROOT);
    }
}
