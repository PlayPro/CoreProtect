package net.coreprotect.consumer.process;

import java.sql.Connection;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.Material;

import net.coreprotect.config.ConfigHandler;
import net.coreprotect.consumer.Consumer;
import net.coreprotect.consumer.Queue;
import net.coreprotect.database.ConsumerEntitySpawnUpdates;
import net.coreprotect.database.ConsumerWriteBatch;
import net.coreprotect.database.Database;
import net.coreprotect.database.logger.EntityInteractionLogger;
import net.coreprotect.database.statement.EntitySpawnStatement;
import net.coreprotect.model.entity.EntityContainerRollbackUpdate;
import net.coreprotect.model.entity.EntityContainerTransaction;
import net.coreprotect.model.entity.EntityInteraction;
import net.coreprotect.model.entity.EntitySpawnData;
import net.coreprotect.model.entity.EntitySpawnIdentity;
import net.coreprotect.model.rollback.RollbackUpdateTargets;
import net.coreprotect.utility.ErrorReporter;
import net.coreprotect.utility.EntitySpawnTracking;

public class Process {

    public static final int BLOCK_BREAK = 0;
    public static final int BLOCK_PLACE = 1;
    public static final int SIGN_TEXT = 2;
    public static final int CONTAINER_BREAK = 3;
    public static final int PLAYER_INTERACTION = 4;
    public static final int CONTAINER_TRANSACTION = 5;
    public static final int STRUCTURE_GROWTH = 6;
    public static final int ROLLBACK_UPDATE = 7;
    public static final int CONTAINER_ROLLBACK_UPDATE = 8;
    public static final int WORLD_INSERT = 9;
    public static final int SIGN_UPDATE = 10;
    public static final int SKULL_UPDATE = 11;
    public static final int PLAYER_CHAT = 12;
    public static final int PLAYER_COMMAND = 13;
    public static final int PLAYER_LOGIN = 14;
    public static final int PLAYER_LOGOUT = 15;
    public static final int ENTITY_KILL = 16;
    public static final int ENTITY_SPAWN = 17;
    public static final int NATURAL_BLOCK_BREAK = 20;
    public static final int MATERIAL_INSERT = 21;
    public static final int ART_INSERT = 22;
    public static final int ENTITY_INSERT = 23;
    public static final int PLAYER_KILL = 24;
    public static final int BLOCKDATA_INSERT = 25;
    public static final int ITEM_TRANSACTION = 26;
    public static final int INVENTORY_ROLLBACK_UPDATE = 27;
    public static final int INVENTORY_CONTAINER_ROLLBACK_UPDATE = 28;
    public static final int BLOCK_INVENTORY_ROLLBACK_UPDATE = 29;
    public static final int ENTITY_SPAWN_LOG = 30;
    public static final int ENTITY_SPAWN_UPDATE = 31;
    public static final int ENTITY_CONTAINER_TRANSACTION = 32;
    public static final int ENTITY_CONTAINER_ROLLBACK_UPDATE = 33;
    public static final int ENTITY_CONTAINER_TRANSITION_UPDATE = 34;
    public static final int ENTITY_INTERACTION = 35;

    public static int lastLockUpdate = 0;
    private static volatile int currentConsumerSize = 0;

    private enum TransactionOutcome {
        COMMITTED,
        RETRY,
        DISCARDED
    }

    public static int getCurrentConsumerSize() {
        return currentConsumerSize;
    }

    public static boolean isRollbackPublication(int action, Object object) {
        if (action == ROLLBACK_UPDATE || action == CONTAINER_ROLLBACK_UPDATE || action == INVENTORY_ROLLBACK_UPDATE || action == INVENTORY_CONTAINER_ROLLBACK_UPDATE || action == BLOCK_INVENTORY_ROLLBACK_UPDATE || action == ENTITY_CONTAINER_ROLLBACK_UPDATE || action == ENTITY_CONTAINER_TRANSITION_UPDATE) {
            return true;
        }
        if (action != ENTITY_SPAWN_UPDATE || !(object instanceof EntitySpawnData)) {
            return false;
        }

        EntitySpawnData.Operation operation = ((EntitySpawnData) object).getOperation();
        return operation == EntitySpawnData.Operation.ROLLBACK || operation == EntitySpawnData.Operation.RESTORE || operation == EntitySpawnData.Operation.KILL_ROLLBACK || operation == EntitySpawnData.Operation.KILL_RESTORE || operation == EntitySpawnData.Operation.COMPOSITE_ROLLBACK || operation == EntitySpawnData.Operation.COMPOSITE_RESTORE || operation == EntitySpawnData.Operation.CLAIM_RELEASE;
    }

    protected static void updateLockTable(ConsumerWriteBatch batch, int locked) {
        try {
            int unixTimestamp = (int) (System.currentTimeMillis() / 1000L);
            int timeSinceLastUpdate = unixTimestamp - lastLockUpdate;
            if (timeSinceLastUpdate >= 15 || locked == 0) {
                batch.updateDatabaseLock(locked, unixTimestamp);
                lastLockUpdate = unixTimestamp;
            }
        }
        catch (Exception e) {
            Database.handleWriteFailure(e);
        }
    }

