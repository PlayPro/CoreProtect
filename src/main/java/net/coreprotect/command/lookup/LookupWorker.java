package net.coreprotect.command.lookup;

import java.sql.Connection;
import java.sql.Statement;

import org.bukkit.command.CommandSender;

import net.coreprotect.database.Database;
import net.coreprotect.language.Phrase;
import net.coreprotect.utility.Chat;
import net.coreprotect.utility.Color;
import net.coreprotect.utility.ErrorReporter;
import net.coreprotect.utility.LookupThrottle;

final class LookupWorker {

    private LookupWorker() {
    }

    static void run(CommandSender sender, Query query) {
        String name = sender.getName();
        if (!LookupThrottle.tryAcquire(name, 50)) {
            sendBusy(sender);
            return;
        }

        try (Connection connection = Database.getConnection(true)) {
            if (connection == null) {
                sendBusy(sender);
                return;
            }

            try (Statement statement = connection.createStatement()) {
                query.run(statement);
            }
        }
        catch (Exception e) {
            ErrorReporter.report(e);
        }
        finally {
            LookupThrottle.release(name);
        }
    }

    private static void sendBusy(CommandSender sender) {
        Chat.sendMessage(sender, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.DATABASE_BUSY));
    }

    @FunctionalInterface
    interface Query {
        void run(Statement statement);
    }
}
