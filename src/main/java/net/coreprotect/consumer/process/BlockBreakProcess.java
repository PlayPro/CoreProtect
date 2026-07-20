package net.coreprotect.consumer.process;

import net.coreprotect.database.ConsumerWriteBatch;
import java.util.List;

import org.bukkit.Material;
import org.bukkit.block.BlockState;
import org.bukkit.block.Skull;

import net.coreprotect.database.logger.BlockBreakLogger;
import net.coreprotect.database.logger.SkullBreakLogger;
import net.coreprotect.utility.BlockUtils;
import net.coreprotect.utility.MaterialUtils;

class BlockBreakProcess {

    static void process(ConsumerWriteBatch preparedStmt, ConsumerWriteBatch preparedStmtSkulls, int batchCount, int processId, int id, Material blockType, int blockDataId, Material replaceType, int forceData, String user, Object object, String blockData) {
        if (object instanceof BlockState) {
            BlockState block = (BlockState) object;
            List<Object> meta = BlockUtils.processMeta(block);
            if (block instanceof Skull && blockType != null && blockType.equals(block.getType())) {
                SkullBreakLogger.log(preparedStmt, preparedStmtSkulls, batchCount, user, block);
            }
            else {
                BlockBreakLogger.log(preparedStmt, batchCount, user, block.getLocation(), MaterialUtils.getBlockId(blockType), blockDataId, meta, blockData, blockData);
            }
        }
    }
}
