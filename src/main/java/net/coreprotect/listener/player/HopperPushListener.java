package net.coreprotect.listener.player;

import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import net.coreprotect.CoreProtect;
import net.coreprotect.config.Config;
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.utility.Util;

public final class HopperPushListener {

    static void processHopperPush(Location location, InventoryHolder sourceHolder, InventoryHolder destinationHolder, ItemStack item, ItemStack movedItem) {
        ItemStack[] containerState = null;
        if (!ConfigHandler.isPaper) {
            containerState = Util.getContainerState(destinationHolder.getInventory().getContents());
        }
        ItemStack[] destinationContainer = containerState;

        final long taskStarted = InventoryChangeListener.tasksStarted.incrementAndGet();
        Bukkit.getServer().getScheduler().runTaskAsynchronously(CoreProtect.getInstance(), () -> {
            try {
                InventoryChangeListener.checkTasks(taskStarted);
                if (sourceHolder == null || destinationHolder == null) {
                    return;
                }

                int itemHash = Util.getItemStackHashCode(item);

                if (ConfigHandler.isPaper) {
                    for (ItemStack itemStack : sourceHolder.getInventory().getContents()) {
                        if (itemStack != null && Util.getItemStackHashCode(itemStack) == itemHash) {
                            if (itemHash != Util.getItemStackHashCode(movedItem) || destinationHolder.getInventory().firstEmpty() == -1) {
                                return;
                            }

                            break;
                        }
                    }
                }
                else {
                    ItemStack[] destinationContents = destinationHolder.getInventory().getContents();
                    boolean addedInventory = Util.addedContainer(destinationContainer, destinationContents);
                    if (!addedInventory) {
                        return;
                    }
                }

                List<Object> list = ConfigHandler.transactingChest.get(location.getWorld().getUID() + "." + location.getBlockX() + "." + location.getBlockY() + "." + location.getBlockZ());
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

                    ItemStack itemStack = originalDestination[i];
                    if (itemStack != null && itemStack.isSimilar(movedItem)) {
                        itemStack = itemStack.clone();
                        if (itemStack.getAmount() >= removeAmount) {
                            itemStack.setAmount(itemStack.getAmount() - removeAmount);
                            removeAmount = 0;
                        }
                        else {
                            removeAmount = removeAmount - itemStack.getAmount();
                            itemStack.setAmount(0);
                        }

                        originalDestination[i] = itemStack;
                    }
                }

                InventoryChangeListener.onInventoryInteract("#hopper", destinationInventory, originalDestination, null, destinationInventory.getLocation(), true);
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

}
