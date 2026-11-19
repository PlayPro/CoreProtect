package net.coreprotect.listener.player;

import org.bukkit.Material;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Cake;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.FoodLevelChangeEvent;

import net.coreprotect.CoreProtect;
import net.coreprotect.config.Config;
import net.coreprotect.consumer.Queue;
import net.coreprotect.thread.CacheHandler;
import net.coreprotect.thread.Scheduler;
import net.coreprotect.utility.BlockUtils;

public final class FoodLevelChangeListener implements Listener {

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
        if (!before.getWorld().equals(player.getWorld())) {
            return;
        }
        Config config = Config.getConfig(before.getWorld());
        String user = player.getName();
        Scheduler.runTask(CoreProtect.getInstance(), () -> logCakeBite(before, user, config), before.getLocation());
    }

    private static void logCakeBite(BlockState before, String user, Config config) {
        BlockState after = before.getBlock().getState();
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
