package net.coreprotect.consumer.process;

import java.sql.Statement;
import java.util.List;

import org.bukkit.block.BlockState;
import org.bukkit.entity.EntityType;

import net.coreprotect.config.ConfigHandler;
import net.coreprotect.database.statement.EntityStatement;
import net.coreprotect.utility.EntitySpawnTracking;
import net.coreprotect.utility.entity.EntityUtil;

class EntitySpawnProcess {

    static void process(Statement statement, Object object, int rowId) {
        if (object instanceof Object[]) {
            BlockState block = (BlockState) ((Object[]) object)[0];
            Object recordedType = ((Object[]) object)[1];
            String query = "SELECT data FROM " + ConfigHandler.prefix + "entity WHERE rowid=" + rowId + " LIMIT 1 OFFSET 0";
            List<Object> data = EntityStatement.getData(statement, query);
            if (recordedType instanceof Integer) {
                int typeId = (Integer) recordedType;
                EntityUtil.spawnEntity(block, EntitySpawnTracking.resolveKillType(typeId, data), data, Integer.toString(typeId));
            }
            else if (recordedType instanceof EntityType) {
                EntityUtil.spawnEntity(block, (EntityType) recordedType, data);
            }
        }
    }
}
