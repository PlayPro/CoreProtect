package net.coreprotect.utility;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.coreprotect.config.ConfigHandler;

public class DatabaseUtils {

    private DatabaseUtils() {
        throw new IllegalStateException("Utility class");
    }

    public static <K, V extends Comparable<? super V>> List<Map.Entry<K, V>> entriesSortedByValues(Map<K, V> map) {
        if (map == null || map.isEmpty()) {
            return new ArrayList<>();
        }
        List<Map.Entry<K, V>> sortedEntries = new ArrayList<>(map.entrySet());
        sortedEntries.sort(Map.Entry.comparingByValue());
        return sortedEntries;
    }

    public static byte[] getBytes(ResultSet resultSet, String column) throws SQLException {
        int columnIndex = resultSet.findColumn(column);
        byte[] value;
        try {
            value = resultSet.getBytes(column);
        }
        catch (SQLFeatureNotSupportedException e) {
            value = resultSet.getBytes(columnIndex);
        }
        if (!ConfigHandler.databaseType.isClickHouse() || resultSet.getMetaData().getColumnType(columnIndex) != Types.ARRAY || value == null) {
            return value;
        }
        if (value.length == 0) {
            return null;
        }
        if (value[0] != 0) {
            throw new SQLException("Invalid ClickHouse binary presence marker for column " + column);
        }
        byte[] binary = new byte[value.length - 1];
        System.arraycopy(value, 1, binary, 0, binary.length);
        return binary;
    }

    public static String caseInsensitiveEquals(String column) {
        if (ConfigHandler.databaseType.isClickHouse()) {
            return "lowerUTF8(" + column + ") = lowerUTF8(?)";
        }
        return column + " = ?" + (ConfigHandler.databaseType.isMySQL() ? "" : " COLLATE NOCASE");
    }

}
