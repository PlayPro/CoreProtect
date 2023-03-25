package net.coreprotect.paper;

import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.entity.Entity;

public class PaperHandler extends PaperAdapter implements PaperInterface {

    @Override
    public boolean isStopping(Server server) {
        return server.isStopping();
    }

    @Override
    public void teleportAsync(Entity entity, Location location) {
        entity.teleportAsync(location);
    }

}
