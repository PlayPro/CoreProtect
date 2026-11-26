package net.coreprotect.paper.listener;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.bukkit.GameEvent;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.DoubleChest;
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

import io.papermc.paper.event.entity.ItemTransportingEntityValidateTargetEvent;
import net.coreprotect.CoreProtect;
import net.coreprotect.bukkit.BukkitAdapter;
import net.coreprotect.config.Config;
import net.coreprotect.listener.player.InventoryChangeListener;
import net.coreprotect.paper.PaperAdapter;
import net.coreprotect.thread.Scheduler;
import net.coreprotect.utility.BlockUtils;
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
    private final Map<UUID, TransportTarget> transportTargets = new ConcurrentHashMap<>();
    private final Map<UUID, RecentEmptyCopperChestSkip> recentEmptyCopperChestSkips = new ConcurrentHashMap<>();
    private final Map<TransactionKey, Map<UUID, OpenInteraction>> openInteractionIndexByContainerKey = new ConcurrentHashMap<>();
    private final Map<TransactionKey, Set<UUID>> emptySkipGolemsByContainerKey = new ConcurrentHashMap<>();
    private final AtomicLong nextCleanupMillis = new AtomicLong();

    public CopperGolemChestListener(CoreProtect plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onValidateTarget(ItemTransportingEntityValidateTargetEvent event) {
        if (!event.isAllowed() || !(event.getEntity() instanceof CopperGolem)
                || !Config.getConfig(event.getBlock().getWorld()).ITEM_TRANSACTIONS) {
            return;
        }

        long now = System.currentTimeMillis();
        cleanupOpenInteractions(now);
        CopperGolem golem = (CopperGolem) event.getEntity();
        transportTargets.put(golem.getUniqueId(), new TransportTarget(TransactionKey.of(event.getBlock().getLocation()), new WeakReference<>(golem), now));
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
        if (entity != null && !(entity instanceof CopperGolem)) {
            return;
        }

        Location eventLocation = event.getLocation();
        if (eventLocation == null || eventLocation.getWorld() == null) {
            return;
        }

        if (!Config.getConfig(eventLocation.getWorld()).ITEM_TRANSACTIONS) {
            return;
        }

        Block eventBlock = eventLocation.getBlock();
        if (!isChest(eventBlock.getType())) {
            return;
        }
        BlockState blockState = PaperAdapter.ADAPTER.getBlockState(eventBlock, false);
        if (!(blockState instanceof InventoryHolder)) {
            return;
        }

        Location containerLocation = blockState.getLocation();
        if (containerLocation == null || containerLocation.getWorld() == null) {
            return;
        }

        Material containerType = blockState.getType();

        long now = System.currentTimeMillis();
        cleanupOpenInteractions(now);

        InventoryHolder inventoryHolder = (InventoryHolder) blockState;
        Inventory inventory = inventoryHolder.getInventory();
        Location canonicalLocation = getCanonicalContainerLocation(containerLocation, inventory);
        TransactionKey containerKey = TransactionKey.of(canonicalLocation);

        if (gameEvent == GameEvent.CONTAINER_OPEN) {
            if (entity == null) {
                entity = findOpeningGolem(eventLocation, inventory);
                if (entity == null) {
                    return;
                }
            }
            handleContainerOpen((CopperGolem) entity, canonicalLocation, containerKey, containerType, inventoryHolder, now);
        }
        else {
            if (entity instanceof CopperGolem) {
                handleContainerClose((CopperGolem) entity, canonicalLocation, containerKey, containerType, now);
            }
            else if (entity == null) {
                handleContainerCloseWithoutEntity(containerKey, containerType, now);
            }
        }
    }

    private CopperGolem findOpeningGolem(Location location, Inventory inventory) {
        if (inventory == null || !inventory.getViewers().isEmpty()) {
            return null;
        }

        TransactionKey containerKey = TransactionKey.of(location);
        TransactionKey otherHalfKey = containerKey;
        InventoryHolder holder = PaperAdapter.ADAPTER.getHolder(inventory, false);
        if (holder instanceof DoubleChest) {
            DoubleChest chest = (DoubleChest) holder;
            if (chest.getLeftSide() instanceof BlockState && chest.getRightSide() instanceof BlockState) {
                containerKey = TransactionKey.of(((BlockState) chest.getLeftSide()).getLocation());
                otherHalfKey = TransactionKey.of(((BlockState) chest.getRightSide()).getLocation());
            }
        }

        CopperGolem openingGolem = null;
        long now = System.currentTimeMillis();
        for (TransportTarget target : transportTargets.values()) {
            if (now - target.selectedAtMillis > OPEN_INTERACTION_TIMEOUT_MILLIS
                    || (!target.containerKey.equals(containerKey) && !target.containerKey.equals(otherHalfKey))) {
                continue;
            }
            CopperGolem nearby = getOwnedGolem(target.golem);
            if (nearby == null || !PaperAdapter.ADAPTER.isCopperGolemInteracting(nearby)) {
                continue;
            }
            Location golemLocation = nearby.getLocation();
            if (!Objects.equals(location.getWorld(), golemLocation.getWorld())
                    || Math.abs(golemLocation.getX() - location.getX()) > 3
                    || Math.abs(golemLocation.getY() - location.getY()) > 2
                    || Math.abs(golemLocation.getZ() - location.getZ()) > 3) {
                continue;
            }
            if (openingGolem != null) {
                return null;
            }
            openingGolem = nearby;
        }
        return openingGolem;
    }

    static Location getCanonicalContainerLocation(Location containerLocation, Inventory inventory) {
        if (containerLocation == null || containerLocation.getWorld() == null || inventory == null) {
            return containerLocation;
        }

        InventoryHolder holder = PaperAdapter.ADAPTER.getHolder(inventory, false);
        if (!(holder instanceof DoubleChest)) {
            return containerLocation;
        }

        Location doubleChestLocation = ((DoubleChest) holder).getLocation();
        if (doubleChestLocation == null) {
            return containerLocation;
        }

        Location canonical = new Location(containerLocation.getWorld(), doubleChestLocation.getBlockX(), doubleChestLocation.getBlockY(), doubleChestLocation.getBlockZ());
        if (canonical.getWorld() == null) {
            return containerLocation;
        }

        return canonical;
    }

    private static boolean isChest(Material material) {
        return isCopperChest(material) || material == Material.CHEST || material == Material.TRAPPED_CHEST;
    }

    private static CopperGolem getOwnedGolem(WeakReference<CopperGolem> reference) {
        CopperGolem golem = reference.get();
        return golem != null && PaperAdapter.ADAPTER.isOwnedByCurrentRegion(golem) && golem.isValid() ? golem : null;
    }

    private static boolean isCopperChest(Material material) {
        return BukkitAdapter.ADAPTER != null && BukkitAdapter.ADAPTER.isCopperChest(material);
    }

    private void handleContainerOpen(CopperGolem golem, Location containerLocation, TransactionKey containerKey, Material containerType, InventoryHolder inventoryHolder, long nowMillis) {
        Inventory inventory = inventoryHolder.getInventory();
        if (inventory == null) {
            return;
        }

        HeldItemSnapshot held = getHeldItemSnapshot(golem);
        boolean isCopperChest = isCopperChest(containerType);
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
                UUID golemId = golem.getUniqueId();
                RecentEmptyCopperChestSkip previous = recentEmptyCopperChestSkips.put(golemId, new RecentEmptyCopperChestSkip(containerKey, nowMillis));
                if (previous != null) {
                    unindexEmptySkip(previous.containerKey, golemId);
                }
                indexEmptySkip(containerKey, golemId);
                return;
            }
        }
        else {
            if (!isInventoryEmpty(contents) && !containsMaterial(contents, held.material)) {
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
        OpenInteraction interaction = new OpenInteraction(containerKey, containerLocation.clone(), containerType, baseline, heldMaterial, heldAmount, nowMillis, golem);
        UUID golemId = golem.getUniqueId();
        RecentEmptyCopperChestSkip removedSkip = recentEmptyCopperChestSkips.remove(golemId);
        if (removedSkip != null) {
            unindexEmptySkip(removedSkip.containerKey, golemId);
        }

        OpenInteraction previous = openInteractions.put(golemId, interaction);
        if (previous != null) {
            unindexInteraction(golemId, previous);
        }
        openInteractionIndexByContainerKey.compute(containerKey, (key, entries) -> {
            Map<UUID, OpenInteraction> open = entries == null ? new ConcurrentHashMap<>() : entries;
            open.put(golemId, interaction);
            return open;
        });
    }

    private void unindexInteraction(UUID golemId, OpenInteraction interaction) {
        openInteractionIndexByContainerKey.computeIfPresent(interaction.containerKey, (key, entries) -> {
            entries.remove(golemId, interaction);
            return entries.isEmpty() ? null : entries;
        });
    }

    private void handleContainerClose(CopperGolem golem, Location containerLocation, TransactionKey containerKey, Material containerType, long nowMillis) {
        UUID golemId = golem.getUniqueId();
        OpenInteraction interaction = openInteractions.get(golemId);
        if (interaction == null) {
            if (isCopperChest(containerType)) {
                RecentEmptyCopperChestSkip emptySkip = recentEmptyCopperChestSkips.get(golemId);
                if (emptySkip != null && emptySkip.containerKey.equals(containerKey) && nowMillis - emptySkip.skippedAtMillis <= EMPTY_COPPER_CHEST_SKIP_TTL_MILLIS) {
                    if (recentEmptyCopperChestSkips.remove(golemId, emptySkip)) {
                        unindexEmptySkip(emptySkip.containerKey, golemId);
                    }
                    return;
                }

                scheduleUntrackedCopperChestCloseFinalize(new WeakReference<>(golem), containerLocation.clone(), containerType, 1);
            }
            return;
        }

        if (interaction.containerKey.equals(containerKey) && openInteractions.remove(golemId, interaction)) {
            unindexInteraction(golemId, interaction);
            if (nowMillis - interaction.openedAtMillis <= OPEN_INTERACTION_TIMEOUT_MILLIS) {
                scheduleCloseFinalize(golemId, interaction, false, 1);
            }
        }
    }

    private void handleContainerCloseWithoutEntity(TransactionKey containerKey, Material containerType, long nowMillis) {
        if (isCopperChest(containerType)) {
            clearRecentEmptyCopperChestSkipsByKey(containerKey);
        }

        Map<UUID, OpenInteraction> candidates = openInteractionIndexByContainerKey.get(containerKey);
        if (candidates == null) {
            return;
        }
        candidates.forEach((golemId, interaction) -> {
            if (nowMillis - interaction.openedAtMillis <= OPEN_INTERACTION_TIMEOUT_MILLIS && interaction.probing.compareAndSet(false, true)) {
                scheduleCloseFinalize(golemId, interaction, true, 1);
            }
        });
    }

    private void clearRecentEmptyCopperChestSkipsByKey(TransactionKey containerKey) {
        Set<UUID> golemIds = emptySkipGolemsByContainerKey.remove(containerKey);
        if (golemIds == null || golemIds.isEmpty()) {
            return;
        }

        for (UUID golemId : golemIds) {
            RecentEmptyCopperChestSkip skip = recentEmptyCopperChestSkips.get(golemId);
            if (skip != null && skip.containerKey.equals(containerKey)) {
                recentEmptyCopperChestSkips.remove(golemId, skip);
            }
        }
    }

    private void indexEmptySkip(TransactionKey containerKey, UUID golemId) {
        emptySkipGolemsByContainerKey.computeIfAbsent(containerKey, key -> ConcurrentHashMap.newKeySet()).add(golemId);
    }

    private void unindexEmptySkip(TransactionKey containerKey, UUID golemId) {
        if (containerKey == null || golemId == null) {
            return;
        }
        Set<UUID> golemIds = emptySkipGolemsByContainerKey.get(containerKey);
        if (golemIds == null) {
            return;
        }
        golemIds.remove(golemId);
        if (golemIds.isEmpty()) {
            emptySkipGolemsByContainerKey.remove(containerKey, golemIds);
        }
    }

    private void scheduleCloseFinalize(UUID golemId, OpenInteraction interaction, boolean probe, int attempt) {
        Scheduler.scheduleSyncDelayedTask(plugin, () -> finalizeContainerClose(golemId, interaction, probe, attempt), interaction.location, (int) CLOSE_FINALIZE_DELAY_TICKS);
    }

    private void scheduleUntrackedCopperChestCloseFinalize(WeakReference<CopperGolem> golem, Location containerLocation, Material containerType, int attempt) {
        Scheduler.scheduleSyncDelayedTask(plugin, () -> finalizeUntrackedCopperChestClose(golem, containerLocation, containerType, attempt), containerLocation, (int) CLOSE_FINALIZE_DELAY_TICKS);
    }

    private void finalizeContainerClose(UUID golemId, OpenInteraction interaction, boolean probe, int attempt) {
        if (probe && openInteractions.get(golemId) != interaction) {
            return;
        }

        CopperGolem golem = getOwnedGolem(interaction.golem);
        if (golem == null) {
            interaction.probing.set(false);
            return;
        }

        Block block = interaction.location.getBlock();
        BlockState blockState = PaperAdapter.ADAPTER.getBlockState(block, false);
        if (blockState.getType() != interaction.containerType || !(blockState instanceof InventoryHolder)) {
            interaction.probing.set(false);
            return;
        }

        Inventory inventory = ((InventoryHolder) blockState).getInventory();
        if (inventory == null) {
            interaction.probing.set(false);
            return;
        }

        ItemStack[] currentContents = inventory.getContents();
        if (currentContents == null) {
            interaction.probing.set(false);
            return;
        }

        boolean changed = hasInventoryChanged(interaction.baselineState, currentContents);
        if (!changed || !isAttributableToGolem(golem, interaction, currentContents)) {
            if (attempt < CLOSE_FINALIZE_MAX_ATTEMPTS) {
                scheduleCloseFinalize(golemId, interaction, probe, attempt + 1);
            }
            else {
                interaction.probing.set(false);
            }
            return;
        }

        if (!probe || openInteractions.remove(golemId, interaction)) {
            if (probe) {
                unindexInteraction(golemId, interaction);
            }
            InventoryChangeListener.queueContainerSnapshots(USERNAME, interaction.location, interaction.containerType, interaction.baselineState, currentContents);
            updateOtherBaselines(interaction, currentContents);
        }
    }

    private void updateOtherBaselines(OpenInteraction completed, ItemStack[] contents) {
        Map<UUID, OpenInteraction> others = openInteractionIndexByContainerKey.get(completed.containerKey);
        if (others == null) {
            return;
        }
        ItemStack[] snapshot = ItemUtils.getContainerState(contents);
        for (OpenInteraction other : others.values()) {
            if (other == completed || hasInventoryChanged(completed.baselineState, other.baselineState)) {
                continue;
            }
            CopperGolem golem = getOwnedGolem(other.golem);
            if (golem != null) {
                HeldItemSnapshot held = getHeldItemSnapshot(golem);
                if (held.material == other.heldMaterial && held.amount == other.heldAmount) {
                    other.baselineState = snapshot;
                }
            }
        }
    }

    private void finalizeUntrackedCopperChestClose(WeakReference<CopperGolem> reference, Location containerLocation, Material containerType, int attempt) {
        CopperGolem golem = getOwnedGolem(reference);
        if (golem == null || !isCopperChest(containerType)) {
            return;
        }

        ItemStack heldNowStack = getHeldItemStack(golem);
        if (isEmptyItem(heldNowStack) || heldNowStack == null || heldNowStack.getAmount() <= 0) {
            if (attempt < CLOSE_FALLBACK_MAX_ATTEMPTS) {
                scheduleUntrackedCopperChestCloseFinalize(reference, containerLocation, containerType, attempt + 1);
            }
            return;
        }

        int heldAmount = heldNowStack.getAmount();
        if (heldAmount > 16) {
            return;
        }

        BlockState blockState = PaperAdapter.ADAPTER.getBlockState(containerLocation.getBlock(), false);
        if (!(blockState instanceof InventoryHolder) || !isCopperChest(blockState.getType())) {
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

        InventoryChangeListener.queueContainerSnapshots(USERNAME, containerLocation, containerType, baselineCandidate, currentContents);
    }

    private boolean isAttributableToGolem(CopperGolem golem, OpenInteraction interaction, ItemStack[] currentContents) {
        boolean isCopperChest = isCopperChest(interaction.containerType);
        HeldItemSnapshot heldNow = getHeldItemSnapshot(golem);

        if (isCopperChest) {
            if (heldNow.material == null || heldNow.amount <= 0 || !otherSlotsUnchanged(interaction.baselineState, currentContents, heldNow.material)) {
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

        if (!otherSlotsUnchanged(interaction.baselineState, currentContents, heldMaterial)) {
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

    private void cleanupOpenInteractions(long nowMillis) {
        long next = nextCleanupMillis.get();
        if (nowMillis < next || !nextCleanupMillis.compareAndSet(next, nowMillis + CLEANUP_INTERVAL_MILLIS)) {
            return;
        }
        transportTargets.values().removeIf(target -> nowMillis - target.selectedAtMillis > OPEN_INTERACTION_TIMEOUT_MILLIS || target.golem.get() == null);
        openInteractions.forEach((golemId, interaction) -> {
            if ((nowMillis - interaction.openedAtMillis > OPEN_INTERACTION_TIMEOUT_MILLIS || interaction.golem.get() == null)
                    && openInteractions.remove(golemId, interaction)) {
                unindexInteraction(golemId, interaction);
            }
        });
        recentEmptyCopperChestSkips.forEach((golemId, skip) -> {
            if (nowMillis - skip.skippedAtMillis > EMPTY_COPPER_CHEST_SKIP_TTL_MILLIS && recentEmptyCopperChestSkips.remove(golemId, skip)) {
                unindexEmptySkip(skip.containerKey, golemId);
            }
        });
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
        return item == null || BlockUtils.isAir(item.getType()) || item.getAmount() <= 0;
    }

    private boolean containsMaterial(ItemStack[] state, Material material) {
        for (ItemStack item : state) {
            if (!isEmptyItem(item) && item.getType() == material) {
                return true;
            }
        }
        return false;
    }

    private boolean otherSlotsUnchanged(ItemStack[] before, ItemStack[] after, Material material) {
        if (before.length != after.length) {
            return false;
        }
        for (int slot = 0; slot < before.length; slot++) {
            ItemStack oldItem = before[slot];
            ItemStack newItem = after[slot];
            if (Objects.equals(oldItem, newItem)) {
                continue;
            }
            if ((!isEmptyItem(oldItem) && oldItem.getType() != material) || (!isEmptyItem(newItem) && newItem.getType() != material)) {
                return false;
            }
        }
        return true;
    }

    private boolean hasSpaceForMaterial(ItemStack[] state, Material material) {
        if (state == null || material == null) {
            return false;
        }

        for (ItemStack item : state) {
            if (isEmptyItem(item)) {
                return true;
            }
            if (item.getType() == material && item.getAmount() < item.getMaxStackSize()) {
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
        private ItemStack[] baselineState;
        private final Material heldMaterial;
        private final int heldAmount;
        private final long openedAtMillis;
        private final WeakReference<CopperGolem> golem;
        private final AtomicBoolean probing = new AtomicBoolean();

        private OpenInteraction(TransactionKey containerKey, Location location, Material containerType, ItemStack[] baselineState, Material heldMaterial, int heldAmount, long openedAtMillis, CopperGolem golem) {
            this.containerKey = containerKey;
            this.location = location;
            this.containerType = containerType;
            this.baselineState = baselineState;
            this.heldMaterial = heldMaterial;
            this.heldAmount = heldAmount;
            this.openedAtMillis = openedAtMillis;
            this.golem = new WeakReference<>(golem);
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

    private static final class TransportTarget {
        private final TransactionKey containerKey;
        private final WeakReference<CopperGolem> golem;
        private final long selectedAtMillis;

        private TransportTarget(TransactionKey containerKey, WeakReference<CopperGolem> golem, long selectedAtMillis) {
            this.containerKey = containerKey;
            this.golem = golem;
            this.selectedAtMillis = selectedAtMillis;
        }
    }
}
