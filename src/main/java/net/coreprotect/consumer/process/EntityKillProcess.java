package net.coreprotect.consumer.process;

import net.coreprotect.database.ConsumerWriteBatch;
import java.util.List;

import org.bukkit.Location;
import org.bukkit.block.BlockState;
import org.bukkit.entity.EntityType;

import net.coreprotect.consumer.Consumer;
import net.coreprotect.consumer.QueuePayload;
import net.coreprotect.database.logger.EntityKillLogger;
import net.coreprotect.utility.EntityUtils;

class EntityKillProcess {

    static void process(ConsumerWriteBatch preparedStmt, ConsumerWriteBatch preparedStmtEntities, ConsumerWriteBatch preparedStmtEntityKillLinks, int batchCount, int processId, int id, Object object, String user) {
        if (object instanceof Object[]) {
            Object[] values = (Object[]) object;
            if (values.length <= 1 || !(values[1] instanceof EntityType)) {
                return;
            }

            Location location = getLocation(values[0]);
            if (location == null) {
                return;
            }
            EntityType type = (EntityType) values[1];
            List<Object> objectList = QueuePayload.detail(Consumer.consumerObjects.get(processId).get(id));
            if (objectList != null) {
                int entityId = EntityUtils.getEntityId(type);
                EntityKillLogger.log(preparedStmt, preparedStmtEntities, preparedStmtEntityKillLinks, batchCount, user, location, objectList, entityId);
            }
        }
    }

    static Location getLocation(Object value) {
        if (value instanceof Location) {
            return ((Location) value).clone();
        }

        if (!(value instanceof BlockState)) {
            return null;
        }
        BlockState block = (BlockState) value;
        return new Location(block.getWorld(), block.getX(), block.getY(), block.getZ());
    }
}
