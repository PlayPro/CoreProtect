package net.coreprotect.listener.block;

import java.util.concurrent.ConcurrentHashMap;

import net.coreprotect.config.Config;
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.consumer.Queue;
import net.coreprotect.database.Lookup;
import net.coreprotect.thread.CacheHandler;
import net.coreprotect.utility.WorldUtils;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Levelled;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockFromToEvent;

public final class BlockFromToListener extends Queue implements Listener {

    private static final int FLOW_DUPLICATE_THRESHOLD = 512;
    private static final int FLOW_DUPLICATE_WINDOW_SECONDS = 900;

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    protected void onBlockFromTo(BlockFromToEvent event) {
        Block block = event.getBlock();
        Material type = block.getType(); // old block type
        World world = block.getWorld();
        Config config = Config.getConfig(world);

        if (!config.WATER_FLOW && !config.LAVA_FLOW && type != Material.DRAGON_EGG) {
            return;
        }

        BlockData blockData = block.getBlockData();
        if (blockData instanceof Waterlogged) {
            Waterlogged waterlogged = (Waterlogged) blockData;
            if (waterlogged.isWaterlogged()) {
                type = Material.WATER;
                blockData = type.createBlockData();
            }
        }

        if ((config.WATER_FLOW && type.equals(Material.WATER)) || (config.LAVA_FLOW && type.equals(Material.LAVA))) {
            Block toBlock = event.getToBlock();

            if (blockData instanceof Levelled) {
                Levelled levelled = (Levelled) blockData;
                int waterLevel = levelled.getLevel() + 1;
                if (waterLevel > 8) {
                    waterLevel = waterLevel - 8;
                }
                levelled.setLevel(waterLevel);
                blockData = levelled;
            }

            boolean replaceable = (toBlock.getBlockData() instanceof Waterlogged) || toBlock.isEmpty();

            String f = "#flow";
            if (type.equals(Material.WATER)) {
                f = "#water";
            } else if (type.equals(Material.LAVA)) {
                f = "#lava";
            }

            int unixtimestamp = (int) (System.currentTimeMillis() / 1000L);
            int x = toBlock.getX();
            int y = toBlock.getY();
            int z = toBlock.getZ();
            int wid = WorldUtils.getWorldId(world.getName());
            if (config.LIQUID_TRACKING) {
                String p = Lookup.whoPlacedCache(block);
                if (p.length() > 0) {
                    f = p;
                }
            }

            String cacheId = x + "." + y + "." + z + "." + wid;
            if (config.DUPLICATE_SUPPRESSION && f.startsWith("#")) {
                Object[] cacheData = CacheHandler.spreadCache.put(cacheId, new Object[]{unixtimestamp, type});
                if (replaceable && cacheData != null && ((Material) cacheData[1]) == type) {
                    return;
                }
            }

            if (config.DUPLICATE_SUPPRESSION && shouldSuppressFlowDuplicate(f, wid, block, toBlock, type, blockData, replaceable ? null : toBlock)) {
                return;
            }

            // Later form and flow attribution reads this entry even for rows the settling rule drops
            CacheHandler.lookupCache.put(cacheId, new Object[]{unixtimestamp, f, type});
            if (isSettlingInNewChunk(f, world, x, z, unixtimestamp)) {
                return;
            }

            BlockState toBlockSnapshot = toBlock.getState();
            Queue.queueBlockPlace(f, toBlockSnapshot, block.getType(), replaceable ? null : toBlockSnapshot, type, -1, 0, blockData.getAsString());
        } else if (type.equals(Material.DRAGON_EGG)) {
            Location location = block.getLocation();
            int worldId = WorldUtils.getWorldId(location.getWorld().getName());
            int x = location.getBlockX();
            int y = location.getBlockY();
            int z = location.getBlockZ();
            String coordinates = x + "." + y + "." + z + "." + worldId + "." + type.name();
            String user = "#entity";

            Object[] data = CacheHandler.interactCache.get(coordinates);
            if (data != null && data[1] == Material.DRAGON_EGG) {
                long newTime = System.currentTimeMillis();
                long oldTime = (long) data[0];

                if ((newTime - oldTime) < 20) { // 50ms = 1 tick
                    user = (String) data[2];
                }
                CacheHandler.interactCache.remove(coordinates);
            }

            if (config.BLOCK_BREAK) {
                Queue.queueBlockBreak(user, block.getState(), block.getType(), block.getBlockData().getAsString(), 0);
            }
            if (config.BLOCK_PLACE) {
                Block toBlock = event.getToBlock();
                BlockState toBlockState = toBlock.getState();
                if (config.BLOCK_MOVEMENT) {
                    toBlockState = BlockUtil.gravityScan(toBlock.getLocation(), type, user).getState();
                }

                Queue.queueBlockPlace(user, toBlockState, block.getType(), toBlockState, type, -1, 0, blockData.getAsString());
            }
        }
    }

    /**
     * Same rule BlockPlaceLogger applies to settling liquid in freshly generated chunks, checked here so those rows are never built.
     * The consumer applies it later than the flow happens, so the last second of each window stays with BlockPlaceLogger.
     */
    private static boolean isSettlingInNewChunk(String user, World world, int x, int z, long now) {
        boolean water = user.equals("#water");
        if (!water && !user.equals("#lava")) {
            return false;
        }

        ConcurrentHashMap<Long, Long> worldChunks = ConfigHandler.populatedChunks.get(world.getUID());
        if (worldChunks == null) {
            return false;
        }

        Long populatedAt = worldChunks.get((x >> 4) & 0xffffffffL | ((z >> 4) & 0xffffffffL) << 32);
        return populatedAt != null && now - populatedAt < (water ? 60 : 240);
    }

    private boolean shouldSuppressFlowDuplicate(String user, int worldId, Block sourceBlock, Block targetBlock, Material liquidType, BlockData blockData, Block replacedBlock) {
        long signature = worldId;
        signature = signature * 31 + sourceBlock.getX();
        signature = signature * 31 + sourceBlock.getY();
        signature = signature * 31 + sourceBlock.getZ();
        signature = signature * 31 + targetBlock.getX();
        signature = signature * 31 + targetBlock.getY();
        signature = signature * 31 + targetBlock.getZ();
        signature = signature * 31 + user.hashCode();
        signature = signature * 31 + liquidType.ordinal();
        signature = signature * 31 + blockData.hashCode();
        if (replacedBlock != null) {
            signature = signature * 31 + replacedBlock.getType().ordinal();
            signature = signature * 31 + replacedBlock.getBlockData().hashCode();
        }

        return CacheHandler.shouldSuppressRepeat(CacheHandler.flowDuplicateCache, Long.toHexString(signature), FLOW_DUPLICATE_THRESHOLD, FLOW_DUPLICATE_WINDOW_SECONDS);
    }

}
