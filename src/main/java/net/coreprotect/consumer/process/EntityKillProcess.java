package net.coreprotect.consumer.process;

import java.sql.PreparedStatement;
import java.util.List;
import java.util.Map;

import org.bukkit.Location;
import org.bukkit.block.BlockState;
import org.bukkit.entity.EntityType;

import net.coreprotect.consumer.Consumer;
import net.coreprotect.database.logger.EntityKillLogger;
import net.coreprotect.utility.EntityUtils;

class EntityKillProcess {

    static void process(PreparedStatement preparedStmt, PreparedStatement preparedStmtEntities, int batchCount, int processId, int id, Object object, String user) {
        if (object instanceof Object[]) {
            Object[] values = (Object[]) object;
            if (values.length <= 1 || !isLocationData(values[0]) || !(values[1] instanceof EntityType)) {
                return;
            }

            Location location = getLocation(values[0]);
            EntityType type = (EntityType) values[1];
            Map<Integer, List<Object>> objectLists = Consumer.consumerObjectList.get(processId);
            if (objectLists != null && objectLists.get(id) != null) {
                List<Object> objectList = objectLists.get(id);
                int entityId = EntityUtils.getEntityId(type);
                EntityKillLogger.log(preparedStmt, preparedStmtEntities, batchCount, user, location, objectList, entityId);
                objectLists.remove(id);
            }
        }
    }

    private static boolean isLocationData(Object value) {
        return value instanceof Location || value instanceof BlockState;
    }

    private static Location getLocation(Object value) {
        if (value instanceof Location) {
            return ((Location) value).clone();
        }

        BlockState block = (BlockState) value;
        return new Location(block.getWorld(), block.getX(), block.getY(), block.getZ());
    }
}
