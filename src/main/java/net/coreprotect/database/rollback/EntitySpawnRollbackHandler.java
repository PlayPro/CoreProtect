package net.coreprotect.database.rollback;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import net.coreprotect.CoreProtect;
import net.coreprotect.bukkit.BukkitAdapter;
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.consumer.Queue;
import net.coreprotect.database.statement.EntityStatement;
import net.coreprotect.listener.player.EntityInteractionListener;
import net.coreprotect.listener.player.InventoryChangeListener;
import net.coreprotect.model.entity.EntitySpawnData;
import net.coreprotect.model.entity.EntitySpawnRecord;
import net.coreprotect.paper.PaperAdapter;
import net.coreprotect.thread.Scheduler;
import net.coreprotect.utility.EntitySpawnTracking;
import net.coreprotect.utility.EntityUtils;
import net.coreprotect.utility.ErrorReporter;
import net.coreprotect.utility.ItemUtils;
import net.coreprotect.utility.MaterialUtils;
import net.coreprotect.utility.WorldUtils;
import net.coreprotect.utility.entity.EntityUtil;

public final class EntitySpawnRollbackHandler {

    private static final long CHUNK_STEP_BUDGET_NANOS = 2_000_000L;
    private static final long TRANSACTION_BUDGET_WINDOW_NANOS = 50_000_000L;
    private static final int MAX_CHUNK_MUTATIONS_PER_STEP = 16;
    private static final int MAX_CHUNK_WORK_PER_STEP = 256;
    private static final int MAX_ENTITY_LOAD_RETRIES = 5;
    private static final int MAX_LOCATION_RETRIES = 3;
    private static final int DEFAULT_INVENTORY_MAX_STACK_SIZE = 64;
    private static final Set<Integer> activeTrackingRows = ConcurrentHashMap.newKeySet();
    private static final Object transactionBudgetLock = new Object();
    private static long transactionBudgetWindowStart = System.nanoTime();
    private static long transactionBudgetNanos;
    private static int transactionBudgetMutations;
    private static boolean transactionStepActive;

    private EntitySpawnRollbackHandler() {
        throw new IllegalStateException("Database class");
    }

    static Context prepare(List<Object[]> rows, Map<Integer, EntitySpawnRecord> records, List<Object[]> killRows, Map<Integer, EntitySpawnRecord> killRecords, Map<Integer, List<Object>> killData, List<Object[]> containerRows, int rollbackType, boolean inventoryRollback, int preview, String userString, Location radiusOrigin, Integer[] radius) {
        List<Work> scheduledWork = new ArrayList<>();
        List<NoWorldTransition> directTransitions = new ArrayList<>();
        Set<Integer> requiredRows = new HashSet<>();
        Set<Integer> pairedKillRows = new HashSet<>();
        Set<Integer> compositeSpawnRows = new HashSet<>();
        Map<Integer, Object[]> killRowsById = new HashMap<>();
        Map<Integer, List<Object[]>> containerRowsByTrackingId = groupContainerRows(containerRows, rollbackType, inventoryRollback);
        Set<Integer> skippedCompositeRows = new HashSet<>();

        for (Object[] row : killRows) {
            int killRowId = (Integer) row[7];
            if (isEligible(row, rollbackType)) {
                killRowsById.put(killRowId, row);
            }
        }

        for (Object[] row : rows) {
            EntitySpawnRecord record = records.get((Integer) row[7]);
            if (record == null || !isEligible(row, rollbackType) || record.getKillRowId() <= 0) {
                continue;
            }

            Object[] killRow = killRowsById.get(record.getKillRowId());
            EntitySpawnRecord killRecord = killRecords.get(record.getKillRowId());
            if (killRow == null || killRecord == null || killRecord.getRowId() != record.getRowId()) {
                continue;
            }

            List<Object[]> transactions = containerRowsByTrackingId.getOrDefault(record.getRowId(), Collections.emptyList());
            if (rollbackType == 0 && record.isRemoved()) {
                pairedKillRows.add(record.getKillRowId());
                List<Object> data = killData.get(record.getKillRowId());
                EntityType type = EntityUtils.getEntityType((Integer) killRow[6]);
                Location location = getRowLocation(killRow);
                if (location != null && EntitySpawnTracking.isPlacedEntityType(type)) {
                    Location exactLocation = EntitySpawnTracking.getKillRestoreLocation(location.getWorld(), data);
                    if (exactLocation != null) {
                        location = exactLocation;
                    }
                }
                if (data == null || location == null || type == null || type == EntityType.UNKNOWN) {
                    skippedCompositeRows.add(record.getRowId());
                    continue;
                }

                long spawnBlockRowId = (Long) row[0];
                long killBlockRowId = (Long) killRow[0];
                compositeSpawnRows.add(record.getRowId());
                if (transactions.isEmpty() && EntitySpawnTracking.isPlacedEntityType(type)) {
                    byte[] state = serializeKillState(data);
                    if (state == null) {
                        skippedCompositeRows.add(record.getRowId());
                        continue;
                    }
                    directTransitions.add(NoWorldTransition.compositeRollback(record, spawnBlockRowId, killBlockRowId, location, state));
                }
                else {
                    scheduledWork.add(Work.compositeRollback(spawnBlockRowId, killBlockRowId, record.getKillRowId(), record, type, location, data, transactions));
                }
                containerRowsByTrackingId.remove(record.getRowId());
                if (preview == 0) {
                    requiredRows.add(record.getRowId());
                }
            }
            else if (rollbackType == 1 && record.isRemoved()) {
                long spawnBlockRowId = (Long) row[0];
                long killBlockRowId = (Long) killRow[0];
                pairedKillRows.add(record.getKillRowId());
                compositeSpawnRows.add(record.getRowId());
                directTransitions.add(NoWorldTransition.compositeRestore(record, spawnBlockRowId, killBlockRowId, transactions));
                containerRowsByTrackingId.remove(record.getRowId());
                if (preview == 0) {
                    requiredRows.add(record.getRowId());
                }
            }
        }

        for (Object[] row : rows) {
            EntitySpawnRecord record = records.get((Integer) row[7]);
            if (record == null || compositeSpawnRows.contains(record.getRowId()) || !isEligible(row, rollbackType)) {
                continue;
            }

            long blockRowId = (Long) row[0];
            List<Object[]> transactions = containerRowsByTrackingId.getOrDefault(record.getRowId(), Collections.emptyList());
            Work work;
            if (rollbackType == 0) {
                work = Work.spawnRemoval(blockRowId, record, getRemovalLocation(record), transactions);
                if (record.isRemoved()) {
                    continue;
                }
                else if (work.getLocation() != null) {
                    scheduledWork.add(work);
                    containerRowsByTrackingId.remove(record.getRowId());
                }
                else {
                    continue;
                }
            }
            else {
                Location location = record.getLocation();
                EntityType type = EntityUtils.getEntityType((Integer) row[6]);
                if (record.getState() == null || location == null || type == null || type == EntityType.UNKNOWN) {
                    continue;
                }
                work = Work.spawnRestore(blockRowId, record, type, location, transactions);
                scheduledWork.add(work);
                containerRowsByTrackingId.remove(record.getRowId());
            }

            if (preview == 0) {
                requiredRows.add(record.getRowId());
            }
        }

        for (Object[] row : killRows) {
            int killRowId = (Integer) row[7];
            EntitySpawnRecord record = killRecords.get(killRowId);
            if (record == null || pairedKillRows.contains(killRowId) || !isEligible(row, rollbackType)) {
                continue;
            }

            long blockRowId = (Long) row[0];
            List<Object[]> transactions = containerRowsByTrackingId.getOrDefault(record.getRowId(), Collections.emptyList());
            if (rollbackType == 0) {
                List<Object> data = killData.get(killRowId);
                EntityType type = EntityUtils.getEntityType((Integer) row[6]);
                Location location = getRowLocation(row);
                if (location != null && EntitySpawnTracking.isPlacedEntityType(type)) {
                    Location exactLocation = EntitySpawnTracking.getKillRestoreLocation(location.getWorld(), data);
                    if (exactLocation != null) {
                        location = exactLocation;
                    }
                }
                if (!record.isRemoved() || data == null || location == null || type == null || type == EntityType.UNKNOWN) {
                    continue;
                }
                scheduledWork.add(Work.killRollback(blockRowId, killRowId, record, type, location, data, transactions));
                containerRowsByTrackingId.remove(record.getRowId());
            }
            else if (record.isRemoved()) {
                continue;
            }
            else {
                Location location = getRemovalLocation(record);
                if (location == null) {
                    continue;
                }
                scheduledWork.add(Work.killRestore(blockRowId, killRowId, record, location, transactions));
                containerRowsByTrackingId.remove(record.getRowId());
            }

            if (preview == 0) {
                requiredRows.add(record.getRowId());
            }
        }

        for (Map.Entry<Integer, List<Object[]>> entry : containerRowsByTrackingId.entrySet()) {
            EntitySpawnRecord record = records.get(entry.getKey());
            if (record == null || record.isRemoved()) {
                continue;
            }
            Location location = getRemovalLocation(record);
            if (location == null) {
                continue;
            }
            scheduledWork.add(Work.container(record, location, entry.getValue(), rollbackType));
            if (preview == 0) {
                requiredRows.add(record.getRowId());
            }
        }

        if (!skippedCompositeRows.isEmpty()) {
            Rollback.warnSkippedEntityRows("Skipping tracked entity spawn rows with missing kill data", skippedCompositeRows);
        }

        Set<Integer> claimedRows = preview == 0 ? claimTrackingRows(requiredRows) : ConcurrentHashMap.newKeySet();
        Context context = new Context(userString, preview, rollbackType, inventoryRollback, claimedRows, radiusOrigin, radius);
        if (claimedRows == null) {
            context.cancel();
            return context;
        }

        for (Work work : scheduledWork) {
            context.add(work);
        }
        for (NoWorldTransition transition : directTransitions) {
            context.add(transition);
        }
        return context;
    }

