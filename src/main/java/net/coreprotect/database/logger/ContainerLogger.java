package net.coreprotect.database.logger;

import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import net.coreprotect.CoreProtect;
import net.coreprotect.config.Config;
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.consumer.Queue;
import net.coreprotect.database.statement.ContainerStatement;
import net.coreprotect.database.statement.UserStatement;
import net.coreprotect.event.CoreProtectPreLogEvent;
import net.coreprotect.thread.CacheHandler;
import net.coreprotect.utility.BlockUtils;
import net.coreprotect.utility.ItemUtils;
import net.coreprotect.utility.MaterialUtils;
import net.coreprotect.utility.WorldUtils;
import net.coreprotect.utility.serialize.ItemMetaHandler;

public class ContainerLogger extends Queue {

    private static final int CONTAINER_DUPLICATE_THRESHOLD_DROPPER = 512;
    private static final int CONTAINER_DUPLICATE_THRESHOLD_DISPENSER = 256;
    private static final int CONTAINER_DUPLICATE_WINDOW_SECONDS = 1200;

    private ContainerLogger() {
        throw new IllegalStateException("Database class");
    }

    public static void log(PreparedStatement preparedStmtContainer, PreparedStatement preparedStmtItems, int batchCount, String player, Material type, Object container, Location location) {
        try {
            ItemStack[] contents = null;
            String faceData = null;

            if (type == Material.ITEM_FRAME) {
                contents = (ItemStack[]) ((Object[]) container)[1];
                faceData = ((BlockFace) ((Object[]) container)[2]).name();
            }
            else if (type == Material.JUKEBOX || type == Material.ARMOR_STAND) {
                contents = (ItemStack[]) ((Object[]) container)[1];
            }
            else if (container instanceof ItemStack[]) {
                contents = (ItemStack[]) container;
            }
            else {
                Inventory inventory = (Inventory) container;
                if (inventory != null) {
                    contents = inventory.getContents();
                }
            }

            if (contents == null) {
                return;
            }

            String loggingContainerId = player.toLowerCase(Locale.ROOT) + "." + location.getBlockX() + "." + location.getBlockY() + "." + location.getBlockZ();
            List<ItemStack[]> oldList = ConfigHandler.oldContainer.get(loggingContainerId);
            ItemStack[] oi1 = oldList.get(0);
            ItemStack[] oldInventory = ItemUtils.getContainerState(oi1);
            ItemStack[] newInventory = ItemUtils.getContainerState(contents);
            if (oldInventory == null || newInventory == null) {
                return;
            }
            boolean duplicateSuppression = Config.getConfig(location.getWorld()).DUPLICATE_SUPPRESSION;

            // Check if this is a dispenser with no actual changes
            if (duplicateSuppression && "#dispenser".equals(player) && ItemUtils.compareContainers(oldInventory, newInventory)) {
                // No changes detected, mark this dispenser in the dispenserNoChange map
                // Extract the location key from the loggingContainerId
                // Format: #dispenser.x.y.z
                String[] parts = loggingContainerId.split("\\.");
                if (parts.length >= 4) {
                    int x = Integer.parseInt(parts[1]);
                    int y = Integer.parseInt(parts[2]);
                    int z = Integer.parseInt(parts[3]);

                    // Create the location key
                    String locationKey = location.getWorld().getUID().toString() + "." + x + "." + y + "." + z;

                    // Check if we have pending event details for this dispenser
                    Object[] pendingEvent = ConfigHandler.dispenserPending.remove(locationKey);
                    if (pendingEvent != null) {
                        // We have the exact event details, use them to mark this event as unchanged
                        String eventKey = (String) pendingEvent[0];

                        // Get or create the inner map for this location
                        ConfigHandler.dispenserNoChange.computeIfAbsent(locationKey, k -> new ConcurrentHashMap<>()).put(eventKey, System.currentTimeMillis());
                    }
                }
                return;
            }

            // If we reach here, the dispenser event resulted in changes
            // Remove any pending event for this dispenser
            if (duplicateSuppression && "#dispenser".equals(player)) {
                String[] parts = loggingContainerId.split("\\.");
                if (parts.length >= 4) {
                    int x = Integer.parseInt(parts[1]);
                    int y = Integer.parseInt(parts[2]);
                    int z = Integer.parseInt(parts[3]);

                    String locationKey = location.getWorld().getUID().toString() + "." + x + "." + y + "." + z;

                    // Remove the pending event since it resulted in changes
                    ConfigHandler.dispenserPending.remove(locationKey);

                    // Clear any existing dispenserNoChange entries for this location
                    ConfigHandler.dispenserNoChange.remove(locationKey);
                }
            }

            List<ItemStack[]> forceList = ConfigHandler.forceContainer.get(loggingContainerId);
            if (forceList != null) {
                int forceSize = 0;
                if (!forceList.isEmpty()) {
                    newInventory = ItemUtils.getContainerState(forceList.get(0));
                    forceSize = modifyForceContainer(loggingContainerId, null);
                }
                if (forceSize == 0) {
                    ConfigHandler.forceContainer.remove(loggingContainerId);
                }
            }
            else {
                String transactingChestId = location.getWorld().getUID().toString() + "." + location.getBlockX() + "." + location.getBlockY() + "." + location.getBlockZ();
                if (ConfigHandler.transactingChest.get(transactingChestId) != null) {
                    List<Object> list = Collections.synchronizedList(new ArrayList<>(ConfigHandler.transactingChest.get(transactingChestId)));
                    if (list.size() > 0) {
                        ItemStack[] newMerge = new ItemStack[newInventory.length + list.size()];
                        int count = 0;
                        for (int i = 0; i < newInventory.length; i++) {
                            newMerge[i] = newInventory[i];
                            count++;
                        }
                        for (Object item : list) {
                            ItemStack addItem = null;
                            ItemStack removeItem = null;
                            if (item instanceof ItemStack) {
                                addItem = (ItemStack) item;
                            }
                            else if (item != null) {
                                addItem = ((ItemStack[]) item)[0];
                                removeItem = ((ItemStack[]) item)[1];
                            }

                            // item was removed by hopper, add back to state
                            if (addItem != null) {
                                newMerge[count] = addItem;
                                count++;
                            }

                            // item was added by hopper, remove from state
                            if (removeItem != null) {
                                for (ItemStack check : newMerge) {
                                    if (check != null && check.isSimilar(removeItem)) {
                                        check.setAmount(check.getAmount() - 1);
                                        break;
                                    }
                                }
                            }
                        }
                        newInventory = newMerge;
                    }
                }
            }

            if (duplicateSuppression && shouldSuppressContainerDuplicate(player, location, oldInventory, newInventory)) {
                oldList.remove(0);
                ConfigHandler.oldContainer.put(loggingContainerId, oldList);
                if ("#hopper".equals(player)) {
                    String hopperPush = "#hopper-push." + location.getBlockX() + "." + location.getBlockY() + "." + location.getBlockZ();
                    ConfigHandler.hopperSuccess.remove(hopperPush);
                    ConfigHandler.hopperAbort.remove(hopperPush);
                }
                return;
            }

            for (ItemStack oldi : oldInventory) {
                for (ItemStack newi : newInventory) {
                    if (oldi != null && newi != null) {
                        if (oldi.isSimilar(newi) && !BlockUtils.isAir(oldi.getType())) { // Ignores amount
                            int oldAmount = oldi.getAmount();
                            int newAmount = newi.getAmount();
                            if (newAmount >= oldAmount) {
                                newAmount = newAmount - oldAmount;
                                oldi.setAmount(0);
                                newi.setAmount(newAmount);
                            }
                            else {
                                oldAmount = oldAmount - newAmount;
                                oldi.setAmount(oldAmount);
                                newi.setAmount(0);
                            }
                        }
                    }
                }
            }

            ItemUtils.mergeItems(type, oldInventory);
            ItemUtils.mergeItems(type, newInventory);

            if (type != Material.ENDER_CHEST) {
                logTransaction(preparedStmtContainer, batchCount, player, type, faceData, oldInventory, 0, location);
                logTransaction(preparedStmtContainer, batchCount, player, type, faceData, newInventory, 1, location);
            }
            else { // pass ender chest transactions to item logger
                ItemLogger.logTransaction(preparedStmtItems, batchCount, 0, player, location, oldInventory, ItemLogger.ITEM_REMOVE_ENDER);
                ItemLogger.logTransaction(preparedStmtItems, batchCount, 0, player, location, newInventory, ItemLogger.ITEM_ADD_ENDER);
            }

            oldList.remove(0);
            ConfigHandler.oldContainer.put(loggingContainerId, oldList);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    protected static void logTransaction(PreparedStatement preparedStmt, int batchCount, String user, Material type, String faceData, ItemStack[] items, int action, Location location) {
        try {
            if (ConfigHandler.isBlacklisted(user)) {
                return;
            }
            boolean success = false;
            int slot = 0;
            for (ItemStack item : items) {
                if (item != null) {
                    if (item.getAmount() > 0 && !BlockUtils.isAir(item.getType())) {
                        // Object[] metadata = new Object[] { slot, item.getItemMeta() };
                        if (ConfigHandler.isFilterBlacklisted(user, item.getType().getKey().toString())){
                            continue;
                        }

                        List<List<Map<String, Object>>> metadata = ItemMetaHandler.serialize(item, type, faceData, slot);
                        if (metadata.size() == 0) {
                            metadata = null;
                        }

                        CoreProtectPreLogEvent event = new CoreProtectPreLogEvent(user, location, CoreProtectPreLogEvent.Action.CONTAINER_TRANSACTION, action, item.getType(), null, null);
                        if (Config.getGlobal().API_ENABLED && !Bukkit.isPrimaryThread()) {
                            CoreProtect.getInstance().getServer().getPluginManager().callEvent(event);
                        }

                        if (event.isCancelled()) {
                            return;
                        }  


                        int userId = UserStatement.getId(preparedStmt, event.getUser(), true);
                        Location eventLocation = event.getLocation();
                        int wid = WorldUtils.getWorldId(eventLocation.getWorld().getName());
                        int time = (int) (System.currentTimeMillis() / 1000L);
                        int x = eventLocation.getBlockX();
                        int y = eventLocation.getBlockY();
                        int z = eventLocation.getBlockZ();
                        int typeId = MaterialUtils.getBlockId(item.getType().name(), true);
                        int data = 0;
                        int amount = item.getAmount();
                        ContainerStatement.insert(preparedStmt, batchCount, time, userId, wid, x, y, z, typeId, data, amount, metadata, action, 0);
                        success = true;
                    }
                }
                slot++;
            }

            if (success && user.equals("#hopper")) {
                String hopperPush = "#hopper-push." + location.getBlockX() + "." + location.getBlockY() + "." + location.getBlockZ();
                ConfigHandler.hopperSuccess.remove(hopperPush);
                ConfigHandler.hopperAbort.remove(hopperPush);
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static boolean shouldSuppressContainerDuplicate(String user, Location location, ItemStack[] oldInventory, ItemStack[] newInventory) {
        if (!"#dropper".equals(user) && !"#dispenser".equals(user)) {
            return false;
        }

        String oldSignature = getContainerStateSignature(oldInventory);
        String newSignature = getContainerStateSignature(newInventory);
        String transitionSignature = oldSignature + ">" + newSignature;
        if ("#dispenser".equals(user)) {
            if (oldSignature.compareTo(newSignature) <= 0) {
                transitionSignature = oldSignature + "<>" + newSignature;
            }
            else {
                transitionSignature = newSignature + "<>" + oldSignature;
            }
        }

        int threshold = getContainerDuplicateThreshold(user);
        String signature = location.getWorld().getUID().toString() + "." + location.getBlockX() + "." + location.getBlockY() + "." + location.getBlockZ() + "." + user + "." + transitionSignature;
        return CacheHandler.shouldSuppressRepeat(CacheHandler.containerDuplicateCache, signature, threshold, CONTAINER_DUPLICATE_WINDOW_SECONDS);
    }

    private static int getContainerDuplicateThreshold(String user) {
        if ("#dispenser".equals(user)) {
            return CONTAINER_DUPLICATE_THRESHOLD_DISPENSER;
        }
        return CONTAINER_DUPLICATE_THRESHOLD_DROPPER;
    }

    private static String getContainerStateSignature(ItemStack[] inventory) {
        if (inventory == null || inventory.length == 0) {
            return "-";
        }

        Map<String, Integer> itemCounts = new HashMap<>();
        for (ItemStack itemStack : inventory) {
            if (itemStack == null || itemStack.getAmount() <= 0 || BlockUtils.isAir(itemStack.getType())) {
                continue;
            }

            ItemStack normalized = itemStack.clone();
            normalized.setAmount(1);
            String key = normalized.toString();
            itemCounts.merge(key, itemStack.getAmount(), Integer::sum);
        }

        if (itemCounts.isEmpty()) {
            return "-";
        }

        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, Integer> entry : new TreeMap<>(itemCounts).entrySet()) {
            if (builder.length() > 0) {
                builder.append('|');
            }
            builder.append(entry.getKey()).append('*').append(entry.getValue());
        }

        return Integer.toHexString(builder.toString().hashCode());
    }

}
