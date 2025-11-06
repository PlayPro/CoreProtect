package net.coreprotect.listener.block;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import net.coreprotect.bukkit.BukkitAdapter;
import net.coreprotect.model.BlockGroup;
import net.coreprotect.thread.CacheHandler;
import net.coreprotect.utility.WorldUtils;

public class BlockUtil {

    public static final int NONE = 0;
    public static final int TOP = 5;
    public static final int BOTTOM = 6;

    public static boolean verticalBreakScan(Player player, String user, Block block, Block scanBlock, Material scanType, int scanMin) {
        if (!BlockGroup.VERTICAL.contains(scanType)) {
            return false;
        }

        if (scanType != block.getType()) {
            boolean trackTop = BlockGroup.TRACK_TOP_BOTTOM.contains(scanType) || BlockGroup.TRACK_TOP.contains(scanType);
            boolean trackBottom = BlockGroup.TRACK_TOP_BOTTOM.contains(scanType) || BlockGroup.TRACK_BOTTOM.contains(scanType);
            if ((!trackTop && scanMin == TOP) || (!trackBottom && scanMin == BOTTOM)) {
                return false;
            }
        }

        boolean top = BlockGroup.VERTICAL_TOP_BOTTOM.contains(scanType) || BlockGroup.VERTICAL_TOP.contains(scanType);
        boolean bottom = BlockGroup.VERTICAL_TOP_BOTTOM.contains(scanType) || BlockGroup.VERTICAL_BOTTOM.contains(scanType);
        if ((top && scanMin == TOP) || (bottom && scanMin == BOTTOM)) {
            BlockBreakListener.processBlockBreak(player, user, scanBlock, true, (scanMin == BOTTOM ? TOP : BOTTOM));
            return true;
        }

        return false;
    }

    public static Block gravityScan(Location location, Material type, String player) {
        Block block = location.getBlock();
        int timestamp = (int) (System.currentTimeMillis() / 1000L);
        if (type.hasGravity() || type == Material.ARMOR_STAND) {
            int x = location.getBlockX();
            int y = location.getBlockY();
            int z = location.getBlockZ();
            World world = location.getWorld();
            int wid = WorldUtils.getWorldId(world.getName());
            int yc = y - 1;
            // user placing sand/gravel. Find the bottom block
            int bottomfound = 0;
            while (bottomfound == 0) {
                if (yc < BukkitAdapter.ADAPTER.getMinHeight(world)) {
                    block = world.getBlockAt(x, yc + 1, z);
                    bottomfound = 1;
                }
                else {
                    Block block_down = world.getBlockAt(x, yc, z);
                    Material down = block_down.getType();
                    if (!BukkitAdapter.ADAPTER.isInvisible(down) && !down.equals(Material.WATER) && !down.equals(Material.LAVA) && !down.equals(Material.SNOW)) {
                        block = world.getBlockAt(x, yc + 1, z);
                        bottomfound = 1;
                    }
                    else if (down == Material.WATER && type.name().endsWith("_CONCRETE_POWDER")) {
                        block = world.getBlockAt(x, yc, z);
                        bottomfound = 1;
                    }
                    else {
                        String cords = "" + x + "." + yc + "." + z + "." + wid + "";
                        Object[] data = CacheHandler.lookupCache.get(cords);
                        if (data != null) {
                            Material t = (Material) data[2];
                            if (type.equals(t) && type != Material.ARMOR_STAND) {
                                block = world.getBlockAt(x, yc + 1, z);
                                bottomfound = 1;
                            }
                        }
                    }
                    yc--;
                }
            }
            CacheHandler.lookupCache.put("" + x + "." + block.getY() + "." + z + "." + wid + "", new Object[] { timestamp, player, type });
        }

        return block;
    }

}
