package net.coreprotect.patch.script;

import java.sql.Statement;

import net.coreprotect.config.ConfigHandler;
import net.coreprotect.patch.Patch;
import net.coreprotect.utility.ErrorReporter;

public class __2_23_1 {

    protected static boolean patch(Statement statement) {
        try {
            LegacyPatchSchema.ensureColumn(statement, ConfigHandler.prefix + "skull", "skin", "TEXT");
            return ConfigHandler.databaseType.isMySQL() || Patch.continuePatch();
        }
        catch (Exception e) {
            ErrorReporter.report(e);
            return false;
        }
    }

}
