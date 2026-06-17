package net.coreprotect.database.logger;

import java.sql.PreparedStatement;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;

import net.coreprotect.CoreProtect;
import net.coreprotect.config.Config;
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.database.statement.BlockStatement;
import net.coreprotect.database.statement.EntityStatement;
import net.coreprotect.database.statement.UserStatement;
import net.coreprotect.event.CoreProtectPreLogEvent;
import net.coreprotect.model.action.LookupActions;
import net.coreprotect.utility.WorldUtils;
import net.coreprotect.utility.ErrorReporter;

public class EntityKillLogger {

    private EntityKillLogger() {
        throw new IllegalStateException("Database class");
    }

    public static void log(PreparedStatement preparedStmt, PreparedStatement preparedStmt2, int batchCount, String user, Location location, String entityData, int type) {
        try {
            if (ConfigHandler.isBlacklisted(user)) {
                return;
            }

            EntityType checkType = net.coreprotect.utility.EntityUtils.getEntityType(type);
            if (checkType == null) {
                return;
            }
            // Ignore blacklist if the entity has a custom name
            // data[4] contains custom name data
            // CH: we don't have a data array though, so ignore ignoring that for now
            if (ConfigHandler.isBlacklisted(user, checkType.getKey().toString())) {
                return;
            }

            Location initialLocation = location.clone();
            CoreProtectPreLogEvent event = new CoreProtectPreLogEvent(user, initialLocation, CoreProtectPreLogEvent.Action.ENTITY_KILL, LookupActions.ENTITY_KILL, null, checkType, null);
            if (Config.getGlobal().API_ENABLED && !Bukkit.isPrimaryThread()) {
                CoreProtect.getInstance().getServer().getPluginManager().callEvent(event);
            }

            if (event.isCancelled()) {
                return;
            }

            int userId = UserStatement.getId(preparedStmt, event.getUser(), true);
            Location eventLocation = event.getLocation();
            int wid = WorldUtils.getWorldId(eventLocation.getWorld().getName());
            int time = (int) (System.currentTimeMillis() / 1000L);
            int x = eventLocation.getBlockX();
            int y = eventLocation.getBlockY();
            int z = eventLocation.getBlockZ();
            long entity_key = EntityStatement.insert(preparedStmt2, time, entityData);

            BlockStatement.insert(preparedStmt, batchCount, time, userId, wid, x, y, z, type, entity_key, null, null, LookupActions.ENTITY_KILL, 0);
        }
        catch (Exception e) {
            ErrorReporter.report(e);
        }
    }

}
