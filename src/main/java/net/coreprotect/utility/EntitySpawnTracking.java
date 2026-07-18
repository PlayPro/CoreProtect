package net.coreprotect.utility;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.TreeSpecies;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import net.coreprotect.CoreProtect;
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.consumer.Queue;
import net.coreprotect.model.entity.EntityInteractionOrigin;
import net.coreprotect.model.entity.EntitySpawnData;
import net.coreprotect.paper.PaperAdapter;
import net.coreprotect.thread.Scheduler;

public final class EntitySpawnTracking {

    private static final String TRACKING_KEY = "spawn";
    private static final String ORIGIN_SEED_KEY = "entity_origin";
    private static final String PENDING_IDENTITY_KEY = "pending_entity_identity";
    private static final int ORIGIN_SEED_SIZE = Integer.BYTES + Double.BYTES * 3;
    private static final int KILL_LOCATION_INDEX = 8;
    private static final long PENDING_CLEAR_TTL_MILLIS = 300_000L;
    private static final long UNLOADED_CACHE_RETENTION_MILLIS = 300_000L;
    private static final long CLEANUP_INTERVAL_MILLIS = 60_000L;
    private static final int CHUNK_SCAN_BATCH_SIZE = 64;
    private static final int ENTITY_SCAN_BATCH_SIZE = 256;
    private static final long ENTITY_SCAN_TIMEOUT_SECONDS = 30L;
    private static final Map<UUID, TrackedLocation> trackedLocations = new ConcurrentHashMap<>();
    private static final Map<UUID, PendingClear> pendingClear = new ConcurrentHashMap<>();
    private static final Map<UUID, PendingIdentityConfirmation> pendingIdentityConfirmations = new ConcurrentHashMap<>();
    private static final Map<UUID, Location> pendingIdentityVerifications = new ConcurrentHashMap<>();
    private static final Set<UUID> coreProtectRemovals = ConcurrentHashMap.newKeySet();
    private static final AtomicLong verificationEpoch = new AtomicLong();
    private static final AtomicLong nextCleanup = new AtomicLong();
    private static volatile NamespacedKey trackingKey;
    private static volatile NamespacedKey originSeedKey;
    private static volatile NamespacedKey pendingIdentityKey;

    private EntitySpawnTracking() {
        throw new IllegalStateException("Utility class");
    }

    public static boolean isPlacedEntity(Entity entity) {
        return entity instanceof Boat || entity instanceof Minecart;
    }

    public static boolean isPlacedEntityType(EntityType type) {
        if (type == null) {
            return false;
        }

        Class<? extends Entity> entityClass = type.getEntityClass();
        return entityClass != null && (Boat.class.isAssignableFrom(entityClass) || Minecart.class.isAssignableFrom(entityClass));
    }

    public static Set<Integer> getPlacedEntityTypeIds() {
        Set<Integer> ids = new LinkedHashSet<>();
        for (EntityType type : EntityType.values()) {
            if (!isPlacedEntityType(type)) {
                continue;
            }

            String name = type.name().toLowerCase(Locale.ROOT);
            Integer id = ConfigHandler.entities.get(name);
            if (id == null) {
                id = ConfigHandler.entities.get("minecraft:" + name);
            }
            if (id != null) {
                ids.add(id);
            }
        }
        return ids;
    }

    public static boolean isTracked(Entity entity) {
        return entity != null && entity.getPersistentDataContainer().has(getKey(), PersistentDataType.BYTE);
    }

    public static boolean isTrackedOrPendingIdentity(Entity entity) {
        return entity != null && (isTracked(entity) || entity.getPersistentDataContainer().has(getPendingIdentityKey(), PersistentDataType.BYTE));
    }

    public static void beginDatabaseIdentityPromotion(Entity entity) {
        if (entity != null && !isTracked(entity)) {
            pendingClear.remove(entity.getUniqueId());
            entity.getPersistentDataContainer().set(getPendingIdentityKey(), PersistentDataType.BYTE, (byte) 1);
        }
    }

    public static void track(Entity entity) {
        scheduleCleanup();
        UUID uuid = entity.getUniqueId();
        Location location = entity.getLocation();
        pendingClear.remove(uuid);
        pendingIdentityConfirmations.remove(uuid);
        entity.getPersistentDataContainer().set(getKey(), PersistentDataType.BYTE, (byte) 1);
        entity.getPersistentDataContainer().remove(getPendingIdentityKey());
        entity.getPersistentDataContainer().remove(getOriginSeedKey());
        trackedLocations.compute(uuid, (key, previous) -> TrackedLocation.from(location, true, verificationEpoch.get()));
    }

    public static void seedOrigin(Entity entity) {
        if (!isEligibleInteractionEntity(entity) || isTracked(entity)) {
            return;
        }
        byte[] existingSeed = entity.getPersistentDataContainer().get(getOriginSeedKey(), PersistentDataType.BYTE_ARRAY);
        if (existingSeed != null && existingSeed.length == ORIGIN_SEED_SIZE) {
            return;
        }

        Location location = entity.getLocation();
        if (location.getWorld() == null) {
            return;
        }
        int worldId = WorldUtils.getWorldId(location.getWorld().getName());
        if (worldId < 0) {
            return;
        }
        ByteBuffer buffer = ByteBuffer.allocate(ORIGIN_SEED_SIZE);
        buffer.putInt(worldId);
        buffer.putDouble(location.getX());
        buffer.putDouble(location.getY());
        buffer.putDouble(location.getZ());
        entity.getPersistentDataContainer().set(getOriginSeedKey(), PersistentDataType.BYTE_ARRAY, buffer.array());
    }

    public static EntityInteractionOrigin getOrCreateInteractionOrigin(Entity entity) {
        if (entity == null) {
            return null;
        }
        seedOrigin(entity);
        byte[] data = entity.getPersistentDataContainer().get(getOriginSeedKey(), PersistentDataType.BYTE_ARRAY);
        if (data == null || data.length != ORIGIN_SEED_SIZE) {
            Location location = entity.getLocation();
            if (location.getWorld() == null) {
                return null;
            }
            int worldId = WorldUtils.getWorldId(location.getWorld().getName());
            return worldId < 0 ? null : new EntityInteractionOrigin(worldId, location.getX(), location.getY(), location.getZ());
        }

        ByteBuffer buffer = ByteBuffer.wrap(data);
        int worldId = buffer.getInt();
        double x = buffer.getDouble();
        double y = buffer.getDouble();
        double z = buffer.getDouble();
        return new EntityInteractionOrigin(worldId, x, y, z);
    }

