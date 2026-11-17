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
        long now = System.currentTimeMillis() / 1000L;
        ConfigHandler.populatedChunks.compute(event.getWorld().getUID(), (world, chunks) -> {
            ConcurrentHashMap<Long, Long> map = chunks != null ? chunks : new ConcurrentHashMap<>();
            map.put(chunkKey, now);
            return map;
        });
    }

}