    public static void releaseTrackingRow(int trackingRowId) {
        activeTrackingRows.remove(trackingRowId);
    }

    static CompletableFuture<Boolean> processChunk(Context context, World world, int chunkX, int chunkZ, List<Work> work) {
        ChunkProcessingState state = new ChunkProcessingState(context, world, chunkX, chunkZ, work);
        processChunkStep(state);
        return state.completion;
    }

    static boolean requiresChunk(Context context, List<Work> work) {
        for (Work current : work) {
            if (!current.isComplete() && ((current.operation.createsEntity() && context.preview == 0) || (!current.operation.createsEntity() && Bukkit.getEntity(current.record.getUuid()) == null))) {
                return true;
            }
        }
        return false;
    }

    private static void processChunkStep(ChunkProcessingState state) {
        if (state.completion.isDone()) {
            return;
        }
        if (state.context.isCancelled()) {
            state.pending.add(CompletableFuture.completedFuture(false));
            finishChunk(state);
            return;
        }

        long stepStart = System.nanoTime();
        int processed = 0;
        int mutations = 0;
        try {
            while (true) {
                if (state.context.isCancelled()) {
                    state.pending.add(CompletableFuture.completedFuture(false));
                    finishChunk(state);
                    return;
                }

                if (state.phase == ChunkPhase.RESOLVE) {
                    if (state.index >= state.work.size()) {
                        state.phase = ChunkPhase.LOCALIZE;
                        state.index = 0;
                        continue;
                    }

                    Work current = state.work.get(state.index++);
                    processed++;
                    if (!current.isComplete()) {
                        if (!sameChunk(state.chunkLocation, current.getLocation())) {
                            mutations++;
                            if (addMutation(state, scheduleAtLatestLocation(state.context, current, 0))) {
                                return;
                            }
                        }
                        else if (current.operation.createsEntity()) {
                            mutations++;
                            if (addMutation(state, restoreEntity(state.context, current))) {
                                return;
                            }
                        }
                        else {
                            Entity entity = Bukkit.getEntity(current.record.getUuid());
                            if (entity == null) {
                                state.unresolved.add(current);
                            }
                            else if (!PaperAdapter.ADAPTER.isOwnedByCurrentRegion(entity)) {
                                mutations++;
                                if (addMutation(state, scheduleForEntity(state.context, current, entity))) {
                                    return;
                                }
                            }
                            else {
                                mutations++;
                                if (addMutation(state, mutateEntity(state.context, current, entity))) {
                                    return;
                                }
                            }
                        }
                    }
                }
                else if (state.phase == ChunkPhase.LOCALIZE) {
                    if (state.index >= state.unresolved.size()) {
                        if (state.local.isEmpty()) {
                            finishChunk(state);
                            return;
                        }
                        state.phase = ChunkPhase.LOAD;
                        state.index = 0;
                        continue;
                    }

                    Work current = state.unresolved.get(state.index++);
                    processed++;
                    if (!current.isComplete()) {
                        Location latest = getRemovalLocation(current);
                        if (!sameChunk(state.chunkLocation, latest)) {
                            mutations++;
                            if (addMutation(state, scheduleAtLatestLocation(state.context, current, 0))) {
                                return;
                            }
                        }
                        else {
                            state.local.add(current);
                            state.localUuids.add(current.record.getUuid());
                        }
                    }
                }
                else if (state.phase == ChunkPhase.LOAD) {
                    Chunk chunk = state.world.getChunkAt(state.chunkX, state.chunkZ);
                    if (!BukkitAdapter.ADAPTER.isChunkEntitiesLoaded(chunk) && state.entityLoadRetries < MAX_ENTITY_LOAD_RETRIES) {
                        state.entityLoadRetries++;
                        scheduleChunkContinuation(state);
                        return;
                    }

                    state.chunkEntities = chunk.getEntities();
                    state.phase = ChunkPhase.SCAN;
                    state.index = 0;
                    continue;
                }
                else if (state.phase == ChunkPhase.SCAN) {
                    if (state.localUuids.isEmpty() || state.index >= state.chunkEntities.length) {
                        state.chunkEntities = null;
                        state.phase = ChunkPhase.APPLY;
                        state.index = 0;
                        continue;
                    }

                    Entity entity = state.chunkEntities[state.index++];
                    processed++;
                    UUID uuid = entity.getUniqueId();
                    if (state.localUuids.remove(uuid)) {
                        state.entities.put(uuid, entity);
                    }
                }
                else if (state.phase == ChunkPhase.APPLY) {
                    if (state.index >= state.local.size()) {
                        finishChunk(state);
                        return;
                    }

                    Work current = state.local.get(state.index++);
                    processed++;
                    if (!current.isComplete()) {
                        Entity entity = state.entities.remove(current.record.getUuid());
                        if (entity != null) {
                            if (!PaperAdapter.ADAPTER.isOwnedByCurrentRegion(entity)) {
                                mutations++;
                                if (addMutation(state, scheduleForEntity(state.context, current, entity))) {
                                    return;
                                }
                            }
                            else if (entity.isValid()) {
                                mutations++;
                                if (addMutation(state, mutateEntity(state.context, current, entity))) {
                                    return;
                                }
                            }
                            else {
                                entity = null;
                            }
                        }
                        if (entity == null) {
                            Location latest = getRemovalLocation(current);
                            if (!sameChunk(state.chunkLocation, latest)) {
                                mutations++;
                                if (addMutation(state, scheduleAtLatestLocation(state.context, current, 0))) {
                                    return;
                                }
                            }
                            else if (!completeMissingRollback(state.context, current)) {
                                state.pending.add(CompletableFuture.completedFuture(false));
                            }
                        }
                    }
                }
                else {
                    finishChunk(state);
                    return;
                }

                if (shouldYieldChunk(stepStart, processed, mutations)) {
                    scheduleChunkContinuation(state);
                    return;
                }
            }
        }
        catch (Exception e) {
            state.context.cancel();
            ErrorReporter.report(e);
            state.pending.add(CompletableFuture.completedFuture(false));
            finishChunk(state);
        }
    }

