package net.coreprotect.database;

import net.coreprotect.config.ConfigHandler;

public final class LocationQuery {

    private LocationQuery() {
        throw new IllegalStateException("Database class");
    }

    public static String predicate(String column, String comparison) {
        if (!ConfigHandler.databaseType.isClickHouse()) {
            return column + comparison;
        }
        int nameStart = column.lastIndexOf('.') + 1;
        String key = column.substring(0, nameStart) + "_key_" + column.substring(nameStart);
        return "(" + column + " IS NOT NULL AND " + key + comparison + ")";
    }
}
