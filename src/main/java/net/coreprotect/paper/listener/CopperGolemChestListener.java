package net.coreprotect.paper.listener;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.CopperGolem;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import io.papermc.paper.event.entity.ItemTransportingEntityValidateTargetEvent;
import net.coreprotect.CoreProtect;
import net.coreprotect.bukkit.BukkitAdapter;
import net.coreprotect.config.Config;
import net.coreprotect.listener.player.InventoryChangeListener;
import net.coreprotect.utility.ItemUtils;

public final class CopperGolemChestListener implements Listener {

    private static final String COPPER_GOLEM_NAME = "COPPER_GOLEM";
    private static final String USERNAME = "#copper_golem";
    private static final long INITIAL_DELAY_TICKS = 5L;
    private static final long POLL_INTERVAL_TICKS = 15L;
    private static final long MAX_POLL_DURATION_TICKS = 600L;
    private static final long MIN_THROTTLE_MILLIS = 2800L;

    private final CoreProtect plugin;
    private final Map<TransactionKey, PendingTransaction> pendingTransactions = new ConcurrentHashMap<>();
    private final Map<TransactionKey, Long> throttleUntil = new ConcurrentHashMap<>();

    public CopperGolemChestListener(CoreProtect plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onValidate(ItemTransportingEntityValidateTargetEvent event) {
        if (!event.isAllowed()) {
            return;
        }

        Entity entity = event.getEntity();
        if (entity == null || entity.getType() == null || !COPPER_GOLEM_NAME.equals(entity.getType().name())) {
            return;
        }
        CopperGolem copperGolem = (CopperGolem) entity;
        Material heldMaterial = getHeldItemMaterial(copperGolem);

        Block block = event.getBlock();
        if (block == null) {
            return;
        }

        BlockState blockState = block.getState();
        if (!(blockState instanceof InventoryHolder)) {
            return;
        }

        Location location = blockState.getLocation();
        if (location == null || location.getWorld() == null) {
            return;
        }

        if (!Config.getConfig(location.getWorld()).ITEM_TRANSACTIONS) {
            return;
        }

        scheduleTransaction(copperGolem, blockState, heldMaterial);
    }

    private void scheduleTransaction(CopperGolem copperGolem, BlockState blockState, Material heldMaterial) {
        Location location = blockState.getLocation();
        if (location == null || copperGolem == null) {
            return;
        }

        TransactionKey transactionKey = TransactionKey.of(location);
        Location targetLocation = location.clone();
        ItemStack[] baselineState = captureInventoryState(targetLocation);
        if (baselineState == null) {
            return;
        }
        if (!shouldMonitorInteraction(blockState.getType(), baselineState, heldMaterial)) {
            return;
        }

        PendingTransaction existing = pendingTransactions.get(transactionKey);
        if (existing != null) {
            existing.refresh(baselineState);
            return;
        }

        if (isThrottled(transactionKey)) {
            return;
        }

        PendingTransaction scheduled = new PendingTransaction(transactionKey, targetLocation, baselineState);
        pendingTransactions.put(transactionKey, scheduled);
        throttleUntil.put(transactionKey, System.currentTimeMillis() + MIN_THROTTLE_MILLIS);
        scheduled.start();
    }

    private ItemStack[] captureInventoryState(Location location) {
        if (location == null || location.getWorld() == null) {
            return null;
        }

        BlockState blockState = location.getBlock().getState();
        if (!(blockState instanceof InventoryHolder)) {
            return null;
        }

        InventoryHolder inventoryHolder = (InventoryHolder) blockState;
        Inventory inventory = inventoryHolder.getInventory();
        if (inventory == null) {
            return null;
        }

        return ItemUtils.getContainerState(inventory.getContents());
    }

    private boolean hasInventoryChanged(ItemStack[] previousState, ItemStack[] currentState) {
        if (previousState == null || currentState == null) {
            return true;
        }
        if (previousState.length != currentState.length) {
            return true;
        }

        for (int i = 0; i < previousState.length; i++) {
            ItemStack previousItem = previousState[i];
            ItemStack currentItem = currentState[i];

            if (previousItem == null && currentItem == null) {
                continue;
            }

            if (previousItem == null || currentItem == null) {
                return true;
            }

            if (!previousItem.equals(currentItem)) {
                return true;
            }
        }

        return false;
    }

    private boolean shouldMonitorInteraction(Material blockType, ItemStack[] baselineState, Material heldMaterial) {
        boolean isCopperChest = BukkitAdapter.ADAPTER.isCopperChest(blockType);
        boolean isStandardChest = blockType == Material.CHEST || blockType == Material.TRAPPED_CHEST;
        boolean golemHoldingItem = heldMaterial != null && heldMaterial != Material.AIR;

        if (!isCopperChest && !isStandardChest) {
            return false;
        }

        if (isCopperChest) {
            return !golemHoldingItem && !isInventoryEmpty(baselineState);
        }

        if (!golemHoldingItem) {
            return false;
        }

        if (isInventoryEmpty(baselineState)) {
            return true;
        }

        return containsMaterial(baselineState, heldMaterial);
    }

    private boolean isInventoryEmpty(ItemStack[] state) {
        if (state == null) {
            return true;
        }

        for (ItemStack item : state) {
            if (!isEmptyItem(item)) {
                return false;
            }
        }

        return true;
    }

    private boolean isEmptyItem(ItemStack item) {
        return item == null || item.getType().isAir() || item.getAmount() <= 0;
    }

    private Material getHeldItemMaterial(CopperGolem copperGolem) {
        if (copperGolem == null) {
            return null;
        }
        EntityEquipment equipment = copperGolem.getEquipment();
        if (equipment == null) {
            return null;
        }
        ItemStack mainHand = equipment.getItemInMainHand();
        if (isEmptyItem(mainHand)) {
            return null;
        }
        return mainHand.getType();
    }

    private boolean containsMaterial(ItemStack[] state, Material material) {
        if (state == null || material == null) {
            return false;
        }
        for (ItemStack item : state) {
            if (item != null && item.getType() == material && item.getAmount() > 0) {
                return true;
            }
        }
        return false;
    }

    private boolean isThrottled(TransactionKey transactionKey) {
        Long eligibleAt = throttleUntil.get(transactionKey);
        if (eligibleAt == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        if (eligibleAt <= now) {
            throttleUntil.remove(transactionKey, eligibleAt);
            return false;
        }
        return true;
    }

    private final class PendingTransaction implements Runnable {

        private final TransactionKey transactionKey;
        private final Location targetLocation;
        private ItemStack[] baselineState;
        private BukkitTask task;
        private long ticksElapsed;
        private long ticksSinceLastTrigger;

        private PendingTransaction(TransactionKey transactionKey, Location targetLocation, ItemStack[] baselineState) {
            this.transactionKey = transactionKey;
            this.targetLocation = targetLocation;
            this.baselineState = baselineState;
        }

        private void start() {
            task = plugin.getServer().getScheduler().runTaskTimer(plugin, this, INITIAL_DELAY_TICKS, POLL_INTERVAL_TICKS);
        }

        private void refresh(ItemStack[] newBaselineState) {
            ticksSinceLastTrigger = 0L;
            if (newBaselineState != null) {
                baselineState = newBaselineState;
            }
        }

        @Override
        public void run() {
            long increment = ticksElapsed == 0L ? INITIAL_DELAY_TICKS : POLL_INTERVAL_TICKS;
            ticksElapsed += increment;
            ticksSinceLastTrigger += increment;
            if (ticksSinceLastTrigger > MAX_POLL_DURATION_TICKS) {
                cancelAndRemove();
                return;
            }

            ItemStack[] currentState = captureInventoryState(targetLocation);
            if (currentState == null) {
                cancelAndRemove();
                return;
            }

            boolean stateChanged = hasInventoryChanged(baselineState, currentState);
            if (stateChanged) {
                InventoryChangeListener.inventoryTransaction(USERNAME, targetLocation, baselineState);
                baselineState = ItemUtils.getContainerState(currentState);
                // cancelAndRemove();
                // return;
            }
        }

        private void cancel() {
            if (task != null) {
                task.cancel();
            }
        }

        private void cancelAndRemove() {
            cancel();
            pendingTransactions.remove(transactionKey, this);
        }
    }

    private static final class TransactionKey {

        private final UUID worldId;
        private final int x;
        private final int y;
        private final int z;

        private TransactionKey(UUID worldId, int x, int y, int z) {
            this.worldId = worldId;
            this.x = x;
            this.y = y;
            this.z = z;
        }

        private static TransactionKey of(Location location) {
            if (location == null || location.getWorld() == null) {
                throw new IllegalArgumentException("Location must have world");
            }
            return new TransactionKey(location.getWorld().getUID(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof TransactionKey)) {
                return false;
            }
            TransactionKey other = (TransactionKey) obj;
            return worldId.equals(other.worldId) && x == other.x && y == other.y && z == other.z;
        }

        @Override
        public int hashCode() {
            return Objects.hash(worldId, x, y, z);
        }

    }
}
