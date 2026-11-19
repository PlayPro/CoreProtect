package net.coreprotect.database.logger;

import org.bukkit.Location;

import net.coreprotect.config.ConfigHandler;
import net.coreprotect.database.Database;
import net.coreprotect.database.ConsumerWriteBatch;
import net.coreprotect.database.statement.SessionStatement;
import net.coreprotect.database.statement.UserStatement;
import net.coreprotect.event.CoreProtectPreLogEvent;
import net.coreprotect.utility.WorldUtils;

public class PlayerSessionLogger {

    private PlayerSessionLogger() {
        throw new IllegalStateException("Database class");
    }

    public static void log(ConsumerWriteBatch preparedStmt, int batchCount, String user, Location location, int time, int action) {
        try {
            if (ConfigHandler.isBlacklisted(user)) {
                return;
            }
            CoreProtectPreLogEvent event = LoggerPreLog.fire(user, location, CoreProtectPreLogEvent.Action.PLAYER_SESSION, action, null, null, null);
            if (event == null) {
                return;
            }

            Location logged = event.getLocation();
            int wid = WorldUtils.getWorldId(logged.getWorld().getName());
            int userId = UserStatement.getId(preparedStmt, event.getUser(), true);
            SessionStatement.insert(preparedStmt, batchCount, time, userId, wid, logged.getBlockX(), logged.getBlockY(), logged.getBlockZ(), action);
        }
        catch (Exception e) {
            Database.handleWriteFailure(e);
        }
    }

}