    public static boolean isEligibleInteractionEntity(Entity entity) {
        return entity instanceof LivingEntity && !(entity instanceof Player) && !(entity instanceof ArmorStand);
    }

    public static void confirmDatabaseIdentity(UUID uuid, Location location) {
        if (uuid == null || location == null || location.getWorld() == null) {
            return;
        }

        scheduleCleanup();
        PendingIdentityConfirmation confirmation = new PendingIdentityConfirmation(location, System.currentTimeMillis() + PENDING_CLEAR_TTL_MILLIS);
        pendingIdentityConfirmations.put(uuid, confirmation);
        CoreProtect plugin = CoreProtect.getInstance();
        if (plugin == null || !plugin.isEnabled()) {
            return;
        }

        try {
            Scheduler.runTask(plugin, () -> {
                Entity entity = Bukkit.getEntity(uuid);
                if (entity == null) {
                    return;
                }
                Runnable confirm = () -> applyIdentityConfirmation(entity, confirmation);
                if (ConfigHandler.isFolia && !PaperAdapter.ADAPTER.isOwnedByCurrentRegion(entity)) {
                    PaperAdapter.ADAPTER.executeEntityTask(plugin, entity, confirm, () -> {
                    });
                }
                else {
                    confirm.run();
                }
            }, location);
        }
        catch (Exception e) {
            ErrorReporter.report(e);
        }
    }

    public static void trackRevivedEntity(Entity entity, UUID previousUuid) {
        track(entity);
        Queue.queueEntitySpawnRevived(previousUuid, entity.getUniqueId(), entity.getLocation());
    }

    public static void handleLoad(Entity entity) {
        if (clearPendingTracking(entity)) {
            return;
        }
        PendingIdentityConfirmation confirmation = pendingIdentityConfirmations.get(entity.getUniqueId());
        if (confirmation != null && !confirmation.isExpired(System.currentTimeMillis())) {
            applyIdentityConfirmation(entity, confirmation);
        }
        if (isTracked(entity)) {
            observe(entity, true);
        }
        else if (entity.getPersistentDataContainer().has(getPendingIdentityKey(), PersistentDataType.BYTE)) {
            verifyPendingDatabaseIdentity(entity.getUniqueId(), entity.getLocation());
        }
    }

    public static void handleUnload(Entity entity) {
        if (clearPendingTracking(entity)) {
            return;
        }
        if (isTracked(entity)) {
            observe(entity, false);
        }
    }

    public static void handleTeleport(Entity entity, Location destination) {
        if (entity == null || destination == null || destination.getWorld() == null || !isTracked(entity)) {
            return;
        }
        observe(entity.getUniqueId(), destination, true);
    }

    public static void checkpoint(Entity entity, Location location) {
        if (entity != null && isTracked(entity)) {
            observe(entity.getUniqueId(), location, true);
        }
    }

