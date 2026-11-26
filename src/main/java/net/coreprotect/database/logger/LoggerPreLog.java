package net.coreprotect.database.logger;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.EntityType;

import net.coreprotect.CoreProtect;
import net.coreprotect.config.Config;
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.event.CoreProtectPreLogEvent;

final class LoggerPreLog {

    private LoggerPreLog() {
    }

    static CoreProtectPreLogEvent fire(String user, Location location, CoreProtectPreLogEvent.Action action, int actionId, Material material, EntityType entityType, String message) {
        CoreProtectPreLogEvent event = new CoreProtectPreLogEvent(user, location, action, actionId, material, entityType, message);
        if (Config.getGlobal().API_ENABLED) {
            CoreProtect plugin = CoreProtect.getInstance();
            Server server = plugin == null ? null : plugin.getServer();
            if (server != null) {
                server.getPluginManager().callEvent(event);
            }
        }

        String actor = event.getUser();
        if (event.isCancelled() || actor == null || actor.isBlank() || ConfigHandler.isBlacklisted(actor)) {
            return null;
        }
        Location eventLocation = event.getLocation();
        try {
            return eventLocation != null && eventLocation.getWorld() != null ? event : null;
        }
        catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    static boolean samePosition(Location first, Location second) {
        World world = first.getWorld();
        return world != null && world.equals(second.getWorld())
                && Double.compare(first.getX(), second.getX()) == 0 && Double.compare(first.getY(), second.getY()) == 0
                && Double.compare(first.getZ(), second.getZ()) == 0;
    }
}
