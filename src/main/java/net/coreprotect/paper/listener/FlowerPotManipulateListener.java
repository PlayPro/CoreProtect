package net.coreprotect.paper.listener;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import io.papermc.paper.event.player.PlayerFlowerPotManipulateEvent;
import net.coreprotect.config.Config;
import net.coreprotect.listener.player.FlowerPotListener;

public final class FlowerPotManipulateListener implements Listener {

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    protected void onPlayerFlowerPotManipulate(PlayerFlowerPotManipulateEvent event) {
        Player player = event.getPlayer();
        Block flowerPot = event.getFlowerpot();
        if (event.isCancelled() || player == null || flowerPot == null) {
            return;
        }

        Config config = Config.getConfig(flowerPot.getWorld());
        boolean placing = event.isPlacing();
        if (!(placing ? config.BLOCK_PLACE : config.BLOCK_BREAK)) {
            return;
        }

        Material newType = placing ? FlowerPotListener.pottedMaterial(event.getItem()) : Material.FLOWER_POT;
        FlowerPotListener.logTransition(player.getName(), flowerPot.getState(), newType);
    }
}
