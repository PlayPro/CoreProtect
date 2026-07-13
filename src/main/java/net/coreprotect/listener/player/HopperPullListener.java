package net.coreprotect.listener.player;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.bukkit.Location;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import net.coreprotect.CoreProtect;
import net.coreprotect.config.Config;
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.thread.Scheduler;
import net.coreprotect.utility.HopperTransactionUtils;
import net.coreprotect.utility.ItemUtils;
import net.coreprotect.utility.ErrorReporter;

public final class HopperPullListener {

    private static final Map<String, PendingPull> pendingPulls = new ConcurrentHashMap<>();

    private HopperPullListener() {
        throw new IllegalStateException("Listener class");
    }

    static void processHopperPull(Location location, String user, InventoryHolder sourceHolder, InventoryHolder destinationHolder, ItemStack item) {
        if (location == null || location.getWorld() == null || sourceHolder == null || destinationHolder == null || item == null || item.getAmount() <= 0) {
            return;
        }

        Inventory sourceInventory = sourceHolder.getInventory();
        Inventory destinationInventory = destinationHolder.getInventory();
        String loggingChestId = HopperTransactionUtils.getHopperPullId(location);
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
        boolean hopperTransactions = Config.getConfig(location.getWorld()).HOPPER_TRANSACTIONS;
        if (!hopperTransactions) {
            HopperTransactionUtils.recordItemRemoved(HopperTransactionUtils.getTransactionId(location), movedItem);
            return;
        }

        Location destinationLocation = destinationInventory.getLocation();
        if (destinationLocation == null) {
            return;
        }

        HopperTransactionUtils.recordItemAdded(HopperTransactionUtils.getTransactionId(destinationLocation), movedItem);
        if (!Config.getConfig(location.getWorld()).ITEM_TRANSACTIONS) {
            return;
        }
        if (Config.getConfig(location.getWorld()).HOPPER_FILTER_META && !movedItem.hasItemMeta()) {
            HopperTransactionUtils.recordItemRemoved(HopperTransactionUtils.getTransactionId(location), movedItem);
            return;
        }

        String transactionId = HopperTransactionUtils.getTransactionId(location);
        PendingPull pending = pendingPulls.compute(transactionId, (key, current) -> {
            PendingPull value = current;
            if (value == null) {
                value = new PendingPull(sourceInventory, location.clone(), sourceInventory.getMaxStackSize());
            }
            value.accumulator.add(user, movedItem);
            return value;
        });

        if (pending.scheduled.compareAndSet(false, true)) {
            Scheduler.runTask(CoreProtect.getInstance(), () -> capturePendingPull(transactionId, pending), pending.location);
        }
    }

    static void flushPendingPull(Location location, Inventory inventory, ItemStack[] checkpoint) {
        if (location == null || location.getWorld() == null || checkpoint == null) {
            return;
        }

        String transactionId = HopperTransactionUtils.getTransactionId(location);
        PendingPull pending = pendingPulls.remove(transactionId);
        if (pending == null) {
            return;
        }

        Inventory sourceInventory = inventory == null ? pending.inventory : inventory;
        submitPendingPull(pending, sourceInventory, checkpoint);
    }

    static void flushPendingPull(Location location, Inventory inventory) {
        if (location == null || location.getWorld() == null || inventory == null) {
            return;
        }

        String transactionId = HopperTransactionUtils.getTransactionId(location);
        PendingPull pending = pendingPulls.get(transactionId);
        if (pending == null) {
            return;
        }

        ItemStack[] checkpoint = ItemUtils.getContainerState(inventory.getContents());
        if (pendingPulls.remove(transactionId, pending)) {
            submitPendingPull(pending, inventory, checkpoint);
        }
    }

    static void flushPendingPullsForShutdown() {
        for (Map.Entry<String, PendingPull> entry : pendingPulls.entrySet()) {
            PendingPull pending = entry.getValue();
            try {
                ItemStack[] checkpoint = ItemUtils.getContainerState(pending.inventory.getContents());
                if (pendingPulls.remove(entry.getKey(), pending)) {
                    submitPendingPull(pending, pending.inventory, checkpoint);
                }
            }
            catch (Exception e) {
                ErrorReporter.report(e);
            }
        }
    }

    static int pendingLocations() {
        return pendingPulls.size();
    }

    static long pendingMoves() {
        long count = 0;
        for (PendingPull pending : pendingPulls.values()) {
            count += pending.accumulator.moveCount();
        }
        return count;
    }

    private static void capturePendingPull(String transactionId, PendingPull pending) {
        if (!pendingPulls.remove(transactionId, pending)) {
            return;
        }

        ItemStack[] checkpoint = ItemUtils.getContainerState(pending.inventory.getContents());
        submitPendingPull(pending, pending.inventory, checkpoint);
    }

    private static void submitPendingPull(PendingPull pending, Inventory sourceInventory, ItemStack[] checkpoint) {
        if (pending == null || sourceInventory == null || checkpoint == null || pending.accumulator.isEmpty()) {
            return;
        }

        ContainerTransactionDispatcher.submit(pending.location, () -> pending.accumulator.replay(checkpoint, pending.maxStackSize,
                (user, sourceBefore, movedItem) -> InventoryChangeListener.onHopperInventoryInteract(user, sourceInventory, sourceBefore, pending.location, movedItem)));
    }

    private static final class PendingPull {
        private final Inventory inventory;
        private final Location location;
        private final int maxStackSize;
        private final HopperPullAccumulator accumulator = new HopperPullAccumulator();
        private final AtomicBoolean scheduled = new AtomicBoolean(false);

        private PendingPull(Inventory inventory, Location location, int maxStackSize) {
            this.inventory = inventory;
            this.location = location;
            this.maxStackSize = maxStackSize;
        }
    }
}
