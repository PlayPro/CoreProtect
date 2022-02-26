package net.coreprotect.listener;

import org.bukkit.Material;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerPickupArrowEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionData;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionType;

import net.coreprotect.listener.entity.EntityPickupItemListener;

public class PlayerPickupArrowListener extends EntityPickupItemListener implements Listener {

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
            PotionData data = arrowEntity.getBasePotionData();
            if (data.getType() != PotionType.UNCRAFTABLE) {
                itemStack = new ItemStack(Material.TIPPED_ARROW);
                PotionMeta meta = (PotionMeta) itemStack.getItemMeta();
                meta.setBasePotionData(data);
                for (PotionEffect effect : arrowEntity.getCustomEffects()) {
                    meta.addCustomEffect(effect, false);
                }
                itemStack.setItemMeta(meta);
            }
        }

        return itemStack;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    protected void onPlayerPickupArrowEvent(PlayerPickupArrowEvent event) {
        ItemStack itemStack = getArrowType(event.getArrow());
        EntityPickupItemListener.onItemPickup(event.getPlayer(), event.getArrow().getLocation(), itemStack);
    }

}