    private static boolean shouldYieldChunk(long stepStart, int processed, int mutations) {
        return processed >= MAX_CHUNK_WORK_PER_STEP || mutations >= MAX_CHUNK_MUTATIONS_PER_STEP || (processed > 0 && System.nanoTime() - stepStart >= CHUNK_STEP_BUDGET_NANOS);
    }

    private static boolean addMutation(ChunkProcessingState state, CompletableFuture<Boolean> mutation) {
        state.pending.add(mutation);
        if (mutation.isDone()) {
            try {
                if (!Boolean.TRUE.equals(mutation.getNow(Boolean.FALSE))) {
                    state.context.cancel();
                }
            }
            catch (Exception e) {
                state.context.cancel();
                ErrorReporter.report(e);
            }
            return false;
        }

        mutation.whenComplete((result, throwable) -> {
            if (throwable != null || !Boolean.TRUE.equals(result)) {
                state.context.cancel();
                if (throwable != null) {
                    ErrorReporter.report(throwable);
                }
            }
            scheduleChunkContinuation(state);
        });
        return true;
    }

    private static void scheduleChunkContinuation(ChunkProcessingState state) {
        try {
            Scheduler.scheduleSyncDelayedTask(CoreProtect.getInstance(), () -> processChunkStep(state), state.chunkLocation, 1);
        }
        catch (Exception e) {
            state.context.cancel();
            ErrorReporter.report(e);
            state.pending.add(CompletableFuture.completedFuture(false));
            finishChunk(state);
        }
    }

    private static void finishChunk(ChunkProcessingState state) {
        if (state.phase == ChunkPhase.COMPLETE) {
            return;
        }
        state.phase = ChunkPhase.COMPLETE;
        completeFrom(state.completion, combine(state.pending));
    }

    private static CompletableFuture<Boolean> scheduleForEntity(Context context, Work work, Entity entity) {
        CompletableFuture<Boolean> completion = new CompletableFuture<>();
        try {
            Runnable retired = () -> completeFrom(completion, scheduleAtLatestLocation(context, work, 1));
            boolean scheduled = PaperAdapter.ADAPTER.executeEntityTask(CoreProtect.getInstance(), entity, () -> {
                if (context.isCancelled()) {
                    completion.complete(false);
                    return;
                }
                try {
                    completeFrom(completion, mutateEntity(context, work, entity));
                }
                catch (Exception e) {
                    context.cancel();
                    ErrorReporter.report(e);
                    completion.complete(false);
                }
            }, retired);
            if (!scheduled) {
                retired.run();
            }
        }
        catch (Exception e) {
            context.cancel();
            ErrorReporter.report(e);
            completion.complete(false);
        }
        return completion;
    }

    private static CompletableFuture<Boolean> scheduleAtLatestLocation(Context context, Work work, int delay) {
        if (context.isCancelled()) {
            return CompletableFuture.completedFuture(false);
        }
        if (work.locationRetries.incrementAndGet() > MAX_LOCATION_RETRIES) {
            return CompletableFuture.completedFuture(completeUnresolved(context, work));
        }

        Location location = work.operation.createsEntity() ? work.getLocation() : getRemovalLocation(work);
        if (location == null || location.getWorld() == null) {
            return CompletableFuture.completedFuture(completeUnresolved(context, work));
        }

        CompletableFuture<Boolean> completion = new CompletableFuture<>();
        try {
            Scheduler.scheduleSyncDelayedTask(CoreProtect.getInstance(), () -> completeFrom(completion, processChunk(context, location.getWorld(), location.getBlockX() >> 4, location.getBlockZ() >> 4, Collections.singletonList(work))), location, delay);
        }
        catch (Exception e) {
            context.cancel();
            ErrorReporter.report(e);
            completion.complete(false);
        }
        return completion;
    }

    private static CompletableFuture<Boolean> mutateEntity(Context context, Work work, Entity entity) {
        if (work.operation == WorkOperation.CONTAINER_ROLLBACK || work.operation == WorkOperation.CONTAINER_RESTORE) {
            return applyContainerOnly(context, work, entity);
        }
        return removeEntity(context, work, entity);
    }

    private static CompletableFuture<Boolean> removeEntity(Context context, Work work, Entity entity) {
        if (work.isComplete()) {
            return CompletableFuture.completedFuture(true);
        }
        Location currentLocation = entity.getLocation();
        if ((work.operation.enforcesCurrentRadius() || !work.transactions.isEmpty()) && !context.isWithinRadius(currentLocation)) {
            return CompletableFuture.completedFuture(context.complete(work, 0));
        }
        if (context.preview > 0) {
            return CompletableFuture.completedFuture(context.completePreview(work, 1));
        }
        if (!context.beginMutation()) {
            return CompletableFuture.completedFuture(false);
        }

        CompletableFuture<Boolean> completion = new CompletableFuture<>();
        InventoryChangeListener.flushEntityContainer(entity);
        applyTransactions(context, work, entity).whenComplete((applied, throwable) -> {
            try {
                if (throwable != null || !Boolean.TRUE.equals(applied) || (context.isCancelled() && !work.transactionContentsApplied)) {
                    if (throwable != null) {
                        ErrorReporter.report(throwable);
                    }
                    context.cancel();
                    completion.complete(false);
                    return;
                }

                Location finalLocation = entity.getLocation();
                EntitySpawnData transition;
                if (work.operation == WorkOperation.SPAWN_ROLLBACK) {
                    byte[] serializedState = EntityStatement.serializeData(EntitySpawnTracking.serializeState(entity));
                    if (serializedState == null) {
                        context.cancel();
                        completion.complete(false);
                        return;
                    }
                    transition = EntitySpawnData.rollback(work.blockRowId, work.record.getRowId(), finalLocation, serializedState);
                }
                else {
                    transition = EntitySpawnData.killRestore(work.blockRowId, work.record.getRowId(), work.killRowId, finalLocation);
                }

                EntityInteractionListener.flushPendingInteractions(entity);
                EntitySpawnTracking.removeWithoutRemovalLog(entity);
                EntitySpawnTracking.forget(entity.getUniqueId());
                completion.complete(context.transition(work, transition, 1));
            }
            catch (Exception e) {
                context.cancel();
                ErrorReporter.report(e);
                completion.complete(false);
            }
            finally {
                context.endMutation();
            }
        });
        return completion;
    }

    private static CompletableFuture<Boolean> restoreEntity(Context context, Work work) {
        if (work.isComplete()) {
            return CompletableFuture.completedFuture(true);
        }
        if ((work.operation.enforcesCurrentRadius() || !work.transactions.isEmpty()) && !context.isWithinRadius(work.getLocation())) {
            return CompletableFuture.completedFuture(context.complete(work, 0));
        }
        if (context.preview > 0) {
            return CompletableFuture.completedFuture(context.completePreview(work, work.operation == WorkOperation.COMPOSITE_ROLLBACK ? 2 : 1));
        }
        if (!context.beginMutation()) {
            return CompletableFuture.completedFuture(false);
        }

        CompletableFuture<Boolean> completion = new CompletableFuture<>();
        try {
            prepareCreatedTransactions(context, work).whenComplete((prepared, throwable) -> {
                if (throwable != null || !Boolean.TRUE.equals(prepared) || context.isCancelled()) {
                    if (throwable != null) {
                        ErrorReporter.report(throwable);
                    }
                    context.cancel();
                    context.endMutation();
                    completion.complete(false);
                    return;
                }

                if (work.operation == WorkOperation.KILL_ROLLBACK || work.operation == WorkOperation.COMPOSITE_ROLLBACK) {
                    completeFrom(completion, restoreKilledEntity(context, work));
                    return;
                }

                Entity entity = null;
                try {
                    entity = work.location.getWorld().spawnEntity(work.location, work.type);
                    EntitySpawnTracking.restoreState(entity, work.record.getState());
                    completeFrom(completion, completeRestoredEntity(context, work, entity));
                }
                catch (Exception e) {
                    context.cancel();
                    ErrorReporter.report(e);
                    try {
                        discardRestoredEntity(entity);
                    }
                    catch (Exception cleanupException) {
                        ErrorReporter.report(cleanupException);
                    }
                    finally {
                        context.endMutation();
                        completion.complete(false);
                    }
                }
            });
        }
        catch (Exception e) {
            context.cancel();
            ErrorReporter.report(e);
            context.endMutation();
            completion.complete(false);
        }
        return completion;
    }

