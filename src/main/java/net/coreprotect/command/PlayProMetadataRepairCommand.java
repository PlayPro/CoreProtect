package net.coreprotect.command;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.Material;
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
import net.coreprotect.utility.serialize.SerializedItem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public final class PlayProMetadataRepairCommand {

    private static final int BATCH_SIZE = 50;
    private static final java.lang.reflect.Type LEGACY_META_TYPE = new TypeToken<List<List<Map<String, Object>>>>(){}.getType();

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
        ok(sender, "Started PlayPro item metadata repair in " + options.database + " using prefix " + options.prefix + ".");
    }

    private static void runRepair(CommandSender sender, RepairOptions options) {
        boolean success = false;
        try {
            waitForConsumerDrain(sender);
            ConfigHandler.pauseConsumer = true;
            ok(sender, "Logging is paused while item metadata is repaired.");

            try (Connection connection = Database.getConnection(true, 30000)) {
                if (connection == null) {
                    throw new SQLException("Unable to open ClickHouse connection");
                }
                repair(connection, options.database, options.prefix, sender);
            }

            success = true;
            ok(sender, "PlayPro item metadata repair completed. Stop the server and switch back to official PlayPro/CoreProtect.");
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
        String eventTable = qualified(database, prefix + "event_data");
        if (!tableExists(connection, database, prefix + "event_data")) {
            throw new SQLException("Missing official PlayPro event table: " + prefix + "event_data");
        }

        long repaired = 0;
        repaired += repairJsonItemRows(connection, sender, eventTable, database, prefix, "container", "metadata");
        repaired += repairJsonItemRows(connection, sender, eventTable, database, prefix, "item", "payload");
        repaired += repairLegacyMetadataRows(connection, sender, eventTable, database, prefix, "entity_container", "metadata");
        repaired += repairBase64Rows(connection, sender, eventTable, database, prefix, "entity_interaction", "metadata");
        long remainingLegacyRows = countRemainingLegacyRows(connection, eventTable);
        if (remainingLegacyRows > 0) {
            throw new SQLException("Still found " + remainingLegacyRows + " legacy item metadata rows after repair");
        }
        ok(sender, "Repaired " + repaired + " PlayPro item metadata rows.");
    }

    private static long repairJsonItemRows(Connection connection, CommandSender sender, String eventTable, String database, String prefix, String family, String column) throws SQLException {
        long total = 0;
        long lastRowId = 0;
        while (true) {
            List<FixRow> rows = new ArrayList<>();
            String sql = "SELECT rowid,type,amount," + quote(column) + " FROM " + eventTable + " "
                    + "WHERE family=? AND rowid>? AND " + quote(column) + " IS NOT NULL AND startsWith(" + quote(column) + ", '{') "
                    + "ORDER BY rowid LIMIT " + BATCH_SIZE;
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, family);
                statement.setLong(2, lastRowId);
                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        long rowId = resultSet.getLong(1);
                        lastRowId = rowId;
                        byte[] converted = convertSerializedItem(resultSet.getString(4), resultSet.getInt(2), resultSet.getInt(3));
                        rows.add(FixRow.forColumn(family, rowId, column, converted));
                    }
                }
            }
            if (rows.isEmpty()) {
                return total;
            }
            mutateFixRows(connection, eventTable, database, prefix, rows);
            total += rows.size();
            ok(sender, "Repaired " + total + " " + family + "." + column + " rows...");
        }
    }

    private static long repairLegacyMetadataRows(Connection connection, CommandSender sender, String eventTable, String database, String prefix, String family, String column) throws SQLException {
        long total = 0;
        long lastRowId = 0;
        while (true) {
            List<FixRow> rows = new ArrayList<>();
            String sql = "SELECT rowid," + quote(column) + " FROM " + eventTable + " "
                    + "WHERE family=? AND rowid>? AND " + quote(column) + " IS NOT NULL AND startsWith(" + quote(column) + ", '[') "
                    + "ORDER BY rowid LIMIT " + BATCH_SIZE;
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, family);
                statement.setLong(2, lastRowId);
                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        long rowId = resultSet.getLong(1);
                        lastRowId = rowId;
                        List<List<Map<String, Object>>> metadata = JsonSerialization.GSON.fromJson(resultSet.getString(2), LEGACY_META_TYPE);
                        rows.add(FixRow.forColumn(family, rowId, column, ItemUtils.convertByteData(metadata)));
                    }
                }
            }
            if (rows.isEmpty()) {
                return total;
            }
            mutateFixRows(connection, eventTable, database, prefix, rows);
            total += rows.size();
            ok(sender, "Repaired " + total + " " + family + "." + column + " rows...");
        }
    }

    private static long repairBase64Rows(Connection connection, CommandSender sender, String eventTable, String database, String prefix, String family, String column) throws SQLException {
        long total = 0;
        long lastRowId = 0;
        while (true) {
            List<FixRow> rows = new ArrayList<>();
            String sql = "SELECT rowid," + quote(column) + " FROM " + eventTable + " "
                    + "WHERE family=? AND rowid>? AND " + quote(column) + " IS NOT NULL AND match(" + quote(column) + ", '^[A-Za-z0-9+/]+={0,2}$') "
                    + "ORDER BY rowid LIMIT " + BATCH_SIZE;
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, family);
                statement.setLong(2, lastRowId);
                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        long rowId = resultSet.getLong(1);
                        lastRowId = rowId;
                        byte[] decoded = decodeBase64OrNull(resultSet.getString(2));
                        if (decoded != null) {
                            rows.add(FixRow.forColumn(family, rowId, column, decoded));
                        }
                    }
                }
            }
            if (rows.isEmpty()) {
                return total;
            }
            mutateFixRows(connection, eventTable, database, prefix, rows);
            total += rows.size();
            ok(sender, "Repaired " + total + " " + family + "." + column + " rows...");
        }
    }

    private static byte[] convertSerializedItem(String itemData, int typeId, int amount) {
        Material type = MaterialUtils.getType(typeId);
        SerializedItem item = ItemUtils.deserializeItem(itemData, type, amount);
        if (item == null || item.itemStack() == null) {
            return null;
        }

        String faceData = item.faceData() == null ? null : item.faceData().name();
        List<List<Map<String, Object>>> metadata = ItemMetaHandler.serialize(item.itemStack(), null, faceData, item.slot() == null ? -1 : item.slot());
        if (metadata.isEmpty()) {
            return null;
        }
        return ItemUtils.convertByteData(metadata);
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

    private static void mutateFixRows(Connection connection, String eventTable, String database, String prefix, List<FixRow> rows) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            List<FixRow> metadataRows = rows.stream().filter(row -> row.metadataHex != null).toList();
            if (!metadataRows.isEmpty()) {
                executeFixMutation(statement, eventTable, "metadata", metadataRows);
            }
            List<FixRow> payloadRows = rows.stream().filter(row -> row.payloadHex != null).toList();
            if (!payloadRows.isEmpty()) {
                executeFixMutation(statement, eventTable, "payload", payloadRows);
            }
        }
    }

    private static void executeFixMutation(Statement statement, String eventTable, String eventColumn, List<FixRow> rows) throws SQLException {
        String sql = mutationSql(eventTable, eventColumn, rows);
        CoreProtect.getInstance().getSLF4JLogger().info("[PlayPro metadata repair] Executing {} mutation for {} rows ({} SQL chars).", eventColumn, rows.size(), sql.length());
        statement.execute(sql);
    }

    private static String mutationSql(String eventTable, String eventColumn, List<FixRow> rows) {
        StringBuilder expression = new StringBuilder("multiIf(");
        StringBuilder where = new StringBuilder();
        for (int i = 0; i < rows.size(); i++) {
            FixRow row = rows.get(i);
            if (i > 0) {
                expression.append(',');
                where.append(" OR ");
            }

            String predicate = "(family=" + sqlString(row.family) + " AND rowid=" + row.rowId + ")";
            expression.append(predicate).append(',').append(hexValue(eventColumn.equals("payload") ? row.payloadHex : row.metadataHex));
            where.append(predicate);
        }
        expression.append(',').append(quote(eventColumn)).append(')');

        return "ALTER TABLE " + eventTable
                + " UPDATE " + quote(eventColumn) + " = " + expression
                + " WHERE " + where
                + " SETTINGS mutations_sync=2";
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

    private static long countRemainingLegacyRows(Connection connection, String eventTable) throws SQLException {
        String sql = "SELECT count() FROM " + eventTable + " WHERE "
                + "(family='container' AND metadata IS NOT NULL AND startsWith(metadata,'{')) OR "
                + "(family='item' AND payload IS NOT NULL AND startsWith(payload,'{')) OR "
                + "(family='entity_container' AND metadata IS NOT NULL AND startsWith(metadata,'[')) OR "
                + "(family='entity_interaction' AND metadata IS NOT NULL AND metadata!='' AND match(metadata,'^[A-Za-z0-9+/]+={0,2}$'))";
        try (Statement statement = connection.createStatement(); ResultSet resultSet = statement.executeQuery(sql)) {
            return resultSet.next() ? resultSet.getLong(1) : 0;
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

    private static String hexValue(String value) {
        if (value == null || value.isEmpty()) {
            return "CAST(NULL,'Nullable(String)')";
        }
        return "CAST(unhex(" + sqlString(value) + "),'Nullable(String)')";
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

    private record FixRow(String family, long rowId, String metadataHex, String payloadHex) {

        private static FixRow forColumn(String family, long rowId, String column, byte[] bytes) {
            String encoded = hex(bytes);
            if (column.equals("payload")) {
                return new FixRow(family, rowId, null, encoded);
            }
            return new FixRow(family, rowId, encoded, null);
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
