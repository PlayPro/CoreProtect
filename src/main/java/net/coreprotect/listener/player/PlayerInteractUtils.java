package net.coreprotect.listener.player;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import net.coreprotect.thread.CacheHandler;
import net.coreprotect.utility.WorldUtils;

public final class PlayerInteractUtils {

    private PlayerInteractUtils() {
        // Utility class, prevent instantiation
    }

    public static void clickedDragonEgg(Player player, Block block) {
        Location location = block.getLocation();
        long time = System.currentTimeMillis();
        int wid = WorldUtils.getWorldId(location.getWorld().getName());
        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();
        String coordinates = x + "." + y + "." + z + "." + wid + "." + Material.DRAGON_EGG.name();
        CacheHandler.interactCache.put(coordinates, new Object[] { time, Material.DRAGON_EGG, player.getName() });
    }

    public static void handleBisectedBlockVisualization(Player player, Block block, World world) {
        int x = block.getX();
        int y = block.getY();
        int z = block.getZ();

        int worldMaxHeight = world.getMaxHeight();
        if (y < (worldMaxHeight - 1)) {
            Block y1 = world.getBlockAt(x, y + 1, z);
            player.sendBlockChange(y1.getLocation(), y1.getBlockData());
        }

        int worldMinHeight = net.coreprotect.bukkit.BukkitAdapter.ADAPTER.getMinHeight(world);
        if (y > worldMinHeight) {
            Block y2 = world.getBlockAt(x, y - 1, z);
            player.sendBlockChange(y2.getLocation(), y2.getBlockData());
        }

        Block x1 = world.getBlockAt(x + 1, y, z);
        Block x2 = world.getBlockAt(x - 1, y, z);
        Block z1 = world.getBlockAt(x, y, z + 1);
        Block z2 = world.getBlockAt(x, y, z - 1);
        player.sendBlockChange(x1.getLocation(), x1.getBlockData());
        player.sendBlockChange(x2.getLocation(), x2.getBlockData());
        player.sendBlockChange(z1.getLocation(), z1.getBlockData());
        player.sendBlockChange(z2.getLocation(), z2.getBlockData());
    }
}
