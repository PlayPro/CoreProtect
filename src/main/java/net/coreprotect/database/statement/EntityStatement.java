package net.coreprotect.database.statement;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.attribute.Attribute;
import org.bukkit.block.BlockState;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import net.coreprotect.bukkit.BukkitAdapter;
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.database.Database;

public class EntityStatement {

    private EntityStatement() {
        throw new IllegalStateException("Database class");
    }

    public static ResultSet insert(PreparedStatement preparedStmt, int time, List<Object> data) {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            BukkitObjectOutputStream oos = new BukkitObjectOutputStream(bos);
            oos.writeObject(sanitizeData(data));
            oos.flush();
            oos.close();
            bos.close();

            byte[] byte_data = bos.toByteArray();
            preparedStmt.setInt(1, time);
            preparedStmt.setObject(2, byte_data);
            if (Database.hasReturningKeys()) {
                return preparedStmt.executeQuery();
            }
            else {
                preparedStmt.executeUpdate();
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        return null;
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
                byte[] data = resultSet.getBytes("data");
                ByteArrayInputStream bais = new ByteArrayInputStream(data);
                BukkitObjectInputStream ins = new BukkitObjectInputStream(bais);
                @SuppressWarnings("unchecked")
                List<Object> input = (List<Object>) ins.readObject();
                ins.close();
                bais.close();
                result = input;
            }

            resultSet.close();
        }
        catch (Exception e) { // only display exception on development branch
            if (ConfigHandler.EDITION_BRANCH.contains("-dev")) {
                e.printStackTrace();
            }
        }

        return result;
    }
}
