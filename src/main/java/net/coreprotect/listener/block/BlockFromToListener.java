package net.coreprotect.listener.block;

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

import net.coreprotect.config.Config;
import net.coreprotect.consumer.Queue;
import net.coreprotect.database.Lookup;
import net.coreprotect.thread.CacheHandler;
import net.coreprotect.utility.Util;

public final class BlockFromToListener extends Queue implements Listener {

    @EventHandler(priority = EventPriority.MONITOR)
    protected void onBlockFromTo(BlockFromToEvent event) {
        Block block = event.getBlock();
        Material type = block.getType(); // old block type
        if (!event.isCancelled()) {
            BlockData blockData = block.getBlockData();
            if (blockData instanceof Waterlogged) {
                Waterlogged waterlogged = (Waterlogged) blockData;
                if (waterlogged.isWaterlogged()) {
                    type = Material.WATER;
                    blockData = type.createBlockData();
                }
            }

            World world = event.getBlock().getWorld();
            if ((Config.getConfig(world).WATER_FLOW && type.equals(Material.WATER)) || (Config.getConfig(world).LAVA_FLOW && type.equals(Material.LAVA))) {
                Block toBlock = event.getToBlock();
                BlockState toBlockState = toBlock.getState();

                if (blockData instanceof Levelled) {
                    Levelled levelled = (Levelled) blockData;
                    int waterLevel = levelled.getLevel() + 1;
                    if (waterLevel > 8) {
                        waterLevel = waterLevel - 8;
                    }
                    levelled.setLevel(waterLevel);
                    blockData = levelled;
                }

                if ((toBlock.getBlockData() instanceof Waterlogged) || toBlock.isEmpty()) {
                    toBlockState = null;
                }

                String f = "#flow";
                if (type.equals(Material.WATER)) {
                    f = "#water";
                }
                else if (type.equals(Material.LAVA)) {
                    f = "#lava";
                }

                int unixtimestamp = (int) (System.currentTimeMillis() / 1000L);
                int x = toBlock.getX();
                int y = toBlock.getY();
                int z = toBlock.getZ();
                int wid = Util.getWorldId(block.getWorld().getName());
                if (Config.getConfig(world).LIQUID_TRACKING) {
                    String p = Lookup.whoPlacedCache(block);
                    if (p.length() > 0) {
                        f = p;
                    }
                }

                if (f.startsWith("#")) {
                    Location location = toBlock.getLocation();
                    int timestamp = (int) (System.currentTimeMillis() / 1000L);
                    Object[] cacheData = CacheHandler.spreadCache.get(location);
                    CacheHandler.spreadCache.put(location, new Object[] { timestamp, type });
                    if (toBlockState == null && cacheData != null && ((Material) cacheData[1]) == type) {
                        return;
                    }
                }

                CacheHandler.lookupCache.put("" + x + "." + y + "." + z + "." + wid + "", new Object[] { unixtimestamp, f, type });
                Queue.queueBlockPlace(f, toBlock.getState(), block.getType(), toBlockState, type, -1, 0, blockData.getAsString());
            }
        }
    }

}
