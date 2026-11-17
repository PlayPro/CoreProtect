package net.coreprotect.thread;

import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import net.coreprotect.CoreProtect;
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.paper.PaperAdapter;

public class Scheduler {

    private Scheduler() {
        throw new IllegalStateException("Scheduler class");
    }

    public static void scheduleSyncDelayedTask(CoreProtect plugin, Runnable task, Object regionData, int delay) {
        scheduleSyncDelayedTask(plugin, task, null, regionData, delay);
    }

    public static void scheduleSyncDelayedTask(CoreProtect plugin, Runnable task, Runnable retiredTask, Object regionData, int delay) {
        if (ConfigHandler.isFolia) {
            if (PaperAdapter.ADAPTER != null) {
                PaperAdapter.ADAPTER.scheduleSyncDelayedTask(plugin, task, retiredTask, regionData, delay);
            }
        }
        else {
            if (delay <= 0) {
                plugin.getServer().getScheduler().runTask(plugin, task);
            }
            else {
                plugin.getServer().getScheduler().runTaskLater(plugin, task, delay);
            }
        }
    }

    public static Object scheduleSyncRepeatingTask(CoreProtect plugin, Runnable task, Object regionData, int delay, int period) {
        if (ConfigHandler.isFolia) {
            return PaperAdapter.ADAPTER != null ? PaperAdapter.ADAPTER.scheduleSyncRepeatingTask(plugin, task, regionData, delay, period) : null;
        }
        else {
            return plugin.getServer().getScheduler().runTaskTimer(plugin, task, Math.max(0L, delay), Math.max(1L, period));
        }
    }

    public static void scheduleAsyncDelayedTask(CoreProtect plugin, Runnable task, int delay) {
        if (ConfigHandler.isFolia) {
            if (PaperAdapter.ADAPTER != null) {
                PaperAdapter.ADAPTER.scheduleAsyncDelayedTask(plugin, task, delay);
            }
        }
        else {
            if (delay <= 0) {
                plugin.getServer().getScheduler().runTaskAsynchronously(plugin, task);
            }
            else {
                plugin.getServer().getScheduler().runTaskLaterAsynchronously(plugin, task, delay);
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

    public static void runTaskLaterAsynchronously(CoreProtect plugin, Runnable task, int delay) {
        scheduleAsyncDelayedTask(plugin, task, delay);
    }

    public static void cancelTask(Object task) {
        if (task == null) {
            return;
        }
        if (ConfigHandler.isFolia && PaperAdapter.ADAPTER != null && PaperAdapter.ADAPTER.cancelScheduledTask(task)) {
            return;
        }
        if (task instanceof BukkitTask) {
            BukkitTask bukkitTask = (BukkitTask) task;
            bukkitTask.cancel();
        }
        else if (task instanceof Integer) {
            Bukkit.getScheduler().cancelTask((Integer) task);
        }
    }
}
