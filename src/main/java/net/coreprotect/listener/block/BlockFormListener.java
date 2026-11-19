package net.coreprotect.listener.block;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockFormEvent;

import net.coreprotect.config.Config;
import net.coreprotect.consumer.Queue;
import net.coreprotect.thread.CacheHandler;

public final class BlockFormListener extends Queue implements Listener {

    private static final BlockFace[] ADJACENT_LIQUID_FACES = { BlockFace.EAST, BlockFace.WEST, BlockFace.SOUTH, BlockFace.NORTH };

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    protected void onBlockForm(BlockFormEvent event) {
        if (event.isCancelled()) {
            return;
        }
        // random form, snow/ice
        Block block = event.getBlock();
        World world = block.getWorld();
        BlockState newState = event.getNewState();
        int x = block.getX();
        int y = block.getY();
        int z = block.getZ();
        boolean log = false;
        if (Config.getConfig(world).LIQUID_TRACKING && (newState.getType().equals(Material.OBSIDIAN) || newState.getType().equals(Material.COBBLESTONE) || block.getType().name().endsWith("_CONCRETE_POWDER"))) {
            String player = CacheHandler.getBlockSourceActor(world, x, y, z);
            if (player.isEmpty()) {
                for (BlockFace face : ADJACENT_LIQUID_FACES) {
                    Object[] data = CacheHandler.getBlockSource(world, x + face.getModX(), y, z + face.getModZ());
                    if (data != null) {
                        String placed = (String) data[1];
                        Material fromType = (Material) data[2];
                        if (fromType == Material.WATER || fromType == Material.LAVA) {
                            player = placed;
                            break;
                        }
                    }
                }
            }
            if (player.length() > 0) {
                log = true;
                BlockState before = block.getState();
                Queue.queueBlockPlace(player, before, block.getType(), before, newState.getType(), -1, 0, newState.getBlockData().getAsString());
            }
        }
        if (!log && Config.getConfig(world).UNKNOWN_LOGGING && CacheHandler.getBlockSourceActor(world, x, y, z).isEmpty()) {
            BlockState before = block.getState();
            Queue.queueBlockPlace("#unknown", before, block.getType(), before, newState.getType(), -1, 0, newState.getBlockData().getAsString());
        }
    }

}
