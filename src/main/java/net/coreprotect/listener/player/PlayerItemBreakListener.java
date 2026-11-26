package net.coreprotect.listener.player;

import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemBreakEvent;
import org.bukkit.inventory.ItemStack;

import net.coreprotect.config.Config;
import net.coreprotect.consumer.Queue;
import net.coreprotect.database.logger.ItemLogger;

public final class PlayerItemBreakListener implements Listener {

    protected static void playerBreakItem(Location location, String user, ItemStack itemStack) {
        if (!Config.getConfig(location.getWorld()).ITEM_TRANSACTIONS || itemStack == null) {
            return;
        }

        int time = (int) (System.currentTimeMillis() / 1000L) + 1;
        Queue.queuePendingItems(user, location, time, 0, ItemLogger.ITEM_BREAK, itemStack);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    protected void onPlayerItemBreak(PlayerItemBreakEvent event) {
        ItemStack itemStack = event.getBrokenItem();
        playerBreakItem(event.getPlayer().getLocation(), event.getPlayer().getName(), itemStack);
    }

}
