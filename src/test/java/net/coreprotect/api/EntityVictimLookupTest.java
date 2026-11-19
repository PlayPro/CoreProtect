package net.coreprotect.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mockStatic;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.MockedStatic;

import net.coreprotect.api.result.EntityResult;
import net.coreprotect.config.Config;
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.database.Database;
import net.coreprotect.database.DatabaseType;
import net.coreprotect.model.action.LookupActions;
import net.coreprotect.utility.ErrorReporter;
import net.coreprotect.utility.SystemUtils;

class EntityVictimLookupTest {
    @TempDir
    Path directory;

    @ParameterizedTest
    @EnumSource(value = DatabaseType.class, names = { "SQLITE", "DUCKDB" })
    void filtersActorAndVictimBeforePagination(DatabaseType backend) throws Exception {
        try (Fixture ignored = fixture(backend)) {
            LookupOptions first = LookupOptions.builder().time(86400).users(List.of("KillerOne"))
                    .victims(List.of("VictimOne")).limit(0, 1).build();
            List<EntityResult> result = EntityAPI.performLookup(first);
            assertEquals(List.of(14), rowIds(result));
            assertEquals("KillerOne", result.get(0).getPlayer());
            assertEquals("VictimOne", result.get(0).getVictim());
            assertEquals(EntityType.PLAYER, result.get(0).getEntityType());
            assertEquals("world", result.get(0).worldName());

            LookupOptions second = LookupOptions.builder().time(86400).users(List.of("KillerOne"))
                    .victims(List.of("VictimOne")).limit(1, 1).build();
            assertEquals(List.of(11), rowIds(EntityAPI.performLookup(second)));
        }
    }

    @ParameterizedTest
    @EnumSource(value = DatabaseType.class, names = { "SQLITE", "DUCKDB" })
    void resolvesVictimNamesCaseInsensitivelyAndCombinesVictims(DatabaseType backend) throws Exception {
        try (Fixture ignored = fixture(backend)) {
            LookupOptions options = LookupOptions.builder().time(86400)
                    .victims(List.of("vIcTiMoNe", "VICTIMTWO")).build();
            assertEquals(List.of(14, 13, 12, 11), rowIds(EntityAPI.performLookup(options)));
        }
    }

    @ParameterizedTest
    @EnumSource(value = DatabaseType.class, names = { "SQLITE", "DUCKDB" })
    void excludesVictimsWithoutLosingOtherEntityEvents(DatabaseType backend) throws Exception {
        try (Fixture ignored = fixture(backend)) {
            LookupOptions options = LookupOptions.builder().time(86400)
                    .excludeVictims(List.of("VictimOne")).build();
            List<EntityResult> result = EntityAPI.performLookup(options);
            assertEquals(List.of(23, 21, 20, 19, 18, 17, 16, 15, 12), rowIds(result));
            assertEquals("VictimTwo", result.get(result.size() - 1).getVictim());
            result.subList(0, result.size() - 1).forEach(row -> assertNull(row.getVictim()));
        }
    }

    @ParameterizedTest
    @EnumSource(value = DatabaseType.class, names = { "SQLITE", "DUCKDB" })
    void exclusionWinsAndEntityActionFiltersStillApply(DatabaseType backend) throws Exception {
        try (Fixture ignored = fixture(backend)) {
            LookupOptions options = LookupOptions.builder().time(86400)
                    .victims(List.of("VictimOne", "VictimTwo"))
                    .excludeVictims(List.of("VictimOne")).entityActions(List.of(EntityAction.KILL))
                    .includeEntities(List.of(EntityType.PLAYER)).build();
            assertEquals(List.of(12), rowIds(EntityAPI.performLookup(options)));
            assertEquals(List.of(), EntityAPI.performLookup(LookupOptions.builder().time(86400)
                    .victims(List.of("VictimOne")).entityActions(List.of(EntityAction.SPAWN)).build()));
        }
    }

