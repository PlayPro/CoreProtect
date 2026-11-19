package net.coreprotect.consumer.process;

import net.coreprotect.database.ConsumerWriteBatch;
import java.sql.Statement;
import java.util.List;

import org.bukkit.Material;
import org.bukkit.block.BlockState;

import net.coreprotect.consumer.Consumer;
import net.coreprotect.consumer.QueuePayload;
import net.coreprotect.database.Lookup;
import net.coreprotect.database.logger.BlockBreakLogger;

class NaturalBlockBreakProcess {

    static void process(Statement statement, ConsumerWriteBatch preparedStmt, int batchCount, int processId, int id, String user, Object object, Material blockType, int blockData, String overrideData) {
        if (object instanceof BlockState) {
            BlockState block = (BlockState) object;
            List<BlockState> blockStateList = QueuePayload.detail(Consumer.consumerObjects.get(processId).get(id));
            if (blockStateList != null) {
                for (BlockState blockState : blockStateList) {
                    String removed = Lookup.whoRemovedCache(blockState);
                    if (removed.length() > 0) {
                        user = removed;
                    }
                }
                BlockBreakLogger.log(preparedStmt, batchCount, user, block.getLocation(), blockType, blockData, null, block.getBlockData().getAsString(), overrideData);
            }
        }
    }
}
