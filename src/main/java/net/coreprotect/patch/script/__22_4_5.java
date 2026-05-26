package net.coreprotect.patch.script;

import net.coreprotect.CoreProtect;
import net.coreprotect.config.ConfigHandler;
import org.slf4j.Logger;

import java.sql.SQLException;
import java.sql.Statement;

public class __22_4_5 {
    protected static boolean patch(Statement statement) {
        final Logger logger = CoreProtect.getInstance().getSLF4JLogger();

        // Bump from 32 bit to being a 64 bit int
        try {
            statement.executeUpdate("ALTER TABLE " + ConfigHandler.prefix + "block ALTER COLUMN data TYPE Int64");
            return true;
        } catch (SQLException e) {
            logger.error("An unexpected exception happened while altering data type of the data column in the {}block table to Int64", ConfigHandler.prefix, e);
            return false;
        }
    }
}