    protected static void processConsumer(int processId, boolean lastRun) {
        List<PendingEntitySpawnLog> pendingEntitySpawnLogs = new ArrayList<>();
        Map<UUID, EntitySpawnIdentity> entitySpawnIdentities = new LinkedHashMap<>();
        Map<UUID, Location> pendingEntityIdentityConfirmations = new LinkedHashMap<>();
        Set<UUID> invalidatedEntityIdentityConfirmations = new HashSet<>();
        Set<UUID> promotedEntityIdentities = new HashSet<>();
        List<PendingEntityContainerTransaction> pendingEntityContainerTransactions = new ArrayList<>();
        List<PendingEntityInteraction> pendingEntityInteractions = new ArrayList<>();
        List<EntityContainerRollbackRetry> pendingEntityContainerRollbacks = new ArrayList<>();
        ConsumerEntitySpawnUpdates entitySpawnUpdates = null;
        ArrayList<Object[]> consumerData = null;
        Map<Integer, String[]> users = null;
        Map<Integer, Object> consumerObject = null;
        ConsumerWriteBatch writeBatch = null;
        Connection connection = null;
        boolean processingStarted = false;
        boolean consumerDataCleared = false;
        boolean preflightCommitted = false;
        int processedThrough = 0;
        int attemptedThrough = 0;
        try {
            connection = Database.getConnection(false, 500);
            if (connection == null) {
                deferUnavailableColumnarDatabase(processId);
                return;
            }

            Statement statement = connection.createStatement();
            ConsumerWriteBatch batch = Database.openConsumerWriteBatch(connection);
            writeBatch = batch;
            Database.performCheckpoint(statement, ConfigHandler.databaseType);

            Consumer.isPaused = true;
            consumerData = Consumer.consumer.get(processId);
            users = Consumer.consumerUsers.get(processId);
            consumerObject = Consumer.consumerObjects.get(processId);
            int consumerDataSize = consumerData.size();
            currentConsumerSize = consumerDataSize;

            if (!beginConsumerTransaction(writeBatch)) {
                deferConsumerRetry();
                return;
            }

            if (currentConsumerSize == 0) { // No data, skip processing
                updateLockTable(writeBatch, (lastRun ? 0 : 1));
                if (!commit(writeBatch)) {
                    deferConsumerRetry();
                    return;
                }
                statement.close();
                Consumer.consumer_id.put(processId, new Integer[] { 0, 0 });
                Consumer.isPaused = false;
                return;
            }

            boolean hasEntitySpawnUpdates = false;
            boolean hasEntityContainerTransactions = false;
            boolean hasEntityInteractions = false;
            List<EntitySpawnData> entitySpawnUpdateData = new ArrayList<>();
            Set<UUID> entityIdentityUuids = new HashSet<>();
            Set<Integer> entityIdentityRowIds = new HashSet<>();
            for (int index = 0; index < consumerDataSize; index++) {
                Object[] data = consumerData.get(index);
                if (data == null) {
                    continue;
                }
                int action = (int) data[1];
                hasEntitySpawnUpdates |= action == Process.ENTITY_SPAWN_UPDATE || action == Process.ENTITY_CONTAINER_TRANSITION_UPDATE;
                hasEntityContainerTransactions |= action == Process.ENTITY_CONTAINER_TRANSACTION;
                hasEntityInteractions |= action == Process.ENTITY_INTERACTION;

                if (action == Process.ENTITY_CONTAINER_TRANSACTION) {
                    Object object = consumerObject.get((int) data[0]);
                    if (object instanceof EntityContainerTransaction) {
                        entityIdentityUuids.add(((EntityContainerTransaction) object).getEntityUuid());
                    }
                }
                else if (action == Process.ENTITY_INTERACTION) {
                    Object object = consumerObject.get((int) data[0]);
                    if (object instanceof EntityInteraction) {
                        entityIdentityUuids.add(((EntityInteraction) object).getEntityUuid());
                    }
                }
                else if (action == Process.ENTITY_SPAWN_UPDATE || action == Process.ENTITY_CONTAINER_TRANSITION_UPDATE) {
                    Object object = consumerObject.get((int) data[0]);
                    EntitySpawnData spawnData = getEntitySpawnUpdate(object);
                    if (spawnData == null) {
                        continue;
                    }
                    entitySpawnUpdateData.add(spawnData);
                    if (spawnData.getPreviousUuid() != null) {
                        entityIdentityUuids.add(spawnData.getPreviousUuid());
                    }
                    if (spawnData.getTrackingRowId() > 0 && spawnData.getUuid() != null) {
                        entityIdentityRowIds.add(spawnData.getTrackingRowId());
                    }
                }
            }

            // Scan through usernames, ensure everything is loaded in memory.
            for (Entry<Integer, String[]> entry : users.entrySet()) {
                String[] data = entry.getValue();
                if (data != null) {
                    String user = data[0];
                    String uuid = data[1];
                    if (user != null && ConfigHandler.playerIdCache.get(user.toLowerCase(Locale.ROOT)) == null) {
                        writeBatch.resolveUserId(user, uuid);
                    }
                }
            }
            updateLockTable(writeBatch, (lastRun ? 0 : 1));
            if (!commit(writeBatch)) {
                invalidateUserCaches(users);
                failConsumerBatch(processId, consumerData, users, consumerObject, 0, 0);
                return;
            }
            preflightCommitted = true;

            if (hasEntityContainerTransactions || hasEntityInteractions) {
                entitySpawnIdentities.putAll(EntitySpawnStatement.loadIdentities(connection, entityIdentityUuids));
                Map<Integer, EntitySpawnIdentity> identitiesByRowId = EntitySpawnStatement.loadIdentitiesByRowIds(connection, entityIdentityRowIds);
                bindPendingEntitySpawnIdentities(consumerData, consumerObject, entitySpawnIdentities, identitiesByRowId);
            }

            // Scan through consumer data
            if (!beginConsumerTransaction(writeBatch)) {
                deferConsumerRetry();
                return;
            }
            entitySpawnUpdates = hasEntitySpawnUpdates ? writeBatch.entitySpawnUpdates() : null;
            if (entitySpawnUpdates != null) {
                entitySpawnUpdates.prefetch(entitySpawnUpdateData);
            }
            processingStarted = true;
            for (int i = 0; i < consumerDataSize; i++) {
                attemptedThrough = i + 1;
                Object[] data = consumerData.get(i);
                if (data != null) {
                    int id = (int) data[0];
                    int action = (int) data[1];
                    Material blockType = (Material) data[2];
                    int blockData = (int) data[3];
                    Material replaceType = (Material) data[4];
                    int replaceData = (int) data[5];
                    int forceData = (int) data[6];
                    boolean isolatedTransaction = requiresIsolatedDuckDBTransaction(action);

                    if (isolatedTransaction && i > processedThrough) {
                        boolean committed = commit(writeBatch);
                        if (committed) {
                            processedThrough = i;
                        }
                        completeTransactionState(entitySpawnUpdates, pendingEntityContainerTransactions, pendingEntityContainerRollbacks, pendingEntityInteractions, pendingEntityIdentityConfirmations, invalidatedEntityIdentityConfirmations, promotedEntityIdentities, entitySpawnIdentities, pendingEntitySpawnLogs, transactionOutcome(committed));
                        if (!committed) {
                            failConsumerBatch(processId, consumerData, users, consumerObject, processedThrough, i);
                            return;
                        }
                        if (!beginConsumerTransaction(writeBatch)) {
                            retryConsumerBatch(processId, consumerData, users, consumerObject, processedThrough);
                            return;
                        }
                    }

                    if (users.get(id) != null && consumerObject.get(id) != null) {
                        String user = users.get(id)[0];
                        Object object = consumerObject.get(id);

                        try {
                            switch (action) {
                                case Process.BLOCK_BREAK:
                                    BlockBreakProcess.process(writeBatch, writeBatch, i, processId, id, blockType, blockData, replaceType, forceData, user, object, (String) data[7]);
                                    break;
                                case Process.BLOCK_PLACE:
                                    BlockPlaceProcess.process(writeBatch, writeBatch, i, blockType, blockData, replaceType, replaceData, forceData, user, object, (String) data[7], (String) data[8]);
                                    break;
                                case Process.SIGN_TEXT:
                                    SignTextProcess.process(writeBatch, i, processId, id, forceData, user, object, replaceData, blockData);
                                    break;
                                case Process.CONTAINER_BREAK:
                                    ContainerBreakProcess.process(writeBatch, i, processId, id, blockType, user, object);
                                    break;
                                case Process.PLAYER_INTERACTION:
                                    PlayerInteractionProcess.process(writeBatch, i, user, object, blockType);
                                    break;
                                case Process.CONTAINER_TRANSACTION:
                                    ContainerTransactionProcess.process(writeBatch, writeBatch, i, processId, id, blockType, forceData, user, object);
                                    break;
                                case Process.ENTITY_CONTAINER_TRANSACTION:
                                    EntityContainerTransaction transaction = (EntityContainerTransaction) object;
                                    EntitySpawnIdentity identity = entitySpawnIdentities.get(transaction.getEntityUuid());
                                    boolean processedEntityContainerTransaction;
                                    try {
                                        processedEntityContainerTransaction = ContainerTransactionProcess.processEntity(writeBatch, i, user, transaction, identity);
                                    }
                                    catch (Exception e) {
                                        pendingEntityContainerTransactions.add(new PendingEntityContainerTransaction(user, transaction, true));
                                        throw e;
                                    }
                                    if (!processedEntityContainerTransaction) {
                                        pendingEntityContainerTransactions.add(new PendingEntityContainerTransaction(user, transaction, true));
                                    }
                                    else if (ConfigHandler.databaseType.isColumnar()) {
                                        pendingEntityContainerTransactions.add(new PendingEntityContainerTransaction(user, transaction, false));
                                    }
                                    break;
                                case Process.ENTITY_INTERACTION:
                                    EntityInteraction interaction = (EntityInteraction) object;
                                    EntityInteractionLogger.LogContext logContext = EntityInteractionLogger.prepare(user, interaction);
                                    if (logContext == null) {
                                        cancelEntityInteractionPromotion(interaction);
                                        break;
                                    }

                                    EntitySpawnIdentity existingIdentity = entitySpawnIdentities.get(interaction.getEntityUuid());
                                    EntitySpawnIdentity[] loggedIdentity = { existingIdentity };
                                    boolean[] identityActive = new boolean[1];
                                    try {
                                        writeBatch.executeAtomically("entity_interaction_log", () -> {
                                            if (loggedIdentity[0] == null) {
                                                loggedIdentity[0] = EntitySpawnStatement.insertIdentity(batch, interaction.getTime(), interaction.getEntityUuid(), interaction.getOrigin(), interaction.getCurrentLocation());
                                            }
                                            identityActive[0] = EntityInteractionLogger.log(batch, loggedIdentity[0], interaction, logContext);
                                        });
                                    }
                                    catch (Exception e) {
                                        pendingEntityInteractions.add(new PendingEntityInteraction(user, interaction, false, true));
                                        throw e;
                                    }

                                    entitySpawnIdentities.put(interaction.getEntityUuid(), loggedIdentity[0]);
                                    pendingEntityInteractions.add(new PendingEntityInteraction(user, interaction, identityActive[0], false));
                                    if (existingIdentity == null) {
                                        promotedEntityIdentities.add(interaction.getEntityUuid());
                                    }
                                    if (identityActive[0]) {
                                        pendingEntityIdentityConfirmations.put(interaction.getEntityUuid(), interaction.getCurrentLocation());
                                        if (entitySpawnUpdates != null) {
                                            entitySpawnUpdates.identityFound(interaction.getEntityUuid());
                                        }
                                    }
                                    break;
                                case Process.ITEM_TRANSACTION:
                                    ItemTransactionProcess.process(writeBatch, i, processId, id, forceData, replaceData, blockData, user, object);
                                    break;
                                case Process.STRUCTURE_GROWTH:
                                    StructureGrowthProcess.process(statement, writeBatch, i, processId, id, user, object, forceData);
                                    break;
                                case Process.ROLLBACK_UPDATE:
                                    RollbackUpdateProcess.process(writeBatch, processId, id, forceData, RollbackUpdateTargets.BLOCK);
                                    break;
                                case Process.CONTAINER_ROLLBACK_UPDATE:
                                    RollbackUpdateProcess.process(writeBatch, processId, id, forceData, RollbackUpdateTargets.CONTAINER);
                                    break;
                                case Process.INVENTORY_ROLLBACK_UPDATE:
                                    RollbackUpdateProcess.process(writeBatch, processId, id, forceData, RollbackUpdateTargets.INVENTORY_ITEM);
                                    break;
                                case Process.INVENTORY_CONTAINER_ROLLBACK_UPDATE:
                                    RollbackUpdateProcess.process(writeBatch, processId, id, forceData, RollbackUpdateTargets.INVENTORY_CONTAINER);
                                    break;
                                case Process.BLOCK_INVENTORY_ROLLBACK_UPDATE:
                                    RollbackUpdateProcess.process(writeBatch, processId, id, forceData, RollbackUpdateTargets.BLOCK_INVENTORY);
                                    break;
                                case Process.ENTITY_CONTAINER_ROLLBACK_UPDATE:
                                    List<Object[]> entityContainerRows = Consumer.consumerObjectArrayList.get(processId).get(id);
                                    if (entityContainerRows != null) {
                                        try {
                                            writeBatch.executeAtomically("entity_container_rollback", () -> RollbackUpdateProcess.processChecked(batch, entityContainerRows, forceData, RollbackUpdateTargets.ENTITY_CONTAINER, blockData == 1));
                                            pendingEntityContainerRollbacks.add(new EntityContainerRollbackRetry(user, object instanceof Location ? (Location) object : null, entityContainerRows, forceData, blockData == 1, false));
                                        }
                                        catch (Exception e) {
                                            pendingEntityContainerRollbacks.add(new EntityContainerRollbackRetry(user, object instanceof Location ? (Location) object : null, entityContainerRows, forceData, blockData == 1, true));
                                            throw e;
                                        }
                                    }
                                    break;
                                case Process.ENTITY_CONTAINER_TRANSITION_UPDATE:
                                    EntityContainerRollbackUpdate containerUpdate = (EntityContainerRollbackUpdate) object;
                                    entitySpawnUpdates.applyCombined(containerUpdate, () -> RollbackUpdateProcess.processChecked(batch, containerUpdate.getRows(), containerUpdate.getRollbackType(), RollbackUpdateTargets.ENTITY_CONTAINER, containerUpdate.isInventoryRollback()));
                                    break;
                                case Process.WORLD_INSERT:
                                    WorldInsertProcess.process(writeBatch, i, statement, object, forceData);
                                    break;
                                case Process.SIGN_UPDATE:
                                    SignUpdateProcess.process(statement, object, user, blockData, forceData);
                                    break;
                                case Process.SKULL_UPDATE:
                                    SkullUpdateProcess.process(statement, object, forceData);
                                    break;
                                case Process.PLAYER_CHAT:
                                    PlayerChatProcess.process(writeBatch, i, processId, id, object, user);
                                    break;
                                case Process.PLAYER_COMMAND:
                                    PlayerCommandProcess.process(writeBatch, i, processId, id, object, user);
                                    break;
                                case Process.PLAYER_LOGIN:
                                    PlayerLoginProcess.process(writeBatch, i, processId, id, object, blockData, replaceData, forceData, user);
                                    break;
                                case Process.PLAYER_LOGOUT:
                                    PlayerLogoutProcess.process(writeBatch, i, object, forceData, user);
                                    break;
                                case Process.ENTITY_KILL:
                                    EntityKillProcess.process(writeBatch, writeBatch, writeBatch, i, processId, id, object, user);
                                    break;
                                case Process.ENTITY_SPAWN:
                                    EntitySpawnProcess.process(statement, object, forceData);
                                    break;
                                case Process.NATURAL_BLOCK_BREAK:
                                    NaturalBlockBreakProcess.process(statement, writeBatch, i, processId, id, user, object, blockType, blockData, (String) data[7]);
                                    break;
                                case Process.MATERIAL_INSERT:
                                    MaterialInsertProcess.process(writeBatch, statement, i, object, forceData);
                                    break;
                                case Process.ART_INSERT:
                                    ArtInsertProcess.process(writeBatch, statement, i, object, forceData);
                                    break;
                                case Process.ENTITY_INSERT:
                                    EntityInsertProcess.process(writeBatch, statement, i, object, forceData);
                                    break;
                                case Process.PLAYER_KILL:
                                    PlayerKillProcess.process(writeBatch, i, id, object, user);
                                    break;
                                case Process.BLOCKDATA_INSERT:
                                    BlockDataInsertProcess.process(writeBatch, statement, i, object, forceData);
                                    break;
                                case Process.ENTITY_SPAWN_LOG:
                                    if (!(object instanceof EntitySpawnData)) {
                                        break;
                                    }
                                    EntitySpawnData spawnData = (EntitySpawnData) object;
                                    EntitySpawnIdentity spawnIdentity;
                                    try {
                                        spawnIdentity = EntitySpawnLogProcess.process(writeBatch, spawnData, user);
                                    }
                                    catch (Exception e) {
                                        if (ConfigHandler.databaseType.isColumnar()) {
                                            pendingEntitySpawnLogs.add(new PendingEntitySpawnLog(user, spawnData, false, true));
                                        }
                                        else {
                                            EntitySpawnTracking.clearTracking(spawnData.getUuid());
                                        }
                                        throw e;
                                    }
                                    if (spawnIdentity != null) {
                                        entitySpawnIdentities.put(spawnIdentity.getUuid(), spawnIdentity);
                                        pendingEntitySpawnLogs.add(new PendingEntitySpawnLog(user, spawnData, true, false));
                                        if (entitySpawnUpdates != null) {
                                            entitySpawnUpdates.identityFound(spawnIdentity.getUuid());
                                        }
                                    }
                                    break;
                                case Process.ENTITY_SPAWN_UPDATE:
                                    if (object instanceof EntitySpawnData) {
                                        EntitySpawnData update = (EntitySpawnData) object;
                                        invalidateEntityInteractionIdentityConfirmation(update, pendingEntityIdentityConfirmations, invalidatedEntityIdentityConfirmations);
                                        EntitySpawnIdentity createdIdentity = entitySpawnUpdates.apply(update);
                                        if (createdIdentity != null) {
                                            entitySpawnIdentities.put(createdIdentity.getUuid(), createdIdentity);
                                            promotedEntityIdentities.add(createdIdentity.getUuid());
                                        }
                                    }
                                    break;
                            }

                            // If interrupt requested, commit data, sleep, and resume processing
                            boolean interrupted = Consumer.interrupt;
                            if ((interrupted || writeBatch.shouldCommit()) && !isolatedTransaction) {
                                boolean committed = commit(writeBatch);
                                if (committed) {
                                    processedThrough = i + 1;
                                }
                                completeTransactionState(entitySpawnUpdates, pendingEntityContainerTransactions, pendingEntityContainerRollbacks, pendingEntityInteractions, pendingEntityIdentityConfirmations, invalidatedEntityIdentityConfirmations, promotedEntityIdentities, entitySpawnIdentities, pendingEntitySpawnLogs, transactionOutcome(committed));
                                if (!committed) {
                                    failConsumerBatch(processId, consumerData, users, consumerObject, processedThrough, i + 1);
                                    return;
                                }
                                if (interrupted) {
                                    try {
                                        Thread.sleep(500);
                                    }
                                    catch (InterruptedException exception) {
                                        Thread.currentThread().interrupt();
                                        retryConsumerBatch(processId, consumerData, users, consumerObject, processedThrough);
                                        return;
                                    }
                                }
                                if (!beginConsumerTransaction(writeBatch)) {
                                    retryConsumerBatch(processId, consumerData, users, consumerObject, processedThrough);
                                    return;
                                }
                            }
                        }
                        catch (Exception e) {
                            if (ConfigHandler.databaseType.isColumnar()) {
                                throw e;
                            }
                            ErrorReporter.report(e);
                        }

                        // If database connection goes missing, abort and roll back the transaction
                        if (statement.isClosed()) {
                            throw new IllegalStateException("Database connection closed during consumer processing");
                        }
                    }

                    if (isolatedTransaction) {
                        boolean committed = commit(writeBatch);
                        if (committed) {
                            processedThrough = i + 1;
                        }
                        completeTransactionState(entitySpawnUpdates, pendingEntityContainerTransactions, pendingEntityContainerRollbacks, pendingEntityInteractions, pendingEntityIdentityConfirmations, invalidatedEntityIdentityConfirmations, promotedEntityIdentities, entitySpawnIdentities, pendingEntitySpawnLogs, transactionOutcome(committed));
                        if (!committed) {
                            failConsumerBatch(processId, consumerData, users, consumerObject, processedThrough, i + 1);
                            return;
                        }
                        if (!beginConsumerTransaction(writeBatch)) {
                            retryConsumerBatch(processId, consumerData, users, consumerObject, processedThrough);
                            return;
                        }
                    }
                }
                currentConsumerSize--;
            }

            // commit data to database
            boolean committed = commit(writeBatch);
            if (committed) {
                processedThrough = consumerData.size();
            }
            completeTransactionState(entitySpawnUpdates, pendingEntityContainerTransactions, pendingEntityContainerRollbacks, pendingEntityInteractions, pendingEntityIdentityConfirmations, invalidatedEntityIdentityConfirmations, promotedEntityIdentities, entitySpawnIdentities, pendingEntitySpawnLogs, transactionOutcome(committed));
            if (!committed) {
                failConsumerBatch(processId, consumerData, users, consumerObject, processedThrough, consumerData.size());
                return;
            }
            clearConsumerData(processId, consumerData, users, consumerObject);
            consumerDataCleared = true;

            statement.close();
        }
        catch (Exception e) {
            if (writeBatch != null && Consumer.transacting) {
                writeBatch.rollback();
            }
            if (!preflightCommitted && users != null) {
                invalidateUserCaches(users);
            }
            if (processingStarted && !consumerDataCleared && consumerData != null && users != null && consumerObject != null) {
                try {
                    completeTransactionState(entitySpawnUpdates, pendingEntityContainerTransactions, pendingEntityContainerRollbacks, pendingEntityInteractions, pendingEntityIdentityConfirmations, invalidatedEntityIdentityConfirmations, promotedEntityIdentities, entitySpawnIdentities, pendingEntitySpawnLogs, TransactionOutcome.RETRY);
                    discardProcessedConsumerData(processId, consumerData, users, consumerObject, processedThrough);
                    discardProcessedConsumerData(processId, consumerData, users, consumerObject, Math.max(0, attemptedThrough - processedThrough));
                    consumerDataCleared = consumerData.isEmpty();
                }
                catch (Exception cleanupException) {
                    e.addSuppressed(cleanupException);
                }
            }
            ErrorReporter.report(e);
        }
        finally {
            if (writeBatch != null) {
                try {
                    writeBatch.close();
                }
                catch (Exception e) {
                    ErrorReporter.report(e);
                }
            }
            if (connection != null) {
                try {
                    connection.close();
                }
                catch (Exception e) {
                    ErrorReporter.report(e);
                }
            }
        }
        TransactionOutcome cleanupOutcome = TransactionOutcome.RETRY;
        completeEntityInteractions(pendingEntityInteractions, pendingEntityIdentityConfirmations, invalidatedEntityIdentityConfirmations, promotedEntityIdentities, entitySpawnIdentities, cleanupOutcome);
        completeEntitySpawnLogs(pendingEntitySpawnLogs, cleanupOutcome);

        if (consumerDataCleared) {
            currentConsumerSize = 0;
            Consumer.consumer_id.put(processId, new Integer[] { 0, 0 });
            Consumer.isPaused = false;
        }
        else if (!Consumer.isPersistenceHalted()) {
            deferConsumerRetry();
        }
    }

