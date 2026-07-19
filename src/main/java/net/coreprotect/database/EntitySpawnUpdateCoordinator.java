package net.coreprotect.database;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Location;

import net.coreprotect.consumer.Queue;
import net.coreprotect.database.rollback.EntitySpawnRollbackHandler;
import net.coreprotect.model.entity.EntityContainerRollbackUpdate;
import net.coreprotect.model.entity.EntitySpawnData;
import net.coreprotect.utility.EntitySpawnTracking;
import net.coreprotect.utility.ErrorReporter;

public final class EntitySpawnUpdateCoordinator {

    private final Map<UUID, LocationConfirmation> locationConfirmations = new LinkedHashMap<>();
    private final Map<UUID, Long> verificationConfirmations = new LinkedHashMap<>();
    private final Map<UUID, Long> missingRows = new LinkedHashMap<>();
    private final List<EntitySpawnData> lifecycleData = new ArrayList<>();
    private final Set<EntitySpawnData> appliedLifecycleData = new HashSet<>();
    private final Set<UUID> deferredLifecycleUuids = new HashSet<>();
    private final Map<Integer, EntitySpawnData> transitionData = new LinkedHashMap<>();
    private final Set<Integer> transitionRows = new HashSet<>();
    private final Set<Integer> permanentTransitionRows = new HashSet<>();
    private final Map<Integer, EntityContainerRollbackUpdate> combinedTransitionData = new LinkedHashMap<>();
    private final Set<Integer> combinedTransitionRows = new HashSet<>();
    private final Set<Integer> permanentCombinedTransitionRows = new HashSet<>();

    public boolean begin(EntitySpawnData data) {
        Objects.requireNonNull(data, "data");
        if (isLifecycle(data)) {
            lifecycleData.add(data);
            if (dependsOnDeferredLifecycle(data)) {
                deferLifecycle(data);
                return false;
            }
        }
        if (isTransition(data)) {
            int trackingRowId = data.getTrackingRowId();
            transitionData.put(trackingRowId, data);
            transitionRows.remove(trackingRowId);
            permanentTransitionRows.remove(trackingRowId);
        }
        return true;
    }

    public void applied(EntitySpawnData data) {
        if (isLifecycle(data)) {
            appliedLifecycleData.add(data);
        }
    }

    public void failed(EntitySpawnData data) {
        if (isLifecycle(data) || introducesTrackedUuid(data)) {
            deferLifecycle(data);
        }
    }

    public boolean permanentTransitionFailed(EntitySpawnData data, Exception failure) {
        if (failure.getSuppressed().length == 0) {
            permanentTransitionRows.add(data.getTrackingRowId());
            return true;
        }
        failed(data);
        return false;
    }

    public void beginCombined(EntityContainerRollbackUpdate update) {
        EntitySpawnData data = update.getTransition();
        int trackingRowId = data.getTrackingRowId();
        combinedTransitionData.put(trackingRowId, update);
        combinedTransitionRows.remove(trackingRowId);
        permanentCombinedTransitionRows.remove(trackingRowId);
    }

    public void combinedApplied(int trackingRowId) {
        transitionRows.remove(trackingRowId);
        combinedTransitionRows.add(trackingRowId);
    }

    public boolean permanentCombinedFailed(EntitySpawnData data, Exception failure) {
        transitionRows.remove(data.getTrackingRowId());
        if (failure.getSuppressed().length == 0) {
            permanentCombinedTransitionRows.add(data.getTrackingRowId());
            return true;
        }
        failed(data);
        return false;
    }

    public void combinedFailed(EntitySpawnData data) {
        transitionRows.remove(data.getTrackingRowId());
        failed(data);
    }

    public void transitionApplied(int trackingRowId) {
        transitionRows.add(trackingRowId);
    }

    public void verificationFound(EntitySpawnData data) {
        UUID uuid = data.getUuid();
        missingRows.remove(uuid);
        verificationConfirmations.put(uuid, data.getVerificationEpoch());
    }

    public void verificationMissing(EntitySpawnData data) {
        UUID uuid = data.getUuid();
        verificationConfirmations.remove(uuid);
        missingRows.put(uuid, data.getVerificationEpoch());
    }

    public void locationFound(EntitySpawnData data) {
        UUID uuid = data.getUuid();
        missingRows.remove(uuid);
        locationConfirmations.put(uuid, new LocationConfirmation(data));
    }

