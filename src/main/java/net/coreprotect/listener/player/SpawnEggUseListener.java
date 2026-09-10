package net.coreprotect.listener.player;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import net.coreprotect.config.Config;
import net.coreprotect.thread.CacheHandler;

public final class SpawnEggUseListener implements Listener {

    private static final long MAX_AGE_MILLIS = 2000L;
    private static final Map<String, ConcurrentLinkedDeque<PendingUse>> useCache = new ConcurrentHashMap<>();
    private static final AtomicLong nextCleanup = new AtomicLong();

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null || event.getClickedBlock().getType() == Material.SPAWNER) {
            return;
        }

        ItemStack item = event.getItem();
        Location location = event.getClickedBlock().getRelative(event.getBlockFace()).getLocation();
        cacheUse(location, item, event.getPlayer().getName());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        ItemStack item = event.getPlayer().getInventory().getItem(event.getHand());
        cacheUse(event.getRightClicked().getLocation(), item, event.getPlayer().getName());
    }

    private static void cacheUse(Location location, ItemStack item, String user) {
        if (item == null || !item.getType().name().endsWith("_SPAWN_EGG") || !Config.getConfig(location.getWorld()).ENTITY_SPAWNS) {
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
