package net.coreprotect.database.statement;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

final class StatementBatches {

    private static final int SELECT_BATCH_SIZE = 500;

    private StatementBatches() {
        throw new IllegalStateException("Database class");
    }

    static <T> void selectIn(Connection connection, String query, Collection<T> values, ParameterBinder<T> bind, RowReader read) throws SQLException {
        if (values.isEmpty()) {
            return;
        }
        List<T> items = new ArrayList<>(values);
        for (int offset = 0; offset < items.size(); offset += SELECT_BATCH_SIZE) {
            int count = Math.min(SELECT_BATCH_SIZE, items.size() - offset);
            String placeholders = String.join(",", Collections.nCopies(count, "?"));
            try (PreparedStatement statement = connection.prepareStatement(query + " IN(" + placeholders + ")")) {
                for (int index = 0; index < count; index++) {
                    bind.set(statement, index + 1, items.get(offset + index));
                }
                readRows(statement, read);
            }
        }
    }

    static void readRows(PreparedStatement statement, RowReader read) throws SQLException {
        try (ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                read.accept(rows);
            }
        }
    }

    @FunctionalInterface
    interface ParameterBinder<T> {
        void set(PreparedStatement statement, int index, T value) throws SQLException;
    }

    @FunctionalInterface
    interface RowReader {
        void accept(ResultSet row) throws SQLException;
    }
}
