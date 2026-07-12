package net.coreprotect.consumer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.Bisected.Half;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.block.data.type.Bed;
import org.bukkit.block.data.type.Bed.Part;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import net.coreprotect.CoreProtect;
import net.coreprotect.bukkit.BukkitAdapter;
import net.coreprotect.config.Config;
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.consumer.process.Process;
import net.coreprotect.listener.block.BlockUtil;
import net.coreprotect.model.BlockGroup;
import net.coreprotect.model.entity.EntityContainerRollbackUpdate;
import net.coreprotect.model.entity.EntityContainerTransaction;
import net.coreprotect.model.entity.EntitySpawnData;
import net.coreprotect.thread.Scheduler;
import net.coreprotect.thread.CacheHandler;
import net.coreprotect.utility.BlockUtils;
import net.coreprotect.utility.EntityUtils;
import net.coreprotect.utility.WorldUtils;
import net.coreprotect.utility.ErrorReporter;

public class Queue {

    public static synchronized void addForceContainer(String id, ItemStack[] container) {
        if (container == null) {
            return;
        }

        ConfigHandler.forceContainer.computeIfAbsent(id, k -> new ArrayList<>()).add(container);
    }

    public static synchronized void setForceContainer(String id, ItemStack[] container) {
        if (container == null) {
            return;
        }

        List<ItemStack[]> forceList = new ArrayList<>();
        forceList.add(container);
        ConfigHandler.forceContainer.put(id, forceList);
    }

    public static synchronized ItemStack[] pollForceContainer(String id) {
        List<ItemStack[]> forceList = ConfigHandler.forceContainer.get(id);
        if (forceList == null) {
            return null;
        }

        ItemStack[] container = forceList.isEmpty() ? null : forceList.remove(0);
        if (forceList.isEmpty()) {
            ConfigHandler.forceContainer.remove(id);
        }

        return container;
    }

    public static synchronized ItemStack[] peekForceContainer(String id) {
        List<ItemStack[]> forceList = ConfigHandler.forceContainer.get(id);
        return (forceList == null || forceList.isEmpty()) ? null : forceList.get(0);
    }

    public static synchronized int getForceContainerSize(String id) {
        List<ItemStack[]> forceList = ConfigHandler.forceContainer.get(id);
        return forceList == null ? 0 : forceList.size();
    }

    public static synchronized void removeForceContainer(String id) {
        ConfigHandler.forceContainer.remove(id);
    }

    protected static synchronized int getChestId(String id) {
        int chestId = ConfigHandler.loggingChest.getOrDefault(id, -1) + 1;
        ConfigHandler.loggingChest.put(id, chestId);
        return chestId;
    }

    protected static synchronized int getItemId(String id) {
        int chestId = ConfigHandler.loggingItem.getOrDefault(id, -1) + 1;
        ConfigHandler.loggingItem.put(id, chestId);
        return chestId;
    }

    private static void queueStandardData(Object[] data, String[] user, Object object, boolean first, long reservation) {
        queueStandardData(data, user, object, first, null, null, reservation);
    }

    private static synchronized <T> void queueStandardData(Object[] data, String[] user, Object object, boolean first, Map<Integer, ? extends Map<Integer, T>> additionalMaps, T additionalData, long reservation) {
        int currentConsumer = (int) (reservation >>> 32);
        int consumerId = (int) reservation;
        boolean rollbackPublication = Process.isRollbackPublication((int) data[1], object);
        boolean published = false;
        if (rollbackPublication) {
            Consumer.registerRollbackPublications(1);
        }
        try {
            data[0] = consumerId;
            if (additionalMaps != null) {
                additionalMaps.get(currentConsumer).put(consumerId, additionalData);
            }
            Consumer.consumerUsers.get(currentConsumer).put(consumerId, user);
            Consumer.consumerObjects.get(currentConsumer).put(consumerId, object);
            if (first) {
                Consumer.consumer.get(currentConsumer).add(0, data);
            }
            else {
                Consumer.consumer.get(currentConsumer).add(data);
            }
            published = true;
        }
        finally {
            try {
                if (!published) {
                    Consumer.consumer.get(currentConsumer).remove(data);
                    Consumer.consumerUsers.get(currentConsumer).remove(consumerId);
                    Consumer.consumerObjects.get(currentConsumer).remove(consumerId);
                    if (additionalMaps != null) {
                        additionalMaps.get(currentConsumer).remove(consumerId);
                    }
                    if (rollbackPublication) {
                        Consumer.completeRollbackPublications(1);
                    }
                }
            }
            finally {
                Consumer.completeReservation(reservation, 1);
            }
        }
    }

