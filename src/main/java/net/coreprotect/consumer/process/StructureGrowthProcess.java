package net.coreprotect.consumer.process;

import net.coreprotect.database.ConsumerWriteBatch;
import java.sql.Statement;
import java.util.List;

import org.bukkit.block.BlockState;

import net.coreprotect.consumer.Consumer;
import net.coreprotect.consumer.QueuePayload;
import net.coreprotect.database.Lookup;
import net.coreprotect.database.logger.BlockBreakLogger;
import net.coreprotect.database.logger.BlockPlaceLogger;

class StructureGrowthProcess {

    static void process(Statement statement, ConsumerWriteBatch preparedStmt, int batchCount, int processId, int id, String user, Object object, int replaceBlockCount) {
        if (object instanceof BlockState) {
            BlockState block = (BlockState) object;
            List<BlockState> blockStates = QueuePayload.detail(Consumer.consumerObjects.get(processId).get(id));
            if (blockStates != null) {
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
                        BlockBreakLogger.log(preparedStmt, batchCount, user, blockState.getLocation(), blockState.getType(), 0, null, blockState.getBlockData().getAsString(), null);
                    }
                    else {
                        BlockPlaceLogger.log(preparedStmt, batchCount, user, blockState, 0, 0, null, -1, false, null, null, null);
                    }
                    count++;
                }
            }
        }
    }
}
