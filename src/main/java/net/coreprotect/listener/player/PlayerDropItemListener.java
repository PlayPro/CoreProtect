package net.coreprotect.listener.player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.bukkit.Location;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.ItemStack;

import net.coreprotect.config.Config;
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.consumer.Queue;

public final class PlayerDropItemListener extends Queue implements Listener {

    protected static void playerDropItem(Location location, Player player, ItemStack itemStack) {
        if (itemStack == null) {
            return;
        }

        String loggingItemId = player.getName().toLowerCase(Locale.ROOT) + "." + location.getBlockX() + "." + location.getBlockY() + "." + location.getBlockZ();
        int itemId = getItemId(loggingItemId);

        List<ItemStack> list = ConfigHandler.itemsDrop.getOrDefault(loggingItemId, new ArrayList<>());
        list.add(itemStack.clone());
        ConfigHandler.itemsDrop.put(loggingItemId, list);

        int time = (int) (System.currentTimeMillis() / 1000L) + 1;
        Queue.queueItemTransaction(player.getName(), location.clone(), time, itemId);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    protected void onPlayerDropItem(PlayerDropItemEvent event) {
        Item item = event.getItemDrop();
        Location location = item.getLocation();
        if (!Config.getConfig(location.getWorld()).ITEM_DROPS) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack itemStack = item.getItemStack();
        playerDropItem(location, player, itemStack);
    }

}
