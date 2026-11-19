package net.coreprotect.listener.player;

import org.bukkit.Material;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.inventory.ItemStack;

import net.coreprotect.config.Config;
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.listener.player.inspector.ContainerInspector;
import net.coreprotect.model.BlockGroup;
import net.coreprotect.utility.ItemUtils;

public final class ArmorStandManipulateListener implements Listener {

    private final ContainerInspector inspector = new ContainerInspector();

    @EventHandler(priority = EventPriority.MONITOR)
    protected void onPlayerArmorStandManipulateEvent(PlayerArmorStandManipulateEvent event) {
        Player player = event.getPlayer();
        final ArmorStand armorStand = event.getRightClicked();

        if (Boolean.TRUE.equals(ConfigHandler.inspecting.get(player.getName()))) {
            event.setCancelled(true);
            if (BlockGroup.CONTAINERS.contains(Material.ARMOR_STAND) && PlayerInteractListener.hasInspectPermission(player)) {
                inspector.performHangingLookup(player, armorStand.getLocation());
            }
        }
        if (event.isCancelled()) {
            return;
        }

        if (!Config.getConfig(player.getWorld()).ITEM_TRANSACTIONS) {
            return;
        }

        int slot;
        switch (event.getSlot()) {
            case FEET:
                slot = 0;
                break;
            case LEGS:
                slot = 1;
                break;
            case CHEST:
                slot = 2;
                break;
            case HEAD:
                slot = 3;
                break;
            case HAND:
                slot = 4;
                break;
            case OFF_HAND:
                slot = 5;
                break;
            default:
                return;
        }

        ItemStack previous = event.getArmorStandItem();
        ItemStack placed = event.getPlayerItem().clone();
        if (placed.getAmount() > 1) {
            placed.setAmount(1);
        }
        if (previous.equals(placed)) {
            return;
        }

        ItemStack[] oldContents = ItemUtils.getArmorStandContents(armorStand.getEquipment());
        ItemStack[] newContents = oldContents.clone();
        oldContents[slot] = previous.clone();
        newContents[slot] = placed;
        PlayerInteractEntityListener.queueContainerSpecifiedItems(player.getName(), Material.ARMOR_STAND, new Object[] { oldContents, newContents }, armorStand.getLocation(), false);
    }
}
