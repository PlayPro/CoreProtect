package net.coreprotect.paper;

import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class PaperHandler extends PaperAdapter {
    private volatile boolean supportsSnapshotHolderLookup = true;

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
        if (supportsSnapshotHolderLookup) {
            try {
                return holder.getHolder(useSnapshot);
            }
            catch (LinkageError ignored) {
                supportsSnapshotHolderLookup = false;
            }
        }

        return holder.getHolder();
    }

}
