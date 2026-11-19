package net.coreprotect.database.clickhouse;

import java.util.regex.Pattern;

public final class ClickHouseIdentifiers {

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

    static String requirePrefix(String prefix) {
        if (prefix == null) {
            throw new IllegalArgumentException("ClickHouse table prefix cannot be null");
        }
        return prefix.isEmpty() ? prefix : requireIdentifier(prefix, "ClickHouse table prefix");
    }

    static String quote(String value, String name) {
        return "`" + requireIdentifier(value, name) + "`";
    }

    public static String qualified(String database, String table) {
        return quote(database, "ClickHouse database") + "." + quote(table, "ClickHouse table");
    }

    static String qualified(String database, String prefix, String suffix) {
        return qualified(database, requirePrefix(prefix) + suffix);
    }
}
