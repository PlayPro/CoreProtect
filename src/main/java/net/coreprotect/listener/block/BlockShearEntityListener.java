package net.coreprotect.listener.block;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockShearEntityEvent;

import net.coreprotect.listener.entity.SulfurCubeShearLogger;

public final class BlockShearEntityListener implements Listener {

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    protected void onBlockShearEntity(BlockShearEntityEvent event) {
        SulfurCubeShearLogger.logDrops(event.getEntity(), "#dispenser", SulfurCubeShearLogger.getDrops(event));
    }
}