    private static void bindPendingEntitySpawnIdentities(ArrayList<Object[]> consumerData, Map<Integer, Object> consumerObjects, Map<UUID, EntitySpawnIdentity> identities, Map<Integer, EntitySpawnIdentity> identitiesByRowId) {
        for (Object[] data : consumerData) {
            if (data == null || ((int) data[1] != Process.ENTITY_SPAWN_UPDATE && (int) data[1] != Process.ENTITY_CONTAINER_TRANSITION_UPDATE)) {
                continue;
            }

            Object object = consumerObjects.get((int) data[0]);
            EntitySpawnData spawnData = getEntitySpawnUpdate(object);
            if (spawnData == null) {
                continue;
            }
            UUID uuid = spawnData.getUuid();
            if (uuid == null) {
                continue;
            }

            EntitySpawnIdentity identity = null;
            if (spawnData.getPreviousUuid() != null) {
                identity = identities.get(spawnData.getPreviousUuid());
            }
            if (identity == null && spawnData.getTrackingRowId() > 0) {
                identity = identitiesByRowId.get(spawnData.getTrackingRowId());
            }
            if (identity != null) {
                identities.put(uuid, identity);
            }
        }
    }

    private static EntitySpawnData getEntitySpawnUpdate(Object object) {
        if (object instanceof EntitySpawnData) {
            return (EntitySpawnData) object;
        }
        if (object instanceof EntityContainerRollbackUpdate) {
            return ((EntityContainerRollbackUpdate) object).getTransition();
        }
        return null;
    }

