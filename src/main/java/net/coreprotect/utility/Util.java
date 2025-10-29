package net.coreprotect.utility;

import java.util.regex.Pattern;

import org.bukkit.Location;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;

import net.coreprotect.CoreProtect;
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.thread.Scheduler;

/**
 * Central utility class that provides access to various utility functions.
 * Most methods delegate to specialized utility classes.
 */
public class Util {

    public static final Pattern tagParser = Pattern.compile(Chat.COMPONENT_TAG_OPEN + "(.+?)" + Chat.COMPONENT_TAG_CLOSE + "|(.+?)", Pattern.DOTALL);

    private Util() {
        throw new IllegalStateException("Utility class");
    }

    public static void sendBlockChange(Player player, Location location, BlockData blockData) {
        // folia: wrapped sendBlockChange in a scheduler task
        if (ConfigHandler.isFolia) {
            Scheduler.runTask(CoreProtect.getInstance(), () -> player.sendBlockChange(location, blockData), player);
        }
        else {
            player.sendBlockChange(location, blockData);
        }
    }
}
