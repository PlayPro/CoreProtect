package net.coreprotect.database.statement;

import java.io.ByteArrayInputStream;
import java.util.List;

import org.bukkit.util.io.BukkitObjectInputStream;

import net.coreprotect.config.ConfigHandler;
import net.coreprotect.database.ConsumerWriteBatch;
import net.coreprotect.database.Database;
import net.coreprotect.database.DatabaseType;
import net.coreprotect.utility.BlockUtils;
import net.coreprotect.utility.ErrorReporter;
import net.coreprotect.utility.ItemUtils;
import net.coreprotect.utility.serialize.BlockMetaCodec;

public class BlockStatement {

    private BlockStatement() {
        throw new IllegalStateException("Database class");
    }

    public static void insert(ConsumerWriteBatch batch, int batchCount, int time, int id, int wid, int x, int y, int z, int type, int data, List<Object> meta, String blockData, int action, int rolledBack) {
        insertChecked(batch, batchCount, time, id, wid, x, y, z, type, data, meta, blockData, action, rolledBack);
    }

    public static boolean insertChecked(ConsumerWriteBatch batch, int batchCount, int time, int id, int wid, int x, int y, int z, int type, int data, List<Object> meta, String blockData, int action, int rolledBack) {
        try {
            byte[] bBlockData = BlockUtils.stringToByteData(blockData, type);
            byte[] byteData = serializeMetadata(meta);
            batch.addBlock(batchCount, time, id, wid, x, y, z, type, data, byteData, bBlockData, action, rolledBack);
            return true;
        }
        catch (Exception e) {
            Database.handleWriteFailure(e);
            return false;
        }
    }

    public static long insertImmediate(ConsumerWriteBatch batch, int time, int id, int wid, int x, int y, int z, int type, int data, List<Object> meta, String blockData, int action, int rolledBack) throws Exception {
        byte[] bBlockData = BlockUtils.stringToByteData(blockData, type);
        byte[] byteData = serializeMetadata(meta);
        return batch.addBlockReturningId(time, id, wid, x, y, z, type, data, byteData, bBlockData, action, rolledBack);
    }

    public static byte[] serializeMetadata(List<Object> metadata) {
        return serializeMetadata(metadata, ConfigHandler.databaseType);
    }

    public static byte[] serializeMetadata(List<Object> metadata, DatabaseType databaseType) {
        if (metadata == null) {
            return null;
        }
        try {
            return serializeMetadataStrict(metadata, databaseType);
        }
        catch (Exception exception) {
            if (databaseType.isColumnar()) {
                byte[] legacy = ItemUtils.convertByteData(metadata);
                if (legacy != null) {
                    ErrorReporter.report(exception, ConfigHandler.EDITION_BRANCH.contains("-dev"));
                    return legacy;
                }
            }
            ErrorReporter.report(exception, ConfigHandler.EDITION_BRANCH.contains("-dev"));
            return null;
        }
    }

    public static byte[] transcodeMetadata(byte[] metadata, DatabaseType targetType) throws Exception {
        if (metadata == null) {
            return null;
        }
        if (BlockMetaCodec.isEncoded(metadata)) {
            if (targetType.isColumnar()) {
                return BlockMetaCodec.canonicalize(metadata);
            }
            return serializeMetadataStrict(BlockMetaCodec.decode(metadata), targetType);
        }
        return serializeMetadataStrict(deserializeMetadataStrict(metadata), targetType);
    }

    public static List<Object> deserializeMetadata(byte[] metadata) {
        if (metadata == null) {
            return null;
        }
        try {
            return deserializeMetadataStrict(metadata);
        }
        catch (Exception exception) {
            ErrorReporter.report(exception);
            return null;
        }
    }

    private static byte[] serializeMetadataStrict(List<Object> metadata, DatabaseType databaseType) {
        if (databaseType.isColumnar()) {
            return BlockMetaCodec.encode(metadata);
        }
        byte[] result = ItemUtils.convertByteData(metadata);
        if (result == null) {
            throw new IllegalArgumentException("Unable to serialize legacy block metadata");
        }
        return result;
    }

    private static List<Object> deserializeMetadataStrict(byte[] metadata) throws Exception {
        if (BlockMetaCodec.isEncoded(metadata)) {
            return BlockMetaCodec.decode(metadata);
        }
        try (ByteArrayInputStream inputBytes = new ByteArrayInputStream(metadata); BukkitObjectInputStream input = new BukkitObjectInputStream(inputBytes)) {
            Object value = input.readObject();
            if (!(value instanceof List<?>)) {
                throw new IllegalArgumentException("Block metadata root is not a list");
            }
            @SuppressWarnings("unchecked")
            List<Object> values = (List<Object>) value;
            return values;
        }
    }
}
