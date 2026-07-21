package net.coreprotect.paper;

import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;

public class Paper_v26_2 extends Paper_26_0 {

    @Override
    public boolean isAttached(Block block, Block scanBlock, BlockData blockData, int scanMin) {
        if (isSpeleothem(blockData)) {
            try {
                Object direction = blockData.getClass().getMethod("getVerticalDirection").invoke(blockData);
                if (direction instanceof BlockFace) {
                    BlockFace blockFace = (BlockFace) direction;
                    return scanBlock.getRelative(blockFace.getOppositeFace()).getLocation().equals(block.getLocation());
                }
            }
            catch (ReflectiveOperationException e) {
                return true;
            }
        }

        return true;
    }

    private static boolean isSpeleothem(BlockData blockData) {
        for (Class<?> type : blockData.getClass().getInterfaces()) {
            if ("org.bukkit.block.data.type.Speleothem".equals(type.getName())) {
                return true;
            }
        }
        return false;
    }

}
