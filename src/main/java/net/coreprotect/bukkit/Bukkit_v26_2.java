package net.coreprotect.bukkit;

import org.bukkit.Material;

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

}
