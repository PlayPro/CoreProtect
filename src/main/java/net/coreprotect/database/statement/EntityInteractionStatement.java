package net.coreprotect.database.statement;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;

import org.bukkit.Location;

import net.coreprotect.config.ConfigHandler;
import net.coreprotect.model.entity.EntityInteraction;
import net.coreprotect.model.entity.EntitySpawnIdentity;
import net.coreprotect.utility.EntityUtils;
import net.coreprotect.utility.WorldUtils;

public final class EntityInteractionStatement {

    private EntityInteractionStatement() {
        throw new IllegalStateException("Database class");
    }

    public static PreparedStatement prepareCheckpoint(Connection connection) throws SQLException {
        return connection.prepareStatement("UPDATE " + ConfigHandler.prefix + "entity_spawn SET current_wid=?,x=?,y=?,z=?,yaw=?,pitch=? WHERE rowid=? AND removed=0");
    }

    public static void checkpoint(PreparedStatement statement, EntitySpawnIdentity identity, Location location) throws SQLException {
        statement.setInt(1, WorldUtils.getWorldId(location.getWorld().getName()));
        statement.setDouble(2, location.getX());
        statement.setDouble(3, location.getY());
        statement.setDouble(4, location.getZ());
        statement.setFloat(5, location.getYaw());
        statement.setFloat(6, location.getPitch());
        statement.setInt(7, identity.getRowId());
        if (statement.executeUpdate() != 1) {
            throw new SQLException("Entity interaction could not checkpoint its tracking row");
        }
    }

    public static void insert(PreparedStatement statement, int time, int userId, EntitySpawnIdentity identity, int worldId, int x, int y, int z, EntityInteraction interaction) throws SQLException {
        statement.setInt(1, time);
        statement.setInt(2, userId);
        statement.setInt(3, identity.getRowId());
        statement.setInt(4, worldId);
        statement.setInt(5, x);
        statement.setInt(6, y);
        statement.setInt(7, z);
        statement.setInt(8, EntityUtils.getEntityId(interaction.getEntityType()));
        statement.setInt(9, interaction.getAction().getId());
        byte[] metadata = interaction.getMetadata();
        if (metadata == null) {
            statement.setNull(10, Types.BLOB);
        }
        else {
            statement.setBytes(10, metadata);
        }
        statement.setInt(11, 0);
        if (statement.executeUpdate() != 1) {
            throw new SQLException("Entity interaction insert did not insert one row");
        }
    }
}