    private static void retryEntityContainerTransaction(String user, EntityContainerTransaction transaction) {
        EntityContainerTransaction retry = transaction.retry();
        if (retry != null) {
            Queue.queueEntityContainerTransaction(user, retry);
        }
        else { // only print exception on development branch
            ErrorReporter.report(new IllegalStateException("Dropped entity container transaction without tracking row: " + transaction.getEntityUuid()), ConfigHandler.EDITION_BRANCH.contains("-dev"));
        }
    }

    private static void retryEntityInteraction(String user, EntityInteraction interaction) {
        EntityInteraction retry = interaction.retry();
        if (retry != null) {
            Queue.queueEntityInteraction(user, retry);
        }
        else {
            cancelEntityInteractionPromotion(interaction);
            ErrorReporter.report(new IllegalStateException("Dropped entity interaction after repeated persistence failures: " + interaction.getEntityUuid()), ConfigHandler.EDITION_BRANCH.contains("-dev"));
        }
    }

    private static void cancelEntityInteractionPromotion(EntityInteraction interaction) {
        if (interaction.hasIdentityPromotion()) {
            EntitySpawnTracking.cancelDatabaseIdentityPromotion(interaction.getEntityUuid(), interaction.getCurrentLocation());
        }
    }

    static void invalidateEntityInteractionIdentityConfirmation(EntitySpawnData data, Map<UUID, Location> identityConfirmations, Set<UUID> invalidatedIdentities) {
        if (data.getOperation() != EntitySpawnData.Operation.REMOVED || data.getUuid() == null) {
            return;
        }
        identityConfirmations.remove(data.getUuid());
        invalidatedIdentities.add(data.getUuid());
    }

