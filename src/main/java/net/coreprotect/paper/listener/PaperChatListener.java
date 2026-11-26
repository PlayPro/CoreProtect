package net.coreprotect.paper.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.coreprotect.consumer.Queue;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

public final class PaperChatListener implements Listener {

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerChat(AsyncChatEvent event) {
        String message = PlainTextComponentSerializer.plainText().serialize(event.message());
        Queue.queuePlayerChat(event.getPlayer(), message, System.currentTimeMillis() / 1000L, event.isCancelled());
    }
}
