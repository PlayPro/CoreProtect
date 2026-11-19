package net.coreprotect.listener.player;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import net.coreprotect.CoreProtect;
import net.coreprotect.config.Config;
import net.coreprotect.consumer.Queue;
import net.coreprotect.thread.Scheduler;

public final class FlowerPotListener implements Listener {

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.useInteractedBlock() == Event.Result.DENY) {
            return;
        }
        Block flowerPot = event.getClickedBlock();
        if (flowerPot == null) {
            return;
        }
        Material oldType = flowerPot.getType();
        boolean placing = oldType == Material.FLOWER_POT;
        if (!placing && !oldType.name().startsWith("POTTED_")) {
            return;
        }
        if (placing && event.useItemInHand() == Event.Result.DENY) {
            return;
        }
        Config config = Config.getConfig(flowerPot.getWorld());
        if (!(placing ? config.BLOCK_PLACE : config.BLOCK_BREAK)) {
            return;
        }
        Material newType = placing ? pottedMaterial(event.getItem()) : Material.FLOWER_POT;
        if (newType == null) {
            return;
        }
        BlockState before = flowerPot.getState();
        String player = event.getPlayer().getName();
        Scheduler.runTask(CoreProtect.getInstance(), () -> {
            if (flowerPot.getType() == newType) {
                logTransition(player, before, newType);
            }
        }, flowerPot.getLocation());
    }

    public static Material pottedMaterial(ItemStack item) {
        if (item == null) {
            return null;
        }
        switch (item.getType().name()) {
            case "AZALEA":
                return Material.getMaterial("POTTED_AZALEA_BUSH");
            case "FLOWERING_AZALEA":
                return Material.getMaterial("POTTED_FLOWERING_AZALEA_BUSH");
            default:
                return Material.getMaterial("POTTED_" + item.getType().name());
        }
    }

    public static void logTransition(String player, BlockState before, Material newType) {
        Material oldType = before.getType();
        if (newType != null && oldType != newType) {
            Queue.queueBlockPlace(player, before, oldType, before, newType, -1, 0, null);
        }
    }
}
