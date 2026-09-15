package net.coreprotect.api;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.StringJoiner;

import org.bukkit.entity.EntityType;

import net.coreprotect.api.result.EntityResult;
import net.coreprotect.config.Config;
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.database.Database;
import net.coreprotect.database.statement.UserStatement;
import net.coreprotect.model.action.LookupActions;
import net.coreprotect.utility.ErrorReporter;
import net.coreprotect.utility.WorldUtils;

/**
 * Provides API methods for looking up entity spawn and kill events.
 */
public class EntityAPI {

    private EntityAPI() {
        throw new IllegalStateException("API class");
    }

    /**
     * Performs a typed entity lookup, matching original or persisted current/final spawn locations.
     *
     * @param options
     *            Lookup options
     * @return List of entity results at their original event locations
     */
    public static List<EntityResult> performLookup(LookupOptions options) {
        List<EntityResult> result = new ArrayList<>();
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

            boolean snapshot = filter.beginDuckDBSnapshot(connection);
            try {
                StringBuilder query = new StringBuilder("SELECT entity_rows.time,entity_rows." + ConfigHandler.databaseType.getUserColumn() + ",entity_rows.wid,entity_rows.x,entity_rows.y,entity_rows.z,entity_rows.type,entity_rows.action,entity_rows.rolled_back FROM ");
                query.append(filter.entityTable(connection, "entity_rows"));
                query.append(' ');
                filter.appendEntityWhere(connection, query, "entity_rows");
                int[] actions = options.getEntityActions().isEmpty()
                        ? new int[] { LookupActions.ENTITY_KILL, LookupActions.ENTITY_SPAWN }
                        : options.getEntityActions().stream().mapToInt(EntityAction::id).toArray();
                LookupFilter.appendActionWhere(query, "entity_rows", actions);
                if (!options.getIncludeEntities().isEmpty()) {
                    query.append(" AND ").append(entityPredicate(options.getIncludeEntities()));
                }
                if (!options.getExcludeEntities().isEmpty()) {
                    query.append(" AND NOT ").append(entityPredicate(options.getExcludeEntities()));
                }
                query.append(" ORDER BY ").append(ConfigHandler.getDescendingEventOrder().replace("time", "entity_rows.time").replace("rowid", "entity_rows.rowid"));
                filter.appendLimit(query);

                try (PreparedStatement statement = connection.prepareStatement(query.toString())) {
                    filter.bindTrackedEntity(statement, 1);
                    try (ResultSet results = statement.executeQuery()) {
                        while (results.next()) {
                            result.add(new EntityResult(
                                    results.getLong("time"), UserStatement.getName(connection, results.getInt("user")), WorldUtils.getWorldName(results.getInt("wid")),
                                    results.getInt("x"), results.getInt("y"), results.getInt("z"), results.getInt("type"), results.getInt("action"), results.getInt("rolled_back")
                            ));
                        }
                    }
                }
            }
            finally {
                filter.endDuckDBSnapshot(connection, snapshot);
            }
        }
        catch (Exception e) {
            ErrorReporter.report(e);
        }
        return result;
    }

    private static String entityPredicate(List<EntityType> entities) {
        StringJoiner names = new StringJoiner(",");
        for (EntityType entity : entities) {
            String name = entity.name().toLowerCase(Locale.ROOT);
            names.add("'" + name + "'");
            names.add("'minecraft:" + name + "'");
        }
        String predicate = "entity_rows.type IN (SELECT id FROM " + ConfigHandler.prefix + "entity_map WHERE LOWER(entity) IN (" + names + "))";
        if (entities.contains(EntityType.PLAYER)) {
            predicate += " OR (entity_rows.action=" + LookupActions.ENTITY_KILL + " AND entity_rows.type=0)";
        }
        return "(" + predicate + ")";
    }
}
