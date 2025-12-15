package net.coreprotect.paper.listener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.GameEvent;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.BlockState;
import org.bukkit.entity.CopperGolem;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.GenericGameEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import net.coreprotect.CoreProtect;
import net.coreprotect.bukkit.BukkitAdapter;
import net.coreprotect.config.Config;
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.listener.player.InventoryChangeListener;
import net.coreprotect.utility.ItemUtils;

public final class CopperGolemChestListener implements Listener {

    private static final String USERNAME = "#copper_golem";
    private static final long OPEN_INTERACTION_TIMEOUT_MILLIS = 20000L;
    private static final long CLEANUP_INTERVAL_MILLIS = 60000L;
    private static final long EMPTY_COPPER_CHEST_SKIP_TTL_MILLIS = 6000L;
    private static final long CLOSE_FINALIZE_DELAY_TICKS = 1L;
    private static final int CLOSE_FINALIZE_MAX_ATTEMPTS = 3;
    private static final int CLOSE_FALLBACK_MAX_ATTEMPTS = 2;

    private final CoreProtect plugin;
    private final Map<UUID, OpenInteraction> openInteractions = new ConcurrentHashMap<>();
    private final Map<UUID, RecentEmptyCopperChestSkip> recentEmptyCopperChestSkips = new ConcurrentHashMap<>();
    private volatile long lastCleanupMillis;

    public CopperGolemChestListener(CoreProtect plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGenericGameEvent(GenericGameEvent event) {
        if (event == null) {
            return;
        }

        GameEvent gameEvent = event.getEvent();
        if (gameEvent != GameEvent.CONTAINER_OPEN && gameEvent != GameEvent.CONTAINER_CLOSE) {
            return;
        }

        Entity entity = event.getEntity();
        if (!(entity instanceof CopperGolem)) {
            return;
        }

        Location eventLocation = event.getLocation();
        if (eventLocation == null || eventLocation.getWorld() == null) {
            return;
        }

        if (!Config.getConfig(eventLocation.getWorld()).ITEM_TRANSACTIONS) {
            return;
        }

        BlockState blockState = eventLocation.getBlock().getState();
        if (!(blockState instanceof InventoryHolder)) {
            return;
        }

        Location containerLocation = blockState.getLocation();
        if (containerLocation == null || containerLocation.getWorld() == null) {
            return;
        }

        Material containerType = blockState.getType();
        boolean isCopperChest = BukkitAdapter.ADAPTER.isCopperChest(containerType);
        boolean isStandardChest = containerType == Material.CHEST || containerType == Material.TRAPPED_CHEST;
        if (!isCopperChest && !isStandardChest) {
            return;
        }

        long now = System.currentTimeMillis();
        cleanupOpenInteractions(now);

        CopperGolem golem = (CopperGolem) entity;
        TransactionKey containerKey = TransactionKey.of(containerLocation);

        if (gameEvent == GameEvent.CONTAINER_OPEN) {
            handleContainerOpen(golem, containerLocation, containerKey, containerType, (InventoryHolder) blockState, now);
        }
        else {
            handleContainerClose(golem, containerLocation, containerKey, containerType, (InventoryHolder) blockState, now);
        }
    }

    private void handleContainerOpen(CopperGolem golem, Location containerLocation, TransactionKey containerKey, Material containerType, InventoryHolder inventoryHolder, long nowMillis) {
        Inventory inventory = inventoryHolder.getInventory();
        if (inventory == null) {
            return;
        }

        HeldItemSnapshot held = getHeldItemSnapshot(golem);
        boolean isCopperChest = BukkitAdapter.ADAPTER.isCopperChest(containerType);
        if (isCopperChest) {
            if (held.material != null) {
                return;
            }
        }
        else {
            if (held.material == null) {
                return;
            }
        }

        ItemStack[] contents = inventory.getContents();
        if (contents == null) {
            return;
        }

        if (isCopperChest) {
            if (isInventoryEmpty(contents)) {
                recentEmptyCopperChestSkips.put(golem.getUniqueId(), new RecentEmptyCopperChestSkip(containerKey, nowMillis));
                return;
            }
        }
        else {
            if (!isInventoryEmpty(contents) && !containsOnlyMaterial(contents, held.material)) {
                return;
            }

            if (!hasSpaceForMaterial(contents, held.material)) {
                return;
            }
        }

        ItemStack[] baseline = ItemUtils.getContainerState(contents);
        if (baseline == null) {
            return;
        }

        Material heldMaterial = isCopperChest ? null : held.material;
        int heldAmount = isCopperChest ? 0 : held.amount;
        OpenInteraction interaction = new OpenInteraction(containerKey, containerLocation.clone(), containerType, baseline, heldMaterial, heldAmount, nowMillis);
        recentEmptyCopperChestSkips.remove(golem.getUniqueId());
        openInteractions.put(golem.getUniqueId(), interaction);
    }

    private void handleContainerClose(CopperGolem golem, Location containerLocation, TransactionKey containerKey, Material containerType, InventoryHolder inventoryHolder, long nowMillis) {
        UUID golemId = golem.getUniqueId();
        OpenInteraction interaction = openInteractions.get(golemId);
        if (interaction == null) {
            if (BukkitAdapter.ADAPTER.isCopperChest(containerType)) {
                RecentEmptyCopperChestSkip emptySkip = recentEmptyCopperChestSkips.get(golemId);
                if (emptySkip != null && emptySkip.containerKey.equals(containerKey) && (nowMillis - emptySkip.skippedAtMillis) <= EMPTY_COPPER_CHEST_SKIP_TTL_MILLIS) {
                    recentEmptyCopperChestSkips.remove(golemId, emptySkip);
                    return;
                }

                scheduleUntrackedCopperChestCloseFinalize(golemId, containerLocation.clone(), containerType, 1);
                return;
            }

            return;
        }

        if (nowMillis - interaction.openedAtMillis > OPEN_INTERACTION_TIMEOUT_MILLIS) {
            openInteractions.remove(golemId, interaction);
            return;
        }

        if (!interaction.containerKey.equals(containerKey)) {
            return;
        }

        openInteractions.remove(golemId, interaction);
        scheduleCloseFinalize(golemId, interaction, containerKey, 1);
    }

    private void scheduleCloseFinalize(UUID golemId, OpenInteraction interaction, TransactionKey containerKey, int attempt) {
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> finalizeContainerClose(golemId, interaction, containerKey, attempt), CLOSE_FINALIZE_DELAY_TICKS);
    }