    private static CompletableFuture<Boolean> restoreKilledEntity(Context context, Work work) {
        CompletableFuture<Entity> spawnFuture = EntityUtil.restoreEntity(work.getLocation(), work.type, work.killData);
        CompletableFuture<Boolean> completion = new CompletableFuture<>();
        spawnFuture.whenComplete((entity, throwable) -> {
            try {
                if (throwable != null) {
                    context.cancel();
                    ErrorReporter.report(throwable);
                    completion.complete(false);
                    context.endMutation();
                    return;
                }
                if (entity == null) {
                    completion.complete(false);
                    context.endMutation();
                    return;
                }
                if (context.isCancelled()) {
                    try {
                        discardRestoredEntity(entity);
                    }
                    catch (Exception cleanupException) {
                        ErrorReporter.report(cleanupException);
                    }
                    finally {
                        completion.complete(false);
                        context.endMutation();
                    }
                    return;
                }

                completeFrom(completion, completeRestoredEntity(context, work, entity));
            }
            catch (Exception e) {
                context.cancel();
                ErrorReporter.report(e);
                try {
                    discardRestoredEntity(entity);
                }
                catch (Exception cleanupException) {
                    ErrorReporter.report(cleanupException);
                }
                finally {
                    context.endMutation();
                    completion.complete(false);
                }
            }
        });
        return completion;
    }

    private static void discardRestoredEntity(Entity entity) {
        if (entity == null) {
            return;
        }
        EntitySpawnTracking.forget(entity.getUniqueId());
        if (entity.isValid()) {
            EntitySpawnTracking.removeWithoutRemovalLog(entity);
        }
    }

    private static CompletableFuture<Boolean> completeRestoredEntity(Context context, Work work, Entity entity) {
        CompletableFuture<Boolean> completion = new CompletableFuture<>();
        boolean retained = false;
        try {
            if (!applyPreparedTransactions(work, entity) || (context.isCancelled() && !work.transactionContentsApplied)) {
                context.cancel();
                completion.complete(false);
                return completion;
            }

            Location location = entity.getLocation();
            if (work.operation == WorkOperation.COMPOSITE_ROLLBACK) {
                byte[] serializedState = EntityStatement.serializeData(EntitySpawnTracking.serializeState(entity));
                if (serializedState == null) {
                    context.cancel();
                    completion.complete(false);
                    return completion;
                }

                EntitySpawnData transition = EntitySpawnData.compositeRollback(work.blockRowId, work.killBlockRowId, work.record.getRowId(), work.killRowId, location, serializedState);
                if (entity.isValid()) {
                    EntitySpawnTracking.removeWithoutRemovalLog(entity);
                }
                EntitySpawnTracking.forget(entity.getUniqueId());
                completion.complete(context.compositeTransition(work, transition, 2));
                return completion;
            }

            EntitySpawnData transition;
            if (work.operation == WorkOperation.SPAWN_RESTORE) {
                transition = EntitySpawnData.restore(work.blockRowId, work.record.getRowId(), entity.getUniqueId(), location);
            }
            else {
                transition = EntitySpawnData.killRollback(work.blockRowId, work.record.getRowId(), work.killRowId, entity.getUniqueId(), location);
            }
            EntitySpawnTracking.track(entity);
            retained = context.transition(work, transition, 1);
            completion.complete(retained);
        }
        catch (Exception e) {
            context.cancel();
            ErrorReporter.report(e);
            completion.complete(false);
        }
        finally {
            try {
                if (!retained) {
                    EntitySpawnTracking.forget(entity.getUniqueId());
                    if (entity.isValid()) {
                        EntitySpawnTracking.removeWithoutRemovalLog(entity);
                    }
                }
            }
            finally {
                context.endMutation();
            }
        }
        return completion;
    }

    private static CompletableFuture<Boolean> applyContainerOnly(Context context, Work work, Entity entity) {
        if (work.isComplete()) {
            return CompletableFuture.completedFuture(true);
        }
        if (!context.isWithinRadius(entity.getLocation())) {
            return CompletableFuture.completedFuture(context.complete(work, 0));
        }
        if (context.preview > 0) {
            return CompletableFuture.completedFuture(context.completePreview(work, 0));
        }
        if (!context.beginMutation()) {
            return CompletableFuture.completedFuture(false);
        }

        CompletableFuture<Boolean> completion = new CompletableFuture<>();
        InventoryChangeListener.flushEntityContainer(entity);
        applyTransactions(context, work, entity).whenComplete((applied, throwable) -> {
            try {
                if (throwable != null || !Boolean.TRUE.equals(applied) || (context.isCancelled() && !work.transactionContentsApplied)) {
                    if (throwable != null) {
                        ErrorReporter.report(throwable);
                    }
                    context.cancel();
                    completion.complete(false);
                    return;
                }
                completion.complete(context.containerTransition(work));
            }
            catch (Exception e) {
                context.cancel();
                ErrorReporter.report(e);
                completion.complete(false);
            }
            finally {
                context.endMutation();
            }
        });
        return completion;
    }

    private static CompletableFuture<Boolean> applyTransactions(Context context, Work work, Entity entity) {
        if (work.transactions.isEmpty()) {
            return CompletableFuture.completedFuture(true);
        }

        CompletableFuture<Boolean> completion = new CompletableFuture<>();
        completion.whenComplete((result, throwable) -> context.completeTransactionWork());
        Runnable queuedStarter = () -> scheduleTransactionStart(context, work, entity, completion);
        if (context.claimTransactionWork(queuedStarter, completion)) {
            startTransaction(context, work, entity, completion);
        }
        return completion;
    }

    private static CompletableFuture<Boolean> prepareCreatedTransactions(Context context, Work work) {
        if (work.transactions.isEmpty()) {
            return CompletableFuture.completedFuture(true);
        }

        CompletableFuture<Boolean> completion = new CompletableFuture<>();
        completion.whenComplete((result, throwable) -> context.completeTransactionWork());
        Runnable queuedStarter = () -> scheduleCreatedTransactionStart(context, work, completion);
        if (context.claimTransactionWork(queuedStarter, completion)) {
            startCreatedTransaction(context, work, completion);
        }
        return completion;
    }

    private static void startTransaction(Context context, Work work, Entity entity, CompletableFuture<Boolean> completion) {
        try {
            if (context.isCancelled() || !entity.isValid() || !(entity instanceof InventoryHolder)) {
                completion.complete(false);
                return;
            }

            Inventory inventory = ((InventoryHolder) entity).getInventory();
            work.initializeTransactionContents(inventory);
            applyTransactionStep(context, work, entity, inventory, completion);
        }
        catch (Exception e) {
            context.cancel();
            ErrorReporter.report(e);
            completion.complete(false);
        }
    }

    private static void startCreatedTransaction(Context context, Work work, CompletableFuture<Boolean> completion) {
        try {
            if (context.isCancelled() || !work.initializeCreatedTransactionContents()) {
                completion.complete(false);
                return;
            }
            applyCreatedTransactionStep(context, work, completion);
        }
        catch (Exception e) {
            context.cancel();
            ErrorReporter.report(e);
            completion.complete(false);
        }
    }

