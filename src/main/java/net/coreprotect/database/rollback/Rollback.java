package net.coreprotect.database.rollback;

import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import net.coreprotect.CoreProtect;
import net.coreprotect.config.Config;
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.consumer.Consumer;
import net.coreprotect.consumer.Queue;
import net.coreprotect.consumer.process.Process;
import net.coreprotect.database.Lookup;
import net.coreprotect.database.LookupConverter;
import net.coreprotect.database.statement.EntitySpawnStatement;
import net.coreprotect.database.statement.EntityStatement;
import net.coreprotect.database.statement.UserStatement;
import net.coreprotect.language.Phrase;
import net.coreprotect.language.Selector;
import net.coreprotect.model.BlockGroup;
import net.coreprotect.model.action.EntityActionFilter;
import net.coreprotect.model.action.LookupActions;
import net.coreprotect.model.entity.EntitySpawnRecord;
import net.coreprotect.model.item.InventorySources;
import net.coreprotect.model.rollback.RollbackUpdateTargets;
import net.coreprotect.paper.PaperAdapter;
import net.coreprotect.thread.Scheduler;
import net.coreprotect.thread.TickTimeMonitor;
import net.coreprotect.utility.Chat;
import net.coreprotect.utility.Color;
import net.coreprotect.utility.DatabaseUtils;
import net.coreprotect.utility.EntitySpawnTracking;
import net.coreprotect.utility.ErrorReporter;
import net.coreprotect.utility.MaterialUtils;
import net.coreprotect.utility.WorldUtils;

public class Rollback extends RollbackUtil {

    private static final long ROLLBACK_BATCH_BUDGET_BASELINE_NANOS = 25_000_000L;
    private static final long ROLLBACK_BATCH_BUDGET_FLOOR_NANOS = 20_000_000L;
    private static final long ROLLBACK_BATCH_BUDGET_CEILING_NANOS = 50_000_000L;
    private static final long ROLLBACK_BATCH_TICK_YIELD_NANOS = 50_000_000L;
    private static final int CHUNK_PREFETCH_DISTANCE = 8;

    public static List<String[]> performRollbackRestore(Statement statement, CommandSender user, List<String> checkUuids, List<String> checkUsers, String timeString, List<Object> restrictList, Map<Object, Boolean> excludeList, List<String> excludeUserList, List<Integer> actionList, Location location, Integer[] radius, long startTime, long endTime, boolean restrictWorld, boolean lookup, boolean verbose, final int rollbackType, final int preview) {
        return performRollbackRestore(statement, user, checkUuids, checkUsers, timeString, restrictList, excludeList, excludeUserList, actionList, EntityActionFilter.DEFAULT, location, radius, startTime, endTime, restrictWorld, lookup, verbose, rollbackType, preview);
    }

