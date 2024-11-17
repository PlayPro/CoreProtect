package net.coreprotect.listener.block;

import net.coreprotect.config.Config;
import net.coreprotect.consumer.Queue;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockFadeEvent;

public final class BlockFadeListener extends Queue implements Listener {

    @EventHandler
    protected void onBlockFade(BlockFadeEvent event) {
        // snow/ice fading
        if (event.isCancelled()) {
            return;
        }

        Block block = event.getBlock();
        if (block.getType().equals(Material.TURTLE_EGG)) {
            World world = block.getWorld();
            if (!Config.getConfig(world).ENTITY_CHANGE) {
                return;
            }

            Queue.queueBlockBreak("#turtle", block.getState(), block.getType(), block.getBlockData().getAsString(), 0);
        }
    }

}
