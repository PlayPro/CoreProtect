package net.coreprotect.consumer.process;

import net.coreprotect.database.statement.EntitySpawnStatement;
import net.coreprotect.model.entity.EntitySpawnData;

final class EntitySpawnUpdateProcess {

    private EntitySpawnUpdateProcess() {
    }

    static void process(EntitySpawnStatement.Updates updates, Object object) {
        if (object instanceof EntitySpawnData) {
            updates.apply((EntitySpawnData) object);
        }
    }
}
