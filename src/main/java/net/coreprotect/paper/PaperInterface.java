package net.coreprotect.paper;

import org.bukkit.Server;
import org.bukkit.block.Sign;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public interface PaperInterface {

    public InventoryHolder getHolder(Inventory holder, boolean useSnapshot);

    public boolean isStopping(Server server);

    public String getLine(Sign sign, int line);

}
