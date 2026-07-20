package net.coreprotect.model.entity;

import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.entity.EntityType;

public final class EntitySpawnData {

    private static final int MAX_PERSISTENCE_ATTEMPTS = 3;

    public enum Operation {
        VERIFY,
        LOCATION,
        REMOVED,
        REVIVED,
        ROLLBACK,
        RESTORE,
        KILL_ROLLBACK,
        KILL_RESTORE,
        COMPOSITE_ROLLBACK,
        COMPOSITE_RESTORE,
        CLAIM_RELEASE
    }

    private final Operation operation;
    private final UUID uuid;
    private final UUID previousUuid;
    private final EntityType entityType;
    private final Location location;
    private final long blockRowId;
    private final int trackingRowId;
    private final byte[] state;
    private final int rolledBack;
    private final long verificationEpoch;
    private final int killRowId;
    private final long pairedBlockRowId;
    private final int persistenceAttempts;
    private final EntityInteractionOrigin removalOrigin;
    private final int removalTime;

    private EntitySpawnData(Operation operation, UUID uuid, UUID previousUuid, EntityType entityType, Location location, long blockRowId, int trackingRowId, byte[] state, int rolledBack, long verificationEpoch, int killRowId, long pairedBlockRowId) {
        this(operation, uuid, previousUuid, entityType, location, blockRowId, trackingRowId, state, rolledBack, verificationEpoch, killRowId, pairedBlockRowId, 0);
    }

    private EntitySpawnData(Operation operation, UUID uuid, UUID previousUuid, EntityType entityType, Location location, long blockRowId, int trackingRowId, byte[] state, int rolledBack, long verificationEpoch, int killRowId, long pairedBlockRowId, int persistenceAttempts) {
        this(operation, uuid, previousUuid, entityType, location, blockRowId, trackingRowId, state, rolledBack, verificationEpoch, killRowId, pairedBlockRowId, persistenceAttempts, null, 0);
    }

    private EntitySpawnData(Operation operation, UUID uuid, UUID previousUuid, EntityType entityType, Location location, long blockRowId, int trackingRowId, byte[] state, int rolledBack, long verificationEpoch, int killRowId, long pairedBlockRowId, int persistenceAttempts, EntityInteractionOrigin removalOrigin, int removalTime) {
        this.operation = operation;
        this.uuid = uuid;
        this.previousUuid = previousUuid;
        this.entityType = entityType;
        this.location = location == null ? null : location.clone();
        this.blockRowId = blockRowId;
        this.trackingRowId = trackingRowId;
        this.state = state == null ? null : state.clone();
        this.rolledBack = rolledBack;
        this.verificationEpoch = verificationEpoch;
        this.killRowId = killRowId;
        this.pairedBlockRowId = pairedBlockRowId;
        this.persistenceAttempts = persistenceAttempts;
        this.removalOrigin = removalOrigin;
        this.removalTime = removalTime;
    }

    public static EntitySpawnData log(UUID uuid, EntityType entityType, Location location) {
        return new EntitySpawnData(null, uuid, null, entityType, location, 0, 0, null, 0, -1L, 0, 0);
    }

    public static EntitySpawnData location(UUID uuid, Location location, long verificationEpoch) {
        return new EntitySpawnData(Operation.LOCATION, uuid, null, null, location, 0, 0, null, 0, verificationEpoch, 0, 0);
    }

    public static EntitySpawnData verify(UUID uuid, long verificationEpoch) {
        return new EntitySpawnData(Operation.VERIFY, uuid, null, null, null, 0, 0, null, 0, verificationEpoch, 0, 0);
    }

    public static EntitySpawnData removed(UUID uuid, Location location) {
        return new EntitySpawnData(Operation.REMOVED, uuid, null, null, location, 0, 0, null, 0, -1L, 0, 0);
    }

