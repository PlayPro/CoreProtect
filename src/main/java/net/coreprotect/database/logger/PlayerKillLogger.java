package net.coreprotect.database.logger;

import org.bukkit.Location;
import org.bukkit.entity.EntityType;

import net.coreprotect.config.ConfigHandler;
import net.coreprotect.database.Database;
import net.coreprotect.database.ConsumerWriteBatch;
import net.coreprotect.database.statement.BlockStatement;
import net.coreprotect.database.statement.UserStatement;
import net.coreprotect.event.CoreProtectPreLogEvent;
import net.coreprotect.model.action.LookupActions;
import net.coreprotect.utility.WorldUtils;

public class PlayerKillLogger {

    private PlayerKillLogger() {
        throw new IllegalStateException("Database class");
    }

    public static void log(ConsumerWriteBatch preparedStmt, int batchCount, String user, Location location, String player) {
        try {
            if (ConfigHandler.isBlacklisted(user)) {
                return;
            }

            CoreProtectPreLogEvent event = LoggerPreLog.fire(user, location, CoreProtectPreLogEvent.Action.PLAYER_KILL, LookupActions.ENTITY_KILL, null, EntityType.PLAYER, null);
            if (event == null) {
                return;
            }

            int playerId = UserStatement.getId(preparedStmt, player, true);
            int userId = UserStatement.getId(preparedStmt, event.getUser(), true);
            Location eventLocation = event.getLocation();
            int wid = WorldUtils.getWorldId(eventLocation.getWorld().getName());
            int time = (int) (System.currentTimeMillis() / 1000L);
            int x = eventLocation.getBlockX();
            int y = eventLocation.getBlockY();
            int z = eventLocation.getBlockZ();
            BlockStatement.insert(preparedStmt, batchCount, time, userId, wid, x, y, z, 0, playerId, null, null, LookupActions.ENTITY_KILL, 0);
        }
        catch (Exception e) {
            Database.handleWriteFailure(e);
        }
    }

}
