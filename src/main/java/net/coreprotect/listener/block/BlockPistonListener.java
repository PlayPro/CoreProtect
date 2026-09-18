package net.coreprotect.listener.block;

import java.util.List;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
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
        Config config = Config.getConfig(world);
        if (config.PISTONS && !event.isCancelled()) {
            List<Block> blocks = event_blocks;
            BlockFace d = event.getDirection();
            Block bm = event.getBlock().getRelative(d);
            int wid = WorldUtils.getWorldId(bm.getWorld().getName());

            boolean duplicateSuppression = config.DUPLICATE_SUPPRESSION;
            // The sweeper only reads the timestamp, so every key from this event can share one value
            Object[] stamp = new Object[] { (int) (System.currentTimeMillis() / 1000L) };
            int log = 0;
            for (int l = -1; l < blocks.size(); l++) {
                Block n = l == -1 ? bm : blocks.get(l).getRelative(d);
                if (!duplicateSuppression || CacheHandler.pistonCache.put(new MovedBlockKey(n.getX(), n.getY(), n.getZ(), wid, n.getType()), stamp) == null) {
                    log = 1;
                }
            }
            if (log == 1) {
                String e = "#piston";
                for (Block block : blocks) {
                    BlockBreakListener.processBlockBreak(null, e, block, true, BlockUtil.NONE);
                }
                // Queue.queueBlockPlaceDelayed(e,bm,null,20);

                for (Block block : blocks) {
                    Block nblock = block.getRelative(d);
                    queueBlockPlaceValidate(e, nblock.getState(), nblock, null, block.getType(), -1, 0, block.getBlockData().getAsString(), 3);
                }
            }
        }
    }

    /**
     * Equal exactly when the old "x.y.z.worldId.TYPE" string keys were equal, without building a string per moved block.
     */
    private static final class MovedBlockKey {
        private final int x;
        private final int y;
        private final int z;
        private final int worldId;
        private final Material type;
        private final int hash;

        private MovedBlockKey(int x, int y, int z, int worldId, Material type) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.worldId = worldId;
            this.type = type;

            int result = x;
            result = 31 * result + y;
            result = 31 * result + z;
            result = 31 * result + worldId;
            result = 31 * result + type.ordinal();
            this.hash = result;
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) {
                return true;
            }
            if (!(object instanceof MovedBlockKey)) {
                return false;
            }

            MovedBlockKey other = (MovedBlockKey) object;
            return x == other.x && y == other.y && z == other.z && worldId == other.worldId && type == other.type;
        }

        @Override
        public int hashCode() {
            return hash;
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