    public void locationMissing(EntitySpawnData data) {
        UUID uuid = data.getUuid();
        locationConfirmations.remove(uuid);
        missingRows.put(uuid, data.getVerificationEpoch());
    }

    public void entityFound(UUID uuid) {
        missingRows.remove(uuid);
    }

    public void entityMissing(UUID uuid) {
        missingRows.put(uuid, Long.MIN_VALUE);
    }

    public void afterCommit(boolean committed) {
        List<EntitySpawnData> lifecycleRetries = new ArrayList<>();
        List<EntityContainerRollbackUpdate> combinedRetries = new ArrayList<>();
        Set<UUID> deferredUuids = new HashSet<>();
        for (EntitySpawnData data : lifecycleData) {
            if (!committed || !appliedLifecycleData.contains(data)) {
                lifecycleRetries.add(data);
                deferLifecycle(data);
                addDeferredUuid(data, deferredUuids);
            }
        }
        for (Map.Entry<Integer, EntitySpawnData> transition : transitionData.entrySet()) {
            EntitySpawnData data = transition.getValue();
            if (!permanentTransitionRows.contains(transition.getKey()) && introducesTrackedUuid(data) && (!committed || !transitionRows.contains(transition.getKey()))) {
                deferLifecycle(data);
                deferredUuids.add(data.getUuid());
            }
        }
        for (Map.Entry<Integer, EntityContainerRollbackUpdate> transition : combinedTransitionData.entrySet()) {
            EntitySpawnData data = transition.getValue().getTransition();
            if (!permanentCombinedTransitionRows.contains(transition.getKey()) && introducesTrackedUuid(data) && (!committed || !combinedTransitionRows.contains(transition.getKey()))) {
                deferLifecycle(data);
                deferredUuids.add(data.getUuid());
            }
        }

        try {
            if (committed) {
                confirmTracking(deferredUuids);
            }
        }
        finally {
            List<EntitySpawnData> retries = new ArrayList<>();
            List<EntitySpawnData> transitionRetries = new ArrayList<>();
            for (Map.Entry<Integer, EntitySpawnData> transition : transitionData.entrySet()) {
                int trackingRowId = transition.getKey();
                EntitySpawnData data = transition.getValue();
                if (permanentTransitionRows.contains(trackingRowId)) {
                    clearTracking(data);
                    EntitySpawnRollbackHandler.releaseTrackingRow(trackingRowId);
                }
                else if (committed && transitionRows.contains(trackingRowId)) {
                    EntitySpawnRollbackHandler.releaseTrackingRow(trackingRowId);
                }
                else {
                    retries.add(data);
                    transitionRetries.add(data);
                }
            }
            for (Map.Entry<Integer, EntityContainerRollbackUpdate> transition : combinedTransitionData.entrySet()) {
                int trackingRowId = transition.getKey();
                EntityContainerRollbackUpdate update = transition.getValue();
                EntitySpawnData data = update.getTransition();
                if (permanentCombinedTransitionRows.contains(trackingRowId)) {
                    clearTracking(data);
                    EntitySpawnRollbackHandler.releaseTrackingRow(trackingRowId);
                }
                else if (committed && combinedTransitionRows.contains(trackingRowId)) {
                    EntitySpawnRollbackHandler.releaseTrackingRow(trackingRowId);
                }
                else {
                    combinedRetries.add(update);
                }
            }
            retries.addAll(lifecycleRetries);
            queueRetries(combinedRetries, retries, transitionRetries);
            clearPending();
        }
    }

    public void afterDiscard() {
        Set<Integer> trackingRows = new HashSet<>(transitionData.keySet());
        trackingRows.addAll(combinedTransitionData.keySet());
        Map<UUID, EntitySpawnData> identityVerifications = new LinkedHashMap<>();
        for (EntitySpawnData data : lifecycleData) {
            if (data.getOperation() == EntitySpawnData.Operation.VERIFY) {
                identityVerifications.put(data.getUuid(), data);
            }
        }
        try {
            for (Integer trackingRowId : trackingRows) {
                EntitySpawnRollbackHandler.releaseTrackingRow(trackingRowId);
            }
        }
        finally {
            clearPending();
            deferredLifecycleUuids.clear();
            for (Map.Entry<UUID, EntitySpawnData> verification : identityVerifications.entrySet()) {
                runTrackingConfirmation(() -> {
                    if (!EntitySpawnTracking.retryPendingDatabaseIdentityVerification(verification.getKey())) {
                        Queue.queueEntitySpawnUpdateFirst(verification.getValue());
                    }
                });
            }
        }
    }

