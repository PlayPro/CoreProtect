package net.coreprotect.patch.script;

import net.coreprotect.config.Config;
import net.coreprotect.config.ConfigHandler;
import org.bukkit.Art;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;

import java.sql.Statement;
import java.util.Locale;

public class __2_11_0 {

    protected static boolean patch(Statement statement) {
        try {
            if (Config.getGlobal().MYSQL) {
                statement.executeUpdate("START TRANSACTION");
            }
            else {
                statement.executeUpdate("BEGIN TRANSACTION");
            }

            for (Art artType : Art.values()) {
                Integer type = artType.getId();
                String name = artType.toString().toLowerCase(Locale.ROOT);
                statement.executeUpdate("INSERT INTO " + ConfigHandler.prefix + "art_map (id, art) VALUES ('" + type + "', '" + name + "')");
                ConfigHandler.art.put(name, type);
                ConfigHandler.artReversed.put(type, name);
                if (type > ConfigHandler.artId) {
                    ConfigHandler.artId = type;
                }
            }

            for (EntityType entityType : EntityType.values()) {
                Integer type = (int) entityType.getTypeId();
                String name = entityType.toString().toLowerCase(Locale.ROOT);
                statement.executeUpdate("INSERT INTO " + ConfigHandler.prefix + "entity_map (id, entity) VALUES ('" + type + "', '" + name + "')");
                ConfigHandler.entities.put(name, type);
                ConfigHandler.entitiesReversed.put(type, name);
                if (type > ConfigHandler.entityId) {
                    ConfigHandler.entityId = type;
                }
            }

            for (Material material : Material.values()) {
                Integer type = material.getId();
                String name = material.toString().toLowerCase(Locale.ROOT);
                statement.executeUpdate("INSERT INTO " + ConfigHandler.prefix + "material_map (id, material) VALUES ('" + type + "', '" + name + "')");
                ConfigHandler.materials.put(name, type);
                ConfigHandler.materialsReversed.put(type, name);
                if (type > ConfigHandler.materialId) {
                    ConfigHandler.materialId = type;
                }
            }

            if (Config.getGlobal().MYSQL) {
                statement.executeUpdate("COMMIT");
            }
            else {
                statement.executeUpdate("COMMIT TRANSACTION");
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        return true;
    }

}
