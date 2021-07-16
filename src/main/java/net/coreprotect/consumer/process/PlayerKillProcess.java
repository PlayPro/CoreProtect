package net.coreprotect.consumer.process;

import java.sql.PreparedStatement;

import org.bukkit.block.BlockState;

import net.coreprotect.database.logger.PlayerKillLogger;

class PlayerKillProcess {

    static void process(PreparedStatement preparedStmt, int batchCount, int id, Object object, String user) {
        if (object instanceof Object[]) {
            BlockState block = (BlockState) ((Object[]) object)[0];
            String player = (String) ((Object[]) object)[1];
            PlayerKillLogger.log(preparedStmt, batchCount, user, block, player);
        }
    }
}
