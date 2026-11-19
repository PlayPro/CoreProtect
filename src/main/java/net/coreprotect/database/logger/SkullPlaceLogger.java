package net.coreprotect.database.logger;


import java.util.List;

import org.bukkit.Material;
import org.bukkit.block.BlockState;
import org.bukkit.block.Skull;

import net.coreprotect.database.ConsumerWriteBatch;
import net.coreprotect.database.statement.SkullStatement;
import net.coreprotect.paper.PaperAdapter;
import net.coreprotect.worldedit.WorldEditBlockState;

public class SkullPlaceLogger {

    private SkullPlaceLogger() {
        throw new IllegalStateException("Database class");
    }

    public static void log(ConsumerWriteBatch preparedStmt, ConsumerWriteBatch skullBatch, int batchCount, String user,
            BlockState block, Material type, Material replaceType, int replaceData, List<Object> meta, String newBlockData, String replacedBlockData) {
        BlockPlaceLogger.log(preparedStmt, batchCount, user, block, replaceType, replaceData, type,
                () -> storeProfile(skullBatch, block), true, meta, newBlockData, replacedBlockData);
    }

    static int storeProfile(ConsumerWriteBatch batch, BlockState block) {
        String owner = null;
        String skin = null;
        if (block instanceof Skull) {
            Skull skull = (Skull) block;
            owner = PaperAdapter.ADAPTER.getSkullOwner(skull);
            skin = PaperAdapter.ADAPTER.getSkullSkin(skull);
        }
        else if (block instanceof WorldEditBlockState) {
            WorldEditBlockState snapshot = (WorldEditBlockState) block;
            owner = snapshot.getSkullOwner();
            skin = snapshot.getSkullSkin();
        }
        if ((owner == null || owner.isEmpty()) && (skin == null || skin.isEmpty())) {
            return 0;
        }
        return SkullStatement.insert(batch, (int) (System.currentTimeMillis() / 1000L), owner, skin);
    }
}
