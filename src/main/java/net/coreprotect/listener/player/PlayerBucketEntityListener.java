package net.coreprotect.listener.player;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerBucketEntityEvent;
import org.bukkit.inventory.ItemStack;

import net.coreprotect.config.Config;
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.database.logger.ItemLogger;
import net.coreprotect.utility.EntityUtils;

public final class PlayerBucketEntityListener implements Listener {

    @EventHandler(priority = EventPriority.HIGHEST)
    protected void onPlayerBucketEntity(PlayerBucketEntityEvent event) {
        if (!EntityUtils.isSulfurCube(event.getEntity().getType())) {
            return;
        }

        Player player = event.getPlayer();
        String playerName = player.getName();
        if (ConfigHandler.inspecting.get(playerName) != null && ConfigHandler.inspecting.get(playerName)) {
            event.setCancelled(true);
            return;
        }

        if (event.isCancelled()) {
            return;
        }

        Location location = event.getEntity().getLocation();
        World world = location.getWorld();
        if (world == null || !Config.getConfig(world).BUCKETS) {
            return;
        }

        ItemStack entityBucket = event.getEntityBucket();
        if (entityBucket == null || !EntityUtils.isSulfurCubeBucket(entityBucket.getType())) {
            return;
        }

        logItemConversion(location, playerName, event.getOriginalBucket(), entityBucket);
    }

    private static void logItemConversion(Location location, String player, ItemStack originalBucket, ItemStack entityBucket) {
        if (originalBucket != null && originalBucket.getType() != Material.AIR) {
            ItemStack removedItem = originalBucket.clone();
            removedItem.setAmount(1);
            CraftItemListener.logCraftedItem(location, player, removedItem, ItemLogger.ITEM_DESTROY);
        }

        ItemStack addedItem = entityBucket.clone();
        addedItem.setAmount(1);
        CraftItemListener.logCraftedItem(location, player, addedItem, ItemLogger.ITEM_CREATE);
    }
}
