package net.coreprotect.consumer.process;

import net.coreprotect.database.logger.PlayerKillLogger;
import org.bukkit.block.BlockState;

import java.sql.PreparedStatement;

class PlayerKillProcess {

    static void process(PreparedStatement preparedStmt, int batchCount, int id, Object object, String user) {
        if (object instanceof Object[]) {
            BlockState block = (BlockState) ((Object[]) object)[0];
            String player = (String) ((Object[]) object)[1];
            PlayerKillLogger.log(preparedStmt, batchCount, user, block, player);
        }
    }
}
