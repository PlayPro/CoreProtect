package net.coreprotect.listener.player;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import net.coreprotect.CoreProtect;
import net.coreprotect.config.Config;
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.utility.Util;

public final class HopperPullListener {

    static void processHopperPull(Location location, InventoryHolder sourceHolder, InventoryHolder destinationHolder, ItemStack item) {
        String loggingChestId = "#hopper-pull." + location.getBlockX() + "." + location.getBlockY() + "." + location.getBlockZ();
        Object[] lastAbort = ConfigHandler.hopperAbort.get(loggingChestId);
        if (lastAbort != null) {
            ItemStack[] destinationContents = destinationHolder.getInventory().getContents();
            if (((Set<?>) lastAbort[0]).contains(item) && Arrays.equals(destinationContents, (ItemStack[]) lastAbort[1])) {
                return;
            }
        }

        ItemStack[] containerState = null;
        if (!ConfigHandler.isPaper) {
            containerState = Util.getContainerState(sourceHolder.getInventory().getContents());
        }
        ItemStack[] sourceContainer = containerState;
        ItemStack movedItem = item.clone();

        final long taskStarted = InventoryChangeListener.tasksStarted.incrementAndGet();
        Bukkit.getServer().getScheduler().runTaskAsynchronously(CoreProtect.getInstance(), () -> {
            try {
                InventoryChangeListener.checkTasks(taskStarted);
                if (sourceHolder == null || destinationHolder == null) {
                    return;
                }

                boolean hopperTransactions = Config.getConfig(location.getWorld()).HOPPER_TRANSACTIONS;
                int itemHash = Util.getItemStackHashCode(item);
                boolean abort = false;

                if (ConfigHandler.isPaper) {
                    for (ItemStack itemStack : sourceHolder.getInventory().getContents()) {
                        if (itemStack != null && Util.getItemStackHashCode(itemStack) == itemHash) {
                            abort = true;
                            break;
                        }
                    }

                    if (abort) {
                        for (ItemStack itemStack : destinationHolder.getInventory().getContents()) {
                            if (itemStack != null && Util.getItemStackHashCode(itemStack) == Util.getItemStackHashCode(movedItem)) {
                                abort = false;
                                break;
                            }
                        }
                    }
                }
                else {
                    ItemStack[] sourceContents = sourceHolder.getInventory().getContents();
                    boolean addedInventory = Util.addedContainer(sourceContainer, sourceContents);
                    if (addedInventory) {
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

                boolean lastAborted = false;
                if (ConfigHandler.hopperAbort.get(loggingChestId) != null) {
                    ConfigHandler.hopperAbort.remove(loggingChestId);
                    lastAborted = true;
                }

                boolean mergeMoved = true;
                if (lastAborted) {
                    for (String loggingChestIdViewer : ConfigHandler.oldContainer.keySet()) {
                        if (loggingChestIdViewer.equals(loggingChestId) || !loggingChestIdViewer.endsWith("." + location.getBlockX() + "." + location.getBlockY() + "." + location.getBlockZ())) {
                            continue;
                        }

                        if (ConfigHandler.oldContainer.get(loggingChestIdViewer) != null) { // pending consumer item by another user
                            mergeMoved = false;
                            break;
                        }
                    }
                }

                if (!hopperTransactions) {
                    if (mergeMoved) {
                        List<Object> list = ConfigHandler.transactingChest.get(location.getWorld().getUID().toString() + "." + location.getBlockX() + "." + location.getBlockY() + "." + location.getBlockZ());
                        if (list != null) {
                            list.add(movedItem);
                        }
                    }
                    return;
                }

                if (mergeMoved) {
                    Location destinationLocation = destinationHolder.getInventory().getLocation();
                    List<Object> list = ConfigHandler.transactingChest.get(destinationLocation.getWorld().getUID().toString() + "." + destinationLocation.getBlockX() + "." + destinationLocation.getBlockY() + "." + destinationLocation.getBlockZ());
                    if (list != null) {
                        list.add(new ItemStack[] { null, movedItem });
                    }
                }

                if (Config.getConfig(location.getWorld()).HOPPER_FILTER_META && !movedItem.hasItemMeta()) {
                    return;
                }

                Inventory sourceInventory = sourceHolder.getInventory();
                ItemStack[] inventoryContents = sourceInventory.getContents();
                ItemStack[] originalSource = new ItemStack[inventoryContents.length + 1];
                for (int i = 0; i < inventoryContents.length; i++) {
                    ItemStack itemStack = inventoryContents[i];
                    if (itemStack != null) {
                        originalSource[i] = itemStack.clone();
                    }
                }

                if (mergeMoved) {
                    originalSource[inventoryContents.length] = movedItem;
                }

                InventoryChangeListener.onInventoryInteract("#hopper", sourceInventory, originalSource, null, sourceInventory.getLocation(), true);
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

}
