package net.coreprotect.listener.player.inspector;

import java.util.List;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import net.coreprotect.config.ConfigHandler;
import net.coreprotect.database.lookup.EntityInteractionLookup;
import net.coreprotect.database.statement.EntitySpawnStatement;
import net.coreprotect.utility.Chat;
import net.coreprotect.utility.LookupThrottle;

public final class EntityInteractionInspector extends BaseInspector {

    public void performLookup(Player player, UUID entityUuid, Location location) {
        startInspection(player, () -> {
            String name = player.getName();
            LookupThrottle.publish(name, () -> {
                ConfigHandler.lookupEntityInteraction.remove(name);
                ConfigHandler.lookupEntityContainer.remove(name);
                ConfigHandler.lookupType.remove(name);
            });
            withStatement(player, (connection, statement) -> {
                Integer entitySpawnRowId = EntitySpawnStatement.findRowIdByUuid(connection, entityUuid);
                List<String> results = EntityInteractionLookup.performLookup(null, statement, player, 1, 7, entitySpawnRowId, location);
                Chat.sendLookupComponents(player, results);
            });
        });
    }
}
