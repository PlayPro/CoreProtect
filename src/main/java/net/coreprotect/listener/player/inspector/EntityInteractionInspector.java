package net.coreprotect.listener.player.inspector;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import net.coreprotect.config.ConfigHandler;
import net.coreprotect.database.lookup.EntityInteractionLookup;
import net.coreprotect.utility.Chat;
import net.coreprotect.utility.ErrorReporter;

public final class EntityInteractionInspector extends BaseInspector {

    public void performLookup(Player player, UUID entityUuid, Location location) {
        ConfigHandler.lookupEntityInteraction.remove(player.getName());
        ConfigHandler.lookupEntityContainer.remove(player.getName());
        ConfigHandler.lookupType.remove(player.getName());

        Thread thread = new Thread(() -> {
            try {
                checkPreconditions(player);
                try (Connection connection = getDatabaseConnection(player)) {
                    Integer entitySpawnRowId = findEntitySpawnRowId(connection, entityUuid);
                    try (Statement statement = connection.createStatement()) {
                        List<String> results = EntityInteractionLookup.performLookup(null, statement, player, 1, 7, entitySpawnRowId, location);
                        for (String result : results) {
                            Chat.sendComponent(player, result);
                        }
                    }
                }
            }
            catch (InspectionException e) {
                Chat.sendMessage(player, e.getMessage());
            }
            catch (Exception e) {
                ErrorReporter.report(e);
            }
            finally {
                finishInspection(player);
            }
        });
        thread.start();
    }

    private Integer findEntitySpawnRowId(Connection connection, UUID entityUuid) throws Exception {
        String query = "SELECT rowid AS id FROM " + ConfigHandler.prefix + "entity_spawn FINAL WHERE uuid=? LIMIT 1";
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, entityUuid.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getInt("id") : null;
            }
        }
    }
}
