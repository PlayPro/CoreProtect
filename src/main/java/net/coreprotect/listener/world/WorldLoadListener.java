package net.coreprotect.listener.world;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldLoadEvent;

import net.coreprotect.CoreProtect;
import net.coreprotect.thread.Scheduler;
import net.coreprotect.utility.WorldUtils;

public final class WorldLoadListener implements Listener {

    @EventHandler(priority = EventPriority.MONITOR)
    protected void onWorldLoad(WorldLoadEvent event) {
        // Resolve the id off-thread so the first logged event in a new world never waits on a database lookup
        String worldName = event.getWorld().getName();
        Scheduler.runTaskAsynchronously(CoreProtect.getInstance(), () -> WorldUtils.getWorldId(worldName));
    }

}
