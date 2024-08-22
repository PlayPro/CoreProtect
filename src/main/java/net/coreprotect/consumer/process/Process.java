package net.coreprotect.consumer.process;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;

import org.bukkit.Material;

import net.coreprotect.config.ConfigHandler;
import net.coreprotect.consumer.Consumer;
import net.coreprotect.database.Database;
import net.coreprotect.database.statement.UserStatement;

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

    public static int lastLockUpdate = 0;
    private static volatile int currentConsumerSize = 0;

    public static int getCurrentConsumerSize() {
        return currentConsumerSize;
    }

    protected static void updateLockTable(Statement statement, int locked) {
        try {
            int unixTimestamp = (int) (System.currentTimeMillis() / 1000L);
            int timeSinceLastUpdate = unixTimestamp - lastLockUpdate;
            if (timeSinceLastUpdate >= 15 || locked == 0) {
                statement.executeUpdate("UPDATE " + ConfigHandler.prefix + "database_lock SET status = '" + locked + "', time = '" + unixTimestamp + "' WHERE rowid = '1'");
                lastLockUpdate = unixTimestamp;
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    protected static void processConsumer(int processId, boolean lastRun) {
        try (Connection connection = Database.getConnection(false, 500)) {
            if (connection == null) {
                return;
            }

            Statement statement = connection.createStatement();
            Database.performCheckpoint(statement);

            Consumer.isPaused = true;
            ArrayList<Object[]> consumerData = Consumer.consumer.get(processId);
            Map<Integer, String[]> users = Consumer.consumerUsers.get(processId);
            Map<Integer, Object> consumerObject = Consumer.consumerObjects.get(processId);
            int consumerDataSize = consumerData.size();
            currentConsumerSize = consumerDataSize;

            if (currentConsumerSize == 0) { // No data, skip processing
                updateLockTable(statement, (lastRun ? 0 : 1));
                statement.close();
                Consumer.consumer_id.put(processId, new Integer[] { 0, 0 });
                Consumer.isPaused = false;
                return;
            }

            Database.beginTransaction(statement);
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
            Database.commitTransaction(statement);

            // Create prepared statements
            PreparedStatement preparedStmtSigns = Database.prepareStatement(connection, Database.SIGN, false);
            PreparedStatement preparedStmtBlocks = Database.prepareStatement(connection, Database.BLOCK, false);
            PreparedStatement preparedStmtSkulls = Database.prepareStatement(connection, Database.SKULL, true);
            PreparedStatement preparedStmtContainers = Database.prepareStatement(connection, Database.CONTAINER, false);
            PreparedStatement preparedStmtItems = Database.prepareStatement(connection, Database.ITEM, false);
            PreparedStatement preparedStmtWorlds = Database.prepareStatement(connection, Database.WORLD, false);
            PreparedStatement preparedStmtChat = Database.prepareStatement(connection, Database.CHAT, false);
            PreparedStatement preparedStmtCommand = Database.prepareStatement(connection, Database.COMMAND, false);
            PreparedStatement preparedStmtSession = Database.prepareStatement(connection, Database.SESSION, false);
            PreparedStatement preparedStmtEntities = Database.prepareStatement(connection, Database.ENTITY, true);
            PreparedStatement preparedStmtMaterials = Database.prepareStatement(connection, Database.MATERIAL, false);
            PreparedStatement preparedStmtArt = Database.prepareStatement(connection, Database.ART, false);
            PreparedStatement preparedStmtEntity = Database.prepareStatement(connection, Database.ENTITY_MAP, false);
            PreparedStatement preparedStmtBlockdata = Database.prepareStatement(connection, Database.BLOCKDATA, false);

            // Scan through consumer data
            Database.beginTransaction(statement);
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
                                case Process.ITEM_TRANSACTION:
                                    ItemTransactionProcess.process(preparedStmtItems, i, processId, id, forceData, replaceData, blockData, user, object);
                                    break;
                                case Process.STRUCTURE_GROWTH:
                                    StructureGrowthProcess.process(statement, preparedStmtBlocks, i, processId, id, user, object, forceData);
                                    break;
                                case Process.ROLLBACK_UPDATE:
                                    RollbackUpdateProcess.process(statement, processId, id, forceData, 0);
                                    break;
                                case Process.CONTAINER_ROLLBACK_UPDATE:
                                    RollbackUpdateProcess.process(statement, processId, id, forceData, 1);
                                    break;
                                case Process.INVENTORY_ROLLBACK_UPDATE:
                                    RollbackUpdateProcess.process(statement, processId, id, forceData, 2);
                                    break;
                                case Process.INVENTORY_CONTAINER_ROLLBACK_UPDATE:
                                    RollbackUpdateProcess.process(statement, processId, id, forceData, 3);
                                    break;
                                case Process.BLOCK_INVENTORY_ROLLBACK_UPDATE:
                                    RollbackUpdateProcess.process(statement, processId, id, forceData, 4);
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
                                    EntityKillProcess.process(preparedStmtBlocks, preparedStmtEntities, i, processId, id, object, user);
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
                            }

                            // If database connection goes missing, remove processed data from consumer and abort
                            if (statement.isClosed()) {
                                for (int index = (i - 1); index >= 0; index--) {
                                    consumerData.remove(index);
                                }
                                currentConsumerSize = 0;
                                Consumer.consumer_id.put(processId, new Integer[] { 0, 0 });
                                Consumer.isPaused = false;
                                return;
                            }

                            // If interrupt requested, commit data, sleep, and resume processing
                            if (Consumer.interrupt) {
                                commit(statement, preparedStmtSigns, preparedStmtBlocks, preparedStmtSkulls, preparedStmtContainers, preparedStmtItems, preparedStmtWorlds, preparedStmtChat, preparedStmtCommand, preparedStmtSession, preparedStmtEntities, preparedStmtMaterials, preparedStmtArt, preparedStmtEntity, preparedStmtBlockdata);
                                Thread.sleep(500);
                                Database.beginTransaction(statement);
                            }
                        }
                        catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
                currentConsumerSize--;
            }

            // commit data to database
            commit(statement, preparedStmtSigns, preparedStmtBlocks, preparedStmtSkulls, preparedStmtContainers, preparedStmtItems, preparedStmtWorlds, preparedStmtChat, preparedStmtCommand, preparedStmtSession, preparedStmtEntities, preparedStmtMaterials, preparedStmtArt, preparedStmtEntity, preparedStmtBlockdata);

            // close connections/statements
            preparedStmtSigns.close();
            preparedStmtBlocks.close();
            preparedStmtSkulls.close();
            preparedStmtContainers.close();
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
            statement.close();

            // clear maps
            users.clear();
            consumerObject.clear();
            consumerData.clear();
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        Consumer.consumer_id.put(processId, new Integer[] { 0, 0 });
        Consumer.isPaused = false;
    }

    private static void commit(Statement statement, PreparedStatement preparedStmtSigns, PreparedStatement preparedStmtBlocks, PreparedStatement preparedStmtSkulls, PreparedStatement preparedStmtContainers, PreparedStatement preparedStmtItems, PreparedStatement preparedStmtWorlds, PreparedStatement preparedStmtChat, PreparedStatement preparedStmtCommand, PreparedStatement preparedStmtSession, PreparedStatement preparedStmtEntities, PreparedStatement preparedStmtMaterials, PreparedStatement preparedStmtArt, PreparedStatement preparedStmtEntity, PreparedStatement preparedStmtBlockdata) {
        try {
            preparedStmtSigns.executeBatch();
            preparedStmtBlocks.executeBatch();
            preparedStmtSkulls.executeBatch();
            preparedStmtContainers.executeBatch();
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
            Database.commitTransaction(statement);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }
}
