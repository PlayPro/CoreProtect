package net.coreprotect.consumer.process;

import java.sql.PreparedStatement;

import net.coreprotect.utility.serialize.SerializedBlockMeta;
import org.bukkit.Material;
import org.bukkit.block.BlockState;
import org.bukkit.block.Skull;

import net.coreprotect.database.logger.BlockBreakLogger;
import net.coreprotect.database.logger.SkullBreakLogger;
import net.coreprotect.utility.BlockUtils;
import net.coreprotect.utility.MaterialUtils;

class BlockBreakProcess {

    static void process(PreparedStatement preparedStmt, PreparedStatement preparedStmtSkulls, int batchCount, int processId, int id, Material blockType, int blockDataId, Material replaceType, int forceData, String user, Object object, String blockData) {
        if (object instanceof BlockState) {
            BlockState block = (BlockState) object;
            SerializedBlockMeta meta = BlockUtils.processMeta(block);
            if (block instanceof Skull && blockType != null && blockType.equals(block.getType())) {
                SkullBreakLogger.log(preparedStmt, preparedStmtSkulls, batchCount, user, block);
            }
            else {
                BlockBreakLogger.log(preparedStmt, batchCount, user, block.getLocation(), MaterialUtils.getBlockId(blockType), blockDataId, meta, blockData, blockData);
            }
        }
    }
}
