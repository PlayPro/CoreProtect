package net.coreprotect.database.logger;

import java.sql.PreparedStatement;
import java.util.Locale;

import org.bukkit.Location;

import net.coreprotect.config.ConfigHandler;
import net.coreprotect.database.statement.CommandStatement;
import net.coreprotect.utility.Util;

public class CommandLogger {

    private CommandLogger() {
        throw new IllegalStateException("Database class");
    }

    public static void log(PreparedStatement preparedStmt, int batchCount, int time, Location location, String user, String message) {
        try {
            if (ConfigHandler.blacklist.get(user.toLowerCase(Locale.ROOT)) != null) {
                return;
            }
            if (ConfigHandler.blacklist.get(((message + " ").split(" "))[0].toLowerCase(Locale.ROOT)) != null) {
                return;
            }
            int x = location.getBlockX();
            int y = location.getBlockY();
            int z = location.getBlockZ();
            int wid = Util.getWorldId(location.getWorld().getName());
            int userId = ConfigHandler.playerIdCache.get(user.toLowerCase(Locale.ROOT));
            CommandStatement.insert(preparedStmt, batchCount, time, userId, wid, x, y, z, message);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

}
