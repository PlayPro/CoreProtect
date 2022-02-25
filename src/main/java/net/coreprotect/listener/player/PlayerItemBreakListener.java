package net.coreprotect.listener.player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemBreakEvent;
import org.bukkit.inventory.ItemStack;

import net.coreprotect.config.Config;
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.consumer.Queue;

public final class PlayerItemBreakListener extends Queue implements Listener {

    protected static void playerBreakItem(Location location, String user, ItemStack itemStack) {
        if (!Config.getConfig(location.getWorld()).ITEM_TRANSACTIONS || itemStack == null) {
            return;
        }

        String loggingItemId = user.toLowerCase(Locale.ROOT) + "." + location.getBlockX() + "." + location.getBlockY() + "." + location.getBlockZ();
        int itemId = getItemId(loggingItemId);

        List<ItemStack> list = ConfigHandler.itemsBreak.getOrDefault(loggingItemId, new ArrayList<>());
        list.add(itemStack.clone());
        ConfigHandler.itemsBreak.put(loggingItemId, list);

        int time = (int) (System.currentTimeMillis() / 1000L) + 1;
        Queue.queueItemTransaction(user, location.clone(), time, 0, itemId);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    protected void onPlayerItemBreak(PlayerItemBreakEvent event) {
        ItemStack itemStack = event.getBrokenItem();
        playerBreakItem(event.getPlayer().getLocation(), event.getPlayer().getName(), itemStack);
    }

}
