package net.coreprotect.database;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class PurgePolicy {

    private static final List<String> PURGEABLE_TABLES = Collections.unmodifiableList(Arrays.asList("sign", "container", "entity_container", "entity_interaction", "item", "skull", "session", "chat", "command", "entity", "block"));
    private static final List<String> WORLD_SCOPED_TABLES = Collections.unmodifiableList(Arrays.asList("sign", "container", "entity_container", "entity_interaction", "item", "session", "chat", "command", "block"));

    private PurgePolicy() {
        throw new IllegalStateException("Policy class");
    }

    public static List<String> getPurgeableTables() {
        return PURGEABLE_TABLES;
    }

    public static boolean isPurgeable(String table) {
        return PURGEABLE_TABLES.contains(table);
    }

    public static boolean isWorldScoped(String table) {
        return WORLD_SCOPED_TABLES.contains(table);
    }

    public static boolean supportsBlockRestriction(String table) {
        return "block".equals(table);
    }
}
