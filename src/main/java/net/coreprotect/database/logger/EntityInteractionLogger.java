package net.coreprotect.database.logger;


import net.coreprotect.CoreProtect;
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.database.ConsumerWriteBatch;
import net.coreprotect.database.statement.EntityInteractionStatement;
import net.coreprotect.database.statement.UserStatement;
import net.coreprotect.event.CoreProtectPreLogEvent;
import net.coreprotect.model.action.LookupActions;
import net.coreprotect.model.entity.EntityInteraction;
import net.coreprotect.model.entity.EntitySpawnIdentity;
import org.bukkit.Bukkit;
import org.bukkit.Location;

public final class EntityInteractionLogger {

    private EntityInteractionLogger() {
        throw new IllegalStateException("Database class");
    }

    public static LogContext prepare(String user, EntityInteraction interaction) {
        if (interaction == null || ConfigHandler.isBlacklisted(user)) {
            return null;
        }

        if (ConfigHandler.hasFilters() && ConfigHandler.isBlacklisted(user, interaction.getEntityType().getKey().toString())) {
            return null;
        }

        if (!CoreProtectPreLogEvent.isObserved() || Bukkit.isPrimaryThread()) {
            return new LogContext(user);
        }

        Location currentLocation = interaction.getCurrentLocation();
        CoreProtectPreLogEvent event = new CoreProtectPreLogEvent(user, currentLocation.clone(), CoreProtectPreLogEvent.Action.ENTITY_INTERACTION, LookupActions.INTERACTION, null, interaction.getEntityType(), interaction.getAction().name());
        CoreProtect.getInstance().getServer().getPluginManager().callEvent(event);
        return event.isCancelled() ? null : new LogContext(event.getUser());
    }

    public static boolean log(ConsumerWriteBatch batch, EntitySpawnIdentity identity, EntityInteraction interaction, LogContext context) throws Exception {
        Location currentLocation = interaction.getCurrentLocation();
        boolean identityActive = EntityInteractionStatement.checkpoint(batch, identity, currentLocation);

        int worldId = identity.getOriginalWorldId();
        int x = identity.getOriginalX();
        int y = identity.getOriginalY();
        int z = identity.getOriginalZ();

        int userId = UserStatement.getId(batch, context.user, true);
        EntityInteractionStatement.insert(batch, interaction.getTime(), userId, identity, worldId, x, y, z, interaction);
        return identityActive;
    }

    public static final class LogContext {

        private final String user;

        private LogContext(String user) {
            this.user = user;
        }
    }
}
