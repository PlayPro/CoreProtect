package net.coreprotect.database;

import java.util.Locale;

public enum DatabaseType {

    CLICKHOUSE("ClickHouse"),
    DUCKDB("DuckDB"),
    MYSQL("MySQL"),
    SQLITE("SQLite");

    private final String displayName;

    DatabaseType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getUserColumn() {
        if (isDuckDB()) {
            return "\"user\"";
        }
        return isClickHouse() ? "`user`" : "user";
    }

    public boolean isDuckDB() {
        return this == DUCKDB;
    }

    public boolean isClickHouse() {
        return this == CLICKHOUSE;
    }

    public boolean isColumnar() {
        return isClickHouse() || isDuckDB();
    }

    public boolean isEmbedded() {
        return isDuckDB() || isSQLite();
    }

    public boolean isMySQL() {
        return this == MYSQL;
    }

    public boolean isSQLite() {
        return this == SQLITE;
    }

    public static DatabaseType parse(String value, boolean legacyMySQL) {
        if (value == null) {
            return legacyMySQL ? MYSQL : SQLITE;
        }

        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Database engine cannot be empty");
        }
        try {
            return valueOf(normalized.toUpperCase(Locale.ROOT));
        }
        catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unsupported database engine: " + value, exception);
        }
    }

}
