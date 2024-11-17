package net.coreprotect.listener.player;

import net.coreprotect.config.Config;
import net.coreprotect.consumer.Queue;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerTakeLecternBookEvent;

public final class PlayerTakeLecternBookListener extends Queue implements Listener {

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerTakeLecternBook(PlayerTakeLecternBookEvent event) {
        Player player = event.getPlayer();
        if (Config.getConfig(player.getWorld()).ITEM_TRANSACTIONS) {
            InventoryChangeListener.inventoryTransaction(player.getName(), event.getLectern().getLocation(), null);
        }
    }

}
