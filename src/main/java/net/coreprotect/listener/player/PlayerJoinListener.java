package net.coreprotect.listener.player;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import net.coreprotect.config.Config;
import net.coreprotect.consumer.Queue;

public final class PlayerJoinListener extends Queue implements Listener {

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        int time = (int) (System.currentTimeMillis() / 1000L);

        // Pass checkConfig to Process.java, to allow logging of UUIDs
        Queue.queuePlayerLogin(player, time, Config.getConfig(player.getWorld()).PLAYER_SESSIONS ? 1 : 0, Config.getConfig(player.getWorld()).USERNAME_CHANGES ? 1 : 0);
    }
}
