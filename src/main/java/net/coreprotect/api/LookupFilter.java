package net.coreprotect.api;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.bukkit.Location;

import net.coreprotect.config.ConfigHandler;
import net.coreprotect.database.DuckDBLookupQuery;
import net.coreprotect.database.DuckDBSpatialIndex;
import net.coreprotect.utility.WorldUtils;

final class LookupFilter {
    private final Integer userId;
    private final int checkTime;
    private final Location location;
    private final int radius;
    private final int limitOffset;
    private final int limitCount;

    private LookupFilter(Integer userId, int checkTime, Location location, int radius, int limitOffset, int limitCount) {
        this.userId = userId;
        this.checkTime = checkTime;
        this.location = location;
        this.radius = radius;
        this.limitOffset = limitOffset;
        this.limitCount = limitCount;
    }

    static LookupFilter fromOptions(Connection connection, LookupOptions options) throws Exception {
        if (options == null) {
            options = LookupOptions.builder().build();
        }

        Integer userId = MessageAPI.getUserId(connection, options.getUser());
        int checkTime = 0;
        if (options.getTime() > 0) {
            checkTime = (int) (System.currentTimeMillis() / 1000L) - options.getTime();
        }

        return new LookupFilter(userId, checkTime, options.getLocation(), options.getRadius(), options.getLimitOffset(), options.getLimitCount());
    }

    boolean hasInvalidUser() {
        return userId != null && userId == -1;
    }

    boolean hasInvalidLocation() {
        return location != null && location.getWorld() == null;
    }

    boolean hasLocation() {
        return location != null;
    }

    boolean beginDuckDBSnapshot(Connection connection) throws Exception {
        if (!ConfigHandler.databaseType.isDuckDB() || location == null || !connection.getAutoCommit()) {
            return false;
        }
        connection.setAutoCommit(false);
        return true;
    }

    void endDuckDBSnapshot(Connection connection, boolean started) throws Exception {
        if (!started) {
            return;
        }
        try {
            connection.rollback();
        }
        finally {
            connection.setAutoCommit(true);
        }
    }

    void appendWhere(StringBuilder query) {
        appendWhere(query, "");
    }

    void appendWhere(StringBuilder query, String alias) {
        String qualifier = alias.isEmpty() ? "" : alias + ".";
        query.append("WHERE ").append(qualifier).append("time > ?");

        if (userId != null) {
            query.append(" AND ").append(qualifier).append(ConfigHandler.databaseType.getUserColumn()).append(" = ?");
        }

        if (location != null) {
            query.append(" AND ").append(qualifier).append("wid = ?");
            if (radius > 0) {
                query.append(" AND ").append(qualifier).append("x >= ? AND ").append(qualifier).append("x <= ? AND ").append(qualifier).append("z >= ? AND ").append(qualifier).append("z <= ?");
            }
            else {
                query.append(" AND ").append(qualifier).append("x = ? AND ").append(qualifier).append("y = ? AND ").append(qualifier).append("z = ?");
            }
        }
    }

    void appendEntityContainerWhere(StringBuilder query, String transactionAlias, String entityAlias) {
        String transaction = transactionAlias + ".";
        String entity = entityAlias + ".";
        query.append("WHERE ").append(transaction).append("time > ?");
        if (userId != null) {
            query.append(" AND ").append(transaction).append(ConfigHandler.databaseType.getUserColumn()).append(" = ?");
        }
        if (location == null) {
            return;
        }

        query.append(" AND ((").append(transaction).append("wid = ?");
        if (radius > 0) {
            query.append(" AND ").append(transaction).append("x >= ? AND ").append(transaction).append("x <= ? AND ").append(transaction).append("z >= ? AND ").append(transaction).append("z <= ?");
        }
        else {
            query.append(" AND ").append(transaction).append("x = ? AND ").append(transaction).append("y = ? AND ").append(transaction).append("z = ?");
        }

        query.append(") OR (").append(entity).append("current_wid = ?");
        if (radius > 0) {
            query.append(" AND ").append(entity).append("x >= ? AND ").append(entity).append("x < ? AND ").append(entity).append("z >= ? AND ").append(entity).append("z < ?");
        }
        else {
            query.append(" AND ").append(entity).append("x >= ? AND ").append(entity).append("x < ? AND ").append(entity).append("y >= ? AND ").append(entity).append("y < ? AND ").append(entity).append("z >= ? AND ").append(entity).append("z < ?");
        }
        query.append("))");
    }

