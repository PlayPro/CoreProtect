package net.coreprotect.database.logger;

import java.sql.PreparedStatement;
import java.util.Locale;

import org.bukkit.Location;

import net.coreprotect.config.ConfigHandler;
import net.coreprotect.database.statement.SignStatement;
import net.coreprotect.utility.Util;

public class SignTextLogger {

    private SignTextLogger() {
        throw new IllegalStateException("Database class");
    }

    public static void log(PreparedStatement preparedStmt, int batchCount, String user, Location location, int action, int color, int data, String line1, String line2, String line3, String line4, int timeOffset) {
        try {
            if (ConfigHandler.blacklist.get(user.toLowerCase(Locale.ROOT)) != null) {
                return;
            }
            int userId = ConfigHandler.playerIdCache.get(user.toLowerCase(Locale.ROOT));
            int wid = Util.getWorldId(location.getWorld().getName());
            int time = (int) (System.currentTimeMillis() / 1000L) - timeOffset;
            int x = location.getBlockX();
            int y = location.getBlockY();
            int z = location.getBlockZ();
            SignStatement.insert(preparedStmt, batchCount, time, userId, wid, x, y, z, action, color, data, line1, line2, line3, line4);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

}
