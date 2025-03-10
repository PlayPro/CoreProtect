package net.coreprotect.database.logger;

import java.sql.PreparedStatement;
import java.util.Locale;

import org.bukkit.Bukkit;
import org.bukkit.Location;

import net.coreprotect.CoreProtect;
import net.coreprotect.config.Config;
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.database.statement.SignStatement;
import net.coreprotect.database.statement.UserStatement;
import net.coreprotect.event.CoreProtectPreLogEvent;
import net.coreprotect.utility.WorldUtils;

public class SignTextLogger {

    private SignTextLogger() {
        throw new IllegalStateException("Database class");
    }

    public static void log(PreparedStatement preparedStmt, int batchCount, String user, Location location, int action, int color, int colorSecondary, int data, boolean isWaxed, boolean isFront, String line1, String line2, String line3, String line4, String line5, String line6, String line7, String line8, int timeOffset) {
        try {
            if (ConfigHandler.blacklist.get(user.toLowerCase(Locale.ROOT)) != null) {
                return;
            }

            CoreProtectPreLogEvent event = new CoreProtectPreLogEvent(user);
            if (Config.getGlobal().API_ENABLED && !Bukkit.isPrimaryThread()) {
                CoreProtect.getInstance().getServer().getPluginManager().callEvent(event);
            }

            if (event.isCancelled()) {
                return;
            }

            int userId = UserStatement.getId(preparedStmt, event.getUser(), true);
            int wid = WorldUtils.getWorldId(location.getWorld().getName());
            int time = (int) (System.currentTimeMillis() / 1000L) - timeOffset;
            int x = location.getBlockX();
            int y = location.getBlockY();
            int z = location.getBlockZ();

            if (line1.isEmpty() && line2.isEmpty() && line3.isEmpty() && line4.isEmpty()) {
                line1 = null;
                line2 = null;
                line3 = null;
                line4 = null;
            }
            if (line5.isEmpty() && line6.isEmpty() && line7.isEmpty() && line8.isEmpty()) {
                line5 = null;
                line6 = null;
                line7 = null;
                line8 = null;
            }

            SignStatement.insert(preparedStmt, batchCount, time, userId, wid, x, y, z, action, color, colorSecondary, data, isWaxed ? 1 : 0, isFront ? 0 : 1, line1, line2, line3, line4, line5, line6, line7, line8);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

}
