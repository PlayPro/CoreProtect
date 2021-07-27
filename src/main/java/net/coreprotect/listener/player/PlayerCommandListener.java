package net.coreprotect.listener.player;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import net.coreprotect.config.Config;
import net.coreprotect.consumer.Queue;

public final class PlayerCommandListener extends Queue implements Listener {

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerCommandPreprocess(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (Config.getConfig(player.getWorld()).PLAYER_COMMANDS) {
            long timestamp = System.currentTimeMillis() / 1000L;
            Queue.queuePlayerCommand(player, event.getMessage(), timestamp);
        }
    }
}
