package net.coreprotect.listener.entity;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Minecart;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.vehicle.VehicleCreateEvent;
import org.bukkit.inventory.ItemStack;

import net.coreprotect.config.Config;
import net.coreprotect.consumer.Queue;
import net.coreprotect.utility.EntitySpawnTracking;

public final class VehiclePlaceListener extends Queue implements Listener {

    private static final long MAX_AGE_MILLIS = 2000L;
    private static final double MAX_DISTANCE_SQUARED = 100.0D;
    private static final Map<UUID, ConcurrentLinkedDeque<PendingUse>> useCache = new ConcurrentHashMap<>();
    private static final AtomicLong nextCleanup = new AtomicLong();

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_BLOCK && action != Action.RIGHT_CLICK_AIR) {
            return;
        }

        VehicleFamily family = getVehicleFamily(event.getItem());
        if (family == null || !Config.getConfig(event.getPlayer().getWorld()).ENTITY_SPAWNS) {
            return;
        }

        Location location = event.getPlayer().getLocation();
        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock != null) {
            location = clickedBlock.getRelative(event.getBlockFace()).getLocation();
        }

        cacheUse(location, event.getPlayer().getName(), family);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onVehicleCreate(VehicleCreateEvent event) {
        Entity vehicle = event.getVehicle();
        if (!EntitySpawnTracking.isPlacedEntity(vehicle) || EntitySpawnTracking.isTracked(vehicle) || !Config.getConfig(vehicle.getWorld()).ENTITY_SPAWNS) {
            return;
        }

        String user = consumeUser(vehicle);
        if (user == null) {
            return;
        }

        EntitySpawnTracking.track(vehicle);
        Queue.queueEntitySpawnLog(user, vehicle.getUniqueId(), vehicle.getType(), vehicle.getLocation());
    }

    private static void cacheUse(Location location, String user, VehicleFamily family) {
        if (location == null || location.getWorld() == null) {
            return;
        }

        long now = System.currentTimeMillis();
        cleanup(now);
        UUID worldId = location.getWorld().getUID();
        useCache.compute(worldId, (key, queue) -> {
            ConcurrentLinkedDeque<PendingUse> uses = queue == null ? new ConcurrentLinkedDeque<>() : queue;
            uses.addLast(new PendingUse(now, user, family, location.clone()));
            return uses;
        });
    }

    private static String consumeUser(Entity vehicle) {
        VehicleFamily family = getVehicleFamily(vehicle);
        if (family == null) {
            return null;
        }

        Location location = vehicle.getLocation();
        World world = location.getWorld();
        if (world == null) {
            return null;
        }

        long now = System.currentTimeMillis();
        cleanup(now);
        UUID worldId = world.getUID();
        ConcurrentLinkedDeque<PendingUse> queue = useCache.get(worldId);
        if (queue == null) {
            return null;
        }

        while (true) {
            PendingUse bestMatch = null;
            for (PendingUse pending : queue) {
                if (now - pending.timestamp > MAX_AGE_MILLIS || pending.family != family || pending.location.distanceSquared(location) > MAX_DISTANCE_SQUARED) {
                    continue;
                }
                if (bestMatch == null || pending.timestamp > bestMatch.timestamp) {
                    bestMatch = pending;
                }
            }

            if (bestMatch == null) {
                return null;
            }
            if (queue.remove(bestMatch)) {
                useCache.computeIfPresent(worldId, (key, current) -> current == queue && current.isEmpty() ? null : current);
                return bestMatch.user;
            }
        }
    }

    private static VehicleFamily getVehicleFamily(ItemStack item) {
        if (item == null) {
            return null;
        }

        Material type = item.getType();
        if (type == Material.AIR) {
            return null;
        }

        String name = type.name();
        if (name.endsWith("_BOAT") || name.endsWith("_RAFT") || name.equals("BOAT") || name.equals("CHEST_BOAT")) {
            return VehicleFamily.BOAT;
        }
        if (name.contains("MINECART")) {
            return VehicleFamily.MINECART;
        }
        return null;
    }

    private static VehicleFamily getVehicleFamily(Entity entity) {
        if (entity instanceof Boat) {
            return VehicleFamily.BOAT;
        }
        if (entity instanceof Minecart) {
            return VehicleFamily.MINECART;
        }
        return null;
    }

    private static void cleanup(long now) {
        long scheduled = nextCleanup.get();
        if (now < scheduled || !nextCleanup.compareAndSet(scheduled, now + 1000L)) {
            return;
        }

        for (Map.Entry<UUID, ConcurrentLinkedDeque<PendingUse>> entry : useCache.entrySet()) {
            useCache.computeIfPresent(entry.getKey(), (key, queue) -> {
                queue.removeIf(pending -> now - pending.timestamp > MAX_AGE_MILLIS);
                return queue.isEmpty() ? null : queue;
            });
        }
    }

    private enum VehicleFamily {
        BOAT,
        MINECART
    }

    private static final class PendingUse {

        private final long timestamp;
        private final String user;
        private final VehicleFamily family;
        private final Location location;

        private PendingUse(long timestamp, String user, VehicleFamily family, Location location) {
            this.timestamp = timestamp;
            this.user = user;
            this.family = family;
            this.location = location;
        }
    }
}
