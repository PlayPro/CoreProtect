package net.coreprotect.consumer.process;

import java.sql.PreparedStatement;
import java.util.List;

import org.bukkit.Material;
import org.bukkit.block.BlockState;

import net.coreprotect.database.logger.BlockPlaceLogger;
import net.coreprotect.database.logger.SkullPlaceLogger;
import net.coreprotect.utility.BlockUtils;
import net.coreprotect.utility.MaterialUtils;

class BlockPlaceProcess {

    static void process(PreparedStatement preparedStmt, PreparedStatement preparedStmtSkulls, int batchCount, Material blockType, int blockData, Material replaceType, int replaceData, int forceData, String user, Object object, String newBlockData, String replacedBlockData) {
        if (object instanceof BlockState) {
            BlockState block = (BlockState) object;
            List<Object> meta = BlockUtils.processMeta(block);
            if (blockType.equals(Material.SKELETON_SKULL) || blockType.equals(Material.SKELETON_WALL_SKULL) || blockType.equals(Material.WITHER_SKELETON_SKULL) || blockType.equals(Material.WITHER_SKELETON_WALL_SKULL) || blockType.equals(Material.ZOMBIE_HEAD) || blockType.equals(Material.ZOMBIE_WALL_HEAD) || blockType.equals(Material.PLAYER_HEAD) || blockType.equals(Material.PLAYER_WALL_HEAD) || blockType.equals(Material.CREEPER_HEAD) || blockType.equals(Material.CREEPER_WALL_HEAD) || blockType.equals(Material.DRAGON_HEAD) || blockType.equals(Material.DRAGON_WALL_HEAD)) {
                SkullPlaceLogger.log(preparedStmt, preparedStmtSkulls, batchCount, user, block, MaterialUtils.getBlockId(replaceType), replaceData);
            }
            else if (forceData == 1) {
                BlockPlaceLogger.log(preparedStmt, batchCount, user, block, MaterialUtils.getBlockId(replaceType), replaceData, blockType, blockData, true, meta, newBlockData, replacedBlockData);
            }
            else {
                BlockPlaceLogger.log(preparedStmt, batchCount, user, block, MaterialUtils.getBlockId(replaceType), replaceData, blockType, blockData, false, meta, newBlockData, replacedBlockData);
            }
        }
    }
}
