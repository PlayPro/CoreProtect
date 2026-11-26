package net.coreprotect.listener.player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.bukkit.Location;
import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.ItemStack;

import net.coreprotect.config.Config;
import net.coreprotect.consumer.Queue;
import net.coreprotect.database.logger.ItemLogger;

public final class PlayerDropItemListener implements Listener {

    private static final long ITEM_DROP_ATTRIBUTION_MS = TimeUnit.MINUTES.toMillis(5);
    private static final Map<UUID, DropAttribution> itemDropAttributions = new ConcurrentHashMap<>();

    private static class DropAttribution {
        private final String user;
        private final long expiresAt;

        private DropAttribution(String user, long expiresAt) {
            this.user = user;
            this.expiresAt = expiresAt;
        }
    }

    public static void playerDropItem(Location location, String user, ItemStack itemStack) {
        if (!Config.getConfig(location.getWorld()).ITEM_DROPS || itemStack == null) {
            return;
        }

        int time = (int) (System.currentTimeMillis() / 1000L) + 1;
        Queue.queuePendingItems(user, location, time, 0, ItemLogger.ITEM_DROP, itemStack);
    }

    public static String getDroppedItemUser(UUID itemId) {
        if (itemId == null) {
            return null;
        }

        long timestamp = System.currentTimeMillis();

        DropAttribution attribution = itemDropAttributions.get(itemId);
        if (attribution == null) {
            return null;
        }

        if (attribution.expiresAt < timestamp) {
            itemDropAttributions.remove(itemId, attribution);
            return null;
        }

        return attribution.user;
    }

    private static void trackDroppedItem(Item item, String user) {
        if (item == null || user == null || user.isEmpty()) {
            return;
        }

        long timestamp = System.currentTimeMillis();
        itemDropAttributions.put(item.getUniqueId(), new DropAttribution(user, timestamp + ITEM_DROP_ATTRIBUTION_MS));
    }

    public static void clearExpiredAttributions(long timestamp) {
        itemDropAttributions.entrySet().removeIf(entry -> entry.getValue().expiresAt < timestamp);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    protected void onPlayerDropItem(PlayerDropItemEvent event) {
        Item item = event.getItemDrop();
        ItemStack itemStack = item.getItemStack();
        trackDroppedItem(item, event.getPlayer().getName());
        playerDropItem(item.getLocation(), event.getPlayer().getName(), itemStack);
    }

}
