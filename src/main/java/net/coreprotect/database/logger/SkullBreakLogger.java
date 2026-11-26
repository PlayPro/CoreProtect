package net.coreprotect.database.logger;

import java.util.List;

import org.bukkit.block.BlockState;

import net.coreprotect.database.ConsumerWriteBatch;

public class SkullBreakLogger {

    private SkullBreakLogger() {
        throw new IllegalStateException("Database class");
    }

    public static void log(ConsumerWriteBatch preparedStmt, ConsumerWriteBatch skullBatch, int batchCount, String user,
            BlockState block, List<Object> meta, String blockData) {
        String capturedData = blockData == null ? block.getBlockData().getAsString() : blockData;
        BlockBreakLogger.log(preparedStmt, batchCount, user, block.getLocation(), block.getType(), 0,
                () -> SkullPlaceLogger.storeProfile(skullBatch, block), meta, capturedData, null);
    }
}
