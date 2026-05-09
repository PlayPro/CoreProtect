package net.coreprotect.api;

import java.sql.Connection;
import java.sql.PreparedStatement;

import org.bukkit.Location;

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
        query.append("WHERE time > ?");

        if (userId != null) {
            query.append(" AND user = ?");
        }

        if (location != null) {
            query.append(" AND wid = ?");
            if (radius > 0) {
                query.append(" AND x >= ? AND x <= ? AND z >= ? AND z <= ?");
            }
            else {
                query.append(" AND x = ? AND y = ? AND z = ?");
            }
        }
    }

    void appendLimit(StringBuilder query) {
        if (limitOffset >= 0 && limitCount >= 0) {
            query.append(" LIMIT ").append(limitOffset).append(", ").append(limitCount);
        }
    }

    int bind(PreparedStatement statement) throws Exception {
        int parameterIndex = 1;
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
}
