package net.coreprotect.consumer.process;

import net.coreprotect.database.logger.BlockBreakLogger;
import net.coreprotect.utility.Util;
import org.bukkit.Material;
import org.bukkit.block.BlockState;

import java.sql.PreparedStatement;
import java.util.List;

class BlockBreakProcess {

    static void process(PreparedStatement preparedStmt, int batchCount, int processId, int id, Material blockType, int blockDataId, Material replaceType, int forceData, String user, Object object, String blockData) {
        if (object instanceof BlockState) {
            BlockState block = (BlockState) object;
            List<Object> meta = Util.processMeta(block);
            BlockBreakLogger.log(preparedStmt, batchCount, user, block.getLocation(), Util.getBlockId(blockType), blockDataId, meta, block.getBlockData().getAsString(), blockData);
        }
    }
}
