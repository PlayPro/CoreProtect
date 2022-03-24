package net.coreprotect.listener.block;

import net.coreprotect.database.Placed;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockFormEvent;

import net.coreprotect.config.Config;
import net.coreprotect.consumer.Queue;
import net.coreprotect.database.Lookup;
import net.coreprotect.thread.CacheHandler;
import net.coreprotect.utility.Util;

public final class BlockFormListener extends Queue implements Listener {

    @EventHandler(priority = EventPriority.MONITOR)
    protected void onBlockForm(BlockFormEvent event) {
        // random form, snow/ice
        World world = event.getBlock().getWorld();
        BlockState newState = event.getNewState();
        if (Config.getConfig(world).LIQUID_TRACKING && (newState.getType().equals(Material.OBSIDIAN) || newState.getType().equals(Material.COBBLESTONE) || event.getBlock().getType().name().endsWith("_CONCRETE_POWDER"))) {
            Block block = event.getBlock();
            String player = Placed.whoPlacedCache(block);
            int wid = Util.getWorldId(world.getName());
            if (!(player.length() > 0)) {
                int x = block.getX();
                int y = block.getY();
                int z = block.getZ();
                String coords = "";
                for (int i = 0; i < 4; i++) {
                    switch (i) {
                        case 0:
                            coords = "" + (x + 1) + "." + y + "." + z + "." + wid + "";
                            break;
                        case 1:
                            coords = "" + (x - 1) + "." + y + "." + z + "." + wid + "";
                            break;
                        case 2:
                            coords = "" + x + "." + y + "." + (z + 1) + "." + wid + "";
                            break;
                        case 3:
                            coords = "" + x + "." + y + "." + (z - 1) + "." + wid + "";
                            break;
                    }
                    Object[] data = CacheHandler.lookupCache.get(coords);
                    if (data != null) {
                        String placed = (String) data[1];
                        Material fromType = (Material) data[2];
                        if (fromType.equals(Material.WATER) || fromType.equals(Material.LAVA)) {
                            player = placed;
                            break;
                        }
                    }
                }
            }
            if (player.length() > 0) {
                boolean log = true;
                /*
                if (newState.getType().equals(Material.COBBLESTONE)) {
                    log = false;
                    int unixtimestamp = (int) (System.currentTimeMillis() / 1000L);
                    int x = block.getX();
                    int y = block.getY();
                    int z = block.getZ();
                    String cords = "" + x + "." + y + "." + z + "." + wid + "." + newState.getType().name() + "";
                    if (ConfigHandler.cobble_cache.get(cords) == null) {
                        log = true;
                    }
                    ConfigHandler.cobble_cache.put(cords, new Object[] { unixtimestamp });
                }
                */
                if (log) {
                    Queue.queueBlockPlace(player, block.getLocation().getBlock().getState(), block.getType(), block.getState(), newState.getType(), -1, 0, newState.getBlockData().getAsString());
                }
            }
        }
    }

}
