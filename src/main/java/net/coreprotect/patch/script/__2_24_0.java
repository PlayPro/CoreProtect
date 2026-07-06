package net.coreprotect.patch.script;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Locale;

import net.coreprotect.config.Config;
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.language.Phrase;
import net.coreprotect.language.Selector;
import net.coreprotect.utility.Chat;
import net.coreprotect.utility.ErrorReporter;

public class __2_24_0 {

    protected static boolean patch(Statement statement) {
        try {
            if (Config.getGlobal().MYSQL) {
                if (!convertTableCharacterSet(statement, ConfigHandler.prefix + "sign")) {
                    return false;
                }

                if (!updateItemMetadataColumns(statement)) {
                    return false;
                }

                if (!updateSkullSkinColumn(statement)) {
                    return false;
                }
            }
        }
        catch (Exception e) {
            ErrorReporter.report(e);
            return false;
        }

        return true;
    }

    protected static boolean updateItemMetadataColumns(Statement statement) {
        if (Config.getGlobal().MYSQL) {
            return modifyColumn(statement, ConfigHandler.prefix + "container", "metadata", "mediumblob", "ALTER TABLE " + ConfigHandler.prefix + "container MODIFY metadata MEDIUMBLOB") &&
                    modifyColumn(statement, ConfigHandler.prefix + "item", "data", "mediumblob", "ALTER TABLE " + ConfigHandler.prefix + "item MODIFY data MEDIUMBLOB");
        }

        return true;
    }

    protected static boolean updateSkullSkinColumn(Statement statement) {
        if (Config.getGlobal().MYSQL) {
            return modifyColumn(statement, ConfigHandler.prefix + "skull", "skin", "text", "ALTER TABLE " + ConfigHandler.prefix + "skull MODIFY skin TEXT");
        }

        return true;
    }

    private static boolean convertTableCharacterSet(Statement statement, String table) {
        if (isTableUtf8mb4(statement, table)) {
            return true;
        }

        return updateTable(statement, table, "ALTER TABLE " + table + " CONVERT TO CHARACTER SET utf8mb4");
    }

    private static boolean modifyColumn(Statement statement, String table, String column, String dataType, String query) {
        if (hasColumnType(statement, table, column, dataType)) {
            return true;
        }

        return updateTable(statement, table, query);
    }

    private static boolean updateTable(Statement statement, String table, String query) {
        try {
            Chat.console(Phrase.build(Phrase.PATCH_TABLE_STARTED, table));
            statement.executeUpdate(query);
            Chat.console(Phrase.build(Phrase.PATCH_TABLE_COMPLETED, table));
            return true;
        }
        catch (Exception e) {
            Chat.console(Phrase.build(Phrase.PATCH_SKIP_UPDATE, table, Selector.FIRST, Selector.FIRST));
            ErrorReporter.report(e);
        }

        return false;
    }

    private static boolean hasColumnType(Statement statement, String table, String column, String dataType) {
        try (PreparedStatement preparedStatement = statement.getConnection().prepareStatement("SELECT DATA_TYPE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ? LIMIT 1")) {
            preparedStatement.setString(1, table);
            preparedStatement.setString(2, column);

            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                return resultSet.next() && dataType.equalsIgnoreCase(resultSet.getString("DATA_TYPE"));
            }
        }
        catch (Exception e) {
            return false;
        }
    }

    private static boolean isTableUtf8mb4(Statement statement, String table) {
        try (PreparedStatement preparedStatement = statement.getConnection().prepareStatement("SELECT TABLE_COLLATION FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? LIMIT 1")) {
            preparedStatement.setString(1, table);

            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                if (!resultSet.next()) {
                    return false;
                }

                String tableCollation = resultSet.getString("TABLE_COLLATION");
                if (tableCollation == null || !tableCollation.toLowerCase(Locale.ROOT).startsWith("utf8mb4")) {
                    return false;
                }
            }
        }
        catch (Exception e) {
            return false;
        }

        try (PreparedStatement preparedStatement = statement.getConnection().prepareStatement("SELECT COUNT(*) AS column_count FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND CHARACTER_SET_NAME IS NOT NULL AND CHARACTER_SET_NAME <> 'utf8mb4'")) {
            preparedStatement.setString(1, table);

            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                return resultSet.next() && resultSet.getInt("column_count") == 0;
            }
        }
        catch (Exception e) {
            return false;
        }
    }

}
