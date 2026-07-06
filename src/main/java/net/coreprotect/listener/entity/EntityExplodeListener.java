package net.coreprotect.listener.entity;

import java.util.Locale;

import org.bukkit.World;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.EnderDragonPart;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.Ghast;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.Wither;
import org.bukkit.entity.WitherSkull;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.projectiles.ProjectileSource;

import net.coreprotect.bukkit.BukkitAdapter;
import net.coreprotect.config.Config;
import net.coreprotect.consumer.Queue;
import net.coreprotect.listener.block.BlockExplodeListener;

public final class EntityExplodeListener extends Queue implements Listener {

    @EventHandler(priority = EventPriority.MONITOR)
    protected void onEntityExplode(EntityExplodeEvent event) {
        Entity entity = event.getEntity();

        if (!BukkitAdapter.ADAPTER.shouldLogExplosion(event)){
            return;
        }

        World world = event.getLocation().getWorld();
        String user = getExplosionUser(entity);

        boolean log = false;
        if (Config.getConfig(world).EXPLOSIONS) {
            log = true;
        }

        if ((user.equals("#enderdragon") || user.equals("#wither")) && !Config.getConfig(world).ENTITY_CHANGE) {
            log = false;
        }

        if (!event.isCancelled() && log) {
            BlockExplodeListener.processBlockExplode(user, world, event.blockList());
        }
    }

    static String getExplosionUser(Entity entity) {
        if (entity instanceof TNTPrimed) {
            return "#tnt";
        }
        else if (entity instanceof Minecart) {
            String name = entity.getType().name();
            if (name.contains("TNT")) {
                return "#tnt";
            }
        }
        else if (entity instanceof Creeper) {
            return "#creeper";
        }
        else if (entity instanceof Fireball) {
            return getFireballUser((Fireball) entity);
        }
        else if (entity instanceof EnderDragon || entity instanceof EnderDragonPart) {
            return "#enderdragon";
        }
        else if (entity instanceof Wither || entity instanceof WitherSkull) {
            return "#wither";
        }
        else if (entity instanceof EnderCrystal) {
            return "#end_crystal";
        }

        return "#explosion";
    }

    private static String getFireballUser(Fireball fireball) {
        ProjectileSource shooter = fireball.getShooter();
        if (shooter instanceof Player) {
            return ((Player) shooter).getName();
        }
        else if (shooter instanceof Ghast) {
            return "#ghast";
        }
        else if (shooter instanceof LivingEntity) {
            return "#" + ((LivingEntity) shooter).getType().name().toLowerCase(Locale.ROOT);
        }

        return "#explosion";
    }
}
