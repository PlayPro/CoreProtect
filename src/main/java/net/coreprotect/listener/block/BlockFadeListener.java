package net.coreprotect.listener.block;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Scaffolding;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockFadeEvent;

import net.coreprotect.config.Config;
import net.coreprotect.consumer.Queue;
import net.coreprotect.model.BlockGroup;
import net.coreprotect.thread.CacheHandler;

public final class BlockFadeListener extends Queue implements Listener {

    private static final long SCAFFOLDING_ATTRIBUTION_TTL_MS = 2500L;

    static String scaffoldingCacheKey(World world, int x, int y, int z) {
        return (world != null ? CacheHandler.locationKey(world, x, y, z) : (x + ":" + y + ":" + z)) + ".SCAFFOLDING";
    }

    static void trackScaffoldingBreak(String user, World world, int x, int y, int z) {
        if (user == null || user.isEmpty() || world == null) {
            return;
        }
        CacheHandler.interactCache.put(scaffoldingCacheKey(world, x, y, z), new Object[] { System.currentTimeMillis(), Material.SCAFFOLDING, user });
    }

    private static String consumeScaffoldingBreaker(World world, int x, int y, int z) {
        Object[] data = CacheHandler.interactCache.remove(scaffoldingCacheKey(world, x, y, z));
        if (data != null && data.length >= 3 && data[0] instanceof Long && data[2] instanceof String) {
            if (System.currentTimeMillis() - (Long) data[0] <= SCAFFOLDING_ATTRIBUTION_TTL_MS) {
                return (String) data[2];
            }
        }
        return "#physics";
    }

    @EventHandler(priority = EventPriority.MONITOR)
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
        else if (type == Material.SCAFFOLDING) {
            if (event.getNewState().getType() == Material.SCAFFOLDING) {
                return;
            }

            BlockData blockData = block.getBlockData();
            if (blockData instanceof Scaffolding) {
                Scaffolding scaffolding = (Scaffolding) blockData;
                int maxDistance = scaffolding.getMaximumDistance();
                if (maxDistance > 0 && scaffolding.getDistance() >= maxDistance) {
                    return;
                }
            }

            if (!Config.getConfig(world).NATURAL_BREAK) {
                CacheHandler.interactCache.remove(scaffoldingCacheKey(world, block.getX(), block.getY(), block.getZ()));
                return;
            }

            String user = consumeScaffoldingBreaker(world, block.getX(), block.getY(), block.getZ());
            BlockBreakListener.processBlockBreak(null, user, block, true, BlockUtil.NONE);
        }
    }

}