    private static void completeEntityInteractions(List<PendingEntityInteraction> interactions, Map<UUID, Location> identityConfirmations, Set<UUID> invalidatedIdentities, Set<UUID> promotedIdentities, Map<UUID, EntitySpawnIdentity> identities, TransactionOutcome outcome) {
        if (outcome == TransactionOutcome.COMMITTED) {
            for (Map.Entry<UUID, Location> entry : identityConfirmations.entrySet()) {
                if (invalidatedIdentities.contains(entry.getKey())) {
                    continue;
                }
                try {
                    EntitySpawnTracking.confirmDatabaseIdentity(entry.getKey(), entry.getValue());
                }
                catch (Exception e) {
                    ErrorReporter.report(e);
                }
            }
            Set<UUID> clearedIdentities = new HashSet<>(invalidatedIdentities);
            for (PendingEntityInteraction pending : interactions) {
                if (!pending.retryRequired && !pending.identityActive) {
                    clearedIdentities.add(pending.interaction.getEntityUuid());
                }
            }
            for (UUID uuid : clearedIdentities) {
                try {
                    EntitySpawnTracking.clearTracking(uuid);
                }
                catch (Exception e) {
                    ErrorReporter.report(e);
                }
            }
        }
        else if (outcome == TransactionOutcome.DISCARDED) {
            Set<UUID> verifiedIdentities = new HashSet<>();
            for (PendingEntityInteraction pending : interactions) {
                if (pending.retryRequired) {
                    cancelEntityInteractionPromotion(pending.interaction);
                    continue;
                }
                UUID uuid = pending.interaction.getEntityUuid();
                if (!verifiedIdentities.add(uuid)) {
                    continue;
                }
                try {
                    EntitySpawnTracking.verifyPendingDatabaseIdentity(uuid, pending.interaction.getCurrentLocation());
                }
                catch (Exception e) {
                    ErrorReporter.report(e);
                }
            }
        }
        if (outcome != TransactionOutcome.DISCARDED) {
            for (PendingEntityInteraction pending : interactions) {
                if (outcome == TransactionOutcome.COMMITTED && !pending.retryRequired) {
                    continue;
                }
                try {
                    retryEntityInteraction(pending.user, pending.interaction);
                }
                catch (Exception e) {
                    ErrorReporter.report(e);
                }
            }
        }
        if (outcome != TransactionOutcome.COMMITTED) {
            for (UUID uuid : promotedIdentities) {
                identities.remove(uuid);
            }
        }
        interactions.clear();
        identityConfirmations.clear();
        invalidatedIdentities.clear();
        promotedIdentities.clear();
    }

