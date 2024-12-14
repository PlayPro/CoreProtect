package net.coreprotect.paper;

import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class PaperHandler extends PaperAdapter implements PaperInterface {

    @Override
    public boolean isStopping(Server server) {
        return server.isStopping();
    }

    @Override
    public void teleportAsync(Entity entity, Location location) {
        entity.teleportAsync(location);
    }

    @Override
    public InventoryHolder getHolder(Inventory holder, boolean useSnapshot) {
        return holder.getHolder(useSnapshot);
    }

}
