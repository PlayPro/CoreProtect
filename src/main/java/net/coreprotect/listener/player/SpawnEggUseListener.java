package net.coreprotect.listener.player;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;

import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import net.coreprotect.config.Config;
import net.coreprotect.thread.CacheHandler;

public final class SpawnEggUseListener implements Listener {

    private static final long MAX_AGE_MILLIS = 2000L;
    private static final Map<String, ConcurrentLinkedDeque<PendingUse>> useCache = new ConcurrentHashMap<>();
    private static final AtomicLong nextCleanup = new AtomicLong();

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerInteract(PlayerInteractEvent event) {
        ItemStack item = event.getItem();
        if (!isSpawnEgg(item) || event.useItemInHand() == Event.Result.DENY) {
            return;
        }
        Block hit = event.getClickedBlock();
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && hit != null && hit.getType() != Material.SPAWNER) {
            cacheUse(hit.getRelative(event.getBlockFace()).getLocation(), item, event.getPlayer().getName());
        }
        else if (event.getAction() == Action.RIGHT_CLICK_AIR) {
            Block fluid = event.getPlayer().getTargetBlockExact(5, FluidCollisionMode.SOURCE_ONLY);
            if (fluid != null && (fluid.getType() == Material.WATER || fluid.getType() == Material.LAVA)) {
                cacheUse(fluid.getLocation(), item, event.getPlayer().getName());
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        EquipmentSlot hand = event.getHand();
        if (hand == EquipmentSlot.HAND || hand == EquipmentSlot.OFF_HAND) {
            ItemStack item = event.getPlayer().getInventory().getItem(hand);
            cacheUse(event.getRightClicked().getLocation(), item, event.getPlayer().getName());
        }
    }

    private static boolean isSpawnEgg(ItemStack item) {
        return item != null && item.getType().name().endsWith("_SPAWN_EGG");
    }

    private static void cacheUse(Location location, ItemStack item, String user) {
        if (!isSpawnEgg(item) || !Config.getConfig(location.getWorld()).ENTITY_SPAWNS) {
            return;
        }

        long now = System.currentTimeMillis();
        cleanup(now);
        useCache.compute(CacheHandler.locationKey(location), (key, queue) -> {
            ConcurrentLinkedDeque<PendingUse> uses = queue == null ? new ConcurrentLinkedDeque<>() : queue;
            uses.addLast(new PendingUse(now, user, getExpectedType(item.getType())));
            return uses;
        });
    }

    public static String consumeUser(Entity entity) {
        Location location = entity.getLocation();
        long now = System.currentTimeMillis();
        cleanup(now);
        String entityType = entity.getType().getKey().getKey();

        while (true) {
            ConcurrentLinkedDeque<PendingUse> bestQueue = null;
            String bestKey = null;
            PendingUse bestMatch = null;
            for (int x = location.getBlockX() - 1; x <= location.getBlockX() + 1; x++) {
                for (int y = location.getBlockY() - 1; y <= location.getBlockY() + 1; y++) {
                    for (int z = location.getBlockZ() - 1; z <= location.getBlockZ() + 1; z++) {
                        String key = CacheHandler.locationKey(location.getWorld(), x, y, z);
                        ConcurrentLinkedDeque<PendingUse> queue = useCache.get(key);
                        if (queue == null) {
                            continue;
                        }

                        for (PendingUse pending : queue) {
                            if (now - pending.timestamp > MAX_AGE_MILLIS || !pending.expectedType.equals(entityType)) {
                                continue;
                            }
                            if (bestMatch == null || pending.timestamp > bestMatch.timestamp) {
                                bestQueue = queue;
                                bestKey = key;
                                bestMatch = pending;
                            }
                        }
                    }
                }
            }

            if (bestMatch == null) {
                return null;
            }
            if (bestQueue.remove(bestMatch)) {
                ConcurrentLinkedDeque<PendingUse> matchedQueue = bestQueue;
                useCache.computeIfPresent(bestKey, (key, queue) -> queue == matchedQueue && queue.isEmpty() ? null : queue);
                return bestMatch.user;
            }
        }
    }

    private static String getExpectedType(Material material) {
        String key = material.getKey().getKey();
        String suffix = "_spawn_egg";
        if (key.endsWith(suffix)) {
            return key.substring(0, key.length() - suffix.length());
        }
        return key;
    }

    private static void cleanup(long now) {
        long scheduled = nextCleanup.get();
        if (now < scheduled || !nextCleanup.compareAndSet(scheduled, now + 1000L)) {
            return;
        }

        for (Map.Entry<String, ConcurrentLinkedDeque<PendingUse>> entry : useCache.entrySet()) {
            useCache.computeIfPresent(entry.getKey(), (key, queue) -> {
                queue.removeIf(pending -> now - pending.timestamp > MAX_AGE_MILLIS);
                return queue.isEmpty() ? null : queue;
            });
        }
    }

    private static final class PendingUse {

        private final long timestamp;
        private final String user;
        private final String expectedType;

        private PendingUse(long timestamp, String user, String expectedType) {
            this.timestamp = timestamp;
            this.user = user;
            this.expectedType = expectedType;
        }
    }
}
