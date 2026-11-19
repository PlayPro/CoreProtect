package net.coreprotect.listener.block;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Projectile;

import net.coreprotect.config.Config;
import net.coreprotect.consumer.Queue;
import net.coreprotect.thread.CacheHandler;
import net.coreprotect.utility.EntityUtils;
import net.coreprotect.utility.WorldUtils;

public final class TNTPrimeUtil {

    public static volatile boolean useTNTPrimeEvent = false;

    private TNTPrimeUtil() {
    }

    public static void logPrime(Block block, Entity primer, boolean fire) {
        if (block.getType() != Material.TNT || !Config.getConfig(block.getWorld()).EXPLOSIONS) {
            return;
        }

        String user = getUser(primer);
        if (fire) {
            user = getFireUser(block, user);
        }
        Queue.queueBlockBreak(user, block.getState(), Material.TNT, block.getBlockData().getAsString(), 0);
    }

    public static String getFireUser(Block block, String fallback) {
        Block[] blocks = new Block[] {
                block,
                block.getRelative(BlockFace.UP),
                block.getRelative(BlockFace.NORTH),
                block.getRelative(BlockFace.SOUTH),
                block.getRelative(BlockFace.EAST),
                block.getRelative(BlockFace.WEST),
                block.getRelative(BlockFace.DOWN)
        };

        int worldId = WorldUtils.getWorldId(block.getWorld().getName());
        for (Block relative : blocks) {
            Object[] data = CacheHandler.lookupCache.get(relative.getX() + "." + relative.getY() + "." + relative.getZ() + "." + worldId);
            if (data != null && data[1] instanceof String && data[2] instanceof Material && data[1].toString().length() > 0 && !data[1].toString().startsWith("#")) {
                Material type = (Material) data[2];
                if (type == Material.FIRE || type == Material.SOUL_FIRE || type == Material.AIR || type == Material.CAVE_AIR) {
                    return data[1].toString();
                }
            }
        }

        return fallback;
    }

    public static String getUser(Entity entity) {
        String user = entity instanceof Projectile ? EntityUtils.getLivingProjectileShooterUser((Projectile) entity) : EntityUtils.getDirectEntityUser(entity);
        return user == null ? "#tnt" : user;
    }

}