    public Checkpoint checkpoint() {
        return new Checkpoint(this);
    }

    public void restore(Checkpoint checkpoint) {
        Objects.requireNonNull(checkpoint, "checkpoint");
        replace(locationConfirmations, checkpoint.locationConfirmations);
        replace(verificationConfirmations, checkpoint.verificationConfirmations);
        replace(missingRows, checkpoint.missingRows);
        replace(lifecycleData, checkpoint.lifecycleData);
        replace(appliedLifecycleData, checkpoint.appliedLifecycleData);
        replace(deferredLifecycleUuids, checkpoint.deferredLifecycleUuids);
        replace(transitionData, checkpoint.transitionData);
        replace(transitionRows, checkpoint.transitionRows);
        replace(permanentTransitionRows, checkpoint.permanentTransitionRows);
        replace(combinedTransitionData, checkpoint.combinedTransitionData);
        replace(combinedTransitionRows, checkpoint.combinedTransitionRows);
        replace(permanentCombinedTransitionRows, checkpoint.permanentCombinedTransitionRows);
    }

    public void clearDeferred() {
        deferredLifecycleUuids.clear();
    }

    public static boolean isLifecycle(EntitySpawnData data) {
        EntitySpawnData.Operation operation = data.getOperation();
        return operation == EntitySpawnData.Operation.VERIFY || operation == EntitySpawnData.Operation.LOCATION || operation == EntitySpawnData.Operation.REMOVED || operation == EntitySpawnData.Operation.REVIVED;
    }

    private static boolean isTransition(EntitySpawnData data) {
        EntitySpawnData.Operation operation = data.getOperation();
        return operation == EntitySpawnData.Operation.ROLLBACK || operation == EntitySpawnData.Operation.RESTORE || operation == EntitySpawnData.Operation.KILL_ROLLBACK || operation == EntitySpawnData.Operation.KILL_RESTORE || operation == EntitySpawnData.Operation.COMPOSITE_ROLLBACK || operation == EntitySpawnData.Operation.COMPOSITE_RESTORE || operation == EntitySpawnData.Operation.CLAIM_RELEASE;
    }

    private static boolean introducesTrackedUuid(EntitySpawnData data) {
        return data.getOperation() == EntitySpawnData.Operation.RESTORE || data.getOperation() == EntitySpawnData.Operation.KILL_ROLLBACK;
    }

    private boolean dependsOnDeferredLifecycle(EntitySpawnData data) {
        return deferredLifecycleUuids.contains(data.getUuid()) || (data.getOperation() == EntitySpawnData.Operation.REVIVED && data.getPreviousUuid() != null && deferredLifecycleUuids.contains(data.getPreviousUuid()));
    }

    private void deferLifecycle(EntitySpawnData data) {
        addDeferredUuid(data, deferredLifecycleUuids);
    }

    private void confirmTracking(Set<UUID> deferredUuids) {
        for (LocationConfirmation confirmation : locationConfirmations.values()) {
            if (!deferredUuids.contains(confirmation.uuid)) {
                runTrackingConfirmation(() -> EntitySpawnTracking.confirmDatabaseLocation(confirmation.uuid, confirmation.location, confirmation.epoch));
            }
        }
        for (Map.Entry<UUID, Long> confirmation : verificationConfirmations.entrySet()) {
            if (!deferredUuids.contains(confirmation.getKey())) {
                runTrackingConfirmation(() -> EntitySpawnTracking.confirmDatabaseVerification(confirmation.getKey(), confirmation.getValue()));
            }
        }
        for (Map.Entry<UUID, Long> missing : missingRows.entrySet()) {
            if (!deferredUuids.contains(missing.getKey())) {
                runTrackingConfirmation(() -> {
                    if (missing.getValue() == Long.MIN_VALUE) {
                        EntitySpawnTracking.confirmDatabaseIdentityMissing(missing.getKey());
                    }
                    else {
                        EntitySpawnTracking.confirmDatabaseIdentityMissing(missing.getKey(), missing.getValue());
                    }
                });
            }
        }
    }

