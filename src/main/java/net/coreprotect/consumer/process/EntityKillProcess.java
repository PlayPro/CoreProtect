package net.coreprotect.consumer.process;

import java.sql.PreparedStatement;

import org.bukkit.Location;
import org.bukkit.entity.EntityType;

import net.coreprotect.database.logger.EntityKillLogger;
import net.coreprotect.utility.EntityUtils;

class EntityKillProcess {

    static void process(PreparedStatement preparedStmt, PreparedStatement preparedStmtEntities, int batchCount, int processId, int id, Object object, String user) {
        if (object instanceof Object[]) {
            Location location = (Location) ((Object[]) object)[0];
            EntityType type = (EntityType) ((Object[]) object)[1];
            String data = (String) ((Object[]) object)[2];

            int entityId = EntityUtils.getEntityId(type);
            EntityKillLogger.log(preparedStmt, preparedStmtEntities, batchCount, user, location, data, entityId);
        }
    }
}
