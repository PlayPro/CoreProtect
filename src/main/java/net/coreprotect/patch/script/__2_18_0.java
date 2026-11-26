package net.coreprotect.patch.script;

import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.Rotatable;

import net.coreprotect.config.Config;
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.patch.Patch;
import net.coreprotect.utility.BlockUtils;
import net.coreprotect.utility.ErrorReporter;

public class __2_18_0 {

    private static final String LEGACY_PREFIX = "minecraft:legacy_";

    protected static boolean patch(Statement statement) {
        return patch(statement, true);
    }

    static boolean patch(Statement statement, boolean createIndexes) {
        boolean referencesChanged = false;
        try {
            String prefix = ConfigHandler.prefix;
            if (Config.getGlobal().MYSQL) {
                try {
                    statement.executeUpdate("ALTER TABLE " + prefix + "block ADD COLUMN blockdata BLOB");
                }
                catch (SQLException e) {
                    if (e.getErrorCode() != 1060) {
                        throw e;
                    }
                }
            }
            if (!Patch.continuePatch()) {
                return false;
            }

            List<LegacyMaterial> legacyMaterials = new ArrayList<>();
            try (ResultSet rows = statement.executeQuery("SELECT rowid,id,material FROM " + prefix + "material_map WHERE material LIKE 'minecraft:legacy_%' ORDER BY rowid")) {
                while (rows.next()) {
                    String name = rows.getString("material");
                    if (name != null && name.startsWith(LEGACY_PREFIX)) {
                        Material material = modernMaterial(name);
                        if (material != null && !material.isLegacy()) {
                            legacyMaterials.add(new LegacyMaterial(rows.getLong("rowid"), rows.getInt("id"), material));
                        }
                    }
                }
            }
            if (!legacyMaterials.isEmpty()) {
                LegacyPatchReferences materials = new LegacyPatchReferences(statement, prefix + "material_map", "material");
                LegacyPatchReferences blockdata = new LegacyPatchReferences(statement, prefix + "blockdata_map", "data");
                for (LegacyMaterial entry : legacyMaterials) {
                    LegacyPatchTransaction.run(statement, () -> migrateMaterial(statement, prefix, entry, materials, blockdata));
                    referencesChanged = true;
                    if (!Patch.continuePatch()) {
                        return false;
                    }
                }
            }
            referencesChanged = false;
            if (!ConfigHandler.loadMaterials(statement) || !ConfigHandler.loadBlockdata(statement)) {
                return false;
            }
            if (createIndexes) {
                createIndexes(statement, prefix);
            }
            return true;
        }
        catch (Exception e) {
            ErrorReporter.report(e);
            return false;
        }
        finally {
            if (referencesChanged) {
                ConfigHandler.loadMaterials(statement);
                ConfigHandler.loadBlockdata(statement);
            }
        }
    }

    static Material modernMaterial(String materialName) {
        switch (materialName) {
            case "minecraft:legacy_wall_sign":
                return Material.OAK_WALL_SIGN;
            case "minecraft:legacy_skull":
                return Material.SKELETON_SKULL;
            case "minecraft:legacy_long_grass":
                Material grass = Material.matchMaterial("minecraft:short_grass", false);
                return grass == null ? Material.matchMaterial("minecraft:grass", false) : grass;
            case "minecraft:legacy_double_plant":
                return Material.TALL_GRASS;
            default:
                Material legacyMaterial = Material.matchMaterial(materialName, false);
                if (legacyMaterial == null || !legacyMaterial.isLegacy()) {
                    return null;
                }
                Material modernMaterial = Bukkit.getUnsafe().fromLegacy(legacyMaterial);
                return modernMaterial == Material.AIR && legacyMaterial != Material.LEGACY_AIR ? null : modernMaterial;
        }
    }

    private static void migrateMaterial(Statement statement, String prefix, LegacyMaterial entry, LegacyPatchReferences materials,
            LegacyPatchReferences blockdata) throws SQLException {
        int modernId = materials.resolve(statement, entry.material.getKey().toString());
        try (PreparedStatement blocks = statement.getConnection().prepareStatement("SELECT rowid,data,blockdata FROM " + prefix + "block WHERE type = ? AND action < 3");
                PreparedStatement updateBlock = statement.getConnection().prepareStatement("UPDATE " + prefix + "block SET type = ?, blockdata = ? WHERE rowid = ?");
                PreparedStatement updateContainers = statement.getConnection().prepareStatement("UPDATE " + prefix + "container SET type = ? WHERE type = ?");
                PreparedStatement deleteMaterial = statement.getConnection().prepareStatement("DELETE FROM " + prefix + "material_map WHERE rowid = ?")) {
            blocks.setInt(1, entry.id);
            try (ResultSet rows = blocks.executeQuery()) {
                int batchSize = 0;
                while (rows.next()) {
                    int data = rows.getInt("data");
                    Material material = entry.material == Material.WHITE_WOOL ? getWoolColor(data) : entry.material;
                    int materialId = material == entry.material ? modernId : materials.resolve(statement, material.getKey().toString());
                    byte[] encoded = rows.getBytes("blockdata");
                    if (encoded == null && material.isBlock()) {
                        encoded = encodeBlockData(statement, material, data, blockdata);
                    }
                    updateBlock.setInt(1, materialId);
                    updateBlock.setBytes(2, encoded);
                    updateBlock.setLong(3, rows.getLong("rowid"));
                    updateBlock.addBatch();
                    if (++batchSize == 1000) {
                        updateBlock.executeBatch();
                        batchSize = 0;
                    }
                }
                if (batchSize > 0) {
                    updateBlock.executeBatch();
                }
            }
            updateContainers.setInt(1, modernId);
            updateContainers.setInt(2, entry.id);
            updateContainers.executeUpdate();
            deleteMaterial.setLong(1, entry.rowId);
            deleteMaterial.executeUpdate();
        }
    }

