package net.coreprotect.consumer.process;

import net.coreprotect.database.logger.AbilityBlockBreakLogger;
import net.coreprotect.utility.Util;
import org.bukkit.Material;
import org.bukkit.block.BlockState;

import java.sql.PreparedStatement;
import java.util.List;
import java.util.UUID;

class AbilityBlockBreakProcess {

    static void process(PreparedStatement preparedStmt, int batchCount, Material blockType, int blockDataId, String user, Object object, String blockData, String player, String ability) {
        if (object instanceof BlockState) {
            BlockState block = (BlockState) object;
            List<Object> meta = Util.processMeta(block);
            AbilityBlockBreakLogger.log(preparedStmt, batchCount, user, block.getLocation(), Util.getBlockId(blockType), blockDataId, meta, block.getBlockData().getAsString(), blockData, player, ability);
        }
    }
}
