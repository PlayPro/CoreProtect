package net.coreprotect.listener.block;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Lightable;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockIgniteEvent.IgniteCause;

import net.coreprotect.bukkit.BukkitAdapter;
import net.coreprotect.config.Config;
import net.coreprotect.consumer.Queue;
import net.coreprotect.model.BlockGroup;
import net.coreprotect.thread.CacheHandler;
import net.coreprotect.utility.Util;

public final class BlockIgniteListener extends Queue implements Listener {

    @EventHandler(priority = EventPriority.MONITOR)
    protected void onBlockIgnite(BlockIgniteEvent event) {
        World world = event.getBlock().getWorld();
        if (!event.isCancelled() && Config.getConfig(world).BLOCK_IGNITE) {
            Block block = event.getBlock();
            if (block == null) {
                return;
            }

            Material blockType = block.getType();
            Material blockIgnited = Material.FIRE;
            Material blockBelow = Material.AIR;
            Location scanLocation = block.getLocation().clone();
            if (scanLocation.getY() > BukkitAdapter.ADAPTER.getMinHeight(world)) {
                scanLocation.setY(scanLocation.getY() - 1);
                blockBelow = scanLocation.getBlock().getType();

                if (BlockGroup.SOUL_BLOCKS.contains(blockBelow)) {
                    // Set blockIgnited to SOUL_FIRE
                    for (Material fire : BlockGroup.FIRE) {
                        if (fire != blockIgnited) {
                            blockIgnited = fire;
                            break;
                        }
                    }
                }
            }

            BlockState replacedBlock = null;
            BlockData forceBlockData = block.getBlockData();
            if (BlockGroup.LIGHTABLES.contains(blockType)) {
                // Set block as lit campfire, rather than as fire block
                blockIgnited = blockType;
                replacedBlock = block.getState();
                if (forceBlockData instanceof Lightable) {
                    Lightable lightable = (Lightable) forceBlockData;
                    lightable.setLit(true);
                }
            }

            if (event.getPlayer() == null) {

                if (event.getCause() == IgniteCause.FIREBALL && (blockType == Material.AIR || blockType == Material.CAVE_AIR)) {
                    // Fix bug where fire is recorded as being placed above a campfire (when lit via a fireball from a dispenser)
                    if (BlockGroup.LIGHTABLES.contains(blockBelow)) {
                        return;
                    }
                }

                Queue.queueBlockPlace("#fire", block.getState(), block.getType(), replacedBlock, blockIgnited, -1, 0, forceBlockData.getAsString());
            }
            else {
                Player player = event.getPlayer();
                Queue.queueBlockPlace(player.getName(), block.getState(), block.getType(), replacedBlock, blockIgnited, -1, 0, forceBlockData.getAsString());
                int unixtimestamp = (int) (System.currentTimeMillis() / 1000L);
                int world_id = Util.getWorldId(block.getWorld().getName());
                CacheHandler.lookupCache.put("" + block.getX() + "." + block.getY() + "." + block.getZ() + "." + world_id + "", new Object[] { unixtimestamp, player.getName(), block.getType() });
            }
        }
    }

}
