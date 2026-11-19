package net.coreprotect.listener.player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.DoubleChest;
import org.bukkit.block.Hopper;
import org.bukkit.entity.Entity;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import net.coreprotect.CoreProtect;
import net.coreprotect.config.Config;
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.consumer.Queue;
import net.coreprotect.database.logger.ContainerLogger;
import net.coreprotect.model.BlockGroup;
import net.coreprotect.paper.PaperAdapter;
import net.coreprotect.thread.Scheduler;
import net.coreprotect.utility.EntitySpawnTracking;
import net.coreprotect.utility.ErrorReporter;
import net.coreprotect.utility.HopperTransactionUtils;
import net.coreprotect.utility.ItemUtils;
import net.coreprotect.utility.Validate;
import us.lynuxcraft.deadsilenceiv.advancedchests.AdvancedChestsAPI;
import us.lynuxcraft.deadsilenceiv.advancedchests.chest.AdvancedChest;

public final class InventoryChangeListener implements Listener {

    private static final Map<UUID, PendingEntityContainerTransaction> pendingEntityTransactions = new HashMap<>();

    public static boolean inventoryTransaction(String user, Location location, ItemStack[] inventoryData) {
        if (location != null) {
            return inventoryTransaction(user, location.getBlock().getState(), inventoryData);
        }
        return false;
    }

    public static boolean inventoryTransaction(String user, BlockState blockState, ItemStack[] inventoryData) {
        if (user != null && blockState != null) {
            if (user.length() > 0) {
                Material type = blockState.getType();

                if (BlockGroup.CONTAINERS.contains(type) && blockState instanceof InventoryHolder) {
                    InventoryHolder inventoryHolder = (InventoryHolder) blockState;
                    return onInventoryInteract(user, inventoryHolder.getInventory(), inventoryData, null, blockState.getLocation(), false);
                }
            }
        }
        return false;
    }

    public static void queueContainerSnapshots(String user, Location location, Material type, ItemStack[] before, ItemStack[] after) {
        if (user == null || user.isEmpty() || location == null || location.getWorld() == null || type == null || before == null || after == null
                || !Config.getConfig(location.getWorld()).ITEM_TRANSACTIONS || ItemUtils.compareContainers(before, after)) {
            return;
        }

        Location capturedLocation = location.clone();
        ItemStack[] capturedBefore = ItemUtils.getContainerState(before);
        ItemStack[] capturedAfter = ItemUtils.getContainerState(after);
        if (type != Material.ENDER_CHEST) {
            HopperPullListener.flushPendingPull(capturedLocation, capturedBefore);
        }
        publishCapturedContainer(user, capturedLocation, type, capturedBefore, capturedAfter);
    }

    static void publishCapturedContainer(String user, Location location, Material type, ItemStack[] before, ItemStack[] after) {
        if (after == null) {
            return;
        }
        if (ItemUtils.compareContainers(before, after)) {
            if ("#dispenser".equals(user) && Config.getConfig(location.getWorld()).DUPLICATE_SUPPRESSION) {
                ContainerLogger.recordDispenserUnchanged(location);
            }
            return;
        }
        ContainerTransactionDispatcher.submit(user, type, location, () -> queueContainerTransaction(user, location, type, before, after));
    }

    public static void queueContainerBreak(String user, Location location, Material type, ItemStack[] contents) {
        if (user == null || user.isEmpty() || location == null || location.getWorld() == null || type == null || contents == null) {
            return;
        }

        Location capturedLocation = location.clone();
        Location boundaryLocation = getCanonicalContainerLocation(location);
        ItemStack[] capturedContents = ItemUtils.getContainerState(contents);
        String transactionId = HopperTransactionUtils.getTransactionId(boundaryLocation);
        ContainerCapture.finishAt(boundaryLocation);
        HopperPullListener.flushPendingPull(boundaryLocation);
        ContainerTransactionDispatcher.submit(boundaryLocation, () -> HopperTransactionUtils.synchronizeTransaction(transactionId, () -> {
            Queue.queueContainerBreak(user, capturedLocation, type, capturedContents);
        }));
    }

    public static void flushPendingContainer(Location location, ItemStack[] contents) {
        if (location == null || location.getWorld() == null || contents == null) {
            return;
        }

        Location canonical = getCanonicalContainerLocation(location);
        ContainerCapture.finishAt(canonical);
        HopperPullListener.flushPendingPull(canonical, ItemUtils.getContainerState(contents));
    }

