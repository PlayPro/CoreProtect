package net.coreprotect.listener.block;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockFadeEvent;

import net.coreprotect.config.Config;
import net.coreprotect.consumer.Queue;
import net.coreprotect.model.BlockGroup;

public final class BlockFadeListener extends Queue implements Listener {

    @EventHandler
    protected void onBlockFade(BlockFadeEvent event) {
        if (event.isCancelled()) {
            return;
        }

        Block block = event.getBlock();
        Material type = block.getType();
        World world = block.getWorld();

        if (BlockGroup.FIRE.contains(type)) {
            if (!Config.getConfig(world).FIRE_EXTINGUISH) {
                return;
            }

            BlockState newState = event.getNewState();
            Material newType = newState.getType();
            if (newType == Material.AIR || newType == Material.CAVE_AIR || newType == Material.VOID_AIR) {
                Queue.queueBlockBreak("#fire", block.getState(), type, block.getBlockData().getAsString(), 0);
            }
        }
        else if (type.equals(Material.TURTLE_EGG)) {
            if (!Config.getConfig(world).ENTITY_CHANGE) {
                return;
            }

            Queue.queueBlockBreak("#turtle", block.getState(), type, block.getBlockData().getAsString(), 0);
        }
    }

}
