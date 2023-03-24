package net.coreprotect.listener.player;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.bukkit.Location;
import org.bukkit.inventory.BrewerInventory;
import org.bukkit.inventory.FurnaceInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import net.coreprotect.CoreProtect;
import net.coreprotect.config.Config;
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.thread.Scheduler;
import net.coreprotect.utility.Util;

public final class HopperPushListener {

    static void processHopperPush(Location location, InventoryHolder sourceHolder, InventoryHolder destinationHolder, ItemStack item) {
        String loggingChestId = "#hopper-push." + location.getBlockX() + "." + location.getBlockY() + "." + location.getBlockZ();
        Object[] lastAbort = ConfigHandler.hopperAbort.get(loggingChestId);
        if (lastAbort != null) {
            ItemStack[] destinationContents = destinationHolder.getInventory().getContents();
            if (((Set<?>) lastAbort[0]).contains(item) && Arrays.equals(destinationContents, (ItemStack[]) lastAbort[1])) {
                return;
            }
        }

        ItemStack[] containerState = null;
        if (!ConfigHandler.isPaper) {
            containerState = Util.getContainerState(destinationHolder.getInventory().getContents());
        }
        ItemStack[] destinationContainer = containerState;
        ItemStack movedItem = item.clone();

        final long taskStarted = InventoryChangeListener.tasksStarted.incrementAndGet();
        Scheduler.runTaskAsynchronously(CoreProtect.getInstance(), () -> {
            try {
                if (sourceHolder == null || destinationHolder == null) {
                    return;
                }

                int itemHash = Util.getItemStackHashCode(item);
                boolean abort = false;

                if (ConfigHandler.isPaper) {
                    for (ItemStack itemStack : sourceHolder.getInventory().getContents()) {
                        if (itemStack != null && Util.getItemStackHashCode(itemStack) == itemHash) {
                            if (itemHash != Util.getItemStackHashCode(movedItem) || destinationHolder.getInventory().firstEmpty() == -1 || destinationHolder.getInventory() instanceof BrewerInventory || destinationHolder.getInventory() instanceof FurnaceInventory) {
                                abort = true;
                            }

                            break;
                        }
                    }
                }
                else {
                    ItemStack[] destinationContents = destinationHolder.getInventory().getContents();
                    boolean addedInventory = Util.addedContainer(destinationContainer, destinationContents);
                    if (!addedInventory) {
                        abort = true;
                    }
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
                InventoryChangeListener.onInventoryInteract("#hopper", destinationInventory, originalDestination, null, destinationInventory.getLocation(), true);
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

}