    private static void retryEntityContainerRollbacks(List<EntityContainerRollbackRetry> updates, TransactionOutcome outcome) {
        if (outcome != TransactionOutcome.DISCARDED) {
            for (EntityContainerRollbackRetry update : updates) {
                if (outcome == TransactionOutcome.COMMITTED && !update.retryRequired) {
                    continue;
                }
                try {
                    Queue.queueEntityContainerRollbackUpdate(update.user, update.location, update.rows, update.rollbackType, update.inventoryRollback);
                }
                catch (Exception e) {
                    ErrorReporter.report(e);
                }
            }
        }
        updates.clear();
    }

    private static boolean beginConsumerTransaction(ConsumerWriteBatch batch) {
        try {
            batch.begin();
            return true;
        }
        catch (Exception e) {
            ErrorReporter.report(e);
            return false;
        }
    }

    private static void deferConsumerRetry() {
        currentConsumerSize = 0;
        Consumer.isPaused = false;
    }

    static void retryConsumerBatch(int processId, ArrayList<Object[]> consumerData, Map<Integer, String[]> users, Map<Integer, Object> consumerObject, int processedThrough) {
        discardProcessedConsumerData(processId, consumerData, users, consumerObject, processedThrough);
        deferConsumerRetry();
    }

    private static void deferUnavailableColumnarDatabase(int processId) {
        if (!ConfigHandler.databaseType.isColumnar()) {
            return;
        }
        ArrayList<Object[]> consumerData = Consumer.consumer.get(processId);
        if (consumerData != null) {
            currentConsumerSize = consumerData.size();
        }
        Consumer.isPaused = false;
    }

