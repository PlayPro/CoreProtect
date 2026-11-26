package net.coreprotect.database.logger;

import net.coreprotect.config.ConfigHandler;
import net.coreprotect.database.ConsumerWriteBatch;
import net.coreprotect.database.Database;

public class UsernameLogger {

    private UsernameLogger() {
        throw new IllegalStateException("Database class");
    }

    public static void log(ConsumerWriteBatch batch, String user, String uuid, int configUsernames, int time) {
        try {
            if (ConfigHandler.isBlacklisted(user)) {
                return;
            }
            batch.recordUsername(user, uuid, configUsernames, time);
        }
        catch (Exception e) {
            Database.handleWriteFailure(e);
        }
    }

}
