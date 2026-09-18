package net.coreprotect.listener.block;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockFertilizeEvent;

import net.coreprotect.config.Config;
import net.coreprotect.consumer.Queue;
import net.coreprotect.thread.CacheHandler;
import net.coreprotect.utility.TransactionId;

public final class BlockFertilizeListener extends Queue implements Listener {

    private static final Set<Material> MUSHROOM_GROWTH_BLOCKS = mushroomGrowthBlocks();
    private static final int BONEMEAL_DUPLICATE_THRESHOLD = 256;
    private static final int BONEMEAL_DUPLICATE_WINDOW_SECONDS = 900;

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    protected void onBlockFertilize(BlockFertilizeEvent event) {
        if (event.isCancelled()) {
            return;
        }

        Block block = event.getBlock();
        Config config = Config.getConfig(block.getWorld());
        if (!config.BLOCK_PLACE) {
            return;
        }

        Material blockType = block.getType();
        Location location = block.getLocation();
        List<BlockState> blocks = event.getBlocks();
        boolean singleBlockGrowth = blocks.size() == 1 && blocks.get(0).getLocation().equals(location);
        boolean mushroomGrowthBlock = isMushroomGrowthBlock(blockType);

        if (mushroomGrowthBlock && (!config.MUSHROOM_GROWTH || singleBlockGrowth)) {
            return;
        }
        if (!mushroomGrowthBlock && Tag.SAPLINGS.isTagged(blockType) && (!config.TREE_GROWTH || singleBlockGrowth)) {
            return;
        }
        if (blockType == Material.AIR && blocks.size() > 1 && Tag.LOGS.isTagged(blocks.get(1).getType()) && !config.TREE_GROWTH) {
            return;
        }

        String user = "#bonemeal";
        Player player = event.getPlayer();
        if (player != null) {
            user = player.getName();
        }
        else {
            TransactionId key = TransactionId.of(location);
            Object[] data = CacheHandler.redstoneCache.get(key);
            if (data != null) {
                long newTime = System.currentTimeMillis();
                long oldTime = (long) data[0];
                if ((newTime - oldTime) < 50) { // check that within same tick
                    user = (String) data[1];
                }

                CacheHandler.redstoneCache.remove(key);
            }
        }

        // Only growth attributed to a dispenser is skipped. Bone meal applied by other
        // plugins without a player stays as "#bonemeal" and is still logged.
        if (!config.DISPENSERS && "#dispenser".equals(user)) {
            return;
        }

        if (config.DUPLICATE_SUPPRESSION && "#dispenser".equals(user) && shouldSuppressBonemealDuplicate(location, blocks)) {
            return;
        }

        for (BlockState newBlock : blocks) {
            Queue.queueBlockPlace(user, newBlock, newBlock.getType(), newBlock.getBlock().getState(), newBlock.getType(), -1, 0, newBlock.getBlockData().getAsString());
        }
    }

    private static boolean isMushroomGrowthBlock(Material blockType) {
        return MUSHROOM_GROWTH_BLOCKS.contains(blockType);
    }

    private static Set<Material> mushroomGrowthBlocks() {
        Set<Material> materials = EnumSet.of(Material.CRIMSON_FUNGUS, Material.WARPED_FUNGUS);
        for (Material material : Material.values()) {
            if (material.name().contains("MUSHROOM")) {
                materials.add(material);
            }
        }
        return materials;
    }

    private boolean shouldSuppressBonemealDuplicate(Location location, List<BlockState> blocks) {
        if (blocks == null || blocks.isEmpty()) {
            return false;
        }

        // Summing mixed per-block hashes identifies the same set of changes in any order, without building and sorting strings
        long statesHash = 0L;
        for (BlockState newBlock : blocks) {
            long blockHash = ((long) newBlock.getX() * 73856093L) ^ ((long) newBlock.getY() * 19349663L) ^ ((long) newBlock.getZ() * 83492791L);
            blockHash = blockHash * 31 + newBlock.getBlockData().hashCode();
            statesHash += blockHash * 0x9E3779B97F4A7C15L;
        }
        String signature = location.getWorld().getUID() + "." + location.getBlockX() + "." + location.getBlockY() + "." + location.getBlockZ() + "." + Long.toHexString(statesHash);
        return CacheHandler.shouldSuppressRepeat(CacheHandler.bonemealDuplicateCache, signature, BONEMEAL_DUPLICATE_THRESHOLD, BONEMEAL_DUPLICATE_WINDOW_SECONDS);
    }

}
