package net.coreprotect.listener.player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.CrossbowMeta;

import net.coreprotect.config.Config;
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.consumer.Queue;
import net.coreprotect.database.logger.ItemLogger;
import net.coreprotect.utility.Util;

public final class ProjectileLaunchListener extends Queue implements Listener {

    public static Set<Material> BOWS = new HashSet<>(Arrays.asList(Material.BOW, Material.CROSSBOW));

    public static void playerLaunchProjectile(Location location, String user, ItemStack itemStack, int amount, int delay, int offset, int action) {
        if (!Config.getConfig(location.getWorld()).ITEM_DROPS || itemStack == null) {
            return;
        }

        String loggingItemId = user.toLowerCase(Locale.ROOT) + "." + location.getBlockX() + "." + location.getBlockY() + "." + location.getBlockZ();
        int itemId = getItemId(loggingItemId);

        itemStack = itemStack.clone();
        if (amount > 0) {
            itemStack.setAmount(amount);
        }

        if (action == ItemLogger.ITEM_SHOOT) {
            List<ItemStack> list = ConfigHandler.itemsShot.getOrDefault(loggingItemId, new ArrayList<>());
            list.add(itemStack);
            ConfigHandler.itemsShot.put(loggingItemId, list);
        }
        else {
            List<ItemStack> list = ConfigHandler.itemsThrown.getOrDefault(loggingItemId, new ArrayList<>());
            list.add(itemStack);
            ConfigHandler.itemsThrown.put(loggingItemId, list);
        }

        int time = (int) (System.currentTimeMillis() / 1000L) + delay;
        Queue.queueItemTransaction(user, location.clone(), time, offset, itemId);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    protected void onProjectileLaunch(ProjectileLaunchEvent event) {
        Location location = event.getEntity().getLocation();
        String key = location.getWorld().getName() + "-" + location.getBlockX() + "-" + location.getBlockY() + "-" + location.getBlockZ();
        Iterator<Entry<String, Object[]>> it = ConfigHandler.entityBlockMapper.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Object[]> pair = it.next();
            String name = pair.getKey();
            Object[] data = pair.getValue();
            ItemStack itemStack = (ItemStack) data[3];
            Material entityMaterial = Util.getEntityMaterial(event.getEntityType());
            boolean isBow = BOWS.contains(itemStack.getType());
            if ((data[1].equals(key) || data[2].equals(key)) && (entityMaterial == itemStack.getType() || (itemStack.getType() == Material.LINGERING_POTION && entityMaterial == Material.SPLASH_POTION) || isBow)) {
                boolean thrownItem = (itemStack.getType() != Material.FIREWORK_ROCKET && !isBow);
                if (isBow) {
                    if (itemStack.getType() == Material.CROSSBOW) {
                        CrossbowMeta meta = (CrossbowMeta) itemStack.getItemMeta();
                        for (ItemStack item : meta.getChargedProjectiles()) {
                            itemStack = item;
                            break;
                        }
                    }
                    else if (event.getEntity() instanceof AbstractArrow) {
                        itemStack = PlayerPickupArrowListener.getArrowType((AbstractArrow) event.getEntity());
                    }

                    if (itemStack == null || BOWS.contains(itemStack.getType())) {
                        return; // unnecessary under normal circumstances
                    }
                }

                playerLaunchProjectile(location, name, itemStack, 1, 1, 0, (thrownItem ? ItemLogger.ITEM_THROW : ItemLogger.ITEM_SHOOT));
                it.remove();
            }
        }
    }

    // Requires Bukkit 1.17+
    /*
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    protected void onEntityShootBow(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }

        ItemStack itemStack = event.getConsumable();
        playerLaunchProjectile(event.getEntity().getLocation(), event.getEntity().getName(), itemStack, 1, 1, 0, ItemLogger.ITEM_SHOOT);
    }
    */
    // Requires Bukkit 1.16+
    /*
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    protected void onPlayerEggThrow(PlayerEggThrowEvent event) {
        Egg egg = event.getEgg();
        ItemStack itemStack = egg.getItem();
        playerLaunchProjectile(event.getPlayer().getLocation(), event.getPlayer().getName(), itemStack, 0, 1, 0, ItemLogger.ITEM_THROW);
    }
    */

}
