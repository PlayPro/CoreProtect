package net.coreprotect.database.logger;

import java.sql.PreparedStatement;
import java.util.Locale;

import net.coreprotect.CoreProtect;
import net.coreprotect.event.CoreProtectPreLogEvent;
import org.bukkit.Location;

import net.coreprotect.config.ConfigHandler;
import net.coreprotect.database.statement.CommandStatement;
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
            CoreProtect.getInstance().getServer().getPluginManager().callEvent(event);
            if (!event.getUser().equals(user)) {
                user = event.getUser();
            }

            int x = location.getBlockX();
            int y = location.getBlockY();
            int z = location.getBlockZ();
            int wid = Util.getWorldId(location.getWorld().getName());
            int userId = ConfigHandler.getOrCreateUserId(preparedStmt.getConnection(), user);
            CommandStatement.insert(preparedStmt, batchCount, time, userId, wid, x, y, z, message);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

}
