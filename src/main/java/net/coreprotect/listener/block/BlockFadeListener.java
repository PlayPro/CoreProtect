package net.coreprotect.listener.block;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockFadeEvent;

import net.coreprotect.config.Config;
import net.coreprotect.consumer.Queue;
import net.coreprotect.model.BlockGroup;
import net.coreprotect.thread.CacheHandler;

public final class BlockFadeListener extends Queue implements Listener {

    private static final BlockFace[] SCAFFOLDING_DEPENDENTS = {
            BlockFace.UP, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST
    };

    private static String scaffoldingKey(Location location) {
        return "scaffolding." + location.getWorld().getUID() + "." + location.getBlockX() + "." + location.getBlockY() + "." + location.getBlockZ();
    }

    static void trackScaffoldingBreak(String user, Location location) {
        // Use a separate key from placement/gravity attribution. CacheHandler
        // expires these entries after 30 seconds, including surviving scaffolds.
        CacheHandler.lookupCache.put(scaffoldingKey(location), new Object[] {
                (int) (System.currentTimeMillis() / 1000L), user, Material.SCAFFOLDING
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    protected void onBlockFade(BlockFadeEvent event) {
        if (event.isCancelled()) {
            return;
        }

        Block block = event.getBlock();
        Material type = block.getType();
        World world = block.getWorld();

        if (type == Material.SCAFFOLDING) {
            if (!Config.getConfig(world).NATURAL_BREAK || event.getNewState().getType() == type) {
                return;
            }

            Object[] attribution = CacheHandler.lookupCache.remove(scaffoldingKey(block.getLocation()));
            int now = (int) (System.currentTimeMillis() / 1000L);
            String user = attribution != null && now - (int) attribution[0] <= 30 ? (String) attribution[1] : "#physics";

            // Each confirmed collapse passes attribution on to the next tick's
            // dependents, without predicting which scaffolds will lose support.
            for (BlockFace face : SCAFFOLDING_DEPENDENTS) {
                Location dependent = block.getLocation().add(face.getModX(), face.getModY(), face.getModZ());
                trackScaffoldingBreak(user, dependent);
            }

            BlockState state = block.getState();
            Queue.queueBlockBreak(user, state, type, state.getBlockData().getAsString(), 0);
            if (state.getBlockData() instanceof Waterlogged && ((Waterlogged) state.getBlockData()).isWaterlogged()) {
                Queue.queueBlockPlace(user, state, type, null, Material.WATER, -1, 0, null);
            }
        }
        else if (BlockGroup.FIRE.contains(type)) {
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
