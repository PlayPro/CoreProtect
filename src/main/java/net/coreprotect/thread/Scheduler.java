package net.coreprotect.thread;

import java.util.concurrent.TimeUnit;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;

import net.coreprotect.CoreProtect;
import net.coreprotect.config.ConfigHandler;

public class Scheduler {

    private Scheduler() {
        throw new IllegalStateException("Scheduler class");
    }

    public static void scheduleSyncDelayedTask(CoreProtect plugin, Runnable task, Object regionData, int delay) {
        if (ConfigHandler.isFolia) {
            if (regionData instanceof Location) { // REGION
                Location location = (Location) regionData;
                if (delay == 0) {
                    Bukkit.getServer().getRegionScheduler().run(plugin, location, value -> task.run());
                }
                else {
                    Bukkit.getServer().getRegionScheduler().runDelayed(plugin, location, value -> task.run(), delay);
                }
            }
            else if (regionData instanceof Entity) { // ENTITY
                Entity entity = (Entity) regionData;
                if (delay == 0) {
                    entity.getScheduler().run(plugin, value -> task.run(), task);
                }
                else {
                    entity.getScheduler().runDelayed(plugin, value -> task.run(), task, delay);
                }
            }
            else { // GLOBAL
                if (delay == 0) {
                    Bukkit.getServer().getGlobalRegionScheduler().run(plugin, value -> task.run());
                }
                else {
                    Bukkit.getServer().getGlobalRegionScheduler().runDelayed(plugin, value -> task.run(), delay);
                }
            }
        }
        else { // BUKKIT
            if (delay == 0) {
                Bukkit.getServer().getScheduler().runTask(plugin, task);
            }
            else {
                Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(plugin, task, delay);
            }
        }
    }

    public static void scheduleAsyncDelayedTask(CoreProtect plugin, Runnable task, int delay) {
        if (ConfigHandler.isFolia) {
            if (delay == 0) {
                Bukkit.getServer().getAsyncScheduler().runNow(plugin, value -> task.run());
            }
            else {
                Bukkit.getServer().getAsyncScheduler().runDelayed(plugin, value -> task.run(), (delay * 50L), TimeUnit.MILLISECONDS);
            }
        }
        else { // BUKKIT
            if (delay == 0) {
                Bukkit.getServer().getScheduler().runTaskAsynchronously(plugin, task);
            }
            else {
                Bukkit.getServer().getScheduler().runTaskLaterAsynchronously(plugin, task, delay);
            }
        }
    }

    public static void scheduleSyncDelayedTask(CoreProtect plugin, Runnable task, int delay) {
        scheduleSyncDelayedTask(plugin, task, null, delay);
    }

    public static void runTask(CoreProtect plugin, Runnable task) {
        scheduleSyncDelayedTask(plugin, task, null, 0);
    }

    public static void runTask(CoreProtect plugin, Runnable task, Object regionData) {
        scheduleSyncDelayedTask(plugin, task, regionData, 0);
    }

    public static void runTaskAsynchronously(CoreProtect plugin, Runnable task) {
        scheduleAsyncDelayedTask(plugin, task, 0);
    }
}
