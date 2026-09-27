package net.coreprotect.database.clickhouse;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

public final class ClickHouseLookupIndex {

    public static final String FAMILIES = "'block','container','entity_container','item','entity_interaction'";
    public static final String METADATA_COLUMNS = "rowid,time,user_id,wid,x,y,z,wid_present,x_present,z_present,type,data,action,rolled_back,amount,entity_spawn_rowid";
    private static final String[] RETAINED_COLUMNS = ("batch_sequence,batch_ordinal,write_version," + METADATA_COLUMNS).split(",");
    private static final UUID ZERO_UUID = new UUID(0, 0);

    private ClickHouseLookupIndex() {
        throw new IllegalStateException("Lookup index class");
    }

    static int familyCode(String family) {
        switch (family) {
            case "block": return 1;
            case "container": return 2;
            case "entity_container": return 3;
            case "item": return 4;
            case "entity_interaction": return 5;
            default: return 0;
        }
    }

    public static String logicalFamily() {
        return "if(family='_batch_receipt' AND lookup_kind IN(1,2),"
                + "transform(x,[1,2,3,4,5,17,18,19,20],['block','container','entity_container','item','entity_interaction','block','container','entity_container','item'],''),family)";
    }

    static boolean append(ClickHouseRowBinaryBuffer rows, ClickHouseFamily family, int partition, boolean state) throws SQLException {
        int code = familyCode(family.getTableName());
        if (code == 0) {
            return false;
        }
        rows.beginRow(RETAINED_COLUMNS);
        rows.set("lookup_kind", state ? 2 : 1);
        rows.set("lookup_wid", rows.get("wid"));
        rows.set("lookup_x", rows.get("x"));
        rows.set("lookup_z", rows.get("z"));
        Object user = rows.get("user_id");
        rows.set("family", ClickHouseSchema.BATCH_RECEIPT_FAMILY);
        rows.set("batch_id", ZERO_UUID);
        rows.set("wid", partition);
        rows.set("x", code + (state ? 16 : 0));
        rows.set("z", state || user == null ? 0 : ((Number) user).intValue());
        rows.commitRow("lookup index", partition);
        return true;
    }

    public static String ready(String table) {
        return "(SELECT ifNull(min(status),1) FROM " + table
                + " FINAL WHERE family='_batch_receipt' AND wid=0 AND x=0 AND z=-1 AND rowid=1 AND lookup_kind=3)";
    }

    public static void setReady(Connection connection, String table, UUID owner, boolean ready) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO " + table + " (family,wid,x,z,rowid,lookup_kind,status,write_version,batch_id)"
                    + " SETTINGS insert_deduplication_token='lookup_gate_" + UUID.randomUUID() + "' VALUES"
                    + " ('_batch_receipt',0,0,-1,1,3," + (ready ? 1 : 0) + "," + ClickHouseSchema.VERSION + ",'" + owner + "')");
        }
    }

    static void recover(Connection connection, String database, String prefix, UUID owner) throws SQLException {
        String table = ClickHouseIdentifiers.qualified(database, prefix + "event_data");
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(
                "SELECT 1 FROM " + table + " FINAL WHERE family='_batch_receipt' AND wid=0 AND x=0 AND z=-1"
                        + " AND rowid=1 AND lookup_kind=3 AND batch_id='" + owner + "' AND status=0 LIMIT 1")) {
            if (!result.next()) {
                return;
            }
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM system.mutations WHERE database=? AND table=? AND NOT is_done LIMIT 1")) {
            statement.setString(1, database);
            statement.setString(2, prefix + "event_data");
            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) {
                    throw new SQLException("ClickHouse maintenance has unfinished mutations; allow them to finish before restarting");
                }
            }
        }
        setReady(connection, table, owner, true);
    }

    static String source(String table, String family, String predicate, String users, long startTime, long endTime, boolean state) {
        int code = familyCode(family);
        String months = monthRange("p.wid", startTime, endTime);
        String filter = predicate.isEmpty() ? "" : " AND (" + predicate + ")";
        String metadata = "SELECT p.* REPLACE('" + family + "' AS family,p.lookup_wid AS wid,p.lookup_x AS x,p.lookup_z AS z) FROM "
                + table + " AS p WHERE p.family='_batch_receipt' AND p.lookup_kind=1 AND p.x=" + code + months
                + (users.isEmpty() ? "" : " AND p.z IN(toInt32(" + users.replace(",", "),toInt32(") + "))") + filter;
        if (state && code != 5) {
            String updates = "SELECT rowid,rolled_back FROM " + table + " FINAL WHERE family='_batch_receipt' AND lookup_kind=2 AND x="
                    + (code + 16) + " AND z=0" + monthRange("wid", startTime, endTime)
                    + (startTime > 0 ? " AND time>" + startTime : "") + (endTime > 0 ? " AND time<=" + endTime : "");
            metadata = "SELECT base.* REPLACE(coalesce(state.rolled_back,base.rolled_back) AS rolled_back) FROM (" + metadata
                    + ") AS base LEFT ANY JOIN (" + updates + ") AS state USING(rowid)";
        }
        return "SELECT * FROM (" + metadata + ") WHERE " + ready(table) + "=1 UNION ALL SELECT * FROM ("
                + ClickHouseSchema.eventSource(table, family, predicate) + ") WHERE " + ready(table) + "!=1";
    }

    private static String monthRange(String column, long startTime, long endTime) {
        return (startTime > 0 ? " AND " + column + ">=toYYYYMM(toDateTime(" + startTime + ",'UTC'))" : "")
                + (endTime > 0 ? " AND " + column + "<=toYYYYMM(toDateTime(" + endTime + ",'UTC'))" : "");
    }
}
