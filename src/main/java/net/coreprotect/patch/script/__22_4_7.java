package net.coreprotect.patch.script;

import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Locale;

import net.coreprotect.CoreProtect;
import net.coreprotect.config.Config;
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.utility.ErrorReporter;

public class __22_4_7 {

    protected static boolean patch(Statement statement) {
        try {
            updateRollbackOrderBy(statement, "block");
            updateRollbackOrderBy(statement, "container");
            updateRollbackOrderBy(statement, "item");
            return true;
        }
        catch (Exception e) {
            ErrorReporter.report(e);
            return false;
        }
    }

    private static void updateRollbackOrderBy(Statement statement, String table) throws Exception {
        if (sortingKeyIncludesRowId(statement, table)) {
            return;
        }

        try {
            statement.executeUpdate("ALTER TABLE " + ConfigHandler.prefix + table + " MODIFY ORDER BY (wid, y, x, z, time, user, type, rowid)");
        }
        catch (Exception e) {
            CoreProtect.getInstance().getSLF4JLogger().warn("Could not modify ORDER BY for {}{} in-place, rebuilding table instead.", ConfigHandler.prefix, table, e);
            rebuildRollbackTable(statement, table);
        }
    }

    private static boolean sortingKeyIncludesRowId(Statement statement, String table) throws Exception {
        String tableName = ConfigHandler.prefix + table;
        try (ResultSet resultSet = statement.executeQuery("SELECT sorting_key FROM system.tables WHERE database = currentDatabase() AND name = '" + tableName + "' LIMIT 1")) {
            if (resultSet.next()) {
                String sortingKey = resultSet.getString("sorting_key");
                return sortingKey != null && sortingKey.toLowerCase(Locale.ROOT).contains("rowid");
            }
        }

        return false;
    }

    private static void rebuildRollbackTable(Statement statement, String table) throws Exception {
        String tableName = ConfigHandler.prefix + table;
        String suffix = "_backup_22_4_7_" + System.currentTimeMillis();
        String backupName = tableName + suffix;
        String rebuiltName = tableName + "_rebuild_22_4_7_" + System.currentTimeMillis();
        String columns = columns(table);

        CoreProtect.getInstance().getSLF4JLogger().info("Creating rebuilt table {} for {}.", rebuiltName, tableName);
        statement.executeUpdate("CREATE TABLE " + rebuiltName + tableDefinition(table));

        CoreProtect.getInstance().getSLF4JLogger().info("Copying data from {} to {}.", tableName, rebuiltName);
        statement.executeUpdate("INSERT INTO " + rebuiltName + " (" + columns + ") SELECT " + columns + " FROM " + tableName);

        CoreProtect.getInstance().getSLF4JLogger().info("Swapping {} with rebuilt table. Backup table will be {}.", tableName, backupName);
        statement.executeUpdate("RENAME TABLE " + tableName + " TO " + backupName + ", " + rebuiltName + " TO " + tableName);
    }

    private static String tableDefinition(String table) {
        String orderBy = " ORDER BY (wid, y, x, z, time, user, type, rowid)";
        String partitionBy = " PARTITION BY " + Config.getGlobal().PARTITIONING;

        return switch (table) {
            case "block" -> "(rowid UInt64, time UInt32, user UInt32, wid UInt32, x Int32, y Int16, z Int32, type UInt32, data Int64, meta String, blockdata LowCardinality(String), action UInt8, rolled_back UInt8, version UInt8) ENGINE = ReplacingMergeTree(version)" + orderBy + partitionBy;
            case "container" -> "(rowid UInt64, time UInt32, user UInt32, wid UInt32, x Int32, y Int16, z Int32, type UInt32, data UInt32, amount UInt32, metadata String, action UInt8, rolled_back UInt8, version UInt8) ENGINE = ReplacingMergeTree(version)" + orderBy + partitionBy;
            case "item" -> "(rowid UInt64, time UInt32, user UInt32, wid UInt32, x Int32, y Int16, z Int32, type UInt32, data String, amount UInt32, action UInt8, rolled_back UInt8, version UInt8) ENGINE = ReplacingMergeTree(version)" + orderBy + partitionBy;
            default -> throw new IllegalArgumentException("Unsupported rollback table: " + table);
        };
    }

    private static String columns(String table) {
        return switch (table) {
            case "block" -> "rowid,time,user,wid,x,y,z,type,data,meta,blockdata,action,rolled_back,version";
            case "container" -> "rowid,time,user,wid,x,y,z,type,data,amount,metadata,action,rolled_back,version";
            case "item" -> "rowid,time,user,wid,x,y,z,type,data,amount,action,rolled_back,version";
            default -> throw new IllegalArgumentException("Unsupported rollback table: " + table);
        };
    }
}
