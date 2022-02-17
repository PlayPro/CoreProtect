package net.coreprotect.consumer.process;

import org.bukkit.Material;
import org.bukkit.block.BlockState;

import net.coreprotect.utility.entity.HangingUtil;

class HangingSpawnProcess {

    static void process(Object object, Material type, int data, String hangingData, int delay) {
        if (object instanceof BlockState) {
            BlockState block = (BlockState) object;
            HangingUtil.spawnHanging(block, type, hangingData, data, delay);
        }
    }
}
