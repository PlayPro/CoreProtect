package net.coreprotect.consumer.process;

import net.coreprotect.consumer.Consumer;
import net.coreprotect.database.Lookup;
import net.coreprotect.database.logger.BlockBreakLogger;
import net.coreprotect.utility.Util;
import org.bukkit.Material;
import org.bukkit.block.BlockState;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

class NaturalBlockBreakProcess {

    static void process(Statement statement, PreparedStatement preparedStmt, int batchCount, int processId, int id, String user, Object object, Material blockType, int blockData, String overrideData) {
        if (object instanceof BlockState) {
            BlockState block = (BlockState) object;
            Map<Integer, List<BlockState>> blockLists = Consumer.consumerBlockList.get(processId);
            if (blockLists.get(id) != null) {
                List<BlockState> blockStateList = blockLists.get(id);
                for (BlockState blockState : blockStateList) {
                    String removed = Lookup.whoRemovedCache(blockState);
                    if (removed.length() > 0) {
                        user = removed;
                    }
                }
                blockLists.remove(id);
                BlockBreakLogger.log(preparedStmt, batchCount, user, block.getLocation(), Util.getBlockId(blockType), blockData, null, block.getBlockData().getAsString(), overrideData);
            }
        }
    }
}
