package net.coreprotect.consumer.process;

import net.coreprotect.database.ConsumerWriteBatch;

import org.bukkit.Material;
import org.bukkit.block.BlockState;

import net.coreprotect.database.logger.PlayerInteractLogger;

class PlayerInteractionProcess {

    static void process(ConsumerWriteBatch preparedStmt, int batchCount, String user, Object object, Material type) {
        if (object instanceof BlockState) {
            BlockState block = (BlockState) object;
            PlayerInteractLogger.log(preparedStmt, batchCount, user, block, type);
        }
    }
}
