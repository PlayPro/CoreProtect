package net.coreprotect.paper.listener;

import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Dispenser;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import io.papermc.paper.event.block.BlockPreDispenseEvent;
import net.coreprotect.config.Config;
import net.coreprotect.consumer.Queue;
import net.coreprotect.listener.player.InventoryChangeListener;

public final class BlockPreDispenseListener extends Queue implements Listener {

    public static boolean useBlockPreDispenseEvent = true;

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPreDispense(BlockPreDispenseEvent event) {
        Block block = event.getBlock();
        World world = block.getWorld();
        if (!Config.getConfig(world).BLOCK_PLACE) {
            return;
        }

        BlockData blockData = block.getBlockData();
        if (blockData instanceof Dispenser) {
            String user = "#dispenser";
            ItemStack[] inventory = ((InventoryHolder) block.getState()).getInventory().getStorageContents();
            InventoryChangeListener.inventoryTransaction(user, block.getLocation(), inventory);
        }
    }

}
