package net.coreprotect.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ObjectInputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

class PlayProMigrationCompatibilityTest {

    private static final Set<String> EXPECTED_VIEWS = Set.of(
            "art_map", "block", "chat", "command", "container", "entity_container",
            "entity_interaction", "item", "database_lock", "entity", "entity_spawn",
            "entity_map", "material_map", "blockdata_map", "session", "sign", "skull",
            "user", "username_log", "version", "world");
    private static final Set<String> BINARY_VIEWS = Set.of(
            "block", "container", "entity_container", "entity_interaction", "item", "entity", "entity_spawn");

    @Test
    void generatesTheCompletePlayProCompatibilityViewSet() {
        List<String> statements = PlayProMigrationCommand.compatibilityViewSql("coreprotect_art", "co_");
        Pattern namePattern = Pattern.compile("CREATE OR REPLACE VIEW `coreprotect_art`\\.`co_([a-z_]+)`");
        Set<String> names = new HashSet<>();
        Map<String, String> byName = new HashMap<>();

        for (String statement : statements) {
            Matcher matcher = namePattern.matcher(statement);
            assertTrue(matcher.find(), statement);
            names.add(matcher.group(1));
            byName.put(matcher.group(1), statement);
        }

        assertEquals(EXPECTED_VIEWS, names);
        assertEquals(EXPECTED_VIEWS.size(), statements.size());
        for (String name : BINARY_VIEWS) {
            assertTrue(byName.get(name).contains("CAST([], 'Array(Int8)')"), name);
            assertTrue(byName.get(name).contains("arrayConcat([toInt8(0)]"), name);
        }
        assertEquals(2, occurrences(byName.get("block"), "CAST([], 'Array(Int8)')"));
    }

    @Test
    void convertsForkJsonNumbersToSerializablePlayProValues() throws Exception {
        byte[] converted = PlayProMetadataRepairCommand.convertLegacyEntityData(
                "entity_spawn", "[null,true,\"OAK\",[{\"amount\":1.0}],null]");

        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(converted))) {
            List<?> values = assertInstanceOf(List.class, input.readObject());
            List<?> inventory = assertInstanceOf(List.class, values.get(3));
            Map<?, ?> item = assertInstanceOf(Map.class, inventory.get(0));
            assertInstanceOf(Integer.class, item.get("amount"));
        }
    }

    @Test
    void convertsForkSnbtEntityRowsToValidDefaultPlayProState() throws Exception {
        byte[] converted = PlayProMetadataRepairCommand.convertLegacyEntityData("entity", "{Data:1b}");

        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(converted))) {
            List<?> values = assertInstanceOf(List.class, input.readObject());
            assertEquals(7, values.size());
            assertInstanceOf(List.class, values.get(2));
        }
    }

    @Test
    void convertsForkCommandBlockMetadata() throws Exception {
        byte[] converted = PlayProMetadataRepairCommand.convertBlockMeta("{\"command\":\"say migrated\"}");

        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(converted))) {
            List<?> values = assertInstanceOf(List.class, input.readObject());
            assertEquals(List.of("say migrated"), values);
        }
    }

    @Test
    void restoresJavaSerializationBytesStoredAsAnIsoString() {
        byte[] original = new byte[] { (byte) 0xAC, (byte) 0xED, 0, 5, 0x74, 0, 3, 0x66, 0x6f, 0x6f };
        String storedValue = new String(original, StandardCharsets.ISO_8859_1);

        assertArrayEquals(original, PlayProMetadataRepairCommand.restoreIsoBlob(storedValue));
    }

    private static int occurrences(String value, String needle) {
        int result = 0;
        int offset = 0;
        while ((offset = value.indexOf(needle, offset)) >= 0) {
            result++;
            offset += needle.length();
        }
        return result;
    }
}
