package net.coreprotect.listener.player;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Egg;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerEggThrowEvent;
import org.bukkit.inventory.ItemStack;

import net.coreprotect.config.Config;
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.consumer.Queue;
import net.coreprotect.database.logger.ItemLogger;
import net.coreprotect.utility.Util;

public class ProjectileLaunchListener extends Queue implements Listener {

    protected static void playerLaunchProjectile(Location location, String user, ItemStack itemStack, int amount, int delay, int offset, int action) {
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
        Iterator<Entry<UUID, Object[]>> it = ConfigHandler.entityBlockMapper.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Object[]> pair = it.next();
            UUID uuid = pair.getKey();
            Object[] data = pair.getValue();
            if ((data[0].equals(key) || data[1].equals(key)) && Util.getEntityMaterial(event.getEntityType()) == ((ItemStack) data[2]).getType()) {
                Player player = Bukkit.getServer().getPlayer(uuid);
                ItemStack itemStack = (ItemStack) data[2];
                playerLaunchProjectile(location, player.getName(), itemStack, 1, 1, 0, (itemStack.getType() != Material.FIREWORK_ROCKET ? ItemLogger.ITEM_THROW : ItemLogger.ITEM_SHOOT));
                it.remove();
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    protected void onEntityShootBow(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }

        ItemStack itemStack = event.getConsumable();
        playerLaunchProjectile(event.getEntity().getLocation(), event.getEntity().getName(), itemStack, 1, 1, 0, ItemLogger.ITEM_SHOOT);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    protected void onPlayerEggThrow(PlayerEggThrowEvent event) {
        Egg egg = event.getEgg();
        ItemStack itemStack = egg.getItem();
        playerLaunchProjectile(event.getPlayer().getLocation(), event.getPlayer().getName(), itemStack, 0, 1, 0, ItemLogger.ITEM_THROW);
    }

}
