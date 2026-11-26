package net.coreprotect.database.lookup;

import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Locale;

import org.bukkit.block.Block;
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
import net.coreprotect.utility.ChatUtils;
import net.coreprotect.utility.Color;
import net.coreprotect.utility.MaterialUtils;
import net.coreprotect.utility.WorldUtils;

public class InteractionLookup {

    public static String performLookup(String command, Statement statement, Block block, CommandSender commandSender, int offset, int page, int limit) {
        String result = "";

        try {
            if (block == null) {
                return result;
            }

            command = InspectorLookup.resolveCommand(command, commandSender);

            boolean found = false;
            int x = block.getX();
            int y = block.getY();
            int z = block.getZ();
            long time = System.currentTimeMillis() / 1000L;
            int worldId = WorldUtils.getWorldId(block.getWorld().getName());
            long checkTime = 0L;
            if (offset > 0) {
                checkTime = time - offset;
            }

            String where = LocationQuery.predicate("wid", " = " + worldId) + " AND " + LocationQuery.predicate("x", " = " + x) + " AND " + LocationQuery.predicate("z", " = " + z) + " AND y = " + y + " AND action=2 AND time >= " + checkTime;
            String spatialTable = ConfigHandler.databaseType.isDuckDB()
                    ? DuckDBLookupQuery.spatialTable(statement.getConnection(), "block", worldId, x, x, z, z, "spatial_rows") : null;
            String columns = "time," + ConfigHandler.databaseType.getUserColumn() + ",action,type,data,rolled_back";
            InspectorLookup.Page pageQuery = InspectorLookup.queryPage(statement, ConfigHandler.prefix + "block", spatialTable, WorldUtils.getWidIndex("block"), where, columns,
                    ConfigHandler.getDescendingEventOrder(), false, page, limit);
            long count = pageQuery.count;

            StringBuilder resultBuilder = new StringBuilder();
            try (ResultSet results = statement.executeQuery(pageQuery.query)) {
                while (results.next()) {
                    if (pageQuery.combinedCount) {
                        count = results.getLong("count");
                        if (results.getObject("result_id") == null) {
                            continue;
                        }
                    }
                    int resultUserId = results.getInt("user");
                    int resultAction = results.getInt("action");
                    int resultType = results.getInt("type");
                    int resultData = results.getInt("data");
                    long resultTime = results.getLong("time");
                    int resultRolledBack = results.getInt("rolled_back");

                    String resultUser = UserStatement.getName(statement.getConnection(), resultUserId);
                    String timeAgo = ChatUtils.getTimeSince(resultTime, time, true);

                    if (!found) {
                        resultBuilder = new StringBuilder(Color.WHITE + "----- " + Color.DARK_AQUA + Phrase.build(Phrase.INTERACTIONS_HEADER) + Color.WHITE + " ----- " + ChatUtils.getCoordinates(command, worldId, x, y, z, false, false) + "\n");
                    }
                    found = true;

                    String rbFormat = "";
                    if (resultRolledBack == 1 || resultRolledBack == 3) {
                        rbFormat = Color.STRIKETHROUGH;
                    }

                    String target = MaterialUtils.getBlockDisplayName(resultType, resultData);
                    if (target.length() > 0 && !target.contains(":")) {
                        target = "minecraft:" + target.toLowerCase(Locale.ROOT) + "";
                    }

                    target = InspectorLookup.displayTarget(target);

                    resultBuilder.append(timeAgo + " " + Color.WHITE + "- ").append(Phrase.build(Phrase.LOOKUP_INTERACTION, Color.DARK_AQUA + rbFormat + resultUser + Color.WHITE + rbFormat, Color.DARK_AQUA + rbFormat + target + Color.WHITE, Selector.FIRST)).append("\n");
                    PluginChannelListener.getInstance().sendData(commandSender, resultTime, Phrase.LOOKUP_INTERACTION, Selector.FIRST, resultUser, target, -1, x, y, z, worldId, rbFormat, false, false);
                }
            }
            result = resultBuilder.toString();
            int totalPages = InspectorLookup.totalPages(count, limit);

            if (found) {
                if (count > limit) {
                    String pageInfo = Color.WHITE + "-----\n";
                    pageInfo = pageInfo + ChatUtils.getPageNavigation(command, page, totalPages) + "\n";
                    result = result + pageInfo;
                }
            }
            else {
                if (pageQuery.end > count && count > 0) {
                    result = Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.NO_RESULTS_PAGE, Selector.SECOND);
                }
                else {
                    result = Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.NO_DATA_LOCATION, Selector.THIRD);
                }
            }

            String name = commandSender.getName();
            String history = x + "." + y + "." + z + "." + worldId + ".2." + limit;
            LookupThrottle.publish(name, () -> {
                ConfigHandler.lookupPage.put(name, page);
                ConfigHandler.lookupType.put(name, 7);
                ConfigHandler.lookupCommand.put(name, history);
            });
        }
        catch (Exception e) {
            Database.reportDatabaseFailure(e);
        }

        return result;
    }

}
