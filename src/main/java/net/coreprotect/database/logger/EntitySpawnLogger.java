package net.coreprotect.database.logger;


import net.coreprotect.CoreProtect;
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.database.ConsumerWriteBatch;
import net.coreprotect.database.statement.BlockStatement;
import net.coreprotect.database.statement.EntitySpawnStatement;
import net.coreprotect.database.statement.UserStatement;
import net.coreprotect.event.CoreProtectPreLogEvent;
import net.coreprotect.model.action.LookupActions;
import net.coreprotect.model.entity.EntitySpawnData;
import net.coreprotect.model.entity.EntitySpawnIdentity;
import net.coreprotect.utility.EntityUtils;
import net.coreprotect.utility.WorldUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;

public final class EntitySpawnLogger {

    private EntitySpawnLogger() {
        throw new IllegalStateException("Database class");
    }

    public static EntitySpawnIdentity logIdentity(ConsumerWriteBatch batch, String user, EntitySpawnData data, EntitySpawnIdentity existingIdentity) throws Exception {
        if (existingIdentity != null && existingIdentity.hasSpawnLog()) {
            return existingIdentity;
        }
        if (ConfigHandler.isBlacklisted(user)) {
            return null;
        }

        EntityType type = data.getEntityType();
        if (type == null || (ConfigHandler.hasFilters() && ConfigHandler.isBlacklisted(user, type.getKey().toString()))) {
            return null;
        }

        Location initialLocation = data.getLocation();
        String eventUser = user;
        Location eventLocation = initialLocation;
        if (CoreProtectPreLogEvent.isObserved() && !Bukkit.isPrimaryThread()) {
            CoreProtectPreLogEvent event = new CoreProtectPreLogEvent(user, initialLocation.clone(), CoreProtectPreLogEvent.Action.ENTITY_SPAWN, LookupActions.ENTITY_SPAWN, null, type, null);
            CoreProtect.getInstance().getServer().getPluginManager().callEvent(event);
            if (event.isCancelled()) {
                return null;
            }

            eventUser = event.getUser();
            eventLocation = event.getLocation();
        }

        final Location location = eventLocation;
        int time = (int) (System.currentTimeMillis() / 1000L);
        int userId = UserStatement.getId(batch, eventUser, true);
        int worldId = WorldUtils.getWorldId(location.getWorld().getName());
        int entityId = EntityUtils.getEntityId(type);

        EntitySpawnIdentity[] identity = { existingIdentity };
        batch.executeAtomically("entity_spawn_log", () -> {
            if (identity[0] == null) {
                int trackingRowId = EntitySpawnStatement.insert(batch, time, data, location);
                identity[0] = new EntitySpawnIdentity(trackingRowId, data.getUuid(), worldId, location.getX(), location.getY(), location.getZ());
            }
            long blockRowId = BlockStatement.insertImmediate(batch, time, userId, worldId, location.getBlockX(), location.getBlockY(), location.getBlockZ(), entityId, identity[0].getRowId(), null, null, LookupActions.ENTITY_SPAWN, 0);
            EntitySpawnStatement.linkBlock(batch, identity[0].getRowId(), blockRowId);
        });
        identity[0].markSpawnLogged();
        return identity[0];
    }
}
