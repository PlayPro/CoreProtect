package net.coreprotect.consumer.process;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

import org.bukkit.block.BlockState;

import net.coreprotect.consumer.Consumer;
import net.coreprotect.database.Lookup;
import net.coreprotect.database.logger.BlockBreakLogger;
import net.coreprotect.database.logger.BlockPlaceLogger;
import net.coreprotect.utility.MaterialUtils;
import net.coreprotect.utility.Util;

class StructureGrowthProcess {

    static void process(Statement statement, PreparedStatement preparedStmt, int batchCount, int processId, int id, String user, Object object, int replaceBlockCount) {
        if (object instanceof BlockState) {
            BlockState block = (BlockState) object;
            Map<Integer, List<BlockState>> blockLists = Consumer.consumerBlockList.get(processId);
            if (blockLists.get(id) != null) {
                List<BlockState> blockStates = blockLists.get(id);
                String resultData = Lookup.whoPlacedCache(block);
                if (resultData.isEmpty()) {
                    resultData = Lookup.whoPlaced(statement, block);
                }
                if (resultData.length() > 0) {
                    user = resultData;
                }
                int count = 0;
                for (BlockState blockState : blockStates) {
                    if (count < replaceBlockCount) {
                        BlockBreakLogger.log(preparedStmt, batchCount, user, blockState.getLocation(), MaterialUtils.getBlockId(blockState.getType()), 0, null, blockState.getBlockData().getAsString(), null);
                    }
                    else {
                        BlockPlaceLogger.log(preparedStmt, batchCount, user, blockState, 0, 0, null, -1, false, null, null, null);
                    }
                    count++;
                }
                blockLists.remove(id);
            }
        }
    }
}
