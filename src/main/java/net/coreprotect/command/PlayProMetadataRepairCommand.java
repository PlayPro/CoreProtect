package net.coreprotect.command;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.util.io.BukkitObjectOutputStream;
import org.bukkit.command.CommandSender;

import com.google.gson.reflect.TypeToken;

import net.coreprotect.CoreProtect;
import net.coreprotect.config.Config;
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.consumer.Consumer;
import net.coreprotect.consumer.process.Process;
import net.coreprotect.database.Database;
import net.coreprotect.utility.ItemUtils;
import net.coreprotect.utility.MaterialUtils;
import net.coreprotect.utility.serialize.ItemMetaHandler;
import net.coreprotect.utility.serialize.JsonSerialization;
import net.coreprotect.utility.serialize.SerializedBlockMeta;
import net.coreprotect.utility.serialize.SerializedItem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public final class PlayProMetadataRepairCommand {

    private static final int BATCH_SIZE = 1000;
    private static final java.lang.reflect.Type LEGACY_META_TYPE = new TypeToken<List<List<Map<String, Object>>>>(){}.getType();
    private static final String EVENT_KEY_COLUMNS = "rowid,toString(producer_id),producer_sequence,toString(batch_id),batch_ordinal";
    private static final String EVENT_KEY_ORDER = "rowid,toString(producer_id),producer_sequence,toString(batch_id),batch_ordinal";
    private static final String EVENT_KEY_CURSOR = "tuple(rowid,toString(producer_id),producer_sequence,toString(batch_id),batch_ordinal)>tuple(toUInt64(?),toString(?),toUInt64(?),toString(?),toUInt32(?))";

    private PlayProMetadataRepairCommand() {
        throw new IllegalStateException("Command class");
    }

    protected static void runCommand(CommandSender sender, boolean permission, String[] args) {
        if (!permission) {
            return;
        }
        if (!Config.getGlobal().MYSQL) {
            error(sender, "This repair is only available for the ClickHouse storage backend.");
            return;
        }
        if (!Bukkit.getOnlinePlayers().isEmpty()) {
            error(sender, "Run this repair during maintenance with no players online.");
            return;
        }
        if (ConfigHandler.converterRunning || ConfigHandler.migrationRunning || ConfigHandler.purgeRunning) {
            error(sender, "Another database operation is already running.");
            return;
        }

        RepairOptions options;
        try {
            options = RepairOptions.parse(args);
        }
        catch (IllegalArgumentException e) {
            error(sender, e.getMessage());
            usage(sender);
            return;
        }

        ConfigHandler.migrationRunning = true;
        Thread repairThread = new Thread(() -> runRepair(sender, options));
        repairThread.setName("CoreProtect PlayPro Metadata Repair");
        repairThread.setUncaughtExceptionHandler((thread, throwable) -> {
            CoreProtect.getInstance().getSLF4JLogger().error("Unhandled PlayPro metadata repair failure", throwable);
            ConfigHandler.migrationRunning = false;
            error(sender, "Repair failed unexpectedly. Logging remains paused; keep the server in maintenance and see console for details.");
        });
        repairThread.start();
        ok(sender, "Started PlayPro compatibility repair in " + options.database + " using prefix " + options.prefix + ".");
    }

    private static void runRepair(CommandSender sender, RepairOptions options) {
        boolean success = false;
        try {
            waitForConsumerDrain(sender);
            ConfigHandler.pauseConsumer = true;
            ok(sender, "Logging is paused while PlayPro compatibility data is repaired.");

            try (Connection connection = Database.getConnection(true, 30000)) {
                if (connection == null) {
                    throw new SQLException("Unable to open ClickHouse connection");
                }
                repair(connection, options.database, options.prefix, sender);
            }

            success = true;
            ok(sender, "PlayPro compatibility repair completed. Stop the server and switch back to official PlayPro/CoreProtect.");
        }
        catch (Exception e) {
            CoreProtect.getInstance().getSLF4JLogger().error("PlayPro metadata repair failed", e);
            error(sender, "Repair failed: " + e.getMessage());
            error(sender, "Logging remains paused. Keep the server in maintenance until repair succeeds.");
        }
        finally {
            ConfigHandler.migrationRunning = false;
        }
    }

    static void repair(Connection connection, String database, String prefix, CommandSender sender) throws SQLException {
        String eventTableName = prefix + "event_data";
        String eventTable = qualified(database, eventTableName);
        if (!tableExists(connection, database, eventTableName)) {
            throw new SQLException("Missing official PlayPro event table: " + eventTableName);
        }
        killPendingMutations(connection, database, eventTableName);
        dropStaleRebuildTables(connection, database, eventTableName);

        String rawFixTable = prefix + "metadata_fix_" + System.currentTimeMillis();
        String fixTable = qualified(database, rawFixTable);
        createFixTable(connection, fixTable);
        try {
            long repaired = 0;
            repaired += repairIsoBlobRows(connection, sender, eventTable, fixTable, "block", "meta");
            repaired += repairIsoBlobRows(connection, sender, eventTable, fixTable, "container", "metadata");
            repaired += repairIsoBlobRows(connection, sender, eventTable, fixTable, "entity_container", "metadata");
            repaired += repairIsoBlobRows(connection, sender, eventTable, fixTable, "item", "payload");
            repaired += repairIsoBlobRows(connection, sender, eventTable, fixTable, "entity", "payload");
            repaired += repairIsoBlobRows(connection, sender, eventTable, fixTable, "entity_spawn", "entity_data");
            repaired += repairJsonBlockMetaRows(connection, sender, eventTable, fixTable);
            repaired += repairJsonItemRows(connection, sender, eventTable, fixTable, "container", "metadata");
            repaired += repairJsonItemRows(connection, sender, eventTable, fixTable, "item", "payload");
            repaired += repairLegacyMetadataRows(connection, sender, eventTable, fixTable, "entity_container", "metadata");
            repaired += repairBase64Rows(connection, sender, eventTable, fixTable, "entity_interaction", "metadata");
            repaired += repairLegacyEntityDataRows(connection, sender, eventTable, fixTable, "entity", "payload");
            repaired += repairLegacyEntityDataRows(connection, sender, eventTable, fixTable, "entity_spawn", "entity_data");
            if (repaired > 0) {
                rebuildEventTable(connection, sender, database, eventTableName, eventTable, fixTable);
            }
            PlayProMigrationCommand.recreateCompatibilityViews(connection, database, prefix);
            ok(sender, "Recreated official PlayPro compatibility views.");

            long remainingLegacyRows = countRemainingLegacyRows(connection, eventTable);
            if (remainingLegacyRows > 0) {
                throw new SQLException("Still found " + remainingLegacyRows + " incompatible PlayPro rows after repair");
            }
            ok(sender, "Repaired " + repaired + " PlayPro compatibility rows.");
        }
        finally {
            dropTable(connection, fixTable);
        }
    }

    private static long repairIsoBlobRows(Connection connection, CommandSender sender, String eventTable, String fixTable, String family, String column) throws SQLException {
        long total = 0;
        EventKey cursor = EventKey.START;
        while (true) {
            List<FixRow> rows = new ArrayList<>();
            String sql = "SELECT " + EVENT_KEY_COLUMNS + "," + quote(column) + " FROM " + eventTable + " FINAL "
                    + "WHERE family=? AND " + EVENT_KEY_CURSOR + " AND " + quote(column) + " IS NOT NULL "
                    + "AND startsWith(hex(" + quote(column) + "), 'C2ACC3AD') ORDER BY " + EVENT_KEY_ORDER + " LIMIT " + BATCH_SIZE;
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, family);
                bindCursor(statement, 2, cursor);
                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        EventKey key = readEventKey(resultSet);
                        cursor = key;
                        byte[] restored;
                        try {
                            restored = restoreIsoBlob(resultSet.getString(6));
                        }
                        catch (IllegalArgumentException exception) {
                            throw new SQLException("Invalid legacy binary stream in " + family + "." + column + " at rowid " + key.rowId);
                        }
                        rows.add(FixRow.forColumn(key, family, column, restored));
                    }
                }
            }
            if (rows.isEmpty()) {
                return total;
            }
            writeFixRows(connection, fixTable, rows);
            total += rows.size();
            ok(sender, "Prepared " + total + " legacy " + family + "." + column + " rows...");
        }
    }

    private static long repairJsonBlockMetaRows(Connection connection, CommandSender sender, String eventTable, String fixTable) throws SQLException {
        long total = 0;
        EventKey cursor = EventKey.START;
        while (true) {
            List<FixRow> rows = new ArrayList<>();
            String sql = "SELECT " + EVENT_KEY_COLUMNS + ",meta FROM " + eventTable + " FINAL "
                    + "WHERE family='block' AND " + EVENT_KEY_CURSOR + " AND meta IS NOT NULL AND startsWith(meta, '{') "
                    + "ORDER BY " + EVENT_KEY_ORDER + " LIMIT " + BATCH_SIZE;
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                bindCursor(statement, 1, cursor);
                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        EventKey key = readEventKey(resultSet);
                        cursor = key;
                        try {
                            rows.add(FixRow.forColumn(key, "block", "meta", convertBlockMeta(resultSet.getString(6))));
                        }
                        catch (Exception exception) {
                            throw new SQLException("Unable to convert block metadata at rowid " + key.rowId, exception);
                        }
                    }
                }
            }
            if (rows.isEmpty()) {
                return total;
            }
            writeFixRows(connection, fixTable, rows);
            total += rows.size();
            ok(sender, "Prepared " + total + " block.meta rows...");
        }
    }

    private static long repairJsonItemRows(Connection connection, CommandSender sender, String eventTable, String fixTable, String family, String column) throws SQLException {
        long total = 0;
        EventKey cursor = EventKey.START;
        while (true) {
            List<FixRow> rows = new ArrayList<>();
            String sql = "SELECT " + EVENT_KEY_COLUMNS + ",type,amount," + quote(column) + " FROM " + eventTable + " FINAL "
                    + "WHERE family=? AND " + EVENT_KEY_CURSOR + " AND " + quote(column) + " IS NOT NULL AND startsWith(" + quote(column) + ", '{') "
                    + "ORDER BY " + EVENT_KEY_ORDER + " LIMIT " + BATCH_SIZE;
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, family);
                bindCursor(statement, 2, cursor);
                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        EventKey key = readEventKey(resultSet);
                        cursor = key;
                        try {
                            byte[] converted = convertSerializedItem(resultSet.getString(8), resultSet.getInt(6), resultSet.getInt(7));
                            rows.add(FixRow.forColumn(key, family, column, converted));
                        }
                        catch (Exception exception) {
                            throw new SQLException("Unable to convert " + family + "." + column + " at rowid " + key.rowId, exception);
                        }
                    }
                }
            }
            if (rows.isEmpty()) {
                return total;
            }
            writeFixRows(connection, fixTable, rows);
            total += rows.size();
            ok(sender, "Prepared " + total + " " + family + "." + column + " rows...");
        }
    }

    private static long repairLegacyMetadataRows(Connection connection, CommandSender sender, String eventTable, String fixTable, String family, String column) throws SQLException {
        long total = 0;
        EventKey cursor = EventKey.START;
        while (true) {
            List<FixRow> rows = new ArrayList<>();
            String sql = "SELECT " + EVENT_KEY_COLUMNS + "," + quote(column) + " FROM " + eventTable + " FINAL "
                    + "WHERE family=? AND " + EVENT_KEY_CURSOR + " AND " + quote(column) + " IS NOT NULL AND startsWith(" + quote(column) + ", '[') "
                    + "ORDER BY " + EVENT_KEY_ORDER + " LIMIT " + BATCH_SIZE;
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, family);
                bindCursor(statement, 2, cursor);
                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        EventKey key = readEventKey(resultSet);
                        cursor = key;
                        try {
                            List<List<Map<String, Object>>> metadata = JsonSerialization.GSON.fromJson(resultSet.getString(6), LEGACY_META_TYPE);
                            if (metadata == null) {
                                throw new IllegalArgumentException("Metadata JSON is empty");
                            }
                            byte[] converted = ItemUtils.convertByteData(normalizeJsonValue(metadata));
                            if (converted == null) {
                                throw new IllegalArgumentException("Unable to serialize converted metadata");
                            }
                            rows.add(FixRow.forColumn(key, family, column, converted));
                        }
                        catch (Exception exception) {
                            throw new SQLException("Unable to convert " + family + "." + column + " at rowid " + key.rowId, exception);
                        }
                    }
                }
            }
            if (rows.isEmpty()) {
                return total;
            }
            writeFixRows(connection, fixTable, rows);
            total += rows.size();
            ok(sender, "Prepared " + total + " " + family + "." + column + " rows...");
        }
    }

    private static long repairBase64Rows(Connection connection, CommandSender sender, String eventTable, String fixTable, String family, String column) throws SQLException {
        long total = 0;
        EventKey cursor = EventKey.START;
        while (true) {
            List<FixRow> rows = new ArrayList<>();
            String sql = "SELECT " + EVENT_KEY_COLUMNS + "," + quote(column) + " FROM " + eventTable + " FINAL "
                    + "WHERE family=? AND " + EVENT_KEY_CURSOR + " AND " + quote(column) + " IS NOT NULL AND match(" + quote(column) + ", '^[A-Za-z0-9+/]+={0,2}$') "
                    + "ORDER BY " + EVENT_KEY_ORDER + " LIMIT " + BATCH_SIZE;
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, family);
                bindCursor(statement, 2, cursor);
                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        EventKey key = readEventKey(resultSet);
                        cursor = key;
                        byte[] decoded = decodeBase64OrNull(resultSet.getString(6));
                        if (decoded != null) {
                            rows.add(FixRow.forColumn(key, family, column, decoded));
                        }
                    }
                }
            }
            if (rows.isEmpty()) {
                return total;
            }
            writeFixRows(connection, fixTable, rows);
            total += rows.size();
            ok(sender, "Prepared " + total + " " + family + "." + column + " rows...");
        }
    }

    private static long repairLegacyEntityDataRows(Connection connection, CommandSender sender, String eventTable, String fixTable, String family, String column) throws SQLException {
        long total = 0;
        EventKey cursor = EventKey.START;
        while (true) {
            List<FixRow> rows = new ArrayList<>();
            String sql = "SELECT " + EVENT_KEY_COLUMNS + "," + quote(column) + " FROM " + eventTable + " FINAL "
                    + "WHERE family=? AND " + EVENT_KEY_CURSOR + " AND " + quote(column) + " IS NOT NULL "
                    + "AND NOT startsWith(hex(" + quote(column) + "), 'ACED') AND NOT startsWith(hex(" + quote(column) + "), 'C2ACC3AD') "
                    + "ORDER BY " + EVENT_KEY_ORDER + " LIMIT " + BATCH_SIZE;
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, family);
                bindCursor(statement, 2, cursor);
                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        EventKey key = readEventKey(resultSet);
                        cursor = key;
                        try {
                            byte[] converted = convertLegacyEntityData(family, resultSet.getString(6));
                            rows.add(FixRow.forColumn(key, family, column, converted));
                        }
                        catch (Exception exception) {
                            throw new SQLException("Unable to convert " + family + "." + column + " at rowid " + key.rowId, exception);
                        }
                    }
                }
            }
            if (rows.isEmpty()) {
                return total;
            }
            writeFixRows(connection, fixTable, rows);
            total += rows.size();
            ok(sender, "Prepared " + total + " " + family + "." + column + " rows...");
        }
    }

    private static byte[] convertSerializedItem(String itemData, int typeId, int amount) {
        Material type = MaterialUtils.getType(typeId);
        SerializedItem item = ItemUtils.deserializeItem(itemData, type, amount);
        if (item == null || item.itemStack() == null) {
            throw new IllegalArgumentException("Unable to deserialize item JSON for material id " + typeId);
        }

        String faceData = item.faceData() == null ? null : item.faceData().name();
        List<List<Map<String, Object>>> metadata = ItemMetaHandler.serialize(item.itemStack(), null, faceData, item.slot() == null ? -1 : item.slot());
        if (metadata.isEmpty()) {
            return null;
        }
        return ItemUtils.convertByteData(metadata);
    }

    static byte[] convertBlockMeta(String metadata) {
        SerializedBlockMeta blockMeta = JsonSerialization.GSON.fromJson(metadata, SerializedBlockMeta.class);
        if (blockMeta == null) {
            throw new IllegalArgumentException("Block metadata JSON is empty");
        }

        List<Object> values = new ArrayList<>();
        if (blockMeta.command() != null && !blockMeta.command().isEmpty()) {
            values.add(blockMeta.command());
        }
        else if (blockMeta.bannerData() != null) {
            values.add(blockMeta.bannerData().baseColor());
            for (org.bukkit.block.banner.Pattern pattern : blockMeta.bannerData().patterns()) {
                values.add(pattern.serialize());
            }
        }
        else if (blockMeta.items() != null) {
            for (SerializedItem item : blockMeta.items()) {
                if (item == null || item.itemStack() == null || item.itemStack().isEmpty()) {
                    continue;
                }
                String faceData = item.faceData() == null ? null : item.faceData().name();
                Map<Integer, Object> itemData = ItemUtils.serializeItemStackLegacy(item.itemStack(), faceData, item.slot() == null ? -1 : item.slot());
                if (!itemData.isEmpty()) {
                    values.add(itemData);
                }
            }
        }
        if (values.isEmpty()) {
            return null;
        }
        byte[] converted = ItemUtils.convertByteData(values);
        if (converted == null) {
            throw new IllegalArgumentException("Unable to serialize converted block metadata");
        }
        return converted;
    }

    private static byte[] decodeBase64OrNull(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            return Base64.getDecoder().decode(value);
        }
        catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    static byte[] restoreIsoBlob(String value) {
        byte[] restored = value == null ? new byte[0] : value.getBytes(StandardCharsets.ISO_8859_1);
        if (restored.length < 2 || restored[0] != (byte) 0xAC || restored[1] != (byte) 0xED) {
            throw new IllegalArgumentException("Invalid Java serialization stream");
        }
        return restored;
    }

    static byte[] convertLegacyEntityData(String family, String data) throws Exception {
        if (data == null || data.isEmpty()) {
            return null;
        }
        if (data.startsWith("[")) {
            @SuppressWarnings("unchecked")
            List<Object> entityData = JsonSerialization.GSON.fromJson(data, List.class);
            if (entityData == null) {
                throw new IllegalArgumentException("Entity JSON did not contain a list");
            }
            return serializeOfficialData(normalizeJsonValue(entityData));
        }
        if ("entity_spawn".equals(family)) {
            throw new IllegalArgumentException("Unsupported entity_spawn state format");
        }

        // The fork stored ordinary entity deaths as SNBT, while PlayPro expects a
        // serialized legacy list. Preserve a valid default-state record so the
        // entity remains rollbackable; the original SNBT stays in the backup table.
        List<Object> defaultState = new ArrayList<>();
        defaultState.add(new ArrayList<>());
        defaultState.add(new ArrayList<>());
        defaultState.add(new ArrayList<>());
        defaultState.add(Boolean.FALSE);
        defaultState.add(null);
        defaultState.add(new ArrayList<>());
        defaultState.add(new ArrayList<>());
        return serializeOfficialData(defaultState);
    }

    private static byte[] serializeOfficialData(Object data) throws Exception {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream(); BukkitObjectOutputStream objectOutput = new BukkitObjectOutputStream(output)) {
            objectOutput.writeObject(data);
            objectOutput.flush();
            return output.toByteArray();
        }
    }

    static Object normalizeJsonValue(Object value) {
        if (value instanceof Double number && Double.isFinite(number) && number == Math.rint(number)) {
            if (number >= Integer.MIN_VALUE && number <= Integer.MAX_VALUE) {
                return number.intValue();
            }
            if (number >= Long.MIN_VALUE && number <= Long.MAX_VALUE) {
                return number.longValue();
            }
        }
        if (value instanceof List<?> list) {
            List<Object> result = new ArrayList<>(list.size());
            for (Object item : list) {
                result.add(normalizeJsonValue(item));
            }
            return result;
        }
        if (value instanceof Map<?, ?> map) {
            Map<Object, Object> result = new LinkedHashMap<>(map.size());
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                result.put(normalizeJsonValue(entry.getKey()), normalizeJsonValue(entry.getValue()));
            }
            return result;
        }
        return value;
    }

    private static void bindCursor(PreparedStatement statement, int startIndex, EventKey cursor) throws SQLException {
        statement.setLong(startIndex, cursor.rowId);
        statement.setString(startIndex + 1, cursor.producerId);
        statement.setLong(startIndex + 2, cursor.producerSequence);
        statement.setString(startIndex + 3, cursor.batchId);
        statement.setInt(startIndex + 4, cursor.batchOrdinal);
    }

    private static EventKey readEventKey(ResultSet resultSet) throws SQLException {
        return new EventKey(resultSet.getLong(1), resultSet.getString(2), resultSet.getLong(3), resultSet.getString(4), resultSet.getInt(5));
    }

    private static void createFixTable(Connection connection, String fixTable) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE " + fixTable + " (family String,rowid UInt64,producer_id String,producer_sequence UInt64,batch_id String,batch_ordinal UInt32,meta_hex Nullable(String),metadata_hex Nullable(String),payload_hex Nullable(String),entity_data_hex Nullable(String)) ENGINE = MergeTree ORDER BY (family,rowid,producer_id,producer_sequence,batch_id,batch_ordinal)");
        }
    }

    private static void writeFixRows(Connection connection, String fixTable, List<FixRow> rows) throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement("INSERT INTO " + fixTable + " (family,rowid,producer_id,producer_sequence,batch_id,batch_ordinal,meta_hex,metadata_hex,payload_hex,entity_data_hex) VALUES (?,?,?,?,?,?,?,?,?,?)")) {
            for (FixRow row : rows) {
                insert.setString(1, row.family);
                insert.setLong(2, row.rowId);
                insert.setString(3, row.producerId);
                insert.setLong(4, row.producerSequence);
                insert.setString(5, row.batchId);
                insert.setInt(6, row.batchOrdinal);
                if (row.metaHex == null) {
                    insert.setNull(7, java.sql.Types.VARCHAR);
                }
                else {
                    insert.setString(7, row.metaHex);
                }
                if (row.metadataHex == null) {
                    insert.setNull(8, java.sql.Types.VARCHAR);
                }
                else {
                    insert.setString(8, row.metadataHex);
                }
                if (row.payloadHex == null) {
                    insert.setNull(9, java.sql.Types.VARCHAR);
                }
                else {
                    insert.setString(9, row.payloadHex);
                }
                if (row.entityDataHex == null) {
                    insert.setNull(10, java.sql.Types.VARCHAR);
                }
                else {
                    insert.setString(10, row.entityDataHex);
                }
                insert.addBatch();
            }
            insert.executeBatch();
        }
    }

    private static void rebuildEventTable(Connection connection, CommandSender sender, String database, String eventTableName, String eventTable, String fixTable) throws SQLException {
        String timestamp = String.valueOf(System.currentTimeMillis());
        String rebuildTable = qualified(database, eventTableName + "_repair_" + timestamp);
        String backupTable = qualified(database, eventTableName + "_backup_repair_" + timestamp);
        long sourceRows = countFinalRows(connection, eventTable);
        ok(sender, "Rebuilding " + eventTableName + " with repaired metadata. This can take a while...");
        try (Statement statement = connection.createStatement()) {
            statement.execute(createTableLikeSql(connection, eventTable, rebuildTable));
            String sql = rebuildInsertSql(connection, database, eventTableName, eventTable, rebuildTable, fixTable);
            CoreProtect.getInstance().getSLF4JLogger().info("[PlayPro metadata repair] Copying repaired event_data into {}.", rebuildTable);
            statement.execute(sql);
            long rebuiltRows = countFinalRows(connection, rebuildTable);
            if (sourceRows != rebuiltRows) {
                throw new SQLException("Repaired event_data FINAL row mismatch: source=" + sourceRows + ", rebuilt=" + rebuiltRows);
            }
            CoreProtect.getInstance().getSLF4JLogger().info("[PlayPro metadata repair] Swapping {} with repaired table. Backup table: {}.", eventTable, backupTable);
            statement.execute("RENAME TABLE " + eventTable + " TO " + backupTable + ", " + rebuildTable + " TO " + eventTable);
        }
        catch (SQLException exception) {
            dropTable(connection, rebuildTable);
            throw exception;
        }
        ok(sender, "Rebuilt " + eventTableName + ". Backup table: " + backupTable + ".");
    }

    private static String createTableLikeSql(Connection connection, String sourceTable, String targetTable) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet resultSet = statement.executeQuery("SHOW CREATE TABLE " + sourceTable)) {
            if (!resultSet.next()) {
                throw new SQLException("ClickHouse did not return CREATE TABLE for " + sourceTable);
            }
            String createTable = resultSet.getString(1);
            int definitionStart = createTable.indexOf('(');
            if (definitionStart < 0) {
                throw new SQLException("Unable to parse CREATE TABLE for " + sourceTable);
            }
            return "CREATE TABLE " + targetTable + " " + createTable.substring(definitionStart);
        }
    }

    private static String rebuildInsertSql(Connection connection, String database, String eventTableName, String eventTable, String rebuildTable, String fixTable) throws SQLException {
        List<String> columns = orderedColumns(connection, database, eventTableName);
        StringBuilder columnList = new StringBuilder();
        StringBuilder projection = new StringBuilder();
        for (String column : columns) {
            if (columnList.length() > 0) {
                columnList.append(',');
                projection.append(',');
            }
            columnList.append(quote(column));
            if (column.equals("meta")) {
                projection.append("if(isNull(f.meta_hex),e.").append(quote(column)).append(",").append(hexExpression("f.meta_hex")).append(") AS ").append(quote(column));
            }
            else if (column.equals("metadata")) {
                projection.append("if(isNull(f.metadata_hex),e.").append(quote(column)).append(",").append(hexExpression("f.metadata_hex")).append(") AS ").append(quote(column));
            }
            else if (column.equals("payload")) {
                projection.append("if(isNull(f.payload_hex),e.").append(quote(column)).append(",").append(hexExpression("f.payload_hex")).append(") AS ").append(quote(column));
            }
            else if (column.equals("entity_data")) {
                projection.append("if(isNull(f.entity_data_hex),e.").append(quote(column)).append(",").append(hexExpression("f.entity_data_hex")).append(") AS ").append(quote(column));
            }
            else {
                projection.append("e.").append(quote(column));
            }
        }

        return "INSERT INTO " + rebuildTable + " (" + columnList + ") SELECT " + projection
                + " FROM " + eventTable + " FINAL AS e LEFT JOIN (SELECT family,rowid,producer_id,producer_sequence,batch_id,batch_ordinal,any(meta_hex) AS meta_hex,any(metadata_hex) AS metadata_hex,any(payload_hex) AS payload_hex,any(entity_data_hex) AS entity_data_hex FROM "
                + fixTable + " GROUP BY family,rowid,producer_id,producer_sequence,batch_id,batch_ordinal) AS f ON toString(e.family)=f.family AND e.rowid=f.rowid"
                + " AND toString(e.producer_id)=f.producer_id AND e.producer_sequence=f.producer_sequence"
                + " AND toString(e.batch_id)=f.batch_id AND e.batch_ordinal=f.batch_ordinal";
    }

    private static List<String> orderedColumns(Connection connection, String database, String table) throws SQLException {
        List<String> columns = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("SELECT name FROM system.columns WHERE database=? AND table=? ORDER BY position")) {
            statement.setString(1, database);
            statement.setString(2, table);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    columns.add(resultSet.getString(1));
                }
            }
        }
        if (columns.isEmpty()) {
            throw new SQLException("Unable to load columns for " + table);
        }
        return columns;
    }

    private static void waitForConsumerDrain(CommandSender sender) throws InterruptedException {
        ok(sender, "Waiting for the current consumer queue to drain...");
        long start = System.currentTimeMillis();
        while (Consumer.getConsumerSize(0) > 0 || Consumer.getConsumerSize(1) > 0 || Process.getCurrentConsumerSize() > 0) {
            if (System.currentTimeMillis() - start > 300000L) {
                throw new IllegalStateException("Timed out while waiting for the consumer queue to drain");
            }
            Thread.sleep(500L);
        }
    }

    private static boolean tableExists(Connection connection, String database, String table) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT 1 FROM system.tables WHERE database=? AND name=? LIMIT 1")) {
            statement.setString(1, database);
            statement.setString(2, table);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    private static long countFinalRows(Connection connection, String table) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet resultSet = statement.executeQuery("SELECT count() FROM " + table + " FINAL")) {
            if (!resultSet.next()) {
                throw new SQLException("ClickHouse did not return a FINAL row count for " + table);
            }
            return resultSet.getLong(1);
        }
    }

    private static void dropStaleRebuildTables(Connection connection, String database, String eventTableName) throws SQLException {
        List<String> staleTables = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("SELECT name FROM system.tables WHERE database=? AND startsWith(name,?)")) {
            statement.setString(1, database);
            statement.setString(2, eventTableName + "_repair_");
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    staleTables.add(resultSet.getString(1));
                }
            }
        }
        for (String table : staleTables) {
            String qualifiedTable = qualified(database, table);
            CoreProtect.getInstance().getSLF4JLogger().info("[PlayPro metadata repair] Dropping stale incomplete rebuild table {}.", qualifiedTable);
            try (Statement statement = connection.createStatement()) {
                statement.execute("DROP TABLE " + qualifiedTable);
            }
        }
    }

    private static long countRemainingLegacyRows(Connection connection, String eventTable) throws SQLException {
        String sql = "SELECT count() FROM " + eventTable + " FINAL WHERE "
                + "(family='block' AND meta IS NOT NULL AND startsWith(meta,'{')) OR "
                + "(family='container' AND metadata IS NOT NULL AND startsWith(metadata,'{')) OR "
                + "(family='item' AND payload IS NOT NULL AND startsWith(payload,'{')) OR "
                + "(family='entity_container' AND metadata IS NOT NULL AND startsWith(metadata,'[')) OR "
                + "(family='entity_interaction' AND metadata IS NOT NULL AND metadata!='' AND match(metadata,'^[A-Za-z0-9+/]+={0,2}$')) OR "
                + "(family='entity' AND payload IS NOT NULL AND NOT startsWith(hex(payload),'ACED')) OR "
                + "(family='entity_spawn' AND entity_data IS NOT NULL AND NOT startsWith(hex(entity_data),'ACED')) OR "
                + "startsWith(hex(ifNull(meta,'')),'C2ACC3AD') OR startsWith(hex(ifNull(metadata,'')),'C2ACC3AD') OR "
                + "startsWith(hex(ifNull(payload,'')),'C2ACC3AD') OR startsWith(hex(ifNull(entity_data,'')),'C2ACC3AD')";
        try (Statement statement = connection.createStatement(); ResultSet resultSet = statement.executeQuery(sql)) {
            return resultSet.next() ? resultSet.getLong(1) : 0;
        }
    }

    private static void dropTable(Connection connection, String table) {
        try (Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS " + table);
        }
        catch (SQLException e) {
            CoreProtect.getInstance().getSLF4JLogger().warn("[PlayPro metadata repair] Unable to drop temporary table {}", table, e);
        }
    }

    private static void killPendingMutations(Connection connection, String database, String table) {
        String sql = "KILL MUTATION WHERE database=" + sqlString(database) + " AND table=" + sqlString(table) + " AND is_done=0 SYNC";
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
        catch (SQLException e) {
            CoreProtect.getInstance().getSLF4JLogger().warn("[PlayPro metadata repair] Unable to kill pending mutations for {}.{}; continuing with table rebuild.", database, table, e);
        }
    }

    private static String qualified(String database, String table) {
        return quote(database) + "." + quote(table);
    }

    private static String quote(String identifier) {
        return "`" + identifier + "`";
    }

    private static String sqlString(String value) {
        return "'" + value.replace("'", "''") + "'";
    }

    private static String hexExpression(String value) {
        return "if(" + value + "='',CAST(NULL,'Nullable(String)'),CAST(unhex(" + value + "),'Nullable(String)'))";
    }

    private static String hex(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return "";
        }
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            builder.append(Character.forDigit((value >> 4) & 0xF, 16));
            builder.append(Character.forDigit(value & 0xF, 16));
        }
        return builder.toString();
    }

    private static void ok(CommandSender sender, String message) {
        sender.sendMessage(Component.text(message, NamedTextColor.GREEN));
        CoreProtect.getInstance().getSLF4JLogger().info("[PlayPro metadata repair] {}", message);
    }

    private static void error(CommandSender sender, String message) {
        sender.sendMessage(Component.text(message, NamedTextColor.RED));
        CoreProtect.getInstance().getSLF4JLogger().warn("[PlayPro metadata repair] {}", message);
    }

    private static void usage(CommandSender sender) {
        sender.sendMessage(Component.text("Usage: /co repair-playpro-items [database:coreprotect_art] [prefix:co_]", NamedTextColor.YELLOW));
    }

    private record EventKey(long rowId, String producerId, long producerSequence, String batchId, int batchOrdinal) {

        private static final EventKey START = new EventKey(0L, "", 0L, "", 0);
    }

    private record FixRow(String family, long rowId, String producerId, long producerSequence, String batchId, int batchOrdinal,
            String metaHex, String metadataHex, String payloadHex, String entityDataHex) {

        private static FixRow forColumn(EventKey key, String family, String column, byte[] bytes) {
            String encoded = hex(bytes);
            if (column.equals("meta")) {
                return new FixRow(family, key.rowId, key.producerId, key.producerSequence, key.batchId, key.batchOrdinal, encoded, null, null, null);
            }
            if (column.equals("payload")) {
                return new FixRow(family, key.rowId, key.producerId, key.producerSequence, key.batchId, key.batchOrdinal, null, null, encoded, null);
            }
            if (column.equals("entity_data")) {
                return new FixRow(family, key.rowId, key.producerId, key.producerSequence, key.batchId, key.batchOrdinal, null, null, null, encoded);
            }
            return new FixRow(family, key.rowId, key.producerId, key.producerSequence, key.batchId, key.batchOrdinal, null, encoded, null, null);
        }
    }

    private record RepairOptions(String database, String prefix) {

        private static RepairOptions parse(String[] commandArgs) {
            String database = ConfigHandler.database;
            String prefix = ConfigHandler.prefix;

            for (int i = 1; i < commandArgs.length; i++) {
                String arg = commandArgs[i].trim();
                if (arg.isEmpty()) {
                    continue;
                }
                String[] split = arg.split(":", 2);
                if (split.length != 2) {
                    throw new IllegalArgumentException("Invalid argument: " + arg);
                }
                String key = split[0].toLowerCase(Locale.ROOT);
                String value = split[1];
                switch (key) {
                    case "database" -> database = value;
                    case "prefix" -> prefix = value;
                    default -> throw new IllegalArgumentException("Unknown argument: " + key);
                }
            }

            validateIdentifier(database, "database");
            validatePrefix(prefix, "prefix");
            return new RepairOptions(database, prefix);
        }

        private static void validateIdentifier(String value, String name) {
            if (value == null || !value.matches("[A-Za-z_][A-Za-z0-9_]*")) {
                throw new IllegalArgumentException("Invalid " + name + ": " + value);
            }
        }

        private static void validatePrefix(String value, String name) {
            if (value == null) {
                throw new IllegalArgumentException("Invalid " + name + ": null");
            }
            if (!value.isEmpty() && !value.matches("[A-Za-z_][A-Za-z0-9_]*")) {
                throw new IllegalArgumentException("Invalid " + name + ": " + value);
            }
        }
    }
}
