package net.coreprotect.utility;

import org.bukkit.entity.ChestedHorse;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.AbstractHorseInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.LlamaInventory;

public final class HorseContainerUtils {

    private HorseContainerUtils() {
        throw new IllegalStateException("Utility class");
    }

    public static boolean isHorseContainer(EntityType type) {
        return type == EntityType.DONKEY || type == EntityType.MULE || type == EntityType.LLAMA || type == EntityType.TRADER_LLAMA;
    }

    // Chest storage has three rows. Depending on the platform/version, getContents()
    // prefixes it with zero, one (saddle), or two (saddle and body) equipment slots.
    private static int storageOffset(int size) {
        return size % 3;
    }

    public static int getViewSize(Inventory inventory) {
        // The horse GUI retains both equipment slots even if getSize() omits them.
        return inventory.getSize() - storageOffset(inventory.getSize()) + 2;
    }

    public static ItemStack[] normalizeContents(EntityType type, ItemStack[] contents) {
        if (!isHorseContainer(type)) {
            return ItemUtils.getContainerState(contents);
        }
        int offset = storageOffset(contents.length);
        ItemStack[] normalized = new ItemStack[contents.length - offset + 2];
        System.arraycopy(contents, 0, normalized, 0, offset);
        System.arraycopy(contents, offset, normalized, 2, contents.length - offset);
        return ItemUtils.getContainerState(normalized);
    }

    // Snapshots always use saddle/decor at 0/1 and storage from 2, independent
    // of the native inventory layout. Read separate equipment through its API.
    public static ItemStack[] getContents(Entity entity, ItemStack[] contents) {
        ItemStack[] snapshot = normalizeContents(entity.getType(), contents);
        if (entity instanceof ChestedHorse) {
            AbstractHorseInventory inventory = ((ChestedHorse) entity).getInventory();
            if (inventory instanceof LlamaInventory) {
                snapshot[1] = copyItem(((LlamaInventory) inventory).getDecor());
            }
            else {
                snapshot[0] = copyItem(inventory.getSaddle());
            }
        }
        return snapshot;
    }

    public static void setContents(Entity entity, ItemStack[] contents) {
        Inventory inventory = ((InventoryHolder) entity).getInventory();
        if (!(entity instanceof ChestedHorse)) {
            inventory.setContents(ItemUtils.getContainerState(contents));
            return;
        }

        ItemStack[] nativeContents = inventory.getContents();
        int offset = storageOffset(nativeContents.length);
        if (contents.length != nativeContents.length - offset + 2) {
            throw new IllegalArgumentException("Horse container storage size changed");
        }
        System.arraycopy(contents, 2, nativeContents, offset, contents.length - 2);
        inventory.setContents(ItemUtils.getContainerState(nativeContents));
        AbstractHorseInventory horseInventory = ((ChestedHorse) entity).getInventory();
        if (horseInventory instanceof LlamaInventory) {
            ((LlamaInventory) horseInventory).setDecor(copyItem(contents[1]));
        }
        else {
            horseInventory.setSaddle(copyItem(contents[0]));
        }
    }

    private static ItemStack copyItem(ItemStack item) {
        return item == null ? null : item.clone();
    }
}