    @ParameterizedTest
    @EnumSource(value = DatabaseType.class, names = { "SQLITE", "DUCKDB" })
    void handlesUnknownVictimsWithoutWideningInclusion(DatabaseType backend) throws Exception {
        try (Fixture fixture = fixture(backend)) {
            assertEquals(List.of(), EntityAPI.performLookup(LookupOptions.builder()
                    .victims(List.of("NeverSeen")).build()));
            assertEquals(rowIds(EntityAPI.performLookup(LookupOptions.builder().time(86400).build())),
                    rowIds(EntityAPI.performLookup(LookupOptions.builder().time(86400)
                            .excludeVictims(List.of("NeverSeen")).build())));
            assertEquals(List.of(14, 13, 11), rowIds(EntityAPI.performLookup(LookupOptions.builder()
                    .time(86400).victims(List.of("NeverSeen", "VictimOne")).build())));
            try (Statement statement = fixture.connection.createStatement();
                    var rows = statement.executeQuery("SELECT COUNT(*) FROM co_user")) {
                rows.next();
                assertEquals(4, rows.getInt(1), "Lookup must not insert unknown players");
            }
        }
    }

    @ParameterizedTest
    @EnumSource(value = DatabaseType.class, names = { "SQLITE", "DUCKDB" })
    void onlyDecodesVictimsForPlayerKillsAndRetainsLegacyResults(DatabaseType backend) throws Exception {
        try (Fixture ignored = fixture(backend)) {
            List<EntityResult> result = EntityAPI.performLookup(LookupOptions.builder().time(86400).build());
            assertEquals(List.of(23, 21, 20, 19, 18, 17, 16, 15, 14, 13, 12, 11), rowIds(result));
            for (EntityResult row : result) {
                if (row.getX() >= 15) {
                    assertNull(row.getVictim(), "Invalid IDs, mob metadata and spawn data are not victims");
                }
            }
            assertEquals(List.of(14, 13, 12, 11), rowIds(EntityAPI.performLookup(LookupOptions.builder()
                    .time(86400).victims(List.of("VictimOne", "VictimTwo")).build())));
        }
    }

    @Test
    void victimOptionsAreImmutableAndRejectWildcards() {
        List<String> values = new ArrayList<>(List.of("VictimOne"));
        LookupOptions options = LookupOptions.builder().victims(values).excludeVictims(values).build();
        values.set(0, "Other");
        assertEquals(List.of("VictimOne"), options.getVictims());
        assertEquals(List.of("VictimOne"), options.getExcludeVictims());
        assertThrows(UnsupportedOperationException.class, () -> options.getVictims().add("Other"));
        assertThrows(UnsupportedOperationException.class, () -> options.getExcludeVictims().clear());
        for (String invalid : List.of("", "  ", "#global", "#zombie")) {
            assertThrows(IllegalArgumentException.class, () -> LookupOptions.builder().victims(List.of(invalid)));
            assertThrows(IllegalArgumentException.class, () -> LookupOptions.builder().excludeVictims(List.of(invalid)));
        }
        assertThrows(NullPointerException.class, () -> LookupOptions.builder().victims(null));
        assertThrows(NullPointerException.class, () -> LookupOptions.builder().excludeVictims(Arrays.asList("VictimOne", null)));
        assertEquals(List.of(), LookupOptions.builder().build().getVictims());
        assertEquals(List.of(), LookupOptions.builder().build().getExcludeVictims());
    }

    @Test
    void keepsExistingResultConstructorAndRestrictsVictimToPlayerKills() {
        EntityResult original = new EntityResult(123, "KillerOne", "world", 1, 2, 3, 0,
                LookupActions.ENTITY_KILL, 0);
        assertNull(original.getVictim());
        assertEquals("KillerOne", original.getPlayer());
        assertEquals(123000, original.getTimestamp());
        assertFalse(original.isRolledBack());
        assertNull(new EntityResult(123, "KillerOne", "world", 1, 2, 3, 23,
                LookupActions.ENTITY_KILL, 0, "VictimOne").getVictim());
        assertNull(new EntityResult(123, "KillerOne", "world", 1, 2, 3, 0,
                LookupActions.ENTITY_SPAWN, 0, "VictimOne").getVictim());
    }

    private static List<Integer> rowIds(List<EntityResult> rows) {
        return rows.stream().map(EntityResult::getX).collect(Collectors.toList());
    }

    private Fixture fixture(DatabaseType backend) throws Exception {
        String url = backend.isSQLite()
                ? "jdbc:sqlite:file:entity-victims-" + UUID.randomUUID() + "?mode=memory&cache=shared"
                : "jdbc:duckdb:" + directory.resolve("entity-victims.duckdb");
        return new Fixture(backend, url);
    }