    public static EntitySpawnData removed(UUID uuid, EntityInteractionOrigin origin, Location location, int time) {
        if (uuid == null || origin == null || location == null || location.getWorld() == null) {
            throw new IllegalArgumentException("Invalid entity removal snapshot");
        }
        return new EntitySpawnData(Operation.REMOVED, uuid, null, null, location, 0, 0, null, 0, -1L, 0, 0, 0, origin, time);
    }

    public static EntitySpawnData revived(UUID previousUuid, UUID uuid, Location location) {
        return new EntitySpawnData(Operation.REVIVED, uuid, previousUuid, null, location, 0, 0, null, 0, -1L, 0, 0);
    }

    public static EntitySpawnData rollback(long blockRowId, int trackingRowId, Location location, byte[] state) {
        return new EntitySpawnData(Operation.ROLLBACK, null, null, null, location, blockRowId, trackingRowId, state, 1, -1L, 0, 0);
    }

    public static EntitySpawnData restore(long blockRowId, int trackingRowId, UUID uuid, Location location) {
        return new EntitySpawnData(Operation.RESTORE, uuid, null, null, location, blockRowId, trackingRowId, null, 0, -1L, 0, 0);
    }

    public static EntitySpawnData killRollback(long killBlockRowId, int trackingRowId, int killRowId, UUID uuid, Location location) {
        return new EntitySpawnData(Operation.KILL_ROLLBACK, uuid, null, null, location, killBlockRowId, trackingRowId, null, 1, -1L, killRowId, 0);
    }

    public static EntitySpawnData killRestore(long killBlockRowId, int trackingRowId, int killRowId, Location location) {
        return new EntitySpawnData(Operation.KILL_RESTORE, null, null, null, location, killBlockRowId, trackingRowId, null, 0, -1L, killRowId, 0);
    }

    public static EntitySpawnData compositeRestore(long spawnBlockRowId, long killBlockRowId, int trackingRowId, int killRowId) {
        return new EntitySpawnData(Operation.COMPOSITE_RESTORE, null, null, null, null, spawnBlockRowId, trackingRowId, null, 0, -1L, killRowId, killBlockRowId);
    }

    public static EntitySpawnData compositeRollback(long spawnBlockRowId, long killBlockRowId, int trackingRowId, int killRowId, Location location, byte[] state) {
        return new EntitySpawnData(Operation.COMPOSITE_ROLLBACK, null, null, null, location, spawnBlockRowId, trackingRowId, state, 1, -1L, killRowId, killBlockRowId);
    }

    public static EntitySpawnData releaseClaim(int trackingRowId) {
        return new EntitySpawnData(Operation.CLAIM_RELEASE, null, null, null, null, 0, trackingRowId, null, 0, -1L, 0, 0);
    }

    public Operation getOperation() {
        return operation;
    }

    public UUID getUuid() {
        return uuid;
    }

    public UUID getPreviousUuid() {
        return previousUuid;
    }

    public EntityType getEntityType() {
        return entityType;
    }

    public Location getLocation() {
        return location == null ? null : location.clone();
    }

    public long getBlockRowId() {
        return blockRowId;
    }

    public int getTrackingRowId() {
        return trackingRowId;
    }

    public byte[] getState() {
        return state == null ? null : state.clone();
    }

    public int getRolledBack() {
        return rolledBack;
    }

    public long getVerificationEpoch() {
        return verificationEpoch;
    }

    public int getKillRowId() {
        return killRowId;
    }

    public long getPairedBlockRowId() {
        return pairedBlockRowId;
    }

    public EntityInteractionOrigin getRemovalOrigin() {
        return removalOrigin;
    }

    public int getRemovalTime() {
        return removalTime;
    }

    public EntitySpawnData retryLog() {
        if (operation != null || persistenceAttempts >= MAX_PERSISTENCE_ATTEMPTS) {
            return null;
        }
        return new EntitySpawnData(null, uuid, null, entityType, location, 0, 0, null, 0, -1L, 0, 0, persistenceAttempts + 1);
    }
}
