package net.coreprotect.bukkit;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Speleothem;

import net.coreprotect.model.BlockGroup;

/**
 * Bukkit adapter implementation for Minecraft 26.2.
 */
public class Bukkit_v26_2 extends Bukkit_v1_21 {

    public Bukkit_v26_2() {
        initializeBlockGroups();
    }

    private void initializeBlockGroups() {
        BlockGroup.TRACK_TOP_BOTTOM.add(Material.SULFUR_SPIKE);
    }

    @Override
    public boolean isAttached(Block block, Block scanBlock, BlockData blockData, int scanMin) {
        if (blockData instanceof Speleothem) {
            Speleothem speleothem = (Speleothem) blockData;
            BlockFace blockFace = speleothem.getVerticalDirection();
            return scanBlock.getRelative(blockFace.getOppositeFace()).getLocation().equals(block.getLocation());
        }

        return super.isAttached(block, scanBlock, blockData, scanMin);
    }

}