    private static boolean applyPreparedTransactions(Work work, Entity entity) {
        if (work.transactions.isEmpty()) {
            return true;
        }
        if (!(entity instanceof InventoryHolder) || work.originalTransactionContents == null || work.transactionContents == null) {
            return false;
        }

        Inventory inventory = ((InventoryHolder) entity).getInventory();
        ItemStack[] currentContents = inventory.getStorageContents();
        if (currentContents.length != work.originalTransactionContents.length || inventory.getMaxStackSize() != work.inventoryMaxStackSize || !ItemUtils.compareContainers(work.originalTransactionContents, currentContents)) {
            return false;
        }
        inventory.setStorageContents(ItemUtils.getContainerState(work.transactionContents));
        work.transactionContentsApplied = true;
        return true;
    }

    private static void applyTransactionStep(Context context, Work work, Entity entity, Inventory inventory, CompletableFuture<Boolean> completion) {
        if (completion.isDone()) {
            return;
        }
        if (context.isCancelled() || !entity.isValid()) {
            completion.complete(false);
            return;
        }

        try {
            TransactionStep result = applyTransactionRows(context, work);
            if (result == TransactionStep.FAILED) {
                completion.complete(false);
                return;
            }
            if (result == TransactionStep.PENDING) {
                scheduleTransactionContinuation(context, work, entity, inventory, completion);
                return;
            }
            if (context.isCancelled() || !entity.isValid() || !ItemUtils.compareContainers(work.originalTransactionContents, inventory.getStorageContents())) {
                completion.complete(false);
                return;
            }
            inventory.setStorageContents(ItemUtils.getContainerState(work.transactionContents));
            work.transactionContentsApplied = true;
            completion.complete(true);
        }
        catch (Exception e) {
            context.cancel();
            ErrorReporter.report(e);
            completion.complete(false);
        }
    }

    private static void applyCreatedTransactionStep(Context context, Work work, CompletableFuture<Boolean> completion) {
        if (completion.isDone()) {
            return;
        }
        if (context.isCancelled()) {
            completion.complete(false);
            return;
        }

        try {
            TransactionStep result = applyTransactionRows(context, work);
            if (result == TransactionStep.FAILED) {
                completion.complete(false);
            }
            else if (result == TransactionStep.PENDING) {
                scheduleCreatedTransactionContinuation(context, work, completion);
            }
            else {
                completion.complete(true);
            }
        }
        catch (Exception e) {
            context.cancel();
            ErrorReporter.report(e);
            completion.complete(false);
        }
    }

    private static TransactionStep applyTransactionRows(Context context, Work work) {
        if (work.transactionIndex >= work.transactions.size()) {
            return TransactionStep.COMPLETE;
        }
        TransactionBudget budget = beginTransactionStep();
        if (budget == null) {
            return TransactionStep.PENDING;
        }

        long stepStart = System.nanoTime();
        int processed = 0;
        try {
            while (work.transactionIndex < work.transactions.size()) {
                Object[] row = work.transactions.get(work.transactionIndex);
                if (!applyTransaction(context.rollbackType, work.transactionContents, work.inventoryMaxStackSize, row)) {
                    return TransactionStep.FAILED;
                }
                work.transactionIndex++;
                work.appliedTransactions.add(row);
                work.appliedItemCount += (Integer) row[11];
                processed++;
                if (work.transactionIndex < work.transactions.size() && (processed >= budget.mutations || System.nanoTime() - stepStart >= budget.nanos)) {
                    return TransactionStep.PENDING;
                }
            }
            return TransactionStep.COMPLETE;
        }
        finally {
            endTransactionStep(processed, System.nanoTime() - stepStart);
        }
    }

    private static TransactionBudget beginTransactionStep() {
        synchronized (transactionBudgetLock) {
            if (transactionStepActive) {
                return null;
            }

            long now = System.nanoTime();
            if (now - transactionBudgetWindowStart >= TRANSACTION_BUDGET_WINDOW_NANOS) {
                transactionBudgetWindowStart = now;
                transactionBudgetNanos = 0;
                transactionBudgetMutations = 0;
            }
            int mutations = MAX_CHUNK_MUTATIONS_PER_STEP - transactionBudgetMutations;
            long nanos = CHUNK_STEP_BUDGET_NANOS - transactionBudgetNanos;
            if (mutations <= 0 || nanos <= 0) {
                return null;
            }

            transactionStepActive = true;
            return new TransactionBudget(mutations, nanos);
        }
    }

    private static void endTransactionStep(int mutations, long nanos) {
        synchronized (transactionBudgetLock) {
            transactionBudgetMutations += mutations;
            transactionBudgetNanos += Math.max(0L, nanos);
            transactionStepActive = false;
        }
    }

    private static boolean applyTransaction(int rollbackType, ItemStack[] contents, int inventoryMaxStackSize, Object[] row) {
        Material rowType = MaterialUtils.getType((Integer) row[6]);
        int rowAmount = (Integer) row[11];
        if (rowType == null || rowAmount <= 0) {
            return false;
        }

        ItemStack itemStack = new ItemStack(rowType, rowAmount);
        Object[] populatedStack = RollbackItemHandler.populateItemStack(itemStack, (byte[]) row[12]);
        int rowAction = (Integer) row[8];
        int action = ((rollbackType == 0 && rowAction == 0) || (rollbackType == 1 && rowAction == 1)) ? 1 : 0;
        return populatedStack[2] instanceof ItemStack && modifyTransactionContents(contents, (ItemStack) populatedStack[2], action, inventoryMaxStackSize);
    }

    private static boolean modifyTransactionContents(ItemStack[] contents, ItemStack itemStack, int action, int inventoryMaxStackSize) {
        int remaining = itemStack.getAmount();
        if (action == 0) {
            for (int index = contents.length - 1; index >= 0 && remaining > 0; index--) {
                ItemStack current = contents[index];
                if (current == null || !current.isSimilar(itemStack)) {
                    continue;
                }
                int removed = Math.min(remaining, current.getAmount());
                remaining -= removed;
                if (removed == current.getAmount()) {
                    contents[index] = null;
                }
                else {
                    current.setAmount(current.getAmount() - removed);
                }
            }
            return remaining == 0;
        }

        int maxStackSize = itemStack.getMaxStackSize();
        if (inventoryMaxStackSize > 0 && (maxStackSize < 0 || inventoryMaxStackSize < maxStackSize)) {
            maxStackSize = inventoryMaxStackSize;
        }
        if (maxStackSize < 1) {
            maxStackSize = 1;
        }
        for (ItemStack current : contents) {
            if (remaining <= 0) {
                return true;
            }
            if (current == null || !current.isSimilar(itemStack) || current.getAmount() >= maxStackSize) {
                continue;
            }
            int added = Math.min(remaining, maxStackSize - current.getAmount());
            current.setAmount(current.getAmount() + added);
            remaining -= added;
        }
        for (int index = 0; index < contents.length && remaining > 0; index++) {
            ItemStack current = contents[index];
            if (current != null && current.getType() != Material.AIR) {
                continue;
            }
            int added = Math.min(remaining, maxStackSize);
            ItemStack addedItem = itemStack.clone();
            addedItem.setAmount(added);
            contents[index] = addedItem;
            remaining -= added;
        }
        return remaining == 0;
    }

    private static void scheduleTransactionStart(Context context, Work work, Entity entity, CompletableFuture<Boolean> completion) {
        if (context.isCancelled()) {
            completion.complete(false);
            return;
        }
        Runnable start = () -> startTransaction(context, work, entity, completion);
        Runnable retired = () -> {
            context.cancel();
            completion.complete(false);
        };
        try {
            boolean scheduled = PaperAdapter.ADAPTER.executeEntityTask(CoreProtect.getInstance(), entity, start, retired, 1L);
            if (!scheduled) {
                if (ConfigHandler.isFolia) {
                    retired.run();
                }
                else {
                    Scheduler.scheduleSyncDelayedTask(CoreProtect.getInstance(), start, entity, 1);
                }
            }
        }
        catch (Exception e) {
            context.cancel();
            ErrorReporter.report(e);
            completion.complete(false);
        }
    }

