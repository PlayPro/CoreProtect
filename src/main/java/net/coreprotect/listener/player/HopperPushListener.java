package net.coreprotect.listener.player;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.bukkit.Location;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import net.coreprotect.CoreProtect;
import net.coreprotect.config.Config;
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.thread.Scheduler;
import net.coreprotect.utility.ItemUtils;

public final class HopperPushListener {

    private static final ConcurrentLinkedQueue<Object[]> hopperQueue = new ConcurrentLinkedQueue<>();
    private static final AtomicBoolean processorRunning = new AtomicBoolean(false);
    private static final int BATCH_SIZE = 100;
    private static final int PROCESS_DELAY_TICKS = 1;
    private static final int MAX_CONCURRENT_PROCESSORS = 4;
    private static final AtomicInteger activeProcessors = new AtomicInteger(0);

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

        ItemStack[] destinationContainer = ItemUtils.getContainerState(destinationHolder.getInventory().getContents());
        ItemStack movedItem = item.clone();

        hopperQueue.add(new Object[] { location, user, sourceHolder, destinationHolder, movedItem, destinationContainer, loggingChestId, lastAbort });

        // Start the processor if it's not already running
        if (processorRunning.compareAndSet(false, true)) {
            startHopperProcessor();
        }
    }

    private static void startHopperProcessor() {
        if (activeProcessors.incrementAndGet() <= MAX_CONCURRENT_PROCESSORS) {
            Scheduler.runTaskAsynchronously(CoreProtect.getInstance(), () -> {
                try {
                    // Use the same server running check as Consumer class
                    while (!hopperQueue.isEmpty() && (ConfigHandler.serverRunning || ConfigHandler.converterRunning)) {
                        processHopperBatch();
                    }
                }
                catch (Exception e) {
                    e.printStackTrace();
                }
                finally {
                    activeProcessors.decrementAndGet();
                    processorRunning.set(false);

                    // If more items were added and we're still running, restart the processor
                    if (!hopperQueue.isEmpty() && (ConfigHandler.serverRunning || ConfigHandler.converterRunning) && activeProcessors.get() == 0) {
                        startHopperProcessor();
                    }
                }
            });
        }
        else {
            activeProcessors.decrementAndGet();
        }
    }

    private static void processHopperBatch() {
        int processed = 0;
        final long taskStarted = InventoryChangeListener.tasksStarted.incrementAndGet();

        while (!hopperQueue.isEmpty() && processed < BATCH_SIZE && (ConfigHandler.serverRunning || ConfigHandler.converterRunning)) {
            Object[] data = hopperQueue.poll();
            if (data == null)
                continue;

            Location location = (Location) data[0];
            String user = (String) data[1];
            InventoryHolder sourceHolder = (InventoryHolder) data[2];
            InventoryHolder destinationHolder = (InventoryHolder) data[3];
            ItemStack movedItem = (ItemStack) data[4];
            ItemStack[] destinationContainer = (ItemStack[]) data[5];
            String loggingChestId = (String) data[6];
            Object[] lastAbort = (Object[]) data[7];

            processSingleHopperPush(location, user, sourceHolder, destinationHolder, movedItem, destinationContainer, loggingChestId, lastAbort);

            processed++;
        }

        InventoryChangeListener.checkTasks(taskStarted);
    }

    private static void processSingleHopperPush(Location location, String user, InventoryHolder sourceHolder, InventoryHolder destinationHolder, ItemStack movedItem, ItemStack[] destinationContainer, String loggingChestId, Object[] lastAbort) {

        if (sourceHolder == null || destinationHolder == null) {
            return;
        }

        boolean abort = false;
        boolean addedInventory = ItemUtils.canAddContainer(destinationContainer, movedItem, destinationHolder.getInventory().getMaxStackSize());
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

            ConfigHandler.hopperAbort.put(loggingChestId, new Object[] { movedItems, ItemUtils.getContainerState(destinationContents) });
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

        InventoryChangeListener.onInventoryInteract(user, destinationInventory, originalDestination, null, destinationInventory.getLocation(), true);
    }
}
