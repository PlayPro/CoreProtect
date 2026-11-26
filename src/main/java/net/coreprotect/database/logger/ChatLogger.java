package net.coreprotect.database.logger;

import org.bukkit.Location;

import net.coreprotect.config.ConfigHandler;
import net.coreprotect.database.ConsumerWriteBatch;
import net.coreprotect.database.Database;
import net.coreprotect.database.statement.ChatStatement;
import net.coreprotect.database.statement.UserStatement;
import net.coreprotect.event.CoreProtectPreLogEvent;
import net.coreprotect.model.action.LookupActions;
import net.coreprotect.utility.WorldUtils;

public class ChatLogger {

    private ChatLogger() {
        throw new IllegalStateException("Database class");
    }

    public static void log(ConsumerWriteBatch preparedStmt, int batchCount, long time, Location location, String user, String message) {
        try {
            if (ConfigHandler.isBlacklisted(user)) {
                return;
            }
            CoreProtectPreLogEvent event = LoggerPreLog.fire(user, location, CoreProtectPreLogEvent.Action.PLAYER_CHAT, LookupActions.CHAT, null, null, message);
            if (event == null) {
                return;
            }

            Location logged = event.getLocation();
            int wid = WorldUtils.getWorldId(logged.getWorld().getName());
            int userId = UserStatement.getId(preparedStmt, event.getUser(), true);
            ChatStatement.insert(preparedStmt, batchCount, time, userId, wid, logged.getBlockX(), logged.getBlockY(), logged.getBlockZ(), message);
        }
        catch (Exception e) {
            Database.handleWriteFailure(e);
        }
    }

}
