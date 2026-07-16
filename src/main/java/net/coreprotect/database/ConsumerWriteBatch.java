package net.coreprotect.database;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public interface ConsumerWriteBatch extends AutoCloseable {

    enum ReferenceKind {
        ART,
        BLOCK_DATA,
        ENTITY,
        MATERIAL,
        WORLD
    }

    void begin() throws Exception;

    boolean commit() throws Exception;

    default boolean shouldCommit() {
        return false;
    }

    void rollback();

    void executeAtomically(String name, Database.SavepointOperation operation) throws Exception;

    int resolveUserId(String user, String uuid) throws Exception;

    void recordUsername(String user, String uuid, int retainHistory, int time) throws Exception;

    void updateDatabaseLock(int status, int time) throws Exception;

    void addReference(ReferenceKind kind, int batchCount, int id, String value) throws Exception;

    void addBlock(int batchCount, int time, int userId, int worldId, int x, int y, int z, int type, int data, byte[] meta, byte[] blockData, int action, int rolledBack) throws Exception;

    long addBlockReturningId(int time, int userId, int worldId, int x, int y, int z, int type, int data, byte[] meta, byte[] blockData, int action, int rolledBack) throws Exception;

    int addSkull(int time, String owner, String skin) throws Exception;

    void addContainer(int batchCount, int time, int userId, int worldId, int x, int y, int z, int type, int data, int amount, byte[] metadata, int action, int rolledBack) throws Exception;

    void addEntityContainer(int batchCount, int time, int userId, int entitySpawnRowId, int worldId, int x, int y, int z, int type, int data, int amount, byte[] metadata, int action, int rolledBack) throws Exception;

    void addItem(int batchCount, int time, int userId, int worldId, int x, int y, int z, int type, byte[] data, int amount, int action, int rolledBack) throws Exception;

    void addChat(int batchCount, long time, int userId, int worldId, int x, int y, int z, String message) throws Exception;

    void addCommand(int batchCount, long time, int userId, int worldId, int x, int y, int z, String message) throws Exception;

    void addSession(int batchCount, int time, int userId, int worldId, int x, int y, int z, int action) throws Exception;

    void addSign(int batchCount, int time, int userId, int worldId, int x, int y, int z, int action, int color, int colorSecondary, int data, int waxed, int face, String[] lines) throws Exception;

    int addEntity(int time, byte[] data) throws Exception;

    int addEntitySpawn(int time, Long blockRowId, Integer killRowId, UUID uuid, int originWorldId, int currentWorldId, double originX, double originY, double originZ, double currentX, double currentY, double currentZ, float yaw, float pitch, byte[] data, int removed) throws Exception;

    void linkEntitySpawnBlock(int trackingRowId, long blockRowId) throws Exception;

    void linkEntitySpawnKill(UUID uuid, int killRowId) throws Exception;

    void checkpointEntitySpawn(int trackingRowId, int worldId, double x, double y, double z, float yaw, float pitch) throws Exception;

    void addEntityInteraction(int time, int userId, int entitySpawnRowId, int worldId, int x, int y, int z, int type, int action, byte[] metadata, int rolledBack) throws Exception;

    void updateRolledBack(int target, int rolledBack, List<Long> rowIds) throws Exception;

    default void updateRolledBackRows(int target, int rolledBack, List<Object[]> rows) throws Exception {
        List<Long> rowIds = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            rowIds.add((Long) row[0]);
        }
        updateRolledBack(target, rolledBack, rowIds);
    }

    ConsumerEntitySpawnUpdates entitySpawnUpdates() throws Exception;

    @Override
    void close() throws Exception;

}
