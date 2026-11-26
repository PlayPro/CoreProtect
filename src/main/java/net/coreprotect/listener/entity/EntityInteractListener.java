package net.coreprotect.listener.entity;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.TurtleEgg;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityInteractEvent;

import net.coreprotect.CoreProtect;
import net.coreprotect.config.Config;
import net.coreprotect.consumer.Queue;
import net.coreprotect.thread.Scheduler;
import net.coreprotect.utility.BlockUtils;
import net.coreprotect.utility.EntityUtils;

public final class EntityInteractListener implements Listener {

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    protected void onEntityInteractEntity(EntityInteractEvent event) {
        Block block = event.getBlock();
        if (event.isCancelled() || block.getType() != Material.TURTLE_EGG) {
            return;
        }
        Config config = Config.getConfig(block.getWorld());
        if (!config.ENTITY_CHANGE || (!config.BLOCK_BREAK && !config.BLOCK_PLACE)) {
            return;
        }
        BlockState before = block.getState();
        BlockData previousData = before.getBlockData();
        if (!(previousData instanceof TurtleEgg)) {
            return;
        }
        int remaining = ((TurtleEgg) previousData).getEggs() - 1;
        String user = EntityUtils.getBreakUser(event.getEntity(), "#entity");
        Scheduler.runTask(CoreProtect.getInstance(), () -> {
            BlockState after = block.getState();
            BlockData nextData = after.getBlockData();
            boolean observedBreak = remaining == 0 ? BlockUtils.isAir(after.getType())
                : nextData instanceof TurtleEgg && ((TurtleEgg) nextData).getEggs() == remaining;
            if (!observedBreak) {
                return;
            }
            if (config.BLOCK_BREAK) {
                Queue.queueBlockBreak(user, before, Material.TURTLE_EGG, previousData.getAsString(), 0);
            }
            if (config.BLOCK_PLACE && remaining > 0) {
                Queue.queueBlockPlace(user, after, Material.TURTLE_EGG, null, null, -1, 0, nextData.getAsString());
            }
        }, before.getLocation());

    }

}