    private static void scheduleCreatedTransactionStart(Context context, Work work, CompletableFuture<Boolean> completion) {
        if (context.isCancelled()) {
            completion.complete(false);
            return;
        }
        Location location = work.getLocation();
        if (location == null || location.getWorld() == null) {
            completion.complete(false);
            return;
        }
        try {
            Scheduler.scheduleSyncDelayedTask(CoreProtect.getInstance(), () -> startCreatedTransaction(context, work, completion), location, 1);
        }
        catch (Exception e) {
            context.cancel();
            ErrorReporter.report(e);
            completion.complete(false);
        }
    }

    private static void scheduleTransactionContinuation(Context context, Work work, Entity entity, Inventory inventory, CompletableFuture<Boolean> completion) {
        Runnable continuation = () -> applyTransactionStep(context, work, entity, inventory, completion);
        Runnable retired = () -> {
            context.cancel();
            completion.complete(false);
        };
        try {
            boolean scheduled = PaperAdapter.ADAPTER.executeEntityTask(CoreProtect.getInstance(), entity, continuation, retired, 1L);
            if (!scheduled) {
                if (ConfigHandler.isFolia) {
                    retired.run();
                }
                else {
                    Scheduler.scheduleSyncDelayedTask(CoreProtect.getInstance(), continuation, entity, 1);
                }
            }
        }
        catch (Exception e) {
            context.cancel();
            ErrorReporter.report(e);
            completion.complete(false);
        }
    }

    private static void scheduleCreatedTransactionContinuation(Context context, Work work, CompletableFuture<Boolean> completion) {
        Location location = work.getLocation();
        if (location == null || location.getWorld() == null) {
            completion.complete(false);
            return;
        }
        try {
            Scheduler.scheduleSyncDelayedTask(CoreProtect.getInstance(), () -> applyCreatedTransactionStep(context, work, completion), location, 1);
        }
        catch (Exception e) {
            context.cancel();
            ErrorReporter.report(e);
            completion.complete(false);
        }
    }

    private static boolean completeMissingRollback(Context context, Work work) {
        return context.complete(work, 0);
    }

    private static boolean completeUnresolved(Context context, Work work) {
        return work.operation.createsEntity() ? context.complete(work, 0) : completeMissingRollback(context, work);
    }

    private static CompletableFuture<Boolean> combine(List<CompletableFuture<Boolean>> futures) {
        if (futures.isEmpty()) {
            return CompletableFuture.completedFuture(true);
        }

        CompletableFuture<Void> all = CompletableFuture.allOf(futures.toArray(new CompletableFuture<?>[0]));
        return all.handle((ignored, throwable) -> {
            if (throwable != null) {
                return false;
            }
            for (CompletableFuture<Boolean> future : futures) {
                if (!Boolean.TRUE.equals(future.getNow(Boolean.FALSE))) {
                    return false;
                }
            }
            return true;
        });
    }

    private static void completeFrom(CompletableFuture<Boolean> target, CompletableFuture<Boolean> source) {
        source.whenComplete((result, throwable) -> {
            if (throwable != null) {
                target.completeExceptionally(throwable);
            }
            else {
                target.complete(Boolean.TRUE.equals(result));
            }
        });
    }

    private static Set<Integer> claimTrackingRows(Set<Integer> requiredRows) {
        Set<Integer> claimedRows = ConcurrentHashMap.newKeySet();
        for (Integer trackingRowId : requiredRows) {
            if (!activeTrackingRows.add(trackingRowId)) {
                for (Integer claimedRowId : claimedRows) {
                    releaseTrackingRow(claimedRowId);
                }
                return null;
            }
            claimedRows.add(trackingRowId);
        }
        return claimedRows;
    }

    private static boolean isEligible(Object[] row, int rollbackType) {
        return isEligible(row, rollbackType, false);
    }

    private static boolean isEligible(Object[] row, int rollbackType, boolean inventoryRollback) {
        int rolledBack = MaterialUtils.rolledBack((Integer) row[9], inventoryRollback);
        return (rollbackType == 0 && rolledBack == 0) || (rollbackType == 1 && rolledBack == 1);
    }

    private static Map<Integer, List<Object[]>> groupContainerRows(List<Object[]> rows, int rollbackType, boolean inventoryRollback) {
        Map<Integer, List<Object[]>> result = new HashMap<>();
        for (Object[] row : rows) {
            if (row.length <= 15 || !(row[15] instanceof Integer) || (Integer) row[15] <= 0 || !isEligible(row, rollbackType, inventoryRollback)) {
                continue;
            }
            result.computeIfAbsent((Integer) row[15], key -> new ArrayList<>()).add(row);
        }
        if (rollbackType == 1) {
            for (List<Object[]> transactions : result.values()) {
                Collections.reverse(transactions);
            }
        }
        return result;
    }

    private static byte[] serializeKillState(List<Object> killData) {
        if (killData.size() <= 2 || !(killData.get(2) instanceof List<?>)) {
            return null;
        }

        @SuppressWarnings("unchecked")
        List<Object> state = (List<Object>) killData.get(2);
        return EntityStatement.serializeData(state);
    }

    private static Location getRemovalLocation(EntitySpawnRecord record) {
        Location cached = EntitySpawnTracking.getCachedLocation(record.getUuid());
        return cached == null ? record.getLocation() : cached;
    }

    private static Location getRowLocation(Object[] row) {
        World world = Bukkit.getWorld(WorldUtils.getWorldName((Integer) row[10]));
        return world == null ? null : new Location(world, (Integer) row[3], (Integer) row[4], (Integer) row[5]);
    }

    private static Location getRemovalLocation(Work work) {
        Location cached = EntitySpawnTracking.getCachedLocation(work.record.getUuid());
        if (cached != null) {
            work.location = cached.clone();
        }
        return work.getLocation();
    }

    private static boolean sameChunk(Location first, Location second) {
        return first != null && second != null && first.getWorld() != null && second.getWorld() != null && first.getWorld().getUID().equals(second.getWorld().getUID()) && (first.getBlockX() >> 4) == (second.getBlockX() >> 4) && (first.getBlockZ() >> 4) == (second.getBlockZ() >> 4);
    }

    private static long chunkKey(Location location) {
        int chunkX = location.getBlockX() >> 4;
        int chunkZ = location.getBlockZ() >> 4;
        return chunkX & 0xffffffffL | (chunkZ & 0xffffffffL) << 32;
    }

    private enum ChunkPhase {
        RESOLVE,
        LOCALIZE,
        LOAD,
        SCAN,
        APPLY,
        COMPLETE
    }

    private static final class ChunkProcessingState {

        private final Context context;
        private final World world;
        private final int chunkX;
        private final int chunkZ;
        private final Location chunkLocation;
        private final List<Work> work;
        private final List<Work> unresolved = new ArrayList<>();
        private final List<Work> local = new ArrayList<>();
        private final Set<UUID> localUuids = new HashSet<>();
        private final Map<UUID, Entity> entities = new HashMap<>();
        private final List<CompletableFuture<Boolean>> pending = new ArrayList<>();
        private final CompletableFuture<Boolean> completion = new CompletableFuture<>();
        private ChunkPhase phase = ChunkPhase.RESOLVE;
        private Entity[] chunkEntities;
        private int index;
        private int entityLoadRetries;

        private ChunkProcessingState(Context context, World world, int chunkX, int chunkZ, List<Work> work) {
            this.context = context;
            this.world = world;
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.chunkLocation = new Location(world, chunkX << 4, 0, chunkZ << 4);
            this.work = work;
        }
    }

    static final class Context implements AutoCloseable {

