package net.coreprotect.listener.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.consumer.Queue;

public class EntityPickupItemListener extends Queue implements Listener {

    protected static void onItemPickup(Player player, Location location, ItemStack itemStack) {
        if (itemStack == null || location == null || !Config.getConfig(location.getWorld()).ITEM_PICKUPS) {
            return;
        }

        String loggingItemId = player.getName().toLowerCase(Locale.ROOT) + "." + location.getBlockX() + "." + location.getBlockY() + "." + location.getBlockZ();
        int itemId = getItemId(loggingItemId);

        List<ItemStack> list = ConfigHandler.itemsPickup.getOrDefault(loggingItemId, new ArrayList<>());
        list.add(itemStack.clone());
        ConfigHandler.itemsPickup.put(loggingItemId, list);

        int time = (int) (System.currentTimeMillis() / 1000L) + 1;
        Queue.queueItemTransaction(player.getName(), location.clone(), time, itemId);
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
