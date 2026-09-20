package net.coreprotect.database.rollback;

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
import net.coreprotect.model.lookup.EntityLookupContext;
import net.coreprotect.model.rollback.RollbackUpdateTargets;
import net.coreprotect.paper.PaperAdapter;
import net.coreprotect.thread.Scheduler;
import net.coreprotect.thread.TickTimeMonitor;
import net.coreprotect.utility.*;
import net.coreprotect.utility.Color;
import org.bukkit.*;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.sql.Statement;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class Rollback extends RollbackUtil {

    private static final long ROLLBACK_BATCH_BUDGET_BASELINE_NANOS = 25_000_000L;
    private static final long ROLLBACK_BATCH_BUDGET_FLOOR_NANOS = 20_000_000L;
    private static final long ROLLBACK_BATCH_BUDGET_CEILING_NANOS = 50_000_000L;
    private static final long ROLLBACK_BATCH_TICK_YIELD_NANOS = 50_000_000L;
    private static final int CHUNK_PREFETCH_DISTANCE = 8;
    private static final long ROLLBACK_STALL_MILLIS = 300_000L;
    private static final long CONTEXT_CLOSE_TIMEOUT_MILLIS = 60_000L;
    private static final long SHUTDOWN_FINISH_GRACE_MILLIS = 2_000L;
    private static final int FOLIA_MAX_CONCURRENT_BATCHES = 4;
    private static final int FOLIA_GROUP_CELL_SHIFT = 3;
    private static final Set<RollbackRun> ACTIVE_RUNS = ConcurrentHashMap.newKeySet();
    private static volatile boolean shutdownRequested;
    private static volatile long shutdownDeadline;

    public static List<String[]> performRollbackRestore(Statement statement, CommandSender user, List<String> checkUuids, List<String> checkUsers, String timeString, List<Object> restrictList, Map<Object, Boolean> excludeList, List<String> excludeUserList, List<Integer> actionList, Location location, Integer[] radius, long startTime, long endTime, boolean restrictWorld, boolean lookup, boolean verbose, final int rollbackType, final int preview) {
        return performRollbackRestore(statement, user, checkUuids, checkUsers, timeString, restrictList, excludeList, excludeUserList, actionList, EntityActionFilter.DEFAULT, location, radius, startTime, endTime, restrictWorld, lookup, verbose, rollbackType, preview);
    }

    public static List<String[]> performRollbackRestore(Statement statement, CommandSender user, List<String> checkUuids, List<String> checkUsers, String timeString, List<Object> restrictList, Map<Object, Boolean> excludeList, List<String> excludeUserList, List<Integer> actionList, EntityActionFilter entityActionFilter, Location location, Integer[] radius, long startTime, long endTime, boolean restrictWorld, boolean lookup, boolean verbose, final int rollbackType, final int preview) {
        return performRollbackRestore(statement, user, null, checkUuids, checkUsers, timeString, restrictList, excludeList, excludeUserList, actionList, entityActionFilter, location, radius, startTime, endTime, restrictWorld, lookup, verbose, rollbackType, preview, true);
    }

    /**
     * @param rollbackKey    The key the rollback was claimed under. Its progress counters and abort flag live under this key, which is removed when the
     *                       rollback ends. Null keeps them under the user name.
     * @param convertResults Whether to convert the looked up rows into the returned list. When false a completed rollback returns an empty list.
     */
    public static List<String[]> performRollbackRestore(Statement statement, CommandSender user, String rollbackKey, List<String> checkUuids, List<String> checkUsers, String timeString, List<Object> restrictList, Map<Object, Boolean> excludeList, List<String> excludeUserList, List<Integer> actionList, EntityActionFilter entityActionFilter, Location location, Integer[] radius, long startTime, long endTime, boolean restrictWorld, boolean lookup, boolean verbose, final int rollbackType, final int preview, boolean convertResults) {
        List<String[]> list = new ArrayList<>();
        EntitySpawnRollbackHandler.Context entitySpawnContext = null;
        String userString = "#server";
        if (user != null) {
            userString = user.getName();
        }
        RollbackRun run = startRun(rollbackKey != null ? rollbackKey : userString);

        try {
            long timeStart = System.currentTimeMillis();
            List<Object[]> lookupList = new ArrayList<>();
            EntityLookupContext entityContext = EntityLookupContext.legacy(Collections.emptySet(), Collections.emptySet());
            Integer exactEntityContainerId = user == null || !actionList.contains(5) ? null : ConfigHandler.lookupEntityContainer.get(user.getName());
            boolean rollbackContainerItems = false;
            List<Object> itemRestrictList = new ArrayList<>(restrictList);
            Map<Object, Boolean> itemExcludeList = new HashMap<>(excludeList);
            if (actionList.contains(LookupActions.BLOCK_PLACE)) {
                for (Object target : restrictList) {
                    if (target instanceof Material && !excludeList.containsKey(target) && BlockGroup.CONTAINERS.contains(target)) {
                        rollbackContainerItems = true;
                        itemRestrictList.clear();
                        itemExcludeList.clear();
                        break;
                    }
                }
            }
            boolean includeItemLookup = exactEntityContainerId == null && Config.getGlobal().ROLLBACK_ITEMS && !checkUsers.contains("#container")
                    && (actionList.isEmpty() || actionList.contains(LookupActions.CONTAINER) || rollbackContainerItems) && preview == 0;
            boolean entityLocationsReconciled = false;

            if ((!actionList.contains(LookupActions.CONTAINER) && !actionList.contains(5) && !checkUsers.contains("#container")) || exactEntityContainerId != null) {
                boolean includeEntitySpawns = entityActionFilter.includesAnySpawn(actionList, Config.getGlobal().ROLLBACK_ENTITIES);
                if (!ConfigHandler.databaseType.isDuckDB() && !lookup && rollbackType == 0 && radius != null && includeEntitySpawns) {
                    Set<UUID> databaseCandidates = includeItemLookup
                            ? EntitySpawnStatement.loadActiveUuids(statement.getConnection(), location, radius)
                            : EntitySpawnStatement.loadActiveUuids(statement.getConnection(), location, radius, startTime, endTime);
                    EntitySpawnTracking.LoadedEntityRadius loadedEntities = EntitySpawnTracking.findLoadedEntities(location, radius, databaseCandidates);
                    entityContext = EntityLookupContext.legacy(loadedEntities.getInside(), loadedEntities.getLoadedCandidates());
                    entityLocationsReconciled = true;
                }
                lookupList = Lookup.performLookupRaw(statement, user, checkUuids, checkUsers, restrictList, excludeList, excludeUserList, actionList, entityActionFilter, entityContext, location, radius, null, startTime, endTime, -1, -1, restrictWorld, lookup, exactEntityContainerId);
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
                rollbackLookupList.removeIf(row -> {
                    if ((Integer) row[8] == LookupActions.ENTITY_KILL && entityKillRecords.containsKey((Integer) row[7])) {
                        trackedKillList.add(row);
                        return true;
                    }
                    return false;
                });
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

            List<Object[]> itemList = new ArrayList<>();
            if (includeItemLookup) {
                List<Integer> itemActionList = new ArrayList<>(actionList);

                if (!itemActionList.contains(LookupActions.CONTAINER)) {
                    itemActionList.add(LookupActions.CONTAINER);
                }

                itemExcludeList.entrySet().removeIf(entry -> Boolean.TRUE.equals(entry.getValue()));
                if (!ConfigHandler.databaseType.isDuckDB() && !lookup && radius != null && !entityLocationsReconciled) {
                    Set<UUID> databaseCandidates = EntitySpawnStatement.loadActiveUuids(statement.getConnection(), location, radius);
                    EntitySpawnTracking.LoadedEntityRadius loadedEntities = EntitySpawnTracking.findLoadedEntities(location, radius, databaseCandidates);
                    entityContext = EntityLookupContext.legacy(loadedEntities.getInside(), loadedEntities.getLoadedCandidates());
                }
                itemList = Lookup.performLookupRaw(statement, user, checkUuids, checkUsers, itemRestrictList, itemExcludeList, excludeUserList, itemActionList, EntityActionFilter.DEFAULT, entityContext, location, radius, null, startTime, endTime, -1, -1, restrictWorld, lookup, null);
                if (itemList == null) {
                    sendAborted(user);
                    return null;
                }

                itemList.removeIf(row -> {
                    if (row.length > 15 && row[14] instanceof Integer && (Integer) row[14] == InventorySources.ENTITY_CONTAINER) {
                        if (inventoryRollback) {
                            entityContainerInventoryTrackingRowIds.add((Integer) row[15]);
                        }
                        else {
                            entityContainerList.add(row);
                            entityContainerTrackingRowIds.add((Integer) row[15]);
                            return true;
                        }
                    }
                    return false;
                });
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

            if (isRollbackAborted(run.key)) {
                sendAborted(user);
                return null;
            }

            LinkedHashSet<Integer> worldList = new LinkedHashSet<>();
            TreeMap<Long, Integer> chunkList = new TreeMap<>();
            HashMap<Integer, HashMap<Long, ArrayList<Object[]>>> dataList = new HashMap<>();
            HashMap<Integer, HashMap<Long, ArrayList<Object[]>>> itemDataList = new HashMap<>();
            Set<Integer> inventoryUserUuids = new HashSet<>();
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

                    String rowUser = UserStatement.getName(statement.getConnection(), userId);
                    if (inventoryRollback && inventoryUserUuids.add(userId) && rowUser != null && !rowUser.isEmpty()) {
                        UserStatement.getUuid(statement.getConnection(), rowUser);
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
            entitySpawnContext = EntitySpawnRollbackHandler.prepare(spawnList, entitySpawnRecords, trackedKillList, entityKillRecords, entityKillData, entityContainerList, rollbackType, inventoryRollback, preview, userString, run.key, location, radius);
            if (rollbackType == 1) {
                entitySpawnContext.reverseWork();
            }
            addEntitySpawnChunks(entitySpawnContext, worldList, chunkList, location);

            if (user != null && verbose && preview == 0 && !actionList.contains(LookupActions.ITEM)) {
                Integer chunks = chunkList.size();
                Chat.sendMessage(user, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.ROLLBACK_CHUNKS_FOUND, chunks.toString(), (chunks == 1 ? Selector.FIRST : Selector.SECOND)));
            }

            RollbackBlockDataCache blockDataCache = new RollbackBlockDataCache();
            List<Entry<Long, Integer>> sortedChunks = new ArrayList<>(DatabaseUtils.entriesSortedByValues(chunkList));
            if (entitySpawnContext.isCancelled()) {
                sendAborted(user);
                return null;
            }
            RollbackPublisher publisher = null;
            if (preview == 0) {
                if (Consumer.isPersistenceHalted()) {
                    entitySpawnContext.cancel();
                    sendAborted(user);
                    return null;
                }
                publisher = new RollbackPublisher(userString, location, rollbackType, actionList.contains(LookupActions.ITEM), entitySpawnContext);
            }

            if (ConfigHandler.isFolia) {
                chunkCount += processFoliaChunks(run, sortedChunks, worldList, dataList, itemDataList, rollbackType, preview, user, inventoryRollback, verbose, actionList, blockDataCache, entitySpawnContext, publisher);
            } else {
                chunkCount += processBukkitChunks(run, sortedChunks, worldList, dataList, itemDataList, rollbackType, preview, user, inventoryRollback, verbose, actionList, blockDataCache, entitySpawnContext, publisher);
            }
            if (publisher != null && !entitySpawnContext.isCancelled()) {
                publisher.publishRemaining(dataList, itemDataList);
            }
            if (!entitySpawnContext.completeDirectTransitions()) {
                entitySpawnContext.cancel();
            }

            chunkList.clear();
            dataList.clear();
            itemDataList.clear();

            addRollbackCounts(run.key, entitySpawnContext.getItemCount(), 0, entitySpawnContext.getEntityCount());
            if (entitySpawnContext.isCancelled()) {
                sendAborted(user);
                return null;
            }
            int[] rollbackHashData = ConfigHandler.rollbackHash.get(run.key);
            int itemCount = rollbackHashData[0];
            int blockCount = rollbackHashData[1];
            int entityCount = rollbackHashData[2];
            long timeFinish = System.currentTimeMillis();
            double totalSeconds = (timeFinish - timeStart) / 1000.0;

            if (user != null) {
                RollbackComplete.output(user, location, checkUsers, restrictList, excludeList, excludeUserList, actionList, timeString, chunkCount, totalSeconds, itemCount, blockCount, entityCount, rollbackType, radius, verbose, restrictWorld, preview);
            }

            if (convertResults) {
                list = LookupConverter.convertRawLookup(statement, lookupList);
            }
            return list;
        }
        catch (Exception e) {
            ErrorReporter.report(e);
        }
        finally {
            if (entitySpawnContext != null) {
                entitySpawnContext.close(run.closeTimeoutMillis());
            }
            finishRun(run, rollbackKey != null);
        }

        return null;
    }

    /**
     * Aborts every running rollback and restore so the plugin can shut down. Chunks that were already applied keep their rolled_back
     * flags. Chunks a rollback never reached stay unflagged.
     *
     * @param timeoutMillis
     *            How long rollbacks may keep waiting on world work that is already scheduled
     * @return True if every rollback finished in time
     */
    public static boolean abortAllForShutdown(long timeoutMillis) {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(Math.max(0L, timeoutMillis));
        shutdownDeadline = deadline;
        shutdownRequested = true;
        List<RollbackRun> runs = new ArrayList<>(ACTIVE_RUNS);
        for (RollbackRun run : runs) {
            run.abort(deadline);
        }

        long finishDeadline = deadline + TimeUnit.MILLISECONDS.toNanos(SHUTDOWN_FINISH_GRACE_MILLIS);
        boolean finished = true;
        for (RollbackRun run : runs) {
            try {
                if (!run.finished.await(Math.max(0L, finishDeadline - System.nanoTime()), TimeUnit.NANOSECONDS)) {
                    finished = false;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }

        return finished;
    }

    private static RollbackRun startRun(String rollbackKey) {
        RollbackRun run = new RollbackRun(rollbackKey);
        ConfigHandler.rollbackHash.put(rollbackKey, new int[]{0, 0, 0, 0, 0});
        ACTIVE_RUNS.add(run);
        long deadline = shutdownDeadline;
        if (shutdownRequested && System.nanoTime() - deadline < 0) {
            run.abort(deadline);
        }

        return run;
    }

    private static void finishRun(RollbackRun run, boolean removeKey) {
        ACTIVE_RUNS.remove(run);
        if (removeKey) {
            ConfigHandler.rollbackHash.remove(run.key);
        }
        run.finished.countDown();
    }

    static void addRollbackCounts(String rollbackKey, int items, int blocks, int entities) {
        ConfigHandler.rollbackHash.computeIfPresent(rollbackKey, (key, data) -> new int[]{data[0] + items, data[1] + blocks, data[2] + entities, data[3], data[4]});
    }

    static void abortRollback(String rollbackKey) {
        ConfigHandler.rollbackHash.computeIfPresent(rollbackKey, (key, data) -> new int[]{data[0], data[1], data[2], 2, data[4]});
    }

    static boolean isRollbackAborted(String rollbackKey) {
        int[] data = ConfigHandler.rollbackHash.get(rollbackKey);
        return data != null && data[3] == 2;
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

    private static void queueRollbackRows(String user, Location location, List<Object[]> blockRows, List<Object[]> itemRows, int rollbackType, boolean inventoryRollback) {
        if (inventoryRollback) {
            List<Object[]> blockList = new ArrayList<>();
            List<Object[]> inventoryList = new ArrayList<>();
            List<Object[]> containerList = new ArrayList<>();
            List<Object[]> entityContainerInventoryUpdates = new ArrayList<>();
            for (Object[] data : itemRows) {
                int table = (Integer) data[14];
                if (table == RollbackUpdateTargets.INVENTORY_ITEM) {
                    inventoryList.add(data);
                } else if (table == RollbackUpdateTargets.CONTAINER) {
                    containerList.add(data);
                } else if (table == InventorySources.ENTITY_CONTAINER) {
                    entityContainerInventoryUpdates.add(data);
                } else {
                    blockList.add(data);
                }
            }
            if (!inventoryList.isEmpty()) {
                Queue.queueRollbackUpdate(user, location, inventoryList, Process.INVENTORY_ROLLBACK_UPDATE, rollbackType);
            }
            if (!containerList.isEmpty()) {
                Queue.queueRollbackUpdate(user, location, containerList, Process.INVENTORY_CONTAINER_ROLLBACK_UPDATE, rollbackType);
            }
            if (!entityContainerInventoryUpdates.isEmpty()) {
                Queue.queueEntityContainerRollbackUpdate(user, location, entityContainerInventoryUpdates, rollbackType, true);
            }
            if (!blockList.isEmpty()) {
                Queue.queueRollbackUpdate(user, location, blockList, Process.BLOCK_INVENTORY_ROLLBACK_UPDATE, rollbackType);
            }
        } else {
            if (!blockRows.isEmpty()) {
                Queue.queueRollbackUpdate(user, location, blockRows, Process.ROLLBACK_UPDATE, rollbackType);
            }
            if (!itemRows.isEmpty()) {
                Queue.queueRollbackUpdate(user, location, itemRows, Process.CONTAINER_ROLLBACK_UPDATE, rollbackType);
            }
        }
    }

    private static int processFoliaChunks(RollbackRun run, List<Entry<Long, Integer>> sortedChunks, LinkedHashSet<Integer> worldList, HashMap<Integer, HashMap<Long, ArrayList<Object[]>>> dataList, HashMap<Integer, HashMap<Long, ArrayList<Object[]>>> itemDataList, int rollbackType, int preview, CommandSender user, boolean inventoryRollback, boolean verbose, List<Integer> actionList, RollbackBlockDataCache blockDataCache, EntitySpawnRollbackHandler.Context entitySpawnContext, RollbackPublisher publisher) throws InterruptedException {
        FoliaRollbackState state = new FoliaRollbackState(run.key, rollbackType, preview, user, inventoryRollback, verbose, actionList, blockDataCache, entitySpawnContext, sortedChunks.size());
        List<FoliaChunkWork> work = buildFoliaChunkWork(sortedChunks, worldList, dataList, itemDataList, entitySpawnContext);
        for (FoliaChunkWork chunkWork : work) {
            if (chunkWork.world == null && chunkWork.lastWorldForChunk && !completeChunk(run.key, state.chunkCount.incrementAndGet(), state.totalChunks, verbose, user, preview, actionList)) {
                Chat.console(Phrase.build(Phrase.ROLLBACK_ABORTED));
                entitySpawnContext.cancel();
                return state.chunkCount.get();
            }
        }

        LinkedList<FoliaChunkGroup> groups = groupFoliaChunkWork(work);
        int maxBatches = inventoryRollback ? 1 : FOLIA_MAX_CONCURRENT_BATCHES;
        List<FoliaBatch> batches = new ArrayList<>();
        int delay = preview == 1 ? 1 : 5;
        long lastProgress = System.nanoTime();
        boolean failed = false;
        while (true) {
            boolean stopping = failed || entitySpawnContext.isCancelled();
            Iterator<FoliaBatch> batchIterator = batches.iterator();
            while (batchIterator.hasNext()) {
                FoliaBatch batch = batchIterator.next();
                if (!batch.isSettled(stopping)) {
                    continue;
                }

                batchIterator.remove();
                batch.group.batch = null;
                lastProgress = System.nanoTime();
                if (publisher != null) {
                    publisher.publish(batch.processedRows);
                }
                if (!batch.isSuccessful()) {
                    failed = true;
                    entitySpawnContext.cancel();
                }
            }

            stopping = failed || entitySpawnContext.isCancelled();
            if (!stopping) {
                int loading = 0;
                Iterator<FoliaChunkGroup> groupIterator = groups.iterator();
                while (groupIterator.hasNext() && batches.size() < maxBatches && batches.size() + loading < maxBatches * 2) {
                    FoliaChunkGroup group = groupIterator.next();
                    if (group.batch != null) {
                        continue;
                    }
                    if (!group.hasNext()) {
                        groupIterator.remove();
                        continue;
                    }
                    if (!prepareFoliaGroup(state, group)) {
                        loading++;
                        continue;
                    }

                    FoliaBatch batch = new FoliaBatch(group);
                    group.batch = batch;
                    batches.add(batch);
                    lastProgress = System.nanoTime();
                    scheduleFoliaBatch(state, batch, 0);
                }
            }

            if (batches.isEmpty() && (stopping || groups.isEmpty())) {
                break;
            }
            if (run.isWaitExpired() || System.nanoTime() - lastProgress > TimeUnit.MILLISECONDS.toNanos(ROLLBACK_STALL_MILLIS)) {
                for (FoliaBatch batch : batches) {
                    if (!batch.state.compareAndSet(FoliaBatch.NEW, FoliaBatch.SKIPPED) && batch.completion.isDone() && publisher != null) {
                        publisher.publish(batch.processedRows);
                    }
                }
                failed = true;
                break;
            }
            Thread.sleep(delay);
        }

        if (failed) {
            Chat.console(Phrase.build(Phrase.ROLLBACK_ABORTED));
            entitySpawnContext.cancel();
        }

        return state.chunkCount.get();
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

    /**
     * Splits the chunk work into spatial clusters. Chunks in neighbouring 8x8 chunk cells share a cluster, so separate clusters are at
     * least 9 chunks apart and can be applied in parallel without touching each other. Clusters keep the distance order of their first
     * chunk, and chunks keep their distance order inside a cluster.
     */
    private static LinkedList<FoliaChunkGroup> groupFoliaChunkWork(List<FoliaChunkWork> work) {
        Map<Integer, Set<Long>> occupiedCells = new HashMap<>();
        for (FoliaChunkWork chunkWork : work) {
            if (chunkWork.world != null) {
                occupiedCells.computeIfAbsent(chunkWork.worldId, key -> new HashSet<>()).add(cellKey(chunkWork.chunkX >> FOLIA_GROUP_CELL_SHIFT, chunkWork.chunkZ >> FOLIA_GROUP_CELL_SHIFT));
            }
        }

        LinkedList<FoliaChunkGroup> groups = new LinkedList<>();
        Map<Integer, Map<Long, FoliaChunkGroup>> cellGroups = new HashMap<>();
        for (FoliaChunkWork chunkWork : work) {
            if (chunkWork.world == null) {
                continue;
            }

            Map<Long, FoliaChunkGroup> worldGroups = cellGroups.computeIfAbsent(chunkWork.worldId, key -> new HashMap<>());
            long cell = cellKey(chunkWork.chunkX >> FOLIA_GROUP_CELL_SHIFT, chunkWork.chunkZ >> FOLIA_GROUP_CELL_SHIFT);
            FoliaChunkGroup group = worldGroups.get(cell);
            if (group == null) {
                group = new FoliaChunkGroup();
                groups.add(group);
                Set<Long> worldCells = occupiedCells.get(chunkWork.worldId);
                Deque<Long> pendingCells = new ArrayDeque<>();
                worldGroups.put(cell, group);
                pendingCells.add(cell);
                while (!pendingCells.isEmpty()) {
                    long currentCell = pendingCells.poll();
                    int cellX = (int) currentCell;
                    int cellZ = (int) (currentCell >> 32);
                    for (int offsetX = -1; offsetX <= 1; offsetX++) {
                        for (int offsetZ = -1; offsetZ <= 1; offsetZ++) {
                            long neighbourCell = cellKey(cellX + offsetX, cellZ + offsetZ);
                            if (worldCells.contains(neighbourCell) && worldGroups.putIfAbsent(neighbourCell, group) == null) {
                                pendingCells.add(neighbourCell);
                            }
                        }
                    }
                }
            }
            group.work.add(chunkWork);
        }

        return groups;
    }

    private static long cellKey(int cellX, int cellZ) {
        return cellX & 0xffffffffL | (cellZ & 0xffffffffL) << 32;
    }

    private static boolean prepareFoliaGroup(FoliaRollbackState state, FoliaChunkGroup group) {
        if (group.headLoadIndex != group.index) {
            group.headLoadIndex = group.index;
            group.headLoad = requestChunk(state, group.peek());
            int prefetchLimit = Math.min(group.index + CHUNK_PREFETCH_DISTANCE, group.work.size());
            for (int index = Math.max(group.prefetchIndex, group.index + 1); index < prefetchLimit; index++) {
                requestChunk(state, group.work.get(index));
            }
            group.prefetchIndex = Math.max(group.prefetchIndex, prefetchLimit);
        }

        return group.headLoad == null || group.headLoad.isDone();
    }

    private static CompletableFuture<Chunk> requestChunk(FoliaRollbackState state, FoliaChunkWork work) {
        if (work.world.isChunkLoaded(work.chunkX, work.chunkZ) || !state.needsChunk(work)) {
            return null;
        }

        return work.world.getChunkAtAsync(work.chunkX, work.chunkZ);
    }

    private static void scheduleFoliaBatch(FoliaRollbackState state, FoliaBatch batch, int delay) {
        FoliaChunkWork head = batch.group.peek();
        Location chunkLocation = new Location(head.world, (head.chunkX << 4), 0, (head.chunkZ << 4));
        Scheduler.scheduleSyncDelayedTask(CoreProtect.getInstance(), () -> runFoliaBatch(state, batch), chunkLocation, delay);
    }

    private static void runFoliaBatch(FoliaRollbackState state, FoliaBatch batch) {
        if (!batch.state.compareAndSet(FoliaBatch.NEW, FoliaBatch.RUNNING)) {
            return;
        }

        try {
            FoliaChunkGroup group = batch.group;
            if (state.entitySpawnContext.isCancelled()) {
                batch.completion.complete(false);
                return;
            }
            if (!state.claimRegionTick(group)) {
                batch.state.set(FoliaBatch.NEW);
                scheduleFoliaBatch(state, batch, 1);
                return;
            }

            long batchBudget = adaptiveBatchBudgetNanos();
            long batchStart = System.nanoTime();
            do {
                FoliaChunkWork work = group.next();
                if (!processChunkWorld(work.chunkX, work.chunkZ, work.chunkKey, work.worldId, work.blockList, work.itemList, state.rollbackType, state.preview, state.rollbackKey, state.user, work.world, state.inventoryRollback, state.blockDataCache, state.entitySpawnContext, batch.pendingTasks, batch.processedRows)) {
                    batch.completion.complete(false);
                    return;
                }

                if (work.lastWorldForChunk && !completeChunk(state.rollbackKey, state.chunkCount.incrementAndGet(), state.totalChunks, state.verbose, state.user, state.preview, state.actionList)) {
                    batch.completion.complete(false);
                    return;
                }
            }
            while (canContinueFoliaBatch(state, group, batchStart, batchBudget));

            batch.completion.complete(true);
        } catch (Exception e) {
            state.entitySpawnContext.cancel();
            ErrorReporter.report(e);
            batch.completion.complete(false);
        }
    }

    private static int processBukkitChunks(RollbackRun run, List<Entry<Long, Integer>> sortedChunks, LinkedHashSet<Integer> worldList, HashMap<Integer, HashMap<Long, ArrayList<Object[]>>> dataList, HashMap<Integer, HashMap<Long, ArrayList<Object[]>>> itemDataList, int rollbackType, int preview, CommandSender user, boolean inventoryRollback, boolean verbose, List<Integer> actionList, RollbackBlockDataCache blockDataCache, EntitySpawnRollbackHandler.Context entitySpawnContext, RollbackPublisher publisher) throws InterruptedException {
        HashMap<Integer, World> worldMap = getRollbackWorlds(worldList);
        RollbackBatchState batchState = new RollbackBatchState(sortedChunks);
        if (!batchState.hasNext()) {
            return 0;
        }

        CompletableFuture<Boolean> completion = new CompletableFuture<>();
        scheduleChunkBatchTask(batchState, worldMap, dataList, itemDataList, rollbackType, preview, run.key, user, inventoryRollback, verbose, actionList, blockDataCache, entitySpawnContext, publisher, completion, 0);
        boolean completed = awaitRollbackCompletion(run, completion, batchState, preview, publisher);
        if (publisher != null) {
            publisher.publishCompleted();
        }
        if (!completed || !awaitChunkTasks(run, entitySpawnContext.drainPending(), preview)) {
            Chat.console(Phrase.build(Phrase.ROLLBACK_ABORTED));
            entitySpawnContext.cancel();
        }

        return batchState.chunkCount;
    }

    private static void scheduleChunkBatchTask(RollbackBatchState batchState, HashMap<Integer, World> worldMap, HashMap<Integer, HashMap<Long, ArrayList<Object[]>>> dataList, HashMap<Integer, HashMap<Long, ArrayList<Object[]>>> itemDataList, int rollbackType, int preview, String rollbackKey, CommandSender user, boolean inventoryRollback, boolean verbose, List<Integer> actionList, RollbackBlockDataCache blockDataCache, EntitySpawnRollbackHandler.Context entitySpawnContext, RollbackPublisher publisher, CompletableFuture<Boolean> completion, int delay) {
        Scheduler.scheduleSyncDelayedTask(CoreProtect.getInstance(), () -> {
            batchState.running = true;
            try {
                if (completion.isDone()) {
                    return;
                }
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

                    if (!processChunkEntry(entry, worldMap, dataList, itemDataList, rollbackType, preview, rollbackKey, user, inventoryRollback, blockDataCache, entitySpawnContext, publisher == null ? null : publisher.completed)) {
                        completion.complete(false);
                        return;
                    }

                    if (!completeChunk(rollbackKey, batchState.chunkCount, batchState.totalChunks(), verbose, user, preview, actionList)) {
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
                    scheduleChunkBatchTask(batchState, worldMap, dataList, itemDataList, rollbackType, preview, rollbackKey, user, inventoryRollback, verbose, actionList, blockDataCache, entitySpawnContext, publisher, completion, nextDelay);
                } else {
                    completion.complete(true);
                }
            }
            catch (Exception e) {
                entitySpawnContext.cancel();
                ErrorReporter.report(e);
                completion.complete(false);
            }
            finally {
                batchState.running = false;
            }
        }, delay);
    }

    private static boolean awaitRollbackCompletion(RollbackRun run, CompletableFuture<Boolean> completion, RollbackBatchState batchState, int preview, RollbackPublisher publisher) throws InterruptedException {
        int delay = preview == 1 ? 1 : 5;
        int lastChunkCount = -1;
        long stalledTime = 0;

        while (!completion.isDone()) {
            if (publisher != null) {
                publisher.publishCompleted();
            }
            if ((run.isAborted() && !batchState.running) || run.isWaitExpired()) {
                completion.complete(false);
                return false;
            }

            int chunkCount = batchState.chunkCount;
            if (chunkCount != lastChunkCount) {
                lastChunkCount = chunkCount;
                stalledTime = 0;
            }

            stalledTime += delay;
            if (stalledTime > ROLLBACK_STALL_MILLIS) {
                completion.complete(false);
                return false;
            }
            Thread.sleep(delay);
        }

        return isTrue(completion);
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

    private static boolean canContinueFoliaBatch(FoliaRollbackState state, FoliaChunkGroup group, long batchStart, long batchBudget) {
        if (!group.hasNext() || (System.nanoTime() - batchStart) >= batchBudget) {
            return false;
        }

        FoliaChunkWork nextWork = group.peek();
        return PaperAdapter.ADAPTER.isOwnedByCurrentRegion(nextWork.world, nextWork.chunkX, nextWork.chunkZ) && (nextWork.world.isChunkLoaded(nextWork.chunkX, nextWork.chunkZ) || !state.needsChunk(nextWork));
    }

    private static boolean processChunkEntry(Entry<Long, Integer> entry, HashMap<Integer, World> worldMap, HashMap<Integer, HashMap<Long, ArrayList<Object[]>>> dataList, HashMap<Integer, HashMap<Long, ArrayList<Object[]>>> itemDataList, int rollbackType, int preview, String rollbackKey, CommandSender user, boolean inventoryRollback, RollbackBlockDataCache blockDataCache, EntitySpawnRollbackHandler.Context entitySpawnContext, Collection<ChunkRows> processedRows) {
        long chunkKey = entry.getKey();
        int chunkX = getChunkX(entry);
        int chunkZ = getChunkZ(entry);

        for (Entry<Integer, World> rollbackWorlds : worldMap.entrySet()) {
            int rollbackWorldId = rollbackWorlds.getKey();
            World bukkitRollbackWorld = rollbackWorlds.getValue();
            HashMap<Long, ArrayList<Object[]>> blockList = dataList.get(rollbackWorldId);
            HashMap<Long, ArrayList<Object[]>> itemList = itemDataList.get(rollbackWorldId);

            if (!hasChunkWork(rollbackWorldId, chunkKey, dataList, itemDataList, entitySpawnContext)) {
                continue;
            }
            if (!processChunkWorld(chunkX, chunkZ, chunkKey, rollbackWorldId, blockList, itemList, rollbackType, preview, rollbackKey, user, bukkitRollbackWorld, inventoryRollback, blockDataCache, entitySpawnContext, null, processedRows)) {
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

    private static boolean completeChunk(String rollbackKey, Integer chunkCount, int totalChunks, boolean verbose, CommandSender user, int preview, List<Integer> actionList) {
        if (isRollbackAborted(rollbackKey)) {
            return false;
        }

        if (verbose && user != null && preview == 0 && !actionList.contains(LookupActions.ITEM)) {
            Integer chunks = totalChunks;
            Chat.sendMessage(user, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.ROLLBACK_CHUNKS_MODIFIED, chunkCount.toString(), chunks.toString(), (chunks == 1 ? Selector.FIRST : Selector.SECOND)));
        }

        return true;
    }

    private static boolean processChunkWorld(int chunkX, int chunkZ, long chunkKey, int worldId, HashMap<Long, ArrayList<Object[]>> blockList, HashMap<Long, ArrayList<Object[]>> itemList, int rollbackType, int preview, String rollbackKey, CommandSender user, World world, boolean inventoryRollback, RollbackBlockDataCache blockDataCache, EntitySpawnRollbackHandler.Context entitySpawnContext, List<CompletableFuture<Boolean>> pendingTasks, Collection<ChunkRows> processedRows) {
        if (entitySpawnContext.isCancelled()) {
            return false;
        }
        ArrayList<Object[]> blockData = blockList != null ? blockList.getOrDefault(chunkKey, new ArrayList<>()) : new ArrayList<>();
        ArrayList<Object[]> itemData = itemList != null ? itemList.getOrDefault(chunkKey, new ArrayList<>()) : new ArrayList<>();
        List<EntitySpawnRollbackHandler.Work> entitySpawnWork = entitySpawnContext.getWork(worldId, chunkKey);
        if (!entitySpawnWork.isEmpty()) {
            CompletableFuture<Boolean> entitySpawnFuture = EntitySpawnRollbackHandler.processChunk(entitySpawnContext, world, chunkX, chunkZ, entitySpawnWork);
            if (pendingTasks != null) {
                pendingTasks.add(entitySpawnFuture);
            } else {
                entitySpawnContext.addPending(entitySpawnFuture);
            }
            if (entitySpawnFuture.isDone() && !Boolean.TRUE.equals(entitySpawnFuture.getNow(Boolean.FALSE))) {
                return false;
            }
        }
        if (blockData.isEmpty() && itemData.isEmpty()) {
            return true;
        }
        Player rollbackPlayer = user instanceof Player ? (Player) user : null;
        try {
            return RollbackProcessor.processChunk(chunkX, chunkZ, chunkKey, blockData, itemData, rollbackType, preview, rollbackKey, rollbackPlayer, world, inventoryRollback, blockDataCache, pendingTasks);
        } finally {
            if (processedRows != null) {
                processedRows.add(new ChunkRows(blockData, itemData));
            }
        }
    }

    private static boolean isTrue(CompletableFuture<Boolean> future) {
        try {
            return Boolean.TRUE.equals(future.getNow(Boolean.FALSE));
        } catch (Exception e) {
            return false;
        }
    }

    private static final class RollbackBatchState {
        private final List<Entry<Long, Integer>> sortedChunks;
        private int index = 0;
        private int prefetchIndex = 0;
        private long nanosSinceTickYield = 0L;
        private volatile int chunkCount = 0;
        private volatile boolean running;

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

    private static final class FoliaRollbackState {
        private final String rollbackKey;
        private final int rollbackType;
        private final int preview;
        private final CommandSender user;
        private final boolean inventoryRollback;
        private final boolean verbose;
        private final List<Integer> actionList;
        private final RollbackBlockDataCache blockDataCache;
        private final EntitySpawnRollbackHandler.Context entitySpawnContext;
        private final int totalChunks;
        private final AtomicInteger chunkCount = new AtomicInteger();
        private final RegionRun[] regionRuns = new RegionRun[FOLIA_MAX_CONCURRENT_BATCHES * 2];
        private int nextRegionRun;

        private FoliaRollbackState(String rollbackKey, int rollbackType, int preview, CommandSender user, boolean inventoryRollback, boolean verbose, List<Integer> actionList, RollbackBlockDataCache blockDataCache, EntitySpawnRollbackHandler.Context entitySpawnContext, int totalChunks) {
            this.rollbackKey = rollbackKey;
            this.rollbackType = rollbackType;
            this.preview = preview;
            this.user = user;
            this.inventoryRollback = inventoryRollback;
            this.verbose = verbose;
            this.actionList = actionList;
            this.blockDataCache = blockDataCache;
            this.entitySpawnContext = entitySpawnContext;
            this.totalChunks = totalChunks;
        }

        private boolean needsChunk(FoliaChunkWork work) {
            if (inventoryRollback) {
                return false;
            }

            return containsChunk(work.blockList, work.chunkKey) || containsChunk(work.itemList, work.chunkKey) || EntitySpawnRollbackHandler.requiresChunk(entitySpawnContext, entitySpawnContext.getWork(work.worldId, work.chunkKey));
        }

        /**
         * Lets one cluster run per region per server tick. Clusters that land in the same region would otherwise stack their batch budgets
         * into a single region tick.
         */
        private boolean claimRegionTick(FoliaChunkGroup group) {
            FoliaChunkWork head = group.peek();
            int tick = Bukkit.getCurrentTick();
            RegionRun[] recentRuns;
            synchronized (regionRuns) {
                recentRuns = regionRuns.clone();
            }

            for (RegionRun recentRun : recentRuns) {
                if (recentRun != null && recentRun.tick == tick && recentRun.group != group && PaperAdapter.ADAPTER.isOwnedByCurrentRegion(recentRun.world, recentRun.chunkX, recentRun.chunkZ)) {
                    return false;
                }
            }

            synchronized (regionRuns) {
                regionRuns[nextRegionRun] = new RegionRun(group, head.world, head.chunkX, head.chunkZ, tick);
                nextRegionRun = (nextRegionRun + 1) % regionRuns.length;
            }
            return true;
        }
    }

    private static final class RegionRun {
        private final FoliaChunkGroup group;
        private final World world;
        private final int chunkX;
        private final int chunkZ;
        private final int tick;

        private RegionRun(FoliaChunkGroup group, World world, int chunkX, int chunkZ, int tick) {
            this.group = group;
            this.world = world;
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.tick = tick;
        }
    }

    private static final class FoliaChunkGroup {
        private final List<FoliaChunkWork> work = new ArrayList<>();
        private int index;
        private int prefetchIndex;
        private int headLoadIndex = -1;
        private CompletableFuture<Chunk> headLoad;
        private FoliaBatch batch;

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

    private static final class FoliaBatch {
        private static final int NEW = 0;
        private static final int RUNNING = 1;
        private static final int SKIPPED = 2;

        private final FoliaChunkGroup group;
        private final AtomicInteger state = new AtomicInteger(NEW);
        private final CompletableFuture<Boolean> completion = new CompletableFuture<>();
        private final List<CompletableFuture<Boolean>> pendingTasks = new ArrayList<>();
        private final List<ChunkRows> processedRows = new ArrayList<>();

        private FoliaBatch(FoliaChunkGroup group) {
            this.group = group;
        }

        private boolean isSettled(boolean stopping) {
            if (stopping && state.compareAndSet(NEW, SKIPPED)) {
                return true;
            }
            if (!completion.isDone()) {
                return false;
            }

            for (CompletableFuture<Boolean> task : pendingTasks) {
                if (!task.isDone()) {
                    return false;
                }
            }
            return true;
        }

        private boolean isSuccessful() {
            if (state.get() == SKIPPED) {
                return true;
            }
            if (!isTrue(completion)) {
                return false;
            }

            for (CompletableFuture<Boolean> task : pendingTasks) {
                if (!isTrue(task)) {
                    return false;
                }
            }
            return true;
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

    private static final class ChunkRows {
        private final List<Object[]> blockRows;
        private final List<Object[]> itemRows;

        private ChunkRows(List<Object[]> blockRows, List<Object[]> itemRows) {
            this.blockRows = blockRows;
            this.itemRows = itemRows;
        }
    }

    private static final class RollbackPublisher {
        private final String user;
        private final Location location;
        private final int rollbackType;
        private final boolean inventoryRollback;
        private final EntitySpawnRollbackHandler.Context entitySpawnContext;
        private final ConcurrentLinkedQueue<ChunkRows> completed = new ConcurrentLinkedQueue<>();
        private final Set<List<Object[]>> published = Collections.newSetFromMap(new IdentityHashMap<>());

        private RollbackPublisher(String user, Location location, int rollbackType, boolean inventoryRollback, EntitySpawnRollbackHandler.Context entitySpawnContext) {
            this.user = user;
            this.location = location;
            this.rollbackType = rollbackType;
            this.inventoryRollback = inventoryRollback;
            this.entitySpawnContext = entitySpawnContext;
        }

        private void publishCompleted() {
            List<ChunkRows> rows = new ArrayList<>();
            ChunkRows chunkRows;
            while ((chunkRows = completed.poll()) != null) {
                rows.add(chunkRows);
            }
            publish(rows);
        }

        private void publishRemaining(HashMap<Integer, HashMap<Long, ArrayList<Object[]>>> dataList, HashMap<Integer, HashMap<Long, ArrayList<Object[]>>> itemDataList) {
            publishCompleted();
            List<ChunkRows> rows = new ArrayList<>();
            for (Entry<Integer, HashMap<Long, ArrayList<Object[]>>> worldRows : dataList.entrySet()) {
                HashMap<Long, ArrayList<Object[]>> worldItemRows = itemDataList.get(worldRows.getKey());
                for (Entry<Long, ArrayList<Object[]>> blockRows : worldRows.getValue().entrySet()) {
                    rows.add(new ChunkRows(blockRows.getValue(), worldItemRows.get(blockRows.getKey())));
                }
            }
            publish(rows);
        }

        private void publish(Collection<ChunkRows> rows) {
            List<Object[]> blockRows = new ArrayList<>();
            List<Object[]> itemRows = new ArrayList<>();
            for (ChunkRows chunkRows : rows) {
                if (published.add(chunkRows.blockRows)) {
                    blockRows.addAll(chunkRows.blockRows);
                    itemRows.addAll(chunkRows.itemRows);
                }
            }
            if (blockRows.isEmpty() && itemRows.isEmpty()) {
                return;
            }

            try {
                queueRollbackRows(user, location, blockRows, itemRows, rollbackType, inventoryRollback);
            } catch (Exception e) {
                entitySpawnContext.cancel();
                ErrorReporter.report(e);
            }
        }
    }

    private static final class RollbackRun {
        private final String key;
        private final CountDownLatch finished = new CountDownLatch(1);
        private volatile boolean aborted;
        private volatile long deadline;

        private RollbackRun(String key) {
            this.key = key;
        }

        private void abort(long deadline) {
            this.deadline = deadline;
            aborted = true;
            abortRollback(key);
        }

        private boolean isAborted() {
            return aborted;
        }

        private boolean isWaitExpired() {
            return aborted && System.nanoTime() - deadline >= 0;
        }

        private long closeTimeoutMillis() {
            if (!aborted) {
                return CONTEXT_CLOSE_TIMEOUT_MILLIS;
            }

            return Math.max(0L, TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime()));
        }
    }

    private static boolean awaitChunkTasks(RollbackRun run, List<CompletableFuture<Boolean>> futures, int preview) throws InterruptedException {
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
            if (sleepTime > ROLLBACK_STALL_MILLIS || run.isWaitExpired()) {
                return false;
            }
            Thread.sleep(delay);
        }

        for (CompletableFuture<Boolean> future : futures) {
            if (!isTrue(future)) {
                return false;
            }
        }

        return true;
    }

}
