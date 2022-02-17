package net.coreprotect.consumer.process;

import java.sql.PreparedStatement;
import java.util.List;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.BlockState;
import org.bukkit.block.Skull;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.type.Door;

import net.coreprotect.database.logger.BlockBreakLogger;
import net.coreprotect.database.logger.SkullBreakLogger;
import net.coreprotect.model.BlockGroup;
import net.coreprotect.utility.Util;

class BlockBreakProcess {

    static void process(PreparedStatement preparedStmt, PreparedStatement preparedStmtSkulls, int batchCount, int processId, int id, Material blockType, int blockDataId, Material replaceType, int forceData, String user, Object object, String blockData) {
        if (object instanceof BlockState) {
            BlockState block = (BlockState) object;
            List<Object> meta = Util.processMeta(block);
            if (block instanceof Skull) {
                SkullBreakLogger.log(preparedStmt, preparedStmtSkulls, batchCount, user, block);
            }
            else {
                BlockBreakLogger.log(preparedStmt, batchCount, user, block.getLocation(), Util.getBlockId(blockType), blockDataId, meta, block.getBlockData().getAsString(), blockData);
                if (forceData == 5) { // Fix for doors
                    if ((blockType == Material.IRON_DOOR || BlockGroup.DOORS.contains(blockType)) && (replaceType != Material.IRON_DOOR && !BlockGroup.DOORS.contains(replaceType))) {
                        Door door = (Door) block.getBlockData();
                        door.setHalf(Bisected.Half.TOP);
                        blockData = door.getAsString();
                        Location location = block.getLocation();
                        location.setY(location.getY() + 1);
                        BlockBreakLogger.log(preparedStmt, batchCount, user, location, Util.getBlockId(blockType), 0, null, blockData, null);
                    }
                }
            }
        }
    }
}
