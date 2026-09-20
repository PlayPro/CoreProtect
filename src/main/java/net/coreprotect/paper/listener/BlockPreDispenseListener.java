package net.coreprotect.paper.listener;

import io.papermc.paper.event.block.BlockPreDispenseEvent;
import net.coreprotect.config.Config;
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.consumer.Queue;
import net.coreprotect.listener.player.InventoryChangeListener;
import net.coreprotect.paper.PaperAdapter;
import net.coreprotect.utility.HopperTransactionUtils;
import net.coreprotect.utility.TransactionId;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Dispenser;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public final class BlockPreDispenseListener extends Queue implements Listener {

    public static boolean useBlockPreDispenseEvent = true;
    public static boolean useForDroppers = false;

    // Maximum time to keep entries in the cache (in milliseconds)
    private static final long CACHE_EXPIRY_TIME = 5000; // 5 seconds

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPreDispense(BlockPreDispenseEvent event) {
        Block block = event.getBlock();
        World world = block.getWorld();
        Config config = Config.getConfig(world);
        if (!config.BLOCK_PLACE) {
            return;
        }

        BlockData blockData = block.getBlockData();
        if (blockData instanceof Dispenser) {
            if (!useForDroppers && block.getType() == Material.DROPPER) {
                useForDroppers = true;
            }

            if (!config.ITEM_TRANSACTIONS || !config.DISPENSER_TRANSACTIONS) {
                return;
            }

            // Safeguard against null items
            ItemStack item = event.getItemStack();
            if (item == null) {
                return;
            }

            TransactionId locationKey = HopperTransactionUtils.getTransactionId(block.getLocation());
            if (config.DUPLICATE_SUPPRESSION) {
                Object eventKey = List.of(event.getSlot(), item.getType(), item.getAmount());

                if (item.hasItemMeta()) {
                    try {
                        eventKey = List.of(event.getSlot(), item.getType(), item.getAmount(), item.getItemMeta().hashCode());
                    }
                    catch (Exception e) {
                    }
                }

                ConcurrentHashMap<Object, Long> locationMap = ConfigHandler.dispenserNoChange.get(locationKey);
                Long lastNoChangeTime = locationMap == null ? null : locationMap.get(eventKey);

                long currentTime = System.currentTimeMillis();
                if (lastNoChangeTime != null && (currentTime - lastNoChangeTime) < CACHE_EXPIRY_TIME) {
                    locationMap.put(eventKey, currentTime);
                    return;
                }

                ConfigHandler.dispenserNoChange.remove(locationKey);
                ConfigHandler.dispenserPending.put(locationKey, new Object[]{eventKey, currentTime});
            }
            else {
                ConfigHandler.dispenserNoChange.remove(locationKey);
                ConfigHandler.dispenserPending.remove(locationKey);
            }

            // Process the inventory transaction
            String user = "#dispenser";
            BlockState blockState = PaperAdapter.ADAPTER.getBlockState(block, false);
            ItemStack[] inventory = ((InventoryHolder) blockState).getInventory().getStorageContents();
            InventoryChangeListener.inventoryTransaction(user, blockState, inventory);
        }
    }
}
