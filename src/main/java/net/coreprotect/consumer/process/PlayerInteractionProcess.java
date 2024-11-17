package net.coreprotect.consumer.process;

import net.coreprotect.database.logger.PlayerInteractLogger;
import org.bukkit.Material;
import org.bukkit.block.BlockState;

import java.sql.PreparedStatement;

class PlayerInteractionProcess {

    static void process(PreparedStatement preparedStmt, int batchCount, String user, Object object, Material type) {
        if (object instanceof BlockState) {
            BlockState block = (BlockState) object;
            PlayerInteractLogger.log(preparedStmt, batchCount, user, block, type);
        }
    }
}
