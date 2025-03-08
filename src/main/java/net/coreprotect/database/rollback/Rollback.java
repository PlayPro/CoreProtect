package net.coreprotect.database.rollback;

import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import net.coreprotect.CoreProtect;
import net.coreprotect.bukkit.BukkitAdapter;
import net.coreprotect.config.Config;
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.consumer.Queue;
import net.coreprotect.consumer.process.Process;
import net.coreprotect.database.Lookup;
import net.coreprotect.database.LookupConverter;
import net.coreprotect.database.statement.UserStatement;
import net.coreprotect.language.Phrase;
import net.coreprotect.language.Selector;
import net.coreprotect.model.BlockGroup;
import net.coreprotect.thread.Scheduler;
import net.coreprotect.utility.Chat;
import net.coreprotect.utility.Color;
import net.coreprotect.utility.DatabaseUtils;
import net.coreprotect.utility.WorldUtils;

public class Rollback extends RollbackUtil {

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

                    if (rowWorldId != worldId) {
                        String world = WorldUtils.getWorldName(rowWorldId);
                        World bukkitWorld = Bukkit.getServer().getWorld(world);
                        if (bukkitWorld != null) {
                            worldMin = BukkitAdapter.ADAPTER.getMinHeight(bukkitWorld);
                            worldMax = bukkitWorld.getMaxHeight();
                        }
                    }

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
            for (Entry<Long, Integer> entry : DatabaseUtils.entriesSortedByValues(chunkList)) {
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
                    String rollbackWorld = WorldUtils.getWorldName(rollbackWorldId);
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
                        // Process this chunk using our new RollbackProcessor class
                        ArrayList<Object[]> blockData = finalBlockList != null ? finalBlockList.getOrDefault(chunkKey, new ArrayList<>()) : new ArrayList<>();
                        ArrayList<Object[]> itemData = finalItemList != null ? finalItemList.getOrDefault(chunkKey, new ArrayList<>()) : new ArrayList<>();
                        RollbackProcessor.processChunk(finalChunkX, finalChunkZ, chunkKey, blockData, itemData, rollbackType, preview, finalUserString, finalUser instanceof Player ? (Player) finalUser : null, bukkitRollbackWorld, inventoryRollback);
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
                RollbackComplete.output(user, location, checkUsers, restrictList, excludeList, excludeUserList, actionList, timeString, chunkCount, totalSeconds, itemCount, blockCount, entityCount, rollbackType, radius, verbose, restrictWorld, preview);
            }

            list = LookupConverter.convertRawLookup(statement, lookupList);
            return list;
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

}
