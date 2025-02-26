package net.coreprotect.listener.world;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.PortalCreateEvent;

import net.coreprotect.config.Config;
import net.coreprotect.consumer.Queue;
import net.coreprotect.database.Lookup;
import net.coreprotect.utility.BlockUtils;

public final class PortalCreateListener extends Queue implements Listener {

    @EventHandler(priority = EventPriority.MONITOR)
    protected void onPortalCreate(PortalCreateEvent event) {
        World world = event.getWorld();
        if (event.isCancelled() || !Config.getConfig(world).PORTALS) {
            return;
        }

        String user = "#portal";
        for (BlockState block : event.getBlocks()) {
            Material type = block.getType();
            if (type == Material.NETHER_PORTAL || type == Material.FIRE) {
                String resultData = Lookup.whoPlacedCache(block);
                if (resultData.length() > 0) {
                    user = resultData;
                    break;
                }
            }
        }

        for (BlockState blockState : event.getBlocks()) {
            Material type = blockState.getType();
            BlockState oldBlock = blockState.getBlock().getState();
            if (oldBlock.equals(blockState)) {
                continue;
            }

            if (BlockUtils.isAir(type)) {
                Queue.queueBlockBreak(user, oldBlock, oldBlock.getType(), oldBlock.getBlockData().getAsString(), 0);
            }
            else {
                Queue.queueBlockPlace(user, blockState, oldBlock.getType(), oldBlock, type, -1, 0, blockState.getBlockData().getAsString());
            }
        }
    }
}