        private final String userString;
        private final int preview;
        private final int rollbackType;
        private final boolean inventoryRollback;
        private final Set<Integer> claimedRows;
        private final UUID radiusWorldId;
        private final Integer[] radius;
        private final List<Work> work = new ArrayList<>();
        private final List<NoWorldTransition> directTransitions = new ArrayList<>();
        private final Map<Integer, Map<Long, List<Work>>> workByWorld = new HashMap<>();
        private final List<CompletableFuture<Boolean>> pending = new ArrayList<>();
        private final Deque<TransactionWork> transactionWaiters = new ArrayDeque<>();
        private final AtomicInteger items = new AtomicInteger();
        private final AtomicInteger entities = new AtomicInteger();
        private final AtomicBoolean cancelled = new AtomicBoolean();
        private int activeMutations;
        private boolean transactionWorkActive;
        private boolean transactionWorkAdvancing;

        private Context(String userString, int preview, int rollbackType, boolean inventoryRollback, Set<Integer> claimedRows, Location radiusOrigin, Integer[] radius) {
            this.userString = userString;
            this.preview = preview;
            this.rollbackType = rollbackType;
            this.inventoryRollback = inventoryRollback;
            this.claimedRows = claimedRows == null ? ConcurrentHashMap.newKeySet() : claimedRows;
            this.radiusWorldId = radiusOrigin == null || radiusOrigin.getWorld() == null ? null : radiusOrigin.getWorld().getUID();
            this.radius = radius == null ? null : radius.clone();
        }

        private void add(Work value) {
            Location location = value.getLocation();
            int worldId = WorldUtils.getWorldId(location.getWorld().getName());
            work.add(value);
            workByWorld.computeIfAbsent(worldId, key -> new HashMap<>()).computeIfAbsent(chunkKey(location), key -> new ArrayList<>()).add(value);
        }

        private void add(NoWorldTransition value) {
            directTransitions.add(value);
        }

        List<Work> getWork() {
            return work;
        }

        List<Work> getWork(int worldId, long key) {
            Map<Long, List<Work>> worldWork = workByWorld.get(worldId);
            return worldWork == null ? Collections.emptyList() : worldWork.getOrDefault(key, Collections.emptyList());
        }

        void reverseWork() {
            for (Map<Long, List<Work>> worldWork : workByWorld.values()) {
                for (List<Work> chunkWork : worldWork.values()) {
                    Collections.reverse(chunkWork);
                }
            }
        }

        synchronized void addPending(CompletableFuture<Boolean> future) {
            if (future != null && (!future.isDone() || !Boolean.TRUE.equals(future.getNow(Boolean.FALSE)))) {
                pending.add(future);
            }
        }

        synchronized List<CompletableFuture<Boolean>> drainPending() {
            List<CompletableFuture<Boolean>> result = new ArrayList<>(pending);
            pending.clear();
            return result;
        }

        int getEntityCount() {
            return entities.get();
        }

        int getItemCount() {
            return items.get();
        }

        boolean completeDirectTransitions() {
            for (NoWorldTransition transition : directTransitions) {
                if (isCancelled() || !transition(transition)) {
                    return false;
                }
            }
            directTransitions.clear();
            return true;
        }

        boolean isCancelled() {
            int[] rollbackData = ConfigHandler.rollbackHash.get(userString);
            return cancelled.get() || (rollbackData != null && rollbackData[3] == 2);
        }

        void cancel() {
            cancelled.set(true);
        }

        private synchronized boolean claimTransactionWork(Runnable queuedStarter, CompletableFuture<Boolean> completion) {
            if (!transactionWorkActive) {
                transactionWorkActive = true;
                return true;
            }
            transactionWaiters.addLast(new TransactionWork(queuedStarter, completion));
            return false;
        }

        private void completeTransactionWork() {
            synchronized (this) {
                if (transactionWorkAdvancing) {
                    return;
                }
                transactionWorkAdvancing = true;
            }
            while (true) {
                TransactionWork next;
                synchronized (this) {
                    next = transactionWaiters.pollFirst();
                    if (next == null) {
                        transactionWorkActive = false;
                        transactionWorkAdvancing = false;
                        return;
                    }
                }
                try {
                    next.starter.run();
                }
                catch (Exception e) {
                    cancel();
                    ErrorReporter.report(e);
                    next.completion.complete(false);
                }
                synchronized (this) {
                    if (!next.completion.isDone()) {
                        transactionWorkAdvancing = false;
                        return;
                    }
                }
            }
        }

        private synchronized boolean beginMutation() {
            if (isCancelled()) {
                return false;
            }
            activeMutations++;
            return true;
        }

        private synchronized void endMutation() {
            activeMutations--;
            notifyAll();
        }

        private synchronized void cancelAndAwaitMutations() {
            cancelled.set(true);
            boolean interrupted = false;
            while (activeMutations > 0) {
                try {
                    wait();
                }
                catch (InterruptedException e) {
                    interrupted = true;
                }
            }
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }

        private boolean isWithinRadius(Location location) {
            if (radius == null) {
                return true;
            }
            if (location == null || location.getWorld() == null || radiusWorldId == null || !radiusWorldId.equals(location.getWorld().getUID())) {
                return false;
            }
            if (location.getBlockX() < radius[1] || location.getBlockX() > radius[2] || location.getBlockZ() < radius[5] || location.getBlockZ() > radius[6]) {
                return false;
            }
            return radius[3] == null || radius[4] == null || (location.getBlockY() >= radius[3] && location.getBlockY() <= radius[4]);
        }

        private boolean complete(Work value, int count) {
            if (!value.complete.compareAndSet(false, true)) {
                return true;
            }
            releaseClaim(value.record.getRowId());
            entities.addAndGet(count);
            return true;
        }

        private boolean completePreview(Work value, int count) {
            if (!value.complete.compareAndSet(false, true)) {
                return true;
            }
            releaseClaim(value.record.getRowId());
            entities.addAndGet(count);
            items.addAndGet(value.transactionItemCount);
            return true;
        }

        private boolean transition(Work value, EntitySpawnData data, int count) {
            if (!value.complete.compareAndSet(false, true)) {
                return true;
            }
            try {
                publishTransition(data, value.appliedTransactions);
                claimedRows.remove(value.record.getRowId());
                items.addAndGet(value.appliedItemCount);
                entities.addAndGet(count);
                return true;
            }
            catch (Exception e) {
                releaseClaim(value.record.getRowId());
                cancel();
                ErrorReporter.report(e);
                return false;
            }
        }

        private boolean compositeTransition(Work value, EntitySpawnData data, int count) {
            if (!value.complete.compareAndSet(false, true)) {
                return true;
            }
            try {
                publishTransition(data, value.appliedTransactions);
                claimedRows.remove(value.record.getRowId());
                items.addAndGet(value.appliedItemCount);
                entities.addAndGet(count);
                return true;
            }
            catch (Exception e) {
                releaseClaim(value.record.getRowId());
                cancel();
                ErrorReporter.report(e);
                return false;
            }
        }

        private boolean containerTransition(Work value) {
            if (!value.complete.compareAndSet(false, true)) {
                return true;
            }
            try {
                publishTransition(EntitySpawnData.releaseClaim(value.record.getRowId()), value.appliedTransactions);
                claimedRows.remove(value.record.getRowId());
                items.addAndGet(value.appliedItemCount);
                return true;
            }
            catch (Exception e) {
                releaseClaim(value.record.getRowId());
                cancel();
                ErrorReporter.report(e);
                return false;
            }
        }

        private boolean transition(NoWorldTransition value) {
            if (preview > 0) {
                entities.addAndGet(value.count);
                items.addAndGet(value.transactionItemCount);
                return true;
            }
            try {
                publishTransition(value.data, value.transactions);
                claimedRows.remove(value.trackingRowId);
                items.addAndGet(value.transactionItemCount);
                entities.addAndGet(value.count);
                return true;
            }
            catch (Exception e) {
                releaseClaim(value.trackingRowId);
                cancel();
                ErrorReporter.report(e);
                return false;
            }
        }

