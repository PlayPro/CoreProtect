package net.coreprotect.listener.player;

import org.bukkit.Material;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerPickupArrowEvent;
import org.bukkit.inventory.ItemStack;

import net.coreprotect.bukkit.BukkitAdapter;
import net.coreprotect.listener.entity.EntityPickupItemListener;
import net.coreprotect.utility.BlockUtils;

public final class PlayerPickupArrowListener implements Listener {

    public static ItemStack getArrowType(AbstractArrow arrow) {
        ItemStack itemStack;
        switch (arrow.getType()) {
            case SPECTRAL_ARROW:
                itemStack = new ItemStack(Material.SPECTRAL_ARROW);
                break;
            case TRIDENT:
                itemStack = new ItemStack(Material.TRIDENT);
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
        Item pickup = event.getItem();
        ItemStack itemStack = pickup == null ? null : pickup.getItemStack();
        if (itemStack == null || BlockUtils.isAir(itemStack.getType())) {
            itemStack = getArrowType(event.getArrow());
        }
        else {
            itemStack = itemStack.clone();
        }
        EntityPickupItemListener.onItemPickup(event.getPlayer(), event.getArrow().getLocation(), itemStack);
    }

}