    static void failConsumerBatch(int processId, ArrayList<Object[]> consumerData, Map<Integer, String[]> users, Map<Integer, Object> consumerObject, int processedThrough, int discardThrough) {
        discardProcessedConsumerData(processId, consumerData, users, consumerObject, processedThrough);
        discardProcessedConsumerData(processId, consumerData, users, consumerObject, Math.max(0, discardThrough - processedThrough));
        deferConsumerRetry();
    }

    private static void invalidateUserCaches(Map<Integer, String[]> users) {
        for (String[] data : users.values()) {
            if (data == null || data[0] == null) {
                continue;
            }
            String user = data[0].toLowerCase(Locale.ROOT);
            Integer userId = ConfigHandler.playerIdCache.remove(user);
            if (userId != null) {
                ConfigHandler.playerIdCacheReversed.remove(userId);
            }
            String uuid = ConfigHandler.uuidCache.remove(user);
            if (uuid != null) {
                ConfigHandler.uuidCacheReversed.remove(uuid);
            }
        }
    }

    private static void discardProcessedConsumerData(int processId, ArrayList<Object[]> consumerData, Map<Integer, String[]> users, Map<Integer, Object> consumerObject, int count) {
        int processed = Math.min(count, consumerData.size());
        for (int index = 0; index < processed; index++) {
            Object[] data = consumerData.get(index);
            if (data == null) {
                continue;
            }
            int id = (int) data[0];
            Object object = consumerObject.get(id);
            if (isRollbackPublication((int) data[1], object)) {
                Consumer.completeRollbackPublications(1);
            }
            users.remove(id);
            consumerObject.remove(id);
            Consumer.consumerStrings.get(processId).remove(id);
            Consumer.consumerSigns.get(processId).remove(id);
            Consumer.consumerContainers.get(processId).remove(id);
            Consumer.consumerInventories.get(processId).remove(id);
            Consumer.consumerBlockList.get(processId).remove(id);
            Consumer.consumerObjectArrayList.get(processId).remove(id);
            Consumer.consumerObjectList.get(processId).remove(id);
        }
        consumerData.subList(0, processed).clear();
    }

    private static void clearConsumerData(int processId, ArrayList<Object[]> consumerData, Map<Integer, String[]> users, Map<Integer, Object> consumerObject) {
        discardProcessedConsumerData(processId, consumerData, users, consumerObject, consumerData.size());
        users.clear();
        consumerObject.clear();
    }

    private static void completeEntityContainerTransactions(List<PendingEntityContainerTransaction> transactions, TransactionOutcome outcome) {
        if (outcome != TransactionOutcome.DISCARDED) {
            for (PendingEntityContainerTransaction pending : transactions) {
                if (outcome == TransactionOutcome.COMMITTED && !pending.retryRequired) {
                    continue;
                }
                try {
                    retryEntityContainerTransaction(pending.user, pending.transaction);
                }
                catch (Exception e) {
                    ErrorReporter.report(e);
                }
            }
        }
        transactions.clear();
    }

    private static void completeEntitySpawnLogs(List<PendingEntitySpawnLog> pendingEntitySpawnLogs, TransactionOutcome outcome) {
        for (PendingEntitySpawnLog pending : pendingEntitySpawnLogs) {
            EntitySpawnData spawnData = pending.data;
            if (outcome == TransactionOutcome.COMMITTED) {
                if (pending.retryRequired) {
                    retryEntitySpawnLog(pending);
                }
                else {
                    try {
                        if (pending.confirmIdentity) {
                            EntitySpawnTracking.confirmDatabaseIdentity(spawnData.getUuid(), spawnData.getLocation());
                        }
                        else {
                            EntitySpawnTracking.clearTracking(spawnData.getUuid());
                        }
                    }
                    catch (Exception e) {
                        ErrorReporter.report(e);
                    }
                }
            }
            else {
                try {
                    EntitySpawnTracking.reverifyDatabaseRow(spawnData.getUuid(), spawnData.getLocation());
                }
                catch (Exception e) {
                    ErrorReporter.report(e);
                }
            }
        }
        pendingEntitySpawnLogs.clear();
    }

