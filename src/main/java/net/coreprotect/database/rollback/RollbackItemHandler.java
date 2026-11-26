package net.coreprotect.database.rollback;

import java.util.List;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import net.coreprotect.utility.ItemUtils;

public class RollbackItemHandler {

    /**
     * Populates an ItemStack with metadata from the database
     * 
     * @param itemstack
     *            The ItemStack to populate
     * @param metadata
     *            The metadata as a byte array
     * @return Object array containing [slot, facing, itemstack]
     */
    public static Object[] populateItemStack(ItemStack itemstack, byte[] metadata) {
        return RollbackUtil.populateItemStack(itemstack, metadata);
    }

    /**
     * Sorts inventory items for better display
     * 
     * @param inventory
     *            The inventory to sort
     * @param slots
     *            The slots to sort
     */
    public static void sortContainerItems(PlayerInventory inventory, List<Integer> slots) {
        ItemStack[] armor = inventory.getArmorContents().clone();
        ItemStack[] storage = inventory.getStorageContents().clone();
        boolean moved = false;
        for (int slot : slots) {
            if (slot < 0 || slot >= armor.length) {
                continue;
            }
            ItemStack item = armor[slot];
            if (ItemUtils.isEmptyContainerSlot(item) || ItemUtils.getPlayerArmorSlot(item.getType()) == slot) {
                continue;
            }
            for (int index = 0; index < storage.length; index++) {
                if (ItemUtils.isEmptyContainerSlot(storage[index])) {
                    storage[index] = item.clone();
                    armor[slot] = null;
                    moved = true;
                    break;
                }
            }
        }
        if (moved) {
            inventory.setStorageContents(storage);
            inventory.setArmorContents(armor);
        }
    }


}
