package net.coreprotect.database;

import java.util.Objects;

import net.coreprotect.config.ConfigHandler;

public final class StatementUtils {
	private StatementUtils() {
	}

	/**
	 * Creates a table name with the configured prefix and provided table name.
	 * 
	 * @param table The SQL table name.
	 * @return The prepended table name with the table prefix.
	 */
	public static String getTableName(String table) {
		Objects.requireNonNull(table, "table cannot be null");
		return "\"" + ConfigHandler.prefix + table + "\"";
	}

	/**
	 * Creates a SQL "LIMIT ... OFFSET ..." query fragment compatible with multiple
	 * databases.
	 * 
	 * @param limit  The number of results to limit.
	 * @param offset The offset for the results.
	 * @return The SQL limit statement as a {@link String}.
	 */
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