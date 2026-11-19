package net.coreprotect.listener.player.inspector;

import java.util.List;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import net.coreprotect.config.ConfigHandler;
import net.coreprotect.database.lookup.ChestTransactionLookup;
import net.coreprotect.database.statement.EntitySpawnStatement;
import net.coreprotect.language.Phrase;
import net.coreprotect.language.Selector;
import net.coreprotect.utility.Chat;
import net.coreprotect.utility.Color;
import net.coreprotect.utility.LookupThrottle;

public class ContainerInspector extends BaseInspector {

    public void performContainerLookup(Player player, Location location) {
        startInspection(player, () -> {
            String name = player.getName();
            LookupThrottle.publish(name, () -> ConfigHandler.lookupEntityContainer.remove(name));
            withStatement(player, (connection, statement) -> {
                List<String> results = ChestTransactionLookup.performLookup(null, statement, location, player, 1, 7, false);
                Chat.sendLookupComponents(player, results);
            });
        });
    }

    public void performEntityContainerLookup(Player player, UUID entityUuid, Location location) {
        startInspection(player, () -> {
            String name = player.getName();
            LookupThrottle.publish(name, () -> {
                ConfigHandler.lookupEntityContainer.remove(name);
                ConfigHandler.lookupType.remove(name);
            });
            withStatement(player, (connection, statement) -> {
                Integer entitySpawnRowId = EntitySpawnStatement.findRowIdByUuid(connection, entityUuid);
                if (entitySpawnRowId == null) {
                    LookupThrottle.publish(name, () -> {
                        ConfigHandler.lookupEntityContainer.remove(name);
                        ConfigHandler.lookupType.remove(name);
                        ConfigHandler.lookupCommand.remove(name);
                    });
                    Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.NO_DATA_LOCATION, Selector.SECOND));
                    return;
                }

                List<String> results = ChestTransactionLookup.performLookup(null, statement, location, player, 1, 7, false, entitySpawnRowId);
                Chat.sendLookupComponents(player, results);
            });
        });
    }
}
