package net.coreprotect.listener.player;

import java.util.List;
import java.util.Map;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;

import net.coreprotect.config.Config;
import net.coreprotect.consumer.Queue;
import net.coreprotect.database.logger.ItemLogger;

public final class PlayerDeathListener implements Listener {

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    protected void onPlayerDeath(PlayerDeathEvent event) {
        if (event.getKeepInventory()) {
            return;
        }

        Entity entity = event.getEntity();
        if (!(entity instanceof Player)) {
            return;
        }

        Location location = entity.getLocation();
        if (!Config.getConfig(location.getWorld()).ITEM_DROPS) {
            return;
        }

        String user = ((Player) entity).getName();
        List<ItemStack> items = event.getDrops();
        if (items == null || items.isEmpty()) {
            return;
        }

        int time = (int) (System.currentTimeMillis() / 1000L) + 1;
        Queue.queuePendingItems(user, location, time, 0, Map.of(ItemLogger.ITEM_DROP, items));
    }

}
