package net.coreprotect.listener.player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.DoubleChest;
import org.bukkit.block.Hopper;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.BlockInventoryHolder;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import net.coreprotect.CoreProtect;
import net.coreprotect.bukkit.BukkitAdapter;
import net.coreprotect.config.Config;
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.consumer.Queue;
import net.coreprotect.model.BlockGroup;
import net.coreprotect.paper.PaperAdapter;
import net.coreprotect.thread.Scheduler;
import net.coreprotect.utility.HopperTransactionUtils;
import net.coreprotect.utility.ItemUtils;
import net.coreprotect.utility.Validate;
import net.coreprotect.utility.ErrorReporter;
import us.lynuxcraft.deadsilenceiv.advancedchests.AdvancedChestsAPI;
import us.lynuxcraft.deadsilenceiv.advancedchests.chest.AdvancedChest;

public final class InventoryChangeListener extends Queue implements Listener {

    protected static AtomicLong tasksStarted = new AtomicLong();
    protected static AtomicLong tasksCompleted = new AtomicLong();
    private static ConcurrentHashMap<String, Boolean> inventoryProcessing = new ConcurrentHashMap<>();
    private static final Object taskCompletionLock = new Object();
    private static final long TASK_WAIT_MAX_MS = 50; // Maximum wait time in milliseconds

