package net.coreprotect.listener.entity;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Enderman;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Fox;
import org.bukkit.entity.Ravager;
import org.bukkit.entity.Silverfish;
import org.bukkit.entity.Turtle;
import org.bukkit.entity.Wither;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityChangeBlockEvent;

import net.coreprotect.config.Config;
import net.coreprotect.consumer.Queue;
import net.coreprotect.thread.CacheHandler;
import net.coreprotect.utility.WorldUtils;

public final class EntityChangeBlockListener extends Queue implements Listener {

    @EventHandler(priority = EventPriority.MONITOR)
    protected void onEntityChangeBlock(EntityChangeBlockEvent event) {
        World world = event.getBlock().getWorld();
        if (event.isCancelled() || !Config.getConfig(world).ENTITY_CHANGE) {
            return;
        }

        Entity entity = event.getEntity();
        Block block = event.getBlock();
        Material newtype = event.getTo();
        Material type = event.getBlock().getType();

        if (entity instanceof FallingBlock) {
            handleFallingBlock(event, (FallingBlock) entity, block, newtype, type, world);
            return;
        }

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
        else if (entity instanceof Zombie) {
            e = "#zombie";
        }
        else if (entity instanceof Silverfish) {
            if (newtype.equals(Material.AIR) || newtype.equals(Material.CAVE_AIR)) {
                e = "#silverfish";
            }
        }
        else if (entity.getType().name().equals("WIND_CHARGE")) {
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

    private void handleFallingBlock(EntityChangeBlockEvent event, FallingBlock fallingBlock, Block block, Material newtype, Material type, World world) {
        int worldId = WorldUtils.getWorldId(world.getName());

        if (newtype.equals(Material.AIR) || newtype.equals(Material.CAVE_AIR)) {
            if (!Config.getConfig(world).BLOCK_BREAK) {
                return;
            }

            String coordKey = block.getX() + "." + block.getY() + "." + block.getZ() + "." + worldId;
            String user = lookupCachedUser(coordKey, type);
            if (user == null) {
                user = "#gravity";
            }

            Queue.queueBlockBreak(user, block.getState(), type, block.getBlockData().getAsString(), 0);
            return;
        }

        if (!Config.getConfig(world).BLOCK_PLACE) {
            return;
        }

        Object[] originData = CacheHandler.fallingBlockSpawnCache.remove(fallingBlock.getUniqueId().toString());
        String user = "#gravity";
        if (originData != null) {
            String originKey = (String) originData[1];
            String lookupUser = lookupCachedUser(originKey, fallingBlock.getBlockData().getMaterial());
            if (lookupUser != null) {
                user = lookupUser;
            }
        }

        queueBlockPlace(user, block.getState(), type, block.getState(), newtype, -1, 0, event.getBlockData().getAsString());
    }

    private static String lookupCachedUser(String coordKey, Material expectedType) {
        Object[] data = CacheHandler.lookupCache.get(coordKey);
        if (data == null) {
            return null;
        }
        if (expectedType != null && data[2] instanceof Material && !data[2].equals(expectedType)) {
            return null;
        }
        return (String) data[1];
    }

}
