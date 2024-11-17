package net.coreprotect.listener.block;

import net.coreprotect.config.Config;
import net.coreprotect.consumer.Queue;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBurnEvent;

public final class BlockBurnListener extends Queue implements Listener {

    @EventHandler(priority = EventPriority.MONITOR)
    protected void onBlockBurn(BlockBurnEvent event) {
        World world = event.getBlock().getWorld();
        if (!event.isCancelled() && Config.getConfig(world).BLOCK_BURN) {
            BlockBreakListener.processBlockBreak(null, "#fire", event.getBlock(), true, BlockUtil.NONE);
        }
    }

}
