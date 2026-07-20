package net.coreprotect.utility;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Types;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;

import net.coreprotect.config.ConfigHandler;

public class DatabaseUtils {

    private DatabaseUtils() {
        throw new IllegalStateException("Utility class");
    }

    public static <K, V extends Comparable<? super V>> SortedSet<Map.Entry<K, V>> entriesSortedByValues(Map<K, V> map) {
        SortedSet<Map.Entry<K, V>> sortedEntries = new TreeSet<>((e1, e2) -> {
            int res = e1.getValue().compareTo(e2.getValue());
            return res != 0 ? res : 1;
        });
        sortedEntries.addAll(map.entrySet());
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

    public static boolean successfulQuery(Connection connection, String query) {
        boolean result = false;
        try {
            PreparedStatement preparedStmt = connection.prepareStatement(query);
            ResultSet resultSet = preparedStmt.executeQuery();
            if (resultSet.isBeforeFirst()) {
                result = true;
            }
            resultSet.close();
            preparedStmt.close();
        }
        catch (Exception e) {
            ErrorReporter.report(e);
        }
        return result;
    }

}
