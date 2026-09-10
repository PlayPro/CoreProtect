package net.coreprotect.database.clickhouse;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import net.coreprotect.database.PurgePolicy;

public enum ClickHouseFamily {

    ART_MAP("art_map"),
    BLOCK("block"),
    CHAT("chat"),
    COMMAND("command"),
    CONTAINER("container"),
    ENTITY_CONTAINER("entity_container"),
    ENTITY_INTERACTION("entity_interaction"),
    ITEM("item"),
    DATABASE_LOCK("database_lock"),
    ENTITY("entity"),
    ENTITY_SPAWN("entity_spawn"),
    ENTITY_MAP("entity_map"),
    MATERIAL_MAP("material_map"),
    BLOCKDATA_MAP("blockdata_map"),
    SESSION("session"),
    SIGN("sign"),
    SKULL("skull"),
    USER("user"),
    USERNAME_LOG("username_log"),
    VERSION("version"),
    WORLD("world");

    private static final Map<String, ClickHouseFamily> BY_TABLE_NAME;

    static {
        Map<String, ClickHouseFamily> families = new LinkedHashMap<>();
        for (ClickHouseFamily family : values()) {
            families.put(family.tableName, family);
        }
        BY_TABLE_NAME = Collections.unmodifiableMap(families);
    }

    private final String tableName;

    ClickHouseFamily(String tableName) {
        this.tableName = tableName;
    }

    public String getTableName() {
        return tableName;
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
