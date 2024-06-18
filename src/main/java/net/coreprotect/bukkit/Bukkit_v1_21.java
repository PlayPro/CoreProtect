package net.coreprotect.bukkit;

import org.bukkit.Material;
import org.bukkit.Tag;

import net.coreprotect.model.BlockGroup;

public class Bukkit_v1_21 extends Bukkit_v1_20 implements BukkitInterface {

    public Bukkit_v1_21() {
        for (Material value : Tag.TRAPDOORS.getValues()) {
            if (value == Material.IRON_TRAPDOOR) {
                continue;
            }

            if (!BlockGroup.INTERACT_BLOCKS.contains(value)) {
                BlockGroup.INTERACT_BLOCKS.add(value);
            }
            if (!BlockGroup.SAFE_INTERACT_BLOCKS.contains(value)) {
                BlockGroup.SAFE_INTERACT_BLOCKS.add(value);
            }
        }
    }

}
