package net.coreprotect.listener.block;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPistonEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;

import net.coreprotect.config.Config;
import net.coreprotect.consumer.Queue;
import net.coreprotect.thread.CacheHandler;
import net.coreprotect.utility.WorldUtils;

public final class BlockPistonListener extends Queue implements Listener {

    protected void onBlockPiston(BlockPistonEvent event) {
        List<Block> event_blocks = null;
        if (event instanceof BlockPistonExtendEvent) {
            event_blocks = ((BlockPistonExtendEvent) event).getBlocks();
        }
        else if (event instanceof BlockPistonRetractEvent) {
            event_blocks = ((BlockPistonRetractEvent) event).getBlocks();
        }

        World world = event.getBlock().getWorld();
        if (Config.getConfig(world).PISTONS && !event.isCancelled()) {
            List<Block> nblocks = new ArrayList<>();
            List<Block> blocks = new ArrayList<>();

            for (Block block : event_blocks) {
                Block block_relative = block.getRelative(event.getDirection());
                nblocks.add(block_relative);
                blocks.add(block);
            }

            Block b = event.getBlock();
            BlockFace d = event.getDirection();
            Block bm = b.getRelative(d);
            int wid = WorldUtils.getWorldId(bm.getWorld().getName());

            int unixtimestamp = (int) (System.currentTimeMillis() / 1000L);
            int log = 0;
            int l = 0;
            while (l <= nblocks.size()) {
                int ll = l - 1;
                Block n = null;
                if (ll == -1) {
                    n = bm;
                }
                else {
                    n = nblocks.get(ll);
                }
                if (n != null) {
                    int x = n.getX();
                    int y = n.getY();
                    int z = n.getZ();
                    Material t = n.getType();
                    String cords = "" + x + "." + y + "." + z + "." + wid + "." + t.name() + "";
                    if (CacheHandler.pistonCache.get(cords) == null) {
                        log = 1;
                    }
                    CacheHandler.pistonCache.put(cords, new Object[] { unixtimestamp });
                }
                l++;
            }
            if (log == 1) {
                String e = "#piston";
                for (Block block : blocks) {
                    BlockBreakListener.processBlockBreak(null, e, block, true, BlockUtil.NONE);
                }
                // Queue.queueBlockPlaceDelayed(e,bm,null,20);

                int c = 0;
                for (Block nblock : nblocks) {
                    BlockState block = blocks.get(c).getState();
                    queueBlockPlaceValidate(e, nblock.getState(), nblock, null, block.getType(), -1, 0, block.getBlockData().getAsString(), 3);
                    c++;
                }
            }
        }
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
