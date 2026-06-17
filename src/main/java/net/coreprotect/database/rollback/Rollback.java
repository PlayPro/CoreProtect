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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import net.coreprotect.data.lookup.LookupResult;
import net.coreprotect.data.lookup.result.CommonLookupResult;
import net.coreprotect.data.lookup.type.CommonLookupData;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
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
import net.coreprotect.database.statement.UserStatement;
import net.coreprotect.language.Phrase;
import net.coreprotect.language.Selector;
import net.coreprotect.model.BlockGroup;
import net.coreprotect.model.action.LookupActions;
import net.coreprotect.model.rollback.RollbackUpdateTargets;
import net.coreprotect.thread.Scheduler;
import net.coreprotect.utility.Chat;
import net.coreprotect.utility.Color;
import net.coreprotect.utility.DatabaseUtils;
import net.coreprotect.utility.WorldUtils;
import net.coreprotect.utility.ErrorReporter;

public class Rollback extends RollbackUtil {

    public static LookupResult<?> performRollbackRestore(Statement statement, CommandSender user, List<String> checkUuids, List<String> checkUsers, String timeString, List<Object> restrictList, Map<Object, Boolean> excludeList, List<String> excludeUserList, List<Integer> actionList, Location location, Integer[] radius, long startTime, long endTime, boolean restrictWorld, boolean lookup, boolean verbose, final int rollbackType, final int preview) {
        try {
            long timeStart = System.currentTimeMillis();
            LookupResult<?> rawLookupResult = null;

            if (!actionList.contains(LookupActions.CONTAINER) && !actionList.contains(5) && !checkUsers.contains("#container")) {
                rawLookupResult = Lookup.performLookup(statement, user, checkUuids, checkUsers, restrictList, excludeList, excludeUserList, actionList, location, radius, null, startTime, endTime, -1, -1, restrictWorld, lookup, false);
            }

            boolean ROLLBACK_ITEMS = false;
            List<Object> itemRestrictList = new ArrayList<>(restrictList);
            Map<Object, Boolean> itemExcludeList = new HashMap<>(excludeList);

            if (actionList.contains(LookupActions.BLOCK_PLACE)) {
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

            CommonLookupResult itemLookupResult = null;
            if (Config.getGlobal().ROLLBACK_ITEMS && !checkUsers.contains("#container") && (actionList.size() == 0 || actionList.contains(LookupActions.CONTAINER) || ROLLBACK_ITEMS) && preview == 0) {
                List<Integer> itemActionList = new ArrayList<>(actionList);

                if (!itemActionList.contains(LookupActions.CONTAINER)) {
                    itemActionList.add(LookupActions.CONTAINER);
                }

                itemExcludeList.entrySet().removeIf(entry -> Boolean.TRUE.equals(entry.getValue()));
                final LookupResult<?> rawItemResult = Lookup.performLookup(statement, user, checkUuids, checkUsers, itemRestrictList, itemExcludeList, excludeUserList, itemActionList, location, radius, null, startTime, endTime, -1, -1, restrictWorld, lookup, false);

                if (rawItemResult instanceof CommonLookupResult commonResult) {
                    itemLookupResult = commonResult;
                }
            }

            if (!(rawLookupResult instanceof CommonLookupResult) && itemLookupResult == null) {
                return null;
            }

            LinkedHashSet<Integer> worldList = new LinkedHashSet<>();
            TreeMap<Long, Integer> chunkList = new TreeMap<>();
            Map<Integer, Map<Long, List<CommonLookupData>>> dataList = new HashMap<>();
            Map<Integer, Map<Long, List<CommonLookupData>>> itemDataList = new HashMap<>();
            boolean inventoryRollback = actionList.contains(LookupActions.ITEM);

            int worldId = -1;
            int worldMin = 0;
            int worldMax = 2032;

            int listC = 0;
            while (listC < 2) {
                List<CommonLookupData> scanList = rawLookupResult != null ? ((CommonLookupResult) rawLookupResult).data() : List.of(); // rawLookupResult can be null while upstream just handles it as an empty list

                if (listC == 1 && itemLookupResult != null) {
                    scanList = itemLookupResult.data();
                }

                for (CommonLookupData row : scanList) {
                    int userId = row.userId();
                    int rowX = row.x();
                    int rowY = row.y();
                    int rowZ = row.z();
                    int rowWorldId = row.worldId();
                    int chunkX = rowX >> 4;
                    int chunkZ = rowZ >> 4;
                    long chunkKey = inventoryRollback ? 0 : Chunk.getChunkKey(chunkX, chunkZ);

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
                            distance = (int) Math.sqrt(Math.pow(rowX - location.getBlockX(), 2) + Math.pow(rowZ - location.getBlockZ(), 2));
                        }

                        chunkList.put(chunkKey, distance);
                    }

                    if (ConfigHandler.playerIdCacheReversed.get(userId) == null) {
                        UserStatement.loadName(statement.getConnection(), userId);
                    }

                    Map<Integer, Map<Long, List<CommonLookupData>>> modifyList = dataList;
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

                    modifyList.get(rowWorldId).get(chunkKey).add(row);
                }

                listC++;
            }

            if (rollbackType == 1) { // Restore
                Iterator<Map.Entry<Integer, Map<Long, List<CommonLookupData>>>> dlIterator = dataList.entrySet().iterator();
                while (dlIterator.hasNext()) {
                    for (List<CommonLookupData> list : dlIterator.next().getValue().values()) {
                        Collections.reverse(list);
                    }
                }

                dlIterator = itemDataList.entrySet().iterator();
                while (dlIterator.hasNext()) {
                    for (List<CommonLookupData> list : dlIterator.next().getValue().values()) {
                        Collections.reverse(list);
                    }
                }
            }

            Integer chunkCount = 0;
            String userString = "#server";
            if (user != null) {
                userString = user.getName();
                if (verbose && preview == 0 && !actionList.contains(LookupActions.ITEM)) {
                    int chunks = chunkList.size();
                    Chat.sendMessage(user, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.ROLLBACK_CHUNKS_FOUND, Integer.toString(chunks), (chunks == 1 ? Selector.FIRST : Selector.SECOND)));
                }
            }