    public static void flushPendingContainer(Inventory inventory, Location location) {
        if (inventory == null || location == null || location.getWorld() == null) {
            return;
        }

        Location inventoryLocation = inventory.getLocation();
        Location canonical = inventoryLocation == null ? location : inventoryLocation;
        ContainerCapture.finishAt(canonical);
        HopperPullListener.flushPendingPull(canonical, inventory);
    }

    public static void flushPendingTransactionsForShutdown() {
        ContainerCapture.finishForShutdown();
        HopperPullListener.flushPendingPullsForShutdown();
        ContainerTransactionDispatcher.drainForShutdown();
    }

    static boolean onInventoryInteract(String user, final Inventory inventory, ItemStack[] inventoryData, Material containerType, Location location, boolean aSync) {
        if (inventory != null && location != null) {
            World world = location.getWorld();

            if (Config.getConfig(world).ITEM_TRANSACTIONS) {
                Material type = Material.CHEST;
                Location playerLocation = null;

                if (aSync) {
                    playerLocation = location;
                    if (containerType != null) {
                        type = containerType;
                    }
                }
                else {
                    InventoryHolder inventoryHolder = PaperAdapter.ADAPTER.getHolder(inventory, false);
                    if (inventoryHolder == null) {
                        if (CoreProtect.getInstance().isAdvancedChestsEnabled()) {
                            AdvancedChest<?, ?> advancedChest = AdvancedChestsAPI.getInventoryManager().getAdvancedChest(inventory);
                            if (advancedChest != null) {
                                playerLocation = advancedChest.getLocation();
                            }
                            else {
                                return false;
                            }
                        }
                        else {
                            return false;
                        }
                    }
                    if (inventoryHolder instanceof BlockState) {
                        BlockState state = (BlockState) inventoryHolder;
                        type = state.getType();
                        if (BlockGroup.CONTAINERS.contains(type)) {
                            playerLocation = state.getLocation();
                        }
                    }
                    else if (inventoryHolder instanceof DoubleChest) {
                        DoubleChest state = (DoubleChest) inventoryHolder;
                        playerLocation = state.getLocation();
                    }
                }

                if (playerLocation != null) {
                    if (inventoryData == null) {
                        inventoryData = ItemUtils.getContainerState(inventory.getContents());
                    }
                    else if (!aSync) {
                        inventoryData = ItemUtils.getContainerState(inventoryData);
                    }

                    ItemStack[] capturedState = inventoryData;
                    Location capturedLocation = playerLocation.clone();
                    Material capturedType = type;
                    if (capturedType != Material.ENDER_CHEST) {
                        HopperPullListener.flushPendingPull(capturedLocation, capturedState);
                    }
                    return ContainerCapture.begin(user, capturedType, capturedLocation, inventory, capturedState, null);
                }
            }
        }

        return false;
    }

    private static void queueContainerTransaction(String user, Location location, Material type, ItemStack[] before, ItemStack[] after) {
        String transactionId = HopperTransactionUtils.getTransactionId(user, type, location);
        HopperTransactionUtils.synchronizeTransaction(transactionId, () -> {
            String viewerGroupId = HopperTransactionUtils.getViewerGroupId(user, type, location);
            String loggingId = HopperTransactionUtils.getLoggingId(user, type, location);
            List<ItemStack[]> baselines = ConfigHandler.oldContainer.computeIfAbsent(loggingId, key -> new ArrayList<>());
            boolean first = baselines.isEmpty();
            if (first) {
                Queue.removeForceContainer(loggingId);
                ConfigHandler.addOldContainerViewer(viewerGroupId, loggingId);
            }
            ItemStack[] previous = first ? null : baselines.get(baselines.size() - 1);
            ItemStack[] oldSnapshot = ItemUtils.getSharedContainerState(before, previous);
            ItemStack[] afterSnapshot = ItemUtils.getSharedContainerState(after, oldSnapshot);
            baselines.add(oldSnapshot);
            Queue.addForceContainer(loggingId, afterSnapshot);
            if (type != Material.ENDER_CHEST) {
                HopperTransactionUtils.registerSnapshot(transactionId, loggingId, first);
            }
            Queue.queueContainerTransaction(user, location, type, afterSnapshot, Queue.getChestId(loggingId));
        });
    }

