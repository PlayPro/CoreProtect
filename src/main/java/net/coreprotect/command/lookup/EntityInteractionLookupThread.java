package net.coreprotect.command.lookup;

import java.sql.Connection;
import java.sql.Statement;
import java.util.List;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

import net.coreprotect.config.ConfigHandler;
import net.coreprotect.database.Database;
import net.coreprotect.database.lookup.EntityInteractionLookup;
import net.coreprotect.language.Phrase;
import net.coreprotect.utility.Chat;
import net.coreprotect.utility.Color;
import net.coreprotect.utility.ErrorReporter;

public final class EntityInteractionLookupThread implements Runnable {
    private final CommandSender player;
    private final Command command;
    private final int page;
    private final int limit;

    public EntityInteractionLookupThread(CommandSender player, Command command, int page, int limit) {
        this.player = player;
        this.command = command;
        this.page = page;
        this.limit = limit;
    }

    @Override
    public void run() {
        try (Connection connection = Database.getConnection(true)) {
            ConfigHandler.lookupThrottle.put(player.getName(), new Object[] { true, System.currentTimeMillis() });
            if (connection == null) {
                Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.DATABASE_BUSY));
                return;
            }

            Integer entitySpawnRowId = ConfigHandler.lookupEntityInteraction.get(player.getName());
            try (Statement statement = connection.createStatement()) {
                List<String> results = EntityInteractionLookup.performLookup(command.getName(), statement, player, page, limit, entitySpawnRowId);
                for (String result : results) {
                    Chat.sendComponent(player, result);
                }
            }
        }
        catch (Exception e) {
            ErrorReporter.report(e);
        }
        finally {
            ConfigHandler.lookupThrottle.put(player.getName(), new Object[] { false, System.currentTimeMillis() });
        }
    }
}
