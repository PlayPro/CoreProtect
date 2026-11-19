package net.coreprotect.listener.player;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import net.coreprotect.consumer.Queue;

public final class PlayerChatListener implements Listener {

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Queue.queuePlayerChat(event.getPlayer(), event.getMessage(), System.currentTimeMillis() / 1000L, event.isCancelled());
    }
}
