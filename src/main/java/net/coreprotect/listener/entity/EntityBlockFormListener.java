package net.coreprotect.listener.entity;

import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Snowman;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.EntityBlockFormEvent;

import net.coreprotect.config.Config;
import net.coreprotect.consumer.Queue;

public final class EntityBlockFormListener extends Queue implements Listener {

    @EventHandler(priority = EventPriority.MONITOR)
    protected void onEntityBlockForm(EntityBlockFormEvent event) {
        World world = event.getBlock().getWorld();
        if (!event.isCancelled() && Config.getConfig(world).ENTITY_CHANGE) {
            Entity entity = event.getEntity();
            Block block = event.getBlock();
            BlockState newState = event.getNewState();
            String e = "";
            if (entity instanceof Snowman) {
                e = "#snowman";
            }
            if (e.length() > 0) {
                Queue.queueBlockPlace(e, block.getState(), block.getType(), null, newState.getType(), -1, 0, newState.getBlockData().getAsString());
            }
        }
    }

}
