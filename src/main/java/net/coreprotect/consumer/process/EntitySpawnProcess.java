package net.coreprotect.consumer.process;

import net.coreprotect.config.ConfigHandler;
import net.coreprotect.database.statement.EntityStatement;
import net.coreprotect.utility.entity.EntityUtil;
import org.bukkit.block.BlockState;
import org.bukkit.entity.EntityType;

import java.sql.Statement;
import java.util.List;

class EntitySpawnProcess {

    static void process(Statement statement, Object object, int rowId) {
        if (object instanceof Object[]) {
            BlockState block = (BlockState) ((Object[]) object)[0];
            EntityType type = (EntityType) ((Object[]) object)[1];
            String query = "SELECT data FROM " + ConfigHandler.prefix + "entity WHERE rowid='" + rowId + "' LIMIT 0, 1";
            List<Object> data = EntityStatement.getData(statement, block, query);
            EntityUtil.spawnEntity(block, type, data);
        }
    }
}
