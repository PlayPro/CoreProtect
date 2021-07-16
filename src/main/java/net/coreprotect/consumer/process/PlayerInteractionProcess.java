package net.coreprotect.consumer.process;

import java.sql.PreparedStatement;

import org.bukkit.block.BlockState;

import net.coreprotect.database.logger.PlayerInteractLogger;

class PlayerInteractionProcess {

    static void process(PreparedStatement preparedStmt, int batchCount, String user, Object object) {
        if (object instanceof BlockState) {
            BlockState block = (BlockState) object;
            PlayerInteractLogger.log(preparedStmt, batchCount, user, block);
        }
    }
}
