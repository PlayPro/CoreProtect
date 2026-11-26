package net.coreprotect.bukkit;

import org.bukkit.Material;

import net.coreprotect.model.BlockGroup;

/**
 * Bukkit adapter implementation for Minecraft 26.3.
 */
public class Bukkit_v26_3 extends Bukkit_v26_2 {

    public Bukkit_v26_3() {
        initializeBlockGroups();
    }

    private void initializeBlockGroups() {
        // Not covered by the flowers tag, and breaks with the block below it.
        BlockGroup.TRACK_TOP.add(Material.RED_SHRUB);
        BlockGroup.NON_ATTACHABLE.add(Material.RED_SHRUB);
        BlockGroup.NATURAL_BLOCKS.add(Material.RED_SHRUB);

        // Breaks with the block it is attached to; faces away from it, like a wall torch.
        BlockGroup.TRACK_SIDE.add(Material.SHELF_MUSHROOM);
        BlockGroup.NON_ATTACHABLE.add(Material.SHELF_MUSHROOM);
        BlockGroup.NATURAL_BLOCKS.add(Material.SHELF_MUSHROOM);

        // Not in the beds tag, so it is missing from the fixed list of beds.
        BlockGroup.TRACK_SIDE.add(Material.STRAW_BED);
    }

}
