package net.coreprotect.listener.world;

import java.util.Locale;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.PortalCreateEvent;

import net.coreprotect.config.Config;
import net.coreprotect.consumer.Queue;
import net.coreprotect.thread.CacheHandler;
import net.coreprotect.utility.BlockUtils;

public final class PortalCreateListener extends Queue implements Listener {

    @EventHandler(priority = EventPriority.MONITOR)
    protected void onPortalCreate(PortalCreateEvent event) {
        World world = event.getWorld();
        if (event.isCancelled() || !Config.getConfig(world).PORTALS) {
            return;
        }

        String user = resolveActor(event);
        for (BlockState blockState : event.getBlocks()) {
            Material type = blockState.getType();
            BlockState oldBlock = blockState.getBlock().getState();
            if (oldBlock.getType() == type && oldBlock.getBlockData().equals(blockState.getBlockData())) {
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

    private static String resolveActor(PortalCreateEvent event) {
        Entity entity = event.getEntity();
        if (entity instanceof Player) {
            return entity.getName();
        }
        if (entity != null) {
            return "#" + entity.getType().name().toLowerCase(Locale.ROOT);
        }
        for (BlockState block : event.getBlocks()) {
            Material type = block.getType();
            if (type == Material.NETHER_PORTAL || type == Material.FIRE) {
                String actor = CacheHandler.getBlockSourceActor(event.getWorld(), block.getX(), block.getY(), block.getZ());
                if (!actor.isEmpty()) {
                    return actor;
                }
            }
        }
        return "#portal";
    }
}
