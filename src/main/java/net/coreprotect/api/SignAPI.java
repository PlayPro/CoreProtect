package net.coreprotect.api;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

import org.bukkit.Location;

import net.coreprotect.api.result.SignResult;
import net.coreprotect.config.Config;
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.database.Database;
import net.coreprotect.database.statement.UserStatement;
import net.coreprotect.model.action.SignActions;
import net.coreprotect.utility.WorldUtils;
import net.coreprotect.utility.ErrorReporter;

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
        List<SignResult> result = new ArrayList<>();

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

            StringBuilder query = new StringBuilder("SELECT time," + ConfigHandler.databaseType.getUserColumn() + ",wid,x,y,z,action,color,color_secondary,data,waxed,face,line_1,line_2,line_3,line_4,line_5,line_6,line_7,line_8 FROM ");
            query.append(ConfigHandler.prefix).append("sign ");
            if (filter.hasLocation()) {
                query.append(WorldUtils.getWidIndex("sign"));
            }
            filter.appendWhere(query);
            query.append(" AND action = '").append(SignActions.PLACE).append("' AND (LENGTH(line_1) > 0 OR LENGTH(line_2) > 0 OR LENGTH(line_3) > 0 OR LENGTH(line_4) > 0 OR LENGTH(line_5) > 0 OR LENGTH(line_6) > 0 OR LENGTH(line_7) > 0 OR LENGTH(line_8) > 0)");
            query.append(" ORDER BY rowid DESC");
            filter.appendLimit(query);

            try (PreparedStatement statement = connection.prepareStatement(query.toString())) {
                filter.bind(statement);

                try (ResultSet results = statement.executeQuery()) {
                    while (results.next()) {
                        result.add(parseSignResult(connection, results));
                    }
                }
            }
        }
        catch (Exception e) {
            ErrorReporter.report(e);
        }

        return result;
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
