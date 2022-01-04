package net.coreprotect.consumer.process;

import org.bukkit.block.BlockState;

import net.coreprotect.utility.entity.HangingUtil;

class HangingRemoveProcess {

    static void process(Object object, int delay) {
        if (object instanceof BlockState) {
            BlockState block = (BlockState) object;
            HangingUtil.removeHanging(block, delay);
        }
    }
}
