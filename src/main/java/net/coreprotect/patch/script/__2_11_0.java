package net.coreprotect.patch.script;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

import org.bukkit.Material;
import org.bukkit.entity.EntityType;

import net.coreprotect.config.ConfigHandler;
import net.coreprotect.utility.ErrorReporter;

public class __2_11_0 {

    private static final List<String> LEGACY_ART_BY_ID = List.of("kebab", "aztec", "alban", "aztec2", "bomb", "plant", "wasteland", "pool",
            "courbet", "sea", "sunset", "creebet", "wanderer", "graham", "match", "bust", "stage", "void", "skull_and_roses", "wither",
            "fighters", "pointer", "pigscene", "burning_skull", "skeleton", "donkey_kong");

    protected static boolean patch(Statement statement) {
        try {
            LegacyPatchTransaction.run(statement, () -> {
                populate(statement, "art_map", "art", __2_11_0::legacyArt);
                populate(statement, "entity_map", "entity", __2_11_0::legacyEntities);
                populate(statement, "material_map", "material", __2_11_0::legacyMaterials);
            });
            return ConfigHandler.loadArt(statement) && ConfigHandler.loadEntities(statement) && ConfigHandler.loadMaterials(statement);
        }
        catch (Exception e) {
            ErrorReporter.report(e);
            return false;
        }
    }

    private static void populate(Statement statement, String table, String column, Supplier<Map<Integer, String>> legacyValues) throws SQLException {
        String qualifiedTable = ConfigHandler.prefix + table;
        try (ResultSet existing = statement.executeQuery("SELECT id FROM " + qualifiedTable + " LIMIT 1")) {
            if (existing.next()) {
                return;
            }
        }
        try (PreparedStatement insert = statement.getConnection().prepareStatement("INSERT INTO " + qualifiedTable + " (id," + column + ") VALUES (?,?)")) {
            for (Map.Entry<Integer, String> entry : legacyValues.get().entrySet()) {
                insert.setInt(1, entry.getKey());
                insert.setString(2, entry.getValue());
                insert.addBatch();
            }
            insert.executeBatch();
        }
    }

    private static Map<Integer, String> legacyArt() {
        Map<Integer, String> values = new LinkedHashMap<>();
        for (int id = 0; id < LEGACY_ART_BY_ID.size(); id++) {
            values.put(id, LEGACY_ART_BY_ID.get(id));
        }
        return values;
    }

    private static Map<Integer, String> legacyEntities() {
        Map<Integer, String> values = new LinkedHashMap<>();
        for (EntityType type : EntityType.values()) {
            int id = type.getTypeId();
            if (id >= 0) {
                values.putIfAbsent(id, type.name().toLowerCase(Locale.ROOT));
            }
        }
        return values;
    }

    private static Map<Integer, String> legacyMaterials() {
        Map<Integer, String> values = new LinkedHashMap<>();
        for (Material material : Material.values()) {
            if (material.isLegacy()) {
                values.putIfAbsent(material.getId(), "minecraft:" + material.name().toLowerCase(Locale.ROOT));
            }
        }
        return values;
    }

}
