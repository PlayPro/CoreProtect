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
import net.coreprotect.model.action.LookupActions;
import net.coreprotect.model.rollback.RollbackUpdateTargets;
import net.coreprotect.thread.Scheduler;
import net.coreprotect.utility.Chat;
import net.coreprotect.utility.Color;
import net.coreprotect.utility.DatabaseUtils;
import net.coreprotect.utility.WorldUtils;
import net.coreprotect.utility.ErrorReporter;

public class Rollback extends RollbackUtil {

    private static final long ROLLBACK_BATCH_BUDGET_NANOS = 25_000_000L;

    public static List<String[]> performRollbackRestore(Statement statement, CommandSender user, List<String> checkUuids, List<String> checkUsers, String timeString, List<Object> restrictList, Map<Object, Boolean> excludeList, List<String> excludeUserList, List<Integer> actionList, Location location, Integer[] radius, long startTime, long endTime, boolean restrictWorld, boolean lookup, boolean verbose, final int rollbackType, final int preview) {
        List<String[]> list = new ArrayList<>();

        try {
            long timeStart = System.currentTimeMillis();
            List<Object[]> lookupList = new ArrayList<>();

            if (!actionList.contains(LookupActions.CONTAINER) && !actionList.contains(5) && !checkUsers.contains("#container")) {
                lookupList = Lookup.performLookupRaw(statement, user, checkUuids, checkUsers, restrictList, excludeList, excludeUserList, actionList, location, radius, null, startTime, endTime, -1, -1, restrictWorld, lookup);
            }

            if (lookupList == null) {
                return null;
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
            if (Config.getGlobal().ROLLBACK_ITEMS && !checkUsers.contains("#container") && (actionList.size() == 0 || actionList.contains(LookupActions.CONTAINER) || ROLLBACK_ITEMS) && preview == 0) {
                List<Integer> itemActionList = new ArrayList<>(actionList);

                if (!itemActionList.contains(LookupActions.CONTAINER)) {
                    itemActionList.add(LookupActions.CONTAINER);
                }

                itemExcludeList.entrySet().removeIf(entry -> Boolean.TRUE.equals(entry.getValue()));
                itemList = Lookup.performLookupRaw(statement, user, checkUuids, checkUsers, itemRestrictList, itemExcludeList, excludeUserList, itemActionList, location, radius, null, startTime, endTime, -1, -1, restrictWorld, lookup);
            }

            LinkedHashSet<Integer> worldList = new LinkedHashSet<>();
            TreeMap<Long, Integer> chunkList = new TreeMap<>();
            HashMap<Integer, HashMap<Long, ArrayList<Object[]>>> dataList = new HashMap<>();
            HashMap<Integer, HashMap<Long, ArrayList<Object[]>>> itemDataList = new HashMap<>();
            boolean inventoryRollback = actionList.contains(LookupActions.ITEM);

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
                if (verbose && preview == 0 && !actionList.contains(LookupActions.ITEM)) {
                    Integer chunks = chunkList.size();
                    Chat.sendMessage(user, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.ROLLBACK_CHUNKS_FOUND, chunks.toString(), (chunks == 1 ? Selector.FIRST : Selector.SECOND)));
                }
            }

            // Perform update transaction(s) in consumer
            if (preview == 0) {
                if (actionList.contains(LookupActions.ITEM)) {
                    List<Object[]> blockList = new ArrayList<>();
                    List<Object[]> inventoryList = new ArrayList<>();
                    List<Object[]> containerList = new ArrayList<>();
                    for (Object[] data : itemList) {
                        int table = (Integer) data[14];
                        if (table == RollbackUpdateTargets.INVENTORY_ITEM) {
                            inventoryList.add(data);
                        }
                        else if (table == RollbackUpdateTargets.CONTAINER) {
                            containerList.add(data);
                        }
                        else {
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
            List<Entry<Long, Integer>> sortedChunks = new ArrayList<>(DatabaseUtils.entriesSortedByValues(chunkList));
            if (ConfigHandler.isFolia) {
                chunkCount = processFoliaChunks(sortedChunks, worldList, dataList, itemDataList, rollbackType, preview, finalUserString, user, inventoryRollback, verbose, actionList);
            }
            else {
                chunkCount = processBukkitChunks(sortedChunks, worldList, dataList, itemDataList, rollbackType, preview, finalUserString, user, inventoryRollback, verbose, actionList);
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
            ErrorReporter.report(e);
        }

        return null;
    }

    private static int processFoliaChunks(List<Entry<Long, Integer>> sortedChunks, LinkedHashSet<Integer> worldList, HashMap<Integer, HashMap<Long, ArrayList<Object[]>>> dataList, HashMap<Integer, HashMap<Long, ArrayList<Object[]>>> itemDataList, int rollbackType, int preview, String userString, CommandSender user, boolean inventoryRollback, boolean verbose, List<Integer> actionList) throws InterruptedException {
        FoliaRollbackBatchState batchState = new FoliaRollbackBatchState(buildFoliaChunkWork(sortedChunks, worldList, dataList, itemDataList), sortedChunks.size());
        while (batchState.hasNext()) {
            if (!processFoliaEmptyChunks(batchState, userString, verbose, user, preview, actionList)) {
                Chat.console(Phrase.build(Phrase.ROLLBACK_ABORTED));
                break;
            }
            if (!batchState.hasNext()) {
                break;
            }

            CompletableFuture<Boolean> batchFuture = scheduleFoliaChunkBatchTask(batchState, rollbackType, preview, userString, user, inventoryRollback, verbose, actionList);
            if (!awaitChunkTasks(Collections.singletonList(batchFuture), preview)) {
                Chat.console(Phrase.build(Phrase.ROLLBACK_ABORTED));
                break;
            }
        }

        return batchState.chunkCount;
    }

    private static List<FoliaChunkWork> buildFoliaChunkWork(List<Entry<Long, Integer>> sortedChunks, LinkedHashSet<Integer> worldList, HashMap<Integer, HashMap<Long, ArrayList<Object[]>>> dataList, HashMap<Integer, HashMap<Long, ArrayList<Object[]>>> itemDataList) {
        List<FoliaChunkWork> work = new ArrayList<>();
        HashMap<Integer, World> worldMap = getRollbackWorlds(worldList);
        List<Entry<Integer, World>> rollbackWorlds = new ArrayList<>(worldMap.entrySet());

        for (Entry<Long, Integer> entry : sortedChunks) {
            long chunkKey = entry.getKey();
            int chunkX = getChunkX(entry);
            int chunkZ = getChunkZ(entry);

            if (rollbackWorlds.isEmpty()) {
                work.add(new FoliaChunkWork(chunkKey, chunkX, chunkZ, null, null, null, true));
                continue;
            }

            for (int index = 0; index < rollbackWorlds.size(); index++) {
                Entry<Integer, World> rollbackWorld = rollbackWorlds.get(index);
                int rollbackWorldId = rollbackWorld.getKey();
                World world = rollbackWorld.getValue();
                HashMap<Long, ArrayList<Object[]>> blockList = dataList.get(rollbackWorldId);
                HashMap<Long, ArrayList<Object[]>> itemList = itemDataList.get(rollbackWorldId);
                work.add(new FoliaChunkWork(chunkKey, chunkX, chunkZ, world, blockList, itemList, index == rollbackWorlds.size() - 1));
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

    private static CompletableFuture<Boolean> scheduleFoliaChunkBatchTask(FoliaRollbackBatchState batchState, int rollbackType, int preview, String userString, CommandSender user, boolean inventoryRollback, boolean verbose, List<Integer> actionList) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        FoliaChunkWork firstWork = batchState.peek();
        Location chunkLocation = new Location(firstWork.world, (firstWork.chunkX << 4), 0, (firstWork.chunkZ << 4));

        Scheduler.scheduleSyncDelayedTask(CoreProtect.getInstance(), () -> {
            try {
                long batchStart = System.nanoTime();
                do {
                    FoliaChunkWork work = batchState.next();

                    prepareChunkCounters(userString);
                    if (!processChunkWorld(work.chunkX, work.chunkZ, work.chunkKey, work.blockList, work.itemList, rollbackType, preview, userString, user, work.world, inventoryRollback)) {
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
                ErrorReporter.report(e);
                future.complete(false);
            }
        }, chunkLocation, 0);

        return future;
    }

    private static int processBukkitChunks(List<Entry<Long, Integer>> sortedChunks, LinkedHashSet<Integer> worldList, HashMap<Integer, HashMap<Long, ArrayList<Object[]>>> dataList, HashMap<Integer, HashMap<Long, ArrayList<Object[]>>> itemDataList, int rollbackType, int preview, String userString, CommandSender user, boolean inventoryRollback, boolean verbose, List<Integer> actionList) throws InterruptedException {
        RollbackBatchState batchState = new RollbackBatchState(sortedChunks);
        while (batchState.hasNext()) {
            CompletableFuture<Boolean> batchFuture = scheduleChunkBatchTask(batchState, worldList, dataList, itemDataList, rollbackType, preview, userString, user, inventoryRollback, verbose, actionList);
            if (!awaitChunkTasks(Collections.singletonList(batchFuture), preview)) {
                Chat.console(Phrase.build(Phrase.ROLLBACK_ABORTED));
                break;
            }
        }

        return batchState.chunkCount;
    }

    private static CompletableFuture<Boolean> scheduleChunkBatchTask(RollbackBatchState batchState, LinkedHashSet<Integer> worldList, HashMap<Integer, HashMap<Long, ArrayList<Object[]>>> dataList, HashMap<Integer, HashMap<Long, ArrayList<Object[]>>> itemDataList, int rollbackType, int preview, String userString, CommandSender user, boolean inventoryRollback, boolean verbose, List<Integer> actionList) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        Scheduler.scheduleSyncDelayedTask(CoreProtect.getInstance(), () -> {
            try {
                long batchStart = System.nanoTime();
                do {
                    Entry<Long, Integer> entry = batchState.next();
                    batchState.chunkCount++;

                    if (!processChunkEntry(entry, worldList, dataList, itemDataList, rollbackType, preview, userString, user, inventoryRollback)) {
                        future.complete(false);
                        return;
                    }

                    if (!completeChunk(userString, batchState.chunkCount, batchState.totalChunks(), verbose, user, preview, actionList)) {
                        future.complete(false);
                        return;
                    }
                }
                while (batchState.hasNext() && (System.nanoTime() - batchStart) < ROLLBACK_BATCH_BUDGET_NANOS);

                future.complete(true);
            }
            catch (Exception e) {
                ErrorReporter.report(e);
                future.complete(false);
            }
        }, 0);

        return future;
    }

    private static boolean canContinueFoliaBatch(FoliaRollbackBatchState batchState, long batchStart) {
        if (!batchState.hasNext() || (System.nanoTime() - batchStart) >= ROLLBACK_BATCH_BUDGET_NANOS) {
            return false;
        }

        FoliaChunkWork nextWork = batchState.peek();
        return nextWork.world != null && Bukkit.isOwnedByCurrentRegion(nextWork.world, nextWork.chunkX, nextWork.chunkZ);
    }

    private static boolean processChunkEntry(Entry<Long, Integer> entry, LinkedHashSet<Integer> worldList, HashMap<Integer, HashMap<Long, ArrayList<Object[]>>> dataList, HashMap<Integer, HashMap<Long, ArrayList<Object[]>>> itemDataList, int rollbackType, int preview, String userString, CommandSender user, boolean inventoryRollback) {
        long chunkKey = entry.getKey();
        int chunkX = getChunkX(entry);
        int chunkZ = getChunkZ(entry);
        HashMap<Integer, World> worldMap = getRollbackWorlds(worldList);
        prepareChunkCounters(userString);

        for (Entry<Integer, World> rollbackWorlds : worldMap.entrySet()) {
            int rollbackWorldId = rollbackWorlds.getKey();
            World bukkitRollbackWorld = rollbackWorlds.getValue();
            HashMap<Long, ArrayList<Object[]>> blockList = dataList.get(rollbackWorldId);
            HashMap<Long, ArrayList<Object[]>> itemList = itemDataList.get(rollbackWorldId);

            if (!processChunkWorld(chunkX, chunkZ, chunkKey, blockList, itemList, rollbackType, preview, userString, user, bukkitRollbackWorld, inventoryRollback)) {
                return false;
            }
        }

        return true;
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

    private static boolean processChunkWorld(int chunkX, int chunkZ, long chunkKey, HashMap<Long, ArrayList<Object[]>> blockList, HashMap<Long, ArrayList<Object[]>> itemList, int rollbackType, int preview, String userString, CommandSender user, World world, boolean inventoryRollback) {
        ArrayList<Object[]> blockData = blockList != null ? blockList.getOrDefault(chunkKey, new ArrayList<>()) : new ArrayList<>();
        ArrayList<Object[]> itemData = itemList != null ? itemList.getOrDefault(chunkKey, new ArrayList<>()) : new ArrayList<>();
        Player rollbackPlayer = user instanceof Player ? (Player) user : null;
        return RollbackProcessor.processChunk(chunkX, chunkZ, chunkKey, blockData, itemData, rollbackType, preview, userString, rollbackPlayer, world, inventoryRollback);
    }

    private static final class RollbackBatchState {
        private final List<Entry<Long, Integer>> sortedChunks;
        private int index = 0;
        private int chunkCount = 0;

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
        private final World world;
        private final HashMap<Long, ArrayList<Object[]>> blockList;
        private final HashMap<Long, ArrayList<Object[]>> itemList;
        private final boolean lastWorldForChunk;

        private FoliaChunkWork(long chunkKey, int chunkX, int chunkZ, World world, HashMap<Long, ArrayList<Object[]>> blockList, HashMap<Long, ArrayList<Object[]>> itemList, boolean lastWorldForChunk) {
            this.chunkKey = chunkKey;
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
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
