package net.coreprotect.utility;

import org.bukkit.block.DoubleChest;
import org.bukkit.block.Dropper;
import org.bukkit.block.Hopper;
import org.bukkit.entity.minecart.HopperMinecart;
import org.bukkit.inventory.BlockInventoryHolder;
import org.bukkit.inventory.InventoryHolder;

public class Validate {

    private Validate() {
        throw new IllegalStateException("Utility class");
    }

    public static boolean isHopper(InventoryHolder inventoryHolder) {
        return (inventoryHolder instanceof Hopper || inventoryHolder instanceof HopperMinecart);
    }

    public static boolean isDropper(InventoryHolder inventoryHolder) {
        return (inventoryHolder instanceof Dropper);
    }

    /* check if valid hopper destination */
    public static boolean isContainer(InventoryHolder inventoryHolder) {
        return (inventoryHolder instanceof BlockInventoryHolder || inventoryHolder instanceof DoubleChest);
    }

}
