package net.coreprotect.listener.player;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import net.coreprotect.CoreProtect;
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.paper.PaperAdapter;
import net.coreprotect.thread.Scheduler;
import net.coreprotect.utility.ErrorReporter;
import net.coreprotect.utility.HopperTransactionUtils;
import net.coreprotect.utility.ItemUtils;

final class ContainerCapture {

    private static final Map<String, ContainerCapture> PENDING = new ConcurrentHashMap<>();

    private final String user;
    private final Material type;
    private final Location location;
    private final Inventory inventory;
    private final ItemStack[] before;
    private final Player owner;
    private final boolean regionThreaded;
    private final String transactionId;
    private final String group;
    private final String blockId;
    private final String snapshotOwner;

    private ContainerCapture(String user, Material type, Location location, Inventory inventory, ItemStack[] before, Player owner, boolean regionThreaded) {
        this.user = user;
        this.type = type;
        this.location = location;
        this.inventory = inventory;
        this.before = before;
        this.owner = owner;
        this.regionThreaded = regionThreaded;
        this.transactionId = HopperTransactionUtils.getTransactionId(user, type, location);
        this.group = HopperTransactionUtils.getViewerGroupId(user, type, location);
        this.blockId = type == Material.ENDER_CHEST ? HopperTransactionUtils.getLoggingIdSuffix(location) : group;
        this.snapshotOwner = group + "#capture";
    }

    static boolean begin(String user, Material type, Location location, Inventory inventory, ItemStack[] before, Player owner) {
        return begin(user, type, location, inventory, before, owner, ConfigHandler.isFolia);
    }

    static boolean begin(String user, Material type, Location location, Inventory inventory, ItemStack[] before, Player owner, boolean regionThreaded) {
        CoreProtect plugin = CoreProtect.getInstance();
        if (plugin == null || before == null) {
            return false;
        }

        ContainerCapture capture = new ContainerCapture(user, type, location, inventory, before, owner, regionThreaded);
        StartResult started = HopperTransactionUtils.synchronizeTransaction(capture.transactionId, () -> {
            ContainerCapture previous = PENDING.get(capture.group);
            boolean sameOwner = previous != null && previous.owner == owner;
            if (sameOwner && previous.user.equals(user) && previous.type == type && previous.blockId.equals(capture.blockId)) {
                return new StartResult(false, null);
            }
            Runnable previousCompletion = sameOwner ? previous.finishLocked(before) : null;
            PENDING.put(capture.group, capture);
            if (type != Material.ENDER_CHEST) {
                HopperTransactionUtils.registerSnapshot(capture.transactionId, capture.snapshotOwner, true);
            }
            return new StartResult(true, previousCompletion);
        });
        if (!started.added) {
            return true;
        }
        publish(started.previousCompletion);

        try {
            if (owner != null && regionThreaded) {
                if (!PaperAdapter.ADAPTER.executeEntityTask(plugin, owner, capture::finish, capture::discard, 1L)) {
                    capture.discard();
                    return false;
                }
            }
            else {
                Scheduler.scheduleSyncDelayedTask(plugin, capture::finish, location, 1);
            }
            return true;
        }
        catch (RuntimeException e) {
            capture.discard();
            ErrorReporter.report(e);
            return false;
        }
    }

    static void finishAt(Location location) {
        ContainerCapture pending = PENDING.get(HopperTransactionUtils.getLoggingIdSuffix(location));
        if (pending != null) {
            pending.finish();
        }
    }

    static void finishEnderChest(Player owner) {
        finishEnderChest(owner, null);
    }

    static void finishEnderChest(Player owner, Inventory closingInventory) {
        ContainerCapture pending = PENDING.get(HopperTransactionUtils.getEnderChestGroupId(owner.getName()));
        if (pending != null && pending.owner == owner && (closingInventory == null || pending.inventory.equals(closingInventory))) {
            pending.finish();
        }
    }

    static void finishForShutdown() {
        for (ContainerCapture capture : PENDING.values()) {
            capture.finish();
        }
    }

    private void finish() {
        if (PENDING.get(group) != this) {
            return;
        }
        try {
            if (!ownedByCurrentRegion()) {
                discard();
                return;
            }
            ItemStack[] after = ItemUtils.getContainerState(inventory.getContents());
            publish(HopperTransactionUtils.synchronizeTransaction(transactionId, () -> finishLocked(after)));
        }
        catch (RuntimeException e) {
            discard();
            ErrorReporter.report(e);
        }
    }

    private boolean ownedByCurrentRegion() {
        if (!regionThreaded) {
            return true;
        }
        if (owner != null) {
            return PaperAdapter.ADAPTER.isOwnedByCurrentRegion(owner);
        }
        return location.isWorldLoaded() && PaperAdapter.ADAPTER.isOwnedByCurrentRegion(location.getWorld(), location.getBlockX() >> 4, location.getBlockZ() >> 4);
    }

    private Runnable finishLocked(ItemStack[] after) {
        if (!PENDING.remove(group, this)) {
            return null;
        }
        if (type != Material.ENDER_CHEST) {
            try {
                long mark = HopperTransactionUtils.peekSnapshotMark(transactionId, snapshotOwner);
                after = HopperTransactionUtils.applyPendingChanges(after, transactionId, mark);
            }
            finally {
                HopperTransactionUtils.removeOwner(transactionId, snapshotOwner);
            }
        }
        ItemStack[] capturedAfter = after;
        return () -> InventoryChangeListener.publishCapturedContainer(user, location, type, before, capturedAfter);
    }

    private static void publish(Runnable completion) {
        if (completion != null) {
            completion.run();
        }
    }

    private void discard() {
        HopperTransactionUtils.synchronizeTransaction(transactionId, () -> {
            if (PENDING.remove(group, this) && type != Material.ENDER_CHEST) {
                HopperTransactionUtils.removeOwner(transactionId, snapshotOwner);
            }
        });
    }

    private static final class StartResult {
        private final boolean added;
        private final Runnable previousCompletion;

        private StartResult(boolean added, Runnable previousCompletion) {
            this.added = added;
            this.previousCompletion = previousCompletion;
        }
    }
}
