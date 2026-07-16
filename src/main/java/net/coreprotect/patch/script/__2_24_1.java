package net.coreprotect.patch.script;

import java.sql.Statement;

import net.coreprotect.patch.Patch;
import net.coreprotect.utility.ErrorReporter;

public class __2_24_1 {

    protected static boolean patchClickHouse(Statement statement) {
        return true;
    }

    protected static boolean patchDuckDB(Statement statement) {
        return true;
    }

    protected static boolean patch(Statement statement) {
        try {
            Integer[] last_version = Patch.getDatabaseVersion(statement.getConnection(), true);
            if (last_version[0] == 2 && last_version[1] == 24 && last_version[2] == 0) {
                if (!__2_24_0.updateItemMetadataColumns(statement) || !__2_24_0.updateSkullSkinColumn(statement)) {
                    return false;
                }
            }
            return true;
        }
        catch (Exception e) {
            ErrorReporter.report(e);
            return false;
        }
    }

}
