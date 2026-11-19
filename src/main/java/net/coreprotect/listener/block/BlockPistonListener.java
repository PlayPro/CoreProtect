package net.coreprotect.listener.block;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.PistonMoveReaction;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPistonEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;

import net.coreprotect.config.Config;
import net.coreprotect.consumer.Queue;
import net.coreprotect.thread.CacheHandler;
import net.coreprotect.utility.BlockUtils;

public final class BlockPistonListener extends Queue implements Listener {

    protected void onBlockPiston(BlockPistonEvent event) {
        if (event.isCancelled()) {
            return;
        }

        Block piston = event.getBlock();
        World world = piston.getWorld();
        Config config = Config.getConfig(world);
        if (!config.PISTONS) {
            return;
        }

        boolean retracting = event instanceof BlockPistonRetractEvent;
        List<Block> blocks = retracting ? ((BlockPistonRetractEvent) event).getBlocks() : ((BlockPistonExtendEvent) event).getBlocks();
        if (blocks.isEmpty()) {
            return;
        }

        BlockData pistonData = piston.getBlockData();
        BlockFace movement = event.getDirection();
        BlockFace facing = pistonData instanceof Directional ? ((Directional) pistonData).getFacing()
                : retracting ? movement.getOppositeFace() : movement;
        Block head = piston.getRelative(facing);
        Map<Block, BlockState> originals = new LinkedHashMap<>();
        for (Block block : blocks) {
            originals.put(block, block.getState());
        }

        if (config.DUPLICATE_SUPPRESSION && suppressRepeat(piston, movement, retracting, originals)) {
            return;
        }

        Set<Block> recorded = new HashSet<>(originals.keySet());
        recorded.add(head);
        for (Block block : originals.keySet()) {
            BlockBreakListener.processBlockBreak(null, "#piston", block, true, BlockUtil.NONE, recorded);
        }

        for (Map.Entry<Block, BlockState> entry : originals.entrySet()) {
            Block source = entry.getKey();
            if (source.getPistonMoveReaction() == PistonMoveReaction.BREAK) {
                continue;
            }
            Block destination = source.getRelative(movement);
            BlockState before = destination.getState();
            BlockState replaced = originals.containsKey(destination) || destination.equals(head) || BlockUtils.isAir(before.getType()) ? null : before;
            BlockState moved = entry.getValue();
            queueBlockPlaceValidate("#piston", before, destination, replaced, moved.getType(), -1, 0, moved.getBlockData().getAsString(), 3);
        }
    }

    private static boolean suppressRepeat(Block piston, BlockFace direction, boolean retracting, Map<Block, BlockState> originals) {
        StringBuilder key = new StringBuilder(piston.getWorld().getUID().toString()).append('.')
                .append(piston.getX()).append('.').append(piston.getY()).append('.').append(piston.getZ())
                .append('.').append(direction).append('.').append(retracting);
        for (BlockState state : originals.values()) {
            key.append('|').append(state.getX()).append('.').append(state.getY()).append('.').append(state.getZ())
                    .append('=').append(state.getBlockData().getAsString());
        }
        long timestamp = System.currentTimeMillis();
        Object[] previous = CacheHandler.pistonCache.put(key.toString(), new Object[] { (int) (timestamp / 1000L), timestamp });
        return previous != null && timestamp - (long) previous[1] <= 50L;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    protected void onBlockPistonExtend(BlockPistonExtendEvent event) {
        onBlockPiston(event);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    protected void onBlockPistonRetract(BlockPistonRetractEvent event) {
        onBlockPiston(event);
    }

}
