package net.coreprotect.listener.entity;

import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntitySpawnEvent;

import net.coreprotect.thread.CacheHandler;
import net.coreprotect.utility.WorldUtils;

public final class EntitySpawnListener implements Listener {

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    protected void onEntitySpawn(EntitySpawnEvent event) {
        if (event.getEntityType() != EntityType.FALLING_BLOCK) {
            return;
        }

        Location location = event.getLocation();
        if (location.getWorld() == null) {
            return;
        }

        int worldId = WorldUtils.getWorldId(location.getWorld().getName());
        String originKey = location.getBlockX() + "." + location.getBlockY() + "." + location.getBlockZ() + "." + worldId;
        int timestamp = (int) (System.currentTimeMillis() / 1000L);

        CacheHandler.fallingBlockSpawnCache.put(event.getEntity().getUniqueId().toString(), new Object[] { timestamp, originKey });
    }

}
