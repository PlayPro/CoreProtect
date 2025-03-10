package net.coreprotect.consumer.process;

import java.sql.PreparedStatement;
import java.util.List;
import java.util.Map;

import org.bukkit.block.BlockState;
import org.bukkit.entity.EntityType;

import net.coreprotect.consumer.Consumer;
import net.coreprotect.database.logger.EntityKillLogger;
import net.coreprotect.utility.EntityUtils;
import net.coreprotect.utility.Util;

class EntityKillProcess {

    static void process(PreparedStatement preparedStmt, PreparedStatement preparedStmtEntities, int batchCount, int processId, int id, Object object, String user) {
        if (object instanceof Object[]) {
            BlockState block = (BlockState) ((Object[]) object)[0];
            EntityType type = (EntityType) ((Object[]) object)[1];
            Map<Integer, List<Object>> objectLists = Consumer.consumerObjectList.get(processId);
            if (objectLists.get(id) != null) {
                List<Object> objectList = objectLists.get(id);
                int entityId = EntityUtils.getEntityId(type);
                EntityKillLogger.log(preparedStmt, preparedStmtEntities, batchCount, user, block, objectList, entityId);
                objectLists.remove(id);
            }
        }
    }
}
