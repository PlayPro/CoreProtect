package net.coreprotect.command.lookup;

import java.sql.Connection;
import java.sql.Statement;
import java.util.List;

import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

import net.coreprotect.config.ConfigHandler;
import net.coreprotect.database.Database;
import net.coreprotect.database.lookup.ChestTransactionLookup;
import net.coreprotect.language.Phrase;
import net.coreprotect.utility.Chat;
import net.coreprotect.utility.Color;
import net.coreprotect.utility.ErrorReporter;
import net.coreprotect.utility.LookupThrottle;

public class ChestTransactionLookupThread implements Runnable {
    private final CommandSender player;
    private final Command command;
    private final Location location;
    private final int page;
    private final int limit;

    public ChestTransactionLookupThread(CommandSender player, Command command, Location location, int page, int limit) {
        this.player = player;
        this.command = command;
        this.location = location;
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
            if (connection != null) {
                Statement statement = connection.createStatement();
                Integer entitySpawnRowId = ConfigHandler.lookupEntityContainer.get(player.getName());
                List<String> blockData = ChestTransactionLookup.performLookup(command.getName(), statement, location, player, page, limit, false, entitySpawnRowId);
                for (String data : blockData) {
                    Chat.sendComponent(player, data);
                }
                statement.close();
            }
            else {
                Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.DATABASE_BUSY));
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
