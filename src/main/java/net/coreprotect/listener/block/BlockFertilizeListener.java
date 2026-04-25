package net.coreprotect.listener.block;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

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

public final class BlockFertilizeListener extends Queue implements Listener {

    private static final int BONEMEAL_DUPLICATE_THRESHOLD = 256;
    private static final int BONEMEAL_DUPLICATE_WINDOW_SECONDS = 900;

    @EventHandler(priority = EventPriority.MONITOR)
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
            String key = CacheHandler.locationKey(location);
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

        if (config.DUPLICATE_SUPPRESSION && "#dispenser".equals(user) && shouldSuppressBonemealDuplicate(location, blocks)) {
            return;
        }

        for (BlockState newBlock : blocks) {
            Queue.queueBlockPlace(user, newBlock, newBlock.getType(), newBlock.getBlock().getState(), newBlock.getType(), -1, 0, newBlock.getBlockData().getAsString());
        }
    }

    private static boolean isMushroomGrowthBlock(Material blockType) {
        return blockType == Material.CRIMSON_FUNGUS || blockType == Material.WARPED_FUNGUS || blockType.name().toLowerCase(Locale.ROOT).contains("mushroom");
    }

    private boolean shouldSuppressBonemealDuplicate(Location location, List<BlockState> blocks) {
        if (blocks == null || blocks.isEmpty()) {
            return false;
        }

        List<String> states = new ArrayList<>();
        for (BlockState newBlock : blocks) {
            Location newLocation = newBlock.getLocation();
            states.add(newLocation.getBlockX() + "." + newLocation.getBlockY() + "." + newLocation.getBlockZ() + "." + newBlock.getType().name() + "." + newBlock.getBlockData().getAsString());
        }
        Collections.sort(states);
        String signature = location.getWorld().getUID().toString() + "." + location.getBlockX() + "." + location.getBlockY() + "." + location.getBlockZ() + "." + Integer.toHexString(String.join("|", states).hashCode());
        return CacheHandler.shouldSuppressRepeat(CacheHandler.bonemealDuplicateCache, signature, BONEMEAL_DUPLICATE_THRESHOLD, BONEMEAL_DUPLICATE_WINDOW_SECONDS);
    }

}
