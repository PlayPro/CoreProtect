package net.coreprotect.listener.entity;

import org.bukkit.World;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.EnderDragonPart;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.minecart.ExplosiveMinecart;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityExplodeEvent;

import net.coreprotect.bukkit.BukkitAdapter;
import net.coreprotect.config.Config;
import net.coreprotect.listener.block.BlockExplodeListener;
import net.coreprotect.utility.EntityUtils;

public final class EntityExplodeListener implements Listener {

    @EventHandler(priority = EventPriority.MONITOR)
    protected void onEntityExplode(EntityExplodeEvent event) {
        Entity entity = event.getEntity();

        if (entity instanceof ExplosiveMinecart) {
            VehicleDestroyListener.logTntExplosion((ExplosiveMinecart) entity);
        }

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
        if (entity instanceof TNTPrimed || entity instanceof ExplosiveMinecart) {
            return "#tnt";
        }
        else if (entity instanceof Fireball) {
            String user = EntityUtils.getLivingProjectileShooterUser((Fireball) entity);
            return user == null ? "#explosion" : user;
        }
        else if (entity instanceof EnderDragon || entity instanceof EnderDragonPart) {
            return "#enderdragon";
        }
        else if (entity instanceof EnderCrystal) {
            return "#end_crystal";
        }

        String user = EntityUtils.getEntityTypeUser(entity);
        return user == null ? "#explosion" : user;
    }
}
