package net.coreprotect.database;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import net.coreprotect.api.LookupOptions;
import net.coreprotect.config.ConfigHandler;

class MessageFilterQueryTest {
    @ParameterizedTest
    @EnumSource(value = DatabaseType.class, names = {"SQLITE", "DUCKDB"})
    void filtersBeforePaginationWithLiteralPrefixes(DatabaseType type) throws Exception {
        DatabaseType previous = ConfigHandler.databaseType;
        ConfigHandler.databaseType = type;
        try (Connection connection = DriverManager.getConnection(type.isSQLite() ? "jdbc:sqlite::memory:" : "jdbc:duckdb:")) {
            connection.createStatement().execute("CREATE TABLE co_command (rowid INTEGER, message VARCHAR)");
            List<String> messages = List.of("/msg Notch hello", "/tell Notch hello", "/msg Notch secret", "/msg Other hello", "100%_~* literal", "100XYZ literal", "o'hare \\ hello", "abcdefghijklmnop-long", "abcdefghijklmnop-wrong");
            try (PreparedStatement insert = connection.prepareStatement("INSERT INTO co_command VALUES (?, ?)")) {
                for (int index = 0; index < messages.size(); index++) {
                    insert.setInt(1, index + 1);
                    insert.setString(2, messages.get(index));
                    insert.executeUpdate();
                }
            }
            assertEquals(List.of(1, 2), lookup(connection, "command", List.of("/msg Notch ", "/tell Notch ", "-/msg Notch secret"), ""));
            assertEquals(List.of(2), lookup(connection, "command", List.of("/msg Notch ", "/tell Notch ", "-/msg Notch secret"), " LIMIT 1 OFFSET 1"));
            assertEquals(List.of(2, 4, 5, 6, 7, 8, 9), lookup(connection, "command", List.of("-/msg Notch "), ""));
            assertEquals(List.of(5), lookup(connection, "command", List.of("100%_~*"), ""));
            assertEquals(List.of(7), lookup(connection, "command", List.of("o'hare \\"), ""));
            assertEquals(List.of(8), lookup(connection, "command", List.of("abcdefghijklmnop-long"), ""));
            assertEquals(9, lookup(connection, "command", List.of(), "").size());
            connection.createStatement().execute("CREATE TABLE co_chat AS SELECT * FROM co_command");
            assertEquals(List.of(5), lookup(connection, "chat", List.of("100%_~*"), ""));
        }
        finally {
            ConfigHandler.databaseType = previous;
        }
    }

    @ParameterizedTest
    @EnumSource(value = DatabaseType.class, names = {"SQLITE", "DUCKDB"})
    void signMatchesOnlyLoggedFace(DatabaseType type) throws Exception {
        DatabaseType previous = ConfigHandler.databaseType;
        ConfigHandler.databaseType = type;
        try (Connection connection = DriverManager.getConnection(type.isSQLite() ? "jdbc:sqlite::memory:" : "jdbc:duckdb:")) {
            connection.createStatement().execute("CREATE TABLE co_sign (rowid INTEGER, face INTEGER, line_1 VARCHAR, line_2 VARCHAR, line_3 VARCHAR, line_4 VARCHAR, line_5 VARCHAR, line_6 VARCHAR, line_7 VARCHAR, line_8 VARCHAR)");
            connection.createStatement().execute("INSERT INTO co_sign VALUES (1,0,'hello',NULL,NULL,NULL,'secret',NULL,NULL,NULL),(2,1,'hello',NULL,NULL,NULL,'secret',NULL,NULL,NULL),(3,0,'hello','secret',NULL,NULL,NULL,NULL,NULL,NULL)");
            assertEquals(List.of(1, 3), lookup(connection, "sign", List.of("hello"), ""));
            assertEquals(List.of(1), lookup(connection, "sign", List.of("hello", "-secret"), ""));
            assertEquals(List.of(1), lookup(connection, "sign", List.of("-secret"), ""));
            assertEquals(List.of(2, 3), lookup(connection, "sign", List.of("secret"), ""));
        }
        finally {
            ConfigHandler.databaseType = previous;
        }
    }

    @Test
    void validatesCodePointsAndCopiesOptions() {
        for (String invalid : List.of("", "ab", "-ab", "-", "😀😀")) {
            assertThrows(IllegalArgumentException.class, () -> LookupOptions.builder().messageFilters(List.of(invalid)));
        }
        List<String> values = new ArrayList<>(List.of("😀😀😀", "-😀😀😀", "/msg Notch "));
        LookupOptions options = LookupOptions.builder().messageFilters(values).build();
        values.clear();
        assertEquals(3, options.getMessageFilters().size());
        assertEquals("/msg Notch ", options.getMessageFilters().get(2));
        assertThrows(UnsupportedOperationException.class, () -> options.getMessageFilters().clear());
        assertTrue(LookupOptions.builder().build().getMessageFilters().isEmpty());
    }

    private List<Integer> lookup(Connection connection, String table, List<String> filters, String limit) throws Exception {
        List<String> bindings = new ArrayList<>();
        String query = MessageFilterQuery.append("SELECT rowid FROM co_" + table + " WHERE rowid > ?", filters, table, bindings) + " ORDER BY rowid" + limit;
        List<Integer> rows = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setInt(1, 0);
            for (int index = 0; index < bindings.size(); index++) {
                statement.setString(index + 2, bindings.get(index));
            }
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    rows.add(result.getInt(1));
                }
            }
        }
        return rows;
    }
}
