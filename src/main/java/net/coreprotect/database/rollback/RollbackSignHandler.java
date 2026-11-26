package net.coreprotect.database.rollback;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;

import net.coreprotect.consumer.Queue;
import net.coreprotect.consumer.process.Process;
import net.coreprotect.model.SignState;
import net.coreprotect.model.rollback.SignRollbackChange;

public final class RollbackSignHandler extends Queue {
    private RollbackSignHandler() {
    }

    public static boolean process(Block block, Object[] row, int rollbackType) {
        if ((Integer) row[9] != rollbackType) {
            return false;
        }
        SignRollbackChange change = (SignRollbackChange) row[14];
        if (change.getBefore() == null || !change.allows(block.getType())) {
            return false;
        }
        BlockState state = block.getState();
        if (!(state instanceof Sign)) {
            return false;
        }
        Sign sign = (Sign) state;
        SignState source = rollbackType == 0 ? change.getAfter() : change.getBefore();
        SignState target = rollbackType == 0 ? change.getBefore() : change.getAfter();
        if (!source.hasChanges(target, change.isFront()) || !SignState.capture(sign).matchesChanges(source, target, change.isFront())) {
            return false;
        }
        target.applyChanges(sign, source, change.isFront());
        return sign.update(false, false);
    }

    public static void processChunk(World world, List<Object[]> rows, int rollbackType, String user, RollbackCounters counters) {
        List<Object[]> updates = new ArrayList<>();
        try {
            for (Object[] row : rows) {
                if (process(world.getBlockAt((Integer) row[3], (Integer) row[4], (Integer) row[5]), row, rollbackType)) {
                    counters.addBlocks(1);
                    updates.add(row);
                }
            }
        }
        finally {
            if (!updates.isEmpty()) {
                queueRollbackUpdate(user, null, updates, Process.SIGN_ROLLBACK_UPDATE, rollbackType);
            }
        }
    }
}
