package net.coreprotect.paper;

import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Speleothem;

public class Paper_v26_2 extends Paper_26_0 {

    @Override
    public boolean isAttached(Block block, Block scanBlock, BlockData blockData, int scanMin) {
        if (blockData instanceof Speleothem) {
            Speleothem speleothem = (Speleothem) blockData;
            BlockFace blockFace = speleothem.getVerticalDirection();
            return scanBlock.getRelative(blockFace.getOppositeFace()).getLocation().equals(block.getLocation());
        }

        return true;
    }

}
