package net.coreprotect.consumer.process;

import org.bukkit.block.BlockState;

import net.coreprotect.utility.Util;

class HangingRemoveProcess {

    static void process(Object object, int delay) {
        if (object instanceof BlockState) {
            BlockState block = (BlockState) object;
            Util.removeHanging(block, delay);
        }
    }
}
