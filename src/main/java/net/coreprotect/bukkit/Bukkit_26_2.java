package net.coreprotect.bukkit;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Speleothem;

import net.coreprotect.model.BlockGroup;

public class Bukkit_26_2 extends Bukkit_v1_21 {

    public Bukkit_26_2() {
        initializeBlockGroups();
    }

    private void initializeBlockGroups() {
        Material potentSulfur = Material.getMaterial("POTENT_SULFUR");
        if (potentSulfur != null) {
            BlockGroup.UPDATE_STATE.add(potentSulfur);
        }

        Material sulfurSpike = Material.getMaterial("SULFUR_SPIKE");
        if (sulfurSpike != null) {
            BlockGroup.TRACK_TOP_BOTTOM.add(sulfurSpike);
        }
    }

    @Override
    public boolean isAttached(Block block, Block scanBlock, BlockData blockData, int scanMin) {
        if (blockData instanceof Speleothem) {
            Speleothem speleothem = (Speleothem) blockData;
            return scanBlock.getRelative(speleothem.getVerticalDirection().getOppositeFace()).getLocation().equals(block.getLocation());
        }

        return super.isAttached(block, scanBlock, blockData, scanMin);
    }
}
