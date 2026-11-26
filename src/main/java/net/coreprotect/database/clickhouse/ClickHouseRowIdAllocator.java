package net.coreprotect.database.clickhouse;

import java.sql.SQLException;

@FunctionalInterface
public interface ClickHouseRowIdAllocator {

    long nextRowId(ClickHouseFamily family) throws SQLException;

    default void observeRowId(ClickHouseFamily family, long rowId) {
    }

}
