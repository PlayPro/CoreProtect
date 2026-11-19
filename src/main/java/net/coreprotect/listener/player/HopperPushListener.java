package net.coreprotect.listener.player;

import java.util.Arrays;
import java.util.Set;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import net.coreprotect.config.Config;
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.utility.HopperTransactionUtils;
import net.coreprotect.utility.ItemUtils;
import net.coreprotect.utility.Validate;

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

        ConfigHandler.hopperAbort.remove(loggingChestId);

        if (Validate.isContainer(sourceHolder) && Validate.isHopper(sourceHolder)) {
            HopperTransactionUtils.recordItemRemoved(HopperTransactionUtils.getTransactionId(location), movedItem);
        }
        HopperPullListener.flushPendingPull(destinationLocation, destinationContainer);
        if (!Config.getConfig(location.getWorld()).ITEM_TRANSACTIONS) {
            return;
        }
        if (Config.getConfig(location.getWorld()).HOPPER_FILTER_META && !movedItem.hasItemMeta()) {
            HopperTransactionUtils.recordItemAdded(HopperTransactionUtils.getTransactionId(destinationLocation), movedItem);
            return;
        }

        ContainerCapture.begin(user, Material.CHEST, destinationLocation.clone(), destinationInventory, destinationContainer, null);
    }
}
