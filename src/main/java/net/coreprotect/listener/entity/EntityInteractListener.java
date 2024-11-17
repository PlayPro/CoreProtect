package net.coreprotect.listener.entity;

import net.coreprotect.config.Config;
import net.coreprotect.consumer.Queue;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityInteractEvent;

import java.util.Locale;

public final class EntityInteractListener extends Queue implements Listener {

    @EventHandler(priority = EventPriority.MONITOR)
    protected void onEntityInteractEntity(EntityInteractEvent event) {
        Block block = event.getBlock();
        World world = block.getWorld();
        if (event.isCancelled() || !Config.getConfig(world).ENTITY_CHANGE) {
            return;
        }
        if (!block.getType().equals(Material.TURTLE_EGG)) {
            return;
        }

        EntityType entityType = event.getEntityType();
        String user = "#entity";
        if (entityType != null) {
            user = "#" + entityType.name().toLowerCase(Locale.ROOT);
        }

        Queue.queueBlockBreak(user, block.getState(), block.getType(), block.getBlockData().getAsString(), 0);
        Queue.queueBlockPlaceDelayed(user, block.getLocation(), block.getType(), null, null, 0);
    }

}
