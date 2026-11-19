package net.coreprotect.listener.player;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Cake;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.world.WorldUnloadEvent;

import net.coreprotect.CoreProtect;
import net.coreprotect.config.Config;
import net.coreprotect.consumer.Queue;
import net.coreprotect.thread.CacheHandler;
import net.coreprotect.thread.Scheduler;
import net.coreprotect.utility.BlockUtils;

public final class FoodLevelChangeListener implements Listener {

    private final Map<String, CakeBite> pendingCakeBites = new ConcurrentHashMap<>();

    static void recordCakeUse(Player player, BlockState before) {
        CacheHandler.interactCache.put(cakeUseKey(player), new Object[] { System.currentTimeMillis(), Material.CAKE, before });
    }

    private static String cakeUseKey(Player player) {
        return "cake." + player.getUniqueId();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    protected void onFoodLevelChangeEvent(FoodLevelChangeEvent event) {
        if (event.isCancelled() || event.getEntityType() != EntityType.PLAYER || event.getItem() != null) {
            return;
        }
        Player player = (Player) event.getEntity();
        int change = event.getFoodLevel() - player.getFoodLevel();
        if (change != 2 && (change != 1 || event.getFoodLevel() != 20)) {
            return;
        }

        Object[] use = CacheHandler.interactCache.remove(cakeUseKey(player));
        if (use == null || System.currentTimeMillis() - (long) use[0] > 50L) {
            return;
        }
        BlockState before = (BlockState) use[2];
        World world = before.getWorld();
        CoreProtect plugin = CoreProtect.getInstance();
        if (world == null || world != player.getWorld() || plugin == null || !plugin.isEnabled()) {
            return;
        }

        Location location = before.getLocation();
        String key = CacheHandler.locationKey(location);
        CakeBite bite = new CakeBite(before, player.getName(), Config.getConfig(world));
        CakeBite previous = pendingCakeBites.put(key, bite);
        try {
            if (previous != null && previous.before.getWorld() == world) {
                previous.logIfObserved(before);
            }
            Scheduler.runTask(plugin, () -> {
                if (pendingCakeBites.remove(key, bite) && plugin.isEnabled() && Bukkit.getWorld(world.getUID()) == world
                        && world.isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) {
                    bite.logIfObserved(before.getBlock().getState());
                }
            }, location);
        }
        catch (RuntimeException e) {
            pendingCakeBites.remove(key, bite);
            throw e;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    protected void onWorldUnload(WorldUnloadEvent event) {
        pendingCakeBites.values().removeIf(bite -> bite.before.getWorld() == event.getWorld());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    protected void onPluginDisable(PluginDisableEvent event) {
        if (event.getPlugin() == CoreProtect.getInstance()) {
            pendingCakeBites.clear();
        }
    }

    private static final class CakeBite {
        private final BlockState before;
        private final String user;
        private final Config config;

        private CakeBite(BlockState before, String user, Config config) {
            this.before = before;
            this.user = user;
            this.config = config;
        }

        private void logIfObserved(BlockState after) {
            BlockData previousData = before.getBlockData();
            BlockData newData = after.getBlockData();
            boolean eaten;
            if (previousData instanceof Cake) {
                Cake previousCake = (Cake) previousData;
                eaten = newData instanceof Cake && ((Cake) newData).getBites() == previousCake.getBites() + 1
                        || previousCake.getBites() == previousCake.getMaximumBites() && BlockUtils.isAir(after.getType());
            }
            else {
                eaten = newData instanceof Cake && ((Cake) newData).getBites() == 1;
            }
            if (eaten) {
                if (config.BLOCK_BREAK) {
                    Queue.queueBlockBreak(user, before, before.getType(), previousData.getAsString(), 0);
                }
                if (config.BLOCK_PLACE && after.getType() == Material.CAKE) {
                    Queue.queueBlockPlace(user, after, Material.CAKE, null, null, -1, 0, newData.getAsString());
                }
            }
        }
    }
}
