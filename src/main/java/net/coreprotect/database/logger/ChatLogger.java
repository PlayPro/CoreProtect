package net.coreprotect.database.logger;

import java.sql.PreparedStatement;
import java.util.Locale;

import org.bukkit.Location;

import net.coreprotect.config.ConfigHandler;
import net.coreprotect.database.statement.ChatStatement;
import net.coreprotect.utility.WorldUtils;
import net.coreprotect.utility.ErrorReporter;

public class ChatLogger {

    private ChatLogger() {
        throw new IllegalStateException("Database class");
    }

    public static void log(PreparedStatement preparedStmt, int batchCount, long time, Location location, String user, String message) {
        log(preparedStmt, batchCount, time, location, user, message, false);
    }

    public static void log(PreparedStatement preparedStmt, int batchCount, long time, Location location, String user, String message, boolean cancelled) {
        try {
            if (ConfigHandler.isBlacklisted(user)) {
                return;
            }
            int x = location.getBlockX();
            int y = location.getBlockY();
            int z = location.getBlockZ();
            int wid = WorldUtils.getWorldId(location.getWorld().getName());
            int userId = ConfigHandler.playerIdCache.get(user.toLowerCase(Locale.ROOT));
            ChatStatement.insert(preparedStmt, batchCount, time, userId, wid, x, y, z, message, cancelled);
        }
        catch (Exception e) {
            ErrorReporter.report(e);
        }
    }

}