    private static final class Fixture implements AutoCloseable {
        final Connection connection;
        private final MockedStatic<Database> database;
        private final MockedStatic<ErrorReporter> errors;
        private final DatabaseType previousBackend;
        private final String previousPrefix;
        private final boolean previousApi;
        private final Map<String, Integer> previousIds;
        private final Map<Integer, String> previousNames;
        private final Map<String, String> previousUuids;
        private final Map<String, String> previousUuidNames;
        private final Map<Integer, String> previousWorlds;

        Fixture(DatabaseType backend, String url) throws Exception {
            SystemUtils.setTestMode(true);
            previousBackend = ConfigHandler.databaseType;
            previousPrefix = ConfigHandler.prefix;
            previousApi = Config.getGlobal().API_ENABLED;
            previousIds = ConfigHandler.playerIdCache;
            previousNames = ConfigHandler.playerIdCacheReversed;
            previousUuids = ConfigHandler.uuidCache;
            previousUuidNames = ConfigHandler.uuidCacheReversed;
            previousWorlds = ConfigHandler.worldsReversed;
            ConfigHandler.databaseType = backend;
            ConfigHandler.prefix = "co_";
            Config.getGlobal().API_ENABLED = true;
            ConfigHandler.playerIdCache = new HashMap<>();
            ConfigHandler.playerIdCacheReversed = new HashMap<>();
            ConfigHandler.uuidCache = new HashMap<>();
            ConfigHandler.uuidCacheReversed = new HashMap<>();
            ConfigHandler.worldsReversed = new HashMap<>(Map.of(1, "world"));
            connection = DriverManager.getConnection(url);
            try (Statement statement = connection.createStatement()) {
                statement.execute("CREATE TABLE co_user (rowid INTEGER PRIMARY KEY, time BIGINT, \"user\" VARCHAR, uuid VARCHAR)");
                statement.execute("INSERT INTO co_user VALUES (1,0,'KillerOne',NULL),(2,0,'OtherKiller',NULL),(3,0,'VictimOne',NULL),(4,0,'VictimTwo',NULL)");
                statement.execute("CREATE TABLE co_block (rowid BIGINT PRIMARY KEY, time BIGINT, \"user\" INTEGER, wid INTEGER, x INTEGER, y INTEGER, z INTEGER, type INTEGER, data BIGINT, action INTEGER, rolled_back INTEGER)");
                statement.execute("CREATE TABLE co_entity_map (id INTEGER, entity VARCHAR)");
                int time = (int) (System.currentTimeMillis() / 1000L);
                for (String row : List.of("11,1,0,3,3", "12,1,0,4,3", "13,2,0,3,3", "14,1,0,3,3",
                        "15,1,23,3,13", "16,1,23,3,3", "17,1,0,NULL,3", "18,1,0,0,3",
                        "19,1,0,-1,3", "20,1,0,999,3", "21,1,0,4294967299,3", "23,1,NULL,3,3")) {
                    String[] parts = row.split(",");
                    statement.execute("INSERT INTO co_block VALUES (" + parts[0] + "," + time + ","
                            + parts[1] + ",1," + parts[0] + ",64,0," + parts[2] + "," + parts[3]
                            + "," + parts[4] + ",0)");
                }
                statement.execute("INSERT INTO co_block VALUES (22," + (time - 200000) + ",1,1,22,64,0,0,3,3,0)");
            }
            errors = mockStatic(ErrorReporter.class, invocation -> {
                throw new AssertionError("Lookup swallowed a database error", (Throwable) invocation.getArgument(0));
            });
            database = mockStatic(Database.class);
            database.when(() -> Database.getConnection(false, 1000)).thenAnswer(invocation -> DriverManager.getConnection(url));
        }

        @Override
        public void close() throws Exception {
            database.close();
            errors.close();
            connection.close();
            ConfigHandler.databaseType = previousBackend;
            ConfigHandler.prefix = previousPrefix;
            Config.getGlobal().API_ENABLED = previousApi;
            ConfigHandler.playerIdCache = previousIds;
            ConfigHandler.playerIdCacheReversed = previousNames;
            ConfigHandler.uuidCache = previousUuids;
            ConfigHandler.uuidCacheReversed = previousUuidNames;
            ConfigHandler.worldsReversed = previousWorlds;
            SystemUtils.setTestMode(false);
        }
    }
}
