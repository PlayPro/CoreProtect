package net.coreprotect.consumer.process;

import net.coreprotect.database.logger.BlockPlaceLogger;
import net.coreprotect.utility.Util;
import org.bukkit.Material;
import org.bukkit.block.BlockState;

import java.sql.PreparedStatement;
import java.util.List;

class BlockPlaceProcess {

    static void process(PreparedStatement preparedStmt, int batchCount, Material blockType, int blockData, Material replaceType, int replaceData, int forceData, String user, Object object, String newBlockData, String replacedBlockData) {
        if (object instanceof BlockState) {
            BlockState block = (BlockState) object;
            List<Object> meta = Util.processMeta(block);
            if (forceData == 1) {
                BlockPlaceLogger.log(preparedStmt, batchCount, user, block, Util.getBlockId(replaceType), replaceData, blockType, blockData, true, meta, newBlockData, replacedBlockData);
            } else {
                BlockPlaceLogger.log(preparedStmt, batchCount, user, block, Util.getBlockId(replaceType), replaceData, blockType, blockData, false, meta, newBlockData, replacedBlockData);
            }
        }
    }
}
