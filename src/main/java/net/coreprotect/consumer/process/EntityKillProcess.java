package net.coreprotect.consumer.process;

import java.sql.PreparedStatement;
import java.util.List;
import java.util.Map;

import org.bukkit.block.BlockState;
import org.bukkit.entity.EntityType;

import net.coreprotect.consumer.Consumer;
import net.coreprotect.database.logger.EntityKillLogger;
import net.coreprotect.utility.EntityUtils;

class EntityKillProcess {

    static void process(PreparedStatement preparedStmt, PreparedStatement preparedStmtEntities, int batchCount, int processId, int id, Object object, String user) {
        if (object instanceof Object[]) {
            Object[] values = (Object[]) object;
            if (values.length <= 1 || !(values[0] instanceof BlockState) || !(values[1] instanceof EntityType)) {
                return;
            }

            BlockState block = (BlockState) values[0];
            EntityType type = (EntityType) values[1];
            Map<Integer, List<Object>> objectLists = Consumer.consumerObjectList.get(processId);
            if (objectLists != null && objectLists.get(id) != null) {
                List<Object> objectList = objectLists.get(id);
                int entityId = EntityUtils.getEntityId(type);
                EntityKillLogger.log(preparedStmt, preparedStmtEntities, batchCount, user, block, objectList, entityId);
                objectLists.remove(id);
            }
        }
    }
}
