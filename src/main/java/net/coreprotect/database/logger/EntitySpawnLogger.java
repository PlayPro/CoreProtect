package net.coreprotect.database.logger;


import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;

import net.coreprotect.CoreProtect;
import net.coreprotect.config.Config;
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.database.Database;
import net.coreprotect.database.ConsumerWriteBatch;
import net.coreprotect.database.statement.BlockStatement;
import net.coreprotect.database.statement.EntitySpawnStatement;
import net.coreprotect.database.statement.UserStatement;
import net.coreprotect.event.CoreProtectPreLogEvent;
import net.coreprotect.model.action.LookupActions;
import net.coreprotect.model.entity.EntitySpawnData;
import net.coreprotect.model.entity.EntitySpawnIdentity;
import net.coreprotect.utility.EntitySpawnTracking;
import net.coreprotect.utility.EntityUtils;
import net.coreprotect.utility.WorldUtils;

public final class EntitySpawnLogger {

    private EntitySpawnLogger() {
        throw new IllegalStateException("Database class");
    }

    public static EntitySpawnIdentity logIdentity(ConsumerWriteBatch batch, String user, EntitySpawnData data) throws Exception {
        if (ConfigHandler.isBlacklisted(user)) {
            EntitySpawnTracking.clearTracking(data.getUuid());
            return null;
        }

        EntityType type = data.getEntityType();
        if (type == null || ConfigHandler.isBlacklisted(user, type.getKey().toString())) {
            EntitySpawnTracking.clearTracking(data.getUuid());
            return null;
        }

        Location initialLocation = data.getLocation();
        CoreProtectPreLogEvent event = new CoreProtectPreLogEvent(user, initialLocation.clone(), CoreProtectPreLogEvent.Action.ENTITY_SPAWN, LookupActions.ENTITY_SPAWN, null, type, null);
        if (Config.getGlobal().API_ENABLED && !Bukkit.isPrimaryThread()) {
            CoreProtect.getInstance().getServer().getPluginManager().callEvent(event);
        }
        if (event.isCancelled()) {
            EntitySpawnTracking.clearTracking(data.getUuid());
            return null;
        }

        int time = (int) (System.currentTimeMillis() / 1000L);
        int userId = UserStatement.getId(batch, event.getUser(), true);
        Location location = event.getLocation();
        int worldId = WorldUtils.getWorldId(location.getWorld().getName());
        int entityId = EntityUtils.getEntityId(type);

        int[] trackingRowId = new int[1];
        batch.executeAtomically("entity_spawn_log", () -> {
            trackingRowId[0] = EntitySpawnStatement.insert(batch, time, data, location);
            long blockRowId = BlockStatement.insertImmediate(batch, time, userId, worldId, location.getBlockX(), location.getBlockY(), location.getBlockZ(), entityId, trackingRowId[0], null, null, LookupActions.ENTITY_SPAWN, 0);
            EntitySpawnStatement.linkBlock(batch, trackingRowId[0], blockRowId);
        });
        return new EntitySpawnIdentity(trackingRowId[0], data.getUuid(), worldId, location.getX(), location.getY(), location.getZ());
    }
}