    protected static void checkTasks(long taskStarted) {
        try {
            // Skip checking if this is the first task or we're already caught up
            if (taskStarted <= 1 || tasksCompleted.get() >= (taskStarted - 1L)) {
                tasksCompleted.set(taskStarted);
                return;
            }

            // Try to update without waiting if possible
            if (tasksCompleted.compareAndSet(taskStarted - 1L, taskStarted)) {
                return;
            }

            // Use proper synchronization instead of busy waiting
            synchronized (taskCompletionLock) {
                if (tasksCompleted.get() < (taskStarted - 1L)) {
                    taskCompletionLock.wait(TASK_WAIT_MAX_MS);
                }
                tasksCompleted.set(taskStarted);
                taskCompletionLock.notifyAll(); // Notify other waiting threads
            }
        }
        catch (Exception e) {
            ErrorReporter.report(e);
        }
    }

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
                    InventoryHolder inventoryHolder = inventory.getHolder();
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
                        inventoryData = inventory.getContents();
                    }

                    return queueContainerTransaction(user, playerLocation, type, inventory, inventoryData, null, null);
                }
            }
        }

        return false;
    }

    private static boolean queueContainerTransaction(String user, Location playerLocation, Material type, Object inventory, ItemStack[] inventoryData, ItemStack[] forceInventoryData, ItemStack batchItem) {
        String transactingChestId = HopperTransactionUtils.getTransactionId(playerLocation);
        String loggingChestIdSuffix = HopperTransactionUtils.getLoggingIdSuffix(playerLocation);
        String loggingChestId = HopperTransactionUtils.getLoggingId(user, loggingChestIdSuffix);
        Set<String> locationViewers = ConfigHandler.oldContainerViewers.get(loggingChestIdSuffix);
        if (locationViewers != null) {
            for (String loggingChestIdViewer : locationViewers) {
                if (loggingChestIdViewer.equals(loggingChestId)) {
                    continue;
                }

                List<ItemStack[]> viewerOldList = ConfigHandler.oldContainer.get(loggingChestIdViewer);
                if (viewerOldList != null) { // viewer has pending consumer item
                    int sizeOld = viewerOldList.size();
                    int forceSize = getForceContainerSize(loggingChestIdViewer);

                    if (forceSize < sizeOld) {
                        ItemStack[] containerState = ItemUtils.getContainerState(inventoryData);

                        long snapshotMark = HopperTransactionUtils.getSnapshotMark(transactingChestId, loggingChestIdViewer, forceSize);
                        containerState = HopperTransactionUtils.applyPendingChanges(containerState, transactingChestId, snapshotMark);

                        addForceContainer(loggingChestIdViewer, containerState);
                    }
                }
            }
        }

        if (forceInventoryData == null && batchItem != null && HopperTransactionUtils.shouldForceBatchBoundary(transactingChestId, loggingChestId, batchItem)) {
            forceInventoryData = inventoryData;
        }
        if (forceInventoryData != null) {
            addForceContainer(loggingChestId, ItemUtils.getContainerState(forceInventoryData));
        }

        int chestId = getChestId(loggingChestId);
        if (chestId > 0) {
            int forceSize = getForceContainerSize(loggingChestId);
            if (forceSize > 0) {
                List<ItemStack[]> list = ConfigHandler.oldContainer.get(loggingChestId);

                if (list != null && list.size() <= forceSize) {
                    list.add(ItemUtils.getContainerState(inventoryData));
                    ConfigHandler.oldContainer.put(loggingChestId, list);
                    HopperTransactionUtils.registerSnapshot(transactingChestId, loggingChestId, false);
                }
            }
        }
        else {
            List<ItemStack[]> list = new ArrayList<>();
            list.add(ItemUtils.getContainerState(inventoryData));
            ConfigHandler.oldContainer.put(loggingChestId, list);
            ConfigHandler.addOldContainerViewer(loggingChestIdSuffix, loggingChestId);
            HopperTransactionUtils.registerSnapshot(transactingChestId, loggingChestId, true);
        }

        Queue.queueContainerTransaction(user, playerLocation, type, inventory, chestId);
        return true;
    }

    static boolean onHopperInventoryInteract(String user, Inventory inventory, ItemStack[] inventoryData, Location location, ItemStack movedItem) {
        if (inventory == null || location == null) {
            return false;
        }
        if (!Config.getConfig(location.getWorld()).ITEM_TRANSACTIONS) {
            return false;
        }

        return queueContainerTransaction(user, location, Material.CHEST, inventory, inventoryData, null, movedItem);
    }

    static void onInventoryInteractAsync(Player player, Inventory inventory, boolean enderChest) {
        if (inventory == null) {
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

        if (location == null) {
            return;
        }

        if (!Config.getConfig(location.getWorld()).ITEM_TRANSACTIONS) {
            return;
        }

        Location inventoryLocation = location;
        ItemStack[] containerState = ItemUtils.getContainerState(inventory.getContents());

        String loggingChestId = player.getName() + "." + location.getBlockX() + "." + location.getBlockY() + "." + location.getBlockZ();
        Boolean lastTransaction = inventoryProcessing.get(loggingChestId);
        if (lastTransaction != null) {
            return;
        }
        inventoryProcessing.put(loggingChestId, true);

        final long taskStarted = InventoryChangeListener.tasksStarted.incrementAndGet();
        Scheduler.runTaskAsynchronously(CoreProtect.getInstance(), () -> {
            try {
                Material containerType = (enderChest != true ? null : Material.ENDER_CHEST);
                InventoryChangeListener.checkTasks(taskStarted);
                inventoryProcessing.remove(loggingChestId);
                onInventoryInteract(player.getName(), inventory, containerState, containerType, inventoryLocation, true);
            }
            catch (Exception e) {
                ErrorReporter.report(e);
            }
        });
    }

    /**
     * Checks for anvil operations to properly track enchanted item results
     * 
     * @param event
     *            The inventory click event
     * @return true if this was an anvil result operation that was handled, false otherwise
     */
    private boolean checkAnvilOperation(InventoryClickEvent event) {
        if (event.getInventory().getType() != InventoryType.ANVIL) {
            return false;
        }

        // Only process result slot clicks in anvils (slot 2)
        if (event.getRawSlot() != 2) {
            return false;
        }

        // Ensure we have a valid player and item
        Player player = (Player) event.getWhoClicked();
        ItemStack resultItem = event.getCurrentItem();
        if (resultItem == null || resultItem.getType() == Material.AIR) {
            return false;
        }

        // Get the input items (slots 0 and 1 in the anvil)
        ItemStack firstItem = event.getInventory().getItem(0);
        ItemStack secondItem = event.getInventory().getItem(1);

        if (firstItem == null || secondItem == null) {
            return false;
        }

        // Process the enchantment operation
        Location location = player.getLocation();
        String loggingItemId = player.getName().toLowerCase(Locale.ROOT) + "." + location.getBlockX() + "." + location.getBlockY() + "." + location.getBlockZ();
        int itemId = getItemId(loggingItemId);

        // Log the input items as removed
        List<ItemStack> removedItems = new ArrayList<>();
        removedItems.add(firstItem.clone());
        removedItems.add(secondItem.clone());
        ConfigHandler.itemsDestroy.put(loggingItemId, removedItems);

        // Log the output item as created
        List<ItemStack> createdItems = new ArrayList<>();
        createdItems.add(resultItem.clone());
        ConfigHandler.itemsCreate.put(loggingItemId, createdItems);

        int time = (int) (System.currentTimeMillis() / 1000L) + 1;
        Queue.queueItemTransaction(player.getName(), location.clone(), time, 0, itemId);

        return true;
    }

    private boolean checkCrafterSlotChange(InventoryClickEvent event) {
        // Check if the clicked inventory is a crafter
        if (!BukkitAdapter.ADAPTER.isCrafter(event.getInventory().getType())) {
            return false;
        }

        // Check that the Action is NOTHING
        if (event.getAction() != InventoryAction.NOTHING) {
            return false;
        }

        // Check if the clicked slot is one of the crafter slots
        if (event.getRawSlot() < 0 || event.getRawSlot() > 8) {
            return false;
        }

        // Check that the click type is not a middle click
        if (!(event.getClick() == ClickType.LEFT || event.getClick() == ClickType.RIGHT)) {
            return false;
        }

        // Gather other necessary information
        Player player = (Player) event.getWhoClicked();
        Inventory inventory = event.getInventory();

        Location location = null;
        try {
            location = inventory.getLocation();
        }
        catch (Exception e) {
            return false;
        }

        if (location == null) {
            return false;
        }

        Block block = location.getBlock();
        BlockState blockState = block.getState();

        Queue.queueBlockPlace(player.getName(), blockState, block.getType(), blockState, block.getType(), -1, 0, blockState.getBlockData().getAsString());
        return true;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    protected void onInventoryClick(InventoryClickEvent event) {
        InventoryAction inventoryAction = event.getAction();

        if (checkCrafterSlotChange(event)) {
            return;
        }

        if (inventoryAction == InventoryAction.NOTHING) {
            return;
        }

        // Check if this is an anvil operation first
        if (checkAnvilOperation(event)) {
            return;
        }

        boolean enderChest = false;
        boolean advancedChest;
        if (inventoryAction != InventoryAction.MOVE_TO_OTHER_INVENTORY && inventoryAction != InventoryAction.COLLECT_TO_CURSOR && inventoryAction != InventoryAction.UNKNOWN) {
            // Perform this check to prevent triggering onInventoryInteractAsync when a user is just clicking items in their own inventory
            Inventory inventory = null;
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
            if (inventory == null) {
                return;
            }

            InventoryHolder inventoryHolder = inventory.getHolder();
            enderChest = inventory.equals(event.getWhoClicked().getEnderChest());
            advancedChest = isAdvancedChest(inventory);
            if ((!(inventoryHolder instanceof BlockInventoryHolder || inventoryHolder instanceof DoubleChest)) && !enderChest && !advancedChest) {
                return;
            }
            if (advancedChest && event.getSlot() > inventory.getSize() - 10) {
                return;
            }
        }
        else {
            // Perform standard inventory holder check on primary inventory
            Inventory inventory = event.getInventory();
            if (inventory == null) {
                return;
            }

            InventoryHolder inventoryHolder = inventory.getHolder();
            enderChest = inventory.equals(event.getWhoClicked().getEnderChest());
            advancedChest = isAdvancedChest(inventory);
            if ((!(inventoryHolder instanceof BlockInventoryHolder || inventoryHolder instanceof DoubleChest)) && !enderChest && !advancedChest) {
                return;
            }
            if (advancedChest && event.getSlot() > inventory.getSize() - 10) {
                return;
            }
        }

        Player player = (Player) event.getWhoClicked();
        onInventoryInteractAsync(player, event.getInventory(), enderChest);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    protected void onInventoryDragEvent(InventoryDragEvent event) {
        boolean movedItem = false;
        boolean enderChest = false;

        Inventory inventory = event.getInventory();
        InventoryHolder inventoryHolder = inventory.getHolder();
        if (inventory == null || inventoryHolder != null && inventoryHolder.equals(event.getWhoClicked())) {
            return;
        }

        enderChest = inventory.equals(event.getWhoClicked().getEnderChest());
        if (((inventoryHolder instanceof BlockInventoryHolder || inventoryHolder instanceof DoubleChest)) || enderChest || isAdvancedChest(inventory)) {
            movedItem = true;
        }

        if (!movedItem) {
            return;
        }

        Player player = (Player) event.getWhoClicked();
        onInventoryInteractAsync(player, event.getInventory(), enderChest);
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

        Location location = sourceInventory.getLocation();
        if (location == null) {
            return;
        }

        boolean hopperTransactions = Config.getConfig(location.getWorld()).HOPPER_TRANSACTIONS;
        if (!hopperTransactions && !Config.getConfig(location.getWorld()).ITEM_TRANSACTIONS) {
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

        if (hopperTransactions) {
            if (Validate.isHopper(destinationHolder) && (Validate.isContainer(sourceHolder) && !Validate.isHopper(sourceHolder))) {
                HopperPullListener.processHopperPull(location, "#hopper", sourceHolder, destinationHolder, event.getItem());
            }
            else if (Validate.isHopper(sourceHolder) && (Validate.isContainer(destinationHolder) && !Validate.isHopper(destinationHolder))) {
                HopperPushListener.processHopperPush(location, "#hopper", sourceHolder, destinationHolder, event.getItem());
            }
            else if (Validate.isDropper(sourceHolder) && (Validate.isContainer(destinationHolder))) {
                HopperPullListener.processHopperPull(location, "#dropper", sourceHolder, destinationHolder, event.getItem());
                if (!Validate.isHopper(destinationHolder)) {
                    HopperPushListener.processHopperPush(location, "#dropper", sourceHolder, destinationHolder, event.getItem());
                }
            }

            return;
        }

        if (destinationHolder instanceof Player || (!(sourceHolder instanceof BlockInventoryHolder) && !(sourceHolder instanceof DoubleChest))) {
            return;
        }

        String transactingChestId = HopperTransactionUtils.getTransactionId(location);
        if (!HopperTransactionUtils.hasTransaction(transactingChestId)) {
            return;
        }

        HopperPullListener.processHopperPull(location, "#hopper", sourceHolder, destinationHolder, event.getItem());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    protected void onInventoryPickupItem(InventoryPickupItemEvent event) {
        Inventory inventory = event.getInventory();
        if (inventory == null) {
            return;
        }

        InventoryHolder holder = PaperAdapter.ADAPTER.getHolder(inventory, false);
        if (!(holder instanceof Hopper)) {
            return;
        }

        Location location = inventory.getLocation();
        if (location == null) {
            location = ((Hopper) holder).getLocation();
        }

        if (location == null || !Config.getConfig(location.getWorld()).ITEM_TRANSACTIONS) {
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

        queueContainerTransaction(user, location, Material.HOPPER, newContents, oldContents, newContents, null);
    }

    private static ItemStack[] addPickedItem(ItemStack[] contents, ItemStack itemStack, int inventoryMaxStackSize) {
        if (contents == null || itemStack == null || itemStack.getAmount() <= 0 || itemStack.getType() == Material.AIR) {
            return null;
        }

        ItemStack[] result = ItemUtils.getContainerState(contents);
        ItemStack pickedItem = itemStack.clone();
        int remaining = pickedItem.getAmount();

        for (ItemStack item : result) {
            if (remaining <= 0) {
                break;
            }

            if (item == null || item.getType() == Material.AIR || !item.isSimilar(pickedItem)) {
                continue;
            }

            int maxStackSize = getMaxStackSize(item, inventoryMaxStackSize);
            int accepted = Math.min(remaining, maxStackSize - item.getAmount());
            if (accepted > 0) {
                item.setAmount(item.getAmount() + accepted);
                remaining -= accepted;
            }
        }

        for (int i = 0; i < result.length && remaining > 0; i++) {
            ItemStack item = result[i];
            if (item != null && item.getType() != Material.AIR) {
                continue;
            }

            int accepted = Math.min(remaining, getMaxStackSize(pickedItem, inventoryMaxStackSize));
            ItemStack addedItem = pickedItem.clone();
            addedItem.setAmount(accepted);
            result[i] = addedItem;
            remaining -= accepted;
        }

        if (remaining == pickedItem.getAmount()) {
            return null;
        }

        return result;
    }

    private static int getMaxStackSize(ItemStack itemStack, int inventoryMaxStackSize) {
        int maxStackSize = itemStack.getMaxStackSize();
        if (inventoryMaxStackSize > 0 && (inventoryMaxStackSize < maxStackSize || maxStackSize == -1)) {
            maxStackSize = inventoryMaxStackSize;
        }

        if (maxStackSize == -1) {
            return 1;
        }

        return maxStackSize;
    }

    private boolean isAdvancedChest(Inventory inventory) {
        return CoreProtect.getInstance().isAdvancedChestsEnabled() && AdvancedChestsAPI.getInventoryManager().getAdvancedChest(inventory) != null;
    }

}
