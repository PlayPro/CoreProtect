package net.coreprotect.database;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Statement;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.DyeColor;
import org.bukkit.FireworkEffect;
import org.bukkit.FireworkEffect.Builder;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Banner;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.CommandBlock;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.block.Jukebox;
import org.bukkit.block.ShulkerBox;
import org.bukkit.block.banner.Pattern;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.Bisected.Half;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.MultipleFacing;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.block.data.type.Bed;
import org.bukkit.block.data.type.Bed.Part;
import org.bukkit.block.data.type.Chest;
import org.bukkit.block.data.type.Door;
import org.bukkit.block.data.type.Door.Hinge;
import org.bukkit.block.data.type.Piston;
import org.bukkit.block.data.type.PistonHead;
import org.bukkit.block.data.type.RedstoneWire;
import org.bukkit.block.data.type.Snow;
import org.bukkit.block.data.type.Stairs;
import org.bukkit.block.data.type.TechnicalPiston;
import org.bukkit.block.data.type.TrapDoor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.BannerMeta;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.CrossbowMeta;
import org.bukkit.inventory.meta.FireworkEffectMeta;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.inventory.meta.SuspiciousStewMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.util.io.BukkitObjectInputStream;

import net.coreprotect.CoreProtect;
import net.coreprotect.bukkit.BukkitAdapter;
import net.coreprotect.config.Config;
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.consumer.Queue;
import net.coreprotect.consumer.process.Process;
import net.coreprotect.database.logger.ItemLogger;
import net.coreprotect.database.statement.UserStatement;
import net.coreprotect.language.Phrase;
import net.coreprotect.language.Selector;
import net.coreprotect.model.BlockGroup;
import net.coreprotect.paper.PaperAdapter;
import net.coreprotect.thread.CacheHandler;
import net.coreprotect.thread.Scheduler;
import net.coreprotect.utility.Chat;
import net.coreprotect.utility.ChestTool;
import net.coreprotect.utility.Color;
import net.coreprotect.utility.Teleport;
import net.coreprotect.utility.Util;
import net.coreprotect.utility.entity.HangingUtil;

public class Rollback extends Queue {

