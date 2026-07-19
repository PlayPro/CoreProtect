package net.coreprotect.database;

import java.util.List;
import java.util.UUID;

import net.coreprotect.model.entity.EntityContainerRollbackUpdate;
import net.coreprotect.model.entity.EntitySpawnData;

public interface ConsumerEntitySpawnUpdates extends AutoCloseable {

    default void prefetch(List<EntitySpawnData> updates) throws Exception {
    }

    void apply(EntitySpawnData data);

    void applyCombined(EntityContainerRollbackUpdate update, Database.SavepointOperation rowUpdate);

    void identityFound(UUID uuid);

    void afterCommit(boolean committed);

    void afterDiscard();

    @Override
    void close() throws Exception;

}
