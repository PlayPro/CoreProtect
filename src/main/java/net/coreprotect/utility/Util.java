package net.coreprotect.utility;

import java.util.regex.Pattern;

import org.bukkit.Location;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;

import net.coreprotect.consumer.Queue;

/**
 * Central utility class that provides access to various utility functions.
 * Most methods delegate to specialized utility classes.
 */
public class Util extends Queue {

    public static final Pattern tagParser = Pattern.compile(Chat.COMPONENT_TAG_OPEN + "(.+?)" + Chat.COMPONENT_TAG_CLOSE + "|(.+?)", Pattern.DOTALL);

    private Util() {
        throw new IllegalStateException("Utility class");
    }

    public static void sendBlockChange(Player player, Location location, BlockData blockData) {
        player.sendBlockChange(location, blockData);
    }
}
