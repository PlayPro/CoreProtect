package net.coreprotect.paper;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class Paper_v1_16 extends PaperHandler implements PaperInterface {

    @Override
    public InventoryHolder getHolder(Inventory holder, boolean useSnapshot) {
        return holder.getHolder(useSnapshot);
    }

}
