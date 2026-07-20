package net.coreprotect.database.clickhouse;

import java.util.regex.Pattern;

final class ClickHouseIdentifiers {

    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    private ClickHouseIdentifiers() {
        throw new IllegalStateException("Utility class");
    }

    static String requireIdentifier(String value, String name) {
        if (value == null || !IDENTIFIER.matcher(value).matches()) {
            throw new IllegalArgumentException(name + " must be a simple ClickHouse identifier");
        }
        return value;
    }

    static String quote(String value, String name) {
        return "`" + requireIdentifier(value, name) + "`";
    }

    static String qualified(String database, String table) {
        return quote(database, "ClickHouse database") + "." + quote(table, "ClickHouse table");
    }
}
