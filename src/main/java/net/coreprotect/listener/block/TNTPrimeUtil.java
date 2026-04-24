package net.coreprotect.listener.block;

import java.util.Locale;

import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.projectiles.ProjectileSource;

public final class TNTPrimeUtil {

    private TNTPrimeUtil() {
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