    static void queueHopperInventoryChange(String user, Location location, ItemStack[] before, ItemStack[] after) {
        if (Config.getConfig(location.getWorld()).ITEM_TRANSACTIONS && !ItemUtils.compareContainers(before, after)) {
            queueContainerTransaction(user, location, Material.CHEST, before, after);
        }
    }

    static void onInventoryInteractAsync(Player player, Inventory inventory, boolean enderChest) {
        if (inventory == null) {
            return;
        }

        Entity entityContainer = getTrackedEntityContainer(PaperAdapter.ADAPTER.getHolder(inventory, false));
        if (entityContainer != null) {
            captureEntityContainerTransaction(player.getName(), entityContainer, inventory);
            return;
        }

        Location location = null;
        try {
            location = inventory.getLocation();
        }
        catch (Exception e) {
            return;
        }

        if (location == null && !CoreProtect.getInstance().isAdvancedChestsEnabled()) {
            return;
        }
        if (CoreProtect.getInstance().isAdvancedChestsEnabled()) {
            AdvancedChest<?, ?> chest = AdvancedChestsAPI.getInventoryManager().getAdvancedChest(inventory);
            if (chest != null) {
                location = chest.getLocation();
            }
        }

        if (location == null || location.getWorld() == null) {
            return;
        }

        if (!Config.getConfig(location.getWorld()).ITEM_TRANSACTIONS) {
            return;
        }

        String user = player.getName();
        if (user == null) {
            return;
        }

        Location inventoryLocation = location.clone();
        ItemStack[] containerState = ItemUtils.getContainerState(inventory.getContents());
        if (!enderChest) {
            HopperPullListener.flushPendingPull(inventoryLocation, containerState);
        }

        Material containerType = enderChest ? Material.ENDER_CHEST : Material.CHEST;
        ContainerCapture.begin(user, containerType, inventoryLocation, inventory, containerState, enderChest ? player : null);
    }

    public static void flushEntityContainer(Entity entity) {
        if (!(entity instanceof InventoryHolder)) {
            return;
        }
        synchronized (pendingEntityTransactions) {
            if (!pendingEntityTransactions.containsKey(entity.getUniqueId())) {
                return;
            }
        }

        try {
            flushEntityContainer(entity, ((InventoryHolder) entity).getInventory().getContents());
        }
        catch (Exception e) {
            ErrorReporter.report(e);
        }
    }

    public static void flushEntityContainer(Entity entity, ItemStack[] currentContents) {
        flushEntityContainer(entity, currentContents, null);
    }

    private static void flushEntityContainer(Entity entity, ItemStack[] currentContents, Object scheduleToken) {
        if (entity == null || currentContents == null) {
            return;
        }

        PendingEntityContainerTransaction pending;
        synchronized (pendingEntityTransactions) {
            pending = pendingEntityTransactions.get(entity.getUniqueId());
            if (pending == null || scheduleToken != null && pending.scheduleToken != scheduleToken) {
                return;
            }
            pendingEntityTransactions.remove(entity.getUniqueId());
        }

        try {
            pending.log(entity, currentContents);
        }
        catch (Exception e) {
            ErrorReporter.report(e);
        }
    }

    private static void captureEntityContainerTransaction(String user, Entity entity, Inventory inventory) {
        if (!Config.getConfig(entity.getWorld()).ITEM_TRANSACTIONS) {
            return;
        }

        ItemStack[] oldContents = inventory.getContents();
        PendingEntityContainerTransaction previous = null;
        Object scheduleToken = null;
        synchronized (pendingEntityTransactions) {
            PendingEntityContainerTransaction pending = pendingEntityTransactions.get(entity.getUniqueId());
            if (pending == null) {
                pending = new PendingEntityContainerTransaction(user, oldContents);
                pendingEntityTransactions.put(entity.getUniqueId(), pending);
                scheduleToken = pending.scheduleToken;
            }
            else if (!pending.user.equals(user)) {
                previous = pending;
                pendingEntityTransactions.put(entity.getUniqueId(), new PendingEntityContainerTransaction(user, oldContents, pending.scheduleToken));
            }
        }

        if (previous != null) {
            previous.log(entity, oldContents);
        }
        if (scheduleToken != null) {
            scheduleEntityContainerFlush(entity, scheduleToken);
        }
    }