    private static void queueRetries(List<EntityContainerRollbackUpdate> combinedRetries, List<EntitySpawnData> retries, List<EntitySpawnData> transitionRetries) {
        try {
            Queue.queueEntityRetriesFirst(combinedRetries, retries);
        }
        catch (Exception exception) {
            for (EntitySpawnData data : transitionRetries) {
                cleanupFailedRetry(data, exception);
            }
            for (EntityContainerRollbackUpdate update : combinedRetries) {
                cleanupFailedRetry(update.getTransition(), exception);
            }
            ErrorReporter.report(exception);
        }
    }

    private static void cleanupFailedRetry(EntitySpawnData data, Exception failure) {
        try {
            EntitySpawnRollbackHandler.releaseTrackingRow(data.getTrackingRowId());
            if (data.getUuid() != null) {
                EntitySpawnTracking.clearTracking(data.getUuid());
            }
        }
        catch (Exception cleanupException) {
            failure.addSuppressed(cleanupException);
        }
    }

    private static void clearTracking(EntitySpawnData data) {
        if (data.getUuid() != null) {
            runTrackingConfirmation(() -> EntitySpawnTracking.clearTracking(data.getUuid()));
        }
    }

    private void clearPending() {
        locationConfirmations.clear();
        verificationConfirmations.clear();
        missingRows.clear();
        lifecycleData.clear();
        appliedLifecycleData.clear();
        transitionData.clear();
        transitionRows.clear();
        permanentTransitionRows.clear();
        combinedTransitionData.clear();
        combinedTransitionRows.clear();
        permanentCombinedTransitionRows.clear();
    }

    private static void addDeferredUuid(EntitySpawnData data, Set<UUID> uuids) {
        if (data.getUuid() != null) {
            uuids.add(data.getUuid());
        }
        if (data.getOperation() == EntitySpawnData.Operation.REVIVED && data.getPreviousUuid() != null) {
            uuids.add(data.getPreviousUuid());
        }
    }

    private static void runTrackingConfirmation(Runnable confirmation) {
        try {
            confirmation.run();
        }
        catch (Exception exception) {
            ErrorReporter.report(exception);
        }
    }

    private static <K, V> void replace(Map<K, V> target, Map<K, V> source) {
        target.clear();
        target.putAll(source);
    }

    private static <E> void replace(Set<E> target, Set<E> source) {
        target.clear();
        target.addAll(source);
    }

    private static <E> void replace(List<E> target, List<E> source) {
        target.clear();
        target.addAll(source);
    }

    public static final class Checkpoint {

        private final Map<UUID, LocationConfirmation> locationConfirmations;
        private final Map<UUID, Long> verificationConfirmations;
        private final Map<UUID, Long> missingRows;
        private final List<EntitySpawnData> lifecycleData;
        private final Set<EntitySpawnData> appliedLifecycleData;
        private final Set<UUID> deferredLifecycleUuids;
        private final Map<Integer, EntitySpawnData> transitionData;
        private final Set<Integer> transitionRows;
        private final Set<Integer> permanentTransitionRows;
        private final Map<Integer, EntityContainerRollbackUpdate> combinedTransitionData;
        private final Set<Integer> combinedTransitionRows;
        private final Set<Integer> permanentCombinedTransitionRows;

        private Checkpoint(EntitySpawnUpdateCoordinator coordinator) {
            locationConfirmations = new LinkedHashMap<>(coordinator.locationConfirmations);
            verificationConfirmations = new LinkedHashMap<>(coordinator.verificationConfirmations);
            missingRows = new LinkedHashMap<>(coordinator.missingRows);
            lifecycleData = new ArrayList<>(coordinator.lifecycleData);
            appliedLifecycleData = new HashSet<>(coordinator.appliedLifecycleData);
            deferredLifecycleUuids = new HashSet<>(coordinator.deferredLifecycleUuids);
            transitionData = new LinkedHashMap<>(coordinator.transitionData);
            transitionRows = new HashSet<>(coordinator.transitionRows);
            permanentTransitionRows = new HashSet<>(coordinator.permanentTransitionRows);
            combinedTransitionData = new LinkedHashMap<>(coordinator.combinedTransitionData);
            combinedTransitionRows = new HashSet<>(coordinator.combinedTransitionRows);
            permanentCombinedTransitionRows = new HashSet<>(coordinator.permanentCombinedTransitionRows);
        }
    }

    private static final class LocationConfirmation {

        private final UUID uuid;
        private final Location location;
        private final long epoch;

        private LocationConfirmation(EntitySpawnData data) {
            uuid = data.getUuid();
            location = data.getLocation();
            epoch = data.getVerificationEpoch();
        }
    }
}
