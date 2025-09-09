package net.coreprotect.listener.player;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.FoodLevelChangeEvent;

import net.coreprotect.CoreProtect;
import net.coreprotect.consumer.Queue;
import net.coreprotect.thread.CacheHandler;
import net.coreprotect.thread.Scheduler;
import net.coreprotect.utility.WorldUtils;

public final class FoodLevelChangeListener extends Queue implements Listener {

    @EventHandler(priority = EventPriority.MONITOR)
    protected void onFoodLevelChangeEvent(FoodLevelChangeEvent event) {
        if (event.isCancelled() || event.getEntityType() != EntityType.PLAYER) {
            return;
        }

        Player player = (Player) event.getEntity();
        int changeLevel = event.getFoodLevel() - player.getFoodLevel();
        if (changeLevel == 2) { // cake...
            Location location = player.getLocation();
            int worldId = WorldUtils.getWorldId(location.getWorld().getName());
            int x = location.getBlockX();
            int y = location.getBlockY();
            int z = location.getBlockZ();
            String userUUID = player.getUniqueId().toString();
            String coordinates = x + "." + y + "." + z + "." + worldId + "." + userUUID;

            Object[] data = CacheHandler.interactCache.get(coordinates);
            if (data != null && data[1] == Material.CAKE) {
                long newTime = System.currentTimeMillis();
                long oldTime = (long) data[0];

                if ((newTime - oldTime) < 20) { // 50ms = 1 tick
                    final BlockState oldBlockState = (BlockState) data[2];

                    Material oldType = oldBlockState.getType();
                    if (oldType.name().endsWith(Material.CAKE.name())) {
                        oldType = Material.CAKE;
                    }
                    final Material oldBlockType = oldType;

                    Scheduler.runTask(CoreProtect.getInstance(), () -> {
                        try {
                            Block newBlock = oldBlockState.getBlock();
                            BlockState newBlockState = newBlock.getState();

                            if (!oldBlockState.getBlockData().matches(newBlockState.getBlockData())) {
                                Queue.queueBlockBreak(player.getName(), oldBlockState, oldBlockState.getType(), oldBlockState.getBlockData().getAsString(), 0);

                                if (oldBlockType == newBlockState.getType()) {
                                    Queue.queueBlockPlace(player.getName(), newBlockState, newBlock.getType(), null, newBlockState.getType(), -1, 0, newBlockState.getBlockData().getAsString());
                                }
                            }
                        }
                        catch (Exception e) {
                            e.printStackTrace();
                        }
                    }, oldBlockState.getLocation());
                }

                CacheHandler.interactCache.remove(coordinates);
            }
        }
    }
}
