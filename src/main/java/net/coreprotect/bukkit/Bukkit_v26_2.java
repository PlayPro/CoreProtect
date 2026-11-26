package net.coreprotect.bukkit;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.PointedDripstone;

import net.coreprotect.config.ConfigHandler;
import net.coreprotect.model.BlockGroup;

/**
 * Bukkit adapter implementation for Minecraft 26.2.
 */
public class Bukkit_v26_2 extends Bukkit_v1_21_11 {

    private static final Pattern SULFUR_DIRECTION = Pattern.compile("(?:\\[|,)vertical_direction=(up|down)(?=,|\\])");

    public Bukkit_v26_2() {
        initializeBlockGroups();
    }

    private void initializeBlockGroups() {
        BlockGroup.TRACK_TOP_BOTTOM.add(Material.SULFUR_SPIKE);
        BlockGroup.VERTICAL_TOP_BOTTOM.add(Material.SULFUR_SPIKE);
    }

    @Override
    public boolean isAttached(Block block, Block scanBlock, BlockData blockData, int scanMin) {
        return isAttached(block, scanBlock, blockData, scanMin, ConfigHandler.isPaper);
    }

    boolean isAttached(Block block, Block scanBlock, BlockData blockData, int scanMin, boolean paper) {
        if (paper || blockData instanceof PointedDripstone || blockData == null || blockData.getMaterial() != Material.SULFUR_SPIKE) {
            return super.isAttached(block, scanBlock, blockData, scanMin);
        }

        String state = blockData.getAsString();
        if (state == null) {
            return false;
        }
        Matcher direction = SULFUR_DIRECTION.matcher(state);
        if (!direction.find()) {
            return false;
        }

        BlockFace support = "up".equals(direction.group(1)) ? BlockFace.DOWN : BlockFace.UP;
        return scanBlock.getRelative(support).getLocation().equals(block.getLocation());
    }

}