    String table(Connection connection, String table, String alias) {
        if (location == null) {
            return ConfigHandler.prefix + table + alias(alias);
        }

        int x = location.getBlockX();
        int z = location.getBlockZ();
        int minimumX = radius > 0 ? MessageAPI.clampToInt((long) x - radius) : x;
        int maximumX = radius > 0 ? MessageAPI.clampToInt((long) x + radius) : x;
        int minimumZ = radius > 0 ? MessageAPI.clampToInt((long) z - radius) : z;
        int maximumZ = radius > 0 ? MessageAPI.clampToInt((long) z + radius) : z;
        int worldId = WorldUtils.getWorldId(location.getWorld().getName());
        return DuckDBLookupQuery.spatialTable(connection, table, worldId, minimumX, maximumX, minimumZ, maximumZ, alias);
    }

    String entityContainerTable(Connection connection, String alias) throws Exception {
        if (location == null || !ConfigHandler.databaseType.isDuckDB()) {
            return ConfigHandler.prefix + "entity_container" + alias(alias);
        }

        int x = location.getBlockX();
        int z = location.getBlockZ();
        int minimumX = radius > 0 ? MessageAPI.clampToInt((long) x - radius) : x;
        int maximumX = radius > 0 ? MessageAPI.clampToInt((long) x + radius) : x;
        int minimumZ = radius > 0 ? MessageAPI.clampToInt((long) z - radius) : z;
        int maximumZ = radius > 0 ? MessageAPI.clampToInt((long) z + radius) : z;
        int worldId = WorldUtils.getWorldId(location.getWorld().getName());
        List<Integer> entitySpawnRowIds = loadCurrentEntitySpawnRowIds(connection);
        return DuckDBSpatialIndex.tableExpression(
                connection,
                ConfigHandler.prefix,
                "entity_container",
                worldId,
                minimumX,
                maximumX,
                minimumZ,
                maximumZ,
                entitySpawnRowIds,
                Collections.emptySet(),
                alias
        );
    }

