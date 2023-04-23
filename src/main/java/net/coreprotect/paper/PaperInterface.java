package net.coreprotect.paper;

import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.block.Sign;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public interface PaperInterface {

    public InventoryHolder getHolder(Inventory holder, boolean useSnapshot);

    public boolean isStopping(Server server);

    public String getLine(Sign sign, int line);

    public void teleportAsync(Entity entity, Location location);

}
