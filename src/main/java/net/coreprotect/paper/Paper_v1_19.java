package net.coreprotect.paper;

import java.util.concurrent.TimeUnit;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
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
        return entity.getScheduler().execute(plugin, task, retiredTask, Math.max(0L, delayTicks));
    }

    @Override
    public void scheduleSyncDelayedTask(Plugin plugin, Runnable task, Runnable retiredTask, Object regionData, long delayTicks) {
        if (regionData instanceof Location) {
            Location location = (Location) regionData;
            if (delayTicks <= 0L) {
                plugin.getServer().getRegionScheduler().run(plugin, location, value -> task.run());
            }
            else {
                plugin.getServer().getRegionScheduler().runDelayed(plugin, location, value -> task.run(), delayTicks);
            }
        }
        else if (regionData instanceof Entity) {
            Entity entity = (Entity) regionData;
            if (delayTicks <= 0L) {
                entity.getScheduler().run(plugin, value -> task.run(), retiredTask);
            }
            else {
                entity.getScheduler().runDelayed(plugin, value -> task.run(), retiredTask, delayTicks);
            }
        }
        else {
            if (delayTicks <= 0L) {
                plugin.getServer().getGlobalRegionScheduler().run(plugin, value -> task.run());
            }
            else {
                plugin.getServer().getGlobalRegionScheduler().runDelayed(plugin, value -> task.run(), delayTicks);
            }
        }
    }

    @Override
    public Object scheduleSyncRepeatingTask(Plugin plugin, Runnable task, Object regionData, long delayTicks, long periodTicks) {
        long initialDelay = Math.max(1L, delayTicks);
        long period = Math.max(1L, periodTicks);
        if (regionData instanceof Location) {
            Location location = (Location) regionData;
            return plugin.getServer().getRegionScheduler().runAtFixedRate(plugin, location, value -> task.run(), initialDelay, period);
        }
        else if (regionData instanceof Entity) {
            Entity entity = (Entity) regionData;
            return entity.getScheduler().runAtFixedRate(plugin, value -> task.run(), null, initialDelay, period);
        }
        else {
            return plugin.getServer().getGlobalRegionScheduler().runAtFixedRate(plugin, value -> task.run(), initialDelay, period);
        }
    }

    @Override
    public void scheduleAsyncDelayedTask(Plugin plugin, Runnable task, long delayTicks) {
        if (delayTicks <= 0L) {
            plugin.getServer().getAsyncScheduler().runNow(plugin, value -> task.run());
        }
        else {
            plugin.getServer().getAsyncScheduler().runDelayed(plugin, value -> task.run(), delayTicks * 50L, TimeUnit.MILLISECONDS);
        }
    }

    @Override
    public boolean cancelScheduledTask(Object task) {
        if (task instanceof ScheduledTask) {
            ((ScheduledTask) task).cancel();
            return true;
        }
        return false;
    }

}
