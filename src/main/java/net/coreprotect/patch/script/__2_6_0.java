package net.coreprotect.patch.script;

import java.sql.Statement;

import net.coreprotect.config.Config;
import net.coreprotect.config.ConfigHandler;

public class __2_6_0 {

    protected static boolean patch(Statement statement) {
        try {
            if (Config.getGlobal().MYSQL) {
                statement.executeUpdate("START TRANSACTION");
                statement.executeUpdate("CREATE TEMPORARY TABLE " + ConfigHandler.prefix + "version_tmp(rowid int, time int, version varchar(16)) ENGINE=InnoDB");
                statement.executeUpdate("INSERT INTO " + ConfigHandler.prefix + "version_tmp SELECT rowid,time,version FROM " + ConfigHandler.prefix + "version;");
                statement.executeUpdate("DROP TABLE " + ConfigHandler.prefix + "version;");
                statement.executeUpdate("CREATE TABLE " + ConfigHandler.prefix + "version(rowid int NOT NULL AUTO_INCREMENT,PRIMARY KEY(rowid),time int,version varchar(16)) ENGINE=InnoDB");
                statement.executeUpdate("INSERT INTO " + ConfigHandler.prefix + "version SELECT rowid,time,version FROM " + ConfigHandler.prefix + "version_tmp;");
                statement.executeUpdate("DROP TEMPORARY TABLE " + ConfigHandler.prefix + "version_tmp;");
                statement.executeUpdate("COMMIT");
            }
            else {
                statement.executeUpdate("BEGIN TRANSACTION");
                statement.executeUpdate("CREATE TEMPORARY TABLE " + ConfigHandler.prefix + "version_tmp (time INTEGER, version TEXT);");
                statement.executeUpdate("INSERT INTO " + ConfigHandler.prefix + "version_tmp SELECT time,version FROM " + ConfigHandler.prefix + "version;");
                statement.executeUpdate("DROP TABLE " + ConfigHandler.prefix + "version;");
                statement.executeUpdate("CREATE TABLE " + ConfigHandler.prefix + "version (time INTEGER, version TEXT);");
                statement.executeUpdate("INSERT INTO " + ConfigHandler.prefix + "version SELECT time,version FROM " + ConfigHandler.prefix + "version_tmp;");
                statement.executeUpdate("DROP TABLE " + ConfigHandler.prefix + "version_tmp;");
                statement.executeUpdate("COMMIT TRANSACTION");
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        return true;
    }

}
