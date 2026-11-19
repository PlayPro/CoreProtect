package net.coreprotect.listener.player;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerBucketFillEvent;

import net.coreprotect.CoreProtect;
import net.coreprotect.config.Config;
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.consumer.Queue;
import net.coreprotect.thread.Scheduler;

public final class PlayerBucketFillListener extends Queue implements Listener {

    @EventHandler(priority = EventPriority.LOWEST)
    protected void inspectBucketFill(PlayerBucketFillEvent event) {
        if (Boolean.TRUE.equals(ConfigHandler.inspecting.get(event.getPlayer().getName()))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    protected void onPlayerBucketFill(PlayerBucketFillEvent event) {
        Block block = event.getBlock();
        if (event.isCancelled() || !Config.getConfig(block.getWorld()).BUCKETS) {
            return;
        }
        BlockState before = block.getState();
        BlockData beforeData = before.getBlockData();
        Material liquid = isWaterlogged(beforeData) ? Material.WATER : before.getType();
        if (liquid != Material.WATER && liquid != Material.LAVA && !liquid.name().equals("POWDER_SNOW")) {
            return;
        }
        String user = event.getPlayer().getName();
        String data = isWaterlogged(beforeData) ? Material.WATER.createBlockData().getAsString() : beforeData.getAsString();
        Scheduler.runTask(CoreProtect.getInstance(), () -> {
            if (block.getType() != liquid && (liquid != Material.WATER || !isWaterlogged(block.getBlockData()))) {
                Queue.queueBlockBreak(user, before, liquid, data, 0);
            }
        }, before.getLocation());
    }

    private static boolean isWaterlogged(BlockData data) {
        return data instanceof Waterlogged && ((Waterlogged) data).isWaterlogged();
    }
}
