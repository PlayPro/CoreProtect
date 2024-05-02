package net.coreprotect.listener.player;

import org.bukkit.Material;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerPickupArrowEvent;
import org.bukkit.inventory.ItemStack;

import net.coreprotect.bukkit.BukkitAdapter;
import net.coreprotect.consumer.Queue;
import net.coreprotect.listener.entity.EntityPickupItemListener;

public final class PlayerPickupArrowListener extends Queue implements Listener {

    public static ItemStack getArrowType(AbstractArrow arrow) {
        ItemStack itemStack = null;
        switch (arrow.getType()) {
            case SPECTRAL_ARROW:
                itemStack = new ItemStack(Material.SPECTRAL_ARROW);
                break;
            default:
                itemStack = new ItemStack(Material.ARROW);
                break;
        }

        if (arrow instanceof Arrow) {
            Arrow arrowEntity = (Arrow) arrow;
            itemStack = BukkitAdapter.ADAPTER.getArrowMeta(arrowEntity, itemStack);
        }

        return itemStack;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    protected void onPlayerPickupArrowEvent(PlayerPickupArrowEvent event) {
        ItemStack itemStack = getArrowType(event.getArrow());
        EntityPickupItemListener.onItemPickup(event.getPlayer(), event.getArrow().getLocation(), itemStack);
    }

}
