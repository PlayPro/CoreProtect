package net.coreprotect.listener.player;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.bukkit.Location;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import net.coreprotect.CoreProtect;
import net.coreprotect.config.Config;
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.thread.Scheduler;
import net.coreprotect.utility.Util;

public final class HopperPushListener {

    static void processHopperPush(Location location, String user, InventoryHolder sourceHolder, InventoryHolder destinationHolder, ItemStack item) {
        Location destinationLocation = destinationHolder.getInventory().getLocation();
        if (destinationLocation == null) {
            return;
        }

        String loggingChestId = "#hopper-push." + destinationLocation.getBlockX() + "." + destinationLocation.getBlockY() + "." + destinationLocation.getBlockZ();
        Object[] lastAbort = ConfigHandler.hopperAbort.get(loggingChestId);
        if (lastAbort != null) {
            ItemStack[] destinationContents = destinationHolder.getInventory().getContents();
            if (((Set<?>) lastAbort[0]).contains(item) && Arrays.equals(destinationContents, (ItemStack[]) lastAbort[1])) {
                return;
            }
        }

        ItemStack[] destinationContainer = Util.getContainerState(destinationHolder.getInventory().getContents());
        ItemStack movedItem = item.clone();

        final long taskStarted = InventoryChangeListener.tasksStarted.incrementAndGet();
        Scheduler.runTaskAsynchronously(CoreProtect.getInstance(), () -> {
            try {
                if (sourceHolder == null || destinationHolder == null) {
                    return;
                }

                boolean abort = false;
                boolean addedInventory = Util.canAddContainer(destinationContainer, movedItem, destinationHolder.getInventory().getMaxStackSize());
                if (!addedInventory) {
                    abort = true;
                }

                if (abort) {
                    Set<ItemStack> movedItems = new HashSet<>();
                    ItemStack[] destinationContents = destinationHolder.getInventory().getContents();
                    if (lastAbort != null && Arrays.equals(destinationContents, (ItemStack[]) lastAbort[1])) {
                        ((Set<?>) lastAbort[0]).forEach(itemStack -> movedItems.add((ItemStack) itemStack));
                    }
                    movedItems.add(movedItem);

                    ConfigHandler.hopperAbort.put(loggingChestId, new Object[] { movedItems, Util.getContainerState(destinationContents) });
                    return;
                }
                else {
                    ConfigHandler.hopperSuccess.put(loggingChestId, new Object[] { destinationContainer, movedItem });
                }

                List<Object> list = ConfigHandler.transactingChest.get(location.getWorld().getUID().toString() + "." + location.getBlockX() + "." + location.getBlockY() + "." + location.getBlockZ());
                if (list != null) {
                    list.add(movedItem);
                }

                if (Config.getConfig(location.getWorld()).HOPPER_FILTER_META && !movedItem.hasItemMeta()) {
                    return;
                }

                Inventory destinationInventory = destinationHolder.getInventory();
                ItemStack[] originalDestination = destinationInventory.getContents().clone();
                int removeAmount = movedItem.getAmount();
                for (int i = 0; i < originalDestination.length; i++) {
                    if (removeAmount == 0) {
                        break;
                    }

                    ItemStack itemStack = (originalDestination[i] != null ? originalDestination[i].clone() : null);
                    if (itemStack != null && itemStack.isSimilar(movedItem)) {
                        if (itemStack.getAmount() >= removeAmount) {
                            itemStack.setAmount(itemStack.getAmount() - removeAmount);
                            removeAmount = 0;
                        }
                        else {
                            removeAmount = removeAmount - itemStack.getAmount();
                            itemStack = null;
                        }

                        originalDestination[i] = itemStack;
                    }
                }

                InventoryChangeListener.checkTasks(taskStarted);
                InventoryChangeListener.onInventoryInteract(user, destinationInventory, originalDestination, null, destinationInventory.getLocation(), true);
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

}