    public static List<String[]> performRollbackRestore(Statement statement, CommandSender user, List<String> checkUuids, List<String> checkUsers, String timeString, List<Object> restrictList, Map<Object, Boolean> excludeList, List<String> excludeUserList, List<Integer> actionList, Location location, Integer[] radius, long startTime, long endTime, boolean restrictWorld, boolean lookup, boolean verbose, final int rollbackType, final int preview) {
        List<String[]> list = new ArrayList<>();

        try {
            long timeStart = System.currentTimeMillis();
            List<Object[]> lookupList = new ArrayList<>();

            if (!actionList.contains(4) && !actionList.contains(5) && !checkUsers.contains("#container")) {
                lookupList = Lookup.performLookupRaw(statement, user, checkUuids, checkUsers, restrictList, excludeList, excludeUserList, actionList, location, radius, null, startTime, endTime, -1, -1, restrictWorld, lookup);
            }

            if (lookupList == null) {
                return null;
            }

            boolean ROLLBACK_ITEMS = false;
            List<Object> itemRestrictList = new ArrayList<>(restrictList);
            Map<Object, Boolean> itemExcludeList = new HashMap<>(excludeList);

            if (actionList.contains(1)) {
                for (Object target : restrictList) {
                    if (target instanceof Material) {
                        if (!excludeList.containsKey(target)) {
                            if (BlockGroup.CONTAINERS.contains(target)) {
                                ROLLBACK_ITEMS = true;
                                itemRestrictList.clear();
                                itemExcludeList.clear();
                                break;
                            }
                        }
                    }
                }
            }

            List<Object[]> itemList = new ArrayList<>();
            if (Config.getGlobal().ROLLBACK_ITEMS && !checkUsers.contains("#container") && (actionList.size() == 0 || actionList.contains(4) || ROLLBACK_ITEMS) && preview == 0) {
                List<Integer> itemActionList = new ArrayList<>(actionList);

                if (!itemActionList.contains(4)) {
                    itemActionList.add(4);
                }

                itemExcludeList.entrySet().removeIf(entry -> Boolean.TRUE.equals(entry.getValue()));
                itemList = Lookup.performLookupRaw(statement, user, checkUuids, checkUsers, itemRestrictList, itemExcludeList, excludeUserList, itemActionList, location, radius, null, startTime, endTime, -1, -1, restrictWorld, lookup);
            }

            LinkedHashSet<Integer> worldList = new LinkedHashSet<>();
            TreeMap<Long, Integer> chunkList = new TreeMap<>();
            HashMap<Integer, HashMap<Long, ArrayList<Object[]>>> dataList = new HashMap<>();
            HashMap<Integer, HashMap<Long, ArrayList<Object[]>>> itemDataList = new HashMap<>();
            boolean inventoryRollback = actionList.contains(11);

            /*
            int worldMin = BukkitAdapter.ADAPTER.getMinHeight(world);
            int worldHeight = world.getMaxHeight() - worldMin;
            int negativeOffset = (-(worldMin) >> 4);
            Integer[] chunkSections = new Integer[worldHeight >> 4];
            
            int y = -2044;
            if (y < worldMin) {
                return;
            }
            int chunkSection = ((y >> 4) + negativeOffset);
            */

            int worldId = -1;
            int worldMin = 0;
            int worldMax = 2032;

            int listC = 0;
            while (listC < 2) {
                List<Object[]> scanList = lookupList;

                if (listC == 1) {
                    scanList = itemList;
                }

                for (Object[] result : scanList) {
                    int userId = (Integer) result[2];
                    int rowX = (Integer) result[3];
                    int rowY = (Integer) result[4];
                    int rowZ = (Integer) result[5];
                    int rowWorldId = (Integer) result[10];
                    int chunkX = rowX >> 4;
                    int chunkZ = rowZ >> 4;
                    long chunkKey = inventoryRollback ? 0 : (chunkX & 0xffffffffL | (chunkZ & 0xffffffffL) << 32);
                    // int rowAction = result[8];
                    // if (rowAction==10) result[8] = 0;
                    // if (rowAction==11) result[8] = 1;

                    if (rowWorldId != worldId) {
                        String world = Util.getWorldName(rowWorldId);
                        World bukkitWorld = Bukkit.getServer().getWorld(world);
                        if (bukkitWorld != null) {
                            worldMin = BukkitAdapter.ADAPTER.getMinHeight(bukkitWorld);
                            worldMax = bukkitWorld.getMaxHeight();
                        }
                    }

                    /*
                    if (rowY < worldMin) {
                        continue;
                    }
                    */

                    if (chunkList.get(chunkKey) == null) {
                        int distance = 0;
                        if (location != null) {
                            distance = (int) Math.sqrt(Math.pow((Integer) result[3] - location.getBlockX(), 2) + Math.pow((Integer) result[5] - location.getBlockZ(), 2));
                        }

                        chunkList.put(chunkKey, distance);
                    }

                    if (ConfigHandler.playerIdCacheReversed.get(userId) == null) {
                        UserStatement.loadName(statement.getConnection(), userId);
                    }

                    HashMap<Integer, HashMap<Long, ArrayList<Object[]>>> modifyList = dataList;
                    if (listC == 1) {
                        modifyList = itemDataList;
                    }

                    if (modifyList.get(rowWorldId) == null) {
                        dataList.put(rowWorldId, new HashMap<>());
                        itemDataList.put(rowWorldId, new HashMap<>());
                        worldList.add(rowWorldId);
                    }

                    if (modifyList.get(rowWorldId).get(chunkKey) == null) {
                        // Integer[][] chunkSections = new Integer[((worldMax - worldMin) >> 4)][];
                        // adjacentDataList.put(chunkKey, chunkSections);
                        dataList.get(rowWorldId).put(chunkKey, new ArrayList<>());
                        itemDataList.get(rowWorldId).put(chunkKey, new ArrayList<>());
                    }

                    modifyList.get(rowWorldId).get(chunkKey).add(result);
                }

                listC++;
            }

            if (rollbackType == 1) { // Restore
                Iterator<Map.Entry<Integer, HashMap<Long, ArrayList<Object[]>>>> dlIterator = dataList.entrySet().iterator();
                while (dlIterator.hasNext()) {
                    for (ArrayList<Object[]> map : dlIterator.next().getValue().values()) {
                        Collections.reverse(map);
                    }
                }

                dlIterator = itemDataList.entrySet().iterator();
                while (dlIterator.hasNext()) {
                    for (ArrayList<Object[]> map : dlIterator.next().getValue().values()) {
                        Collections.reverse(map);
                    }
                }
            }

            Integer chunkCount = 0;
            String userString = "#server";
            if (user != null) {
                userString = user.getName();
                if (verbose && preview == 0 && !actionList.contains(11)) {
                    Integer chunks = chunkList.size();
                    Chat.sendMessage(user, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.ROLLBACK_CHUNKS_FOUND, chunks.toString(), (chunks == 1 ? Selector.FIRST : Selector.SECOND)));
                }
            }

            // Perform update transaction(s) in consumer
            if (preview == 0) {
                if (actionList.contains(11)) {
                    List<Object[]> blockList = new ArrayList<>();
                    List<Object[]> inventoryList = new ArrayList<>();
                    List<Object[]> containerList = new ArrayList<>();
                    for (Object[] data : itemList) {
                        int table = (Integer) data[14];
                        if (table == 2) { // item
                            inventoryList.add(data);
                        }
                        else if (table == 1) { // container
                            containerList.add(data);
                        }
                        else { // block
                            blockList.add(data);
                        }
                    }
                    Queue.queueRollbackUpdate(userString, location, inventoryList, Process.INVENTORY_ROLLBACK_UPDATE, rollbackType);
                    Queue.queueRollbackUpdate(userString, location, containerList, Process.INVENTORY_CONTAINER_ROLLBACK_UPDATE, rollbackType);
                    Queue.queueRollbackUpdate(userString, location, blockList, Process.BLOCK_INVENTORY_ROLLBACK_UPDATE, rollbackType);
                }
                else {
                    Queue.queueRollbackUpdate(userString, location, lookupList, Process.ROLLBACK_UPDATE, rollbackType);
                    Queue.queueRollbackUpdate(userString, location, itemList, Process.CONTAINER_ROLLBACK_UPDATE, rollbackType);
                }
            }

            ConfigHandler.rollbackHash.put(userString, new int[] { 0, 0, 0, 0, 0 });

            final String finalUserString = userString;
            for (Entry<Long, Integer> entry : Util.entriesSortedByValues(chunkList)) {
                chunkCount++;

                int itemCount = 0;
                int blockCount = 0;
                int entityCount = 0;
                int scannedWorldData = 0;
                int[] rollbackHashData = ConfigHandler.rollbackHash.get(finalUserString);
                itemCount = rollbackHashData[0];
                blockCount = rollbackHashData[1];
                entityCount = rollbackHashData[2];
                scannedWorldData = rollbackHashData[4];

                long chunkKey = entry.getKey();
                final int finalChunkX = (int) chunkKey;
                final int finalChunkZ = (int) (chunkKey >> 32);
                final CommandSender finalUser = user;

                HashMap<Integer, World> worldMap = new HashMap<>();
                for (int rollbackWorldId : worldList) {
                    String rollbackWorld = Util.getWorldName(rollbackWorldId);
                    if (rollbackWorld.length() == 0) {
                        continue;
                    }

                    World bukkitRollbackWorld = Bukkit.getServer().getWorld(rollbackWorld);
                    if (bukkitRollbackWorld == null) {
                        continue;
                    }

                    worldMap.put(rollbackWorldId, bukkitRollbackWorld);
                }

                ConfigHandler.rollbackHash.put(finalUserString, new int[] { itemCount, blockCount, entityCount, 0, scannedWorldData });
                for (Entry<Integer, World> rollbackWorlds : worldMap.entrySet()) {
                    Integer rollbackWorldId = rollbackWorlds.getKey();
                    World bukkitRollbackWorld = rollbackWorlds.getValue();
                    Location chunkLocation = new Location(bukkitRollbackWorld, (finalChunkX << 4), 0, (finalChunkZ << 4));
                    final HashMap<Long, ArrayList<Object[]>> finalBlockList = dataList.get(rollbackWorldId);
                    final HashMap<Long, ArrayList<Object[]>> finalItemList = itemDataList.get(rollbackWorldId);

                    Scheduler.scheduleSyncDelayedTask(CoreProtect.getInstance(), () -> {
                        try {
                            boolean clearInventories = false;
                            if (Config.getGlobal().ROLLBACK_ITEMS) {
                                clearInventories = true;
                            }

                            ArrayList<Object[]> data = finalBlockList.getOrDefault(chunkKey, new ArrayList<>());
                            ArrayList<Object[]> itemData = finalItemList.getOrDefault(chunkKey, new ArrayList<>());
                            Map<Block, BlockData> chunkChanges = new LinkedHashMap<>();

                            for (Object[] row : data) {
                                int unixtimestamp = (int) (System.currentTimeMillis() / 1000L);
                                int[] rollbackHashData1 = ConfigHandler.rollbackHash.get(finalUserString);
                                int itemCount1 = rollbackHashData1[0];
                                int blockCount1 = rollbackHashData1[1];
                                int entityCount1 = rollbackHashData1[2];
                                int scannedWorlds = rollbackHashData1[4];
                                // int rowId = row[0];
                                int rowTime = (Integer) row[1];
                                int rowUserId = (Integer) row[2];
                                int rowX = (Integer) row[3];
                                int rowY = (Integer) row[4];
                                int rowZ = (Integer) row[5];
                                int rowTypeRaw = (Integer) row[6];
                                int rowData = (Integer) row[7];
                                int rowAction = (Integer) row[8];
                                int rowRolledBack = Util.rolledBack((Integer) row[9], false);
                                int rowWorldId = (Integer) row[10];
                                byte[] rowMeta = (byte[]) row[12];
                                byte[] rowBlockData = (byte[]) row[13];
                                String blockDataString = Util.byteDataToString(rowBlockData, rowTypeRaw);
                                Material rowType = Util.getType(rowTypeRaw);

                                List<Object> meta = null;
                                if (rowMeta != null) {
                                    ByteArrayInputStream metaByteStream = new ByteArrayInputStream(rowMeta);
                                    BukkitObjectInputStream metaObjectStream = new BukkitObjectInputStream(metaByteStream);
                                    @SuppressWarnings("unchecked")
                                    List<Object> metaList = (List<Object>) metaObjectStream.readObject();
                                    metaObjectStream.close();
                                    metaByteStream.close();
                                    meta = metaList;
                                }

                                BlockData blockData = null;
                                if (blockDataString != null && blockDataString.contains(":")) {
                                    try {
                                        blockData = Bukkit.getServer().createBlockData(blockDataString);
                                    }
                                    catch (Exception e) {
                                        // corrupt BlockData, let the server automatically set the BlockData instead
                                    }
                                }

                                BlockData rawBlockData = null;
                                if (blockData != null) {
                                    rawBlockData = blockData.clone();
                                }
                                if (rawBlockData == null && rowType != null && rowType.isBlock()) {
                                    rawBlockData = Util.createBlockData(rowType);
                                }

                                String rowUser = ConfigHandler.playerIdCacheReversed.get(rowUserId);
                                int oldTypeRaw = rowTypeRaw;
                                Material oldTypeMaterial = Util.getType(oldTypeRaw);

                                if (rowAction == 1 && rollbackType == 0) { // block placement
                                    rowType = Material.AIR;
                                    blockData = null;
                                    rowTypeRaw = 0;
                                }
                                else if (rowAction == 0 && rollbackType == 1) { // block removal
                                    rowType = Material.AIR;
                                    blockData = null;
                                    rowTypeRaw = 0;
                                }
                                else if (rowAction == 4 && rollbackType == 0) { // entity placement
                                    rowType = null;
                                    rowTypeRaw = 0;
                                }
                                else if (rowAction == 3 && rollbackType == 1) { // entity removal
                                    rowType = null;
                                    rowTypeRaw = 0;
                                }

                                if (preview > 0) {
                                    if (rowAction != 3) { // entity kill
                                        String world = Util.getWorldName(rowWorldId);
                                        if (world.length() == 0) {
                                            continue;
                                        }

                                        World bukkitWorld = Bukkit.getServer().getWorld(world);
                                        if (bukkitWorld == null) {
                                            continue;
                                        }

                                        Block block = new Location(bukkitWorld, rowX, rowY, rowZ).getBlock();
                                        if (preview == 2) {
                                            Material blockType = block.getType();
                                            if (!BukkitAdapter.ADAPTER.isItemFrame(blockType) && !blockType.equals(Material.PAINTING) && !blockType.equals(Material.ARMOR_STAND) && !blockType.equals(Material.END_CRYSTAL)) {
                                                Util.prepareTypeAndData(chunkChanges, block, blockType, block.getBlockData(), true);
                                                blockCount1++;
                                            }
                                        }
                                        else {
                                            if ((!BukkitAdapter.ADAPTER.isItemFrame(rowType)) && (rowType != Material.PAINTING) && (rowType != Material.ARMOR_STAND) && (rowType != Material.END_CRYSTAL)) {
                                                Util.prepareTypeAndData(chunkChanges, block, rowType, blockData, true);
                                                blockCount1++;
                                            }
                                        }
                                    }
                                    else {
                                        entityCount1++;
                                    }
                                }
                                else if (rowAction == 3) { // entity kill
                                    String world = Util.getWorldName(rowWorldId);
                                    if (world.length() == 0) {
                                        continue;
                                    }

                                    World bukkitWorld = Bukkit.getServer().getWorld(world);
                                    if (bukkitWorld == null) {
                                        continue;
                                    }
                                    Block block = bukkitWorld.getBlockAt(rowX, rowY, rowZ);
                                    if (!bukkitWorld.isChunkLoaded(block.getChunk())) {
                                        bukkitWorld.getChunkAt(block.getLocation());
                                    }

                                    if (rowTypeRaw > 0) {
                                        // spawn in entity
                                        if (rowRolledBack == 0) {
                                            EntityType entity_type = Util.getEntityType(rowTypeRaw);
                                            Queue.queueEntitySpawn(rowUser, block.getState(), entity_type, rowData);
                                            entityCount1++;
                                        }
                                    }
                                    else if (oldTypeRaw > 0) {
                                        // attempt to remove entity
                                        if (rowRolledBack == 1) {
                                            boolean removed = false;
                                            int entityId = -1;
                                            String entityName = Util.getEntityType(oldTypeRaw).name();
                                            String token = "" + rowX + "." + rowY + "." + rowZ + "." + rowWorldId + "." + entityName + "";
                                            Object[] cachedEntity = CacheHandler.entityCache.get(token);

                                            if (cachedEntity != null) {
                                                entityId = (Integer) cachedEntity[1];
                                            }

                                            int xmin = rowX - 5;
                                            int xmax = rowX + 5;
                                            int ymin = rowY - 1;
                                            int ymax = rowY + 1;
                                            int zmin = rowZ - 5;
                                            int zmax = rowZ + 5;

                                            for (Entity entity : block.getChunk().getEntities()) {
                                                if (entityId > -1) {
                                                    int id = entity.getEntityId();
                                                    if (id == entityId) {
                                                        entityCount1++;
                                                        removed = true;
                                                        entity.remove();
                                                        break;
                                                    }
                                                }
                                                else {
                                                    if (entity.getType().equals(Util.getEntityType(oldTypeRaw))) {
                                                        Location entityLocation = entity.getLocation();
                                                        int entityx = entityLocation.getBlockX();
                                                        int entityY = entityLocation.getBlockY();
                                                        int entityZ = entityLocation.getBlockZ();

                                                        if (entityx >= xmin && entityx <= xmax && entityY >= ymin && entityY <= ymax && entityZ >= zmin && entityZ <= zmax) {
                                                            entityCount1++;
                                                            removed = true;
                                                            entity.remove();
                                                            break;
                                                        }
                                                    }
                                                }
                                            }

                                            if (!removed && entityId > -1) {
                                                for (Entity entity : block.getWorld().getLivingEntities()) {
                                                    int id = entity.getEntityId();
                                                    if (id == entityId) {
                                                        entityCount1++;
                                                        removed = true;
                                                        entity.remove();
                                                        break;
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                else {
                                    if (rowType == null) {
                                        continue;
                                    }

                                    String world = Util.getWorldName(rowWorldId);
                                    if (world.length() == 0) {
                                        continue;
                                    }

                                    World bukkitWorld = Bukkit.getServer().getWorld(world);
                                    if (bukkitWorld == null) {
                                        continue;
                                    }
                                    Block block = bukkitWorld.getBlockAt(rowX, rowY, rowZ);
                                    if (!bukkitWorld.isChunkLoaded(block.getChunk())) {
                                        bukkitWorld.getChunkAt(block.getLocation());
                                    }

                                    boolean changeBlock = true;
                                    boolean countBlock = true;
                                    Material changeType = block.getType();
                                    BlockData changeBlockData = block.getBlockData();
                                    BlockData pendingChangeData = chunkChanges.get(block);
                                    Material pendingChangeType = changeType;
                                    if (pendingChangeData != null) {
                                        pendingChangeType = pendingChangeData.getMaterial();
                                    }
                                    else {
                                        pendingChangeData = changeBlockData;
                                    }

                                    if (rowRolledBack == 1 && rollbackType == 0) { // rollback
                                        countBlock = false;
                                    }

                                    if ((rowType == pendingChangeType) && ((!BukkitAdapter.ADAPTER.isItemFrame(oldTypeMaterial)) && (oldTypeMaterial != Material.PAINTING) && (oldTypeMaterial != Material.ARMOR_STAND)) && (oldTypeMaterial != Material.END_CRYSTAL)) {
                                        // block is already changed!
                                        BlockData checkData = rowType == Material.AIR ? blockData : rawBlockData;
                                        if (checkData != null) {
                                            if (checkData.getAsString().equals(pendingChangeData.getAsString()) || checkData instanceof MultipleFacing || checkData instanceof Stairs || checkData instanceof RedstoneWire) {
                                                if (rowType != Material.CHEST && rowType != Material.TRAPPED_CHEST) { // always update double chests
                                                    changeBlock = false;
                                                }
                                            }
                                        }
                                        else if (rowType == Material.AIR) {
                                            changeBlock = false;
                                        }

                                        countBlock = false;
                                    }
                                    else if ((pendingChangeType != Material.AIR) && (pendingChangeType != Material.CAVE_AIR)) {
                                        countBlock = true;
                                    }

                                    if ((pendingChangeType == Material.WATER) && (rowType != Material.AIR) && (rowType != Material.CAVE_AIR) && blockData != null) {
                                        if (blockData instanceof Waterlogged) {
                                            if (Material.WATER.createBlockData().equals(block.getBlockData())) {
                                                Waterlogged waterlogged = (Waterlogged) blockData;
                                                waterlogged.setWaterlogged(true);
                                            }
                                        }
                                    }

                                    try {
                                        if (changeBlock) {
                                            /* If modifying the head of a piston, update the base piston block to prevent it from being destroyed */
                                            if (changeBlockData instanceof PistonHead) {
                                                PistonHead pistonHead = (PistonHead) changeBlockData;
                                                Block pistonBlock = block.getRelative(pistonHead.getFacing().getOppositeFace());
                                                BlockData pistonData = pistonBlock.getBlockData();
                                                if (pistonData instanceof Piston) {
                                                    Piston piston = (Piston) pistonData;
                                                    piston.setExtended(false);
                                                    pistonBlock.setBlockData(piston, false);
                                                }
                                            }
                                            else if (rowType == Material.MOVING_PISTON && blockData instanceof TechnicalPiston && !(blockData instanceof PistonHead)) {
                                                TechnicalPiston technicalPiston = (TechnicalPiston) blockData;
                                                rowType = (technicalPiston.getType() == org.bukkit.block.data.type.TechnicalPiston.Type.STICKY ? Material.STICKY_PISTON : Material.PISTON);
                                                blockData = rowType.createBlockData();
                                                ((Piston) blockData).setFacing(technicalPiston.getFacing());
                                            }

                                            if ((rowType == Material.AIR) && ((BukkitAdapter.ADAPTER.isItemFrame(oldTypeMaterial)) || (oldTypeMaterial == Material.PAINTING))) {
                                                HangingUtil.removeHanging(block.getState(), blockDataString);
                                            }
                                            else if ((BukkitAdapter.ADAPTER.isItemFrame(rowType)) || (rowType == Material.PAINTING)) {
                                                HangingUtil.spawnHanging(block.getState(), rowType, blockDataString, rowData);
                                            }
                                            else if ((rowType == Material.ARMOR_STAND)) {
                                                Location location1 = block.getLocation();
                                                location1.setX(location1.getX() + 0.50);
                                                location1.setZ(location1.getZ() + 0.50);
                                                location1.setYaw(rowData);
                                                boolean exists = false;

                                                for (Entity entity : block.getChunk().getEntities()) {
                                                    if (entity instanceof ArmorStand) {
                                                        if (entity.getLocation().getBlockX() == location1.getBlockX() && entity.getLocation().getBlockY() == location1.getBlockY() && entity.getLocation().getBlockZ() == location1.getBlockZ()) {
                                                            exists = true;
                                                        }
                                                    }
                                                }

                                                if (!exists) {
                                                    Entity entity = block.getLocation().getWorld().spawnEntity(location1, EntityType.ARMOR_STAND);
                                                    PaperAdapter.ADAPTER.teleportAsync(entity, location1);
                                                }
                                            }
                                            else if ((rowType == Material.END_CRYSTAL)) {
                                                Location location1 = block.getLocation();
                                                location1.setX(location1.getX() + 0.50);
                                                location1.setZ(location1.getZ() + 0.50);
                                                boolean exists = false;

                                                for (Entity entity : block.getChunk().getEntities()) {
                                                    if (entity instanceof EnderCrystal) {
                                                        if (entity.getLocation().getBlockX() == location1.getBlockX() && entity.getLocation().getBlockY() == location1.getBlockY() && entity.getLocation().getBlockZ() == location1.getBlockZ()) {
                                                            exists = true;
                                                        }
                                                    }
                                                }

                                                if (!exists) {
                                                    Entity entity = block.getLocation().getWorld().spawnEntity(location1, EntityType.ENDER_CRYSTAL);
                                                    EnderCrystal enderCrystal = (EnderCrystal) entity;
                                                    enderCrystal.setShowingBottom((rowData != 0));
                                                    PaperAdapter.ADAPTER.teleportAsync(entity, location1);
                                                }
                                            }
                                            else if ((rowType == Material.AIR) && ((oldTypeMaterial == Material.WATER))) {
                                                if (pendingChangeData instanceof Waterlogged) {
                                                    Waterlogged waterlogged = (Waterlogged) pendingChangeData;
                                                    waterlogged.setWaterlogged(false);
                                                    Util.prepareTypeAndData(chunkChanges, block, null, waterlogged, false);
                                                }
                                                else {
                                                    Util.prepareTypeAndData(chunkChanges, block, rowType, blockData, true);
                                                }

                                                if (countBlock) {
                                                    blockCount1++;
                                                }
                                            }
                                            else if ((rowType == Material.AIR) && ((oldTypeMaterial == Material.SNOW))) {
                                                Util.prepareTypeAndData(chunkChanges, block, rowType, blockData, true);
                                                if (countBlock) {
                                                    blockCount1++;
                                                }
                                            }
                                            else if ((rowType == Material.AIR) && ((oldTypeMaterial == Material.END_CRYSTAL))) {
                                                for (Entity entity : block.getChunk().getEntities()) {
                                                    if (entity instanceof EnderCrystal) {
                                                        if (entity.getLocation().getBlockX() == rowX && entity.getLocation().getBlockY() == rowY && entity.getLocation().getBlockZ() == rowZ) {
                                                            entity.remove();
                                                        }
                                                    }
                                                }
                                            }
                                            else if (rollbackType == 0 && rowAction == 0 && (rowType == Material.AIR)) {
                                                // broke block ID #0
                                            }
                                            else if ((rowType == Material.AIR) || (rowType == Material.TNT)) {
                                                if (clearInventories) {
                                                    if (BlockGroup.CONTAINERS.contains(changeType)) {
                                                        Inventory inventory = Util.getContainerInventory(block.getState(), false);
                                                        if (inventory != null) {
                                                            inventory.clear();
                                                        }
                                                    }
                                                    else if (BlockGroup.CONTAINERS.contains(Material.ARMOR_STAND)) {
                                                        if ((oldTypeMaterial == Material.ARMOR_STAND)) {
                                                            for (Entity entity : block.getChunk().getEntities()) {
                                                                if (entity instanceof ArmorStand) {
                                                                    Location entityLocation = entity.getLocation();
                                                                    entityLocation.setY(entityLocation.getY() + 0.99);

                                                                    if (entityLocation.getBlockX() == rowX && entityLocation.getBlockY() == rowY && entityLocation.getBlockZ() == rowZ) {
                                                                        EntityEquipment equipment = Util.getEntityEquipment((LivingEntity) entity);
                                                                        if (equipment != null) {
                                                                            equipment.clear();
                                                                        }

                                                                        entityLocation.setY(entityLocation.getY() - 1.99);
                                                                        PaperAdapter.ADAPTER.teleportAsync(entity, entityLocation);
                                                                        entity.remove();
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                }

                                                boolean remove = true;
                                                if ((rowType == Material.AIR)) {
                                                    if (pendingChangeData instanceof Waterlogged) {
                                                        Waterlogged waterlogged = (Waterlogged) pendingChangeData;
                                                        if (waterlogged.isWaterlogged()) {
                                                            Util.prepareTypeAndData(chunkChanges, block, Material.WATER, Material.WATER.createBlockData(), true);
                                                            remove = false;
                                                        }
                                                    }
                                                    else if ((pendingChangeType == Material.WATER)) {
                                                        if (rawBlockData instanceof Waterlogged) {
                                                            Waterlogged waterlogged = (Waterlogged) rawBlockData;
                                                            if (waterlogged.isWaterlogged()) {
                                                                remove = false;
                                                            }
                                                        }
                                                    }
                                                }

                                                if (remove) {
                                                    boolean physics = true;
                                                    if ((changeType == Material.NETHER_PORTAL) || changeBlockData instanceof MultipleFacing || changeBlockData instanceof Snow || changeBlockData instanceof Stairs || changeBlockData instanceof RedstoneWire || changeBlockData instanceof Chest) {
                                                        physics = true;
                                                    }
                                                    else if (changeBlockData instanceof Bisected && !(changeBlockData instanceof TrapDoor)) {
                                                        Bisected bisected = (Bisected) changeBlockData;
                                                        Location bisectLocation = block.getLocation().clone();
                                                        if (bisected.getHalf() == Half.TOP) {
                                                            bisectLocation.setY(bisectLocation.getY() - 1);
                                                        }
                                                        else {
                                                            bisectLocation.setY(bisectLocation.getY() + 1);
                                                        }

                                                        int worldMaxHeight = bukkitWorld.getMaxHeight();
                                                        int worldMinHeight = BukkitAdapter.ADAPTER.getMinHeight(bukkitWorld);
                                                        if (bisectLocation.getBlockY() >= worldMinHeight && bisectLocation.getBlockY() < worldMaxHeight) {
                                                            Block bisectBlock = block.getWorld().getBlockAt(bisectLocation);
                                                            Util.prepareTypeAndData(chunkChanges, bisectBlock, rowType, null, false);

                                                            if (countBlock) {
                                                                blockCount1++;
                                                            }
                                                        }
                                                    }
                                                    else if (changeBlockData instanceof Bed) {
                                                        Bed bed = (Bed) changeBlockData;
                                                        if (bed.getPart() == Part.FOOT) {
                                                            Block adjacentBlock = block.getRelative(bed.getFacing());
                                                            Util.prepareTypeAndData(chunkChanges, adjacentBlock, rowType, null, false);
                                                        }
                                                    }

                                                    Util.prepareTypeAndData(chunkChanges, block, rowType, null, physics);
                                                }

                                                if (countBlock) {
                                                    blockCount1++;
                                                }
                                            }
                                            else if ((rowType == Material.SPAWNER)) {
                                                try {
                                                    Util.prepareTypeAndData(chunkChanges, block, rowType, blockData, false);
                                                    CreatureSpawner mobSpawner = (CreatureSpawner) block.getState();
                                                    mobSpawner.setSpawnedType(Util.getSpawnerType(rowData));
                                                    mobSpawner.update();

                                                    if (countBlock) {
                                                        blockCount1++;
                                                    }
                                                }
                                                catch (Exception e) {
                                                    // e.printStackTrace();
                                                }
                                            }
                                            else if ((rowType == Material.SKELETON_SKULL) || (rowType == Material.SKELETON_WALL_SKULL) || (rowType == Material.WITHER_SKELETON_SKULL) || (rowType == Material.WITHER_SKELETON_WALL_SKULL) || (rowType == Material.ZOMBIE_HEAD) || (rowType == Material.ZOMBIE_WALL_HEAD) || (rowType == Material.PLAYER_HEAD) || (rowType == Material.PLAYER_WALL_HEAD) || (rowType == Material.CREEPER_HEAD) || (rowType == Material.CREEPER_WALL_HEAD) || (rowType == Material.DRAGON_HEAD) || (rowType == Material.DRAGON_WALL_HEAD)) { // skull
                                                Util.prepareTypeAndData(chunkChanges, block, rowType, blockData, false);
                                                if (rowData > 0) {
                                                    Queue.queueSkullUpdate(rowUser, block.getState(), rowData);
                                                }

                                                if (countBlock) {
                                                    blockCount1++;
                                                }
                                            }
                                            else if (BukkitAdapter.ADAPTER.isSign(rowType)) {// sign
                                                Util.prepareTypeAndData(chunkChanges, block, rowType, blockData, false);
                                                Queue.queueSignUpdate(rowUser, block.getState(), rollbackType, rowTime);

                                                if (countBlock) {
                                                    blockCount1++;
                                                }
                                            }
                                            else if (BlockGroup.SHULKER_BOXES.contains(rowType)) {
                                                Util.prepareTypeAndData(chunkChanges, block, rowType, blockData, false);
                                                if (countBlock) {
                                                    blockCount1++;
                                                }
                                                if (meta != null) {
                                                    Inventory inventory = Util.getContainerInventory(block.getState(), false);
                                                    for (Object value : meta) {
                                                        ItemStack item = Util.unserializeItemStackLegacy(value);
                                                        if (item != null) {
                                                            modifyContainerItems(rowType, inventory, 0, item, 1);
                                                        }
                                                    }
                                                }
                                            }
                                            else if (rowType == Material.COMMAND_BLOCK || rowType == Material.REPEATING_COMMAND_BLOCK || rowType == Material.CHAIN_COMMAND_BLOCK) { // command block
                                                Util.prepareTypeAndData(chunkChanges, block, rowType, blockData, false);
                                                if (countBlock) {
                                                    blockCount1++;
                                                }

                                                if (meta != null) {
                                                    CommandBlock commandBlock = (CommandBlock) block.getState();
                                                    for (Object value : meta) {
                                                        if (value instanceof String) {
                                                            String string = (String) value;
                                                            commandBlock.setCommand(string);
                                                            commandBlock.update();
                                                        }
                                                    }
                                                }
                                            }
                                            else if ((rowType == Material.WATER)) {
                                                if (pendingChangeData instanceof Waterlogged) {
                                                    Waterlogged waterlogged = (Waterlogged) pendingChangeData;
                                                    waterlogged.setWaterlogged(true);
                                                    Util.prepareTypeAndData(chunkChanges, block, null, waterlogged, false);
                                                }
                                                else {
                                                    Util.prepareTypeAndData(chunkChanges, block, rowType, blockData, false);
                                                }

                                                if (countBlock) {
                                                    blockCount1++;
                                                }
                                            }
                                            else if ((rowType == Material.NETHER_PORTAL) && rowAction == 0) {
                                                Util.prepareTypeAndData(chunkChanges, block, Material.FIRE, null, true);
                                            }
                                            else if (blockData == null && rowData > 0 && (rowType == Material.IRON_DOOR || BlockGroup.DOORS.contains(rowType))) {
                                                if (countBlock) {
                                                    blockCount1++;
                                                }

                                                block.setType(rowType, false);
                                                Door door = (Door) block.getBlockData();
                                                if (rowData >= 8) {
                                                    door.setHalf(Half.TOP);
                                                    rowData = rowData - 8;
                                                }
                                                else {
                                                    door.setHalf(Half.BOTTOM);
                                                }
                                                if (rowData >= 4) {
                                                    door.setHinge(Hinge.RIGHT);
                                                    rowData = rowData - 4;
                                                }
                                                else {
                                                    door.setHinge(Hinge.LEFT);
                                                }
                                                BlockFace face = BlockFace.NORTH;

                                                switch (rowData) {
                                                    case 0:
                                                        face = BlockFace.EAST;
                                                        break;
                                                    case 1:
                                                        face = BlockFace.SOUTH;
                                                        break;
                                                    case 2:
                                                        face = BlockFace.WEST;
                                                        break;
                                                }

                                                door.setFacing(face);
                                                door.setOpen(false);
                                                block.setBlockData(door, false);
                                            }
                                            else if (blockData == null && rowData > 0 && (rowType.name().endsWith("_BED"))) {
                                                if (countBlock) {
                                                    blockCount1++;
                                                }

                                                block.setType(rowType, false);
                                                Bed bed = (Bed) block.getBlockData();
                                                BlockFace face = BlockFace.NORTH;

                                                if (rowData > 4) {
                                                    bed.setPart(Part.HEAD);
                                                    rowData = rowData - 4;
                                                }

                                                switch (rowData) {
                                                    case 2:
                                                        face = BlockFace.WEST;
                                                        break;
                                                    case 3:
                                                        face = BlockFace.EAST;
                                                        break;
                                                    case 4:
                                                        face = BlockFace.SOUTH;
                                                        break;
                                                }

                                                bed.setFacing(face);
                                                block.setBlockData(bed, false);
                                            }
                                            else if (rowType.name().endsWith("_BANNER")) {
                                                Util.prepareTypeAndData(chunkChanges, block, rowType, blockData, false);
                                                if (countBlock) {
                                                    blockCount1++;
                                                }

                                                if (meta != null) {
                                                    Banner banner = (Banner) block.getState();

                                                    for (Object value : meta) {
                                                        if (value instanceof DyeColor) {
                                                            banner.setBaseColor((DyeColor) value);
                                                        }
                                                        else if (value instanceof Map) {
                                                            @SuppressWarnings("unchecked")
                                                            Pattern pattern = new Pattern((Map<String, Object>) value);
                                                            banner.addPattern(pattern);
                                                        }
                                                    }

                                                    banner.update();
                                                }
                                            }
                                            else if (rowType != changeType && (BlockGroup.CONTAINERS.contains(rowType) || BlockGroup.CONTAINERS.contains(changeType))) {
                                                block.setType(Material.AIR); // Clear existing container to prevent errors

                                                boolean isChest = (blockData instanceof Chest);
                                                Util.prepareTypeAndData(chunkChanges, block, rowType, blockData, (isChest));
                                                if (isChest) {
                                                    ChestTool.updateDoubleChest(block, blockData, false);
                                                }

                                                if (countBlock) {
                                                    blockCount1++;
                                                }
                                            }
                                            else if (BlockGroup.UPDATE_STATE.contains(rowType) || rowType.name().contains("CANDLE")) {
                                                Util.prepareTypeAndData(chunkChanges, block, rowType, blockData, true);
                                                ChestTool.updateDoubleChest(block, blockData, true);
                                                if (countBlock) {
                                                    blockCount1++;
                                                }
                                            }
                                            else if (rowType != Material.AIR && rawBlockData instanceof Bisected && !(rawBlockData instanceof Stairs || rawBlockData instanceof TrapDoor)) {
                                                Bisected bisected = (Bisected) rawBlockData;
                                                Bisected bisectData = (Bisected) rawBlockData.clone();
                                                Location bisectLocation = block.getLocation().clone();
                                                if (bisected.getHalf() == Half.TOP) {
                                                    bisectData.setHalf(Half.BOTTOM);
                                                    bisectLocation.setY(bisectLocation.getY() - 1);
                                                }
                                                else {
                                                    bisectData.setHalf(Half.TOP);
                                                    bisectLocation.setY(bisectLocation.getY() + 1);
                                                }

                                                int worldMaxHeight = bukkitWorld.getMaxHeight();
                                                int worldMinHeight = BukkitAdapter.ADAPTER.getMinHeight(bukkitWorld);
                                                if (bisectLocation.getBlockY() >= worldMinHeight && bisectLocation.getBlockY() < worldMaxHeight) {
                                                    Block bisectBlock = block.getWorld().getBlockAt(bisectLocation);
                                                    Util.prepareTypeAndData(chunkChanges, bisectBlock, rowType, bisectData, false);
                                                }

                                                Util.prepareTypeAndData(chunkChanges, block, rowType, blockData, false);
                                                if (countBlock) {
                                                    blockCount1++;
                                                    blockCount1++;
                                                }
                                            }
                                            else if (rowType != Material.AIR && rawBlockData instanceof Bed) {
                                                Bed bed = (Bed) rawBlockData;
                                                if (bed.getPart() == Part.FOOT) {
                                                    Block adjacentBlock = block.getRelative(bed.getFacing());
                                                    Bed bedData = (Bed) rawBlockData.clone();
                                                    bedData.setPart(Part.HEAD);
                                                    Util.prepareTypeAndData(chunkChanges, adjacentBlock, rowType, bedData, false);
                                                    if (countBlock) {
                                                        blockCount1++;
                                                    }
                                                }

                                                Util.prepareTypeAndData(chunkChanges, block, rowType, blockData, true);
                                                if (countBlock) {
                                                    blockCount1++;
                                                }
                                            }
                                            else {
                                                boolean physics = true;
                                                /*
                                                if (blockData instanceof MultipleFacing || BukkitAdapter.ADAPTER.isWall(blockData) || blockData instanceof Snow || blockData instanceof Stairs || blockData instanceof RedstoneWire || blockData instanceof Chest) {
                                                physics = !(blockData instanceof Snow) || block.getY() <= BukkitAdapter.ADAPTER.getMinHeight(block.getWorld()) || (block.getWorld().getBlockAt(block.getX(), block.getY() - 1, block.getZ()).getType().equals(Material.GRASS_BLOCK));
                                                }
                                                */

                                                Util.prepareTypeAndData(chunkChanges, block, rowType, blockData, physics);
                                                if (countBlock) {
                                                    blockCount1++;
                                                }
                                            }
                                        }
                                    }
                                    catch (Exception e) {
                                        e.printStackTrace();
                                    }

                                    if ((rowType != Material.AIR) && changeBlock) {
                                        if (rowUser.length() > 0) {
                                            CacheHandler.lookupCache.put(rowX + "." + rowY + "." + rowZ + "." + rowWorldId, new Object[] { unixtimestamp, rowUser, rowType });
                                        }
                                    }
                                }

                                // count++;
                                ConfigHandler.rollbackHash.put(finalUserString, new int[] { itemCount1, blockCount1, entityCount1, 0, scannedWorlds });
                            }
                            data.clear();

                            // Apply cached changes
                            for (Entry<Block, BlockData> chunkChange : chunkChanges.entrySet()) {
                                Block changeBlock = chunkChange.getKey();
                                BlockData changeBlockData = chunkChange.getValue();
                                if (preview > 0) {
                                    Util.sendBlockChange((Player) finalUser, changeBlock.getLocation(), changeBlockData);
                                }
                                else {
                                    Util.setTypeAndData(changeBlock, null, changeBlockData, true);
                                }
                            }
                            chunkChanges.clear();

                            Map<Player, List<Integer>> sortPlayers = new HashMap<>();
                            Object container = null;
                            Material containerType = null;
                            boolean containerInit = false;
                            int lastX = 0;
                            int lastY = 0;
                            int lastZ = 0;
                            int lastWorldId = 0;
                            String lastFace = "";

                            for (Object[] row : itemData) {
                                int[] rollbackHashData1 = ConfigHandler.rollbackHash.get(finalUserString);
                                int itemCount1 = rollbackHashData1[0];
                                int blockCount1 = rollbackHashData1[1];
                                int entityCount1 = rollbackHashData1[2];
                                int scannedWorlds = rollbackHashData1[4];
                                int rowX = (Integer) row[3];
                                int rowY = (Integer) row[4];
                                int rowZ = (Integer) row[5];
                                int rowTypeRaw = (Integer) row[6];
                                int rowData = (Integer) row[7];
                                int rowAction = (Integer) row[8];
                                int rowRolledBack = Util.rolledBack((Integer) row[9], false);
                                int rowWorldId = (Integer) row[10];
                                int rowAmount = (Integer) row[11];
                                byte[] rowMetadata = (byte[]) row[12];
                                Material rowType = Util.getType(rowTypeRaw);

                                int rolledBackInventory = Util.rolledBack((Integer) row[9], true);
                                if (rowType != null) {
                                    if (inventoryRollback && ((rollbackType == 0 && rolledBackInventory == 0) || (rollbackType == 1 && rolledBackInventory == 1))) {
                                        Material inventoryItem = Util.itemFilter(rowType, ((Integer) row[14] == 0));
                                        int rowUserId = (Integer) row[2];
                                        String rowUser = ConfigHandler.playerIdCacheReversed.get(rowUserId);
                                        if (rowUser == null) {
                                            continue;
                                        }

                                        String uuid = ConfigHandler.uuidCache.get(rowUser.toLowerCase(Locale.ROOT));
                                        if (uuid == null) {
                                            continue;
                                        }

                                        Player player = Bukkit.getServer().getPlayer(UUID.fromString(uuid));
                                        if (player == null) {
                                            continue;
                                        }

                                        int inventoryAction = 0;
                                        if (rowAction == ItemLogger.ITEM_DROP || rowAction == ItemLogger.ITEM_PICKUP || rowAction == ItemLogger.ITEM_THROW || rowAction == ItemLogger.ITEM_SHOOT || rowAction == ItemLogger.ITEM_BREAK || rowAction == ItemLogger.ITEM_DESTROY || rowAction == ItemLogger.ITEM_CREATE || rowAction == ItemLogger.ITEM_SELL || rowAction == ItemLogger.ITEM_BUY) {
                                            inventoryAction = ((rowAction == ItemLogger.ITEM_PICKUP || rowAction == ItemLogger.ITEM_CREATE || rowAction == ItemLogger.ITEM_BUY) ? 1 : 0);
                                        }
                                        else if (rowAction == ItemLogger.ITEM_REMOVE_ENDER || rowAction == ItemLogger.ITEM_ADD_ENDER) {
                                            inventoryAction = (rowAction == ItemLogger.ITEM_REMOVE_ENDER ? 1 : 0);
                                        }
                                        else {
                                            inventoryAction = (rowAction == ItemLogger.ITEM_REMOVE ? 1 : 0);
                                        }

                                        int action = rollbackType == 0 ? (inventoryAction ^ 1) : inventoryAction;
                                        ItemStack itemstack = new ItemStack(inventoryItem, rowAmount);
                                        Object[] populatedStack = populateItemStack(itemstack, rowMetadata);
                                        if (rowAction == ItemLogger.ITEM_REMOVE_ENDER || rowAction == ItemLogger.ITEM_ADD_ENDER) {
                                            modifyContainerItems(containerType, player.getEnderChest(), (Integer) populatedStack[0], ((ItemStack) populatedStack[2]).clone(), action ^ 1);
                                        }
                                        int modifiedArmor = modifyContainerItems(containerType, player.getInventory(), (Integer) populatedStack[0], (ItemStack) populatedStack[2], action);
                                        if (modifiedArmor > -1) {
                                            List<Integer> currentSortList = sortPlayers.getOrDefault(player, new ArrayList<>());
                                            if (!currentSortList.contains(modifiedArmor)) {
                                                currentSortList.add(modifiedArmor);
                                            }
                                            sortPlayers.put(player, currentSortList);
                                        }

                                        itemCount1 = itemCount1 + rowAmount;
                                        ConfigHandler.rollbackHash.put(finalUserString, new int[] { itemCount1, blockCount1, entityCount1, 0, scannedWorlds });
                                        continue; // remove this for merged rollbacks in future? (be sure to re-enable chunk sorting)
                                    }

                                    if (inventoryRollback || rowAction > 1) {
                                        continue; // skip inventory & ender chest transactions
                                    }

                                    if ((rollbackType == 0 && rowRolledBack == 0) || (rollbackType == 1 && rowRolledBack == 1)) {
                                        ItemStack itemstack = new ItemStack(rowType, rowAmount);
                                        Object[] populatedStack = populateItemStack(itemstack, rowMetadata);
                                        String faceData = (String) populatedStack[1];

                                        if (!containerInit || rowX != lastX || rowY != lastY || rowZ != lastZ || rowWorldId != lastWorldId || !faceData.equals(lastFace)) {
                                            container = null; // container patch 2.14.0
                                            String world = Util.getWorldName(rowWorldId);
                                            if (world.length() == 0) {
                                                continue;
                                            }

                                            World bukkitWorld = Bukkit.getServer().getWorld(world);
                                            if (bukkitWorld == null) {
                                                continue;
                                            }
                                            Block block = bukkitWorld.getBlockAt(rowX, rowY, rowZ);
                                            if (!bukkitWorld.isChunkLoaded(block.getChunk())) {
                                                bukkitWorld.getChunkAt(block.getLocation());
                                            }

                                            if (BlockGroup.CONTAINERS.contains(block.getType())) {
                                                BlockState blockState = block.getState();
                                                if (blockState instanceof Jukebox) {
                                                    container = blockState;
                                                }
                                                else {
                                                    container = Util.getContainerInventory(blockState, false);
                                                }

                                                containerType = block.getType();
                                            }
                                            else if (BlockGroup.CONTAINERS.contains(Material.ARMOR_STAND) || BlockGroup.CONTAINERS.contains(Material.ITEM_FRAME)) {
                                                for (Entity entity : block.getChunk().getEntities()) {
                                                    if (entity.getLocation().getBlockX() == rowX && entity.getLocation().getBlockY() == rowY && entity.getLocation().getBlockZ() == rowZ) {
                                                        if (entity instanceof ArmorStand) {
                                                            container = Util.getEntityEquipment((LivingEntity) entity);
                                                            containerType = Material.ARMOR_STAND;
                                                        }
                                                        else if (entity instanceof ItemFrame) {
                                                            container = entity;
                                                            containerType = Material.ITEM_FRAME;
                                                            if (faceData.length() > 0 && (BlockFace.valueOf(faceData) == ((ItemFrame) entity).getFacing())) {
                                                                break;
                                                            }
                                                        }
                                                    }
                                                }
                                            }

                                            lastX = rowX;
                                            lastY = rowY;
                                            lastZ = rowZ;
                                            lastWorldId = rowWorldId;
                                            lastFace = faceData;
                                        }

                                        if (container != null) {
                                            int action = 0;
                                            if (rollbackType == 0 && rowAction == 0) {
                                                action = 1;
                                            }

                                            if (rollbackType == 1 && rowAction == 1) {
                                                action = 1;
                                            }

                                            int slot = (Integer) populatedStack[0];
                                            itemstack = (ItemStack) populatedStack[2];

                                            modifyContainerItems(containerType, container, slot, itemstack, action);
                                            itemCount1 = itemCount1 + rowAmount;
                                        }
                                        containerInit = true;
                                    }
                                }

                                ConfigHandler.rollbackHash.put(finalUserString, new int[] { itemCount1, blockCount1, entityCount1, 0, scannedWorlds });
                            }
                            itemData.clear();

                            for (Entry<Player, List<Integer>> sortEntry : sortPlayers.entrySet()) {
                                sortContainerItems(sortEntry.getKey().getInventory(), sortEntry.getValue());
                            }
                            sortPlayers.clear();

                            int[] rollbackHashData1 = ConfigHandler.rollbackHash.get(finalUserString);
                            int itemCount1 = rollbackHashData1[0];
                            int blockCount1 = rollbackHashData1[1];
                            int entityCount1 = rollbackHashData1[2];
                            int scannedWorlds = rollbackHashData1[4];
                            ConfigHandler.rollbackHash.put(finalUserString, new int[] { itemCount1, blockCount1, entityCount1, 1, (scannedWorlds + 1) });

                            // Teleport players out of danger if they're within this chunk
                            if (preview == 0) {
                                for (Player player : Bukkit.getOnlinePlayers()) {
                                    Location playerLocation = player.getLocation();
                                    int chunkX = playerLocation.getBlockX() >> 4;
                                    int chunkZ = playerLocation.getBlockZ() >> 4;

                                    if (chunkX == finalChunkX && chunkZ == finalChunkZ) {
                                        Teleport.performSafeTeleport(player, playerLocation, false);
                                    }
                                }
                            }
                        }
                        catch (Exception e) {
                            e.printStackTrace();
                            int[] rollbackHashData1 = ConfigHandler.rollbackHash.get(finalUserString);
                            int itemCount1 = rollbackHashData1[0];
                            int blockCount1 = rollbackHashData1[1];
                            int entityCount1 = rollbackHashData1[2];
                            int scannedWorlds = rollbackHashData1[4];

                            ConfigHandler.rollbackHash.put(finalUserString, new int[] { itemCount1, blockCount1, entityCount1, 2, (scannedWorlds + 1) });
                        }
                    }, chunkLocation, 0);
                }

                rollbackHashData = ConfigHandler.rollbackHash.get(finalUserString);
                int next = rollbackHashData[3];
                int scannedWorlds = rollbackHashData[4];
                int sleepTime = 0;
                int abort = 0;

                while (next == 0 || scannedWorlds < worldMap.size()) {
                    if (preview == 1) {
                        // Not actually changing blocks, so less intensive.
                        sleepTime = sleepTime + 1;
                        Thread.sleep(1);
                    }
                    else {
                        sleepTime = sleepTime + 5;
                        Thread.sleep(5);
                    }

                    rollbackHashData = ConfigHandler.rollbackHash.get(finalUserString);
                    next = rollbackHashData[3];
                    scannedWorlds = rollbackHashData[4];

                    if (sleepTime > 300000) {
                        abort = 1;
                        break;
                    }
                }

                if (abort == 1 || next == 2) {
                    Chat.console(Phrase.build(Phrase.ROLLBACK_ABORTED));
                    break;
                }

                rollbackHashData = ConfigHandler.rollbackHash.get(finalUserString);
                itemCount = rollbackHashData[0];
                blockCount = rollbackHashData[1];
                entityCount = rollbackHashData[2];
                ConfigHandler.rollbackHash.put(finalUserString, new int[] { itemCount, blockCount, entityCount, 0, 0 });

                if (verbose && user != null && preview == 0 && !actionList.contains(11)) {
                    Integer chunks = chunkList.size();
                    Chat.sendMessage(user, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.ROLLBACK_CHUNKS_MODIFIED, chunkCount.toString(), chunks.toString(), (chunks == 1 ? Selector.FIRST : Selector.SECOND)));
                }
            }

            chunkList.clear();
            dataList.clear();
            itemDataList.clear();

            int[] rollbackHashData = ConfigHandler.rollbackHash.get(finalUserString);
            int itemCount = rollbackHashData[0];
            int blockCount = rollbackHashData[1];
            int entityCount = rollbackHashData[2];
            long timeFinish = System.currentTimeMillis();
            double totalSeconds = (timeFinish - timeStart) / 1000.0;

            if (user != null) {
                finishRollbackRestore(user, location, checkUsers, restrictList, excludeList, excludeUserList, actionList, timeString, chunkCount, totalSeconds, itemCount, blockCount, entityCount, rollbackType, radius, verbose, restrictWorld, preview);
            }

            list = Lookup.convertRawLookup(statement, lookupList);
            return list;
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    static void finishRollbackRestore(CommandSender user, Location location, List<String> checkUsers, List<Object> restrictList, Map<Object, Boolean> excludeList, List<String> excludeUserList, List<Integer> actionList, String timeString, Integer chunkCount, Double seconds, Integer itemCount, Integer blockCount, Integer entityCount, int rollbackType, Integer[] radius, boolean verbose, boolean restrictWorld, int preview) {
        try {
            if (preview == 2) {
                Chat.sendMessage(user, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.PREVIEW_CANCELLED));
                return;
            }

            Chat.sendMessage(user, "-----");

            StringBuilder usersBuilder = new StringBuilder();
            for (String value : checkUsers) {
                if (usersBuilder.length() == 0) {
                    usersBuilder = usersBuilder.append("" + value + "");
                }
                else {
                    usersBuilder.append(", ").append(value);
                }
            }
            String users = usersBuilder.toString();

            if (users.equals("#global") && restrictWorld) {
                users = "#" + location.getWorld().getName();
            }

            if (preview > 0) {
                Chat.sendMessage(user, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.ROLLBACK_COMPLETED, users, Selector.THIRD)); // preview
            }
            else if (rollbackType == 0) {
                Chat.sendMessage(user, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.ROLLBACK_COMPLETED, users, Selector.FIRST)); // rollback
            }
            else if (rollbackType == 1) {
                Chat.sendMessage(user, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.ROLLBACK_COMPLETED, users, Selector.SECOND)); // restore
            }

            if (preview == 1 || rollbackType == 0 || rollbackType == 1) {
                Chat.sendMessage(user, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.ROLLBACK_TIME, timeString));
            }

            if (radius != null) {
                int worldedit = radius[7];
                if (worldedit == 0) {
                    Integer rollbackRadius = radius[0];
                    Chat.sendMessage(user, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.ROLLBACK_RADIUS, rollbackRadius.toString(), (rollbackRadius == 1 ? Selector.FIRST : Selector.SECOND)));
                }
                else {
                    Chat.sendMessage(user, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.ROLLBACK_SELECTION, "#worldedit"));
                }
            }

            if (restrictWorld && radius == null) {
                if (location != null) {
                    Chat.sendMessage(user, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.ROLLBACK_WORLD_ACTION, location.getWorld().getName(), Selector.FIRST));
                }
            }

            if (actionList.contains(4) && actionList.contains(11)) {
                if (actionList.contains(0)) {
                    Chat.sendMessage(user, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.ROLLBACK_WORLD_ACTION, "+inventory", Selector.SECOND));
                }
                else if (actionList.contains(1)) {
                    Chat.sendMessage(user, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.ROLLBACK_WORLD_ACTION, "-inventory", Selector.SECOND));
                }
                else {
                    Chat.sendMessage(user, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.ROLLBACK_WORLD_ACTION, "inventory", Selector.SECOND));
                }
            }
            else if (actionList.contains(4)) {
                if (actionList.contains(0)) {
                    Chat.sendMessage(user, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.ROLLBACK_WORLD_ACTION, "-container", Selector.SECOND));
                }
                else if (actionList.contains(1)) {
                    Chat.sendMessage(user, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.ROLLBACK_WORLD_ACTION, "+container", Selector.SECOND));
                }
                else {
                    Chat.sendMessage(user, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.ROLLBACK_WORLD_ACTION, "container", Selector.SECOND));
                }
            }
            else if (actionList.contains(0) && actionList.contains(1)) {
                Chat.sendMessage(user, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.ROLLBACK_WORLD_ACTION, "block", Selector.SECOND));
            }
            else if (actionList.contains(0)) {
                Chat.sendMessage(user, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.ROLLBACK_WORLD_ACTION, "-block", Selector.SECOND));
            }
            else if (actionList.contains(1)) {
                Chat.sendMessage(user, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.ROLLBACK_WORLD_ACTION, "+block", Selector.SECOND));
            }
            else if (actionList.contains(3)) {
                Chat.sendMessage(user, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.ROLLBACK_WORLD_ACTION, "kill", Selector.SECOND));
            }

            if (restrictList.size() > 0) {
                StringBuilder restrictTargets = new StringBuilder();
                boolean material = false;
                boolean item = false;
                boolean entity = false;

                int targetCount = 0;
                for (Object restrictTarget : restrictList) {
                    String targetName = "";

                    if (restrictTarget instanceof Material) {
                        targetName = ((Material) restrictTarget).name().toLowerCase(Locale.ROOT);
                        item = (!item ? !(((Material) restrictTarget).isBlock()) : item);
                        material = true;
                    }
                    else if (restrictTarget instanceof EntityType) {
                        targetName = ((EntityType) restrictTarget).name().toLowerCase(Locale.ROOT);
                        entity = true;
                    }

                    if (targetCount == 0) {
                        restrictTargets = restrictTargets.append("" + targetName + "");
                    }
                    else {
                        restrictTargets.append(", ").append(targetName);
                    }

                    targetCount++;
                }

                String targetType = Selector.THIRD;
                if (material && !item && !entity) {
                    targetType = Selector.FIRST;
                }
                else if (material && item && !entity) {
                    targetType = Selector.THIRD;
                }
                else if (entity && !material) {
                    targetType = Selector.SECOND;
                }

                Chat.sendMessage(user, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.ROLLBACK_INCLUDE, restrictTargets.toString(), Selector.FIRST, targetType, (targetCount == 1 ? Selector.FIRST : Selector.SECOND))); // include
            }

            if (excludeList.size() > 0) {
                StringBuilder excludeTargets = new StringBuilder();
                boolean material = false;
                boolean item = false;
                boolean entity = false;

                int excludeCount = 0;
                for (Map.Entry<Object, Boolean> entry : excludeList.entrySet()) {
                    Object excludeTarget = entry.getKey();
                    Boolean excludeTargetInternal = entry.getValue();

                    // don't display default block excludes
                    if (Boolean.TRUE.equals(excludeTargetInternal)) {
                        continue;
                    }

                    // don't display that excluded water/fire/farmland in inventory rollbacks
                    if (actionList.contains(4) && actionList.contains(11)) {
                        if (excludeTarget.equals(Material.FIRE) || excludeTarget.equals(Material.WATER) || excludeTarget.equals(Material.FARMLAND)) {
                            continue;
                        }
                    }

                    String targetName = "";
                    if (excludeTarget instanceof Material) {
                        targetName = ((Material) excludeTarget).name().toLowerCase(Locale.ROOT);
                        item = (!item ? !(((Material) excludeTarget).isBlock()) : item);
                        material = true;
                    }
                    else if (excludeTarget instanceof EntityType) {
                        targetName = ((EntityType) excludeTarget).name().toLowerCase(Locale.ROOT);
                        entity = true;
                    }

                    if (excludeCount == 0) {
                        excludeTargets = excludeTargets.append("" + targetName + "");
                    }
                    else {
                        excludeTargets.append(", ").append(targetName);
                    }

                    excludeCount++;
                }

                String targetType = Selector.THIRD;
                if (material && !item && !entity) {
                    targetType = Selector.FIRST;
                }
                else if (material && item && !entity) {
                    targetType = Selector.THIRD;
                }
                else if (entity && !material) {
                    targetType = Selector.SECOND;
                }

                if (excludeCount > 0) {
                    Chat.sendMessage(user, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.ROLLBACK_INCLUDE, excludeTargets.toString(), Selector.SECOND, targetType, (excludeCount == 1 ? Selector.FIRST : Selector.SECOND))); // exclude
                }
            }

            if (excludeUserList.size() > 0) {
                StringBuilder excludeUsers = new StringBuilder();

                int excludeCount = 0;
                for (String excludeUser : excludeUserList) {
                    // don't display that excluded #hopper in inventory rollbacks
                    if (actionList.contains(4) && actionList.contains(11)) {
                        if (excludeUser.equals("#hopper")) {
                            continue;
                        }
                    }

                    if (excludeCount == 0) {
                        excludeUsers = excludeUsers.append("" + excludeUser + "");
                    }
                    else {
                        excludeUsers.append(", ").append(excludeUser);
                    }

                    excludeCount++;
                }

                if (excludeCount > 0) {
                    Chat.sendMessage(user, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.ROLLBACK_EXCLUDED_USERS, excludeUsers.toString(), (excludeCount == 1 ? Selector.FIRST : Selector.SECOND)));
                }
            }

            StringBuilder modifiedData = new StringBuilder();
            Integer modifyCount = 0;
            if (actionList.contains(5)) {
                modifiedData = modifiedData.append(Phrase.build(Phrase.AMOUNT_ITEM, NumberFormat.getInstance().format(blockCount), (blockCount == 1 ? Selector.FIRST : Selector.SECOND)));
                modifyCount++;
            }
            else {
                if (itemCount > 0 || actionList.contains(4)) {
                    modifiedData = modifiedData.append(Phrase.build(Phrase.AMOUNT_ITEM, NumberFormat.getInstance().format(itemCount), (itemCount == 1 ? Selector.FIRST : Selector.SECOND)));
                    modifyCount++;
                }

                if (entityCount > 0) {
                    if (modifyCount > 0) {
                        modifiedData.append(", ");
                    }
                    modifiedData.append(Phrase.build(Phrase.AMOUNT_ENTITY, NumberFormat.getInstance().format(entityCount), (entityCount == 1 ? Selector.FIRST : Selector.SECOND)));
                    modifyCount++;
                }

                if (blockCount > 0 || !actionList.contains(4) || preview > 0) {
                    if (modifyCount > 0) {
                        modifiedData.append(", ");
                    }
                    modifiedData.append(Phrase.build(Phrase.AMOUNT_BLOCK, NumberFormat.getInstance().format(blockCount), (blockCount == 1 ? Selector.FIRST : Selector.SECOND)));
                    modifyCount++;
                }
            }

            StringBuilder modifiedDataVerbose = new StringBuilder();
            if (verbose && preview == 0 && !actionList.contains(11)) {
                if (chunkCount > -1 && modifyCount < 3) {
                    if (modifyCount > 0) {
                        modifiedData.append(", ");
                    }
                    modifiedData.append(Phrase.build(Phrase.AMOUNT_CHUNK, NumberFormat.getInstance().format(chunkCount), (chunkCount == 1 ? Selector.FIRST : Selector.SECOND)));
                    modifyCount++;
                }
                else if (chunkCount > 1) {
                    modifiedDataVerbose.append(Phrase.build(Phrase.AMOUNT_CHUNK, NumberFormat.getInstance().format(chunkCount), (chunkCount == 1 ? Selector.FIRST : Selector.SECOND)));
                }
            }

            Chat.sendMessage(user, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.ROLLBACK_MODIFIED, modifiedData.toString(), (preview == 0 ? Selector.FIRST : Selector.SECOND)));
            if (modifiedDataVerbose.length() > 0) {
                Chat.sendMessage(user, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.ROLLBACK_MODIFIED, modifiedDataVerbose.toString(), (preview == 0 ? Selector.FIRST : Selector.SECOND)));
            }

            if (preview == 0) {
                BigDecimal decimalSeconds = new BigDecimal(seconds).setScale(1, RoundingMode.HALF_EVEN);
                Chat.sendMessage(user, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.ROLLBACK_LENGTH, decimalSeconds.stripTrailingZeros().toPlainString(), (decimalSeconds.doubleValue() == 1 ? Selector.FIRST : Selector.SECOND)));
            }

            Chat.sendMessage(user, "-----");
            if (preview > 0) {
                Chat.sendMessage(user, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.PLEASE_SELECT, "/co apply", "/co cancel"));
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    static int modifyContainerItems(Material type, Object container, int slot, ItemStack itemstack, int action) {
        int modifiedArmor = -1;
        try {
            ItemStack[] contents = null;

            if (type != null && type.equals(Material.ARMOR_STAND)) {
                EntityEquipment equipment = (EntityEquipment) container;
                if (equipment != null) {
                    if (action == 1) {
                        itemstack.setAmount(1);
                    }
                    else {
                        itemstack.setType(Material.AIR);
                        itemstack.setAmount(0);
                    }

                    if (slot < 4) {
                        contents = equipment.getArmorContents();
                        if (slot >= 0) {
                            contents[slot] = itemstack;
                        }
                        equipment.setArmorContents(contents);
                    }
                    else {
                        ArmorStand armorStand = (ArmorStand) equipment.getHolder();
                        armorStand.setArms(true);
                        switch (slot) {
                            case 4:
                                equipment.setItemInMainHand(itemstack);
                                break;
                            case 5:
                                equipment.setItemInOffHand(itemstack);
                                break;
                        }
                    }
                }
            }
            else if (type != null && type.equals(Material.ITEM_FRAME)) {
                ItemFrame frame = (ItemFrame) container;
                if (frame != null) {
                    if (action == 1) {
                        itemstack.setAmount(1);
                    }
                    else {
                        itemstack.setType(Material.AIR);
                        itemstack.setAmount(0);
                    }

                    frame.setItem(itemstack);
                }
            }
            else if (type != null && type.equals(Material.JUKEBOX)) {
                Jukebox jukebox = (Jukebox) container;
                if (jukebox != null) {
                    if (action == 1 && Tag.ITEMS_MUSIC_DISCS.isTagged(itemstack.getType())) {
                        itemstack.setAmount(1);
                    }
                    else {
                        itemstack.setType(Material.AIR);
                        itemstack.setAmount(0);
                    }

                    jukebox.setRecord(itemstack);
                    jukebox.update();
                }
            }
            else {
                Inventory inventory = (Inventory) container;
                if (inventory != null) {
                    boolean isPlayerInventory = (inventory instanceof PlayerInventory);
                    if (action == 1) {
                        int count = 0;
                        int amount = itemstack.getAmount();
                        itemstack.setAmount(1);

                        while (count < amount) {
                            boolean addedItem = false;
                            if (isPlayerInventory) {
                                int setArmor = Util.setPlayerArmor((PlayerInventory) inventory, itemstack);
                                addedItem = (setArmor > -1);
                                modifiedArmor = addedItem ? setArmor : modifiedArmor;
                            }
                            if (!addedItem) {
                                if (BukkitAdapter.ADAPTER.isChiseledBookshelf(type)) {
                                    ItemStack[] inventoryContents = inventory.getStorageContents();
                                    int i = 0;
                                    for (ItemStack stack : inventoryContents) {
                                        if (stack == null) {
                                            inventoryContents[i] = itemstack;
                                            addedItem = true;
                                            break;
                                        }
                                        i++;
                                    }
                                    if (addedItem) {
                                        inventory.setStorageContents(inventoryContents);
                                    }
                                    else {
                                        addedItem = (inventory.addItem(itemstack).size() == 0);
                                    }
                                }
                                else {
                                    addedItem = (inventory.addItem(itemstack).size() == 0);
                                }
                            }
                            if (!addedItem && isPlayerInventory) {
                                PlayerInventory playerInventory = (PlayerInventory) inventory;
                                ItemStack offhand = playerInventory.getItemInOffHand();
                                if (offhand == null || offhand.getType() == Material.AIR || (itemstack.isSimilar(offhand) && offhand.getAmount() < offhand.getMaxStackSize())) {
                                    ItemStack setOffhand = itemstack.clone();
                                    if (itemstack.isSimilar(offhand)) {
                                        setOffhand.setAmount(offhand.getAmount() + 1);
                                    }

                                    playerInventory.setItemInOffHand(setOffhand);
                                }
                            }
                            count++;
                        }
                    }
                    else {
                        int removeAmount = itemstack.getAmount();
                        ItemStack removeMatch = itemstack.clone();
                        removeMatch.setAmount(1);

                        ItemStack[] inventoryContents = (isPlayerInventory ? inventory.getContents() : inventory.getStorageContents()).clone();
                        for (int i = inventoryContents.length - 1; i >= 0; i--) {
                            if (inventoryContents[i] != null) {
                                ItemStack itemStack = inventoryContents[i].clone();
                                int maxAmount = itemStack.getAmount();
                                int currentAmount = maxAmount;
                                itemStack.setAmount(1);

                                if (itemStack.toString().equals(removeMatch.toString())) {
                                    for (int scan = 0; scan < maxAmount; scan++) {
                                        if (removeAmount > 0) {
                                            currentAmount--;
                                            itemStack.setAmount(currentAmount);
                                            removeAmount--;
                                        }
                                        else {
                                            break;
                                        }
                                    }
                                }
                                else {
                                    itemStack.setAmount(maxAmount);
                                }

                                if (itemStack.getAmount() == 0) {
                                    inventoryContents[i] = null;
                                }
                                else {
                                    inventoryContents[i] = itemStack;
                                }
                            }

                            if (removeAmount == 0) {
                                break;
                            }
                        }

                        if (isPlayerInventory) {
                            inventory.setContents(inventoryContents);
                        }
                        else {
                            inventory.setStorageContents(inventoryContents);
                        }

                        int count = 0;
                        while (count < removeAmount) {
                            inventory.removeItem(removeMatch);
                            count++;
                        }
                    }
                }
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        return modifiedArmor;
    }

    public static void sortContainerItems(PlayerInventory inventory, List<Integer> modifiedArmorSlots) {
        try {
            ItemStack[] armorContents = inventory.getArmorContents();
            ItemStack[] storageContents = inventory.getStorageContents();

            for (int armor = 0; armor < armorContents.length; armor++) {
                ItemStack armorItem = armorContents[armor];
                if (armorItem == null || !modifiedArmorSlots.contains(armor)) {
                    continue;
                }

                for (int storage = 0; storage < storageContents.length; storage++) {
                    ItemStack storageItem = storageContents[storage];
                    if (storageItem == null) {
                        storageContents[storage] = armorItem;
                        armorContents[armor] = null;
                        break;
                    }
                }
            }

            inventory.setArmorContents(armorContents);
            inventory.setStorageContents(storageContents);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void buildFireworkEffect(Builder effectBuilder, Material rowType, ItemStack itemstack) {
        try {
            FireworkEffect effect = effectBuilder.build();
            if ((rowType == Material.FIREWORK_ROCKET)) {
                FireworkMeta meta = (FireworkMeta) itemstack.getItemMeta();
                meta.addEffect(effect);
                itemstack.setItemMeta(meta);
            }
            else if ((rowType == Material.FIREWORK_STAR)) {
                FireworkEffectMeta meta = (FireworkEffectMeta) itemstack.getItemMeta();
                meta.setEffect(effect);
                itemstack.setItemMeta(meta);
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    @SuppressWarnings("unchecked")
    public static Object[] populateItemStack(ItemStack itemstack, Object list) {
        int slot = 0;
        String faceData = "";

        try {
            /*
            if (list instanceof Object[]) {
                slot = (int) ((Object[]) list)[0];
                ItemMeta itemMeta = (ItemMeta) ((Object[]) list)[1];
                itemstack.setItemMeta(itemMeta);
                return new Object[] { slot, itemstack };
            }
            */

            Material rowType = itemstack.getType();
            List<Object> metaList = (List<Object>) list;
            if (metaList.size() > 0 && !(metaList.get(0) instanceof List<?>)) {
                if (rowType.name().endsWith("_BANNER")) {
                    BannerMeta meta = (BannerMeta) itemstack.getItemMeta();
                    for (Object value : metaList) {
                        if (value instanceof Map) {
                            Pattern pattern = new Pattern((Map<String, Object>) value);
                            meta.addPattern(pattern);
                        }
                    }
                    itemstack.setItemMeta(meta);
                }
                else if (BlockGroup.SHULKER_BOXES.contains(rowType)) {
                    BlockStateMeta meta = (BlockStateMeta) itemstack.getItemMeta();
                    ShulkerBox shulkerBox = (ShulkerBox) meta.getBlockState();
                    for (Object value : metaList) {
                        ItemStack item = Util.unserializeItemStackLegacy(value);
                        if (item != null) {
                            shulkerBox.getInventory().addItem(item);
                        }
                    }
                    meta.setBlockState(shulkerBox);
                    itemstack.setItemMeta(meta);
                }

                return new Object[] { slot, faceData, itemstack };
            }

            int itemCount = 0;
            Builder effectBuilder = FireworkEffect.builder();
            for (List<Map<String, Object>> map : (List<List<Map<String, Object>>>) list) {
                if (map.size() == 0) {
                    if (itemCount == 3 && (rowType == Material.FIREWORK_ROCKET || rowType == Material.FIREWORK_STAR)) {
                        buildFireworkEffect(effectBuilder, rowType, itemstack);
                        itemCount = 0;
                    }

                    itemCount++;
                    continue;
                }
                Map<String, Object> mapData = map.get(0);

                if (mapData.get("slot") != null) {
                    slot = (Integer) mapData.get("slot");
                }
                else if (mapData.get("facing") != null) {
                    faceData = (String) mapData.get("facing");
                }
                else if (mapData.get("modifiers") != null) {
                    ItemMeta itemMeta = itemstack.getItemMeta();
                    if (itemMeta.hasAttributeModifiers()) {
                        for (Map.Entry<Attribute, AttributeModifier> entry : itemMeta.getAttributeModifiers().entries()) {
                            itemMeta.removeAttributeModifier(entry.getKey(), entry.getValue());
                        }
                    }

                    List<Object> modifiers = (List<Object>) mapData.get("modifiers");

                    for (Object item : modifiers) {
                        Map<Attribute, Map<String, Object>> modifiersMap = (Map<Attribute, Map<String, Object>>) item;
                        for (Map.Entry<Attribute, Map<String, Object>> entry : modifiersMap.entrySet()) {
                            try {
                                Attribute attribute = entry.getKey();
                                AttributeModifier modifier = AttributeModifier.deserialize(entry.getValue());
                                itemMeta.addAttributeModifier(attribute, modifier);
                            }
                            catch (IllegalArgumentException e) {
                                // AttributeModifier already exists
                            }
                        }
                    }

                    itemstack.setItemMeta(itemMeta);
                }
                else if (itemCount == 0) {
                    ItemMeta meta = Util.deserializeItemMeta(itemstack.getItemMeta().getClass(), map.get(0));
                    itemstack.setItemMeta(meta);

                    if (map.size() > 1 && (rowType == Material.POTION)) {
                        PotionMeta subMeta = (PotionMeta) itemstack.getItemMeta();
                        org.bukkit.Color color = org.bukkit.Color.deserialize(map.get(1));
                        subMeta.setColor(color);
                        itemstack.setItemMeta(subMeta);
                    }
                }
                else {
                    if ((rowType == Material.LEATHER_HORSE_ARMOR) || (rowType == Material.LEATHER_HELMET) || (rowType == Material.LEATHER_CHESTPLATE) || (rowType == Material.LEATHER_LEGGINGS) || (rowType == Material.LEATHER_BOOTS)) { // leather armor
                        for (Map<String, Object> colorData : map) {
                            LeatherArmorMeta meta = (LeatherArmorMeta) itemstack.getItemMeta();
                            org.bukkit.Color color = org.bukkit.Color.deserialize(colorData);
                            meta.setColor(color);
                            itemstack.setItemMeta(meta);
                        }
                    }
                    else if ((rowType == Material.POTION)) { // potion
                        for (Map<String, Object> potionData : map) {
                            PotionMeta meta = (PotionMeta) itemstack.getItemMeta();
                            PotionEffect effect = new PotionEffect(potionData);
                            meta.addCustomEffect(effect, true);
                            itemstack.setItemMeta(meta);
                        }
                    }
                    else if (rowType.name().endsWith("_BANNER")) {
                        for (Map<String, Object> patternData : map) {
                            BannerMeta meta = (BannerMeta) itemstack.getItemMeta();
                            Pattern pattern = new Pattern(patternData);
                            meta.addPattern(pattern);
                            itemstack.setItemMeta(meta);
                        }
                    }
                    else if ((rowType == Material.CROSSBOW)) {
                        CrossbowMeta meta = (CrossbowMeta) itemstack.getItemMeta();
                        for (Map<String, Object> itemData : map) {
                            ItemStack crossbowItem = Util.unserializeItemStack(itemData);
                            if (crossbowItem != null) {
                                meta.addChargedProjectile(crossbowItem);
                            }
                        }
                        itemstack.setItemMeta(meta);
                    }
                    else if (rowType == Material.MAP || rowType == Material.FILLED_MAP) {
                        for (Map<String, Object> colorData : map) {
                            MapMeta meta = (MapMeta) itemstack.getItemMeta();
                            org.bukkit.Color color = org.bukkit.Color.deserialize(colorData);
                            meta.setColor(color);
                            itemstack.setItemMeta(meta);
                        }
                    }
                    else if ((rowType == Material.FIREWORK_ROCKET) || (rowType == Material.FIREWORK_STAR)) {
                        if (itemCount == 1) {
                            effectBuilder = FireworkEffect.builder();
                            for (Map<String, Object> fireworkData : map) {
                                org.bukkit.FireworkEffect.Type type = (org.bukkit.FireworkEffect.Type) fireworkData.getOrDefault("type", org.bukkit.FireworkEffect.Type.BALL);
                                boolean hasFlicker = (Boolean) fireworkData.get("flicker");
                                boolean hasTrail = (Boolean) fireworkData.get("trail");
                                effectBuilder.with(type);
                                effectBuilder.flicker(hasFlicker);
                                effectBuilder.trail(hasTrail);
                            }
                        }
                        else if (itemCount == 2) {
                            for (Map<String, Object> colorData : map) {
                                org.bukkit.Color color = org.bukkit.Color.deserialize(colorData);
                                effectBuilder.withColor(color);
                            }
                        }
                        else if (itemCount == 3) {
                            for (Map<String, Object> colorData : map) {
                                org.bukkit.Color color = org.bukkit.Color.deserialize(colorData);
                                effectBuilder.withFade(color);
                            }
                            buildFireworkEffect(effectBuilder, rowType, itemstack);
                            itemCount = 0;
                        }
                    }
                    else if ((rowType == Material.SUSPICIOUS_STEW)) {
                        for (Map<String, Object> suspiciousStewData : map) {
                            SuspiciousStewMeta meta = (SuspiciousStewMeta) itemstack.getItemMeta();
                            PotionEffect effect = new PotionEffect(suspiciousStewData);
                            meta.addCustomEffect(effect, true);
                            itemstack.setItemMeta(meta);
                        }
                    }
                    else {
                        BukkitAdapter.ADAPTER.setItemMeta(rowType, itemstack, map);
                    }
                }

                itemCount++;
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        return new Object[] { slot, faceData, itemstack };
    }

    public static Object[] populateItemStack(ItemStack itemstack, byte[] metadata) {
        if (metadata != null) {
            try {
                ByteArrayInputStream metaByteStream = new ByteArrayInputStream(metadata);
                BukkitObjectInputStream metaObjectStream = new BukkitObjectInputStream(metaByteStream);
                Object metaList = metaObjectStream.readObject();
                metaObjectStream.close();
                metaByteStream.close();

                return populateItemStack(itemstack, metaList);
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }

        return new Object[] { 0, "", itemstack };
    }

}
