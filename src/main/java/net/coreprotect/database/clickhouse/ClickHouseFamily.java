package net.coreprotect.database.clickhouse;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import net.coreprotect.database.PurgePolicy;

public enum ClickHouseFamily {

    ART_MAP("art_map"),
    BLOCK("block", true),
    CHAT("chat", true),
    COMMAND("command", true),
    CONTAINER("container", true),
    ENTITY_CONTAINER("entity_container", true),
    ENTITY_INTERACTION("entity_interaction", true),
    ITEM("item", true),
    DATABASE_LOCK("database_lock"),
    ENTITY("entity", true),
    ENTITY_SPAWN("entity_spawn"),
    ENTITY_MAP("entity_map"),
    MATERIAL_MAP("material_map"),
    BLOCKDATA_MAP("blockdata_map"),
    SESSION("session", true),
    SIGN("sign", true),
    SKULL("skull", true),
    USER("user"),
    USERNAME_LOG("username_log"),
    VERSION("version"),
    WORLD("world"),
    VIRTUAL_CONTAINER("virtual_container"),
    VIRTUAL_CONTAINER_MAP("virtual_container_map");

    private static final Map<String, ClickHouseFamily> BY_TABLE_NAME;

    static {
        Map<String, ClickHouseFamily> families = new LinkedHashMap<>();
        for (ClickHouseFamily family : values()) {
            families.put(family.tableName, family);
        }
        BY_TABLE_NAME = Collections.unmodifiableMap(families);
    }

    private final String tableName;
    private final boolean timePartitioned;

    ClickHouseFamily(String tableName) {
        this(tableName, false);
    }

    ClickHouseFamily(String tableName, boolean timePartitioned) {
        this.tableName = tableName;
        this.timePartitioned = timePartitioned;
    }

    public String getTableName() {
        return tableName;
    }

    /**
     * Returns whether rows of this family are partitioned by month. Existing event tables are validated against a
     * partition key that lists these families, so the set is fixed: a family added later stays in partition 0 and is
     * purged by mutation.
     */
    boolean isTimePartitioned() {
        return timePartitioned;
    }

    boolean isPurgeable() {
        return PurgePolicy.isPurgeable(tableName);
    }

    boolean isWorldScoped() {
        return PurgePolicy.isWorldScoped(tableName);
    }

    public static ClickHouseFamily fromTableName(String tableName) {
        ClickHouseFamily family = BY_TABLE_NAME.get(tableName);
        if (family == null) {
            throw new IllegalArgumentException("Unknown ClickHouse event family: " + tableName);
        }
        return family;
    }

}
