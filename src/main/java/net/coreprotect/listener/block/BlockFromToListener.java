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

import net.coreprotect.CoreProtect;
import net.coreprotect.bukkit.BukkitAdapter;
import net.coreprotect.config.Config;
import net.coreprotect.consumer.Queue;
import net.coreprotect.database.Lookup;
import net.coreprotect.thread.CacheHandler;
import net.coreprotect.thread.Scheduler;
import net.coreprotect.utility.BlockUtils;
import net.coreprotect.utility.WorldUtils;

public final class BlockFromToListener extends Queue implements Listener {

    private static final int FLOW_DUPLICATE_THRESHOLD = 512;
    private static final int FLOW_DUPLICATE_WINDOW_SECONDS = 900;

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
            Config config = Config.getConfig(world);
            if ((config.WATER_FLOW && type.equals(Material.WATER)) || (config.LAVA_FLOW && type.equals(Material.LAVA))) {
                Block toBlock = event.getToBlock();
                BlockState before = toBlock.getState();
                BlockState toBlockState = BlockUtils.isAir(before.getType()) ? null : before;

                if (blockData instanceof Levelled) {
                    Levelled levelled = (Levelled) blockData;
                    int spread = type == Material.LAVA && !BukkitAdapter.ADAPTER.isUltraWarm(world) ? 2 : 1;
                    levelled.setLevel(flowLevel(levelled.getLevel(), toBlock.getY() < block.getY(), spread));
                }

                String f = "#flow";
                if (type.equals(Material.WATER)) {
                    f = "#water";
                }
                else if (type.equals(Material.LAVA)) {
                    f = "#lava";
                }

                if (config.LIQUID_TRACKING) {
                    String p = Lookup.whoPlacedCache(block);
                    if (p.length() > 0) {
                        f = p;
                    }
                }

                if (config.DUPLICATE_SUPPRESSION && f.startsWith("#")) {
                    String cacheId = toBlock.getX() + "." + toBlock.getY() + "." + toBlock.getZ() + "." + WorldUtils.getWorldId(toBlock.getWorld().getName());
                    int timestamp = (int) (System.currentTimeMillis() / 1000L);
                    Object[] cacheData = CacheHandler.spreadCache.get(cacheId);
                    CacheHandler.spreadCache.put(cacheId, new Object[] { timestamp, type });
                    if (toBlockState == null && cacheData != null && ((Material) cacheData[1]) == type) {
                        return;
                    }
                }

                if (config.DUPLICATE_SUPPRESSION && shouldSuppressFlowDuplicate(f, block, toBlock, type, blockData, toBlockState)) {
                    return;
                }

                if (toBlockState != null && before.getBlockData() instanceof Waterlogged) {
                    String user = f;
                    Material liquid = type;
                    boolean wasWaterlogged = ((Waterlogged) before.getBlockData()).isWaterlogged();
                    String flowData = blockData.getAsString();
                    Scheduler.runTask(CoreProtect.getInstance(), () -> {
                        BlockData after = toBlock.getBlockData();
                        if (after.getMaterial() == liquid) {
                            queueFlow(user, before, before, liquid, after.getAsString());
                        }
                        else if (liquid == Material.WATER && !wasWaterlogged && after.getMaterial() == before.getType()
                                && after instanceof Waterlogged && ((Waterlogged) after).isWaterlogged()) {
                            queueFlow(user, before, null, liquid, flowData);
                        }
                    }, before.getLocation());
                }
                else {
                    queueFlow(f, before, toBlockState, type, blockData.getAsString());
                }
            }
            else if (type.equals(Material.DRAGON_EGG)) {
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

                    if ((newTime - oldTime) >= 0 && (newTime - oldTime) <= 50) {
                        user = (String) data[2];
                    }
                    CacheHandler.interactCache.remove(coordinates);
                }

                if (Config.getConfig(block.getWorld()).BLOCK_BREAK) {
                    Queue.queueBlockBreak(user, block.getState(), block.getType(), block.getBlockData().getAsString(), 0);
                }
                if (Config.getConfig(block.getWorld()).BLOCK_PLACE) {
                    Block toBlock = event.getToBlock();
                    BlockState toBlockState = toBlock.getState();
                    if (Config.getConfig(world).BLOCK_MOVEMENT) {
                        toBlockState = BlockUtil.gravityScan(toBlock.getLocation(), type, user).getState();
                    }

                    Queue.queueBlockPlace(user, toBlockState, block.getType(), toBlockState, type, -1, 0, blockData.getAsString());
                }
            }
        }
    }

    static int flowLevel(int sourceLevel, boolean falling, int spread) {
        return falling ? 8 : Math.min(7, (sourceLevel & 7) + spread);
    }

    private static void queueFlow(String user, BlockState target, BlockState replaced, Material liquid, String data) {
        int worldId = WorldUtils.getWorldId(target.getWorld().getName());
        int timestamp = (int) (System.currentTimeMillis() / 1000L);
        String key = target.getX() + "." + target.getY() + "." + target.getZ() + "." + worldId;
        CacheHandler.lookupCache.put(key, new Object[] { timestamp, user, liquid });
        Queue.queueBlockPlace(user, target, liquid, replaced, liquid, -1, 0, data);
    }

    private boolean shouldSuppressFlowDuplicate(String user, Block sourceBlock, Block targetBlock, Material liquidType, BlockData blockData, BlockState replacedState) {
        String replacedSignature = "-";
        if (replacedState != null) {
            replacedSignature = replacedState.getType().name() + ":" + replacedState.getBlockData().getAsString();
        }

        String signature = sourceBlock.getWorld().getUID().toString() + "." + sourceBlock.getX() + "." + sourceBlock.getY() + "." + sourceBlock.getZ() + ">" + targetBlock.getX() + "." + targetBlock.getY() + "." + targetBlock.getZ() + "." + user + "." + liquidType.name() + "." + blockData.getAsString() + "." + replacedSignature;
        return CacheHandler.shouldSuppressRepeat(CacheHandler.flowDuplicateCache, signature, FLOW_DUPLICATE_THRESHOLD, FLOW_DUPLICATE_WINDOW_SECONDS);
    }

}
