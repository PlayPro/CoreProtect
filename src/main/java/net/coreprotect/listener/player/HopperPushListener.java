package net.coreprotect.listener.player;

import net.coreprotect.config.Config;
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.utility.HopperTransactionUtils;
import net.coreprotect.utility.ItemUtils;
import org.bukkit.Location;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.Set;

public final class HopperPushListener {

    private HopperPushListener() {
        throw new IllegalStateException("Listener class");
    }

    static void processHopperPush(Location location, String user, boolean sourceContainer, Inventory destinationInventory, ItemStack[] destinationContents, Boolean fits, ItemStack item) {
        if (location == null || location.getWorld() == null || destinationInventory == null || destinationContents == null || item == null || item.getAmount() <= 0) {
            return;
        }

        Location destinationLocation = destinationInventory.getLocation();
        if (destinationLocation == null || destinationLocation.getWorld() == null) {
            return;
        }

        String destinationSuffix = HopperTransactionUtils.getLoggingIdSuffix(destinationLocation);
        String loggingChestId = HopperTransactionUtils.getHopperPushId(destinationSuffix);
        Object[] lastAbort = ConfigHandler.hopperAbort.get(loggingChestId);
        if (lastAbort != null && ((Set<?>) lastAbort[0]).contains(item) && Arrays.equals(destinationContents, (ItemStack[]) lastAbort[1])) {
            return;
        }

        ItemStack movedItem = item.clone();
        boolean canAdd = fits != null ? fits : ItemUtils.canAddContainer(destinationContents, movedItem, destinationInventory.getMaxStackSize());
        if (!canAdd) {
            ConfigHandler.hopperAbort.put(loggingChestId, HopperTransactionUtils.createAbortState(lastAbort, destinationContents, movedItem));
            return;
        }

        ItemStack[] destinationContainer = ItemUtils.getContainerState(destinationContents);
        Config worldConfig = Config.getConfig(location.getWorld());
        if (sourceContainer) {
            HopperTransactionUtils.recordItemRemoved(HopperTransactionUtils.getTransactionId(location), movedItem);
        }
        HopperPullListener.flushPendingPull(destinationLocation, destinationInventory, destinationContainer);
        if (!worldConfig.ITEM_TRANSACTIONS) {
            return;
        }
        if (worldConfig.HOPPER_FILTER_META && !movedItem.hasItemMeta()) {
            HopperTransactionUtils.recordItemAdded(HopperTransactionUtils.getTransactionId(destinationLocation), movedItem);
            return;
        }

        ContainerTransactionDispatcher.submit(destinationLocation, () -> {
            ConfigHandler.hopperSuccess.put(loggingChestId, new Object[]{destinationContainer, movedItem, (int) (System.currentTimeMillis() / 1000L)});
            InventoryChangeListener.onHopperInventoryInteract(user, destinationInventory, destinationContainer, destinationLocation, HopperTransactionUtils.getTransactionId(destinationLocation), destinationSuffix, movedItem);
        });
    }
}
