package net.coreprotect.patch.script;

import java.sql.Statement;

import net.coreprotect.patch.Patch;

public class __2_24_1 {

    protected static boolean patch(Statement statement) {
        try {
            Integer[] last_version = Patch.getDatabaseVersion(statement.getConnection(), true);
            if (last_version[0] == 2 && last_version[1] == 24 && last_version[2] == 0) {
                return __2_24_0.updateItemMetadataColumns(statement);
            }
        }
        catch (Exception e) {
            e.printStackTrace();
            return false;
        }

        return true;
    }

}
