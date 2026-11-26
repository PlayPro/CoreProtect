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

public abstract class LookupWorker implements Runnable {
    protected final CommandSender player;
    private final LookupThrottle.Claim claim;

    protected LookupWorker(CommandSender player) {
        this.player = player;
        this.claim = LookupThrottle.acquire(player.getName(), 50);
    }

    public final void start() {
        try {
            new Thread(this).start();
        }
        catch (RuntimeException | Error e) {
            if (claim != null) {
                claim.close();
            }
            throw e;
        }
    }

    @Override
    public final void run() {
        if (claim == null) {
            sendBusy();
            return;
        }

        claim.runLookup(this::execute);
    }

    private void execute() {
        try (Connection connection = Database.getConnection(true)) {
            if (connection == null) {
                sendBusy();
                return;
            }

            try (Statement statement = connection.createStatement()) {
                query(statement);
            }
        }
        catch (Exception e) {
            ErrorReporter.report(e);
        }
    }

    private void sendBusy() {
        Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.DATABASE_BUSY));
    }

    protected abstract void query(Statement statement);
}
