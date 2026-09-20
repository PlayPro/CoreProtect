package net.coreprotect.database.logger;

import net.coreprotect.config.ConfigHandler;
import net.coreprotect.database.ConsumerWriteBatch;
import net.coreprotect.database.Database;
import net.coreprotect.database.statement.ChatStatement;
import net.coreprotect.database.statement.UserStatement;
import net.coreprotect.utility.WorldUtils;
import org.bukkit.Location;

public class ChatLogger {

    private ChatLogger() {
        throw new IllegalStateException("Database class");
    }

    public static void log(ConsumerWriteBatch preparedStmt, int batchCount, long time, Location location, String user, String message) {
        try {
            if (ConfigHandler.isBlacklisted(user)) {
                return;
            }
            int x = location.getBlockX();
            int y = location.getBlockY();
            int z = location.getBlockZ();
            int wid = WorldUtils.getWorldId(location.getWorld().getName());
            int userId = UserStatement.getId(preparedStmt, user, true);
            ChatStatement.insert(preparedStmt, batchCount, time, userId, wid, x, y, z, message);
        } catch (Exception e) {
            Database.handleWriteFailure(e);
        }
    }

}