    private static Location getBlockLocation(Location location) {
        return new Location(location.getWorld(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    protected static void queueAdvancedBreak(String user, BlockState block, Material type, String blockData, int data, Material breakType, int blockNumber) {
        queueStandardData(new Object[] { null, Process.BLOCK_BREAK, type, data, breakType, 0, blockNumber, blockData }, new String[] { user, null }, block, false, Consumer.reserveConsumer());
    }

    protected static void queueArtInsert(int id, String name) {
        queueStandardData(new Object[] { null, Process.ART_INSERT, null, 0, null, 0, id, null }, new String[] { null, null }, name, false, Consumer.reserveConsumer());
    }

    protected static void queueBlockBreak(String user, BlockState block, Material type, String blockData, int extraData) {
        queueBlockBreak(user, block, type, blockData, null, extraData, 0);
    }

    protected static void queueBlockBreakValidate(final String user, final Block block, final BlockState blockState, final Material type, final String blockData, final int extraData, int ticks) {
        Scheduler.scheduleSyncDelayedTask(CoreProtect.getInstance(), () -> {
            try {
                if (!Objects.equals(block.getType(), type)) {
                    queueBlockBreak(user, blockState, type, blockData, null, extraData, 0);
                }
            }
            catch (Exception e) {
                ErrorReporter.report(e);
            }
        }, block.getLocation(), ticks);
    }

    protected static void queueBlockBreak(String user, BlockState block, Material type, String blockData, Material breakType, int extraData, int blockNumber) {
        if (type == Material.SPAWNER && block instanceof CreatureSpawner) { // Mob spawner
            CreatureSpawner mobSpawner = (CreatureSpawner) block;
            extraData = EntityUtils.getSpawnerType(mobSpawner.getSpawnedType());
        }
        else if (type != null && (type == Material.IRON_DOOR || BlockGroup.DOORS.contains(type) || type.equals(Material.SUNFLOWER) || type.equals(Material.LILAC) || type.equals(Material.TALL_GRASS) || type.equals(Material.LARGE_FERN) || type.equals(Material.ROSE_BUSH) || type.equals(Material.PEONY))) { // Double plant
            if (block.getBlockData() instanceof Bisected) {
                if (((Bisected) block.getBlockData()).getHalf().equals(Half.TOP)) {
                    if (blockNumber == 5) {
                        return;
                    }

                    if (block.getY() > BukkitAdapter.ADAPTER.getMinHeight(block.getWorld())) {
                        block = block.getWorld().getBlockAt(block.getX(), block.getY() - 1, block.getZ()).getState();
                        if (type != block.getType()) {
                            return;
                        }

                        blockData = block.getBlockData().getAsString();
                    }
                }
            }
        }
        else if (type != null && type.name().endsWith("_BED") && block.getBlockData() instanceof Bed) {
            if (((Bed) block.getBlockData()).getPart().equals(Part.HEAD)) {
                return;
            }
        }

        queueStandardData(new Object[] { null, Process.BLOCK_BREAK, type, extraData, breakType, 0, blockNumber, blockData }, new String[] { user, null }, block, false, Consumer.reserveConsumer());
    }

    protected static void queueBlockPlace(String user, BlockState blockLocation, Material blockType, BlockState blockReplaced, Material forceType, int forceD, int forceData, String blockData) {
        // If force_data equals "1", current block data will be used in consumer.
        Material type = blockType;
        int data = 0;
        Material replaceType = null;
        int replaceData = 0;

        if (type == Material.SPAWNER && blockLocation instanceof CreatureSpawner) { // Mob spawner
            CreatureSpawner mobSpawner = (CreatureSpawner) blockLocation;
            data = EntityUtils.getSpawnerType(mobSpawner.getSpawnedType());
            forceData = 1;
        }

        if (blockReplaced != null) {
            replaceType = blockReplaced.getType();
            replaceData = 0;

            if (replaceType != null && (replaceType == Material.IRON_DOOR || BlockGroup.DOORS.contains(replaceType) || replaceType.equals(Material.SUNFLOWER) || replaceType.equals(Material.LILAC) || replaceType.equals(Material.TALL_GRASS) || replaceType.equals(Material.LARGE_FERN) || replaceType.equals(Material.ROSE_BUSH) || replaceType.equals(Material.PEONY)) && replaceData >= 8) { // Double plant top half
                BlockState blockBelow = blockReplaced.getWorld().getBlockAt(blockReplaced.getX(), blockReplaced.getY() - 1, blockReplaced.getZ()).getState();
                Material belowType = blockBelow.getType();
                Queue.queueBlockBreak(user, blockBelow, belowType, blockBelow.getBlockData().getAsString(), 0);
            }
        }

        if (forceType != null) {
            type = forceType;
            forceData = 1;
        }

        if (forceD != -1) {
            data = forceD;
            forceData = 1;
        }

        String replacedBlockData = null;
        if (blockReplaced != null) {
            replacedBlockData = blockReplaced.getBlockData().getAsString();
        }

        queueStandardData(new Object[] { null, Process.BLOCK_PLACE, type, data, replaceType, replaceData, forceData, blockData, replacedBlockData }, new String[] { user, null }, blockLocation, false, Consumer.reserveConsumer());
    }

    protected static void queueBlockPlaceDelayed(final String user, final Location placed, final Material type, final String blockData, final BlockState replaced, int ticks) {
        Scheduler.scheduleSyncDelayedTask(CoreProtect.getInstance(), () -> {
            try {
                queueBlockPlace(user, placed.getBlock().getState(), type, replaced, null, -1, 0, blockData);
            }
            catch (Exception e) {
                ErrorReporter.report(e);
            }
        }, placed, ticks);
    }

    protected static void queueBlockPlaceValidate(final String user, final BlockState blockLocation, final Block block, final BlockState blockReplaced, final Material forceT, final int forceD, final int forceData, final String blockData, int ticks) {
        queueBlockPlaceValidate(user, blockLocation, block, blockReplaced, forceT, forceD, forceData, blockData, ticks, false);
    }

    protected static void queueBlockPlaceValidate(final String user, final BlockState blockLocation, final Block block, final BlockState blockReplaced, final Material forceT, final int forceD, final int forceData, final String blockData, int ticks, boolean cacheLookup) {
        Scheduler.scheduleSyncDelayedTask(CoreProtect.getInstance(), () -> {
            try {
                Material blockType = block.getType();
                BlockState replacedBlock = blockReplaced;
                boolean placed = Objects.equals(blockType, forceT);
                if (!placed && forceT == Material.WATER) {
                    BlockData currentBlockData = block.getBlockData();
                    if (currentBlockData instanceof Waterlogged) {
                        Waterlogged waterlogged = (Waterlogged) currentBlockData;
                        if (waterlogged.isWaterlogged()) {
                            boolean wasWaterlogged = false;
                            if (blockReplaced != null) {
                                BlockData replacedBlockData = blockReplaced.getBlockData();
                                if (replacedBlockData instanceof Waterlogged) {
                                    wasWaterlogged = ((Waterlogged) replacedBlockData).isWaterlogged();
                                }
                            }

                            if (!wasWaterlogged) {
                                placed = true;
                                replacedBlock = null;
                            }
                        }
                    }
                }

                if (placed) {
                    BlockState blockStateLocation = blockLocation;
                    if (blockType != null && Objects.equals(blockType, forceT) && Config.getConfig(blockLocation.getWorld()).BLOCK_MOVEMENT) {
                        blockStateLocation = BlockUtil.gravityScan(blockLocation.getLocation(), blockType, user).getState();
                    }

                    if (cacheLookup) {
                        Location cacheLocation = blockStateLocation.getLocation();
                        int worldId = WorldUtils.getWorldId(cacheLocation.getWorld().getName());
                        int unixTimestamp = (int) (System.currentTimeMillis() / 1000L);
                        CacheHandler.lookupCache.put(cacheLocation.getBlockX() + "." + cacheLocation.getBlockY() + "." + cacheLocation.getBlockZ() + "." + worldId, new Object[] { unixTimestamp, user, forceT });
                    }

                    queueBlockPlace(user, blockStateLocation, blockType, replacedBlock, forceT, forceD, forceData, blockData);
                }
            }
            catch (Exception e) {
                ErrorReporter.report(e);
            }
        }, blockLocation.getLocation(), ticks);
    }

    protected static void queueContainerBreak(String user, Location location, Material type, ItemStack[] oldInventory) {
        queueStandardData(new Object[] { null, Process.CONTAINER_BREAK, type, 0, null, 0, 0, null }, new String[] { user, null }, location, false, Consumer.consumerContainers, oldInventory, Consumer.reserveConsumer());
    }

    protected static synchronized void queueContainerTransaction(String user, Location location, Material type, Object inventory, int chestId) {
        queueStandardData(new Object[] { null, Process.CONTAINER_TRANSACTION, type, 0, null, 0, chestId, null }, new String[] { user, null }, location, false, Consumer.consumerInventories, inventory, Consumer.reserveConsumer());
    }

    public static void queueEntityContainerTransaction(String user, java.util.UUID entityUuid, Location currentLocation, ItemStack[] oldContents, ItemStack[] newContents) {
        queueEntityContainerTransaction(user, new EntityContainerTransaction(entityUuid, currentLocation, oldContents, newContents));
    }

    public static void queueEntityContainerTransaction(String user, EntityContainerTransaction transaction) {
        if (user == null || user.isEmpty() || transaction == null) {
            return;
        }
        queueStandardData(new Object[] { null, Process.ENTITY_CONTAINER_TRANSACTION, null, 0, null, 0, 0, null }, new String[] { user, null }, transaction, false, Consumer.reserveConsumer());
    }

    protected static void queueItemTransaction(String user, Location location, int time, int offset, int itemId) {
        queueStandardData(new Object[] { null, Process.ITEM_TRANSACTION, null, offset, null, time, itemId, null }, new String[] { user, null }, location, false, Consumer.reserveConsumer());
    }

    protected static void queueEntityInsert(int id, String name) {
        queueStandardData(new Object[] { null, Process.ENTITY_INSERT, null, 0, null, 0, id, null }, new String[] { null, null }, name, false, Consumer.reserveConsumer());
    }

    protected static void queueEntityKill(String user, Location location, List<Object> data, EntityType type) {
        queueStandardData(new Object[] { null, Process.ENTITY_KILL, null, 0, null, 0, 0 }, new String[] { user, null }, new Object[] { getBlockLocation(location), type, null }, false, Consumer.consumerObjectList, data, Consumer.reserveConsumer());
    }

    protected static void queueEntitySpawn(String user, BlockState block, EntityType type, int data) {
        queueStandardData(new Object[] { null, Process.ENTITY_SPAWN, null, 0, null, 0, data, null }, new String[] { user, null }, new Object[] { block, type }, false, Consumer.reserveConsumer());
    }

    public static void queueEntitySpawnLog(String user, java.util.UUID uuid, EntityType type, Location location) {
        queueStandardData(new Object[] { null, Process.ENTITY_SPAWN_LOG, null, 0, null, 0, 0, null }, new String[] { user, null }, EntitySpawnData.log(uuid, type, location), false, Consumer.reserveConsumer());
    }

    public static void queueEntitySpawnLocation(java.util.UUID uuid, Location location, long verificationEpoch) {
        queueEntitySpawnUpdate(EntitySpawnData.location(uuid, location, verificationEpoch));
    }

    public static void queueEntitySpawnRemoved(java.util.UUID uuid, Location location) {
        queueEntitySpawnUpdate(EntitySpawnData.removed(uuid, location));
    }

    public static void queueEntitySpawnRevived(java.util.UUID previousUuid, java.util.UUID uuid, Location location) {
        queueEntitySpawnUpdate(EntitySpawnData.revived(previousUuid, uuid, location));
    }

    public static void queueEntitySpawnUpdate(EntitySpawnData update) {
        queueStandardData(new Object[] { null, Process.ENTITY_SPAWN_UPDATE, null, 0, null, 0, 0, null }, new String[] { null, null }, update, false, Consumer.reserveConsumer());
    }

    public static void queueEntitySpawnUpdateFirst(EntitySpawnData update) {
        queueStandardData(new Object[] { null, Process.ENTITY_SPAWN_UPDATE, null, 0, null, 0, 0, null }, new String[] { null, null }, update, true, Consumer.reserveConsumer());
    }

    protected static void queueMaterialInsert(int id, String name) {
        queueStandardData(new Object[] { null, Process.MATERIAL_INSERT, null, 0, null, 0, id, null }, new String[] { null, null }, name, false, Consumer.reserveConsumer());
    }

    protected static void queueBlockDataInsert(int id, String data) {
        queueStandardData(new Object[] { null, Process.BLOCKDATA_INSERT, null, 0, null, 0, id, null }, new String[] { null, null }, data, false, Consumer.reserveConsumer());
    }

    protected static void queueNaturalBlockBreak(String user, BlockState block, Block relative, Material type, String blockData, int data) {
        List<BlockState> blockStates = new ArrayList<>();
        if (relative != null) {
            blockStates.add(relative.getState());
        }

        queueStandardData(new Object[] { null, Process.NATURAL_BLOCK_BREAK, type, data, null, 0, 0, blockData }, new String[] { user, null }, block, false, Consumer.consumerBlockList, blockStates, Consumer.reserveConsumer());
    }

    protected static void queuePlayerChat(Player player, String message, long timestamp) {
        queueStandardData(new Object[] { null, Process.PLAYER_CHAT, null, 0, null, 0, 0, null }, new String[] { player.getName(), null }, new Object[] { timestamp, player.getLocation().clone() }, false, Consumer.consumerStrings, message, Consumer.reserveConsumer());
    }

    protected static void queuePlayerCommand(Player player, String message, long timestamp) {
        queueStandardData(new Object[] { null, Process.PLAYER_COMMAND, null, 0, null, 0, 0, null }, new String[] { player.getName(), null }, new Object[] { timestamp, player.getLocation().clone() }, false, Consumer.consumerStrings, message, Consumer.reserveConsumer());
    }

    protected static void queuePlayerInteraction(String user, BlockState block, Material type) {
        queueStandardData(new Object[] { null, Process.PLAYER_INTERACTION, type, 0, null, 0, 0, null }, new String[] { user, null }, block, false, Consumer.reserveConsumer());
    }

    protected static void queuePlayerKill(String user, Location location, String player) {
        queueStandardData(new Object[] { null, Process.PLAYER_KILL, null, 0, null, 0, 0, null }, new String[] { user, null }, new Object[] { getBlockLocation(location), player }, false, Consumer.reserveConsumer());
    }

    protected static void queuePlayerLogin(Player player, int time, int configSessions, int configUsernames) {
        String uuid = player.getUniqueId().toString();
        queueStandardData(new Object[] { null, Process.PLAYER_LOGIN, null, configSessions, null, configUsernames, time, null }, new String[] { player.getName(), uuid }, player.getLocation().clone(), false, Consumer.consumerStrings, uuid, Consumer.reserveConsumer());
    }

    protected static void queuePlayerQuit(Player player, int time) {
        queueStandardData(new Object[] { null, Process.PLAYER_LOGOUT, null, 0, null, 0, time, null }, new String[] { player.getName(), null }, player.getLocation().clone(), false, Consumer.reserveConsumer());
    }

    protected static void queueRollbackUpdate(String user, Location location, List<Object[]> list, int table, int action) {
        if (location == null) {
            location = new Location(Bukkit.getServer().getWorlds().get(0), 0, 0, 0);
        }

        queueStandardData(new Object[] { null, table, null, 0, null, 0, action, null }, new String[] { user, null }, location, false, Consumer.consumerObjectArrayList, list, Consumer.reserveConsumer());
    }

    public static void queueEntityContainerRollbackUpdate(String user, Location location, List<Object[]> rows, int rollbackType, boolean inventoryRollback) {
        if (location == null) {
            location = new Location(Bukkit.getServer().getWorlds().get(0), 0, 0, 0);
        }
        queueStandardData(new Object[] { null, Process.ENTITY_CONTAINER_ROLLBACK_UPDATE, null, inventoryRollback ? 1 : 0, null, 0, rollbackType, null }, new String[] { user, null }, location, false, Consumer.consumerObjectArrayList, rows, Consumer.reserveConsumer());
    }

    public static void queueEntityContainerRollbackUpdate(String user, EntitySpawnData transition, List<Object[]> rows, int rollbackType, boolean inventoryRollback) {
        EntityContainerRollbackUpdate update = new EntityContainerRollbackUpdate(user, transition, rows, rollbackType, inventoryRollback);
        queueStandardData(new Object[] { null, Process.ENTITY_CONTAINER_TRANSITION_UPDATE, null, 0, null, 0, 0, null }, new String[] { user, null }, update, false, Consumer.reserveConsumer());
    }

    public static synchronized void queueEntityRetriesFirst(List<EntityContainerRollbackUpdate> containerUpdates, List<EntitySpawnData> spawnUpdates) {
        List<EntityContainerRollbackUpdate> containerRetries = new ArrayList<>(containerUpdates);
        List<EntitySpawnData> spawnRetries = new ArrayList<>(spawnUpdates);
        int updateCount = containerRetries.size() + spawnRetries.size();
        if (updateCount == 0) {
            return;
        }

        List<Object[]> records = new ArrayList<>(updateCount);
        long reservation = Consumer.reserveConsumers(updateCount);
        int rollbackPublicationCount = containerRetries.size();
        for (EntitySpawnData update : spawnRetries) {
            if (Process.isRollbackPublication(Process.ENTITY_SPAWN_UPDATE, update)) {
                rollbackPublicationCount++;
            }
        }
        Consumer.registerRollbackPublications(rollbackPublicationCount);
        boolean published = false;
        try {
            int currentConsumer = (int) (reservation >>> 32);
            int firstConsumerId = (int) reservation;
            int updateIndex = 0;
            for (EntityContainerRollbackUpdate update : containerRetries) {
                int consumerId = firstConsumerId + updateIndex++;
                records.add(new Object[] { consumerId, Process.ENTITY_CONTAINER_TRANSITION_UPDATE, null, 0, null, 0, 0, null });
                Consumer.consumerUsers.get(currentConsumer).put(consumerId, new String[] { update.getUser(), null });
                Consumer.consumerObjects.get(currentConsumer).put(consumerId, update);
            }
            for (EntitySpawnData update : spawnRetries) {
                int consumerId = firstConsumerId + updateIndex++;
                records.add(new Object[] { consumerId, Process.ENTITY_SPAWN_UPDATE, null, 0, null, 0, 0, null });
                Consumer.consumerUsers.get(currentConsumer).put(consumerId, new String[] { null, null });
                Consumer.consumerObjects.get(currentConsumer).put(consumerId, update);
            }
            Consumer.consumer.get(currentConsumer).addAll(0, records);
            published = true;
        }
        finally {
            try {
                if (!published) {
                    int currentConsumer = (int) (reservation >>> 32);
                    int firstConsumerId = (int) reservation;
                    Consumer.consumer.get(currentConsumer).removeAll(records);
                    for (int index = 0; index < updateCount; index++) {
                        int consumerId = firstConsumerId + index;
                        Consumer.consumerUsers.get(currentConsumer).remove(consumerId);
                        Consumer.consumerObjects.get(currentConsumer).remove(consumerId);
                    }
                    Consumer.completeRollbackPublications(rollbackPublicationCount);
                }
            }
            finally {
                Consumer.completeReservation(reservation, updateCount);
            }
        }
    }

    protected static void queueSignText(String user, Location location, int action, int color, int colorSecondary, boolean frontGlowing, boolean backGlowing, boolean isWaxed, boolean isFront, String line1, String line2, String line3, String line4, String line5, String line6, String line7, String line8, int offset) {
        /*
        if (line1.length() == 0 && line2.length() == 0 && line3.length() == 0 && line4.length() == 0) {
            return;
        }
        */
        Object[] signData = new Object[] { colorSecondary, BlockUtils.getSignData(frontGlowing, backGlowing), isWaxed, isFront, line1, line2, line3, line4, line5, line6, line7, line8 };
        queueStandardData(new Object[] { null, Process.SIGN_TEXT, null, color, null, action, offset, null }, new String[] { user, null }, location, false, Consumer.consumerSigns, signData, Consumer.reserveConsumer());
    }

    protected static void queueSignUpdate(String user, BlockState block, int action, int time) {
        queueStandardData(new Object[] { null, Process.SIGN_UPDATE, null, action, null, 0, time, null }, new String[] { user, null }, block, false, Consumer.reserveConsumer());
    }

    protected static void queueSkullUpdate(String user, BlockState block, int rowId) {
        queueStandardData(new Object[] { null, Process.SKULL_UPDATE, null, 0, null, 0, rowId, null }, new String[] { user, null }, block, false, Consumer.reserveConsumer());
    }

    protected static void queueStructureGrow(String user, BlockState block, List<BlockState> blockList, int replacedListSize) {
        queueStandardData(new Object[] { null, Process.STRUCTURE_GROWTH, null, 0, null, 0, replacedListSize, null }, new String[] { user, null }, block, false, Consumer.consumerBlockList, blockList, Consumer.reserveConsumer());
    }

    protected static void queueWorldInsert(int id, String world) {
        queueStandardData(new Object[] { null, Process.WORLD_INSERT, null, 0, null, 0, id, null }, new String[] { null, null }, world, false, Consumer.reserveConsumer());
    }
}
