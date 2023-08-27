package net.coreprotect.database.logger;

import java.sql.PreparedStatement;
import java.util.Locale;

import net.coreprotect.event.CoreProtectPreCommandLogEvent;
import org.bukkit.Location;

import net.coreprotect.CoreProtect;
import net.coreprotect.config.Config;
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.database.statement.CommandStatement;
import net.coreprotect.database.statement.UserStatement;
import net.coreprotect.event.CoreProtectPreLogEvent;
import net.coreprotect.utility.Util;

public class CommandLogger {

    private CommandLogger() {
        throw new IllegalStateException("Database class");
    }

    public static void log(PreparedStatement preparedStmt, int batchCount, long time, Location location, String user, String message) {
        try {
            if (ConfigHandler.blacklist.get(user.toLowerCase(Locale.ROOT)) != null) {
                return;
            }
            if (ConfigHandler.blacklist.get(((message + " ").split(" "))[0].toLowerCase(Locale.ROOT)) != null) {
                return;
            }

            CoreProtectPreLogEvent event = new CoreProtectPreLogEvent(user);
            CoreProtectPreCommandLogEvent commandEvent = new CoreProtectPreCommandLogEvent(user, message);
            if (Config.getGlobal().API_ENABLED) {
                CoreProtect.getInstance().getServer().getPluginManager().callEvent(event);
                CoreProtect.getInstance().getServer().getPluginManager().callEvent(commandEvent);
            }

            if (event.isCancelled() || commandEvent.isCancelled()) {
                return;
            }

            int userId = UserStatement.getId(preparedStmt, event.getUser(), true);
            int wid = Util.getWorldId(location.getWorld().getName());
            int x = location.getBlockX();
            int y = location.getBlockY();
            int z = location.getBlockZ();
            CommandStatement.insert(preparedStmt, batchCount, time, userId, wid, x, y, z, message);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

}
