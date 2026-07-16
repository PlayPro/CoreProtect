package net.coreprotect.api;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

import net.coreprotect.api.result.ItemResult;
import net.coreprotect.config.Config;
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.database.Database;
import net.coreprotect.database.logger.ItemLogger;
import net.coreprotect.database.statement.UserStatement;
import net.coreprotect.utility.WorldUtils;
import net.coreprotect.utility.DatabaseUtils;
import net.coreprotect.utility.ErrorReporter;

/**
 * Provides API methods for world item transaction lookups.
 */
public class ItemAPI {

    private ItemAPI() {
        throw new IllegalStateException("API class");
    }

    public static List<ItemResult> performLookup(LookupOptions options) {
        List<ItemResult> result = new ArrayList<>();

        if (!Config.getGlobal().API_ENABLED) {
            return result;
        }

        try (Connection connection = Database.getConnection(false, 1000)) {
            if (connection == null) {
                return result;
            }

            LookupFilter filter = LookupFilter.fromOptions(connection, options);
            if (filter.hasInvalidUser() || filter.hasInvalidLocation()) {
                return result;
            }

            StringBuilder query = new StringBuilder("SELECT time," + ConfigHandler.databaseType.getUserColumn() + ",wid,x,y,z,type,data,amount,action,rolled_back FROM ");
            query.append(ConfigHandler.prefix).append("item ");
            if (filter.hasLocation()) {
                query.append(WorldUtils.getWidIndex("item"));
            }
            filter.appendWhere(query);
            query.append(" AND action NOT IN (")
                    .append(ItemLogger.ITEM_BREAK).append(",")
                    .append(ItemLogger.ITEM_DESTROY).append(",")
                    .append(ItemLogger.ITEM_CREATE).append(",")
                    .append(ItemLogger.ITEM_SELL).append(",")
                    .append(ItemLogger.ITEM_BUY).append(")");
            query.append(" ORDER BY rowid DESC");
            filter.appendLimit(query);

            try (PreparedStatement statement = connection.prepareStatement(query.toString())) {
                filter.bind(statement);

                try (ResultSet results = statement.executeQuery()) {
                    while (results.next()) {
                        result.add(parseItemResult(connection, results));
                    }
                }
            }
        }
        catch (Exception e) {
            ErrorReporter.report(e);
        }

        return result;
    }

    private static ItemResult parseItemResult(Connection connection, ResultSet results) throws Exception {
        int userId = results.getInt("user");
        String username = UserStatement.getName(connection, userId);

        return new ItemResult(
                results.getLong("time"), username, WorldUtils.getWorldName(results.getInt("wid")),
                results.getInt("x"), results.getInt("y"), results.getInt("z"),
                results.getInt("type"), results.getInt("amount"), DatabaseUtils.getBytes(results, "data"),
                results.getInt("action"), results.getInt("rolled_back")
        );
    }
}