    public static List<String[]> performRollbackRestore(Statement statement, CommandSender user, List<String> checkUuids, List<String> checkUsers, String timeString, List<Object> restrictList, Map<Object, Boolean> excludeList, List<String> excludeUserList, List<Integer> actionList, EntityActionFilter entityActionFilter, Location location, Integer[] radius, long startTime, long endTime, boolean restrictWorld, boolean lookup, boolean verbose, final int rollbackType, final int preview) {
        List<String[]> list = new ArrayList<>();
        EntitySpawnRollbackHandler.Context entitySpawnContext = null;

        try {
            long timeStart = System.currentTimeMillis();
            List<Object[]> lookupList = new ArrayList<>();
            Set<UUID> loadedEntityUuids = Collections.emptySet();
            Set<UUID> loadedEntityCandidates = Collections.emptySet();
            Integer exactEntityContainerId = user == null || !actionList.contains(5) ? null : ConfigHandler.lookupEntityContainer.get(user.getName());

            if ((!actionList.contains(LookupActions.CONTAINER) && !actionList.contains(5) && !checkUsers.contains("#container")) || exactEntityContainerId != null) {
                boolean includeEntitySpawns = entityActionFilter.includesAnySpawn(actionList, Config.getGlobal().ROLLBACK_ENTITIES);
                if (!lookup && rollbackType == 0 && radius != null && includeEntitySpawns) {
                    Set<UUID> databaseCandidates = EntitySpawnStatement.loadActiveUuids(statement.getConnection(), location, radius, startTime, endTime);
                    EntitySpawnTracking.LoadedEntityRadius loadedEntities = EntitySpawnTracking.findLoadedEntities(location, radius, databaseCandidates);
                    loadedEntityUuids = loadedEntities.getInside();
                    loadedEntityCandidates = loadedEntities.getLoadedCandidates();
                }
                lookupList = Lookup.performLookupRaw(statement, user, checkUuids, checkUsers, restrictList, excludeList, excludeUserList, actionList, entityActionFilter, loadedEntityUuids, loadedEntityCandidates, location, radius, null, startTime, endTime, -1, -1, restrictWorld, lookup, exactEntityContainerId);
            }

            if (lookupList == null) {
                sendAborted(user);
                return null;
            }

            List<Object[]> spawnList = new ArrayList<>();
            List<Object[]> trackedKillList = new ArrayList<>();
            List<Object[]> entityContainerList = new ArrayList<>();
            List<Object[]> entityContainerInventoryList = new ArrayList<>();
            List<Object[]> rollbackLookupList = new ArrayList<>();
            Set<Integer> entitySpawnRowIds = new HashSet<>();
            Set<Integer> entityKillRowIds = new HashSet<>();
            Set<Integer> entityContainerTrackingRowIds = new HashSet<>();
            Set<Integer> entityContainerInventoryTrackingRowIds = new HashSet<>();
            boolean inventoryRollback = actionList.contains(LookupActions.ITEM);
            for (Object[] row : lookupList) {
                if (row.length > 15 && row[14] instanceof Integer && (Integer) row[14] == InventorySources.ENTITY_CONTAINER) {
                    if (inventoryRollback) {
                        entityContainerInventoryList.add(row);
                        entityContainerInventoryTrackingRowIds.add((Integer) row[15]);
                    }
                    else {
                        entityContainerList.add(row);
                        entityContainerTrackingRowIds.add((Integer) row[15]);
                    }
                }
                else if ((Integer) row[8] == LookupActions.ENTITY_SPAWN) {
                    spawnList.add(row);
                    entitySpawnRowIds.add((Integer) row[7]);
                }
                else {
                    rollbackLookupList.add(row);
                    if ((Integer) row[8] == LookupActions.ENTITY_KILL && (Integer) row[7] > 0) {
                        entityKillRowIds.add((Integer) row[7]);
                    }
                }
            }
            Map<Integer, EntitySpawnRecord> entitySpawnRecords = EntitySpawnStatement.loadRecords(statement.getConnection(), entitySpawnRowIds);
            if (entitySpawnRecords.size() != entitySpawnRowIds.size()) {
                warnMissingEntityRows("Skipping entity spawn rows with missing tracking data", entitySpawnRowIds, entitySpawnRecords.keySet());
            }
            Map<Integer, EntitySpawnRecord> entityKillRecords = EntitySpawnStatement.loadRecordsByKillRowIds(statement.getConnection(), entityKillRowIds);
            if (!entityKillRecords.isEmpty()) {
                Iterator<Object[]> iterator = rollbackLookupList.iterator();
                while (iterator.hasNext()) {
                    Object[] row = iterator.next();
                    if ((Integer) row[8] == LookupActions.ENTITY_KILL && entityKillRecords.containsKey((Integer) row[7])) {
                        trackedKillList.add(row);
                        iterator.remove();
                    }
                }
            }
            Map<Integer, List<Object>> entityKillData = new HashMap<>();
            if (rollbackType == 0 && !trackedKillList.isEmpty()) {
                Set<Integer> requiredKillData = new HashSet<>();
                for (Object[] row : trackedKillList) {
                    int killRowId = (Integer) row[7];
                    EntitySpawnRecord record = entityKillRecords.get(killRowId);
                    if (record != null && record.isRemoved() && MaterialUtils.rolledBack((Integer) row[9], false) == 0) {
                        requiredKillData.add(killRowId);
                    }
                }
                entityKillData.putAll(EntityStatement.loadData(statement.getConnection(), requiredKillData));
                if (entityKillData.size() != requiredKillData.size()) {
                    warnMissingEntityRows("Skipping tracked entity kill rows with missing entity data", requiredKillData, entityKillData.keySet());
                }
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

            List<Object[]> itemList = new ArrayList<>();
            if (exactEntityContainerId == null && Config.getGlobal().ROLLBACK_ITEMS && !checkUsers.contains("#container") && (actionList.size() == 0 || actionList.contains(LookupActions.CONTAINER) || ROLLBACK_ITEMS) && preview == 0) {
                List<Integer> itemActionList = new ArrayList<>(actionList);

                if (!itemActionList.contains(LookupActions.CONTAINER)) {
                    itemActionList.add(LookupActions.CONTAINER);
                }

                itemExcludeList.entrySet().removeIf(entry -> Boolean.TRUE.equals(entry.getValue()));
                if (!lookup && radius != null) {
                    Set<UUID> databaseCandidates = EntitySpawnStatement.loadActiveUuids(statement.getConnection(), location, radius);
                    EntitySpawnTracking.LoadedEntityRadius loadedEntities = EntitySpawnTracking.findLoadedEntities(location, radius, databaseCandidates);
                    loadedEntityUuids = loadedEntities.getInside();
                    loadedEntityCandidates = loadedEntities.getLoadedCandidates();
                }
                itemList = Lookup.performLookupRaw(statement, user, checkUuids, checkUsers, itemRestrictList, itemExcludeList, excludeUserList, itemActionList, EntityActionFilter.DEFAULT, loadedEntityUuids, loadedEntityCandidates, location, radius, null, startTime, endTime, -1, -1, restrictWorld, lookup);
                if (itemList == null) {
                    sendAborted(user);
                    return null;
                }

                Iterator<Object[]> itemIterator = itemList.iterator();
                while (itemIterator.hasNext()) {
                    Object[] row = itemIterator.next();
                    if (row.length > 15 && row[14] instanceof Integer && (Integer) row[14] == InventorySources.ENTITY_CONTAINER) {
                        if (inventoryRollback) {
                            entityContainerInventoryTrackingRowIds.add((Integer) row[15]);
                        }
                        else {
                            entityContainerList.add(row);
                            entityContainerTrackingRowIds.add((Integer) row[15]);
                            itemIterator.remove();
                        }
                    }
                }
            }
            itemList.addAll(entityContainerInventoryList);
            if (inventoryRollback && !entityContainerInventoryTrackingRowIds.isEmpty()) {
                Map<Integer, EntitySpawnRecord> containerLocations = EntitySpawnStatement.loadLocationRecords(statement.getConnection(), entityContainerInventoryTrackingRowIds);
                itemList = routeEntityContainerInventoryRows(itemList, containerLocations, location);
            }

            if (!entityContainerTrackingRowIds.isEmpty()) {
                Map<Integer, EntitySpawnRecord> containerRecords = EntitySpawnStatement.loadRecords(statement.getConnection(), entityContainerTrackingRowIds);
                entitySpawnRecords.putAll(containerRecords);
                if (containerRecords.size() != entityContainerTrackingRowIds.size()) {
                    warnMissingEntityRows("Skipping entity container rows with missing tracking data", entityContainerTrackingRowIds, containerRecords.keySet());
                }
            }

            LinkedHashSet<Integer> worldList = new LinkedHashSet<>();
            TreeMap<Long, Integer> chunkList = new TreeMap<>();
            HashMap<Integer, HashMap<Long, ArrayList<Object[]>>> dataList = new HashMap<>();
            HashMap<Integer, HashMap<Long, ArrayList<Object[]>>> itemDataList = new HashMap<>();
            int listC = 0;
            while (listC < 2) {
                List<Object[]> scanList = rollbackLookupList;

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

                    if (chunkList.get(chunkKey) == null) {
                        int distance = 0;
                        if (location != null) {
                            distance = (int) Math.sqrt(Math.pow((Integer) result[3] - location.getBlockX(), 2) + Math.pow((Integer) result[5] - location.getBlockZ(), 2));
                        }

                        chunkList.put(chunkKey, distance);
                    }

                    UserStatement.getName(statement.getConnection(), userId);

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
            }

            ConfigHandler.rollbackHash.put(userString, new int[] { 0, 0, 0, 0, 0 });
            entitySpawnContext = EntitySpawnRollbackHandler.prepare(spawnList, entitySpawnRecords, trackedKillList, entityKillRecords, entityKillData, entityContainerList, rollbackType, inventoryRollback, preview, userString, location, radius);
            if (rollbackType == 1) {
                entitySpawnContext.reverseWork();
            }
            addEntitySpawnChunks(entitySpawnContext, worldList, chunkList, location);

            if (user != null && verbose && preview == 0 && !actionList.contains(LookupActions.ITEM)) {
                Integer chunks = chunkList.size();
                Chat.sendMessage(user, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.ROLLBACK_CHUNKS_FOUND, chunks.toString(), (chunks == 1 ? Selector.FIRST : Selector.SECOND)));
            }

            final String finalUserString = userString;
            RollbackBlockDataCache blockDataCache = new RollbackBlockDataCache();
            List<Entry<Long, Integer>> sortedChunks = new ArrayList<>(DatabaseUtils.entriesSortedByValues(chunkList));
            if (entitySpawnContext.isCancelled()) {
                sendAborted(user);
                return null;
            }
            // Perform update transaction(s) in consumer
            if (preview == 0) {
                if (Consumer.isPersistenceHalted()) {
                    entitySpawnContext.cancel();
                    sendAborted(user);
                    return null;
                }
                if (actionList.contains(LookupActions.ITEM)) {
                    List<Object[]> blockList = new ArrayList<>();
                    List<Object[]> inventoryList = new ArrayList<>();
                    List<Object[]> containerList = new ArrayList<>();
                    List<Object[]> entityContainerInventoryUpdates = new ArrayList<>();
                    for (Object[] data : itemList) {
                        int table = (Integer) data[14];
                        if (table == RollbackUpdateTargets.INVENTORY_ITEM) {
                            inventoryList.add(data);
                        }
                        else if (table == RollbackUpdateTargets.CONTAINER) {
                            containerList.add(data);
                        }
                        else if (table == InventorySources.ENTITY_CONTAINER) {
                            entityContainerInventoryUpdates.add(data);
                        }
                        else {
                            blockList.add(data);
                        }
                    }
                    if (!inventoryList.isEmpty()) {
                        Queue.queueRollbackUpdate(userString, location, inventoryList, Process.INVENTORY_ROLLBACK_UPDATE, rollbackType);
                    }
                    if (!containerList.isEmpty()) {
                        Queue.queueRollbackUpdate(userString, location, containerList, Process.INVENTORY_CONTAINER_ROLLBACK_UPDATE, rollbackType);
                    }
                    if (!entityContainerInventoryUpdates.isEmpty()) {
                        Queue.queueEntityContainerRollbackUpdate(userString, location, entityContainerInventoryUpdates, rollbackType, true);
                    }
                    if (!blockList.isEmpty()) {
                        Queue.queueRollbackUpdate(userString, location, blockList, Process.BLOCK_INVENTORY_ROLLBACK_UPDATE, rollbackType);
                    }
                }
                else {
                    if (!rollbackLookupList.isEmpty()) {
                        Queue.queueRollbackUpdate(userString, location, rollbackLookupList, Process.ROLLBACK_UPDATE, rollbackType);
                    }
                    if (!itemList.isEmpty()) {
                        Queue.queueRollbackUpdate(userString, location, itemList, Process.CONTAINER_ROLLBACK_UPDATE, rollbackType);
                    }
                }
            }

            if (ConfigHandler.isFolia) {
                chunkCount += processFoliaChunks(sortedChunks, worldList, dataList, itemDataList, rollbackType, preview, finalUserString, user, inventoryRollback, verbose, actionList, blockDataCache, entitySpawnContext);
            }
            else {
                chunkCount += processBukkitChunks(sortedChunks, worldList, dataList, itemDataList, rollbackType, preview, finalUserString, user, inventoryRollback, verbose, actionList, blockDataCache, entitySpawnContext);
            }
            if (!entitySpawnContext.completeDirectTransitions()) {
                entitySpawnContext.cancel();
            }

            chunkList.clear();
            dataList.clear();
            itemDataList.clear();

            int[] rollbackHashData = ConfigHandler.rollbackHash.get(finalUserString);
            rollbackHashData[0] += entitySpawnContext.getItemCount();
            rollbackHashData[2] += entitySpawnContext.getEntityCount();
            ConfigHandler.rollbackHash.put(finalUserString, rollbackHashData);
            if (entitySpawnContext.isCancelled()) {
                sendAborted(user);
                return null;
            }
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
            ErrorReporter.report(e);
        }
        finally {
            if (entitySpawnContext != null) {
                entitySpawnContext.close();
            }
        }

        return null;
    }