    public static void initializeLoadedEntities() {
        if (ConfigHandler.isFolia) {
            return;
        }

        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                handleLoad(entity);
            }
        }
    }

    public static LoadedEntityRadius findLoadedEntities(Location location, Integer[] radius, Set<UUID> databaseCandidates) throws Exception {
        Set<UUID> inside = ConcurrentHashMap.newKeySet();
        Set<UUID> loadedCandidates = ConcurrentHashMap.newKeySet();
        if (location == null || location.getWorld() == null || radius == null) {
            return new LoadedEntityRadius(inside, loadedCandidates);
        }
        if (!ConfigHandler.isFolia && databaseCandidates.isEmpty() && trackedLocations.isEmpty()) {
            // Folia skips the startup entity sweep, so its scans can't rely on the tracked location cache being complete.
            return new LoadedEntityRadius(inside, loadedCandidates);
        }

        World world = location.getWorld();
        collectRecentlyUnloadedEntities(world, radius, databaseCandidates, inside, loadedCandidates);
        int minChunkX = radius[1] >> 4;
        int maxChunkX = radius[2] >> 4;
        int minChunkZ = radius[5] >> 4;
        int maxChunkZ = radius[6] >> 4;
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(ENTITY_SCAN_TIMEOUT_SECONDS);

        if (ConfigHandler.isFolia) {
            scanFoliaChunks(world, radius, inside, minChunkX, maxChunkX, minChunkZ, maxChunkZ, deadline);
            scanFoliaCandidates(world, radius, databaseCandidates, inside, loadedCandidates, deadline);
        }
        else {
            scanBukkitCandidates(world, radius, databaseCandidates, inside, loadedCandidates, deadline);
            scanBukkitChunks(world, radius, inside, minChunkX, maxChunkX, minChunkZ, maxChunkZ, deadline);
        }

        return new LoadedEntityRadius(inside, loadedCandidates);
    }

    private static void scanFoliaChunks(World world, Integer[] radius, Set<UUID> inside, int minChunkX, int maxChunkX, int minChunkZ, int maxChunkZ, long deadline) throws Exception {
        List<CompletableFuture<Void>> pending = new ArrayList<>(CHUNK_SCAN_BATCH_SIZE);
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                CompletableFuture<Void> completion = new CompletableFuture<>();
                pending.add(completion);
                int targetChunkX = chunkX;
                int targetChunkZ = chunkZ;
                Location chunkLocation = new Location(world, chunkX << 4, 0, chunkZ << 4);
                try {
                    Scheduler.runTask(CoreProtect.getInstance(), () -> {
                        try {
                            collectLoadedEntities(world, targetChunkX, targetChunkZ, radius, inside);
                            completion.complete(null);
                        }
                        catch (Exception e) {
                            completion.completeExceptionally(e);
                        }
                    }, chunkLocation);
                }
                catch (Exception e) {
                    completion.completeExceptionally(e);
                }

                if (pending.size() == CHUNK_SCAN_BATCH_SIZE) {
                    awaitAll(pending, deadline);
                    pending.clear();
                }
            }
        }
        awaitAll(pending, deadline);
    }

    private static void scanBukkitChunks(World world, Integer[] radius, Set<UUID> inside, int minChunkX, int maxChunkX, int minChunkZ, int maxChunkZ, long deadline) throws Exception {
        int[] chunkXs = new int[CHUNK_SCAN_BATCH_SIZE];
        int[] chunkZs = new int[CHUNK_SCAN_BATCH_SIZE];
        int batchSize = 0;
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                chunkXs[batchSize] = chunkX;
                chunkZs[batchSize] = chunkZ;
                batchSize++;
                if (batchSize == CHUNK_SCAN_BATCH_SIZE) {
                    scanBukkitChunkBatch(world, radius, inside, chunkXs, chunkZs, batchSize, deadline);
                    batchSize = 0;
                }
            }
        }
        if (batchSize > 0) {
            scanBukkitChunkBatch(world, radius, inside, chunkXs, chunkZs, batchSize, deadline);
        }
    }

    private static void scanBukkitChunkBatch(World world, Integer[] radius, Set<UUID> inside, int[] chunkXs, int[] chunkZs, int batchSize, long deadline) throws Exception {
        CompletableFuture<Void> completion = new CompletableFuture<>();
        try {
            Scheduler.runTask(CoreProtect.getInstance(), () -> {
                try {
                    for (int index = 0; index < batchSize; index++) {
                        collectLoadedEntities(world, chunkXs[index], chunkZs[index], radius, inside);
                    }
                    completion.complete(null);
                }
                catch (Exception e) {
                    completion.completeExceptionally(e);
                }
            });
        }
        catch (Exception e) {
            completion.completeExceptionally(e);
        }
        await(completion, deadline);
    }

    private static void scanFoliaCandidates(World world, Integer[] radius, Set<UUID> databaseCandidates, Set<UUID> inside, Set<UUID> loadedCandidates, long deadline) throws Exception {
        UUID[] batch = new UUID[ENTITY_SCAN_BATCH_SIZE];
        int batchSize = 0;
        for (UUID uuid : databaseCandidates) {
            batch[batchSize++] = uuid;
            if (batchSize == ENTITY_SCAN_BATCH_SIZE) {
                scanFoliaCandidateBatch(world, radius, inside, loadedCandidates, batch, batchSize, deadline);
                batchSize = 0;
            }
        }
        if (batchSize > 0) {
            scanFoliaCandidateBatch(world, radius, inside, loadedCandidates, batch, batchSize, deadline);
        }
    }

    private static void scanFoliaCandidateBatch(World world, Integer[] radius, Set<UUID> inside, Set<UUID> loadedCandidates, UUID[] batch, int batchSize, long deadline) throws Exception {
        CompletableFuture<Void> completion = new CompletableFuture<>();
        try {
            Scheduler.runTask(CoreProtect.getInstance(), () -> {
                try {
                    List<CompletableFuture<Void>> entityTasks = new ArrayList<>(batchSize);
                    for (int index = 0; index < batchSize; index++) {
                        UUID uuid = batch[index];
                        Entity entity = Bukkit.getEntity(uuid);
                        if (entity == null) {
                            continue;
                        }

                        CompletableFuture<Void> entityCompletion = new CompletableFuture<>();
                        entityTasks.add(entityCompletion);
                        boolean scheduled = PaperAdapter.ADAPTER.executeEntityTask(CoreProtect.getInstance(), entity, () -> {
                            try {
                                loadedCandidates.add(uuid);
                                if (isTracked(entity) && isWithinRadius(entity.getLocation(), world, radius)) {
                                    inside.add(uuid);
                                }
                                entityCompletion.complete(null);
                            }
                            catch (Exception e) {
                                entityCompletion.completeExceptionally(e);
                            }
                        }, () -> entityCompletion.complete(null));
                        if (!scheduled) {
                            entityCompletion.complete(null);
                        }
                    }
                    completeWhenAll(entityTasks, completion);
                }
                catch (Exception e) {
                    completion.completeExceptionally(e);
                }
            });
        }
        catch (Exception e) {
            completion.completeExceptionally(e);
        }
        await(completion, deadline);
    }

    private static void scanBukkitCandidates(World world, Integer[] radius, Set<UUID> databaseCandidates, Set<UUID> inside, Set<UUID> loadedCandidates, long deadline) throws Exception {
        UUID[] batch = new UUID[ENTITY_SCAN_BATCH_SIZE];
        int batchSize = 0;
        for (UUID uuid : databaseCandidates) {
            batch[batchSize++] = uuid;
            if (batchSize == ENTITY_SCAN_BATCH_SIZE) {
                scanBukkitCandidateBatch(world, radius, inside, loadedCandidates, batch, batchSize, deadline);
                batchSize = 0;
            }
        }
        if (batchSize > 0) {
            scanBukkitCandidateBatch(world, radius, inside, loadedCandidates, batch, batchSize, deadline);
        }
    }

    private static void scanBukkitCandidateBatch(World world, Integer[] radius, Set<UUID> inside, Set<UUID> loadedCandidates, UUID[] batch, int batchSize, long deadline) throws Exception {
        CompletableFuture<Void> completion = new CompletableFuture<>();
        try {
            Scheduler.runTask(CoreProtect.getInstance(), () -> {
                try {
                    for (int index = 0; index < batchSize; index++) {
                        UUID uuid = batch[index];
                        Entity entity = Bukkit.getEntity(uuid);
                        if (entity != null) {
                            loadedCandidates.add(uuid);
                            if (isTracked(entity) && isWithinRadius(entity.getLocation(), world, radius)) {
                                inside.add(uuid);
                            }
                        }
                    }
                    completion.complete(null);
                }
                catch (Exception e) {
                    completion.completeExceptionally(e);
                }
            });
        }
        catch (Exception e) {
            completion.completeExceptionally(e);
        }
        await(completion, deadline);
    }

    private static void completeWhenAll(List<CompletableFuture<Void>> pending, CompletableFuture<Void> completion) {
        CompletableFuture.allOf(pending.toArray(new CompletableFuture<?>[0])).whenComplete((ignored, throwable) -> {
            if (throwable == null) {
                completion.complete(null);
            }
            else {
                completion.completeExceptionally(throwable);
            }
        });
    }

    private static void awaitAll(List<CompletableFuture<Void>> pending, long deadline) throws Exception {
        if (!pending.isEmpty()) {
            await(CompletableFuture.allOf(pending.toArray(new CompletableFuture<?>[0])), deadline);
        }
    }

    private static void await(CompletableFuture<Void> completion, long deadline) throws Exception {
        long remaining = deadline - System.nanoTime();
        if (remaining <= 0L) {
            throw new TimeoutException("Timed out scanning loaded tracked entities");
        }
        completion.get(remaining, TimeUnit.NANOSECONDS);
    }

    public static Map<UUID, Location> findLoadedLocations(Collection<UUID> uuids) throws Exception {
        Map<UUID, Location> locations = new ConcurrentHashMap<>();
        if (uuids.isEmpty()) {
            return locations;
        }

        CompletableFuture<Void> completion = new CompletableFuture<>();
        if (ConfigHandler.isFolia) {
            Scheduler.runTask(CoreProtect.getInstance(), () -> {
                try {
                    List<CompletableFuture<Void>> pending = new ArrayList<>();
                    for (UUID uuid : uuids) {
                        Entity entity = Bukkit.getEntity(uuid);
                        if (entity == null) {
                            continue;
                        }

                        CompletableFuture<Void> entityCompletion = new CompletableFuture<>();
                        pending.add(entityCompletion);
                        boolean scheduled = PaperAdapter.ADAPTER.executeEntityTask(CoreProtect.getInstance(), entity, () -> {
                            try {
                                if (isTracked(entity)) {
                                    locations.put(uuid, entity.getLocation());
                                }
                                entityCompletion.complete(null);
                            }
                            catch (Exception e) {
                                entityCompletion.completeExceptionally(e);
                            }
                        }, () -> entityCompletion.complete(null));
                        if (!scheduled) {
                            entityCompletion.complete(null);
                        }
                    }
                    CompletableFuture.allOf(pending.toArray(new CompletableFuture<?>[0])).whenComplete((ignored, throwable) -> {
                        if (throwable == null) {
                            completion.complete(null);
                        }
                        else {
                            completion.completeExceptionally(throwable);
                        }
                    });
                }
                catch (Exception e) {
                    completion.completeExceptionally(e);
                }
            });
        }
        else {
            Scheduler.runTask(CoreProtect.getInstance(), () -> {
                try {
                    for (UUID uuid : uuids) {
                        Entity entity = Bukkit.getEntity(uuid);
                        if (isTracked(entity)) {
                            locations.put(uuid, entity.getLocation());
                        }
                    }
                    completion.complete(null);
                }
                catch (Exception e) {
                    completion.completeExceptionally(e);
                }
            });
        }

        completion.get(30, TimeUnit.SECONDS);
        return locations;
    }

    public static Location getCachedLocation(UUID uuid) {
        scheduleCleanup();
        TrackedLocation tracked = trackedLocations.get(uuid);
        if (tracked != null && tracked.isExpired(System.currentTimeMillis())) {
            trackedLocations.remove(uuid, tracked);
            return null;
        }
        return tracked == null ? null : tracked.getLocation();
    }

    public static void forget(UUID uuid) {
        if (uuid != null) {
            trackedLocations.remove(uuid);
        }
    }

    public static void removeWithoutRemovalLog(Entity entity) {
        UUID uuid = entity.getUniqueId();
        coreProtectRemovals.add(uuid);
        try {
            entity.remove();
        }
        finally {
            coreProtectRemovals.remove(uuid);
        }
    }

    public static boolean isCoreProtectRemoval(UUID uuid) {
        return coreProtectRemovals.contains(uuid);
    }

    public static void clearTracking(UUID uuid) {
        scheduleCleanup();
        pendingIdentityConfirmations.remove(uuid);
        PendingClear request = new PendingClear(System.currentTimeMillis() + PENDING_CLEAR_TTL_MILLIS);
        pendingClear.put(uuid, request);
        Location location = getCachedLocation(uuid);
        CoreProtect plugin = CoreProtect.getInstance();
        if (plugin == null || !plugin.isEnabled()) {
            forget(uuid);
            return;
        }

        try {
            Scheduler.runTask(CoreProtect.getInstance(), () -> {
                try {
                    Entity entity = Bukkit.getEntity(uuid);
                    if (entity == null) {
                        forget(uuid);
                        return;
                    }

                    Runnable clear = () -> {
                        if (!pendingClear.remove(uuid, request)) {
                            return;
                        }
                        entity.getPersistentDataContainer().remove(getKey());
                        entity.getPersistentDataContainer().remove(getPendingIdentityKey());
                        forget(uuid);
                    };
                    if (ConfigHandler.isFolia && !PaperAdapter.ADAPTER.isOwnedByCurrentRegion(entity)) {
                        Runnable retired = () -> {
                            if (pendingClear.remove(uuid, request)) {
                                forget(uuid);
                            }
                        };
                        if (!PaperAdapter.ADAPTER.executeEntityTask(CoreProtect.getInstance(), entity, clear, retired)) {
                            retired.run();
                        }
                    }
                    else {
                        clear.run();
                    }
                }
                catch (Exception e) {
                    ErrorReporter.report(e);
                }
            }, location);
        }
        catch (Exception e) {
            ErrorReporter.report(e);
        }
    }

    public static void queueLoadedLocationsForShutdown() {
        List<CompletableFuture<Void>> pending = new ArrayList<>();
        for (Map.Entry<UUID, TrackedLocation> entry : trackedLocations.entrySet()) {
            CompletableFuture<Void> completion = null;
            try {
                UUID uuid = entry.getKey();
                if (!entry.getValue().isLoaded()) {
                    continue;
                }

                Entity entity = Bukkit.getEntity(uuid);
                if (entity == null) {
                    forget(uuid);
                    continue;
                }

                if (ConfigHandler.isFolia) {
                    completion = new CompletableFuture<>();
                    pending.add(completion);
                    CompletableFuture<Void> entityCompletion = completion;
                    Runnable retired = () -> {
                        forget(uuid);
                        entityCompletion.complete(null);
                    };
                    boolean scheduled = PaperAdapter.ADAPTER.executeEntityTask(CoreProtect.getInstance(), entity, () -> {
                        try {
                            checkpointLoadedEntity(uuid, entity);
                        }
                        catch (Exception e) {
                            ErrorReporter.report(e);
                        }
                        finally {
                            entityCompletion.complete(null);
                        }
                    }, retired);
                    if (!scheduled) {
                        retired.run();
                    }
                }
                else {
                    checkpointLoadedEntity(uuid, entity);
                }
            }
            catch (Exception e) {
                ErrorReporter.report(e);
                if (completion != null) {
                    completion.complete(null);
                }
            }
        }

        if (!pending.isEmpty()) {
            try {
                CompletableFuture.allOf(pending.toArray(new CompletableFuture<?>[0])).get(30, TimeUnit.SECONDS);
            }
            catch (Exception e) {
                ErrorReporter.report(e);
            }
        }
    }

    public static void invalidateDatabaseVerification() {
        long epoch = verificationEpoch.incrementAndGet();
        long previousEpoch = epoch - 1L;
        for (UUID uuid : trackedLocations.keySet()) {
            Location[] location = new Location[1];
            boolean[] verify = new boolean[1];
            boolean[] current = new boolean[1];
            trackedLocations.computeIfPresent(uuid, (key, tracked) -> {
                if (epoch != verificationEpoch.get()) {
                    return tracked;
                }
                current[0] = true;
                if (tracked.isVerified(epoch)) {
                    return tracked;
                }
                verify[0] = tracked.isVerified(previousEpoch);
                if (verify[0]) {
                    return tracked.withPendingVerification(epoch);
                }
                location[0] = tracked.getLocation();
                return tracked.withVerifiedEpoch(-1L);
            });
            if (!current[0]) {
                continue;
            }
            if (verify[0]) {
                Queue.queueEntitySpawnUpdate(EntitySpawnData.verify(uuid, epoch));
            }
            else if (location[0] != null) {
                Queue.queueEntitySpawnLocation(uuid, location[0], epoch);
            }
        }
    }

    public static void reverifyDatabaseRow(UUID uuid, Location loggedLocation) {
        if (uuid == null || loggedLocation == null || loggedLocation.getWorld() == null) {
            return;
        }

        pendingClear.remove(uuid);
        long[] epoch = new long[1];
        Location[] verificationLocation = new Location[1];
        boolean[] verify = new boolean[1];
        trackedLocations.compute(uuid, (key, tracked) -> {
            epoch[0] = verificationEpoch.get();
            if (tracked == null) {
                verificationLocation[0] = loggedLocation;
                return TrackedLocation.from(loggedLocation, true, -1L);
            }
            verify[0] = tracked.isVerified(epoch[0]);
            if (verify[0]) {
                return tracked.withPendingVerification(epoch[0]);
            }
            verificationLocation[0] = tracked.getLocation();
            return tracked.withVerifiedEpoch(-1L);
        });
        if (verify[0]) {
            Queue.queueEntitySpawnUpdate(EntitySpawnData.verify(uuid, epoch[0]));
        }
        else if (verificationLocation[0] != null) {
            Queue.queueEntitySpawnLocation(uuid, verificationLocation[0], epoch[0]);
        }
    }

    public static void verifyPendingDatabaseIdentity(UUID uuid, Location location) {
        if (uuid == null || location == null || location.getWorld() == null) {
            return;
        }

        Location verificationLocation = location.clone();
        if (pendingIdentityVerifications.putIfAbsent(uuid, verificationLocation) != null) {
            return;
        }
        try {
            Queue.queueEntitySpawnUpdate(EntitySpawnData.verify(uuid, verificationEpoch.get()));
        }
        catch (RuntimeException exception) {
            pendingIdentityVerifications.remove(uuid, verificationLocation);
            throw exception;
        }
    }

    public static void confirmDatabaseIdentityMissing(UUID uuid) {
        if (pendingIdentityVerifications.remove(uuid) == null) {
            clearTracking(uuid);
        }
    }

    public static boolean retryPendingDatabaseIdentityVerification(UUID uuid) {
        Location location = pendingIdentityVerifications.remove(uuid);
        if (location == null) {
            return false;
        }
        verifyPendingDatabaseIdentity(uuid, location);
        return true;
    }

    public static void confirmDatabaseVerification(UUID uuid, long epoch) {
        trackedLocations.computeIfPresent(uuid, (key, tracked) -> epoch == verificationEpoch.get() && tracked.isPendingVerification(epoch) ? tracked.withVerifiedEpoch(epoch) : tracked);
        Location pendingIdentityLocation = pendingIdentityVerifications.remove(uuid);
        if (pendingIdentityLocation != null) {
            confirmDatabaseIdentity(uuid, pendingIdentityLocation);
        }
    }

    public static void confirmDatabaseLocation(UUID uuid, Location location, long epoch) {
        Location[] retryLocation = new Location[1];
        trackedLocations.computeIfPresent(uuid, (key, tracked) -> {
            if (epoch != verificationEpoch.get()) {
                return tracked;
            }
            if (tracked.matches(location)) {
                return tracked.withVerifiedEpoch(epoch);
            }
            retryLocation[0] = tracked.getLocation();
            return tracked.withVerifiedEpoch(-1L);
        });
        if (retryLocation[0] != null) {
            Queue.queueEntitySpawnLocation(uuid, retryLocation[0], epoch);
        }
    }

    public static List<Object> serializeState(Entity entity) {
        List<Object> state = new ArrayList<>();
        state.add(entity.getCustomName());
        state.add(entity.isCustomNameVisible());

        String boatType = null;
        if (entity instanceof Boat) {
            TreeSpecies woodType = ((Boat) entity).getWoodType();
            boatType = woodType == null ? null : woodType.name();
        }
        state.add(boatType);

        List<Object> inventoryData = null;
        if (entity instanceof InventoryHolder) {
            Inventory inventory = ((InventoryHolder) entity).getInventory();
            inventoryData = new ArrayList<>(inventory.getSize());
            for (ItemStack item : inventory.getContents()) {
                inventoryData.add(item == null ? null : item.serialize());
            }
        }
        state.add(inventoryData);

        List<Object> equipmentData = null;
        if (entity instanceof LivingEntity) {
            EntityEquipment equipment = ((LivingEntity) entity).getEquipment();
            if (equipment != null) {
                equipmentData = new ArrayList<>(6);
                equipmentData.add(serializeItem(equipment.getItemInMainHand()));
                equipmentData.add(serializeItem(equipment.getItemInOffHand()));
                equipmentData.add(serializeItem(equipment.getBoots()));
                equipmentData.add(serializeItem(equipment.getLeggings()));
                equipmentData.add(serializeItem(equipment.getChestplate()));
                equipmentData.add(serializeItem(equipment.getHelmet()));
            }
        }
        state.add(equipmentData);
        return state;
    }

    public static List<Object> serializeKillData(Entity entity) {
        List<Object> data = new ArrayList<>();
        data.add(new ArrayList<>());
        data.add(new ArrayList<>());
        data.add(serializeState(entity));
        data.add(entity.isCustomNameVisible());
        data.add(entity.getCustomName());
        data.add(new ArrayList<>());
        data.add(new ArrayList<>());
        data.add(isTracked(entity) ? entity.getUniqueId().toString() : null);

        Location location = entity.getLocation();
        List<Object> serializedLocation = new ArrayList<>();
        serializedLocation.add(location.getX());
        serializedLocation.add(location.getY());
        serializedLocation.add(location.getZ());
        serializedLocation.add(location.getYaw());
        serializedLocation.add(location.getPitch());
        data.add(serializedLocation);
        return data;
    }

    public static Location getKillRestoreLocation(World world, List<Object> data) {
        if (world == null || data == null || data.size() <= KILL_LOCATION_INDEX || !(data.get(KILL_LOCATION_INDEX) instanceof List<?>)) {
            return null;
        }

        List<?> serializedLocation = (List<?>) data.get(KILL_LOCATION_INDEX);
        if (serializedLocation.size() < 5 || !(serializedLocation.get(0) instanceof Number) || !(serializedLocation.get(1) instanceof Number) || !(serializedLocation.get(2) instanceof Number) || !(serializedLocation.get(3) instanceof Number) || !(serializedLocation.get(4) instanceof Number)) {
            return null;
        }

        return new Location(world, ((Number) serializedLocation.get(0)).doubleValue(), ((Number) serializedLocation.get(1)).doubleValue(), ((Number) serializedLocation.get(2)).doubleValue(), ((Number) serializedLocation.get(3)).floatValue(), ((Number) serializedLocation.get(4)).floatValue());
    }

    public static void restoreKillState(Entity entity, List<Object> data) {
        if (!isPlacedEntity(entity) || data == null || data.size() <= 2 || !(data.get(2) instanceof List<?>)) {
            return;
        }

        @SuppressWarnings("unchecked")
        List<Object> state = (List<Object>) data.get(2);
        restoreState(entity, state);
    }

    public static ItemStack[] deserializeInventoryState(List<Object> state) {
        if (state == null || state.size() <= 3 || !(state.get(3) instanceof List<?>)) {
            return null;
        }

        List<?> inventoryData = (List<?>) state.get(3);
        ItemStack[] contents = new ItemStack[inventoryData.size()];
        for (int slot = 0; slot < contents.length; slot++) {
            contents[slot] = deserializeItem(inventoryData.get(slot));
        }
        return contents;
    }

    public static void restoreState(Entity entity, List<Object> state) {
        if (state == null || state.isEmpty()) {
            return;
        }

        if (state.size() > 0) {
            entity.setCustomName((String) state.get(0));
        }
        if (state.size() > 1 && state.get(1) instanceof Boolean) {
            entity.setCustomNameVisible((Boolean) state.get(1));
        }
        if (entity instanceof Boat && state.size() > 2 && state.get(2) instanceof String) {
            try {
                ((Boat) entity).setWoodType(TreeSpecies.valueOf((String) state.get(2)));
            }
            catch (Exception ignored) {
            }
        }
        if (entity instanceof InventoryHolder && state.size() > 3 && state.get(3) instanceof List<?>) {
            Inventory inventory = ((InventoryHolder) entity).getInventory();
            List<?> inventoryData = (List<?>) state.get(3);
            ItemStack[] contents = new ItemStack[inventory.getSize()];
            for (int slot = 0; slot < contents.length && slot < inventoryData.size(); slot++) {
                contents[slot] = deserializeItem(inventoryData.get(slot));
            }
            inventory.setContents(contents);
        }
        if (entity instanceof LivingEntity && state.size() > 4 && state.get(4) instanceof List<?>) {
            EntityEquipment equipment = ((LivingEntity) entity).getEquipment();
            List<?> equipmentData = (List<?>) state.get(4);
            if (equipment != null) {
                equipment.setItemInMainHand(getItem(equipmentData, 0));
                equipment.setItemInOffHand(getItem(equipmentData, 1));
                equipment.setBoots(getItem(equipmentData, 2));
                equipment.setLeggings(getItem(equipmentData, 3));
                equipment.setChestplate(getItem(equipmentData, 4));
                equipment.setHelmet(getItem(equipmentData, 5));
            }
        }
    }

    private static Object serializeItem(ItemStack item) {
        return item == null ? null : item.serialize();
    }

    private static ItemStack getItem(List<?> items, int index) {
        return index < items.size() ? deserializeItem(items.get(index)) : null;
    }

    private static ItemStack deserializeItem(Object value) {
        if (!(value instanceof Map<?, ?>)) {
            return null;
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> itemData = (Map<String, Object>) value;
        try {
            return ItemStack.deserialize(itemData);
        }
        catch (Exception ignored) {
            return null;
        }
    }

    private static NamespacedKey getKey() {
        NamespacedKey key = trackingKey;
        if (key == null) {
            synchronized (EntitySpawnTracking.class) {
                key = trackingKey;
                if (key == null) {
                    key = new NamespacedKey(CoreProtect.getInstance(), TRACKING_KEY);
                    trackingKey = key;
                }
            }
        }
        return key;
    }

    private static NamespacedKey getOriginSeedKey() {
        NamespacedKey key = originSeedKey;
        if (key == null) {
            synchronized (EntitySpawnTracking.class) {
                key = originSeedKey;
                if (key == null) {
                    key = new NamespacedKey(CoreProtect.getInstance(), ORIGIN_SEED_KEY);
                    originSeedKey = key;
                }
            }
        }
        return key;
    }

    private static NamespacedKey getPendingIdentityKey() {
        NamespacedKey key = pendingIdentityKey;
        if (key == null) {
            synchronized (EntitySpawnTracking.class) {
                key = pendingIdentityKey;
                if (key == null) {
                    key = new NamespacedKey(CoreProtect.getInstance(), PENDING_IDENTITY_KEY);
                    pendingIdentityKey = key;
                }
            }
        }
        return key;
    }

    private static void applyIdentityConfirmation(Entity entity, PendingIdentityConfirmation confirmation) {
        UUID uuid = entity.getUniqueId();
        if (!pendingIdentityConfirmations.remove(uuid, confirmation)) {
            return;
        }
        if (pendingClear.containsKey(uuid)) {
            return;
        }

        entity.getPersistentDataContainer().set(getKey(), PersistentDataType.BYTE, (byte) 1);
        entity.getPersistentDataContainer().remove(getPendingIdentityKey());
        entity.getPersistentDataContainer().remove(getOriginSeedKey());
        Location location = entity.getLocation();
        long epoch = verificationEpoch.get();
        boolean locationConfirmed = TrackedLocation.from(confirmation.location, true, epoch).matches(location);
        trackedLocations.put(uuid, TrackedLocation.from(location, true, locationConfirmed ? epoch : -1L));
        if (!locationConfirmed) {
            Queue.queueEntitySpawnLocation(uuid, location, epoch);
        }
    }

    private static boolean clearPendingTracking(Entity entity) {
        UUID uuid = entity.getUniqueId();
        PendingClear request = pendingClear.get(uuid);
        if (request == null) {
            return false;
        }
        if (request.expiry < System.currentTimeMillis()) {
            pendingClear.remove(uuid, request);
            return false;
        }
        if (!pendingClear.remove(uuid, request)) {
            return false;
        }

        pendingIdentityConfirmations.remove(uuid);
        entity.getPersistentDataContainer().remove(getKey());
        entity.getPersistentDataContainer().remove(getPendingIdentityKey());
        forget(uuid);
        return true;
    }

    private static void collectLoadedEntities(World world, int chunkX, int chunkZ, Integer[] radius, Set<UUID> result) {
        if (!world.isChunkLoaded(chunkX, chunkZ)) {
            return;
        }

        for (Entity entity : world.getChunkAt(chunkX, chunkZ).getEntities()) {
            if (isTracked(entity) && isWithinRadius(entity.getLocation(), world, radius)) {
                result.add(entity.getUniqueId());
            }
        }
    }

    private static void collectRecentlyUnloadedEntities(World world, Integer[] radius, Set<UUID> databaseCandidates, Set<UUID> inside, Set<UUID> overriddenCandidates) {
        scheduleCleanup();
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, TrackedLocation> entry : trackedLocations.entrySet()) {
            TrackedLocation tracked = entry.getValue();
            if (tracked.isLoaded()) {
                continue;
            }
            if (tracked.isExpired(now)) {
                trackedLocations.remove(entry.getKey(), tracked);
                continue;
            }

            UUID uuid = entry.getKey();
            if (databaseCandidates.contains(uuid)) {
                overriddenCandidates.add(uuid);
            }
            if (tracked.isWithinRadius(world, radius)) {
                inside.add(uuid);
            }
        }
    }

    private static boolean isWithinRadius(Location location, World world, Integer[] radius) {
        if (location.getWorld() == null || !location.getWorld().getUID().equals(world.getUID())) {
            return false;
        }
        if (location.getBlockX() < radius[1] || location.getBlockX() > radius[2] || location.getBlockZ() < radius[5] || location.getBlockZ() > radius[6]) {
            return false;
        }
        return radius[3] == null || radius[4] == null || (location.getBlockY() >= radius[3] && location.getBlockY() <= radius[4]);
    }

    private static void observe(Entity entity, boolean loaded) {
        observe(entity.getUniqueId(), entity.getLocation(), loaded);
    }

    private static void observe(UUID uuid, Location location, boolean loaded) {
        if (uuid == null || location == null || location.getWorld() == null) {
            return;
        }

        scheduleCleanup();
        long now = System.currentTimeMillis();
        long[] updateEpoch = { -1L };
        trackedLocations.compute(uuid, (key, previous) -> {
            long epoch = verificationEpoch.get();
            if (previous != null && previous.isExpired(now)) {
                previous = null;
            }

            boolean update = previous == null || !previous.matches(location) || (loaded && !previous.isLoaded() && !previous.isVerified(epoch));
            if (update) {
                updateEpoch[0] = epoch;
                return TrackedLocation.from(location, loaded, -1L);
            }
            return previous.withLoaded(loaded);
        });
        if (updateEpoch[0] >= 0L) {
            Queue.queueEntitySpawnLocation(uuid, location, updateEpoch[0]);
        }
    }

    private static void checkpointLoadedEntity(UUID uuid, Entity entity) {
        Location location = entity.getLocation();
        long[] updateEpoch = { -1L };
        trackedLocations.computeIfPresent(uuid, (key, tracked) -> {
            long epoch = verificationEpoch.get();
            if (!tracked.isLoaded() || (tracked.matches(location) && tracked.isVerified(epoch))) {
                return tracked;
            }
            updateEpoch[0] = epoch;
            return TrackedLocation.from(location, true, -1L);
        });
        if (updateEpoch[0] >= 0L) {
            Queue.queueEntitySpawnLocation(uuid, location, updateEpoch[0]);
        }
    }

    private static void scheduleCleanup() {
        long now = System.currentTimeMillis();
        long scheduled = nextCleanup.get();
        if (now < scheduled || !nextCleanup.compareAndSet(scheduled, now + CLEANUP_INTERVAL_MILLIS)) {
            return;
        }

        CoreProtect plugin = CoreProtect.getInstance();
        if (plugin == null || !plugin.isEnabled()) {
            nextCleanup.compareAndSet(now + CLEANUP_INTERVAL_MILLIS, 0L);
            return;
        }

        long next = now + CLEANUP_INTERVAL_MILLIS;
        try {
            Scheduler.runTaskAsynchronously(plugin, EntitySpawnTracking::cleanupExpiredEntries);
        }
        catch (Exception e) {
            nextCleanup.compareAndSet(next, 0L);
            ErrorReporter.report(e);
        }
    }

    private static void cleanupExpiredEntries() {
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, PendingClear> entry : pendingClear.entrySet()) {
            if (entry.getValue().expiry < now) {
                pendingClear.remove(entry.getKey(), entry.getValue());
            }
        }
        for (Map.Entry<UUID, TrackedLocation> entry : trackedLocations.entrySet()) {
            if (entry.getValue().isExpired(now)) {
                trackedLocations.remove(entry.getKey(), entry.getValue());
            }
        }
        for (Map.Entry<UUID, PendingIdentityConfirmation> entry : pendingIdentityConfirmations.entrySet()) {
            if (entry.getValue().isExpired(now)) {
                pendingIdentityConfirmations.remove(entry.getKey(), entry.getValue());
            }
        }
    }

    private static final class PendingIdentityConfirmation {

        private final Location location;
        private final long expiry;

        private PendingIdentityConfirmation(Location location, long expiry) {
            this.location = location.clone();
            this.expiry = expiry;
        }

        private boolean isExpired(long now) {
            return expiry < now;
        }
    }

    private static final class TrackedLocation {

        private final UUID worldId;
        private final int chunkX;
        private final int chunkZ;
        private final double x;
        private final double y;
        private final double z;
        private final float yaw;
        private final float pitch;
        private final boolean loaded;
        private final long verifiedEpoch;
        private final long pendingVerificationEpoch;
        private final long retainedUntil;

        private TrackedLocation(UUID worldId, int chunkX, int chunkZ, double x, double y, double z, float yaw, float pitch, boolean loaded, long verifiedEpoch, long pendingVerificationEpoch, long retainedUntil) {
            this.worldId = worldId;
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
            this.pitch = pitch;
            this.loaded = loaded;
            this.verifiedEpoch = verifiedEpoch;
            this.pendingVerificationEpoch = pendingVerificationEpoch;
            this.retainedUntil = retainedUntil;
        }

        private static TrackedLocation from(Location location, boolean loaded, long verifiedEpoch) {
            long retention = !loaded && verifiedEpoch >= 0L ? System.currentTimeMillis() + UNLOADED_CACHE_RETENTION_MILLIS : 0L;
            return new TrackedLocation(location.getWorld().getUID(), location.getBlockX() >> 4, location.getBlockZ() >> 4, location.getX(), location.getY(), location.getZ(), location.getYaw(), location.getPitch(), loaded, verifiedEpoch, -1L, retention);
        }

        private boolean isLoaded() {
            return loaded;
        }

        private boolean isVerified(long epoch) {
            return verifiedEpoch == epoch;
        }

        private boolean isPendingVerification(long epoch) {
            return pendingVerificationEpoch == epoch;
        }

        private TrackedLocation withVerifiedEpoch(long epoch) {
            long retention = !loaded && epoch >= 0L ? System.currentTimeMillis() + UNLOADED_CACHE_RETENTION_MILLIS : 0L;
            return new TrackedLocation(worldId, chunkX, chunkZ, x, y, z, yaw, pitch, loaded, epoch, -1L, retention);
        }

        private TrackedLocation withLoaded(boolean value) {
            if (loaded == value) {
                return this;
            }
            long retention = !value && pendingVerificationEpoch < 0L && verifiedEpoch >= 0L ? System.currentTimeMillis() + UNLOADED_CACHE_RETENTION_MILLIS : 0L;
            return new TrackedLocation(worldId, chunkX, chunkZ, x, y, z, yaw, pitch, value, verifiedEpoch, pendingVerificationEpoch, retention);
        }

        private TrackedLocation withPendingVerification(long epoch) {
            return new TrackedLocation(worldId, chunkX, chunkZ, x, y, z, yaw, pitch, loaded, verifiedEpoch, epoch, 0L);
        }

        private boolean isExpired(long now) {
            return !loaded && retainedUntil > 0L && retainedUntil < now;
        }

        private boolean matches(Location location) {
            return location != null && location.getWorld() != null && worldId.equals(location.getWorld().getUID()) && chunkX == (location.getBlockX() >> 4) && chunkZ == (location.getBlockZ() >> 4) && Double.compare(x, location.getX()) == 0 && Double.compare(y, location.getY()) == 0 && Double.compare(z, location.getZ()) == 0 && Float.compare(yaw, location.getYaw()) == 0 && Float.compare(pitch, location.getPitch()) == 0;
        }

        private boolean isWithinRadius(World world, Integer[] radius) {
            if (!worldId.equals(world.getUID())) {
                return false;
            }

            int blockX = Location.locToBlock(x);
            int blockY = Location.locToBlock(y);
            int blockZ = Location.locToBlock(z);
            if (blockX < radius[1] || blockX > radius[2] || blockZ < radius[5] || blockZ > radius[6]) {
                return false;
            }
            return radius[3] == null || radius[4] == null || (blockY >= radius[3] && blockY <= radius[4]);
        }

        private Location getLocation() {
            World world = Bukkit.getWorld(worldId);
            return world == null ? null : new Location(world, x, y, z, yaw, pitch);
        }
    }

    private static final class PendingClear {

        private final long expiry;

        private PendingClear(long expiry) {
            this.expiry = expiry;
        }
    }

    public static final class LoadedEntityRadius {

        private final Set<UUID> inside;
        private final Set<UUID> loadedCandidates;

        private LoadedEntityRadius(Set<UUID> inside, Set<UUID> loadedCandidates) {
            this.inside = inside;
            this.loadedCandidates = loadedCandidates;
        }

        public Set<UUID> getInside() {
            return inside;
        }

        public Set<UUID> getLoadedCandidates() {
            return loadedCandidates;
        }
    }
}
