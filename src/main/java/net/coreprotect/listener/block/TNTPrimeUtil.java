package net.coreprotect.listener.block;

import java.util.Locale;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.projectiles.ProjectileSource;

import net.coreprotect.thread.CacheHandler;
import net.coreprotect.utility.WorldUtils;

public final class TNTPrimeUtil {

    public static boolean useTNTPrimeEvent = false;

    private TNTPrimeUtil() {
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
        if (entity instanceof Player) {
            return ((Player) entity).getName();
        }
        else if (entity instanceof Projectile) {
            ProjectileSource source = ((Projectile) entity).getShooter();
            if (source instanceof Player) {
                return ((Player) source).getName();
            }
            else if (source instanceof LivingEntity && ((LivingEntity) source).getType() != null) {
                return "#" + ((LivingEntity) source).getType().name().toLowerCase(Locale.ROOT);
            }
        }
        else if (entity != null && entity.getType() != null) {
            return "#" + entity.getType().name().toLowerCase(Locale.ROOT);
        }

        return "#tnt";
    }

}
