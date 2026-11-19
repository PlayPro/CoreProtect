package net.coreprotect.database.logger;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
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
        CoreProtectPreLogEvent event = new CoreProtectPreLogEvent(user, location == null ? null : location.clone(), action, actionId, material, entityType, message);
        if (Config.getGlobal().API_ENABLED && !Bukkit.isPrimaryThread()) {
            CoreProtect.getInstance().getServer().getPluginManager().callEvent(event);
        }

        String actor = event.getUser();
        if (event.isCancelled() || actor == null || actor.isBlank() || ConfigHandler.isBlacklisted(actor) || event.getLocation() == null) {
            return null;
        }
        return event;
    }

    static boolean samePosition(Location first, Location second) {
        World world = first.getWorld();
        return world != null && world.equals(second.getWorld())
                && Double.compare(first.getX(), second.getX()) == 0 && Double.compare(first.getY(), second.getY()) == 0
                && Double.compare(first.getZ(), second.getZ()) == 0;
    }
}