    private static void scheduleEntityContainerFlush(Entity entity, Object scheduleToken) {
        Runnable flush = () -> {
            try {
                if (entity instanceof InventoryHolder) {
                    flushEntityContainer(entity, ((InventoryHolder) entity).getInventory().getContents(), scheduleToken);
                }
            }
            catch (Exception e) {
                discardEntityContainerSchedule(entity.getUniqueId(), scheduleToken);
                ErrorReporter.report(e);
            }
        };
        Runnable retired = () -> discardEntityContainerSchedule(entity.getUniqueId(), scheduleToken);
        try {
            if (!PaperAdapter.ADAPTER.executeEntityTask(CoreProtect.getInstance(), entity, flush, retired, 1L)) {
                if (ConfigHandler.isFolia) {
                    retired.run();
                }
                else {
                    Scheduler.scheduleSyncDelayedTask(CoreProtect.getInstance(), flush, entity, 1);
                }
            }
        }
        catch (Exception e) {
            retired.run();
            ErrorReporter.report(e);
        }
    }

    private static void discardEntityContainerSchedule(UUID uuid, Object scheduleToken) {
        synchronized (pendingEntityTransactions) {
            PendingEntityContainerTransaction pending = pendingEntityTransactions.get(uuid);
            if (pending != null && pending.scheduleToken == scheduleToken) {
                pendingEntityTransactions.remove(uuid);
            }
        }
    }

    private static boolean recordEntityContainerRemoval(Entity entity, ItemStack item, boolean logRemoval) {
        synchronized (pendingEntityTransactions) {
            PendingEntityContainerTransaction pending = pendingEntityTransactions.get(entity.getUniqueId());
            if (pending == null) {
                return false;
            }
            ItemStack removedItem = item.clone();
            pending.removedItems.add(removedItem);
            if (logRemoval) {
                pending.loggedRemovedItems.add(removedItem);
            }
            return true;
        }
    }

    private static void queueEntityContainerDelta(String user, Entity entity, ItemStack[] oldContents, ItemStack[] newContents) {
        if (!EntitySpawnTracking.isTracked(entity)) {
            return;
        }
        if (oldContents == null || newContents == null || ItemUtils.compareContainers(oldContents, newContents)) {
            return;
        }
        Location currentLocation = entity.getLocation();
        EntitySpawnTracking.checkpoint(entity, currentLocation);
        Queue.queueEntityContainerTransaction(user, entity.getUniqueId(), currentLocation, oldContents, newContents);
    }

    private static Entity getTrackedEntityContainer(InventoryHolder holder) {
        if (!(holder instanceof Entity)) {
            return null;
        }

        Entity entity = (Entity) holder;
        return EntitySpawnTracking.isPlacedEntity(entity) && EntitySpawnTracking.isTracked(entity) ? entity : null;
    }

    private static boolean isSupportedContainer(InventoryHolder holder) {
        return Validate.isContainer(holder) || getTrackedEntityContainer(holder) != null;
    }

    private static final class PendingEntityContainerTransaction {
        private final String user;
        private final ItemStack[] oldContents;
        private final Object scheduleToken;
        private final List<ItemStack> removedItems = new ArrayList<>();
        private final List<ItemStack> loggedRemovedItems = new ArrayList<>();

        private PendingEntityContainerTransaction(String user, ItemStack[] oldContents) {
            this(user, oldContents, new Object());
        }

        private PendingEntityContainerTransaction(String user, ItemStack[] oldContents, Object scheduleToken) {
            this.user = user;
            this.oldContents = ItemUtils.getContainerState(oldContents);
            this.scheduleToken = scheduleToken;
        }

