package net.coreprotect.database.statement;

import org.bukkit.Location;

import net.coreprotect.database.ConsumerWriteBatch;
import net.coreprotect.model.entity.EntityInteraction;
import net.coreprotect.model.entity.EntitySpawnIdentity;
import net.coreprotect.utility.EntityUtils;
import net.coreprotect.utility.WorldUtils;

public final class EntityInteractionStatement {

    private EntityInteractionStatement() {
        throw new IllegalStateException("Database class");
    }

    public static boolean checkpoint(ConsumerWriteBatch batch, EntitySpawnIdentity identity, Location location) throws Exception {
        return batch.checkpointEntitySpawn(identity.getRowId(), WorldUtils.getWorldId(location.getWorld().getName()), location.getX(), location.getY(), location.getZ(), location.getYaw(), location.getPitch());
    }

    public static void insert(ConsumerWriteBatch batch, int time, int userId, EntitySpawnIdentity identity, int worldId, int x, int y, int z, EntityInteraction interaction) throws Exception {
        batch.addEntityInteraction(time, userId, identity.getRowId(), worldId, x, y, z, EntityUtils.getEntityId(interaction.getEntityType()), interaction.getAction().getId(), interaction.getMetadata(), 0);
    }
}
