package net.coreprotect.database.clickhouse;

import net.coreprotect.config.ConfigHandler;

public final class ClickHouseLookupQuery {

    private ClickHouseLookupQuery() {
        throw new IllegalStateException("Utility class");
    }

    public static String currentEventTable() {
        return ConfigHandler.prefix + "event_data FINAL";
    }

    public static String currentEventPredicate(String family, String predicate) {
        ClickHouseFamily.fromTableName(family);
        return "family='" + family + "' AND " + predicate;
    }
}