            // Perform update transaction(s) in consumer
            if (preview == 0) {
                if (actionList.contains(LookupActions.ITEM) && itemLookupResult instanceof CommonLookupResult commonResult) {
                    List<CommonLookupData> blockList = new ArrayList<>();
                    List<CommonLookupData> inventoryList = new ArrayList<>();
                    List<CommonLookupData> containerList = new ArrayList<>();

                    for (CommonLookupData data : commonResult.data()) {
                        Integer table = data.table();

                        List<CommonLookupData> addTo = switch (table) {
                            case RollbackUpdateTargets.INVENTORY_ITEM -> inventoryList;
                            case RollbackUpdateTargets.CONTAINER -> containerList;
                            case null, default -> blockList;
                        };

                        addTo.add(data);
                    }

                    Queue.queueRollbackUpdate(userString, inventoryList, Process.INVENTORY_ROLLBACK_UPDATE, rollbackType);
                    Queue.queueRollbackUpdate(userString, containerList, Process.INVENTORY_CONTAINER_ROLLBACK_UPDATE, rollbackType);
                    Queue.queueRollbackUpdate(userString, blockList, Process.BLOCK_INVENTORY_ROLLBACK_UPDATE, rollbackType);
                }
                else {
                    Queue.queueRollbackUpdate(userString, rawLookupResult instanceof CommonLookupResult lookupResult ? lookupResult.data() : null, Process.ROLLBACK_UPDATE, rollbackType);
                    Queue.queueRollbackUpdate(userString, itemLookupResult != null ? itemLookupResult.data() : null, Process.CONTAINER_ROLLBACK_UPDATE, rollbackType);
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
                List<CompletableFuture<Boolean>> chunkFutures = new ArrayList<>();
                for (Entry<Integer, World> rollbackWorlds : worldMap.entrySet()) {
                    Integer rollbackWorldId = rollbackWorlds.getKey();
                    World bukkitRollbackWorld = rollbackWorlds.getValue();
                    Location chunkLocation = new Location(bukkitRollbackWorld, (finalChunkX << 4), 0, (finalChunkZ << 4));
                    final Map<Long, List<CommonLookupData>> finalBlockList = dataList.get(rollbackWorldId);
                    final Map<Long, List<CommonLookupData>> finalItemList = itemDataList.get(rollbackWorldId);

                    Scheduler.scheduleSyncDelayedTask(CoreProtect.getInstance(), () -> {
                        // Process this chunk using our new RollbackProcessor class

                    }, chunkLocation, 0);

                    chunkFutures.add(scheduleChunkTask(finalChunkX, finalChunkZ, chunkKey, finalBlockList, finalItemList, rollbackType, preview, finalUserString, finalUser, bukkitRollbackWorld, inventoryRollback));
                }

                if (ConfigHandler.isFolia) {
                    if (!awaitChunkTasks(chunkFutures, preview)) {
                        Chat.console(Phrase.build(Phrase.ROLLBACK_ABORTED));
                        break;
                    }
                }

                rollbackHashData = ConfigHandler.rollbackHash.get(finalUserString);
                int next = rollbackHashData[3];
                int scannedWorlds = rollbackHashData[4];
                int sleepTime = 0;
                int abort = 0;

                if (!ConfigHandler.isFolia) {
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

                if (verbose && user != null && preview == 0 && !actionList.contains(LookupActions.ITEM)) {
                    int chunks = chunkList.size();
                    Chat.sendMessage(user, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.ROLLBACK_CHUNKS_MODIFIED, chunkCount.toString(), Integer.toString(chunks), (chunks == 1 ? Selector.FIRST : Selector.SECOND)));
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

            return rawLookupResult;
        }
        catch (Exception e) {
            ErrorReporter.report(e);
        }

        return null;
    }

    private static CompletableFuture<Boolean> scheduleChunkTask(int chunkX, int chunkZ, long chunkKey, Map<Long, List<CommonLookupData>> blockList, Map<Long, List<CommonLookupData>> itemList, int rollbackType, int preview, String userString, CommandSender user, World world, boolean inventoryRollback) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        Location chunkLocation = new Location(world, (chunkX << 4), 0, (chunkZ << 4));

        Scheduler.scheduleSyncDelayedTask(CoreProtect.getInstance(), () -> {
            try {
                List<CommonLookupData> blockData = blockList != null ? blockList.getOrDefault(chunkKey, new ArrayList<>()) : new ArrayList<>();
                List<CommonLookupData> itemData = itemList != null ? itemList.getOrDefault(chunkKey, new ArrayList<>()) : new ArrayList<>();
                boolean result = RollbackProcessor.processChunk(chunkX, chunkZ, chunkKey, blockData, itemData, rollbackType, preview, userString, user instanceof Player player ? player : null, world, inventoryRollback);
                future.complete(result);
            }
            catch (Exception e) {
                ErrorReporter.report(e);
                future.complete(false);
            }
        }, chunkLocation, 0);

        return future;
    }

    private static boolean awaitChunkTasks(List<CompletableFuture<Boolean>> futures, int preview) throws InterruptedException {
        if (futures.isEmpty()) {
            return true;
        }

        try {
            CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).get(5, TimeUnit.MINUTES);
            return futures.stream().allMatch(future -> future.getNow(Boolean.FALSE) == Boolean.TRUE);
        } catch (TimeoutException | ExecutionException ignored) {
            return false;
        }
    }
}
