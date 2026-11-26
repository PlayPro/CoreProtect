package net.coreprotect.database.logger;

import org.bukkit.Location;

import net.coreprotect.config.ConfigHandler;
import net.coreprotect.database.ConsumerWriteBatch;
import net.coreprotect.database.statement.EntityInteractionStatement;
import net.coreprotect.database.statement.UserStatement;
import net.coreprotect.event.CoreProtectPreLogEvent;
import net.coreprotect.model.action.LookupActions;
import net.coreprotect.model.entity.EntityInteraction;
import net.coreprotect.model.entity.EntitySpawnIdentity;
import net.coreprotect.utility.WorldUtils;

public final class EntityInteractionLogger {

    private EntityInteractionLogger() {
        throw new IllegalStateException("Database class");
    }

    public static LogContext prepare(String user, EntityInteraction interaction) {
        if (interaction == null || ConfigHandler.isBlacklisted(user) || ConfigHandler.isBlacklisted(user, interaction.getEntityType().getKey().toString())) {
            return null;
        }

        Location currentLocation = interaction.getCurrentLocation();
        CoreProtectPreLogEvent event = LoggerPreLog.fire(user, currentLocation, CoreProtectPreLogEvent.Action.ENTITY_INTERACTION, LookupActions.INTERACTION, null, interaction.getEntityType(), interaction.getAction().name());
        if (event == null || ConfigHandler.isFilterBlacklisted(event.getUser(), interaction.getEntityType().getKey().toString())) {
            return null;
        }

        Location eventLocation = event.getLocation();
        Location locationOverride = LoggerPreLog.samePosition(currentLocation, eventLocation) ? null : eventLocation.clone();
        return new LogContext(event.getUser(), locationOverride);
    }

    public static boolean log(ConsumerWriteBatch batch, EntitySpawnIdentity identity, EntityInteraction interaction, LogContext context) throws Exception {
        Location currentLocation = interaction.getCurrentLocation();
        boolean identityActive = EntityInteractionStatement.checkpoint(batch, identity, currentLocation);

        Location override = context.locationOverride;
        int worldId = override == null ? identity.getOriginalWorldId() : WorldUtils.getWorldId(override.getWorld().getName());
        int x = override == null ? identity.getOriginalX() : override.getBlockX();
        int y = override == null ? identity.getOriginalY() : override.getBlockY();
        int z = override == null ? identity.getOriginalZ() : override.getBlockZ();

        int userId = UserStatement.getId(batch, context.user, true);
        EntityInteractionStatement.insert(batch, interaction.getTime(), userId, identity, worldId, x, y, z, interaction);
        return identityActive;
    }

    public static final class LogContext {

        private final String user;
        private final Location locationOverride;

        private LogContext(String user, Location locationOverride) {
            this.user = user;
            this.locationOverride = locationOverride;
        }
    }
}
