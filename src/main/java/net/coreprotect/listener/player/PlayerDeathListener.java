package net.coreprotect.listener.player;

import java.util.List;

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

public final class PlayerDeathListener extends Queue implements Listener {

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
        if (items == null || items.size() == 0) {
            return;
        }

        for (ItemStack itemStack : items) {
            PlayerDropItemListener.playerDropItem(location, user, itemStack);
        }
    }

}