    private static void addEntitySpawnChunks(EntitySpawnRollbackHandler.Context context, LinkedHashSet<Integer> worldList, TreeMap<Long, Integer> chunkList, Location origin) {
        for (EntitySpawnRollbackHandler.Work work : context.getWork()) {
            Location location = work.getLocation();
            int worldId = WorldUtils.getWorldId(location.getWorld().getName());
            int chunkX = location.getBlockX() >> 4;
            int chunkZ = location.getBlockZ() >> 4;
            long chunkKey = chunkX & 0xffffffffL | (chunkZ & 0xffffffffL) << 32;
            if (!chunkList.containsKey(chunkKey)) {
                int distance = origin == null ? 0 : (int) Math.sqrt(Math.pow(location.getBlockX() - origin.getBlockX(), 2) + Math.pow(location.getBlockZ() - origin.getBlockZ(), 2));
                chunkList.put(chunkKey, distance);
            }
            worldList.add(worldId);
        }
    }

    private static void warnMissingEntityRows(String message, Set<Integer> requiredRowIds, Set<Integer> loadedRowIds) {
        List<Integer> missingRowIds = new ArrayList<>();
        for (Integer rowId : requiredRowIds) {
            if (!loadedRowIds.contains(rowId)) {
                missingRowIds.add(rowId);
            }
        }
        warnSkippedEntityRows(message, missingRowIds);
    }

