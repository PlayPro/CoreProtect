package net.coreprotect.database.statement;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Base64;

import org.bukkit.Location;

import net.coreprotect.CoreProtect;
import net.coreprotect.model.entity.EntityInteraction;
import net.coreprotect.model.entity.EntitySpawnIdentity;
import net.coreprotect.utility.EntityUtils;

public final class EntityInteractionStatement {

    private EntityInteractionStatement() {
        throw new IllegalStateException("Database class");
    }

    public static void checkpoint(PreparedStatement statement, EntitySpawnIdentity identity, Location location) throws Exception {
        EntitySpawnStatement.checkpoint(statement, identity, location);
    }

    public static void insert(PreparedStatement statement, int time, int userId, EntitySpawnIdentity identity, int worldId, int x, int y, int z, EntityInteraction interaction) throws SQLException {
        statement.setLong(1, CoreProtect.getInstance().rowNumbers().nextRowNumber("entity_interaction", statement.getConnection()));
        statement.setInt(2, time);
        statement.setInt(3, userId);
        statement.setInt(4, identity.getRowId());
        statement.setInt(5, worldId);
        statement.setInt(6, x);
        statement.setInt(7, y);
        statement.setInt(8, z);
        statement.setInt(9, EntityUtils.getEntityId(interaction.getEntityType()));
        statement.setInt(10, interaction.getAction().getId());
        statement.setString(11, serializeMetadata(interaction.getMetadata()));
        statement.setInt(12, 0);
        statement.setInt(13, 0);
        if (statement.executeUpdate() != 1) {
            throw new SQLException("Entity interaction insert did not insert one row");
        }
    }

    private static String serializeMetadata(byte[] metadata) {
        return metadata == null || metadata.length == 0 ? "" : Base64.getEncoder().encodeToString(metadata);
    }
}
