package net.coreprotect.listener.player.inspector;

import java.sql.Connection;

import org.bukkit.entity.Player;

import net.coreprotect.config.ConfigHandler;
import net.coreprotect.database.Database;
import net.coreprotect.language.Phrase;
import net.coreprotect.utility.Chat;
import net.coreprotect.utility.Color;
import net.coreprotect.utility.LookupThrottle;

public abstract class BaseInspector {

    protected void startInspection(Player player, Runnable inspection) {
        try {
            acquireInspection(player);
        }
        catch (InspectionException e) {
            Chat.sendMessage(player, e.getMessage());
            return;
        }

        try {
            Thread thread = new Thread(() -> {
                try {
                    inspection.run();
                }
                finally {
                    LookupThrottle.release(player.getName());
                }
            });
            thread.start();
        }
        catch (RuntimeException | Error e) {
            LookupThrottle.release(player.getName());
            throw e;
        }
    }

    private void acquireInspection(Player player) throws InspectionException {
        if (ConfigHandler.converterRunning) {
            throw new InspectionException(Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.UPGRADE_IN_PROGRESS));
        }

        if (ConfigHandler.purgeRunning) {
            throw new InspectionException(Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.PURGE_IN_PROGRESS));
        }

        if (!LookupThrottle.tryAcquire(player.getName(), 100)) {
            throw new InspectionException(Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.DATABASE_BUSY));
        }
    }

    protected Connection getDatabaseConnection(Player player) throws Exception {
        Connection connection = Database.getConnection(true);
        if (connection == null) {
            throw new InspectionException(Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.DATABASE_BUSY));
        }

        return connection;
    }

    public static class InspectionException extends Exception {
        private static final long serialVersionUID = 1L;

        public InspectionException(String message) {
            super(message);
        }
    }
}
