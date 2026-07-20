package net.coreprotect.listener.player.inspector;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import net.coreprotect.database.lookup.ChestTransactionLookup;
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.language.Phrase;
import net.coreprotect.language.Selector;
import net.coreprotect.utility.Chat;
import net.coreprotect.utility.Color;
import net.coreprotect.utility.ErrorReporter;

public class ContainerInspector extends BaseInspector {

    public void performContainerLookup(final Player player, final Location finalLocation) {
        ConfigHandler.lookupEntityContainer.remove(player.getName());

        class BasicThread implements Runnable {
            @Override
            public void run() {
                try {
                    checkPreconditions(player);

                    try (Connection connection = getDatabaseConnection(player)) {
                        Statement statement = connection.createStatement();
                        List<String> blockData = ChestTransactionLookup.performLookup(null, statement, finalLocation, player, 1, 7, false);
                        for (String data : blockData) {
                            Chat.sendComponent(player, data);
                        }

                        statement.close();
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
            }
        }

        Runnable runnable = new BasicThread();
        Thread thread = new Thread(runnable);
        thread.start();
    }

    public void performEntityContainerLookup(final Player player, final UUID entityUuid, final Location location) {
        ConfigHandler.lookupEntityContainer.remove(player.getName());
        ConfigHandler.lookupType.remove(player.getName());

        class BasicThread implements Runnable {
            @Override
            public void run() {
                try {
                    checkPreconditions(player);

                    try (Connection connection = getDatabaseConnection(player)) {
                        Integer entitySpawnRowId = null;
                        String query = "SELECT rowid FROM " + ConfigHandler.prefix + "entity_spawn WHERE uuid=? LIMIT 1";
                        try (PreparedStatement statement = connection.prepareStatement(query)) {
                            statement.setString(1, entityUuid.toString());
                            try (ResultSet resultSet = statement.executeQuery()) {
                                if (resultSet.next()) {
                                    entitySpawnRowId = resultSet.getInt("rowid");
                                }
                            }
                        }

                        if (entitySpawnRowId == null) {
                            ConfigHandler.lookupEntityContainer.remove(player.getName());
                            ConfigHandler.lookupType.remove(player.getName());
                            ConfigHandler.lookupCommand.remove(player.getName());
                            Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.NO_DATA_LOCATION, Selector.SECOND));
                            return;
                        }

                        try (Statement statement = connection.createStatement()) {
                            List<String> blockData = ChestTransactionLookup.performLookup(null, statement, location, player, 1, 7, false, entitySpawnRowId);
                            for (String data : blockData) {
                                Chat.sendComponent(player, data);
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
            }
        }

        Thread thread = new Thread(new BasicThread());
        thread.start();
    }
}
