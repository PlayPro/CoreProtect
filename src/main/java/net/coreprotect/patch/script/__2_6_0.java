package net.coreprotect.patch.script;

import java.sql.Statement;
import java.util.List;

import net.coreprotect.config.ConfigHandler;
import net.coreprotect.utility.ErrorReporter;

public class __2_6_0 {

    protected static boolean patch(Statement statement) {
        try {
            String table = ConfigHandler.prefix + "version";
            if (ConfigHandler.databaseType.isMySQL()) {
                List<String> primaryKey = LegacyPatchSchema.mysqlIndexColumns(statement, table, "PRIMARY");
                String changeKey = "";
                if (!primaryKey.equals(List.of("rowid"))) {
                    changeKey = primaryKey.isEmpty() ? ", ADD PRIMARY KEY(rowid)" : ", DROP PRIMARY KEY, ADD PRIMARY KEY(rowid)";
                }
                statement.executeUpdate("ALTER TABLE " + table + " MODIFY rowid INT NOT NULL AUTO_INCREMENT, MODIFY time INT, MODIFY version VARCHAR(16), ENGINE=InnoDB" + changeKey);
            }
            else {
                String temporary = table + "_tmp";
                LegacyPatchTransaction.run(statement, () -> {
                    statement.executeUpdate("CREATE TEMPORARY TABLE " + temporary + " (time INTEGER, version TEXT)");
                    statement.executeUpdate("INSERT INTO " + temporary + " SELECT time,version FROM " + table);
                    statement.executeUpdate("DROP TABLE " + table);
                    statement.executeUpdate("CREATE TABLE " + table + " (time INTEGER, version TEXT)");
                    statement.executeUpdate("INSERT INTO " + table + " SELECT time,version FROM " + temporary);
                    statement.executeUpdate("DROP TABLE " + temporary);
                });
            }
            return true;
        }
        catch (Exception e) {
            ErrorReporter.report(e);
            return false;
        }
    }

}
