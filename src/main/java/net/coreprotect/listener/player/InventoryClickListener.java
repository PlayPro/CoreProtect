package net.coreprotect.listener.player;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;

import net.coreprotect.consumer.Queue;

public final class InventoryClickListener extends Queue implements Listener {

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    protected void onVillagerTrade(InventoryClickEvent event) {
        CraftItemListener.playerCraftItem(event, true);
    }

}
