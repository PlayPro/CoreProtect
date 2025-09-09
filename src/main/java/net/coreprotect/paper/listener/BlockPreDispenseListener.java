package net.coreprotect.paper.listener;

import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Material;
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
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.consumer.Queue;
import net.coreprotect.listener.player.InventoryChangeListener;

public final class BlockPreDispenseListener extends Queue implements Listener {

    public static boolean useBlockPreDispenseEvent = true;
    public static boolean useForDroppers = false;

    // Maximum time to keep entries in the cache (in milliseconds)
    private static final long CACHE_EXPIRY_TIME = 5000; // 5 seconds

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPreDispense(BlockPreDispenseEvent event) {
        Block block = event.getBlock();
        World world = block.getWorld();
        if (!Config.getConfig(world).BLOCK_PLACE) {
            return;
        }

        BlockData blockData = block.getBlockData();
        if (blockData instanceof Dispenser) {
            if (!useForDroppers && block.getType() == Material.DROPPER) {
                useForDroppers = true;
            }

            // Safeguard against null items
            ItemStack item = event.getItemStack();
            if (item == null) {
                return;
            }

            // Create a basic location key for this dispenser
            String locationKey = block.getWorld().getUID().toString() + "." + block.getX() + "." + block.getY() + "." + block.getZ();

            // Create a detailed event key that includes item details
            String eventKey = event.getSlot() + "." + item.getType().name() + ":" + item.getAmount();

            // Add metadata hash if available
            if (item.hasItemMeta()) {
                try {
                    eventKey += ":" + item.getItemMeta().hashCode();
                }
                catch (Exception e) {
                    // If we can't get metadata hash, just use the basic key
                }
            }

            // Get or create the inner map for this location
            ConcurrentHashMap<String, Long> locationMap = ConfigHandler.dispenserNoChange.computeIfAbsent(locationKey, k -> new ConcurrentHashMap<>());

            // Check if this specific dispenser event has been marked as having no changes recently
            Long lastNoChangeTime = locationMap.get(eventKey);

            long currentTime = System.currentTimeMillis();
            if (lastNoChangeTime != null && (currentTime - lastNoChangeTime) < CACHE_EXPIRY_TIME) {
                // This specific dispenser event was recently processed and had no changes
                // Update the timestamp to extend the skip period
                locationMap.put(eventKey, currentTime);
                return;
            }

            // This is a new or changed event
            // Clear any existing dispenserNoChange entries for this location
            ConfigHandler.dispenserNoChange.remove(locationKey);

            // Store the event details for ContainerLogger to use
            ConfigHandler.dispenserPending.put(locationKey, new Object[] { eventKey, // The detailed event key
                    currentTime, // Timestamp
                    event.getSlot(), // Slot
                    item.clone() // Item (cloned to prevent modification)
            });

            // Process the inventory transaction
            String user = "#dispenser";
            ItemStack[] inventory = ((InventoryHolder) block.getState()).getInventory().getStorageContents();
            InventoryChangeListener.inventoryTransaction(user, block.getLocation(), inventory);
        }
    }
}
