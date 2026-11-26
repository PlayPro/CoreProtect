package net.coreprotect.consumer.process;

import net.coreprotect.database.ConsumerWriteBatch;
import java.util.List;

import org.bukkit.Material;
import org.bukkit.block.BlockState;

import net.coreprotect.database.logger.BlockPlaceLogger;
import net.coreprotect.database.logger.SkullPlaceLogger;
import net.coreprotect.utility.BlockUtils;

class BlockPlaceProcess {

    static void process(ConsumerWriteBatch preparedStmt, ConsumerWriteBatch preparedStmtSkulls, int batchCount, Material blockType, int blockData, Material replaceType, int replaceData, int forceData, String user, Object object, String newBlockData, String replacedBlockData) {
        if (object instanceof BlockState) {
            BlockState block = (BlockState) object;
            boolean matchingMaterial = blockType == null || blockType == block.getType();
            List<Object> meta = matchingMaterial ? BlockUtils.processMeta(block) : null;
            if (matchingMaterial && BlockUtils.isSkull(blockType)) {
                SkullPlaceLogger.log(preparedStmt, preparedStmtSkulls, batchCount, user, block, blockType, replaceType, replaceData, meta, newBlockData, replacedBlockData);
            }
            else {
                BlockPlaceLogger.log(preparedStmt, batchCount, user, block, replaceType, replaceData, blockType, blockData, forceData == 1, meta, newBlockData, replacedBlockData);
            }
        }
    }
}
