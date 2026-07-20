package net.coreprotect.api;

import java.sql.Connection;
import java.sql.PreparedStatement;

import org.bukkit.Location;

import net.coreprotect.config.ConfigHandler;
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

    void appendEntityContainerWhere(StringBuilder query, String transactionAlias) {
        String transaction = transactionAlias + ".";
        String entity = "current_spawn_rows.";
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

        query.append(") OR ").append(transaction).append("entity_spawn_rowid IN(SELECT ").append(entity).append("rowid FROM ")
                .append(ConfigHandler.prefix).append("entity_spawn current_spawn_rows WHERE ").append(entity).append("current_wid = ?");
        if (radius > 0) {
            query.append(" AND ").append(entity).append("x >= ? AND ").append(entity).append("x < ? AND ").append(entity).append("z >= ? AND ").append(entity).append("z < ?");
        }
        else {
            query.append(" AND ").append(entity).append("x >= ? AND ").append(entity).append("x < ? AND ").append(entity).append("y >= ? AND ").append(entity).append("y < ? AND ").append(entity).append("z >= ? AND ").append(entity).append("z < ?");
        }
        query.append("))");
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
}