    private void scheduleUntrackedCopperChestCloseFinalize(UUID golemId, Location containerLocation, Material containerType, int attempt) {
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> finalizeUntrackedCopperChestClose(golemId, containerLocation, containerType, attempt), CLOSE_FINALIZE_DELAY_TICKS);
    }

    private void finalizeContainerClose(UUID golemId, OpenInteraction interaction, TransactionKey containerKey, int attempt) {
        Entity entity = plugin.getServer().getEntity(golemId);
        if (!(entity instanceof CopperGolem)) {
            return;
        }

        BlockState blockState = interaction.location.getBlock().getState();
        if (!(blockState instanceof InventoryHolder)) {
            return;
        }

        Inventory inventory = ((InventoryHolder) blockState).getInventory();
        if (inventory == null) {
            return;
        }

        ItemStack[] currentContents = inventory.getContents();
        if (currentContents == null) {
            return;
        }

        boolean changed = hasInventoryChanged(interaction.baselineState, currentContents);
        if (!changed) {
            if (attempt < CLOSE_FINALIZE_MAX_ATTEMPTS) {
                scheduleCloseFinalize(golemId, interaction, containerKey, attempt + 1);
            }
            return;
        }

        CopperGolem golem = (CopperGolem) entity;
        if (!isAttributableToGolem(golem, interaction, currentContents)) {
            return;
        }

        recordForcedContainerState(interaction.location, currentContents);
        InventoryChangeListener.inventoryTransaction(USERNAME, interaction.location, interaction.baselineState);
    }

    private void finalizeUntrackedCopperChestClose(UUID golemId, Location containerLocation, Material containerType, int attempt) {
        Entity entity = plugin.getServer().getEntity(golemId);
        if (!(entity instanceof CopperGolem)) {
            return;
        }

        if (!BukkitAdapter.ADAPTER.isCopperChest(containerType)) {
            return;
        }

        CopperGolem golem = (CopperGolem) entity;
        ItemStack heldNowStack = getHeldItemStack(golem);
        if (isEmptyItem(heldNowStack) || heldNowStack == null || heldNowStack.getAmount() <= 0) {
            if (attempt < CLOSE_FALLBACK_MAX_ATTEMPTS) {
                scheduleUntrackedCopperChestCloseFinalize(golemId, containerLocation, containerType, attempt + 1);
            }
            return;
        }

        int heldAmount = heldNowStack.getAmount();
        if (heldAmount > 16) {
            return;
        }

        BlockState blockState = containerLocation.getBlock().getState();
        if (!(blockState instanceof InventoryHolder) || !BukkitAdapter.ADAPTER.isCopperChest(blockState.getType())) {
            return;
        }

        Inventory inventory = ((InventoryHolder) blockState).getInventory();
        if (inventory == null) {
            return;
        }

        ItemStack[] currentContents = inventory.getContents();
        if (currentContents == null) {
            return;
        }

        ItemStack[] baselineCandidate = reconstructCopperChestBaseline(currentContents, heldNowStack);
        if (baselineCandidate == null) {
            return;
        }

        recordForcedContainerState(containerLocation, currentContents);
        InventoryChangeListener.inventoryTransaction(USERNAME, containerLocation, baselineCandidate);
    }

    private boolean isAttributableToGolem(CopperGolem golem, OpenInteraction interaction, ItemStack[] currentContents) {
        boolean isCopperChest = BukkitAdapter.ADAPTER.isCopperChest(interaction.containerType);
        HeldItemSnapshot heldNow = getHeldItemSnapshot(golem);

        if (isCopperChest) {
            if (heldNow.material == null || heldNow.amount <= 0) {
                return false;
            }

            int baselineTotal = sumAllItems(interaction.baselineState);
            int currentTotal = sumAllItems(currentContents);
            int totalRemoved = baselineTotal - currentTotal;
            if (totalRemoved != heldNow.amount) {
                return false;
            }

            int removedSameMaterial = sumMaterialAmount(interaction.baselineState, heldNow.material) - sumMaterialAmount(currentContents, heldNow.material);
            return removedSameMaterial == heldNow.amount;
        }

        Material heldMaterial = interaction.heldMaterial;
        if (heldMaterial == null || interaction.heldAmount <= 0) {
            return false;
        }

        if (!containsOnlyMaterial(currentContents, heldMaterial)) {
            return false;
        }

        int baselineCount = sumMaterialAmount(interaction.baselineState, heldMaterial);
        int currentCount = sumMaterialAmount(currentContents, heldMaterial);
        int added = currentCount - baselineCount;
        if (added <= 0) {
            return false;
        }

        int heldAfter = (heldNow.material == heldMaterial ? heldNow.amount : 0);
        int removedFromGolem = interaction.heldAmount - heldAfter;
        return added == removedFromGolem;
    }

    private ItemStack getHeldItemStack(CopperGolem copperGolem) {
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
        return mainHand.clone();
    }

    private ItemStack[] reconstructCopperChestBaseline(ItemStack[] currentContents, ItemStack heldNowStack) {
        if (currentContents == null || heldNowStack == null || isEmptyItem(heldNowStack) || heldNowStack.getAmount() <= 0) {
            return null;
        }

        ItemStack[] baseline = ItemUtils.getContainerState(currentContents);
        if (baseline == null) {
            return null;
        }

        int addAmount = heldNowStack.getAmount();
        int maxStack = heldNowStack.getMaxStackSize();

        for (int i = 0; i < baseline.length; i++) {
            ItemStack item = baseline[i];
            if (item == null) {
                continue;
            }
            if (item.isSimilar(heldNowStack)) {
                if (item.getAmount() + addAmount > maxStack) {
                    return null;
                }
                item.setAmount(item.getAmount() + addAmount);
                return baseline;
            }
        }

        for (int i = 0; i < baseline.length; i++) {
            ItemStack item = baseline[i];
            if (isEmptyItem(item)) {
                ItemStack placed = heldNowStack.clone();
                placed.setAmount(addAmount);
                baseline[i] = placed;
                return baseline;
            }
        }

        return null;
    }

    private void recordForcedContainerState(Location location, ItemStack[] contents) {
        if (location == null) {
            return;
        }

        ItemStack[] snapshot = ItemUtils.getContainerState(contents);
        if (snapshot == null) {
            return;
        }

        String loggingContainerId = USERNAME + "." + location.getBlockX() + "." + location.getBlockY() + "." + location.getBlockZ();
        List<ItemStack[]> forceList = ConfigHandler.forceContainer.get(loggingContainerId);
        if (forceList == null) {
            forceList = Collections.synchronizedList(new ArrayList<>());
            ConfigHandler.forceContainer.put(loggingContainerId, forceList);
        }
        forceList.add(snapshot);
    }

    private void cleanupOpenInteractions(long nowMillis) {
        long last = lastCleanupMillis;
        if (nowMillis - last < CLEANUP_INTERVAL_MILLIS) {
            return;
        }
        lastCleanupMillis = nowMillis;

        for (Map.Entry<UUID, OpenInteraction> entry : openInteractions.entrySet()) {
            UUID golemId = entry.getKey();
            OpenInteraction interaction = entry.getValue();
            if (interaction == null || nowMillis - interaction.openedAtMillis > OPEN_INTERACTION_TIMEOUT_MILLIS || plugin.getServer().getEntity(golemId) == null) {
                openInteractions.remove(golemId, interaction);
            }
        }

        for (Map.Entry<UUID, RecentEmptyCopperChestSkip> entry : recentEmptyCopperChestSkips.entrySet()) {
            UUID golemId = entry.getKey();
            RecentEmptyCopperChestSkip skip = entry.getValue();
            if (skip == null || nowMillis - skip.skippedAtMillis > EMPTY_COPPER_CHEST_SKIP_TTL_MILLIS || plugin.getServer().getEntity(golemId) == null) {
                recentEmptyCopperChestSkips.remove(golemId, skip);
            }
        }
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

    private boolean containsOnlyMaterial(ItemStack[] state, Material material) {
        if (state == null || material == null) {
            return false;
        }
        for (ItemStack item : state) {
            if (isEmptyItem(item)) {
                continue;
            }
            if (item.getType() != material) {
                return false;
            }
        }
        return true;
    }

    private boolean hasSpaceForMaterial(ItemStack[] state, Material material) {
        if (state == null || material == null) {
            return false;
        }

        int maxStackSize = material.getMaxStackSize();
        for (ItemStack item : state) {
            if (isEmptyItem(item)) {
                return true;
            }
            if (item.getType() == material && item.getAmount() < maxStackSize) {
                return true;
            }
        }

        return false;
    }

    private int sumMaterialAmount(ItemStack[] state, Material material) {
        if (state == null || material == null) {
            return 0;
        }
        int total = 0;
        for (ItemStack item : state) {
            if (!isEmptyItem(item) && item.getType() == material) {
                total += item.getAmount();
            }
        }
        return total;
    }

    private int sumAllItems(ItemStack[] state) {
        if (state == null) {
            return 0;
        }
        int total = 0;
        for (ItemStack item : state) {
            if (!isEmptyItem(item)) {
                total += item.getAmount();
            }
        }
        return total;
    }

    private HeldItemSnapshot getHeldItemSnapshot(CopperGolem copperGolem) {
        if (copperGolem == null) {
            return HeldItemSnapshot.EMPTY;
        }
        EntityEquipment equipment = copperGolem.getEquipment();
        if (equipment == null) {
            return HeldItemSnapshot.EMPTY;
        }
        ItemStack mainHand = equipment.getItemInMainHand();
        if (isEmptyItem(mainHand)) {
            return HeldItemSnapshot.EMPTY;
        }
        return new HeldItemSnapshot(mainHand.getType(), mainHand.getAmount());
    }

    private static final class OpenInteraction {

        private final TransactionKey containerKey;
        private final Location location;
        private final Material containerType;
        private final ItemStack[] baselineState;
        private final Material heldMaterial;
        private final int heldAmount;
        private final long openedAtMillis;

        private OpenInteraction(TransactionKey containerKey, Location location, Material containerType, ItemStack[] baselineState, Material heldMaterial, int heldAmount, long openedAtMillis) {
            this.containerKey = containerKey;
            this.location = location;
            this.containerType = containerType;
            this.baselineState = baselineState;
            this.heldMaterial = heldMaterial;
            this.heldAmount = heldAmount;
            this.openedAtMillis = openedAtMillis;
        }
    }

    private static final class RecentEmptyCopperChestSkip {

        private final TransactionKey containerKey;
        private final long skippedAtMillis;

        private RecentEmptyCopperChestSkip(TransactionKey containerKey, long skippedAtMillis) {
            this.containerKey = containerKey;
            this.skippedAtMillis = skippedAtMillis;
        }
    }

    private static final class HeldItemSnapshot {

        private static final HeldItemSnapshot EMPTY = new HeldItemSnapshot(null, 0);

        private final Material material;
        private final int amount;

        private HeldItemSnapshot(Material material, int amount) {
            this.material = material;
            this.amount = amount;
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
