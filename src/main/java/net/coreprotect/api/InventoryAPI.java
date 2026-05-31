package net.coreprotect.api;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

import net.coreprotect.api.result.InventoryResult;
import net.coreprotect.config.Config;
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.database.Database;
import net.coreprotect.database.statement.UserStatement;
import net.coreprotect.utility.WorldUtils;
import net.coreprotect.utility.ErrorReporter;

/**
 * Provides API methods for normalized player inventory transaction lookups.
 */
public class InventoryAPI {

    private InventoryAPI() {
        throw new IllegalStateException("API class");
    }

    public static List<InventoryResult> performLookup(LookupOptions options) {
        List<InventoryResult> result = new ArrayList<>();

        if (!Config.getGlobal().API_ENABLED) {
            return result;
        }

        if (options == null) {
            options = LookupOptions.builder().build();
        }

        try (Connection connection = Database.getConnection(false, 1000)) {
            if (connection == null) {
                return result;
            }

            LookupFilter filter = LookupFilter.fromOptions(connection, options);
            if (filter.hasInvalidUser() || filter.hasInvalidLocation()) {
                return result;
            }

            StringBuilder whereBuilder = new StringBuilder();
            filter.appendWhere(whereBuilder);
            String where = whereBuilder.toString();
            String query = buildQuery(where, options.hasLimit());

            try (PreparedStatement statement = connection.prepareStatement(query)) {
                int parameterIndex = filter.bind(statement);
                parameterIndex = filter.bind(statement, parameterIndex);
                parameterIndex = filter.bind(statement, parameterIndex);
                if (options.hasLimit()) {
                    statement.setInt(parameterIndex++, options.getLimitOffset());
                    statement.setInt(parameterIndex, options.getLimitCount());
                }

                try (ResultSet results = statement.executeQuery()) {
                    while (results.next()) {
                        result.add(parseInventoryResult(connection, results));
                    }
                }
            }
        }
        catch (Exception e) {
            ErrorReporter.report(e);
        }

        return result;
    }

    private static String buildQuery(String where, boolean hasLimit) {
        StringBuilder query = new StringBuilder("SELECT * FROM (");
        query.append("SELECT 0 AS source,rowid AS id,time,user,wid,x,y,z,type,data,1 AS amount,meta AS metadata,action,rolled_back FROM ")
                .append(ConfigHandler.prefix).append("block ").append(where).append(" AND action = 1");
        query.append(" UNION ALL ");
        query.append("SELECT 1 AS source,rowid AS id,time,user,wid,x,y,z,type,data,amount,metadata,action,rolled_back FROM ")
                .append(ConfigHandler.prefix).append("container ").append(where);
        query.append(" UNION ALL ");
        query.append("SELECT 2 AS source,rowid AS id,time,user,wid,x,y,z,type,0 AS data,amount,data AS metadata,action,rolled_back FROM ")
                .append(ConfigHandler.prefix).append("item ").append(where);
        query.append(") AS inventory_lookup ORDER BY time DESC, source DESC, id DESC");
        if (hasLimit) {
            query.append(" LIMIT ?, ?");
        }

        return query.toString();
    }

    private static InventoryResult parseInventoryResult(Connection connection, ResultSet results) throws Exception {
        int userId = results.getInt("user");
        String username = ConfigHandler.playerIdCacheReversed.get(userId);
        if (username == null) {
            username = UserStatement.loadName(connection, userId);
        }

        return new InventoryResult(
                results.getLong("time"), username, WorldUtils.getWorldName(results.getInt("wid")),
                results.getInt("x"), results.getInt("y"), results.getInt("z"),
                results.getInt("type"), results.getInt("data"), results.getInt("amount"), results.getBytes("metadata"),
                results.getInt("action"), results.getInt("rolled_back"), results.getInt("source")
        );
    }
}