    static void warnSkippedEntityRows(String message, Collection<Integer> skippedRowIds) {
        List<Integer> sortedRowIds = new ArrayList<>(skippedRowIds);
        Collections.sort(sortedRowIds);
        StringBuilder rowIds = new StringBuilder();
        int displayLimit = Math.min(sortedRowIds.size(), 10);
        for (int index = 0; index < displayLimit; index++) {
            if (index > 0) {
                rowIds.append(", ");
            }
            rowIds.append(sortedRowIds.get(index));
        }
        if (sortedRowIds.size() > displayLimit) {
            rowIds.append(" and ").append(sortedRowIds.size() - displayLimit).append(" more");
        }
        Chat.console(message + " (rowid: " + rowIds + ").");
    }

    protected static void sendAborted(CommandSender user) {
        if (user == null) {
            Chat.console(Phrase.build(Phrase.ROLLBACK_ABORTED));
        }
        else {
            Chat.sendMessage(user, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.ROLLBACK_ABORTED));
        }
    }

    private static List<Object[]> routeEntityContainerInventoryRows(List<Object[]> rows, Map<Integer, EntitySpawnRecord> records, Location commandLocation) {
        World fallbackWorld = commandLocation == null ? null : commandLocation.getWorld();
        List<World> loadedWorlds = Bukkit.getWorlds();
        if (fallbackWorld == null && !loadedWorlds.isEmpty()) {
            fallbackWorld = loadedWorlds.get(0);
        }

        Map<Integer, Location> currentLocations = new HashMap<>(records.size());
        for (Entry<Integer, EntitySpawnRecord> entry : records.entrySet()) {
            EntitySpawnRecord record = entry.getValue();
            Location currentLocation = EntitySpawnTracking.getCachedLocation(record.getUuid());
            if (currentLocation == null) {
                World currentWorld = Bukkit.getWorld(WorldUtils.getWorldName(record.getWorldId()));
                if (currentWorld != null) {
                    currentLocation = new Location(currentWorld, record.getX(), record.getY(), record.getZ());
                }
            }
            if (currentLocation == null && commandLocation != null && commandLocation.getWorld() != null) {
                currentLocation = commandLocation;
            }
            if (currentLocation == null && fallbackWorld != null) {
                currentLocation = new Location(fallbackWorld, record.getX(), record.getY(), record.getZ());
            }
            if (currentLocation != null) {
                currentLocations.put(entry.getKey(), currentLocation);
            }
        }

        List<Object[]> routedRows = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            if (row.length <= 15 || !(row[14] instanceof Integer) || (Integer) row[14] != InventorySources.ENTITY_CONTAINER) {
                routedRows.add(row);
                continue;
            }

            Object[] routedRow = row.clone();
            EntitySpawnRecord record = records.get((Integer) row[15]);
            Location currentLocation = currentLocations.get((Integer) row[15]);
            if (currentLocation == null && commandLocation != null && commandLocation.getWorld() != null) {
                currentLocation = commandLocation;
            }
            if (currentLocation == null && fallbackWorld != null) {
                double currentX = record == null ? (Integer) row[3] : record.getX();
                double currentY = record == null ? (Integer) row[4] : record.getY();
                double currentZ = record == null ? (Integer) row[5] : record.getZ();
                currentLocation = new Location(fallbackWorld, currentX, currentY, currentZ);
            }
            if (currentLocation != null) {
                routedRow[3] = currentLocation.getBlockX();
                routedRow[4] = currentLocation.getBlockY();
                routedRow[5] = currentLocation.getBlockZ();
                routedRow[10] = WorldUtils.getWorldId(currentLocation.getWorld().getName());
            }
            routedRows.add(routedRow);
        }
        return routedRows;
    }

    private static int processFoliaChunks(List<Entry<Long, Integer>> sortedChunks, LinkedHashSet<Integer> worldList, HashMap<Integer, HashMap<Long, ArrayList<Object[]>>> dataList, HashMap<Integer, HashMap<Long, ArrayList<Object[]>>> itemDataList, int rollbackType, int preview, String userString, CommandSender user, boolean inventoryRollback, boolean verbose, List<Integer> actionList, RollbackBlockDataCache blockDataCache, EntitySpawnRollbackHandler.Context entitySpawnContext) throws InterruptedException {
        FoliaRollbackBatchState batchState = new FoliaRollbackBatchState(buildFoliaChunkWork(sortedChunks, worldList, dataList, itemDataList, entitySpawnContext), sortedChunks.size());
        while (batchState.hasNext()) {
            if (entitySpawnContext.isCancelled()) {
                break;
            }
            if (!processFoliaEmptyChunks(batchState, userString, verbose, user, preview, actionList)) {
                Chat.console(Phrase.build(Phrase.ROLLBACK_ABORTED));
                entitySpawnContext.cancel();
                break;
            }
            if (!batchState.hasNext()) {
                break;
            }

            CompletableFuture<Boolean> batchFuture = scheduleFoliaChunkBatchTask(batchState, rollbackType, preview, userString, user, inventoryRollback, verbose, actionList, blockDataCache, entitySpawnContext);
            if (!awaitChunkTasks(Collections.singletonList(batchFuture), preview) || !awaitChunkTasks(entitySpawnContext.drainPending(), preview)) {
                Chat.console(Phrase.build(Phrase.ROLLBACK_ABORTED));
                entitySpawnContext.cancel();
                break;
            }
        }

        return batchState.chunkCount;
    }

    private static List<FoliaChunkWork> buildFoliaChunkWork(List<Entry<Long, Integer>> sortedChunks, LinkedHashSet<Integer> worldList, HashMap<Integer, HashMap<Long, ArrayList<Object[]>>> dataList, HashMap<Integer, HashMap<Long, ArrayList<Object[]>>> itemDataList, EntitySpawnRollbackHandler.Context entitySpawnContext) {
        List<FoliaChunkWork> work = new ArrayList<>();
        HashMap<Integer, World> worldMap = getRollbackWorlds(worldList);
        List<Entry<Integer, World>> rollbackWorlds = new ArrayList<>(worldMap.entrySet());

        for (Entry<Long, Integer> entry : sortedChunks) {
            long chunkKey = entry.getKey();
            int chunkX = getChunkX(entry);
            int chunkZ = getChunkZ(entry);

            List<Entry<Integer, World>> chunkWorlds = new ArrayList<>();
            for (Entry<Integer, World> rollbackWorld : rollbackWorlds) {
                if (hasChunkWork(rollbackWorld.getKey(), chunkKey, dataList, itemDataList, entitySpawnContext)) {
                    chunkWorlds.add(rollbackWorld);
                }
            }

            if (chunkWorlds.isEmpty()) {
                work.add(new FoliaChunkWork(chunkKey, chunkX, chunkZ, -1, null, null, null, true));
                continue;
            }

            for (int index = 0; index < chunkWorlds.size(); index++) {
                Entry<Integer, World> rollbackWorld = chunkWorlds.get(index);
                int rollbackWorldId = rollbackWorld.getKey();
                World world = rollbackWorld.getValue();
                HashMap<Long, ArrayList<Object[]>> blockList = dataList.get(rollbackWorldId);
                HashMap<Long, ArrayList<Object[]>> itemList = itemDataList.get(rollbackWorldId);
                work.add(new FoliaChunkWork(chunkKey, chunkX, chunkZ, rollbackWorldId, world, blockList, itemList, index == chunkWorlds.size() - 1));
            }
        }

        return work;
    }

    private static boolean processFoliaEmptyChunks(FoliaRollbackBatchState batchState, String userString, boolean verbose, CommandSender user, int preview, List<Integer> actionList) {
        while (batchState.hasNext() && batchState.peek().world == null) {
            FoliaChunkWork work = batchState.next();
            prepareChunkCounters(userString);
            if (work.lastWorldForChunk) {
                batchState.chunkCount++;
                if (!completeChunk(userString, batchState.chunkCount, batchState.totalChunks, verbose, user, preview, actionList)) {
                    return false;
                }
            }
        }

        return true;
    }

    private static CompletableFuture<Boolean> scheduleFoliaChunkBatchTask(FoliaRollbackBatchState batchState, int rollbackType, int preview, String userString, CommandSender user, boolean inventoryRollback, boolean verbose, List<Integer> actionList, RollbackBlockDataCache blockDataCache, EntitySpawnRollbackHandler.Context entitySpawnContext) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        FoliaChunkWork firstWork = batchState.peek();
        Location chunkLocation = new Location(firstWork.world, (firstWork.chunkX << 4), 0, (firstWork.chunkZ << 4));

        Scheduler.scheduleSyncDelayedTask(CoreProtect.getInstance(), () -> {
            try {
                long batchStart = System.nanoTime();
                do {
                    FoliaChunkWork work = batchState.next();

                    prepareChunkCounters(userString);
                    if (!processChunkWorld(work.chunkX, work.chunkZ, work.chunkKey, work.worldId, work.blockList, work.itemList, rollbackType, preview, userString, user, work.world, inventoryRollback, blockDataCache, entitySpawnContext)) {
                        future.complete(false);
                        return;
                    }

                    if (work.lastWorldForChunk) {
                        batchState.chunkCount++;
                        if (!completeChunk(userString, batchState.chunkCount, batchState.totalChunks, verbose, user, preview, actionList)) {
                            future.complete(false);
                            return;
                        }
                    }
                }
                while (canContinueFoliaBatch(batchState, batchStart));

                future.complete(true);
            }
            catch (Exception e) {
                entitySpawnContext.cancel();
                ErrorReporter.report(e);
                future.complete(false);
            }
        }, chunkLocation, 0);

        return future;
    }

    private static int processBukkitChunks(List<Entry<Long, Integer>> sortedChunks, LinkedHashSet<Integer> worldList, HashMap<Integer, HashMap<Long, ArrayList<Object[]>>> dataList, HashMap<Integer, HashMap<Long, ArrayList<Object[]>>> itemDataList, int rollbackType, int preview, String userString, CommandSender user, boolean inventoryRollback, boolean verbose, List<Integer> actionList, RollbackBlockDataCache blockDataCache, EntitySpawnRollbackHandler.Context entitySpawnContext) throws InterruptedException {
        HashMap<Integer, World> worldMap = getRollbackWorlds(worldList);
        RollbackBatchState batchState = new RollbackBatchState(sortedChunks);
        if (!batchState.hasNext()) {
            return 0;
        }

        CompletableFuture<Boolean> completion = new CompletableFuture<>();
        scheduleChunkBatchTask(batchState, worldMap, dataList, itemDataList, rollbackType, preview, userString, user, inventoryRollback, verbose, actionList, blockDataCache, entitySpawnContext, completion, 0);
        if (!awaitRollbackCompletion(completion, batchState, preview) || !awaitChunkTasks(entitySpawnContext.drainPending(), preview)) {
            Chat.console(Phrase.build(Phrase.ROLLBACK_ABORTED));
            entitySpawnContext.cancel();
        }

        return batchState.chunkCount;
    }

    private static void scheduleChunkBatchTask(RollbackBatchState batchState, HashMap<Integer, World> worldMap, HashMap<Integer, HashMap<Long, ArrayList<Object[]>>> dataList, HashMap<Integer, HashMap<Long, ArrayList<Object[]>>> itemDataList, int rollbackType, int preview, String userString, CommandSender user, boolean inventoryRollback, boolean verbose, List<Integer> actionList, RollbackBlockDataCache blockDataCache, EntitySpawnRollbackHandler.Context entitySpawnContext, CompletableFuture<Boolean> completion, int delay) {
        Scheduler.scheduleSyncDelayedTask(CoreProtect.getInstance(), () -> {
            if (completion.isDone()) {
                return;
            }

            try {
                if (entitySpawnContext.isCancelled()) {
                    completion.complete(false);
                    return;
                }
                long batchBudget = adaptiveBatchBudgetNanos();
                long batchStart = System.nanoTime();
                do {
                    prefetchUpcomingChunks(batchState, worldMap, dataList, itemDataList, entitySpawnContext, inventoryRollback);
                    Entry<Long, Integer> entry = batchState.next();
                    batchState.chunkCount++;

                    if (!processChunkEntry(entry, worldMap, dataList, itemDataList, rollbackType, preview, userString, user, inventoryRollback, blockDataCache, entitySpawnContext)) {
                        completion.complete(false);
                        return;
                    }

                    if (!completeChunk(userString, batchState.chunkCount, batchState.totalChunks(), verbose, user, preview, actionList)) {
                        completion.complete(false);
                        return;
                    }
                }
                while (batchState.hasNext() && (System.nanoTime() - batchStart) < batchBudget);

                if (batchState.hasNext()) {
                    batchState.nanosSinceTickYield += (System.nanoTime() - batchStart);
                    int nextDelay = 0;
                    if (batchState.nanosSinceTickYield >= ROLLBACK_BATCH_TICK_YIELD_NANOS) {
                        batchState.nanosSinceTickYield = 0;
                        nextDelay = 1;
                    }
                    scheduleChunkBatchTask(batchState, worldMap, dataList, itemDataList, rollbackType, preview, userString, user, inventoryRollback, verbose, actionList, blockDataCache, entitySpawnContext, completion, nextDelay);
                }
                else {
                    completion.complete(true);
                }
            }
            catch (Exception e) {
                entitySpawnContext.cancel();
                ErrorReporter.report(e);
                completion.complete(false);
            }
        }, delay);
    }

    private static boolean awaitRollbackCompletion(CompletableFuture<Boolean> completion, RollbackBatchState batchState, int preview) throws InterruptedException {
        int delay = preview == 1 ? 1 : 5;
        int lastChunkCount = -1;
        long stalledTime = 0;

        while (!completion.isDone()) {
            int chunkCount = batchState.chunkCount;
            if (chunkCount != lastChunkCount) {
                lastChunkCount = chunkCount;
                stalledTime = 0;
            }

            stalledTime += delay;
            if (stalledTime > 300000) {
                completion.complete(false);
                return false;
            }
            Thread.sleep(delay);
        }

        try {
            return Boolean.TRUE.equals(completion.getNow(Boolean.FALSE));
        }
        catch (Exception e) {
            return false;
        }
    }

    static long adaptiveBatchBudgetNanos() {
        double averageTickTime = PaperAdapter.ADAPTER.getAverageTickTime(Bukkit.getServer());
        if (averageTickTime <= 0.0D) {
            averageTickTime = TickTimeMonitor.getEstimatedTickTime();
        }

        return batchBudgetNanos(averageTickTime);
    }

    public static long batchBudgetNanos(double averageTickTime) {
        if (averageTickTime <= 0.0D) {
            return ROLLBACK_BATCH_BUDGET_BASELINE_NANOS;
        }

        // Scale linearly from the 50ms ceiling on an idle server down to the 20ms floor on an overloaded one, so rollbacks mostly expand into idle tick time
        long tickTimeNanos = (long) (averageTickTime * 1_000_000.0D);
        long budgetNanos = ROLLBACK_BATCH_BUDGET_BASELINE_NANOS + ROLLBACK_BATCH_BUDGET_CEILING_NANOS - tickTimeNanos;
        return Math.max(ROLLBACK_BATCH_BUDGET_FLOOR_NANOS, Math.min(ROLLBACK_BATCH_BUDGET_CEILING_NANOS, budgetNanos));
    }

    private static boolean canContinueFoliaBatch(FoliaRollbackBatchState batchState, long batchStart) {
        if (!batchState.hasNext() || (System.nanoTime() - batchStart) >= ROLLBACK_BATCH_BUDGET_BASELINE_NANOS) {
            return false;
        }

        FoliaChunkWork nextWork = batchState.peek();
        return nextWork.world != null && PaperAdapter.ADAPTER.isOwnedByCurrentRegion(nextWork.world, nextWork.chunkX, nextWork.chunkZ);
    }

    private static boolean processChunkEntry(Entry<Long, Integer> entry, HashMap<Integer, World> worldMap, HashMap<Integer, HashMap<Long, ArrayList<Object[]>>> dataList, HashMap<Integer, HashMap<Long, ArrayList<Object[]>>> itemDataList, int rollbackType, int preview, String userString, CommandSender user, boolean inventoryRollback, RollbackBlockDataCache blockDataCache, EntitySpawnRollbackHandler.Context entitySpawnContext) {
        long chunkKey = entry.getKey();
        int chunkX = getChunkX(entry);
        int chunkZ = getChunkZ(entry);
        prepareChunkCounters(userString);

        for (Entry<Integer, World> rollbackWorlds : worldMap.entrySet()) {
            int rollbackWorldId = rollbackWorlds.getKey();
            World bukkitRollbackWorld = rollbackWorlds.getValue();
            HashMap<Long, ArrayList<Object[]>> blockList = dataList.get(rollbackWorldId);
            HashMap<Long, ArrayList<Object[]>> itemList = itemDataList.get(rollbackWorldId);

            if (!hasChunkWork(rollbackWorldId, chunkKey, dataList, itemDataList, entitySpawnContext)) {
                continue;
            }
            if (!processChunkWorld(chunkX, chunkZ, chunkKey, rollbackWorldId, blockList, itemList, rollbackType, preview, userString, user, bukkitRollbackWorld, inventoryRollback, blockDataCache, entitySpawnContext)) {
                return false;
            }
        }

        return true;
    }

    private static void prefetchUpcomingChunks(RollbackBatchState batchState, HashMap<Integer, World> worldMap, HashMap<Integer, HashMap<Long, ArrayList<Object[]>>> dataList, HashMap<Integer, HashMap<Long, ArrayList<Object[]>>> itemDataList, EntitySpawnRollbackHandler.Context entitySpawnContext, boolean inventoryRollback) {
        if (inventoryRollback) {
            return;
        }

        int prefetchLimit = Math.min(batchState.index + CHUNK_PREFETCH_DISTANCE, batchState.totalChunks());
        while (batchState.prefetchIndex < prefetchLimit) {
            Entry<Long, Integer> entry = batchState.sortedChunks.get(batchState.prefetchIndex);
            int chunkX = getChunkX(entry);
            int chunkZ = getChunkZ(entry);
            for (Entry<Integer, World> worldEntry : worldMap.entrySet()) {
                int worldId = worldEntry.getKey();
                boolean standardWork = containsChunk(dataList.get(worldId), entry.getKey()) || containsChunk(itemDataList.get(worldId), entry.getKey());
                List<EntitySpawnRollbackHandler.Work> entityWork = entitySpawnContext.getWork(worldId, entry.getKey());
                if (standardWork || EntitySpawnRollbackHandler.requiresChunk(entitySpawnContext, entityWork)) {
                    PaperAdapter.ADAPTER.prefetchChunk(worldEntry.getValue(), chunkX, chunkZ);
                }
            }
            batchState.prefetchIndex++;
        }
    }

    private static int getChunkX(Entry<Long, Integer> entry) {
        return (int) (long) entry.getKey();
    }

    private static int getChunkZ(Entry<Long, Integer> entry) {
        return (int) (entry.getKey() >> 32);
    }

    private static HashMap<Integer, World> getRollbackWorlds(LinkedHashSet<Integer> worldList) {
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

        return worldMap;
    }

    private static boolean hasChunkWork(int worldId, long chunkKey, HashMap<Integer, HashMap<Long, ArrayList<Object[]>>> dataList, HashMap<Integer, HashMap<Long, ArrayList<Object[]>>> itemDataList, EntitySpawnRollbackHandler.Context entitySpawnContext) {
        return containsChunk(dataList.get(worldId), chunkKey) || containsChunk(itemDataList.get(worldId), chunkKey) || !entitySpawnContext.getWork(worldId, chunkKey).isEmpty();
    }

    private static boolean containsChunk(HashMap<Long, ArrayList<Object[]>> data, long chunkKey) {
        List<Object[]> rows = data == null ? null : data.get(chunkKey);
        return rows != null && !rows.isEmpty();
    }

    private static void prepareChunkCounters(String userString) {
        int[] rollbackHashData = ConfigHandler.rollbackHash.get(userString);
        int itemCount = rollbackHashData[0];
        int blockCount = rollbackHashData[1];
        int entityCount = rollbackHashData[2];
        int scannedWorlds = rollbackHashData[4];
        ConfigHandler.rollbackHash.put(userString, new int[] { itemCount, blockCount, entityCount, 0, scannedWorlds });
    }

    private static boolean completeChunk(String userString, Integer chunkCount, int totalChunks, boolean verbose, CommandSender user, int preview, List<Integer> actionList) {
        int[] rollbackHashData = ConfigHandler.rollbackHash.get(userString);
        int itemCount = rollbackHashData[0];
        int blockCount = rollbackHashData[1];
        int entityCount = rollbackHashData[2];
        int next = rollbackHashData[3];

        if (next == 2) {
            return false;
        }

        ConfigHandler.rollbackHash.put(userString, new int[] { itemCount, blockCount, entityCount, 0, 0 });

        if (verbose && user != null && preview == 0 && !actionList.contains(LookupActions.ITEM)) {
            Integer chunks = totalChunks;
            Chat.sendMessage(user, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.ROLLBACK_CHUNKS_MODIFIED, chunkCount.toString(), chunks.toString(), (chunks == 1 ? Selector.FIRST : Selector.SECOND)));
        }

        return true;
    }

    private static boolean processChunkWorld(int chunkX, int chunkZ, long chunkKey, int worldId, HashMap<Long, ArrayList<Object[]>> blockList, HashMap<Long, ArrayList<Object[]>> itemList, int rollbackType, int preview, String userString, CommandSender user, World world, boolean inventoryRollback, RollbackBlockDataCache blockDataCache, EntitySpawnRollbackHandler.Context entitySpawnContext) {
        if (preview == 0 && Consumer.isPersistenceHalted()) {
            return false;
        }
        ArrayList<Object[]> blockData = blockList != null ? blockList.getOrDefault(chunkKey, new ArrayList<>()) : new ArrayList<>();
        ArrayList<Object[]> itemData = itemList != null ? itemList.getOrDefault(chunkKey, new ArrayList<>()) : new ArrayList<>();
        List<EntitySpawnRollbackHandler.Work> entitySpawnWork = entitySpawnContext.getWork(worldId, chunkKey);
        if (!entitySpawnWork.isEmpty()) {
            CompletableFuture<Boolean> entitySpawnFuture = EntitySpawnRollbackHandler.processChunk(entitySpawnContext, world, chunkX, chunkZ, entitySpawnWork);
            entitySpawnContext.addPending(entitySpawnFuture);
            if (entitySpawnFuture.isDone() && !Boolean.TRUE.equals(entitySpawnFuture.getNow(Boolean.FALSE))) {
                return false;
            }
        }
        if (blockData.isEmpty() && itemData.isEmpty()) {
            return true;
        }
        Player rollbackPlayer = user instanceof Player ? (Player) user : null;
        return RollbackProcessor.processChunk(chunkX, chunkZ, chunkKey, blockData, itemData, rollbackType, preview, userString, rollbackPlayer, world, inventoryRollback, blockDataCache);
    }

    private static final class RollbackBatchState {
        private final List<Entry<Long, Integer>> sortedChunks;
        private int index = 0;
        private int prefetchIndex = 0;
        private long nanosSinceTickYield = 0L;
        private volatile int chunkCount = 0;

        private RollbackBatchState(List<Entry<Long, Integer>> sortedChunks) {
            this.sortedChunks = sortedChunks;
        }

        private boolean hasNext() {
            return index < sortedChunks.size();
        }

        private Entry<Long, Integer> next() {
            return sortedChunks.get(index++);
        }

        private int totalChunks() {
            return sortedChunks.size();
        }
    }

    private static final class FoliaRollbackBatchState {
        private final List<FoliaChunkWork> work;
        private final int totalChunks;
        private int index = 0;
        private int chunkCount = 0;

        private FoliaRollbackBatchState(List<FoliaChunkWork> work, int totalChunks) {
            this.work = work;
            this.totalChunks = totalChunks;
        }

        private boolean hasNext() {
            return index < work.size();
        }

        private FoliaChunkWork next() {
            return work.get(index++);
        }

        private FoliaChunkWork peek() {
            return work.get(index);
        }
    }

    private static final class FoliaChunkWork {
        private final long chunkKey;
        private final int chunkX;
        private final int chunkZ;
        private final int worldId;
        private final World world;
        private final HashMap<Long, ArrayList<Object[]>> blockList;
        private final HashMap<Long, ArrayList<Object[]>> itemList;
        private final boolean lastWorldForChunk;

        private FoliaChunkWork(long chunkKey, int chunkX, int chunkZ, int worldId, World world, HashMap<Long, ArrayList<Object[]>> blockList, HashMap<Long, ArrayList<Object[]>> itemList, boolean lastWorldForChunk) {
            this.chunkKey = chunkKey;
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.worldId = worldId;
            this.world = world;
            this.blockList = blockList;
            this.itemList = itemList;
            this.lastWorldForChunk = lastWorldForChunk;
        }
    }

    private static boolean awaitChunkTasks(List<CompletableFuture<Boolean>> futures, int preview) throws InterruptedException {
        if (futures.isEmpty()) {
            return true;
        }

        int sleepTime = 0;
        while (true) {
            boolean allDone = true;
            for (CompletableFuture<Boolean> future : futures) {
                if (!future.isDone()) {
                    allDone = false;
                    break;
                }
            }

            if (allDone) {
                break;
            }

            int delay = preview == 1 ? 1 : 5;
            sleepTime += delay;
            if (sleepTime > 300000) {
                return false;
            }
            Thread.sleep(delay);
        }

        for (CompletableFuture<Boolean> future : futures) {
            Boolean result;
            try {
                result = future.getNow(Boolean.FALSE);
            }
            catch (Exception e) {
                return false;
            }

            if (!Boolean.TRUE.equals(result)) {
                return false;
            }
        }

        return true;
    }

}
