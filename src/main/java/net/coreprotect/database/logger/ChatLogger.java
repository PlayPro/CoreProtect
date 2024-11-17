package net.coreprotect.database.logger;

import net.coreprotect.config.ConfigHandler;
import net.coreprotect.database.statement.ChatStatement;
import net.coreprotect.utility.Util;
import org.bukkit.Location;

import java.sql.PreparedStatement;
import java.util.Locale;

public class ChatLogger {

    private ChatLogger() {
        throw new IllegalStateException("Database class");
    }

    public static void log(PreparedStatement preparedStmt, int batchCount, long time, Location location, String user, String message) {
        try {
            if (ConfigHandler.blacklist.get(user.toLowerCase(Locale.ROOT)) != null) {
                return;
            }
            int x = location.getBlockX();
            int y = location.getBlockY();
            int z = location.getBlockZ();
            int wid = Util.getWorldId(location.getWorld().getName());
            int userId = ConfigHandler.playerIdCache.get(user.toLowerCase(Locale.ROOT));
            ChatStatement.insert(preparedStmt, batchCount, time, userId, wid, x, y, z, message);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

}