    private static void retryEntitySpawnLog(PendingEntitySpawnLog pending) {
        EntitySpawnData spawnData = pending.data;
        try {
            EntitySpawnData retry = spawnData.retryLog();
            if (retry != null) {
                Queue.queueEntitySpawnLog(pending.user, retry);
            }
            else {
                EntitySpawnTracking.clearTracking(spawnData.getUuid());
                ErrorReporter.report(new IllegalStateException("Dropped entity spawn log after repeated persistence failures: " + spawnData.getUuid()), ConfigHandler.EDITION_BRANCH.contains("-dev"));
            }
        }
        catch (Exception e) {
            ErrorReporter.report(e);
        }
    }

    private static boolean requiresIsolatedDuckDBTransaction(int action) {
        if (!ConfigHandler.databaseType.isDuckDB()) {
            return false;
        }
        return action == ENTITY_CONTAINER_TRANSACTION || action == ENTITY_INTERACTION || action == ENTITY_CONTAINER_ROLLBACK_UPDATE || action == ENTITY_CONTAINER_TRANSITION_UPDATE || action == ENTITY_SPAWN_LOG || action == ENTITY_SPAWN_UPDATE;
    }

    private static TransactionOutcome transactionOutcome(boolean committed) {
        return committed ? TransactionOutcome.COMMITTED : failedCommitOutcome();
    }

    private static TransactionOutcome failedCommitOutcome() {
        return ConfigHandler.databaseType.isClickHouse() ? TransactionOutcome.DISCARDED : TransactionOutcome.RETRY;
    }

    private static void completeTransactionState(ConsumerEntitySpawnUpdates entitySpawnUpdates, List<PendingEntityContainerTransaction> pendingEntityContainerTransactions, List<EntityContainerRollbackRetry> pendingEntityContainerRollbacks, List<PendingEntityInteraction> pendingEntityInteractions, Map<UUID, Location> pendingEntityIdentityConfirmations, Set<UUID> invalidatedEntityIdentityConfirmations, Set<UUID> promotedEntityIdentities, Map<UUID, EntitySpawnIdentity> entitySpawnIdentities, List<PendingEntitySpawnLog> pendingEntitySpawnLogs, TransactionOutcome outcome) {
        try {
            if (entitySpawnUpdates != null) {
                if (outcome == TransactionOutcome.DISCARDED) {
                    entitySpawnUpdates.afterDiscard();
                }
                else {
                    entitySpawnUpdates.afterCommit(outcome == TransactionOutcome.COMMITTED);
                }
            }
        }
        finally {
            try {
                completeEntityContainerTransactions(pendingEntityContainerTransactions, outcome);
            }
            finally {
                try {
                    retryEntityContainerRollbacks(pendingEntityContainerRollbacks, outcome);
                }
                finally {
                    try {
                        completeEntityInteractions(pendingEntityInteractions, pendingEntityIdentityConfirmations, invalidatedEntityIdentityConfirmations, promotedEntityIdentities, entitySpawnIdentities, outcome);
                    }
                    finally {
                        completeEntitySpawnLogs(pendingEntitySpawnLogs, outcome);
                    }
                }
            }
        }
    }

    private static boolean commit(ConsumerWriteBatch batch) {
        try {
            return batch.commit();
        }
        catch (Exception e) {
            batch.rollback();
            ErrorReporter.report(e);
            return false;
        }
    }

    private static final class PendingEntityContainerTransaction {

        private final String user;
        private final EntityContainerTransaction transaction;
        private final boolean retryRequired;

        private PendingEntityContainerTransaction(String user, EntityContainerTransaction transaction, boolean retryRequired) {
            this.user = user;
            this.transaction = transaction;
            this.retryRequired = retryRequired;
        }
    }

    private static final class EntityContainerRollbackRetry {

        private final String user;
        private final Location location;
        private final List<Object[]> rows;
        private final int rollbackType;
        private final boolean inventoryRollback;
        private final boolean retryRequired;

        private EntityContainerRollbackRetry(String user, Location location, List<Object[]> rows, int rollbackType, boolean inventoryRollback, boolean retryRequired) {
            this.user = user;
            this.location = location == null ? null : location.clone();
            this.rows = new ArrayList<>(rows.size());
            for (Object[] row : rows) {
                if (row != null && row.length > 9 && row[0] instanceof Long && row[9] instanceof Integer) {
                    Object[] copy = new Object[10];
                    copy[0] = row[0];
                    copy[9] = row[9];
                    this.rows.add(copy);
                }
            }
            this.rollbackType = rollbackType;
            this.inventoryRollback = inventoryRollback;
            this.retryRequired = retryRequired;
        }
    }

    private static final class PendingEntitySpawnLog {

        private final String user;
        private final EntitySpawnData data;
        private final boolean confirmIdentity;
        private final boolean retryRequired;

        private PendingEntitySpawnLog(String user, EntitySpawnData data, boolean confirmIdentity, boolean retryRequired) {
            this.user = user;
            this.data = data;
            this.confirmIdentity = confirmIdentity;
            this.retryRequired = retryRequired;
        }
    }

    private static final class PendingEntityInteraction {

        private final String user;
        private final EntityInteraction interaction;
        private final boolean identityActive;
        private final boolean retryRequired;

        private PendingEntityInteraction(String user, EntityInteraction interaction, boolean identityActive, boolean retryRequired) {
            this.user = user;
            this.interaction = interaction;
            this.identityActive = identityActive;
            this.retryRequired = retryRequired;
        }
    }
}