        private void log(Entity entity, ItemStack[] contents) {
            ItemStack[] beforeRemovals = contents;
            if (!removedItems.isEmpty()) {
                beforeRemovals = new ItemStack[contents.length + removedItems.size()];
                System.arraycopy(contents, 0, beforeRemovals, 0, contents.length);
                for (int i = 0; i < removedItems.size(); i++) {
                    beforeRemovals[contents.length + i] = removedItems.get(i).clone();
                }
            }
            queueEntityContainerDelta(user, entity, oldContents, beforeRemovals);
            if (!loggedRemovedItems.isEmpty()) {
                queueEntityContainerDelta("#hopper", entity, loggedRemovedItems.toArray(new ItemStack[0]), new ItemStack[0]);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    protected void onInventoryClick(InventoryClickEvent event) {
        InventoryAction inventoryAction = event.getAction();

        if (inventoryAction == InventoryAction.NOTHING) {
            return;
        }

        Inventory inventory;
        if (inventoryAction == InventoryAction.MOVE_TO_OTHER_INVENTORY || inventoryAction == InventoryAction.COLLECT_TO_CURSOR || inventoryAction == InventoryAction.UNKNOWN) {
            inventory = event.getInventory();
        }
        else {
            try {
                try {
                    inventory = event.getView().getInventory(event.getRawSlot());
                }
                catch (IncompatibleClassChangeError e) {
                    inventory = event.getClickedInventory();
                }
            }
            catch (Exception e) {
                return;
            }
        }

        HumanEntity viewer = event.getWhoClicked();
        if (inventory == null || !(viewer instanceof Player)) {
            return;
        }

        Player player = (Player) viewer;
        InventoryHolder inventoryHolder = PaperAdapter.ADAPTER.getHolder(inventory, false);
        boolean enderChest = inventory.equals(player.getEnderChest());
        boolean advancedChest = isAdvancedChest(inventory);
        if (!isSupportedContainer(inventoryHolder) && !enderChest && !advancedChest) {
            return;
        }
        if (advancedChest && event.getSlot() > inventory.getSize() - 10) {
            return;
        }
        onInventoryInteractAsync(player, event.getInventory(), enderChest);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    protected void onInventoryDragEvent(InventoryDragEvent event) {
        Inventory inventory = event.getInventory();
        if (inventory == null) {
            return;
        }
        int topSize = inventory.getSize();
        if (event.getRawSlots().stream().noneMatch(slot -> slot >= 0 && slot < topSize)) {
            return;
        }
        HumanEntity viewer = event.getWhoClicked();
        if (!(viewer instanceof Player)) {
            return;
        }

        Player player = (Player) viewer;
        InventoryHolder inventoryHolder = PaperAdapter.ADAPTER.getHolder(inventory, false);
        boolean enderChest = inventory.equals(player.getEnderChest());
        if (!enderChest && inventoryHolder != null && inventoryHolder.equals(player)) {
            return;
        }

        if (isSupportedContainer(inventoryHolder) || enderChest || isAdvancedChest(inventory)) {
            onInventoryInteractAsync(player, inventory, enderChest);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    protected void onInventoryClose(InventoryCloseEvent event) {
        HumanEntity viewer = event.getPlayer();
        if (viewer instanceof Player) {
            ContainerCapture.finishEnderChest((Player) viewer, event.getInventory());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    protected void onPlayerQuit(PlayerQuitEvent event) {
        ContainerCapture.finishEnderChest(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    protected void onInventoryMoveItemEvent(InventoryMoveItemEvent event) {
        if (event.isCancelled()) {
            return;
        }

        Inventory sourceInventory = event.getSource();
        if (sourceInventory == null) {
            return;
        }

        InventoryHolder sourceHolder = PaperAdapter.ADAPTER.getHolder(sourceInventory, false);
        if (sourceHolder == null) {
            return;
        }

        InventoryHolder destinationHolder = PaperAdapter.ADAPTER.getHolder(event.getDestination(), false);
        if (destinationHolder == null) {
            return;
        }

        Location location = getInventoryLocation(sourceInventory, sourceHolder);
        if (location == null || location.getWorld() == null) {
            return;
        }

        boolean hopperTransactions = Config.getConfig(location.getWorld()).HOPPER_TRANSACTIONS;
        if (!hopperTransactions && !Config.getConfig(location.getWorld()).ITEM_TRANSACTIONS) {
            return;
        }

        Entity sourceEntity = getTrackedEntityContainer(sourceHolder);
        Entity destinationEntity = getTrackedEntityContainer(destinationHolder);
        if (sourceEntity != null || destinationEntity != null) {
            processEntityInventoryMove(location, sourceHolder, destinationHolder, sourceEntity, destinationEntity, event.getItem(), hopperTransactions);
            return;
        }

        if (!hopperTransactions || Validate.isHopper(sourceHolder) && Validate.isHopper(destinationHolder)) {
            recordUnloggedInventoryMove(location, sourceHolder, event.getDestination(), destinationHolder, event.getItem());
            return;
        }

        if (Validate.isHopper(destinationHolder) && Validate.isContainer(sourceHolder)) {
            HopperPullListener.processHopperPull(location, "#hopper", sourceHolder, destinationHolder, event.getItem());
        }
        else if (Validate.isHopper(sourceHolder) && Validate.isContainer(destinationHolder)) {
            HopperPushListener.processHopperPush(location, "#hopper", sourceHolder, destinationHolder, event.getItem());
        }
        else if (Validate.isDropper(sourceHolder) && Validate.isContainer(destinationHolder)) {
            HopperPullListener.processHopperPull(location, "#dropper", sourceHolder, destinationHolder, event.getItem());
            HopperPushListener.processHopperPush(location, "#dropper", sourceHolder, destinationHolder, event.getItem());
        }
    }

    private static void recordUnloggedInventoryMove(Location sourceLocation, InventoryHolder sourceHolder, Inventory destination,
            InventoryHolder destinationHolder, ItemStack item) {
        if (!Validate.isContainer(sourceHolder) || !Validate.isContainer(destinationHolder) || item == null || item.getAmount() <= 0
                || item.getType() == Material.AIR) {
            return;
        }
        Location destinationLocation = getInventoryLocation(destination, destinationHolder);
        if (destinationLocation == null || destinationLocation.getWorld() == null) {
            return;
        }
        String sourceId = HopperTransactionUtils.getTransactionId(sourceLocation);
        String destinationId = HopperTransactionUtils.getTransactionId(destinationLocation);
        if (!HopperTransactionUtils.hasTransaction(sourceId) && !HopperTransactionUtils.hasTransaction(destinationId)) {
            return;
        }
        ItemStack[] before = ItemUtils.getContainerState(destination.getContents());
        ItemStack[] after = addPickedItem(before, item, destination.getMaxStackSize());
        if (after != null) {
            ItemStack transferred = item.clone();
            transferred.setAmount(inventorySize(after) - inventorySize(before));
            HopperTransactionUtils.recordItemRemoved(sourceId, transferred);
            HopperTransactionUtils.recordItemAdded(destinationId, transferred);
        }
    }

    private static int inventorySize(ItemStack[] contents) {
        int amount = 0;
        for (ItemStack item : contents) {
            if (item != null && item.getType() != Material.AIR) {
                amount += Math.max(0, item.getAmount());
            }
        }
        return amount;
    }

    private static void processEntityInventoryMove(Location sourceLocation, InventoryHolder sourceHolder, InventoryHolder destinationHolder, Entity sourceEntity, Entity destinationEntity, ItemStack movedItem, boolean hopperTransactions) {
        if (movedItem == null || movedItem.getAmount() <= 0 || movedItem.getType() == Material.AIR) {
            return;
        }

        Inventory destinationInventory = destinationHolder.getInventory();
        Location destinationLocation = getInventoryLocation(destinationInventory, destinationHolder);
        if (destinationLocation == null || destinationLocation.getWorld() == null) {
            return;
        }

        ItemStack moved = movedItem.clone();
        ItemStack[] destinationBefore = ItemUtils.getContainerState(destinationInventory.getContents());
        ItemStack[] destinationAfter = addPickedItem(destinationBefore, moved, destinationInventory.getMaxStackSize());
        if (destinationAfter == null) {
            return;
        }

        moved.setAmount(inventorySize(destinationAfter) - inventorySize(destinationBefore));
        Config worldConfig = Config.getConfig(sourceLocation.getWorld());
        boolean logTransfer = hopperTransactions && worldConfig.ITEM_TRANSACTIONS && (!worldConfig.HOPPER_FILTER_META || moved.hasItemMeta());
        boolean sourceRemovalPending = sourceEntity != null && recordEntityContainerRemoval(sourceEntity, moved, logTransfer);
        if (destinationEntity != null && destinationEntity != sourceEntity) {
            flushEntityContainer(destinationEntity, destinationBefore);
        }

        String user = Validate.isDropper(sourceHolder) ? "#dropper" : "#hopper";
        if (sourceEntity == null && Validate.isContainer(sourceHolder)) {
            if (!hopperTransactions || Validate.isDropper(sourceHolder) || Validate.isHopper(destinationHolder) && !Validate.isHopper(sourceHolder)) {
                HopperPullListener.processHopperPull(sourceLocation, user, sourceHolder, destinationHolder, moved);
            }
            else {
                HopperTransactionUtils.recordItemRemoved(HopperTransactionUtils.getTransactionId(sourceLocation), moved);
            }
        }
        if (destinationEntity == null && Validate.isContainer(destinationHolder)) {
            if (hopperTransactions && (Validate.isHopper(sourceHolder) || Validate.isDropper(sourceHolder)) && !Validate.isHopper(destinationHolder)) {
                HopperPushListener.processHopperPush(sourceLocation, user, sourceHolder, destinationHolder, moved);
            }
            else {
                HopperTransactionUtils.recordItemAdded(HopperTransactionUtils.getTransactionId(destinationLocation), moved);
            }
        }
        if (!logTransfer) {
            return;
        }

        if (sourceEntity != null && !sourceRemovalPending) {
            queueEntityContainerDelta(user, sourceEntity, new ItemStack[] { moved }, new ItemStack[0]);
        }
        if (destinationEntity != null) {
            queueEntityContainerDelta(user, destinationEntity, destinationBefore, destinationAfter);
        }
    }

    private static Location getInventoryLocation(Inventory inventory, InventoryHolder holder) {
        Location location = inventory.getLocation();
        if (location == null && holder instanceof Entity) {
            location = ((Entity) holder).getLocation();
        }
        else if (location == null && holder instanceof BlockState) {
            location = ((BlockState) holder).getLocation();
        }
        else if (location == null && holder instanceof DoubleChest) {
            location = ((DoubleChest) holder).getLocation();
        }
        return location;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    protected void onInventoryPickupItem(InventoryPickupItemEvent event) {
        Inventory inventory = event.getInventory();
        if (inventory == null) {
            return;
        }

        InventoryHolder holder = PaperAdapter.ADAPTER.getHolder(inventory, false);
        Entity entityContainer = getTrackedEntityContainer(holder);
        if (!(holder instanceof Hopper) && (entityContainer == null || !Validate.isHopper(holder))) {
            return;
        }

        Location location = getInventoryLocation(inventory, holder);
        if (location == null || location.getWorld() == null) {
            return;
        }
        if (!Config.getConfig(location.getWorld()).ITEM_TRANSACTIONS) {
            return;
        }

        Item item = event.getItem();
        if (item == null) {
            return;
        }

        ItemStack[] oldContents = ItemUtils.getContainerState(inventory.getContents());
        ItemStack[] newContents = addPickedItem(oldContents, item.getItemStack(), inventory.getMaxStackSize());
        if (newContents == null) {
            return;
        }

        String user = PlayerDropItemListener.getDroppedItemUser(item.getUniqueId());
        if (user == null) {
            user = "#hopper";
        }

        if (entityContainer != null) {
            flushEntityContainer(entityContainer);
            queueEntityContainerDelta(user, entityContainer, oldContents, newContents);
        }
        else {
            Location inventoryLocation = location.clone();
            ContainerCapture.finishAt(inventoryLocation);
            publishCapturedContainer(user, inventoryLocation, Material.HOPPER, oldContents, newContents);
        }
    }

    private static Location getCanonicalContainerLocation(Location location) {
        try {
            BlockState state = location.getBlock().getState();
            if (state instanceof InventoryHolder) {
                Location inventoryLocation = ((InventoryHolder) state).getInventory().getLocation();
                if (inventoryLocation != null && inventoryLocation.getWorld() != null) {
                    return inventoryLocation.clone();
                }
            }
        }
        catch (Exception e) {
            ErrorReporter.report(e);
        }
        return location.clone();
    }

    private static ItemStack[] addPickedItem(ItemStack[] contents, ItemStack itemStack, int inventoryMaxStackSize) {
        if (contents == null || itemStack == null || itemStack.getAmount() <= 0 || itemStack.getType() == Material.AIR) {
            return null;
        }

        ItemStack[] result = contents.clone();
        int amount = itemStack.getAmount();
        long remaining = ItemUtils.insertIntoContainerSnapshot(result, itemStack, amount, inventoryMaxStackSize);
        return remaining == amount ? null : result;
    }

    private static boolean isAdvancedChest(Inventory inventory) {
        return CoreProtect.getInstance().isAdvancedChestsEnabled() && AdvancedChestsAPI.getInventoryManager().getAdvancedChest(inventory) != null;
    }

}
