package net.coreprotect.paper.listener;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;

import io.papermc.paper.event.player.PlayerFlowerPotManipulateEvent;
import net.coreprotect.config.Config;
import net.coreprotect.consumer.Queue;

public final class FlowerPotManipulateListener extends Queue implements Listener {

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    protected void onPlayerFlowerPotManipulate(PlayerFlowerPotManipulateEvent event) {
        Player player = event.getPlayer();
        Block flowerPot = event.getFlowerpot();
        ItemStack eventItem = event.getItem();
        if (player == null || flowerPot == null || eventItem == null || eventItem.getType() == Material.AIR) {
            return;
        }

        World world = flowerPot.getWorld();
        if (!Config.getConfig(world).ITEM_TRANSACTIONS) {
            return;
        }

        BlockState oldState = flowerPot.getState();
        Material oldType = oldState.getType();
        Material newType = Material.FLOWER_POT;
        if (event.isPlacing()) {
            newType = Material.getMaterial("POTTED_" + eventItem.getType().name());
            if (newType == null) {
                return;
            }
        }

        if (oldType == newType) {
            return;
        }

        BlockData oldBlockData = oldState.getBlockData();
        String oldBlockDataString = oldBlockData != null ? oldBlockData.getAsString() : null;
        queueBlockPlace(player.getName(), oldState, oldType, oldState, newType, -1, 0, oldBlockDataString);
    }
}
