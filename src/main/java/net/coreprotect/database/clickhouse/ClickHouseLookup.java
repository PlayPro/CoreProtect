package net.coreprotect.database.clickhouse;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public final class ClickHouseLookup {

    private static final long MAX_JOIN_BYTES = 64L * 1024 * 1024;

    private final String prefix;
    private final boolean state;
    private final boolean metadata;
    private final String users;
    private final long startTime;
    private final long endTime;
    private boolean joined;
    private long joinBytes;

    public ClickHouseLookup(String prefix, boolean state, boolean metadata, String users, long startTime, long endTime) {
        this.prefix = prefix;
        this.state = state;
        this.metadata = metadata;
        this.users = users;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public String lookupTable(String table, String predicate) {
        int code = ClickHouseLookupIndex.familyCode(table);
        if (!metadata || code == 0) {
            return ClickHouseSchema.lookupTable(prefix, table, predicate);
        }
        joined |= state && code != 5;
        String eventTable = ClickHouseIdentifiers.quote(prefix + "event_data", "ClickHouse table");
        return "(SELECT " + ClickHouseSchema.rollbackProjection(table) + ",e.rolled_back AS rolled_back"
                + ClickHouseSchema.locationKeys(ClickHouseFamily.fromTableName(table)) + " FROM ("
                + ClickHouseLookupIndex.source(eventTable, table, predicate, users, startTime, endTime, state) + ") AS e)";
    }

    public String query(Statement statement, String sql, boolean spill) throws SQLException {
        String settings = " SETTINGS enable_shared_storage_snapshot_in_query=1,set_overflow_mode='throw'";
        if (joined) {
            if (joinBytes == 0) {
                try (Statement options = statement.getConnection().createStatement();
                        ResultSet result = options.executeQuery("SELECT getSetting('max_bytes_in_join')")) {
                    if (!result.next()) {
                        throw new SQLException("ClickHouse did not return the configured join limit");
                    }
                    long configured = result.getLong(1);
                    joinBytes = configured > 0 ? Math.min(configured, MAX_JOIN_BYTES) : MAX_JOIN_BYTES;
                }
            }
            settings += ",query_plan_join_swap_table=0,join_overflow_mode='throw',max_bytes_in_join=" + joinBytes
                    + ",join_algorithm='" + (spill ? "grace_hash" : "hash") + "'";
        }
        return sql + settings;
    }

    public boolean shouldRetry(SQLException error) {
        return joined && error.getErrorCode() == 191 && error.getMessage() != null
                && error.getMessage().contains("Limit for JOIN exceeded");
    }
}
