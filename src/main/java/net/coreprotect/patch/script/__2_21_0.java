package net.coreprotect.patch.script;

import java.sql.Statement;

import net.coreprotect.config.ConfigFile;

public class __2_21_0 {

    protected static boolean patch(Statement statement) {
        try {
            ConfigFile.modifyLine("language.yml", "LOOKUP_VIEW_PAGE: \"To view a page, type \\\"{0}\\\".\"", null);
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        return true;
    }

}
