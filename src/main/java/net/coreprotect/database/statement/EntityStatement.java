package net.coreprotect.database.statement;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

import net.coreprotect.CoreProtect;
import org.bukkit.block.BlockState;
import org.bukkit.util.io.BukkitObjectInputStream;

import net.coreprotect.config.ConfigHandler;
import net.coreprotect.utility.ErrorReporter;
import net.coreprotect.utility.serialize.JsonSerialization;

public class EntityStatement {

    private static final int SELECT_BATCH_SIZE = 500;

    private EntityStatement() {
        throw new IllegalStateException("Database class");
    }

    public static long insert(PreparedStatement preparedStmt, int time, String entityData) throws SQLException {
        final long rowid = CoreProtect.getInstance().rowNumbers().nextRowNumber("entity", preparedStmt.getConnection());
        preparedStmt.setInt(1, time);
        preparedStmt.setString(2, entityData);
        preparedStmt.setLong(3, rowid);
        preparedStmt.execute();

        return rowid;
    }

    public static long insert(PreparedStatement preparedStmt, int time, List<Object> entityData) throws SQLException {
        return insert(preparedStmt, time, JsonSerialization.GSON.toJson(entityData));
    }

    @Deprecated // ch
    public static List<Object> getData(Statement statement, BlockState block, String query) {
        List<Object> result = new ArrayList<>();

        try {
            ResultSet resultSet = statement.executeQuery(query);
            while (resultSet.next()) {
                result = deserializeData(resultSet.getString("data"));
            }

            resultSet.close();
        }
        catch (Exception e) { // only print exception on development branch
            ErrorReporter.report(e, ConfigHandler.EDITION_BRANCH.contains("-dev"));
        }

        return result;
    }

    public static Map<Integer, List<Object>> loadData(Connection connection, Collection<Integer> rowIds) throws SQLException {
        Map<Integer, List<Object>> result = new HashMap<>();
        if (rowIds.isEmpty()) {
            return result;
        }

        List<Integer> ids = new ArrayList<>(rowIds);
        for (int offset = 0; offset < ids.size(); offset += SELECT_BATCH_SIZE) {
            int end = Math.min(offset + SELECT_BATCH_SIZE, ids.size());
            StringJoiner placeholders = new StringJoiner(",");
            for (int ignored = offset; ignored < end; ignored++) {
                placeholders.add("?");
            }

            String query = "SELECT rowid,data FROM " + ConfigHandler.prefix + "entity WHERE rowid IN(" + placeholders + ")";
            try (PreparedStatement preparedStatement = connection.prepareStatement(query)) {
                for (int index = offset; index < end; index++) {
                    preparedStatement.setInt(index - offset + 1, ids.get(index));
                }
                try (ResultSet resultSet = preparedStatement.executeQuery()) {
                    while (resultSet.next()) {
                        List<Object> data = deserializeData(resultSet.getString("data"));
                        if (!data.isEmpty()) {
                            result.put(resultSet.getInt("rowid"), data);
                        }
                    }
                }
            }
        }
        return result;
    }

    public static List<Object> deserializeData(byte[] data) {
        List<Object> result = new ArrayList<>();
        if (data == null) {
            return result;
        }

        String stringData = new String(data, StandardCharsets.UTF_8);
        if (stringData.startsWith("[")) {
            return deserializeData(stringData);
        }

        try (ByteArrayInputStream bais = new ByteArrayInputStream(data); BukkitObjectInputStream input = new BukkitObjectInputStream(bais)) {
            @SuppressWarnings("unchecked")
            List<Object> values = (List<Object>) input.readObject();
            result = values;
        }
        catch (Exception e) {
            ErrorReporter.report(e, ConfigHandler.EDITION_BRANCH.contains("-dev"));
        }

        return result;
    }

    public static byte[] serializeData(List<Object> data) {
        if (data == null) {
            return null;
        }
        return JsonSerialization.GSON.toJson(data).getBytes(StandardCharsets.UTF_8);
    }

    public static List<Object> deserializeData(String data) {
        if (data == null || data.isEmpty()) {
            return new ArrayList<>();
        }

        try {
            List<Object> result = JsonSerialization.GSON.fromJson(data, List.class);
            return result == null ? new ArrayList<>() : result;
        }
        catch (Exception e) {
            ErrorReporter.report(e, ConfigHandler.EDITION_BRANCH.contains("-dev"));
            return new ArrayList<>();
        }
    }
}
