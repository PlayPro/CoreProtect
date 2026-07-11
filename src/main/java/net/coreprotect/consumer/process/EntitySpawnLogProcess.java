package net.coreprotect.consumer.process;

import java.sql.PreparedStatement;

import net.coreprotect.database.logger.EntitySpawnLogger;
import net.coreprotect.model.entity.EntitySpawnData;
import net.coreprotect.model.entity.EntitySpawnIdentity;

final class EntitySpawnLogProcess {

    private EntitySpawnLogProcess() {
    }

    static EntitySpawnIdentity process(PreparedStatement blockStatement, PreparedStatement entitySpawnStatement, PreparedStatement blockLinkStatement, Object object, String user) {
        if (object instanceof EntitySpawnData) {
            EntitySpawnData data = (EntitySpawnData) object;
            return EntitySpawnLogger.logIdentity(blockStatement, entitySpawnStatement, blockLinkStatement, user, data);
        }
        return null;
    }
}
