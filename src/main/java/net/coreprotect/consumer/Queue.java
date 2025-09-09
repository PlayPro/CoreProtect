package net.coreprotect.consumer;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.Bisected.Half;
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
import net.coreprotect.thread.Scheduler;
import net.coreprotect.utility.BlockUtils;
import net.coreprotect.utility.EntityUtils;

public class Queue {

    protected static synchronized int modifyForceContainer(String id, ItemStack[] container) {
        List<ItemStack[]> forceList = ConfigHandler.forceContainer.get(id);
        if (forceList == null) {
            return 0;
        }

        if (container == null) {
            forceList.remove(0);
        }
        else {
            forceList.add(container);
        }

        return forceList.size();
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

    private static synchronized void addConsumer(int currentConsumer, Object[] data) {
        Consumer.consumer.get(currentConsumer).add(data);
    }

    private static synchronized void queueStandardData(int consumerId, int currentConsumer, String[] user, Object object) {
        Consumer.consumerUsers.get(currentConsumer).put(consumerId, user);
        Consumer.consumerObjects.get(currentConsumer).put(consumerId, object);
        Consumer.consumer_id.put(currentConsumer, new Integer[] { Consumer.consumer_id.get(currentConsumer)[0], 0 });
    }

    protected static void queueAdvancedBreak(String user, BlockState block, Material type, String blockData, int data, Material breakType, int blockNumber) {
        int currentConsumer = Consumer.currentConsumer;
        int consumerId = Consumer.newConsumerId(currentConsumer);
        addConsumer(currentConsumer, new Object[] { consumerId, Process.BLOCK_BREAK, type, data, breakType, 0, blockNumber, blockData });
        queueStandardData(consumerId, currentConsumer, new String[] { user, null }, block);
    }

    protected static void queueArtInsert(int id, String name) {
        int currentConsumer = Consumer.currentConsumer;
        int consumerId = Consumer.newConsumerId(currentConsumer);
        addConsumer(currentConsumer, new Object[] { consumerId, Process.ART_INSERT, null, 0, null, 0, id, null });
        queueStandardData(consumerId, currentConsumer, new String[] { null, null }, name);
    }

    protected static void queueBlockBreak(String user, BlockState block, Material type, String blockData, int extraData) {
        queueBlockBreak(user, block, type, blockData, null, extraData, 0);
    }

    protected static void queueBlockBreakValidate(final String user, final Block block, final BlockState blockState, final Material type, final String blockData, final int extraData, int ticks) {
        Scheduler.scheduleSyncDelayedTask(CoreProtect.getInstance(), () -> {
            try {
                if (!block.getType().equals(type)) {
                    queueBlockBreak(user, blockState, type, blockData, null, extraData, 0);
                }
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }, block.getLocation(), ticks);
    }

    protected static void queueBlockBreak(String user, BlockState block, Material type, String blockData, Material breakType, int extraData, int blockNumber) {
        if (type == Material.SPAWNER && block instanceof CreatureSpawner) { // Mob spawner
            CreatureSpawner mobSpawner = (CreatureSpawner) block;
            extraData = EntityUtils.getSpawnerType(mobSpawner.getSpawnedType());
        }
        else if (type == Material.IRON_DOOR || BlockGroup.DOORS.contains(type) || type.equals(Material.SUNFLOWER) || type.equals(Material.LILAC) || type.equals(Material.TALL_GRASS) || type.equals(Material.LARGE_FERN) || type.equals(Material.ROSE_BUSH) || type.equals(Material.PEONY)) { // Double plant
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
        else if (type.name().endsWith("_BED") && block.getBlockData() instanceof Bed) {
            if (((Bed) block.getBlockData()).getPart().equals(Part.HEAD)) {
                return;
            }
        }

        int currentConsumer = Consumer.currentConsumer;
        int consumerId = Consumer.newConsumerId(currentConsumer);
        addConsumer(currentConsumer, new Object[] { consumerId, Process.BLOCK_BREAK, type, extraData, breakType, 0, blockNumber, blockData });
        queueStandardData(consumerId, currentConsumer, new String[] { user, null }, block);
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

            if ((replaceType == Material.IRON_DOOR || BlockGroup.DOORS.contains(replaceType) || replaceType.equals(Material.SUNFLOWER) || replaceType.equals(Material.LILAC) || replaceType.equals(Material.TALL_GRASS) || replaceType.equals(Material.LARGE_FERN) || replaceType.equals(Material.ROSE_BUSH) || replaceType.equals(Material.PEONY)) && replaceData >= 8) { // Double plant top half
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

        int currentConsumer = Consumer.currentConsumer;
        int consumerId = Consumer.newConsumerId(currentConsumer);
        addConsumer(currentConsumer, new Object[] { consumerId, Process.BLOCK_PLACE, type, data, replaceType, replaceData, forceData, blockData, replacedBlockData });
        queueStandardData(consumerId, currentConsumer, new String[] { user, null }, blockLocation);
    }

    protected static void queueBlockPlaceDelayed(final String user, final Location placed, final Material type, final String blockData, final BlockState replaced, int ticks) {
        Scheduler.scheduleSyncDelayedTask(CoreProtect.getInstance(), () -> {
            try {
                queueBlockPlace(user, placed.getBlock().getState(), type, replaced, null, -1, 0, blockData);
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }, placed, ticks);
    }

    protected static void queueBlockPlaceValidate(final String user, final BlockState blockLocation, final Block block, final BlockState blockReplaced, final Material forceT, final int forceD, final int forceData, final String blockData, int ticks) {
        Scheduler.scheduleSyncDelayedTask(CoreProtect.getInstance(), () -> {
            try {
                Material blockType = block.getType();
                if (blockType.equals(forceT)) {
                    BlockState blockStateLocation = blockLocation;
                    if (Config.getConfig(blockLocation.getWorld()).BLOCK_MOVEMENT) {
                        blockStateLocation = BlockUtil.gravityScan(blockLocation.getLocation(), blockType, user).getState();
                    }

                    queueBlockPlace(user, blockStateLocation, blockType, blockReplaced, forceT, forceD, forceData, blockData);
                }
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }, blockLocation.getLocation(), ticks);
    }

    protected static void queueBlockGravityValidate(final String user, final Location location, final Block block, final Material blockType, int ticks) {
        Scheduler.scheduleSyncDelayedTask(CoreProtect.getInstance(), () -> {
            try {
                Block placementBlock = BlockUtil.gravityScan(location, blockType, user);
                if (!block.equals(placementBlock)) {
                    queueBlockPlace(user, placementBlock.getState(), blockType, null, blockType, -1, 0, null);
                }
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }, location, ticks);
    }

    protected static void queueContainerBreak(String user, Location location, Material type, ItemStack[] oldInventory) {
        int currentConsumer = Consumer.currentConsumer;
        int consumerId = Consumer.newConsumerId(currentConsumer);
        addConsumer(currentConsumer, new Object[] { consumerId, Process.CONTAINER_BREAK, type, 0, null, 0, 0, null });
        Consumer.consumerContainers.get(currentConsumer).put(consumerId, oldInventory);
        queueStandardData(consumerId, currentConsumer, new String[] { user, null }, location);
    }

    protected static synchronized void queueContainerTransaction(String user, Location location, Material type, Object inventory, int chestId) {
        int currentConsumer = Consumer.currentConsumer;
        int consumerId = Consumer.newConsumerId(currentConsumer);
        addConsumer(currentConsumer, new Object[] { consumerId, Process.CONTAINER_TRANSACTION, type, 0, null, 0, chestId, null });
        Consumer.consumerInventories.get(currentConsumer).put(consumerId, inventory);
        queueStandardData(consumerId, currentConsumer, new String[] { user, null }, location);
    }

    protected static void queueItemTransaction(String user, Location location, int time, int offset, int itemId) {
        int currentConsumer = Consumer.currentConsumer;
        int consumerId = Consumer.newConsumerId(currentConsumer);
        addConsumer(currentConsumer, new Object[] { consumerId, Process.ITEM_TRANSACTION, null, offset, null, time, itemId, null });
        queueStandardData(consumerId, currentConsumer, new String[] { user, null }, location);
    }

    protected static void queueEntityInsert(int id, String name) {
        int currentConsumer = Consumer.currentConsumer;
        int consumerId = Consumer.newConsumerId(currentConsumer);
        addConsumer(currentConsumer, new Object[] { consumerId, Process.ENTITY_INSERT, null, 0, null, 0, id, null });
        queueStandardData(consumerId, currentConsumer, new String[] { null, null }, name);
    }

    protected static void queueEntityKill(String user, Location location, List<Object> data, EntityType type) {
        int currentConsumer = Consumer.currentConsumer;
        int consumerId = Consumer.newConsumerId(currentConsumer);
        addConsumer(currentConsumer, new Object[] { consumerId, Process.ENTITY_KILL, null, 0, null, 0, 0 });
        Consumer.consumerObjectList.get(currentConsumer).put(consumerId, data);
        queueStandardData(consumerId, currentConsumer, new String[] { user, null }, new Object[] { location.getBlock().getState(), type, null });
    }

    protected static void queueEntitySpawn(String user, BlockState block, EntityType type, int data) {
        int currentConsumer = Consumer.currentConsumer;
        int consumerId = Consumer.newConsumerId(currentConsumer);
        addConsumer(currentConsumer, new Object[] { consumerId, Process.ENTITY_SPAWN, null, 0, null, 0, data, null });
        queueStandardData(consumerId, currentConsumer, new String[] { user, null }, new Object[] { block, type });
    }

    protected static void queueMaterialInsert(int id, String name) {
        int currentConsumer = Consumer.currentConsumer;
        int consumerId = Consumer.newConsumerId(currentConsumer);
        addConsumer(currentConsumer, new Object[] { consumerId, Process.MATERIAL_INSERT, null, 0, null, 0, id, null });
        queueStandardData(consumerId, currentConsumer, new String[] { null, null }, name);
    }

    protected static void queueBlockDataInsert(int id, String data) {
        int currentConsumer = Consumer.currentConsumer;
        int consumerId = Consumer.newConsumerId(currentConsumer);
        addConsumer(currentConsumer, new Object[] { consumerId, Process.BLOCKDATA_INSERT, null, 0, null, 0, id, null });
        queueStandardData(consumerId, currentConsumer, new String[] { null, null }, data);
    }

    protected static void queueNaturalBlockBreak(String user, BlockState block, Block relative, Material type, String blockData, int data) {
        List<BlockState> blockStates = new ArrayList<>();
        if (relative != null) {
            blockStates.add(relative.getState());
        }

        int currentConsumer = Consumer.currentConsumer;
        int consumerId = Consumer.newConsumerId(currentConsumer);
        addConsumer(currentConsumer, new Object[] { consumerId, Process.NATURAL_BLOCK_BREAK, type, data, null, 0, 0, blockData });
        Consumer.consumerBlockList.get(currentConsumer).put(consumerId, blockStates);
        queueStandardData(consumerId, currentConsumer, new String[] { user, null }, block);
    }

    protected static void queuePlayerChat(Player player, String message, long timestamp) {
        int currentConsumer = Consumer.currentConsumer;
        int consumerId = Consumer.newConsumerId(currentConsumer);
        addConsumer(currentConsumer, new Object[] { consumerId, Process.PLAYER_CHAT, null, 0, null, 0, 0, null });
        Consumer.consumerStrings.get(currentConsumer).put(consumerId, message);
        queueStandardData(consumerId, currentConsumer, new String[] { player.getName(), null }, new Object[] { timestamp, player.getLocation().clone() });
    }

    protected static void queuePlayerCommand(Player player, String message, long timestamp) {
        int currentConsumer = Consumer.currentConsumer;
        int consumerId = Consumer.newConsumerId(currentConsumer);
        addConsumer(currentConsumer, new Object[] { consumerId, Process.PLAYER_COMMAND, null, 0, null, 0, 0, null });
        Consumer.consumerStrings.get(currentConsumer).put(consumerId, message);
        queueStandardData(consumerId, currentConsumer, new String[] { player.getName(), null }, new Object[] { timestamp, player.getLocation().clone() });
    }

    protected static void queuePlayerInteraction(String user, BlockState block, Material type) {
        int currentConsumer = Consumer.currentConsumer;
        int consumerId = Consumer.newConsumerId(currentConsumer);
        addConsumer(currentConsumer, new Object[] { consumerId, Process.PLAYER_INTERACTION, type, 0, null, 0, 0, null });
        queueStandardData(consumerId, currentConsumer, new String[] { user, null }, block);
    }

    protected static void queuePlayerKill(String user, Location location, String player) {
        int currentConsumer = Consumer.currentConsumer;
        int consumerId = Consumer.newConsumerId(currentConsumer);
        addConsumer(currentConsumer, new Object[] { consumerId, Process.PLAYER_KILL, null, 0, null, 0, 0, null });
        queueStandardData(consumerId, currentConsumer, new String[] { user, null }, new Object[] { location.getBlock().getState(), player });
    }

    protected static void queuePlayerLogin(Player player, int time, int configSessions, int configUsernames) {
        int currentConsumer = Consumer.currentConsumer;
        int consumerId = Consumer.newConsumerId(currentConsumer);
        String uuid = player.getUniqueId().toString();
        addConsumer(currentConsumer, new Object[] { consumerId, Process.PLAYER_LOGIN, null, configSessions, null, configUsernames, time, null });
        Consumer.consumerStrings.get(currentConsumer).put(consumerId, uuid);
        queueStandardData(consumerId, currentConsumer, new String[] { player.getName(), uuid }, player.getLocation().clone());
    }

    protected static void queuePlayerQuit(Player player, int time) {
        int currentConsumer = Consumer.currentConsumer;
        int consumerId = Consumer.newConsumerId(currentConsumer);
        addConsumer(currentConsumer, new Object[] { consumerId, Process.PLAYER_LOGOUT, null, 0, null, 0, time, null });
        queueStandardData(consumerId, currentConsumer, new String[] { player.getName(), null }, player.getLocation().clone());
    }

    protected static void queueRollbackUpdate(String user, Location location, List<Object[]> list, int table, int action) {
        if (location == null) {
            location = new Location(Bukkit.getServer().getWorlds().get(0), 0, 0, 0);
        }

        int currentConsumer = Consumer.currentConsumer;
        int consumerId = Consumer.newConsumerId(currentConsumer);
        addConsumer(currentConsumer, new Object[] { consumerId, table, null, 0, null, 0, action, null });
        Consumer.consumerObjectArrayList.get(currentConsumer).put(consumerId, list);
        queueStandardData(consumerId, currentConsumer, new String[] { user, null }, location);
    }

    protected static void queueSignText(String user, Location location, int action, int color, int colorSecondary, boolean frontGlowing, boolean backGlowing, boolean isWaxed, boolean isFront, String line1, String line2, String line3, String line4, String line5, String line6, String line7, String line8, int offset) {
        /*
        if (line1.length() == 0 && line2.length() == 0 && line3.length() == 0 && line4.length() == 0) {
            return;
        }
        */
        int currentConsumer = Consumer.currentConsumer;
        int consumerId = Consumer.newConsumerId(currentConsumer);
        addConsumer(currentConsumer, new Object[] { consumerId, Process.SIGN_TEXT, null, color, null, action, offset, null });
        Consumer.consumerSigns.get(currentConsumer).put(consumerId, new Object[] { colorSecondary, BlockUtils.getSignData(frontGlowing, backGlowing), isWaxed, isFront, line1, line2, line3, line4, line5, line6, line7, line8 });
        queueStandardData(consumerId, currentConsumer, new String[] { user, null }, location);
    }

    protected static void queueSignUpdate(String user, BlockState block, int action, int time) {
        int currentConsumer = Consumer.currentConsumer;
        int consumerId = Consumer.newConsumerId(currentConsumer);
        addConsumer(currentConsumer, new Object[] { consumerId, Process.SIGN_UPDATE, null, action, null, 0, time, null });
        queueStandardData(consumerId, currentConsumer, new String[] { user, null }, block);
    }

    protected static void queueSkullUpdate(String user, BlockState block, int rowId) {
        int currentConsumer = Consumer.currentConsumer;
        int consumerId = Consumer.newConsumerId(currentConsumer);
        addConsumer(currentConsumer, new Object[] { consumerId, Process.SKULL_UPDATE, null, 0, null, 0, rowId, null });
        queueStandardData(consumerId, currentConsumer, new String[] { user, null }, block);
    }

    protected static void queueStructureGrow(String user, BlockState block, List<BlockState> blockList, int replacedListSize) {
        int currentConsumer = Consumer.currentConsumer;
        int consumerId = Consumer.newConsumerId(currentConsumer);
        addConsumer(currentConsumer, new Object[] { consumerId, Process.STRUCTURE_GROWTH, null, 0, null, 0, replacedListSize, null });
        Consumer.consumerBlockList.get(currentConsumer).put(consumerId, blockList);
        queueStandardData(consumerId, currentConsumer, new String[] { user, null }, block);
    }

    protected static void queueWorldInsert(int id, String world) {
        int currentConsumer = Consumer.currentConsumer;
        int consumerId = Consumer.newConsumerId(currentConsumer);
        addConsumer(currentConsumer, new Object[] { consumerId, Process.WORLD_INSERT, null, 0, null, 0, id, null });
        queueStandardData(consumerId, currentConsumer, new String[] { null, null }, world);
    }
}
