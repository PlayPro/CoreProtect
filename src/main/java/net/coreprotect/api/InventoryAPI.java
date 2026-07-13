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
import net.coreprotect.model.item.InventorySources;
import net.coreprotect.utility.WorldUtils;
import net.coreprotect.utility.ErrorReporter;

/**
 * Provides API methods for normalized player inventory transaction lookups. Tracked entity-container rows match their original or persisted
 * current/final location and expose the persisted current/final location as container-source results.
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
            StringBuilder entityWhereBuilder = new StringBuilder();
            filter.appendEntityContainerWhere(entityWhereBuilder, "entity_rows");
            String query = buildQuery(where, entityWhereBuilder.toString(), options.hasLimit());

            try (PreparedStatement statement = connection.prepareStatement(query)) {
                int parameterIndex = filter.bind(statement);
                parameterIndex = filter.bind(statement, parameterIndex);
                parameterIndex = filter.bindEntityContainer(statement, parameterIndex);
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

    private static String buildQuery(String where, String entityWhere, boolean hasLimit) {
        StringBuilder query = new StringBuilder("SELECT * FROM (");
        query.append("SELECT 0 AS source,rowid AS id,time,user,wid,x,y,z,type,data,1 AS amount,meta AS metadata,action,rolled_back FROM ")
                .append(ConfigHandler.prefix).append("block ").append(where).append(" AND action = 1");
        query.append(" UNION ALL ");
        query.append("SELECT 1 AS source,rowid AS id,time,user,wid,x,y,z,type,data,amount,metadata,action,rolled_back FROM ")
                .append(ConfigHandler.prefix).append("container ").append(where);
        query.append(" UNION ALL ");
        query.append("SELECT ").append(InventorySources.ENTITY_CONTAINER).append(" AS source,entity_rows.rowid AS id,entity_rows.time,entity_rows.user,spawn_rows.current_wid AS wid,spawn_rows.x,spawn_rows.y,spawn_rows.z,entity_rows.type,entity_rows.data,entity_rows.amount,entity_rows.metadata,entity_rows.action,entity_rows.rolled_back FROM ")
                .append(ConfigHandler.prefix).append("entity_container FINAL AS entity_rows JOIN ").append(ConfigHandler.prefix).append("entity_spawn FINAL AS spawn_rows ON spawn_rows.rowid=entity_rows.entity_spawn_rowid ").append(entityWhere);
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

        int source = results.getInt("source");
        if (source == InventorySources.ENTITY_CONTAINER) {
            source = InventorySources.CONTAINER;
        }

        return new InventoryResult(
                results.getLong("time"), username, WorldUtils.getWorldName(results.getInt("wid")),
                (int) Math.floor(results.getDouble("x")), (int) Math.floor(results.getDouble("y")), (int) Math.floor(results.getDouble("z")),
                results.getInt("type"), results.getInt("data"), results.getInt("amount"), results.getBytes("metadata"),
                results.getInt("action"), results.getInt("rolled_back"), source
        );
    }
}
