package net.coreprotect.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerPickupArrowEvent;

import net.coreprotect.listener.entity.EntityPickupItemListener;

public class PlayerPickupArrowListener extends EntityPickupItemListener implements Listener {

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    protected void onPlayerPickupArrowEvent(PlayerPickupArrowEvent event) {
        EntityPickupItemListener.onItemPickup(event.getPlayer(), event.getArrow().getLocation(), event.getArrow().getItemStack());
    }

}
