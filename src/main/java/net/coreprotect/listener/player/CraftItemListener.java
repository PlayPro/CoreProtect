package net.coreprotect.listener.player;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantInventory;

import net.coreprotect.config.Config;
import net.coreprotect.consumer.Queue;

public final class CraftItemListener {

    private CraftItemListener() {
    }

    protected static void logCraftedItem(Location location, String user, ItemStack itemStack, int action) {
        if (location != null && location.getWorld() != null && !InventoryResultClick.isEmpty(itemStack) && Config.getConfig(location.getWorld()).ITEM_TRANSACTIONS) {
            Queue.queuePendingItems(user, location, InventoryResultClick.queueTime(), 0, action, itemStack);
        }
    }

    static InventoryResultClick captureCraftOrTrade(InventoryClickEvent event, Player player, Inventory inventory) {
        if (event instanceof CraftItemEvent && inventory instanceof CraftingInventory) {
            return InventoryResultClick.capture(event, player, inventory, ((CraftingInventory) inventory)::getMatrix, true, false);
        }
        if (inventory instanceof MerchantInventory) {
            return InventoryResultClick.capture(event, player, inventory, () -> new ItemStack[] { inventory.getItem(0), inventory.getItem(1) }, false, true);
        }
        return null;
    }
}
