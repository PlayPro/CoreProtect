package net.coreprotect.listener.player;

import net.coreprotect.consumer.Queue;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;

public final class InventoryClickListener extends Queue implements Listener {

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    protected void onVillagerTrade(InventoryClickEvent event) {
        CraftItemListener.playerCraftItem(event, true);
    }

}
