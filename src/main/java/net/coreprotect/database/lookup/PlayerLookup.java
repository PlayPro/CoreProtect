package net.coreprotect.database.lookup;

import java.sql.Connection;
import net.coreprotect.database.statement.UserStatement;
import net.coreprotect.utility.ErrorReporter;

public class PlayerLookup {

    public static boolean playerExists(Connection connection, String user) {
        try {
            return UserStatement.findId(connection, user) > -1;
        }
        catch (Exception e) {
            ErrorReporter.report(e);
        }

        return false;
    }

}
