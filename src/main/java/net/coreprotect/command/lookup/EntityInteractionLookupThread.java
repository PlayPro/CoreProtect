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
import net.coreprotect.utility.LookupThrottle;

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
        if (!LookupThrottle.tryAcquire(player.getName(), 50)) {
            Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.DATABASE_BUSY));
            return;
        }

        try (Connection connection = Database.getConnection(true)) {
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
            LookupThrottle.release(player.getName());
        }
    }
}
