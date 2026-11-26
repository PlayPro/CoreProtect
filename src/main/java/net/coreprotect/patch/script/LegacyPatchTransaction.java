package net.coreprotect.patch.script;

import java.sql.SQLException;
import java.sql.Statement;

import net.coreprotect.config.ConfigHandler;
import net.coreprotect.database.Database;
import net.coreprotect.database.DatabaseType;

final class LegacyPatchTransaction {

    private LegacyPatchTransaction() {
    }

    @FunctionalInterface
    interface Work {
        void run() throws SQLException;
    }

    static void run(Statement statement, Work work) throws SQLException {
        DatabaseType databaseType = ConfigHandler.databaseType;
        Database.beginTransaction(statement, databaseType);
        boolean readyToCommit = false;
        try {
            work.run();
            readyToCommit = true;
        }
        finally {
            if (!readyToCommit) {
                Database.rollbackTransaction(statement, databaseType);
            }
        }
        Database.commitTransaction(statement, databaseType);
    }
}
