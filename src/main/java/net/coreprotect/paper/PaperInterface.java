package net.coreprotect.paper;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public interface PaperInterface {

    public InventoryHolder getHolder(Inventory holder, boolean useSnapshot);

}
