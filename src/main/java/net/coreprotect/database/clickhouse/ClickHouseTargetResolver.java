package net.coreprotect.database.clickhouse;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.StringJoiner;
import java.util.UUID;

final class ClickHouseTargetResolver {

    private static final int SELECT_BATCH_SIZE = 5_000;

    private final ClickHouseJdbc jdbc;
    private final String eventTable;
    private final UUID datasetId;

    ClickHouseTargetResolver(ClickHouseJdbc jdbc, String database, String prefix, UUID datasetId) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        String validatedPrefix = prefix == null || prefix.isEmpty() ? "" : ClickHouseIdentifiers.requireIdentifier(prefix, "ClickHouse table prefix");
        eventTable = ClickHouseIdentifiers.qualified(database, validatedPrefix + "event_data");
        this.datasetId = Objects.requireNonNull(datasetId, "datasetId");
    }

    Map<Long, ClickHouseEventPointer> resolve(ClickHouseFamily family, List<Long> requestedRowIds) throws SQLException {
        Objects.requireNonNull(family, "family");
        Set<Long> unique = uniqueRowIds(requestedRowIds, "rollback");
        List<Long> rowIds = new ArrayList<>(unique);
        Map<Long, ClickHouseEventPointer> pointers = new LinkedHashMap<>();
        try (Connection connection = jdbc.openConnection()) {
            for (int offset = 0; offset < rowIds.size(); offset += SELECT_BATCH_SIZE) {
                resolveBatch(connection, family, rowIds.subList(offset, Math.min(offset + SELECT_BATCH_SIZE, rowIds.size())), pointers, null);
            }
        }
        if (pointers.size() != rowIds.size()) {
            List<Long> missing = new ArrayList<>(unique);
            missing.removeAll(pointers.keySet());
            throw new SQLException("Unable to resolve " + missing.size() + " ClickHouse " + family.getTableName() + " row IDs");
        }
        return pointers;
    }

    Map<Long, ClickHouseEventPointer> resolveAvailableBlocks(List<Long> requestedRowIds, Map<Long, Integer> actions) throws SQLException {
        Objects.requireNonNull(actions, "actions");
        Set<Long> unique = uniqueRowIds(requestedRowIds, "block");
        List<Long> rowIds = new ArrayList<>(unique);
        Map<Long, ClickHouseEventPointer> pointers = new LinkedHashMap<>();
        try (Connection connection = jdbc.openConnection()) {
            for (int offset = 0; offset < rowIds.size(); offset += SELECT_BATCH_SIZE) {
                resolveBatch(connection, ClickHouseFamily.BLOCK, rowIds.subList(offset, Math.min(offset + SELECT_BATCH_SIZE, rowIds.size())), pointers, actions);
            }
        }
        return pointers;
    }

    private void resolveBatch(Connection connection, ClickHouseFamily family, List<Long> rowIds, Map<Long, ClickHouseEventPointer> pointers, Map<Long, Integer> actions) throws SQLException {
        if (rowIds.isEmpty()) {
            return;
        }
        StringJoiner placeholders = new StringJoiner(",");
        for (int ignored = 0; ignored < rowIds.size(); ignored++) {
            placeholders.add("?");
        }
        String sql = "SELECT rowid,producer_id,producer_sequence,batch_ordinal,time,wid,x,z" + (actions == null ? "" : ",action") + " FROM " + eventTable + " FINAL"
                + " WHERE dataset_id=? AND family=?"
                + " AND rowid IN(" + placeholders + ")";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, datasetId);
            statement.setString(2, family.getTableName());
            for (int index = 0; index < rowIds.size(); index++) {
                statement.setLong(index + 3, rowIds.get(index));
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    long rowId = resultSet.getLong(1);
                    ClickHouseEventPointer pointer = new ClickHouseEventPointer(datasetId, family, UUID.fromString(resultSet.getString(2)), resultSet.getLong(3), resultSet.getInt(4), rowId, resultSet.getInt(5), resultSet.getInt(6), resultSet.getInt(7), resultSet.getInt(8));
                    if (pointers.putIfAbsent(rowId, pointer) != null) {
                        throw new SQLException("ClickHouse " + family.getTableName() + " row ID " + rowId + " resolves to multiple committed facts");
                    }
                    if (actions != null) {
                        Object action = resultSet.getObject(9);
                        if (!(action instanceof Number)) {
                            throw new SQLException("ClickHouse block row " + rowId + " has no action");
                        }
                        actions.put(rowId, ((Number) action).intValue());
                    }
                }
            }
        }
    }

    private static Set<Long> uniqueRowIds(List<Long> requestedRowIds, String target) {
        Set<Long> unique = new LinkedHashSet<>();
        for (Long rowId : Objects.requireNonNull(requestedRowIds, "requestedRowIds")) {
            if (rowId == null || rowId < 1) {
                throw new IllegalArgumentException("ClickHouse " + target + " targets require positive row IDs");
            }
            unique.add(rowId);
        }
        return unique;
    }

}
