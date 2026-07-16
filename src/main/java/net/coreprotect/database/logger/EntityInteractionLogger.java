package net.coreprotect.database.logger;


import org.bukkit.Bukkit;
import org.bukkit.Location;

import net.coreprotect.CoreProtect;
import net.coreprotect.config.Config;
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.database.ConsumerWriteBatch;
import net.coreprotect.database.statement.EntityInteractionStatement;
import net.coreprotect.database.statement.UserStatement;
import net.coreprotect.event.CoreProtectPreLogEvent;
import net.coreprotect.model.action.LookupActions;
import net.coreprotect.model.entity.EntityInteraction;
import net.coreprotect.model.entity.EntitySpawnIdentity;

public final class EntityInteractionLogger {

    private EntityInteractionLogger() {
        throw new IllegalStateException("Database class");
    }

    public static LogContext prepare(String user, EntityInteraction interaction) {
        if (interaction == null || ConfigHandler.isBlacklisted(user) || ConfigHandler.isBlacklisted(user, interaction.getEntityType().getKey().toString())) {
            return null;
        }

        Location currentLocation = interaction.getCurrentLocation();
        CoreProtectPreLogEvent event = new CoreProtectPreLogEvent(user, currentLocation.clone(), CoreProtectPreLogEvent.Action.ENTITY_INTERACTION, LookupActions.INTERACTION, null, interaction.getEntityType(), interaction.getAction().name());
        if (Config.getGlobal().API_ENABLED && !Bukkit.isPrimaryThread()) {
            CoreProtect.getInstance().getServer().getPluginManager().callEvent(event);
        }
        return event.isCancelled() ? null : new LogContext(event);
    }

    public static void log(ConsumerWriteBatch batch, EntitySpawnIdentity identity, EntityInteraction interaction, LogContext context) throws Exception {
        Location currentLocation = interaction.getCurrentLocation();
        EntityInteractionStatement.checkpoint(batch, identity, currentLocation);

        int worldId = identity.getOriginalWorldId();
        int x = identity.getOriginalX();
        int y = identity.getOriginalY();
        int z = identity.getOriginalZ();

        int userId = UserStatement.getId(batch, context.event.getUser(), true);
        int time = (int) (System.currentTimeMillis() / 1000L);
        EntityInteractionStatement.insert(batch, time, userId, identity, worldId, x, y, z, interaction);
    }

    public static final class LogContext {

        private final CoreProtectPreLogEvent event;

        private LogContext(CoreProtectPreLogEvent event) {
            this.event = event;
        }
    }
}
