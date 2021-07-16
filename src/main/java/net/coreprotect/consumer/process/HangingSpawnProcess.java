package net.coreprotect.consumer.process;

import org.bukkit.Material;
import org.bukkit.block.BlockState;

import net.coreprotect.utility.Util;

class HangingSpawnProcess {

    static void process(Object object, Material type, int data, int delay) {
        if (object instanceof BlockState) {
            BlockState block = (BlockState) object;
            Util.spawnHanging(block, type, data, delay);
        }
    }
}
