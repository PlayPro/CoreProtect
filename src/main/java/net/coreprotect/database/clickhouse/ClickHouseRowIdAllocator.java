package net.coreprotect.database.clickhouse;

@FunctionalInterface
public interface ClickHouseRowIdAllocator {

    long nextRowId(ClickHouseFamily family);

}
