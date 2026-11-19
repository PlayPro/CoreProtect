package net.coreprotect.command;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.bukkit.WorldEditPlugin;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.world.World;

import net.coreprotect.worldedit.WorldEditLogger;
import net.coreprotect.utility.ErrorReporter;

public class WorldEditHandler {

    protected static Integer[] runWorldEditCommand(CommandSender user) {
        if (!(user instanceof Player)) {
            return null;
        }
        Player player = (Player) user;
        try {
            WorldEditPlugin worldEdit = WorldEditLogger.getWorldEdit(user.getServer());
            if (worldEdit == null) {
                return null;
            }
            LocalSession session = worldEdit.getSession(player);
            World world = session.getSelectionWorld();
            if (world == null || !world.getName().equals(player.getWorld().getName())) {
                return null;
            }
            Region region = session.getSelection(world);
            if (region == null) {
                return null;
            }
            BlockVector3 minimum = region.getMinimumPoint();
            BlockVector3 maximum = region.getMaximumPoint();
            long width = (long) maximum.getBlockX() - minimum.getBlockX() + 1;
            long length = (long) maximum.getBlockZ() - minimum.getBlockZ() + 1;
            int radius = (int) Math.min(Integer.MAX_VALUE, Math.max(width, length) / 2);
            return new Integer[] { radius, minimum.getBlockX(), maximum.getBlockX(), minimum.getBlockY(), maximum.getBlockY(),
                    minimum.getBlockZ(), maximum.getBlockZ(), 1 };
        }
        catch (IncompleteRegionException incomplete) {
            return null;
        }
        catch (Exception e) {
            ErrorReporter.report(e);
        }
        return null;
    }
}
