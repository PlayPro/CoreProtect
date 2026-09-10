package net.coreprotect.listener.block;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBurnEvent;

import net.coreprotect.config.Config;
import net.coreprotect.consumer.Queue;

public final class BlockBurnListener extends Queue implements Listener {

    @EventHandler(priority = EventPriority.MONITOR)
    protected void onBlockBurn(BlockBurnEvent event) {
        World world = event.getBlock().getWorld();
        if (!event.isCancelled() && Config.getConfig(world).BLOCK_BURN) {
            Block block = event.getBlock();
            if (block.getType() == Material.TNT && TNTPrimeUtil.useTNTPrimeEvent) {
                return;
            }

            BlockBreakListener.processBlockBreak(null, "#fire", block, true, BlockUtil.NONE);
        }
    }

}
