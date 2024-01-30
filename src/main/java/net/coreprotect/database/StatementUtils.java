package net.coreprotect.database;

import java.util.Objects;

import org.jetbrains.annotations.NotNull;

import net.coreprotect.config.ConfigHandler;

public final class StatementUtils {
	private StatementUtils() {
	}

	public static String getTableName(@NotNull String table) {
		Objects.requireNonNull(table, "table cannot be null");
		return "\"" + ConfigHandler.prefix + table + "\"";
	}

	public static String getLimit(int limit, int offset) {
		if (limit <= 0) {
			throw new IllegalArgumentException("limit must be greater than 0");
		}
		if (offset < 0) {
			throw new IllegalArgumentException("offset cannot be negative");
		}
		return "LIMIT " + limit + " OFFSET " + offset;
	}
}