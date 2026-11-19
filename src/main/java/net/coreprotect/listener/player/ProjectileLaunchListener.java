package net.coreprotect.listener.player;

import java.util.Locale;
import java.util.Objects;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Trident;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.CrossbowMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.projectiles.ProjectileSource;

import net.coreprotect.bukkit.BukkitAdapter;
import net.coreprotect.config.Config;
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.consumer.Queue;
import net.coreprotect.database.logger.ItemLogger;
import net.coreprotect.paper.PaperAdapter;
import net.coreprotect.utility.BlockUtils;
import net.coreprotect.utility.EntityUtils;

public final class ProjectileLaunchListener extends Queue implements Listener {

    public static void playerLaunchProjectile(Location location, String user, ItemStack itemStack, int amount, int delay, int offset, int action) {
        if (!Config.getConfig(location.getWorld()).ITEM_DROPS || itemStack == null) {
            return;
        }

        String loggingItemId = user.toLowerCase(Locale.ROOT) + "." + location.getBlockX() + "." + location.getBlockY() + "." + location.getBlockZ();

        itemStack = itemStack.clone();
        if (amount > 0) {
            itemStack.setAmount(amount);
        }

        int itemId;
        if (action == ItemLogger.ITEM_SHOOT) {
            itemId = addPendingItems(ConfigHandler.itemsShot, loggingItemId, itemStack);
        }
        else {
            itemId = addPendingItems(ConfigHandler.itemsThrown, loggingItemId, itemStack);
        }

        int time = (int) (System.currentTimeMillis() / 1000L) + delay;
        Queue.queueItemTransaction(user, location.clone(), time, offset, itemId);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    protected void onProjectileLaunch(ProjectileLaunchEvent event) {
        Projectile projectile = event.getEntity();
        ProjectileSource shooter = projectile.getShooter();
        if (shooter == null && projectile instanceof Firework) {
            shooter = PaperAdapter.ADAPTER.getFireworkRider((Firework) projectile);
        }
        if (!(shooter instanceof Player)) {
            return;
        }

        Player player = (Player) shooter;
        String name = player.getName();
        Object[] launch = ConfigHandler.entityBlockMapper.get(name);
        Location location = projectile.getLocation();
        if (launch == null || launch.length < 5 || !Objects.equals(location.getWorld().getUID(), launch[4])) {
            return;
        }
        ItemStack held = (ItemStack) launch[3];
        Material heldType = held.getType();
        boolean bow = heldType == Material.BOW || heldType == Material.CROSSBOW;
        boolean arrow = projectile instanceof AbstractArrow && !(projectile instanceof Trident);
        boolean rocket = projectile instanceof Firework;
        if (bow) {
            if (!arrow && !(heldType == Material.CROSSBOW && rocket)) {
                return;
            }
        }
        else {
            Material launchedType = EntityUtils.getEntityMaterial(projectile);
            if (launchedType != heldType && !(launchedType == Material.SPLASH_POTION && heldType == Material.LINGERING_POTION)) {
                return;
            }
        }
        if (!ConfigHandler.entityBlockMapper.remove(name, launch)) {
            return;
        }

        ItemStack item = bow ? getAmmunition(projectile, held) : held;
        playerLaunchProjectile(location, name, item, 1, 1, 0, bow || rocket ? ItemLogger.ITEM_SHOOT : ItemLogger.ITEM_THROW);
    }

    private static ItemStack getAmmunition(Projectile projectile, ItemStack bow) {
        AbstractArrow arrow = projectile instanceof AbstractArrow ? (AbstractArrow) projectile : null;
        if (arrow != null) {
            ItemStack nativeItem = BukkitAdapter.ADAPTER.getArrowItem(arrow);
            if (nativeItem != null && !BlockUtils.isAir(nativeItem.getType())) {
                return nativeItem;
            }
        }
        if (bow.getType() == Material.CROSSBOW) {
            ItemMeta meta = bow.getItemMeta();
            if (meta instanceof CrossbowMeta) {
                for (ItemStack charged : ((CrossbowMeta) meta).getChargedProjectiles()) {
                    if ((charged.getType() == Material.FIREWORK_ROCKET) == (arrow == null)) {
                        return charged;
                    }
                }
            }
        }
        return arrow == null ? null : PlayerPickupArrowListener.getArrowType(arrow);
    }

}
