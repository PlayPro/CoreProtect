package net.coreprotect.database.logger;

import java.util.List;

import org.bukkit.Location;
import org.bukkit.entity.EntityType;

import net.coreprotect.config.ConfigHandler;
import net.coreprotect.database.Database;
import net.coreprotect.database.ConsumerWriteBatch;
import net.coreprotect.database.statement.BlockStatement;
import net.coreprotect.database.statement.EntitySpawnStatement;
import net.coreprotect.database.statement.EntityStatement;
import net.coreprotect.database.statement.UserStatement;
import net.coreprotect.event.CoreProtectPreLogEvent;
import net.coreprotect.model.action.LookupActions;
import net.coreprotect.utility.EntityUtils;
import net.coreprotect.utility.WorldUtils;

public class EntityKillLogger {

    private EntityKillLogger() {
        throw new IllegalStateException("Database class");
    }

    public static void log(ConsumerWriteBatch preparedStmt, ConsumerWriteBatch preparedStmt2, ConsumerWriteBatch preparedStmtEntityKillLinks, int batchCount, String user, Location location, List<Object> data, int type) {
        log(preparedStmt, preparedStmt2, preparedStmtEntityKillLinks, batchCount, user, location, data, EntityUtils.getEntityType(type));
    }

    public static void log(ConsumerWriteBatch preparedStmt, ConsumerWriteBatch preparedStmt2, ConsumerWriteBatch preparedStmtEntityKillLinks, int batchCount, String user, Location location, List<Object> data, EntityType checkType) {
        try {
            if (ConfigHandler.isBlacklisted(user)){
                return;
            }

            if (checkType == null) {
                return;
            }
            // Ignore blacklist if the entity has a custom name
            // data[4] contains custom name data
            boolean hasCustomName = data.size() > 4 && data.get(4) != null;
            String entityKey = checkType.getKey().toString();
            if (!hasCustomName && ConfigHandler.isBlacklisted(user, entityKey)) {
                return;
            }

            CoreProtectPreLogEvent event = LoggerPreLog.fire(user, location, CoreProtectPreLogEvent.Action.ENTITY_KILL, LookupActions.ENTITY_KILL, null, checkType, null);
            if (event == null || (!hasCustomName && ConfigHandler.isFilterBlacklisted(event.getUser(), entityKey))) {
                return;
            }

            int userId = UserStatement.getId(preparedStmt, event.getUser(), true);
            Location eventLocation = event.getLocation();
            int wid = WorldUtils.getWorldId(eventLocation.getWorld().getName());
            int time = (int) (System.currentTimeMillis() / 1000L);
            int x = eventLocation.getBlockX();
            int y = eventLocation.getBlockY();
            int z = eventLocation.getBlockZ();
            int entity_key = EntityStatement.insert(preparedStmt2, time, data);
            if (entity_key == 0) {
                return;
            }

            if (data.size() > 7 && data.get(7) instanceof String) {
                EntitySpawnStatement.addKillLink(preparedStmtEntityKillLinks, (String) data.get(7), entity_key);
            }

            BlockStatement.insert(preparedStmt, batchCount, time, userId, wid, x, y, z, EntityUtils.getEntityId(checkType), entity_key, null, null, LookupActions.ENTITY_KILL, 0);
        }
        catch (Exception e) {
            Database.handleWriteFailure(e);
        }
    }

}
