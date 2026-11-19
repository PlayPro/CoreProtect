package net.coreprotect.api;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

import org.bukkit.Location;

import net.coreprotect.api.result.SignResult;
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.database.LookupRaw;
import net.coreprotect.database.statement.UserStatement;
import net.coreprotect.model.action.SignActions;
import net.coreprotect.utility.WorldUtils;

/**
 * Provides API methods for sign text lookups.
 */
public class SignAPI {

    private SignAPI() {
        throw new IllegalStateException("API class");
    }

    public static List<SignResult> performLookup(Location location, int offset) {
        if (location == null) {
            return new ArrayList<>();
        }

        return performLookup(LookupOptions.builder().time(offset).location(location).build());
    }

    public static List<SignResult> performLookup(LookupOptions options) {
        return LookupFilter.execute(options, (connection, filter, selected, result) -> {
            StringBuilder query = new StringBuilder("SELECT time," + ConfigHandler.databaseType.getUserColumn() + ",wid,x,y,z,action,color,color_secondary,data,waxed,face,line_1,line_2,line_3,line_4,line_5,line_6,line_7,line_8 FROM ");
            query.append(filter.table(connection, "sign", "")).append(' ');
            if (filter.hasLocation() && !ConfigHandler.databaseType.isDuckDB()) {
                query.append(WorldUtils.getWidIndex("sign"));
            }
            filter.appendWhere(query);
            List<String> messageBindings = new ArrayList<>();
            query = new StringBuilder(LookupRaw.appendMessageFilters(query.toString(),
                    selected.getTextStartsWithAny(),
                    selected.getTextStartsWithNone(), "sign", messageBindings));
            query.append(" AND action = ").append(SignActions.PLACE).append(" AND (LENGTH(line_1) > 0 OR LENGTH(line_2) > 0 OR LENGTH(line_3) > 0 OR LENGTH(line_4) > 0 OR LENGTH(line_5) > 0 OR LENGTH(line_6) > 0 OR LENGTH(line_7) > 0 OR LENGTH(line_8) > 0)");
            query.append(" ORDER BY ").append(ConfigHandler.getDescendingEventOrder());
            filter.appendLimit(query);

            try (PreparedStatement statement = connection.prepareStatement(query.toString())) {
                int parameterIndex = filter.bind(statement);
                for (String binding : messageBindings) {
                    statement.setString(parameterIndex++, binding);
                }

                try (ResultSet results = statement.executeQuery()) {
                    while (results.next()) {
                        result.add(parseSignResult(connection, results));
                    }
                }
            }
        });
    }

    private static SignResult parseSignResult(Connection connection, ResultSet results) throws Exception {
        int userId = results.getInt("user");
        String username = UserStatement.getName(connection, userId);

        String[] lines = new String[] {
                valueOrEmpty(results.getString("line_1")),
                valueOrEmpty(results.getString("line_2")),
                valueOrEmpty(results.getString("line_3")),
                valueOrEmpty(results.getString("line_4")),
                valueOrEmpty(results.getString("line_5")),
                valueOrEmpty(results.getString("line_6")),
                valueOrEmpty(results.getString("line_7")),
                valueOrEmpty(results.getString("line_8"))
        };

        return new SignResult(
                results.getLong("time"), username, WorldUtils.getWorldName(results.getInt("wid")),
                results.getInt("x"), results.getInt("y"), results.getInt("z"),
                results.getInt("action"), results.getInt("color"), results.getInt("color_secondary"),
                results.getInt("data"), results.getInt("waxed") == 1, results.getInt("face") == 0, lines
        );
    }

    private static String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }
}
