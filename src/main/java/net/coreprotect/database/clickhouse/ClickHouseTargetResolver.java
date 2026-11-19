package net.coreprotect.database.clickhouse;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLDataException;
import java.sql.SQLIntegrityConstraintViolationException;
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
        String validatedPrefix = ClickHouseIdentifiers.requirePrefix(prefix);
        eventTable = ClickHouseIdentifiers.qualified(database, validatedPrefix, "event_data");
        this.datasetId = Objects.requireNonNull(datasetId, "datasetId");
    }

    Map<Long, ClickHouseEventPointer> resolve(ClickHouseFamily family, List<Long> requestedRowIds) throws SQLException {
        return resolveRows(family, requestedRowIds, null, true);
    }

    Map<Long, ClickHouseEventPointer> resolveAvailable(ClickHouseFamily family, List<Long> requestedRowIds) throws SQLException {
        return resolveRows(family, requestedRowIds, null, false);
    }

    Map<Long, ClickHouseEventPointer> resolveAvailableBlocks(List<Long> requestedRowIds, Map<Long, Integer> actions) throws SQLException {
        return resolveRows(ClickHouseFamily.BLOCK, requestedRowIds, Objects.requireNonNull(actions, "actions"), false);
    }

    private Map<Long, ClickHouseEventPointer> resolveRows(ClickHouseFamily family, List<Long> requestedRowIds, Map<Long, Integer> actions, boolean requireAll) throws SQLException {
        Objects.requireNonNull(family, "family");
        List<Long> rowIds = new ArrayList<>(uniqueRowIds(requestedRowIds, family.getTableName()));
        Map<Long, ClickHouseEventPointer> pointers = new LinkedHashMap<>();
        if (rowIds.isEmpty()) {
            return pointers;
        }
        try (Connection connection = jdbc.openConnection()) {
            for (int offset = 0; offset < rowIds.size(); offset += SELECT_BATCH_SIZE) {
                resolveBatch(connection, family, rowIds.subList(offset, Math.min(offset + SELECT_BATCH_SIZE, rowIds.size())), pointers, actions);
            }
        }
        if (requireAll && pointers.size() != rowIds.size()) {
            throw new SQLDataException("Unable to resolve " + (rowIds.size() - pointers.size()) + " ClickHouse " + family.getTableName() + " row IDs");
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
        String sql = "SELECT rowid,batch_sequence,batch_ordinal,time,wid,x,z" + (actions == null ? "" : ",action") + " FROM " + eventTable + " FINAL"
                + " WHERE family=?"
                + (family == ClickHouseFamily.ENTITY_SPAWN ? " AND origin_x IS NOT NULL"
                        : ClickHouseSchema.rollbackProjection(family.getTableName()) != null ? " AND action IS NOT NULL" : "")
                + " AND rowid IN(" + placeholders + ")";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, family.getTableName());
            for (int index = 0; index < rowIds.size(); index++) {
                statement.setLong(index + 2, rowIds.get(index));
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    long rowId = resultSet.getLong(1);
                    ClickHouseEventPointer pointer;
                    try {
                        pointer = new ClickHouseEventPointer(datasetId, family, resultSet.getLong(2), resultSet.getInt(3), rowId, resultSet.getInt(4), resultSet.getInt(5), resultSet.getInt(6), resultSet.getInt(7));
                    }
                    catch (IllegalArgumentException exception) {
                        throw new SQLDataException("Invalid ClickHouse " + family.getTableName() + " event pointer for row " + rowId, exception);
                    }
                    if (pointers.putIfAbsent(rowId, pointer) != null) {
                        throw new SQLIntegrityConstraintViolationException("ClickHouse " + family.getTableName() + " row ID " + rowId + " resolves to multiple committed facts");
                    }
                    if (actions != null) {
                        Object action = resultSet.getObject(8);
                        if (!(action instanceof Number)) {
                            throw new SQLDataException("ClickHouse block row " + rowId + " has no action");
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
