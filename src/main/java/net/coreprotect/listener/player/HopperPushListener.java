package net.coreprotect.listener.player;

import java.util.Arrays;
import java.util.Set;

import org.bukkit.Location;
import org.bukkit.Tag;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import net.coreprotect.config.Config;
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.utility.HopperTransactionUtils;
import net.coreprotect.utility.ItemUtils;

public final class HopperPushListener {

    private HopperPushListener() {
        throw new IllegalStateException("Listener class");
    }

    static void processHopperPush(Location location, String user, InventoryHolder sourceHolder, InventoryHolder destinationHolder, ItemStack item) {
        if (location == null || location.getWorld() == null || sourceHolder == null || destinationHolder == null || item == null || item.getAmount() <= 0) {
            return;
        }

        Inventory destinationInventory = destinationHolder.getInventory();
        Location destinationLocation = destinationInventory.getLocation();
        if (destinationLocation == null || destinationLocation.getWorld() == null) {
            return;
        }

        String loggingChestId = HopperTransactionUtils.getHopperPushId(destinationLocation);
        Object[] lastAbort = ConfigHandler.hopperAbort.get(loggingChestId);
        ItemStack[] destinationContents = destinationInventory.getContents();
        if (lastAbort != null && ((Set<?>) lastAbort[0]).contains(item) && Arrays.equals(destinationContents, (ItemStack[]) lastAbort[1])) {
            return;
        }

        ItemStack[] destinationContainer = ItemUtils.getContainerState(destinationContents);
        ItemStack movedItem = item.clone();
        if (!ItemUtils.canAddContainer(destinationContainer, movedItem, destinationInventory.getMaxStackSize())) {
            ConfigHandler.hopperAbort.put(loggingChestId, HopperTransactionUtils.createAbortState(lastAbort, destinationContainer, movedItem));
            return;
        }

        final Config config = Config.getConfig(location.getWorld());
        HopperTransactionUtils.recordItemRemoved(HopperTransactionUtils.getTransactionId(location), movedItem);
        HopperPullListener.flushPendingPull(destinationLocation, destinationInventory, destinationContainer);
        if (!config.ITEM_TRANSACTIONS) {
            return;
        }
        if ((config.HOPPER_FILTER_META && !movedItem.hasItemMeta()) || (config.DISABLE_HOPPER_CARPET_LOGGING && Tag.WOOL_CARPETS.isTagged(movedItem.getType()))) {
            HopperTransactionUtils.recordItemAdded(HopperTransactionUtils.getTransactionId(destinationLocation), movedItem);
            return;
        }

        ContainerTransactionDispatcher.submit(destinationLocation, () -> {
            ConfigHandler.hopperSuccess.put(loggingChestId, new Object[] { destinationContainer, movedItem });
            InventoryChangeListener.onHopperInventoryInteract(user, destinationInventory, destinationContainer, destinationLocation, movedItem);
        });
    }
}
