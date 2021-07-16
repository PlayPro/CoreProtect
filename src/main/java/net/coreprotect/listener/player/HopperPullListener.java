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

public final class HopperPullListener {

    static void processHopperPull(Location location, InventoryHolder sourceHolder, InventoryHolder destinationHolder, ItemStack item, ItemStack movedItem) {
        ItemStack[] containerState = null;
        if (!ConfigHandler.isPaper) {
            containerState = Util.getContainerState(sourceHolder.getInventory().getContents());
        }
        ItemStack[] sourceContainer = containerState;

        final long taskStarted = InventoryChangeListener.tasksStarted.incrementAndGet();
        Bukkit.getServer().getScheduler().runTaskAsynchronously(CoreProtect.getInstance(), () -> {
            try {
                InventoryChangeListener.checkTasks(taskStarted);
                if (sourceHolder == null || destinationHolder == null) {
                    return;
                }

                boolean hopperTransactions = Config.getConfig(location.getWorld()).HOPPER_TRANSACTIONS;
                int itemHash = Util.getItemStackHashCode(item);
                int x = location.getBlockX();
                int y = location.getBlockY();
                int z = location.getBlockZ();
                String loggingChestId = "#hopper." + x + "." + y + "." + z;

                if (ConfigHandler.isPaper) {
                    boolean abort = false;
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

                        if (abort) {
                            ConfigHandler.hopperAbort.put(loggingChestId, true);
                            return;
                        }
                    }
                }
                else {
                    ItemStack[] sourceContents = sourceHolder.getInventory().getContents();
                    boolean addedInventory = Util.addedContainer(sourceContainer, sourceContents);
                    if (addedInventory) {
                        ConfigHandler.hopperAbort.put(loggingChestId, true);
                        return;
                    }
                }

                boolean lastAborted = false;
                if (ConfigHandler.hopperAbort.get(loggingChestId) != null) {
                    ConfigHandler.hopperAbort.remove(loggingChestId);
                    lastAborted = true;
                }

                boolean mergeMoved = true;
                if (lastAborted) {
                    for (String loggingChestIdViewer : ConfigHandler.oldContainer.keySet()) {
                        if (loggingChestIdViewer.equals(loggingChestId) || !loggingChestIdViewer.endsWith("." + x + "." + y + "." + z)) {
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
                        List<Object> list = ConfigHandler.transactingChest.get(location.getWorld().getUID() + "." + location.getBlockX() + "." + location.getBlockY() + "." + location.getBlockZ());
                        if (list != null) {
                            list.add(movedItem);
                        }
                    }
                    return;
                }

                if (mergeMoved) {
                    Location destinationLocation = destinationHolder.getInventory().getLocation();
                    List<Object> list = ConfigHandler.transactingChest.get(destinationLocation.getWorld().getUID() + "." + destinationLocation.getBlockX() + "." + destinationLocation.getBlockY() + "." + destinationLocation.getBlockZ());
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
