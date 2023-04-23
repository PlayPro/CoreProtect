package net.coreprotect.listener.player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.block.DoubleChest;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.inventory.BlockInventoryHolder;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;

import net.coreprotect.CoreProtect;
import net.coreprotect.config.Config;
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.consumer.Queue;
import net.coreprotect.model.BlockGroup;
import net.coreprotect.paper.PaperAdapter;
import net.coreprotect.thread.Scheduler;
import net.coreprotect.utility.Util;
import net.coreprotect.utility.Validate;

public final class InventoryChangeListener extends Queue implements Listener {

    protected static AtomicLong tasksStarted = new AtomicLong();
    protected static AtomicLong tasksCompleted = new AtomicLong();

    protected static void checkTasks(long taskStarted) {
        try {
            int waitCount = 0;
            while (tasksCompleted.get() < (taskStarted - 1L) && waitCount++ <= 50) {
                Thread.sleep(1);
            }
            tasksCompleted.set(taskStarted);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static boolean inventoryTransaction(String user, Location location, ItemStack[] inventoryData) {
        if (user != null && location != null) {
            if (user.length() > 0) {
                BlockState blockState = location.getBlock().getState();
                Material type = blockState.getType();

                if (BlockGroup.CONTAINERS.contains(type) && blockState instanceof InventoryHolder) {
                    InventoryHolder inventoryHolder = (InventoryHolder) blockState;
                    return onInventoryInteract(user, inventoryHolder.getInventory(), inventoryData, null, location, false);
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
                        return false;
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

                    int x = playerLocation.getBlockX();
                    int y = playerLocation.getBlockY();
                    int z = playerLocation.getBlockZ();

                    String transactingChestId = playerLocation.getWorld().getUID().toString() + "." + x + "." + y + "." + z;
                    String loggingChestId = user.toLowerCase(Locale.ROOT) + "." + x + "." + y + "." + z;
                    for (String loggingChestIdViewer : ConfigHandler.oldContainer.keySet()) {
                        if (loggingChestIdViewer.equals(loggingChestId) || !loggingChestIdViewer.endsWith("." + x + "." + y + "." + z)) {
                            continue;
                        }

                        if (ConfigHandler.oldContainer.get(loggingChestIdViewer) != null) { // player has pending consumer item
                            int sizeOld = ConfigHandler.oldContainer.get(loggingChestIdViewer).size();
                            ConfigHandler.forceContainer.computeIfAbsent(loggingChestIdViewer, k -> new ArrayList<>());
                            List<ItemStack[]> list = ConfigHandler.forceContainer.get(loggingChestIdViewer);

                            if (list != null && list.size() < sizeOld) {
                                ItemStack[] containerState = Util.getContainerState(inventoryData);

                                // If items have been removed by a hopper, merge into containerState
                                List<Object> transactingChest = ConfigHandler.transactingChest.get(transactingChestId);
                                if (transactingChest != null) {
                                    List<Object> transactingChestList = Collections.synchronizedList(new ArrayList<>(transactingChest));
                                    if (!transactingChestList.isEmpty()) {
                                        ItemStack[] newState = new ItemStack[containerState.length + transactingChestList.size()];
                                        int count = 0;

                                        for (int j = 0; j < containerState.length; j++) {
                                            newState[j] = containerState[j];
                                            count++;
                                        }

                                        for (Object item : transactingChestList) {
                                            ItemStack addItem = null;
                                            ItemStack removeItem = null;
                                            if (item instanceof ItemStack) {
                                                addItem = (ItemStack) item;
                                            }
                                            else {
                                                addItem = ((ItemStack[]) item)[0];
                                                removeItem = ((ItemStack[]) item)[1];
                                            }

                                            // item was removed by hopper, add back to state
                                            if (addItem != null) {
                                                newState[count] = addItem;
                                                count++;
                                            }

                                            // item was added by hopper, remove from state
                                            if (removeItem != null) {
                                                for (ItemStack check : newState) {
                                                    if (check != null && check.isSimilar(removeItem)) {
                                                        check.setAmount(check.getAmount() - 1);
                                                        break;
                                                    }
                                                }
                                            }
                                        }
                                        containerState = newState;
                                    }
                                }

                                modifyForceContainer(loggingChestIdViewer, containerState);
                            }
                        }
                    }

                    int chestId = getChestId(loggingChestId);
                    if (chestId > 0) {
                        List<ItemStack[]> forceList = ConfigHandler.forceContainer.get(loggingChestId);
                        if (forceList != null) {
                            int forceSize = forceList.size();
                            List<ItemStack[]> list = ConfigHandler.oldContainer.get(loggingChestId);

                            if (list != null && list.size() <= forceSize) {
                                list.add(Util.getContainerState(inventoryData));
                                ConfigHandler.oldContainer.put(loggingChestId, list);
                            }
                        }
                    }
                    else {
                        List<ItemStack[]> list = new ArrayList<>();
                        list.add(Util.getContainerState(inventoryData));
                        ConfigHandler.oldContainer.put(loggingChestId, list);
                    }

                    ConfigHandler.transactingChest.computeIfAbsent(transactingChestId, k -> Collections.synchronizedList(new ArrayList<>()));
                    Queue.queueContainerTransaction(user, playerLocation, type, inventory, chestId);
                    return true;
                }
            }
        }

        return false;
    }

    private static void onInventoryInteractAsync(Player player, Inventory inventory, boolean enderChest) {
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
        if (location == null) {
            return;
        }

        if (!Config.getConfig(location.getWorld()).ITEM_TRANSACTIONS) {
            return;
        }

        Location inventoryLocation = location;
        ItemStack[] containerState = Util.getContainerState(inventory.getContents());

        final long taskStarted = InventoryChangeListener.tasksStarted.incrementAndGet();
        Scheduler.runTaskAsynchronously(CoreProtect.getInstance(), () -> {
            try {
                Material containerType = (enderChest != true ? null : Material.ENDER_CHEST);
                InventoryChangeListener.checkTasks(taskStarted);
                onInventoryInteract(player.getName(), inventory, containerState, containerType, inventoryLocation, true);
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    @EventHandler(priority = EventPriority.LOWEST)
    protected void onInventoryClick(InventoryClickEvent event) {
        InventoryAction inventoryAction = event.getAction();
        if (inventoryAction == InventoryAction.NOTHING) {
            return;
        }

        boolean enderChest = false;
        if (inventoryAction != InventoryAction.MOVE_TO_OTHER_INVENTORY && inventoryAction != InventoryAction.COLLECT_TO_CURSOR && inventoryAction != InventoryAction.UNKNOWN) {
            // Perform this check to prevent triggering onInventoryInteractAsync when a user is just clicking items in their own inventory
            Inventory inventory = null;
            try {
                inventory = event.getView().getInventory(event.getRawSlot());
            }
            catch (Exception e) {
                return;
            }
            if (inventory == null) {
                return;
            }

            InventoryHolder inventoryHolder = inventory.getHolder();
            enderChest = inventory.equals(event.getWhoClicked().getEnderChest());
            if ((inventoryHolder == null || !(inventoryHolder instanceof BlockInventoryHolder || inventoryHolder instanceof DoubleChest)) && !enderChest) {
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
            if ((inventoryHolder == null || !(inventoryHolder instanceof BlockInventoryHolder || inventoryHolder instanceof DoubleChest)) && !enderChest) {
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
        InventoryView inventoryView = event.getView();
        for (Integer slot : event.getRawSlots()) {
            Inventory inventory = inventoryView.getInventory(slot);
            if (inventory == null) {
                continue;
            }

            InventoryHolder inventoryHolder = inventory.getHolder();
            enderChest = inventory.equals(event.getWhoClicked().getEnderChest());
            if ((inventoryHolder != null && (inventoryHolder instanceof BlockInventoryHolder || inventoryHolder instanceof DoubleChest)) || enderChest) {
                movedItem = true;
                break;
            }
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

        Location location = event.getSource().getLocation();
        if (location == null) {
            return;
        }

        boolean hopperTransactions = Config.getConfig(location.getWorld()).HOPPER_TRANSACTIONS;
        if (!hopperTransactions && !Config.getConfig(location.getWorld()).ITEM_TRANSACTIONS) {
            return;
        }

        InventoryHolder sourceHolder = PaperAdapter.ADAPTER.getHolder(event.getSource(), false);
        if (sourceHolder == null) {
            return;
        }

        InventoryHolder destinationHolder = PaperAdapter.ADAPTER.getHolder(event.getDestination(), false);
        if (destinationHolder == null) {
            return;
        }

        if (hopperTransactions) {
            if (Validate.isHopper(destinationHolder) && (Validate.isContainer(sourceHolder) && !Validate.isHopper(sourceHolder))) {
                HopperPullListener.processHopperPull(location, sourceHolder, destinationHolder, event.getItem());
            }
            else if (Validate.isHopper(sourceHolder) && (Validate.isContainer(destinationHolder) && !Validate.isHopper(destinationHolder))) {
                HopperPushListener.processHopperPush(location, sourceHolder, destinationHolder, event.getItem());
            }

            return;
        }

        if (destinationHolder instanceof Player || (!(sourceHolder instanceof BlockInventoryHolder) && !(sourceHolder instanceof DoubleChest))) {
            return;
        }

        List<Object> list = ConfigHandler.transactingChest.get(location.getWorld().getUID().toString() + "." + location.getBlockX() + "." + location.getBlockY() + "." + location.getBlockZ());
        if (list == null) {
            return;
        }

        HopperPullListener.processHopperPull(location, sourceHolder, destinationHolder, event.getItem());
    }
}
