package net.coreprotect.database.lookup;

import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.bukkit.Location;
import org.bukkit.command.CommandSender;

import net.coreprotect.config.ConfigHandler;
import net.coreprotect.utility.LookupThrottle;
import net.coreprotect.database.Database;
import net.coreprotect.database.DuckDBLookupQuery;
import net.coreprotect.database.LocationQuery;
import net.coreprotect.database.statement.UserStatement;
import net.coreprotect.language.Phrase;
import net.coreprotect.language.Selector;
import net.coreprotect.listener.channel.PluginChannelListener;
import net.coreprotect.model.action.SignActions;
import net.coreprotect.utility.ChatUtils;
import net.coreprotect.utility.Color;
import net.coreprotect.utility.WorldUtils;

public class SignMessageLookup {

    private static final Pattern HEX_FORMATTING = Pattern.compile("§x(§[a-fA-F0-9]){6}");

    public static List<String> performLookup(String command, Statement statement, Location l, CommandSender commandSender, int page, int limit) {
        List<String> result = new ArrayList<>();

        try {
            if (l == null) {
                return result;
            }

            command = InspectorLookup.resolveCommand(command, commandSender);

            boolean found = false;
            int x = l.getBlockX();
            int y = l.getBlockY();
            int z = l.getBlockZ();
            long time = (System.currentTimeMillis() / 1000L);
            int worldId = WorldUtils.getWorldId(l.getWorld().getName());

            String where = LocationQuery.predicate("wid", " = " + worldId) + " AND " + LocationQuery.predicate("x", " = " + x) + " AND " + LocationQuery.predicate("z", " = " + z) + " AND y = " + y + " AND action = " + SignActions.PLACE + " AND " + SignLookupText.HAS_VISIBLE_TEXT;
            String spatialTable = ConfigHandler.databaseType.isDuckDB()
                    ? DuckDBLookupQuery.spatialTable(statement.getConnection(), "sign", worldId, x, x, z, z, "spatial_rows") : null;
            String columns = "time," + ConfigHandler.databaseType.getUserColumn() + ",face,line_1,line_2,line_3,line_4,line_5,line_6,line_7,line_8";
            InspectorLookup.Page pageQuery = InspectorLookup.queryPage(statement, ConfigHandler.prefix + "sign", spatialTable, WorldUtils.getWidIndex("sign"), where, columns,
                    ConfigHandler.getDescendingEventOrder(), false, page, limit);
            long count = pageQuery.count;

            try (ResultSet results = statement.executeQuery(pageQuery.query)) {
                while (results.next()) {
                    if (pageQuery.combinedCount) {
                        count = results.getLong("count");
                        if (results.getObject("result_id") == null) {
                            continue;
                        }
                    }
                    long resultTime = results.getLong("time");
                    int resultUserId = results.getInt("user");
                    boolean isFront = results.getInt("face") == 0;
                    String message = SignLookupText.read(results, isFront);
                    String parsedMessage = HEX_FORMATTING.matcher(message).replaceAll("");
                    String resultUser = UserStatement.getName(statement.getConnection(), resultUserId);
                    String timeAgo = ChatUtils.getTimeSince(resultTime, time, true);

                    if (!found) {
                        result.add(new StringBuilder(Color.WHITE + "----- " + Color.DARK_AQUA + Phrase.build(Phrase.SIGN_HEADER) + Color.WHITE + " ----- " + ChatUtils.getCoordinates(command, worldId, x, y, z, false, false) + "").toString());
                    }
                    found = true;
                    result.add(timeAgo + Color.WHITE + " - " + Color.DARK_AQUA + resultUser + ": " + Color.WHITE + "\n" + parsedMessage + Color.WHITE);
                    PluginChannelListener.getInstance().sendMessageData(commandSender, resultTime, resultUser, message, true, x, y, z, worldId);
                }
            }
            int totalPages = InspectorLookup.totalPages(count, limit);

            if (found) {
                if (count > limit) {
                    result.add(Color.WHITE + "-----");
                    result.add(ChatUtils.getPageNavigation(command, page, totalPages));
                }
            }
            else {
                if (pageQuery.end > count && count > 0) {
                    result.add(Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.NO_RESULTS_PAGE, Selector.SECOND));
                }
                else {
                    result.add(Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.NO_DATA_LOCATION, Selector.FOURTH));
                }
            }

            String name = commandSender.getName();
            String history = x + "." + y + "." + z + "." + worldId + ".8." + limit;
            LookupThrottle.publish(name, () -> {
                ConfigHandler.lookupType.put(name, 8);
                ConfigHandler.lookupPage.put(name, page);
                ConfigHandler.lookupCommand.put(name, history);
            });
        }
        catch (Exception e) {
            Database.reportDatabaseFailure(e);
        }

        return result;
    }

}
