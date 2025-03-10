package net.coreprotect.listener.entity;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Enderman;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Fox;
import org.bukkit.entity.Ravager;
import org.bukkit.entity.Silverfish;
import org.bukkit.entity.Turtle;
import org.bukkit.entity.WindCharge;
import org.bukkit.entity.Wither;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityChangeBlockEvent;

import net.coreprotect.config.Config;
import net.coreprotect.consumer.Queue;

public final class EntityChangeBlockListener extends Queue implements Listener {

    @EventHandler(priority = EventPriority.MONITOR)
    protected void onEntityChangeBlock(EntityChangeBlockEvent event) {
        World world = event.getBlock().getWorld();
        if (!event.isCancelled() && Config.getConfig(world).ENTITY_CHANGE) {
            Entity entity = event.getEntity(); // Can be sand/gravel
            Block block = event.getBlock();
            Material newtype = event.getTo();
            Material type = event.getBlock().getType();
            String e = "";
            if (entity instanceof Enderman) {
                e = "#enderman";
            }
            else if (entity instanceof EnderDragon) {
                e = "#enderdragon";
            }
            else if (entity instanceof Fox) {
                e = "#fox";
            }
            else if (entity instanceof Wither) {
                e = "#wither";
            }
            else if (entity instanceof Turtle) {
                e = "#turtle";
            }
            else if (entity instanceof Ravager) {
                e = "#ravager";
            }
            else if (entity instanceof Silverfish) {
                if (newtype.equals(Material.AIR) || newtype.equals(Material.CAVE_AIR)) {
                    e = "#silverfish";
                }
            }
            else if (entity instanceof WindCharge) {
                e = "#windcharge";
            }
            else if (entity.getType().name().equals("BREEZE_WIND_CHARGE")) {
                e = "#breezewindcharge";
            }
            if (e.length() > 0) {
                if (newtype.equals(Material.AIR) || newtype.equals(Material.CAVE_AIR)) {
                    Queue.queueBlockBreak(e, block.getState(), type, block.getBlockData().getAsString(), 0);
                }
                else {
                    queueBlockPlace(e, block.getState(), type, block.getState(), newtype, -1, 0, event.getBlockData().getAsString());
                }
            }
        }
    }

}
