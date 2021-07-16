package net.coreprotect.patch.script;

import java.sql.Statement;

import net.coreprotect.patch.Patch;

public class __2_18_1 {

    protected static boolean patch(Statement statement) {
        try {
            __2_18_0.createIndexes = false;
            Integer[] last_version = Patch.getDatabaseVersion(statement.getConnection(), true);
            if (last_version[0] == 2 && last_version[1] == 18 && last_version[2] == 0) {
                return __2_18_0.patch(statement);
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        return true;
    }

}