    private static byte[] encodeBlockData(Statement statement, Material material, int data, LegacyPatchReferences blockdata) throws SQLException {
        BlockData converted;
        try {
            converted = Bukkit.getUnsafe().fromLegacy(material, (byte) data);
        }
        catch (IllegalArgumentException | UnsupportedOperationException e) {
            return null;
        }
        if (converted == null) {
            return null;
        }
        if (material == Material.OAK_WALL_SIGN && converted instanceof Directional) {
            ((Directional) converted).setFacing(getLegacyDirection(data));
        }
        else if (material == Material.SKELETON_SKULL && converted instanceof Rotatable) {
            ((Rotatable) converted).setRotation(getLegacyRotation(data));
        }
        BlockData defaults = BlockUtils.createBlockData(material);
        String properties = converted.getAsString();
        String prefix = material.getKey() + "[";
        if (defaults == null || defaults.getAsString().equals(properties) || !properties.startsWith(prefix) || !properties.endsWith("]")) {
            return null;
        }
        StringJoiner encoded = new StringJoiner(",");
        for (String property : properties.substring(prefix.length(), properties.length() - 1).split(",")) {
            if (!property.isEmpty()) {
                encoded.add(Integer.toString(blockdata.resolve(statement, property)));
            }
        }
        return encoded.length() == 0 ? null : encoded.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static void createIndexes(Statement statement, String prefix) throws SQLException {
        for (String table : List.of("art_map", "entity_map", "material_map", "world", "blockdata_map")) {
            if (Config.getGlobal().MYSQL) {
                try {
                    statement.executeUpdate("ALTER TABLE " + prefix + table + " ADD INDEX(id)");
                }
                catch (SQLException e) {
                    if (e.getErrorCode() != 1061) {
                        throw e;
                    }
                }
            }
            else {
                statement.executeUpdate("CREATE INDEX IF NOT EXISTS " + table + "_id_index ON " + prefix + table + "(id)");
            }
        }
    }

    private static final class LegacyMaterial {
        private final long rowId;
        private final int id;
        private final Material material;

        private LegacyMaterial(long rowId, int id, Material material) {
            this.rowId = rowId;
            this.id = id;
            this.material = material;
        }
    }

    protected static Material getWoolColor(int data) {
        switch (data) {
            case 0:
                return Material.WHITE_WOOL;
            case 1:
                return Material.ORANGE_WOOL;
            case 2:
                return Material.MAGENTA_WOOL;
            case 3:
                return Material.LIGHT_BLUE_WOOL;
            case 4:
                return Material.YELLOW_WOOL;
            case 5:
                return Material.LIME_WOOL;
            case 6:
                return Material.PINK_WOOL;
            case 7:
                return Material.GRAY_WOOL;
            case 8:
                return Material.LIGHT_GRAY_WOOL;
            case 9:
                return Material.CYAN_WOOL;
            case 10:
                return Material.PURPLE_WOOL;
            case 11:
                return Material.BLUE_WOOL;
            case 12:
                return Material.BROWN_WOOL;
            case 13:
                return Material.GREEN_WOOL;
            case 14:
                return Material.RED_WOOL;
            case 15:
                return Material.BLACK_WOOL;
            default:
                return Material.WHITE_WOOL;
        }
    }

    private static BlockFace getLegacyDirection(int data) {
        switch (data) {
            case 2:
                return BlockFace.NORTH;
            case 3:
                return BlockFace.SOUTH;
            case 4:
                return BlockFace.WEST;
            case 5:
                return BlockFace.EAST;
            default:
                return BlockFace.NORTH;
        }
    }

    private static BlockFace getLegacyRotation(int data) {
        switch (data) {
            case 1:
                return BlockFace.SOUTH_SOUTH_WEST;
            case 2:
                return BlockFace.SOUTH_WEST;
            case 3:
                return BlockFace.WEST_SOUTH_WEST;
            case 4:
                return BlockFace.WEST;
            case 5:
                return BlockFace.WEST_NORTH_WEST;
            case 6:
                return BlockFace.NORTH_WEST;
            case 7:
                return BlockFace.NORTH_NORTH_WEST;
            case 8:
                return BlockFace.NORTH;
            case 9:
                return BlockFace.NORTH_NORTH_EAST;
            case 10:
                return BlockFace.NORTH_EAST;
            case 11:
                return BlockFace.EAST_NORTH_EAST;
            case 12:
                return BlockFace.EAST;
            case 13:
                return BlockFace.EAST_SOUTH_EAST;
            case 14:
                return BlockFace.SOUTH_EAST;
            case 15:
                return BlockFace.SOUTH_SOUTH_EAST;
            default:
                return BlockFace.SOUTH;
        }
    }

}
