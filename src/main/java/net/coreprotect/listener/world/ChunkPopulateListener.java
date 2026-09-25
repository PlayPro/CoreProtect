package net.coreprotect.listener.world;

import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkPopulateEvent;

import net.coreprotect.config.ConfigHandler;
import net.coreprotect.consumer.Queue;

public final class ChunkPopulateListener extends Queue implements Listener {

    @EventHandler(priority = EventPriority.MONITOR)
    protected void onChunkPopulate(ChunkPopulateEvent event) {
        long chunkKey = event.getChunk().getX() & 0xffffffffL | (event.getChunk().getZ() & 0xffffffffL) << 32;
        ConfigHandler.populatedChunks.computeIfAbsent(event.getWorld().getUID(), world -> new ConcurrentHashMap<>()).put(chunkKey, (System.currentTimeMillis() / 1000L));
    }

}
