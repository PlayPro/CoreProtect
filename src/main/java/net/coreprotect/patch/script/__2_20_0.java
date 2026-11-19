package net.coreprotect.patch.script;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;

import org.bukkit.entity.EntityType;

import net.coreprotect.config.ConfigHandler;
import net.coreprotect.patch.Patch;
import net.coreprotect.utility.ErrorReporter;

public class __2_20_0 {

    protected static boolean patch(Statement statement) {
        try {
            String prefix = ConfigHandler.prefix;
            boolean mysql = ConfigHandler.databaseType.isMySQL();
            if (mysql) {
                statement.executeUpdate("ALTER TABLE " + prefix + "command MODIFY message VARCHAR(16000), CONVERT TO CHARACTER SET utf8mb4");
                if (!Patch.continuePatch()) {
                    return false;
                }
                statement.executeUpdate("ALTER TABLE " + prefix + "chat MODIFY message VARCHAR(16000), CONVERT TO CHARACTER SET utf8mb4");
            }
            LegacyPatchSchema.ensureColumn(statement, prefix + "sign", "data", mysql ? "TINYINT" : "INTEGER");
            if (!Patch.continuePatch()) {
                return false;
            }
            LegacyPatchTransaction.run(statement, () -> migrateSpawners(statement, prefix));
            return ConfigHandler.loadEntities(statement);
        }
        catch (Exception e) {
            ErrorReporter.report(e);
            return false;
        }
    }

    private static void migrateSpawners(Statement statement, String prefix) throws SQLException {
        LegacyPatchReferences entities = new LegacyPatchReferences(statement, prefix + "entity_map", "entity");
        String rows = "SELECT rowid,data FROM " + prefix + "block WHERE type = (SELECT id FROM " + prefix
                + "material_map WHERE material='minecraft:spawner' LIMIT 1) ORDER BY rowid ASC";
        try (PreparedStatement update = statement.getConnection().prepareStatement("UPDATE " + prefix + "block SET data=? WHERE rowid=?");
                ResultSet spawners = statement.executeQuery(rows)) {
            while (spawners.next()) {
                EntityType type = spawnerType(spawners.getInt("data"));
                update.setInt(1, entities.resolve(statement, type.name().toLowerCase(Locale.ROOT)));
                update.setLong(2, spawners.getLong("rowid"));
                update.executeUpdate();
            }
        }
    }

    private static EntityType spawnerType(int data) {
        switch (data) {
            case 1:
                return EntityType.ZOMBIE;
            case 2:
                return EntityType.SKELETON;
            case 3:
                return EntityType.SPIDER;
            case 4:
                return EntityType.CAVE_SPIDER;
            case 5:
                return EntityType.SILVERFISH;
            case 6:
                return EntityType.BLAZE;
            default:
                return EntityType.PIG;
        }
    }
}
