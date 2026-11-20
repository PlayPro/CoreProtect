package net.coreprotect.patch.script;

import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import net.coreprotect.config.ConfigHandler;
import net.coreprotect.patch.Patch;
import net.coreprotect.utility.ErrorReporter;

public class __2_22_0 {

    protected static boolean patch(Statement statement) {
        try {
            String table = ConfigHandler.prefix + "sign";
            boolean mysql = ConfigHandler.databaseType.isMySQL();
            String textType = mysql ? "VARCHAR(100)" : "TEXT";
            Map<String, String> requiredColumns = new LinkedHashMap<>();
            for (int line = 5; line <= 8; line++) {
                requiredColumns.put("line_" + line, textType);
            }
            requiredColumns.put("color_secondary", mysql ? "INT" : "INTEGER");
            requiredColumns.put("waxed", "TINYINT DEFAULT 0");
            requiredColumns.put("face", "TINYINT DEFAULT 0");

            Set<String> existingColumns = LegacyPatchSchema.columns(statement, table).keySet();
            for (Map.Entry<String, String> column : requiredColumns.entrySet()) {
                if (!Patch.continuePatch()) {
                    return false;
                }
                if (!existingColumns.contains(column.getKey())) {
                    statement.executeUpdate("ALTER TABLE " + table + " ADD COLUMN " + column.getKey() + " " + column.getValue());
                }
            }
            return Patch.continuePatch();
        }
        catch (Exception e) {
            ErrorReporter.report(e);
            return false;
        }
    }

}
