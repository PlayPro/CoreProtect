package net.coreprotect.database.clickhouse;

import net.coreprotect.config.ConfigHandler;

public final class ClickHouseLookupQuery {

    private ClickHouseLookupQuery() {
        throw new IllegalStateException("Utility class");
    }

    public static String currentEventTable(String family) {
        ClickHouseFamily.fromTableName(family);
        return ConfigHandler.prefix + "event_data FINAL";
    }

    public static String currentEventPredicate(String family, String predicate) {
        ClickHouseFamily.fromTableName(family);
        return "family='" + family + "' AND " + predicate;
    }

    public static String userProjection() {
        return "user_id AS `user`";
    }
}
