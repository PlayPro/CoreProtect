package net.coreprotect.database.statement;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

import org.bukkit.attribute.Attribute;
import org.bukkit.block.BlockState;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import net.coreprotect.bukkit.BukkitAdapter;
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.database.ConsumerWriteBatch;
import net.coreprotect.database.Database;
import net.coreprotect.utility.DatabaseUtils;
import net.coreprotect.utility.ErrorReporter;

public class EntityStatement {

    private static final int SELECT_BATCH_SIZE = 500;

    private EntityStatement() {
        throw new IllegalStateException("Database class");
    }

    public static int insert(ConsumerWriteBatch batch, int time, List<Object> data) {
        try {
            byte[] serializedData = serializeData(data);
            if (serializedData == null) {
                return 0;
            }
            return batch.addEntity(time, serializedData);
        }
        catch (Exception e) {
            Database.handleWriteFailure(e);
        }

        return 0;
    }

    public static byte[] serializeData(List<Object> data) {
        if (data == null) {
            return null;
        }

        try (ByteArrayOutputStream output = new ByteArrayOutputStream(); BukkitObjectOutputStream objectOutput = new BukkitObjectOutputStream(output)) {
            objectOutput.writeObject(sanitizeData(data));
            objectOutput.flush();
            return output.toByteArray();
        }
        catch (Exception e) {
            ErrorReporter.report(e, ConfigHandler.EDITION_BRANCH.contains("-dev"));
            return null;
        }
    }

    private static List<Object> sanitizeData(List<Object> data) {
        List<Object> result = new ArrayList<>(data.size());
        for (Object value : data) {
            result.add(sanitizeValue(value));
        }

        return result;
    }

    private static Object sanitizeValue(Object value) {
        if (value instanceof Attribute) {
            return BukkitAdapter.ADAPTER.getRegistryKey(value);
        }
        else if (value instanceof List<?>) {
            List<?> list = (List<?>) value;
            List<Object> result = new ArrayList<>(list.size());
            for (Object item : list) {
                result.add(sanitizeValue(item));
            }

            return result;
        }
        else if (value instanceof Map<?, ?>) {
            Map<?, ?> map = (Map<?, ?>) value;
            Map<Object, Object> result = new LinkedHashMap<>(map.size());
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                result.put(sanitizeValue(entry.getKey()), sanitizeValue(entry.getValue()));
            }

            return result;
        }

        return value;
    }

    public static List<Object> getData(Statement statement, BlockState block, String query) {
        List<Object> result = new ArrayList<>();

        try {
            ResultSet resultSet = statement.executeQuery(query);
            while (resultSet.next()) {
                result = deserializeData(DatabaseUtils.getBytes(resultSet, "data"));
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
                        List<Object> data = deserializeData(DatabaseUtils.getBytes(resultSet, "data"));
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
}
