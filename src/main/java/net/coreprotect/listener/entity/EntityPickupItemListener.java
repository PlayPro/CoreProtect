package net.coreprotect.listener.entity;

import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.inventory.ItemStack;

import net.coreprotect.config.Config;
import net.coreprotect.consumer.Queue;
import net.coreprotect.database.logger.ItemLogger;

public final class EntityPickupItemListener implements Listener {

    public static void onItemPickup(Player player, Location location, ItemStack itemStack) {
        if (itemStack == null || location == null || !Config.getConfig(location.getWorld()).ITEM_PICKUPS) {
            return;
        }

        int time = (int) (System.currentTimeMillis() / 1000L) + 1;
        Queue.queuePendingItems(player.getName(), location, time, 0, ItemLogger.ITEM_PICKUP, itemStack);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    protected void onEntityPickupItem(EntityPickupItemEvent event) {
        if (event.getEntityType() != EntityType.PLAYER) {
            return;
        }

        Player player = (Player) event.getEntity();
        Item item = event.getItem();
        onItemPickup(player, item.getLocation(), item.getItemStack());
    }

}
