package net.coreprotect.consumer.process;

import java.sql.PreparedStatement;

import org.bukkit.block.BlockState;

import net.coreprotect.database.logger.PlayerKillLogger;

class PlayerKillProcess {

    static void process(PreparedStatement preparedStmt, int batchCount, int id, Object object, String user) {
        if (object instanceof Object[]) {
            Object[] values = (Object[]) object;
            if (values.length <= 1 || !(values[0] instanceof BlockState) || !(values[1] instanceof String)) {
                return;
            }

            BlockState block = (BlockState) values[0];
            String player = (String) values[1];
            PlayerKillLogger.log(preparedStmt, batchCount, user, block, player);
        }
    }
}
