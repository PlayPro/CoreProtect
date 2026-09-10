package net.coreprotect.paper;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

import net.coreprotect.config.ConfigHandler;

public class Paper_v1_19 extends Paper_v1_17 {

    @Override
    public boolean isOwnedByCurrentRegion(Entity entity) {
        return !ConfigHandler.isFolia || Bukkit.isOwnedByCurrentRegion(entity);
    }

    @Override
    public boolean isOwnedByCurrentRegion(World world, int chunkX, int chunkZ) {
        return !ConfigHandler.isFolia || Bukkit.isOwnedByCurrentRegion(world, chunkX, chunkZ);
    }

    @Override
    public boolean executeEntityTask(Plugin plugin, Entity entity, Runnable task, Runnable retiredTask) {
        return executeEntityTask(plugin, entity, task, retiredTask, 0L);
    }

    @Override
    public boolean executeEntityTask(Plugin plugin, Entity entity, Runnable task, Runnable retiredTask, long delayTicks) {
        if (!ConfigHandler.isFolia) {
            return false;
        }
        return entity.getScheduler().execute(plugin, task, retiredTask, delayTicks);
    }

}
