package net.coreprotect.listener.player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.consumer.Queue;

public final class PlayerDropItemListener extends Queue implements Listener {

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

        String loggingItemId = user.toLowerCase(Locale.ROOT) + "." + location.getBlockX() + "." + location.getBlockY() + "." + location.getBlockZ();
        int itemId = getItemId(loggingItemId);

        List<ItemStack> list = ConfigHandler.itemsDrop.getOrDefault(loggingItemId, new ArrayList<>());
        list.add(itemStack.clone());
        ConfigHandler.itemsDrop.put(loggingItemId, list);

        int time = (int) (System.currentTimeMillis() / 1000L) + 1;
        Queue.queueItemTransaction(user, location.clone(), time, 0, itemId);
    }

    public static String getDroppedItemUser(UUID itemId) {
        if (itemId == null) {
            return null;
        }

        long timestamp = System.currentTimeMillis();
        clearExpiredAttributions(timestamp);

        DropAttribution attribution = itemDropAttributions.get(itemId);
        if (attribution == null) {
            return null;
        }

        if (attribution.expiresAt < timestamp) {
            itemDropAttributions.remove(itemId);
            return null;
        }

        return attribution.user;
    }

    private static void trackDroppedItem(Item item, String user) {
        if (item == null || user == null || user.isEmpty()) {
            return;
        }

        long timestamp = System.currentTimeMillis();
        clearExpiredAttributions(timestamp);
        itemDropAttributions.put(item.getUniqueId(), new DropAttribution(user, timestamp + ITEM_DROP_ATTRIBUTION_MS));
    }

    private static void clearExpiredAttributions(long timestamp) {
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
