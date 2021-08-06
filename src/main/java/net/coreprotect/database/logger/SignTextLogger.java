package net.coreprotect.database.logger;

import java.sql.PreparedStatement;
import java.util.Locale;

import net.coreprotect.CoreProtect;
import net.coreprotect.event.CoreProtectPreLogEvent;
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

            CoreProtectPreLogEvent event = new CoreProtectPreLogEvent(user);
            CoreProtect.getInstance().getServer().getPluginManager().callEvent(event);
            if (!event.getUser().equals(user)) {
                user = event.getUser();
            }

            int userId = ConfigHandler.getOrCreateUserId(preparedStmt.getConnection(), user);
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