    private List<Integer> loadCurrentEntitySpawnRowIds(Connection connection) throws Exception {
        List<Integer> rowIds = new ArrayList<>();
        StringBuilder query = new StringBuilder("SELECT rowid FROM ").append(ConfigHandler.prefix).append("entity_spawn WHERE current_wid=?");
        if (radius > 0) {
            query.append(" AND x>=? AND x<? AND z>=? AND z<?");
        }
        else {
            query.append(" AND x>=? AND x<? AND y>=? AND y<? AND z>=? AND z<?");
        }
        try (PreparedStatement statement = connection.prepareStatement(query.toString())) {
            int parameterIndex = 1;
            int x = location.getBlockX();
            int y = location.getBlockY();
            int z = location.getBlockZ();
            statement.setInt(parameterIndex++, WorldUtils.getWorldId(location.getWorld().getName()));
            if (radius > 0) {
                int minimumX = MessageAPI.clampToInt((long) x - radius);
                int maximumX = MessageAPI.clampToInt((long) x + radius);
                int minimumZ = MessageAPI.clampToInt((long) z - radius);
                int maximumZ = MessageAPI.clampToInt((long) z + radius);
                statement.setInt(parameterIndex++, minimumX);
                statement.setLong(parameterIndex++, (long) maximumX + 1L);
                statement.setInt(parameterIndex++, minimumZ);
                statement.setLong(parameterIndex, (long) maximumZ + 1L);
            }
            else {
                statement.setInt(parameterIndex++, x);
                statement.setLong(parameterIndex++, (long) x + 1L);
                statement.setInt(parameterIndex++, y);
                statement.setLong(parameterIndex++, (long) y + 1L);
                statement.setInt(parameterIndex++, z);
                statement.setLong(parameterIndex, (long) z + 1L);
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next() && rowIds.size() <= 4_096) {
                    rowIds.add(resultSet.getInt(1));
                }
            }
        }
        return rowIds;
    }

    void appendLimit(StringBuilder query) {
        if (limitOffset >= 0 && limitCount >= 0) {
            query.append(" LIMIT ").append(limitCount).append(" OFFSET ").append(limitOffset);
        }
    }

    int bind(PreparedStatement statement) throws Exception {
        return bind(statement, 1);
    }

    int bind(PreparedStatement statement, int parameterIndex) throws Exception {
        statement.setInt(parameterIndex++, checkTime);

        if (userId != null) {
            statement.setInt(parameterIndex++, userId);
        }

        if (location != null) {
            int x = location.getBlockX();
            int y = location.getBlockY();
            int z = location.getBlockZ();
            statement.setInt(parameterIndex++, WorldUtils.getWorldId(location.getWorld().getName()));

            if (radius > 0) {
                statement.setInt(parameterIndex++, MessageAPI.clampToInt((long) x - radius));
                statement.setInt(parameterIndex++, MessageAPI.clampToInt((long) x + radius));
                statement.setInt(parameterIndex++, MessageAPI.clampToInt((long) z - radius));
                statement.setInt(parameterIndex++, MessageAPI.clampToInt((long) z + radius));
            }
            else {
                statement.setInt(parameterIndex++, x);
                statement.setInt(parameterIndex++, y);
                statement.setInt(parameterIndex++, z);
            }
        }

        return parameterIndex;
    }

    int bindEntityContainer(PreparedStatement statement, int parameterIndex) throws Exception {
        statement.setInt(parameterIndex++, checkTime);
        if (userId != null) {
            statement.setInt(parameterIndex++, userId);
        }
        if (location == null) {
            return parameterIndex;
        }

        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();
        int worldId = WorldUtils.getWorldId(location.getWorld().getName());
        statement.setInt(parameterIndex++, worldId);
        if (radius > 0) {
            int minimumX = MessageAPI.clampToInt((long) x - radius);
            int maximumX = MessageAPI.clampToInt((long) x + radius);
            int minimumZ = MessageAPI.clampToInt((long) z - radius);
            int maximumZ = MessageAPI.clampToInt((long) z + radius);
            statement.setInt(parameterIndex++, minimumX);
            statement.setInt(parameterIndex++, maximumX);
            statement.setInt(parameterIndex++, minimumZ);
            statement.setInt(parameterIndex++, maximumZ);
            statement.setInt(parameterIndex++, worldId);
            statement.setInt(parameterIndex++, minimumX);
            statement.setLong(parameterIndex++, (long) maximumX + 1L);
            statement.setInt(parameterIndex++, minimumZ);
            statement.setLong(parameterIndex++, (long) maximumZ + 1L);
        }
        else {
            statement.setInt(parameterIndex++, x);
            statement.setInt(parameterIndex++, y);
            statement.setInt(parameterIndex++, z);
            statement.setInt(parameterIndex++, worldId);
            statement.setInt(parameterIndex++, x);
            statement.setLong(parameterIndex++, (long) x + 1L);
            statement.setInt(parameterIndex++, y);
            statement.setLong(parameterIndex++, (long) y + 1L);
            statement.setInt(parameterIndex++, z);
            statement.setLong(parameterIndex++, (long) z + 1L);
        }
        return parameterIndex;
    }

    private static String alias(String alias) {
        return alias == null || alias.isEmpty() ? "" : " AS " + alias;
    }
}
