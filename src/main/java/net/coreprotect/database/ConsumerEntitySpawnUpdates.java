package net.coreprotect.database;

import java.util.List;

import net.coreprotect.model.entity.EntityContainerRollbackUpdate;
import net.coreprotect.model.entity.EntitySpawnData;

public interface ConsumerEntitySpawnUpdates extends AutoCloseable {

    default void prefetch(List<EntitySpawnData> updates) throws Exception {
    }

    void apply(EntitySpawnData data);

    void applyCombined(EntityContainerRollbackUpdate update, Database.SavepointOperation rowUpdate);

    void afterCommit(boolean committed);

    void afterDiscard();

    @Override
    void close() throws Exception;

}
