package net.coreprotect.consumer.process;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
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

import net.coreprotect.config.Config;
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.consumer.Consumer;
import net.coreprotect.consumer.Queue;
import net.coreprotect.database.Database;
import net.coreprotect.database.logger.EntityInteractionLogger;
import net.coreprotect.database.statement.EntityInteractionStatement;
import net.coreprotect.database.statement.EntitySpawnStatement;
import net.coreprotect.database.statement.UserStatement;
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

    protected static void updateLockTable(Statement statement, int locked) {
        if (!Config.getGlobal().DATABASE_LOCK) {
            return;
        }

        try {
            int unixTimestamp = (int) (System.currentTimeMillis() / 1000L);
            int timeSinceLastUpdate = unixTimestamp - lastLockUpdate;
            if (timeSinceLastUpdate >= 15 || locked == 0) {
                statement.executeUpdate("ALTER TABLE " + ConfigHandler.prefix + "database_lock UPDATE status = '" + locked + "', time = '" + unixTimestamp + "' WHERE rowid = '1'");
                lastLockUpdate = unixTimestamp;
            }
        }
        catch (Exception e) {
            ErrorReporter.report(e);
        }
    }

    protected static void processConsumer(int processId, boolean lastRun) {
        if (ConfigHandler.READ_ONLY) {
            Consumer.consumer.get(processId).clear();
            Consumer.consumerUsers.get(processId).clear();
            Consumer.consumerObjects.get(processId).clear();
            Consumer.consumer_id.put(processId, new Integer[] { 0, 0 });

            return;
        }

        Map<UUID, Location> pendingEntitySpawnLogs = new LinkedHashMap<>();
        Map<UUID, EntitySpawnIdentity> entitySpawnIdentities = new LinkedHashMap<>();
        Map<UUID, Location> pendingEntityIdentityConfirmations = new LinkedHashMap<>();
        Set<UUID> promotedEntityIdentities = new HashSet<>();
        List<PendingEntityInteraction> pendingEntityInteractions = new ArrayList<>();
        List<EntityContainerRollbackRetry> pendingEntityContainerRollbacks = new ArrayList<>();
        EntitySpawnStatement.Updates entitySpawnUpdates = null;
        ArrayList<Object[]> consumerData = null;
        Map<Integer, String[]> users = null;
        Map<Integer, Object> consumerObject = null;
        boolean processingStarted = false;
        boolean consumerDataCleared = false;
        try (Connection connection = Database.getConnection(false, 500)) {
            if (connection == null) {
                return;
            }

            Statement statement = connection.createStatement();

            Consumer.isPaused = true;
            consumerData = Consumer.consumer.get(processId);
            users = Consumer.consumerUsers.get(processId);
            consumerObject = Consumer.consumerObjects.get(processId);
            int consumerDataSize = consumerData.size();
            currentConsumerSize = consumerDataSize;

            if (currentConsumerSize == 0) { // No data, skip processing
                updateLockTable(statement, (lastRun ? 0 : 1));
                statement.close();
                Consumer.consumer_id.put(processId, new Integer[] { 0, 0 });
                Consumer.isPaused = false;
                return;
            }

            boolean hasEntitySpawnLogs = false;
            boolean hasEntitySpawnUpdates = false;
            boolean hasEntityKills = false;
            boolean hasEntityContainerTransactions = false;
            boolean hasEntityInteractions = false;
            Set<UUID> entityIdentityUuids = new HashSet<>();
            Set<Integer> entityIdentityRowIds = new HashSet<>();
            for (int index = 0; index < consumerDataSize; index++) {
                Object[] data = consumerData.get(index);
                if (data == null) {
                    continue;
                }
                int action = (int) data[1];
                hasEntitySpawnLogs |= action == Process.ENTITY_SPAWN_LOG;
                hasEntitySpawnUpdates |= action == Process.ENTITY_SPAWN_UPDATE || action == Process.ENTITY_CONTAINER_TRANSITION_UPDATE;
                hasEntityKills |= action == Process.ENTITY_KILL;
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
                    if (spawnData.getPreviousUuid() != null) {
                        entityIdentityUuids.add(spawnData.getPreviousUuid());
                    }
                    if (spawnData.getTrackingRowId() > 0 && spawnData.getUuid() != null) {
                        entityIdentityRowIds.add(spawnData.getTrackingRowId());
                    }
                }
            }

            if (!beginConsumerTransaction(statement)) {
                deferConsumerRetry();
                return;
            }
            // Scan through usernames, ensure everything is loaded in memory.
            for (Entry<Integer, String[]> entry : users.entrySet()) {
                String[] data = entry.getValue();
                if (data != null) {
                    String user = data[0];
                    String uuid = data[1];
                    if (user != null && ConfigHandler.playerIdCache.get(user.toLowerCase(Locale.ROOT)) == null) {
                        UserStatement.loadId(connection, user, uuid);
                    }
                }
            }
            updateLockTable(statement, (lastRun ? 0 : 1));
            if (!Database.commitTransactionChecked(statement, Config.getGlobal().MYSQL)) {
                Database.rollbackTransaction(statement, Config.getGlobal().MYSQL);
                invalidateUserCaches(users);
                deferConsumerRetry();
                return;
            }

            if (hasEntityContainerTransactions || hasEntityInteractions) {
                entitySpawnIdentities.putAll(EntitySpawnStatement.loadIdentities(connection, entityIdentityUuids));
                Map<Integer, EntitySpawnIdentity> identitiesByRowId = EntitySpawnStatement.loadIdentitiesByRowIds(connection, entityIdentityRowIds);
                bindPendingEntitySpawnIdentities(consumerData, consumerObject, entitySpawnIdentities, identitiesByRowId);
            }

            // Create prepared statements
            PreparedStatement preparedStmtSigns = Database.prepareStatement(connection, Database.SIGN);
            PreparedStatement preparedStmtBlocks = Database.prepareStatement(connection, Database.BLOCK);
            PreparedStatement preparedStmtSkulls = Database.prepareStatement(connection, Database.SKULL);
            PreparedStatement preparedStmtContainers = Database.prepareStatement(connection, Database.CONTAINER);
            PreparedStatement preparedStmtItems = Database.prepareStatement(connection, Database.ITEM);
            PreparedStatement preparedStmtWorlds = Database.prepareStatement(connection, Database.WORLD);
            PreparedStatement preparedStmtChat = Database.prepareStatement(connection, Database.CHAT);
            PreparedStatement preparedStmtCommand = Database.prepareStatement(connection, Database.COMMAND);
            PreparedStatement preparedStmtSession = Database.prepareStatement(connection, Database.SESSION);
            PreparedStatement preparedStmtEntities = Database.prepareStatement(connection, Database.ENTITY);
            PreparedStatement preparedStmtMaterials = Database.prepareStatement(connection, Database.MATERIAL);
            PreparedStatement preparedStmtArt = Database.prepareStatement(connection, Database.ART);
            PreparedStatement preparedStmtEntity = Database.prepareStatement(connection, Database.ENTITY_MAP);
            PreparedStatement preparedStmtBlockdata = Database.prepareStatement(connection, Database.BLOCKDATA);
            PreparedStatement preparedStmtEntityContainers = hasEntityContainerTransactions ? Database.prepareStatement(connection, Database.ENTITY_CONTAINER) : null;
            PreparedStatement preparedStmtEntitySpawns = hasEntitySpawnLogs || hasEntityInteractions ? Database.prepareStatement(connection, Database.ENTITY_SPAWN) : null;
            PreparedStatement preparedStmtEntityInteractions = hasEntityInteractions ? Database.prepareStatement(connection, Database.ENTITY_INTERACTION) : null;
            PreparedStatement preparedStmtEntityInteractionCheckpoints = hasEntityInteractions ? Database.prepareStatement(connection, Database.ENTITY_SPAWN) : null;
            PreparedStatement preparedStmtEntitySpawnBlocks = hasEntitySpawnLogs ? Database.prepareStatement(connection, Database.BLOCK) : null;
            PreparedStatement preparedStmtEntitySpawnLinks = hasEntitySpawnLogs ? EntitySpawnStatement.prepareBlockLink(connection) : null;
            PreparedStatement preparedStmtEntityKillLinks = hasEntityKills ? EntitySpawnStatement.prepareKillLink(connection) : null;
            entitySpawnUpdates = hasEntitySpawnUpdates ? new EntitySpawnStatement.Updates(connection) : null;

            // Scan through consumer data
            if (!beginConsumerTransaction(statement)) {
                deferConsumerRetry();
                return;
            }
            int processedThrough = 0;
            processingStarted = true;
            for (int i = 0; i < consumerDataSize; i++) {
                Object[] data = consumerData.get(i);
                if (data != null) {
                    int id = (int) data[0];
                    int action = (int) data[1];
                    Material blockType = (Material) data[2];
                    int blockData = (int) data[3];
                    Material replaceType = (Material) data[4];
                    int replaceData = (int) data[5];
                    int forceData = (int) data[6];

                    if (users.get(id) != null && consumerObject.get(id) != null) {
                        String user = users.get(id)[0];
                        Object object = consumerObject.get(id);

                        try {
                            switch (action) {
                                case Process.BLOCK_BREAK:
                                    BlockBreakProcess.process(preparedStmtBlocks, preparedStmtSkulls, i, processId, id, blockType, blockData, replaceType, forceData, user, object, (String) data[7]);
                                    break;
                                case Process.BLOCK_PLACE:
                                    BlockPlaceProcess.process(preparedStmtBlocks, preparedStmtSkulls, i, blockType, blockData, replaceType, replaceData, forceData, user, object, (String) data[7], (String) data[8]);
                                    break;
                                case Process.SIGN_TEXT:
                                    SignTextProcess.process(preparedStmtSigns, i, processId, id, forceData, user, object, replaceData, blockData);
                                    break;
                                case Process.CONTAINER_BREAK:
                                    ContainerBreakProcess.process(preparedStmtContainers, i, processId, id, blockType, user, object);
                                    break;
                                case Process.PLAYER_INTERACTION:
                                    PlayerInteractionProcess.process(preparedStmtBlocks, i, user, object, blockType);
                                    break;
                                case Process.CONTAINER_TRANSACTION:
                                    ContainerTransactionProcess.process(preparedStmtContainers, preparedStmtItems, i, processId, id, blockType, forceData, user, object);
                                    break;
                                case Process.ENTITY_CONTAINER_TRANSACTION:
                                    EntityContainerTransaction transaction = (EntityContainerTransaction) object;
                                    EntitySpawnIdentity identity = entitySpawnIdentities.get(transaction.getEntityUuid());
                                    if (!ContainerTransactionProcess.processEntity(preparedStmtEntityContainers, i, user, transaction, identity)) {
                                        retryEntityContainerTransaction(user, transaction);
                                    }
                                    break;
                                case Process.ENTITY_INTERACTION:
                                    EntityInteraction interaction = (EntityInteraction) object;
                                    EntityInteractionLogger.LogContext logContext = EntityInteractionLogger.prepare(user, interaction);
                                    if (logContext == null) {
                                        break;
                                    }

                                    EntitySpawnIdentity existingIdentity = entitySpawnIdentities.get(interaction.getEntityUuid());
                                    EntitySpawnIdentity[] loggedIdentity = { existingIdentity };
                                    try {
                                        Database.executeSavepoint(statement, "entity_interaction_log", () -> {
                                            if (loggedIdentity[0] == null) {
                                                int time = (int) (System.currentTimeMillis() / 1000L);
                                                loggedIdentity[0] = EntitySpawnStatement.insertIdentity(preparedStmtEntitySpawns, time, interaction.getEntityUuid(), interaction.getOrigin(), interaction.getCurrentLocation());
                                            }
                                            EntityInteractionLogger.log(preparedStmtEntityInteractions, preparedStmtEntityInteractionCheckpoints, loggedIdentity[0], interaction, logContext);
                                        });
                                    }
                                    catch (Exception e) {
                                        retryEntityInteraction(user, interaction);
                                        throw e;
                                    }

                                    entitySpawnIdentities.put(interaction.getEntityUuid(), loggedIdentity[0]);
                                    pendingEntityInteractions.add(new PendingEntityInteraction(user, interaction));
                                    if (existingIdentity == null) {
                                        promotedEntityIdentities.add(interaction.getEntityUuid());
                                    }
                                    pendingEntityIdentityConfirmations.put(interaction.getEntityUuid(), interaction.getCurrentLocation());
                                    break;
                                case Process.ITEM_TRANSACTION:
                                    ItemTransactionProcess.process(preparedStmtItems, i, processId, id, forceData, replaceData, blockData, user, object);
                                    break;
                                case Process.STRUCTURE_GROWTH:
                                    StructureGrowthProcess.process(statement, preparedStmtBlocks, i, processId, id, user, object, forceData);
                                    break;
                                case Process.ROLLBACK_UPDATE:
                                    RollbackUpdateProcess.process(statement, processId, id, forceData, RollbackUpdateTargets.BLOCK);
                                    break;
                                case Process.CONTAINER_ROLLBACK_UPDATE:
                                    RollbackUpdateProcess.process(statement, processId, id, forceData, RollbackUpdateTargets.CONTAINER);
                                    break;
                                case Process.INVENTORY_ROLLBACK_UPDATE:
                                    RollbackUpdateProcess.process(statement, processId, id, forceData, RollbackUpdateTargets.INVENTORY_ITEM);
                                    break;
                                case Process.INVENTORY_CONTAINER_ROLLBACK_UPDATE:
                                    RollbackUpdateProcess.process(statement, processId, id, forceData, RollbackUpdateTargets.INVENTORY_CONTAINER);
                                    break;
                                case Process.BLOCK_INVENTORY_ROLLBACK_UPDATE:
                                    RollbackUpdateProcess.process(statement, processId, id, forceData, RollbackUpdateTargets.BLOCK_INVENTORY);
                                    break;
                                case Process.ENTITY_CONTAINER_ROLLBACK_UPDATE:
                                    List<Object[]> entityContainerRows = Consumer.consumerObjectArrayList.get(processId).get(id);
                                    if (entityContainerRows != null) {
                                        try {
                                            RollbackUpdateProcess.processChecked(statement, entityContainerRows, forceData, RollbackUpdateTargets.ENTITY_CONTAINER, blockData == 1);
                                            Consumer.consumerObjectArrayList.get(processId).remove(id);
                                            pendingEntityContainerRollbacks.add(new EntityContainerRollbackRetry(user, object instanceof Location ? (Location) object : null, entityContainerRows, forceData, blockData == 1));
                                        }
                                        catch (SQLException e) {
                                            try {
                                                Queue.queueEntityContainerRollbackUpdate(user, object instanceof Location ? (Location) object : null, entityContainerRows, forceData, blockData == 1);
                                            }
                                            catch (Exception retryException) {
                                                e.addSuppressed(retryException);
                                            }
                                            throw e;
                                        }
                                    }
                                    break;
                                case Process.ENTITY_CONTAINER_TRANSITION_UPDATE:
                                    EntityContainerRollbackUpdate containerUpdate = (EntityContainerRollbackUpdate) object;
                                    entitySpawnUpdates.applyCombined(containerUpdate, () -> RollbackUpdateProcess.processChecked(statement, containerUpdate.getRows(), containerUpdate.getRollbackType(), RollbackUpdateTargets.ENTITY_CONTAINER, containerUpdate.isInventoryRollback()));
                                    break;
                                case Process.WORLD_INSERT:
                                    WorldInsertProcess.process(preparedStmtWorlds, i, statement, object, forceData);
                                    break;
                                case Process.SIGN_UPDATE:
                                    SignUpdateProcess.process(statement, object, user, blockData, forceData);
                                    break;
                                case Process.SKULL_UPDATE:
                                    SkullUpdateProcess.process(statement, object, forceData);
                                    break;
                                case Process.PLAYER_CHAT:
                                    PlayerChatProcess.process(preparedStmtChat, i, processId, id, object, user);
                                    break;
                                case Process.PLAYER_COMMAND:
                                    PlayerCommandProcess.process(preparedStmtCommand, i, processId, id, object, user);
                                    break;
                                case Process.PLAYER_LOGIN:
                                    PlayerLoginProcess.process(connection, preparedStmtSession, i, processId, id, object, blockData, replaceData, forceData, user);
                                    break;
                                case Process.PLAYER_LOGOUT:
                                    PlayerLogoutProcess.process(preparedStmtSession, i, object, forceData, user);
                                    break;
                                case Process.ENTITY_KILL:
                                    EntityKillProcess.process(preparedStmtBlocks, preparedStmtEntities, preparedStmtEntityKillLinks, i, processId, id, object, user);
                                    break;
                                case Process.ENTITY_SPAWN:
                                    EntitySpawnProcess.process(statement, object, forceData);
                                    break;
                                case Process.NATURAL_BLOCK_BREAK:
                                    NaturalBlockBreakProcess.process(statement, preparedStmtBlocks, i, processId, id, user, object, blockType, blockData, (String) data[7]);
                                    break;
                                case Process.MATERIAL_INSERT:
                                    MaterialInsertProcess.process(preparedStmtMaterials, statement, i, object, forceData);
                                    break;
                                case Process.ART_INSERT:
                                    ArtInsertProcess.process(preparedStmtArt, statement, i, object, forceData);
                                    break;
                                case Process.ENTITY_INSERT:
                                    EntityInsertProcess.process(preparedStmtEntity, statement, i, object, forceData);
                                    break;
                                case Process.PLAYER_KILL:
                                    PlayerKillProcess.process(preparedStmtBlocks, i, id, object, user);
                                    break;
                                case Process.BLOCKDATA_INSERT:
                                    BlockDataInsertProcess.process(preparedStmtBlockdata, statement, i, object, forceData);
                                    break;
                                case Process.ENTITY_SPAWN_LOG:
                                    EntitySpawnIdentity spawnIdentity = EntitySpawnLogProcess.process(preparedStmtEntitySpawnBlocks, preparedStmtEntitySpawns, preparedStmtEntitySpawnLinks, object, user);
                                    if (spawnIdentity != null && object instanceof EntitySpawnData) {
                                        entitySpawnIdentities.put(spawnIdentity.getUuid(), spawnIdentity);
                                        pendingEntitySpawnLogs.put(spawnIdentity.getUuid(), ((EntitySpawnData) object).getLocation());
                                    }
                                    break;
                                case Process.ENTITY_SPAWN_UPDATE:
                                    EntitySpawnUpdateProcess.process(entitySpawnUpdates, object);
                                    break;
                            }

                            // If database connection goes missing, remove processed data from consumer and abort
                            if (statement.isClosed()) {
                                if (entitySpawnUpdates != null) {
                                    entitySpawnUpdates.afterCommit(false);
                                }
                                retryEntityContainerRollbacks(pendingEntityContainerRollbacks, false);
                                completeEntityInteractions(pendingEntityInteractions, pendingEntityIdentityConfirmations, promotedEntityIdentities, entitySpawnIdentities, false);
                                discardProcessedConsumerData(processId, consumerData, users, consumerObject, i + 1);
                                deferConsumerRetry();
                                completeEntitySpawnLogs(pendingEntitySpawnLogs, false);
                                return;
                            }

                            // If interrupt requested, commit data, sleep, and resume processing
                            if (Consumer.interrupt) {
                                boolean committed = commit(statement, preparedStmtSigns, preparedStmtBlocks, preparedStmtSkulls, preparedStmtContainers, preparedStmtEntityContainers, preparedStmtEntityInteractions, preparedStmtItems, preparedStmtWorlds, preparedStmtChat, preparedStmtCommand, preparedStmtSession, preparedStmtEntities, preparedStmtMaterials, preparedStmtArt, preparedStmtEntity, preparedStmtBlockdata, preparedStmtEntityKillLinks);
                                if (entitySpawnUpdates != null) {
                                    entitySpawnUpdates.afterCommit(committed);
                                }
                                retryEntityContainerRollbacks(pendingEntityContainerRollbacks, committed);
                                completeEntityInteractions(pendingEntityInteractions, pendingEntityIdentityConfirmations, promotedEntityIdentities, entitySpawnIdentities, committed);
                                completeEntitySpawnLogs(pendingEntitySpawnLogs, committed);
                                processedThrough = i + 1;
                                Thread.sleep(500);
                                if (!beginConsumerTransaction(statement)) {
                                    discardProcessedConsumerData(processId, consumerData, users, consumerObject, processedThrough);
                                    deferConsumerRetry();
                                    return;
                                }
                            }
                        }
                        catch (Exception e) {
                            ErrorReporter.report(e);
                        }
                    }
                }
                currentConsumerSize--;
            }

            // commit data to database
            boolean committed = commit(statement, preparedStmtSigns, preparedStmtBlocks, preparedStmtSkulls, preparedStmtContainers, preparedStmtEntityContainers, preparedStmtEntityInteractions, preparedStmtItems, preparedStmtWorlds, preparedStmtChat, preparedStmtCommand, preparedStmtSession, preparedStmtEntities, preparedStmtMaterials, preparedStmtArt, preparedStmtEntity, preparedStmtBlockdata, preparedStmtEntityKillLinks);
            try {
                try {
                    if (entitySpawnUpdates != null) {
                        entitySpawnUpdates.afterCommit(committed);
                    }
                }
                finally {
                    retryEntityContainerRollbacks(pendingEntityContainerRollbacks, committed);
                    completeEntityInteractions(pendingEntityInteractions, pendingEntityIdentityConfirmations, promotedEntityIdentities, entitySpawnIdentities, committed);
                    completeEntitySpawnLogs(pendingEntitySpawnLogs, committed);
                }
            }
            finally {
                clearConsumerData(processId, consumerData, users, consumerObject);
                consumerDataCleared = true;
            }

            // close connections/statements
            preparedStmtSigns.close();
            preparedStmtBlocks.close();
            preparedStmtSkulls.close();
            preparedStmtContainers.close();
            if (preparedStmtEntityContainers != null) {
                preparedStmtEntityContainers.close();
            }
            if (preparedStmtEntityInteractions != null) {
                preparedStmtEntityInteractions.close();
                preparedStmtEntityInteractionCheckpoints.close();
            }
            preparedStmtItems.close();
            preparedStmtWorlds.close();
            preparedStmtChat.close();
            preparedStmtCommand.close();
            preparedStmtSession.close();
            preparedStmtEntities.close();
            preparedStmtMaterials.close();
            preparedStmtArt.close();
            preparedStmtEntity.close();
            preparedStmtBlockdata.close();
            if (preparedStmtEntitySpawns != null) {
                preparedStmtEntitySpawns.close();
                if (preparedStmtEntitySpawnBlocks != null) {
                    preparedStmtEntitySpawnBlocks.close();
                    preparedStmtEntitySpawnLinks.close();
                }
            }
            if (preparedStmtEntityKillLinks != null) {
                preparedStmtEntityKillLinks.close();
            }
            if (entitySpawnUpdates != null) {
                entitySpawnUpdates.close();
                entitySpawnUpdates = null;
            }
            statement.close();
        }
        catch (Exception e) {
            if (processingStarted && !consumerDataCleared && consumerData != null && users != null && consumerObject != null) {
                try {
                    if (entitySpawnUpdates != null) {
                        entitySpawnUpdates.afterCommit(false);
                    }
                    retryEntityContainerRollbacks(pendingEntityContainerRollbacks, false);
                    completeEntityInteractions(pendingEntityInteractions, pendingEntityIdentityConfirmations, promotedEntityIdentities, entitySpawnIdentities, false);
                    clearConsumerData(processId, consumerData, users, consumerObject);
                    consumerDataCleared = true;
                }
                catch (Exception cleanupException) {
                    e.addSuppressed(cleanupException);
                }
            }
            ErrorReporter.report(e);
        }
        finally {
            if (entitySpawnUpdates != null) {
                try {
                    entitySpawnUpdates.close();
                }
                catch (Exception e) {
                    ErrorReporter.report(e);
                }
            }
        }
        completeEntityInteractions(pendingEntityInteractions, pendingEntityIdentityConfirmations, promotedEntityIdentities, entitySpawnIdentities, false);
        completeEntitySpawnLogs(pendingEntitySpawnLogs, false);

        if (consumerDataCleared) {
            currentConsumerSize = 0;
            Consumer.consumer_id.put(processId, new Integer[] { 0, 0 });
            Consumer.isPaused = false;
        }
        else {
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
            ErrorReporter.report(new IllegalStateException("Dropped entity interaction after repeated persistence failures: " + interaction.getEntityUuid()), ConfigHandler.EDITION_BRANCH.contains("-dev"));
        }
    }

    private static void completeEntityInteractions(List<PendingEntityInteraction> interactions, Map<UUID, Location> identityConfirmations, Set<UUID> promotedIdentities, Map<UUID, EntitySpawnIdentity> identities, boolean committed) {
        if (committed) {
            for (Map.Entry<UUID, Location> entry : identityConfirmations.entrySet()) {
                EntitySpawnTracking.confirmDatabaseIdentity(entry.getKey(), entry.getValue());
            }
        }
        else {
            for (PendingEntityInteraction pending : interactions) {
                try {
                    retryEntityInteraction(pending.user, pending.interaction);
                }
                catch (Exception e) {
                    ErrorReporter.report(e);
                }
            }
            for (UUID uuid : promotedIdentities) {
                identities.remove(uuid);
            }
        }
        interactions.clear();
        identityConfirmations.clear();
        promotedIdentities.clear();
    }

    private static void retryEntityContainerRollbacks(List<EntityContainerRollbackRetry> updates, boolean committed) {
        if (!committed) {
            for (EntityContainerRollbackRetry update : updates) {
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

    private static boolean beginConsumerTransaction(Statement statement) {
        try {
            Database.beginTransaction(statement, Config.getGlobal().MYSQL);
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

    private static void completeEntitySpawnLogs(Map<UUID, Location> pendingEntitySpawnLogs, boolean committed) {
        for (Map.Entry<UUID, Location> entry : pendingEntitySpawnLogs.entrySet()) {
            if (committed) {
                EntitySpawnTracking.confirmDatabaseIdentity(entry.getKey(), entry.getValue());
            }
            else {
                try {
                    EntitySpawnTracking.reverifyDatabaseRow(entry.getKey(), entry.getValue());
                }
                catch (Exception e) {
                    ErrorReporter.report(e);
                }
            }
        }
        pendingEntitySpawnLogs.clear();
    }

    private static boolean commit(Statement statement, PreparedStatement preparedStmtSigns, PreparedStatement preparedStmtBlocks, PreparedStatement preparedStmtSkulls, PreparedStatement preparedStmtContainers, PreparedStatement preparedStmtEntityContainers, PreparedStatement preparedStmtEntityInteractions, PreparedStatement preparedStmtItems, PreparedStatement preparedStmtWorlds, PreparedStatement preparedStmtChat, PreparedStatement preparedStmtCommand, PreparedStatement preparedStmtSession, PreparedStatement preparedStmtEntities, PreparedStatement preparedStmtMaterials, PreparedStatement preparedStmtArt, PreparedStatement preparedStmtEntity, PreparedStatement preparedStmtBlockdata, PreparedStatement preparedStmtEntityKillLinks) {
        try {
            preparedStmtSigns.executeBatch();
            preparedStmtBlocks.executeBatch();
            preparedStmtSkulls.executeBatch();
            preparedStmtContainers.executeBatch();
            if (preparedStmtEntityContainers != null) {
                preparedStmtEntityContainers.executeBatch();
            }
            if (preparedStmtEntityInteractions != null) {
                preparedStmtEntityInteractions.executeBatch();
            }
            preparedStmtItems.executeBatch();
            preparedStmtWorlds.executeBatch();
            preparedStmtChat.executeBatch();
            preparedStmtCommand.executeBatch();
            preparedStmtSession.executeBatch();
            preparedStmtEntities.executeBatch();
            preparedStmtMaterials.executeBatch();
            preparedStmtArt.executeBatch();
            preparedStmtEntity.executeBatch();
            preparedStmtBlockdata.executeBatch();
            if (preparedStmtEntityKillLinks != null) {
                preparedStmtEntityKillLinks.executeBatch();
            }
            boolean committed = Database.commitTransactionChecked(statement, Config.getGlobal().MYSQL);
            if (!committed) {
                Database.rollbackTransaction(statement, Config.getGlobal().MYSQL);
            }
            return committed;
        }
        catch (Exception e) {
            Database.rollbackTransaction(statement, Config.getGlobal().MYSQL);
            ErrorReporter.report(e);
            return false;
        }
    }

    private static final class EntityContainerRollbackRetry {

        private final String user;
        private final Location location;
        private final List<Object[]> rows;
        private final int rollbackType;
        private final boolean inventoryRollback;

        private EntityContainerRollbackRetry(String user, Location location, List<Object[]> rows, int rollbackType, boolean inventoryRollback) {
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
        }
    }

    private static final class PendingEntityInteraction {

        private final String user;
        private final EntityInteraction interaction;

        private PendingEntityInteraction(String user, EntityInteraction interaction) {
            this.user = user;
            this.interaction = interaction;
        }
    }
}