        private void publishTransition(EntitySpawnData transition, List<Object[]> rows) {
            if (rows.isEmpty()) {
                Queue.queueEntitySpawnUpdate(transition);
                return;
            }
            Queue.queueEntityContainerRollbackUpdate(userString, transition, rows, rollbackType, inventoryRollback);
        }

        private void releaseClaim(int trackingRowId) {
            if (claimedRows.remove(trackingRowId)) {
                releaseTrackingRow(trackingRowId);
            }
        }

        @Override
        public void close() {
            cancelAndAwaitMutations();
            for (Integer trackingRowId : new HashSet<>(claimedRows)) {
                releaseClaim(trackingRowId);
            }
        }
    }

    private enum WorkOperation {
        SPAWN_ROLLBACK(false, true),
        SPAWN_RESTORE(true, true),
        KILL_ROLLBACK(true, false),
        KILL_RESTORE(false, false),
        COMPOSITE_ROLLBACK(true, false),
        CONTAINER_ROLLBACK(false, true),
        CONTAINER_RESTORE(false, true);

        private final boolean createsEntity;
        private final boolean currentRadius;

        WorkOperation(boolean createsEntity, boolean currentRadius) {
            this.createsEntity = createsEntity;
            this.currentRadius = currentRadius;
        }

        private boolean createsEntity() {
            return createsEntity;
        }

        private boolean enforcesCurrentRadius() {
            return currentRadius;
        }
    }

    private enum TransactionStep {
        COMPLETE,
        PENDING,
        FAILED
    }

    private static final class TransactionBudget {

        private final int mutations;
        private final long nanos;

        private TransactionBudget(int mutations, long nanos) {
            this.mutations = mutations;
            this.nanos = nanos;
        }
    }

    private static final class TransactionWork {

        private final Runnable starter;
        private final CompletableFuture<Boolean> completion;

        private TransactionWork(Runnable starter, CompletableFuture<Boolean> completion) {
            this.starter = starter;
            this.completion = completion;
        }
    }

    private static final class NoWorldTransition {

        private final int trackingRowId;
        private final EntitySpawnData data;
        private final int count;
        private final List<Object[]> transactions;
        private final int transactionItemCount;

        private NoWorldTransition(int trackingRowId, EntitySpawnData data, int count, List<Object[]> transactions) {
            this.trackingRowId = trackingRowId;
            this.data = data;
            this.count = count;
            this.transactions = new ArrayList<>(transactions);
            this.transactionItemCount = Work.itemCount(transactions);
        }

        private static NoWorldTransition compositeRestore(EntitySpawnRecord record, long spawnBlockRowId, long killBlockRowId, List<Object[]> transactions) {
            return new NoWorldTransition(record.getRowId(), EntitySpawnData.compositeRestore(spawnBlockRowId, killBlockRowId, record.getRowId(), record.getKillRowId()), 2, transactions);
        }

        private static NoWorldTransition compositeRollback(EntitySpawnRecord record, long spawnBlockRowId, long killBlockRowId, Location location, byte[] state) {
            return new NoWorldTransition(record.getRowId(), EntitySpawnData.compositeRollback(spawnBlockRowId, killBlockRowId, record.getRowId(), record.getKillRowId(), location, state), 2, Collections.emptyList());
        }
    }

    static final class Work {

        private final long blockRowId;
        private final long killBlockRowId;
        private final int killRowId;
        private final EntitySpawnRecord record;
        private final WorkOperation operation;
        private final EntityType type;
        private final List<Object> killData;
        private final List<Object[]> transactions;
        private final List<Object[]> appliedTransactions = new ArrayList<>();
        private final int transactionItemCount;
        private final AtomicBoolean complete = new AtomicBoolean();
        private final AtomicInteger locationRetries = new AtomicInteger();
        private int transactionIndex;
        private int appliedItemCount;
        private int inventoryMaxStackSize;
        private boolean transactionContentsApplied;
        private ItemStack[] originalTransactionContents;
        private ItemStack[] transactionContents;
        private volatile Location location;

        private Work(long blockRowId, long killBlockRowId, int killRowId, EntitySpawnRecord record, WorkOperation operation, EntityType type, Location location, List<Object> killData, List<Object[]> transactions) {
            this.blockRowId = blockRowId;
            this.killBlockRowId = killBlockRowId;
            this.killRowId = killRowId;
            this.record = record;
            this.operation = operation;
            this.type = type;
            this.location = location == null ? null : location.clone();
            this.killData = killData;
            this.transactions = new ArrayList<>(transactions);
            this.transactionItemCount = itemCount(transactions);
        }

        private static Work spawnRemoval(long blockRowId, EntitySpawnRecord record, Location location, List<Object[]> transactions) {
            return new Work(blockRowId, 0, 0, record, WorkOperation.SPAWN_ROLLBACK, null, location, null, transactions);
        }

        private static Work spawnRestore(long blockRowId, EntitySpawnRecord record, EntityType type, Location location, List<Object[]> transactions) {
            return new Work(blockRowId, 0, 0, record, WorkOperation.SPAWN_RESTORE, type, location, null, transactions);
        }

        private static Work killRollback(long blockRowId, int killRowId, EntitySpawnRecord record, EntityType type, Location location, List<Object> killData, List<Object[]> transactions) {
            return new Work(blockRowId, 0, killRowId, record, WorkOperation.KILL_ROLLBACK, type, location, killData, transactions);
        }

        private static Work killRestore(long blockRowId, int killRowId, EntitySpawnRecord record, Location location, List<Object[]> transactions) {
            return new Work(blockRowId, 0, killRowId, record, WorkOperation.KILL_RESTORE, null, location, null, transactions);
        }

        private static Work compositeRollback(long spawnBlockRowId, long killBlockRowId, int killRowId, EntitySpawnRecord record, EntityType type, Location location, List<Object> killData, List<Object[]> transactions) {
            return new Work(spawnBlockRowId, killBlockRowId, killRowId, record, WorkOperation.COMPOSITE_ROLLBACK, type, location, killData, transactions);
        }

        private static Work container(EntitySpawnRecord record, Location location, List<Object[]> transactions, int rollbackType) {
            WorkOperation operation = rollbackType == 0 ? WorkOperation.CONTAINER_ROLLBACK : WorkOperation.CONTAINER_RESTORE;
            return new Work(0, 0, 0, record, operation, null, location, null, transactions);
        }

        private static int itemCount(List<Object[]> transactions) {
            int result = 0;
            for (Object[] row : transactions) {
                if (row.length > 11 && row[11] instanceof Integer) {
                    result += (Integer) row[11];
                }
            }
            return result;
        }

        private void initializeTransactionContents(Inventory inventory) {
            if (transactionContents != null) {
                return;
            }
            originalTransactionContents = ItemUtils.getContainerState(inventory.getStorageContents());
            transactionContents = ItemUtils.getContainerState(originalTransactionContents);
            inventoryMaxStackSize = inventory.getMaxStackSize();
        }

        private boolean initializeCreatedTransactionContents() {
            if (transactionContents != null) {
                return true;
            }

            List<Object> state = record.getState();
            if (operation == WorkOperation.KILL_ROLLBACK || operation == WorkOperation.COMPOSITE_ROLLBACK) {
                if (killData == null || killData.size() <= 2 || !(killData.get(2) instanceof List<?>)) {
                    return false;
                }
                @SuppressWarnings("unchecked")
                List<Object> killState = (List<Object>) killData.get(2);
                state = killState;
            }

            ItemStack[] contents = EntitySpawnTracking.deserializeInventoryState(state);
            if (contents == null) {
                return false;
            }
            originalTransactionContents = ItemUtils.getContainerState(contents);
            transactionContents = ItemUtils.getContainerState(contents);
            inventoryMaxStackSize = DEFAULT_INVENTORY_MAX_STACK_SIZE;
            return true;
        }

        Location getLocation() {
            Location value = location;
            return value == null ? null : value.clone();
        }

        private boolean isComplete() {
            return complete.get();
        }
    }
}
