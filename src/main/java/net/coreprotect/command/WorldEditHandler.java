package net.coreprotect.command;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.bukkit.WorldEditPlugin;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.world.World;

import net.coreprotect.worldedit.WorldEditLogger;

public class WorldEditHandler {

    protected static Integer[] runWorldEditCommand(CommandSender user) {
        Integer[] result = null;
        try {
            WorldEditPlugin worldEdit = WorldEditLogger.getWorldEdit(user.getServer());
            if (worldEdit != null && user instanceof Player) {
                LocalSession session = worldEdit.getSession((Player) user);
                World world = session.getSelectionWorld();
                if (world != null) {
                    Region region = session.getSelection(world);
                    if (region != null && world.getName().equals(((Player) user).getWorld().getName())) {
                        BlockVector3 block = region.getMinimumPoint();
                        int x = block.getBlockX();
                        int y = block.getBlockY();
                        int z = block.getBlockZ();
                        int width = region.getWidth();
                        int height = region.getHeight();
                        int length = region.getLength();
                        int max = width;
                        if (height > max) {
                            max = height;
                        }
                        if (length > max) {
                            max = length;
                        }
                        int xMax = x + (width - 1);
                        int yMax = y + (height - 1);
                        int zMax = z + (length - 1);
                        result = new Integer[] { max, x, xMax, y, yMax, z, zMax, 1 };
                    }
                }
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }
}
