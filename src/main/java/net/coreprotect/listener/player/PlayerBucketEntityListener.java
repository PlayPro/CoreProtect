package net.coreprotect.listener.player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerBucketEntityEvent;
import org.bukkit.inventory.ItemStack;

import net.coreprotect.config.Config;
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.consumer.Queue;
import net.coreprotect.utility.EntityUtils;

public final class PlayerBucketEntityListener extends Queue implements Listener {

    @EventHandler(priority = EventPriority.LOWEST)
    protected void onPlayerBucketEntityInspect(PlayerBucketEntityEvent event) {
        if (!isSulfurCubeEvent(event)) {
            return;
        }

        Player player = event.getPlayer();
        String playerName = player.getName();
        if (ConfigHandler.inspecting.get(playerName) != null && ConfigHandler.inspecting.get(playerName)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    protected void onPlayerBucketEntity(PlayerBucketEntityEvent event) {
        if (!isSulfurCubeEvent(event)) {
            return;
        }

        Player player = event.getPlayer();
        Location location = player.getLocation();
        if (location.getWorld() == null || !Config.getConfig(location.getWorld()).BUCKETS) {
            return;
        }

        ItemStack originalBucket = event.getOriginalBucket();
        ItemStack entityBucket = event.getEntityBucket();
        if (!isEmptyBucket(originalBucket) || entityBucket == null || !EntityUtils.isSulfurCubeBucket(entityBucket.getType())) {
            return;
        }

        logItemConversion(location, player.getName(), originalBucket, entityBucket);
    }

    private static void logItemConversion(Location location, String player, ItemStack originalBucket, ItemStack entityBucket) {
        if (!Config.getConfig(location.getWorld()).ITEM_TRANSACTIONS) {
            return;
        }

        String loggingItemId = player.toLowerCase(Locale.ROOT) + "." + location.getBlockX() + "." + location.getBlockY() + "." + location.getBlockZ();
        int itemId = getItemId(loggingItemId);

        ItemStack removedItem = originalBucket.clone();
        removedItem.setAmount(1);
        List<ItemStack> removedItems = ConfigHandler.itemsDrop.getOrDefault(loggingItemId, new ArrayList<>());
        removedItems.add(removedItem);
        ConfigHandler.itemsDrop.put(loggingItemId, removedItems);

        ItemStack addedItem = entityBucket.clone();
        addedItem.setAmount(1);
        List<ItemStack> addedItems = ConfigHandler.itemsPickup.getOrDefault(loggingItemId, new ArrayList<>());
        addedItems.add(addedItem);
        ConfigHandler.itemsPickup.put(loggingItemId, addedItems);

        int time = (int) (System.currentTimeMillis() / 1000L) + 1;
        Queue.queueItemTransaction(player, location.clone(), time, 0, itemId);
    }

    private static boolean isEmptyBucket(ItemStack itemStack) {
        return itemStack != null && itemStack.getType() == Material.BUCKET;
    }

    private static boolean isSulfurCubeEvent(PlayerBucketEntityEvent event) {
        return event != null && event.getEntity() != null && EntityUtils.isSulfurCube(event.getEntity().getType());
    }
}
