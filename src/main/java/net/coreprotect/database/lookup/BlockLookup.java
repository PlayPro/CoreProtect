package net.coreprotect.database.lookup;

import net.coreprotect.config.ConfigHandler;
import net.coreprotect.database.DuckDBLookupQuery;
import net.coreprotect.database.LocationQuery;
import net.coreprotect.database.statement.EntitySpawnStatement;
import net.coreprotect.database.statement.UserStatement;
import net.coreprotect.language.Phrase;
import net.coreprotect.language.Selector;
import net.coreprotect.listener.channel.PluginChannelListener;
import net.coreprotect.model.action.LookupActions;
import net.coreprotect.utility.*;
import net.coreprotect.utility.ErrorReporter;
import org.bukkit.Material;
import org.bukkit.block.BlockState;
import org.bukkit.command.CommandSender;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;
import java.util.StringJoiner;
import java.util.UUID;

public class BlockLookup {

    public static String performLookup(String command, Statement statement, BlockState block, CommandSender commandSender, int offset, int page, int limit) {
        return performLookup(command, statement, block, commandSender, offset, page, limit, null);
    }

    public static String performEntityLookup(String command, Statement statement, BlockState block, CommandSender commandSender, int page, int limit, UUID entityUuid) throws SQLException {
        Integer entitySpawnRowId = EntitySpawnStatement.findRowIdByUuid(statement.getConnection(), entityUuid);
        if (entitySpawnRowId == null) {
            ConfigHandler.lookupType.remove(commandSender.getName());
            ConfigHandler.lookupCommand.remove(commandSender.getName());
            ConfigHandler.lookupPage.remove(commandSender.getName());
            return Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.NO_DATA_LOCATION, Selector.FIRST);
        }
        return performLookup(command, statement, block, commandSender, 0, page, limit, entitySpawnRowId);
    }

    public static String performLookup(String command, Statement statement, BlockState block, CommandSender commandSender, int offset, int page, int limit, Integer entitySpawnRowId) {
        String resultText = "";

        try {
            if (block == null) {
                return resultText;
            }

            if (command == null) {
                if (commandSender.hasPermission("coreprotect.co")) {
                    command = "co";
                }
                else if (commandSender.hasPermission("coreprotect.core")) {
                    command = "core";
                }
                else if (commandSender.hasPermission("coreprotect.coreprotect")) {
                    command = "coreprotect";
                }
                else {
                    command = "co";
                }
            }

            boolean found = false;
            int x = block.getX();
            int y = block.getY();
            int z = block.getZ();
            long time = (System.currentTimeMillis() / 1000L);
            int worldId = WorldUtils.getWorldId(block.getWorld().getName());
            long checkTime = 0;
            int count = 0;
            int rowMax = page * limit;
            int page_start = rowMax - limit;
            if (offset > 0) {
                checkTime = time - offset;
            }

            String blockName = block.getType().name().toLowerCase(Locale.ROOT);
            String where;
            String index;
            if (entitySpawnRowId == null) {
                String actionPredicate = "(action IN(0,1," + LookupActions.ENTITY_SPAWN + ") OR (action=" + LookupActions.ENTITY_KILL + " AND type IN(" + placedEntityTypeIds() + ")))";
                where = LocationQuery.predicate("wid", " = " + worldId) + " AND " + LocationQuery.predicate("x", " = " + x) + " AND " + LocationQuery.predicate("z", " = " + z) + " AND y = " + y + " AND " + actionPredicate + " AND time >= " + checkTime;
                index = WorldUtils.getWidIndex("block");
            }
            else {
                where = "rowid IN(" + entityBlockRowIds(statement, entitySpawnRowId) + ") AND action IN(" + LookupActions.ENTITY_SPAWN + "," + LookupActions.ENTITY_KILL + ") AND time >= " + checkTime;
                index = "";
            }
            boolean combinedDuckDBPage = ConfigHandler.databaseType.isDuckDB();
            String query;
            ResultSet results;
            if (combinedDuckDBPage) {
                String sourceTable = entitySpawnRowId == null ? DuckDBLookupQuery.spatialTable(statement.getConnection(), "block", worldId, x, x, z, z, "spatial_rows") : ConfigHandler.prefix + "block";
                String columns = "data_rows.time,data_rows." + ConfigHandler.databaseType.getUserColumn() + ",data_rows.action,data_rows.type,data_rows.data,data_rows.rolled_back";
                if (entitySpawnRowId != null) {
                    columns += ",data_rows.wid,data_rows.x,data_rows.y,data_rows.z";
                }
                query = DuckDBLookupQuery.pageQuery(sourceTable, ConfigHandler.prefix + "block", where, columns, false, limit, page_start);
                results = statement.executeQuery(query);
            }
            else {
                query = "SELECT COUNT(*) as count from " + ConfigHandler.prefix + "block " + index + "WHERE " + where + " LIMIT 1 OFFSET 0";
                results = statement.executeQuery(query);
                while (results.next()) {
                    count = results.getInt("count");
                }
                results.close();
                String columns = "time," + ConfigHandler.databaseType.getUserColumn() + ",action,type,data,rolled_back" + (entitySpawnRowId == null ? "" : ",wid,x,y,z");
                query = "SELECT " + columns + " FROM " + ConfigHandler.prefix + "block " + index + "WHERE " + where + " ORDER BY " + ConfigHandler.getDescendingEventOrder() + " LIMIT " + limit + " OFFSET " + page_start;
                results = statement.executeQuery(query);
            }

            StringBuilder resultTextBuilder = new StringBuilder();

            while (results.next()) {
                if (combinedDuckDBPage) {
                    count = results.getInt("count");
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
                    resultTextBuilder = new StringBuilder(Color.WHITE + "----- " + Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "----- " + ChatUtils.getCoordinates(command, worldId, x, y, z, false, false) + "\n");
                }
                found = true;

                Phrase phrase = Phrase.LOOKUP_BLOCK;
                String selector = Selector.FIRST;
                String tag = Color.WHITE + "-";
                if (resultAction == LookupActions.ENTITY_SPAWN) {
                    phrase = EntitySpawnTracking.isPlacedEntityType(EntityUtils.getEntityType(resultType)) ? Phrase.LOOKUP_BLOCK : Phrase.LOOKUP_ENTITY_SPAWN;
                    selector = Selector.FIRST;
                    tag = Color.GREEN + "+";
                }
                else if (resultAction == LookupActions.ENTITY_KILL && EntitySpawnTracking.isPlacedEntityType(EntityUtils.getEntityType(resultType))) {
                    phrase = Phrase.LOOKUP_BLOCK;
                    selector = Selector.SECOND;
                    tag = Color.RED + "-";
                }
                else if (resultAction == 2 || resultAction == 3) {
                    phrase = Phrase.LOOKUP_INTERACTION; // {clicked|killed}
                    selector = (resultAction != 3 ? Selector.FIRST : Selector.SECOND);
                    tag = (resultAction != 3 ? Color.WHITE + "-" : Color.RED + "-");
                }
                else {
                    phrase = Phrase.LOOKUP_BLOCK; // {placed|broke}
                    selector = (resultAction != 0 ? Selector.FIRST : Selector.SECOND);
                    tag = (resultAction != 0 ? Color.GREEN + "+" : Color.RED + "-");
                }

                String rbFormat = "";
                if (resultRolledBack == 1 || resultRolledBack == 3) {
                    rbFormat = Color.STRIKETHROUGH;
                }

                String target;
                if (resultAction == 3 || resultAction == LookupActions.ENTITY_SPAWN) {
                    target = EntityUtils.getEntityType(resultType).name().toLowerCase(Locale.ROOT);
                }
                else {
                    target = MaterialUtils.getBlockDisplayName(resultType, resultData);
                    if (target.length() > 0 && !target.contains(":")) {
                        target = "minecraft:" + target.toLowerCase(Locale.ROOT);
                    }
                }
                if (target.length() > 0) {
                    target = "" + target + "";
                }

                // Hide "minecraft:" for now.
                if (target.startsWith("minecraft:")) {
                    target = target.split(":")[1];
                }

                String coordinateInfo = "";
                if (entitySpawnRowId != null) {
                    int originWorldId = results.getInt("wid");
                    int originX = results.getInt("x");
                    int originY = results.getInt("y");
                    int originZ = results.getInt("z");
                    if (originWorldId != worldId || originX != x || originY != y || originZ != z) {
                        coordinateInfo = ChatUtils.getCoordinateTooltip(originWorldId, originX, originY, originZ, Phrase.build(Phrase.LOOKUP_ENTITY_ORIGIN), true);
                    }
                }
                resultTextBuilder.append(timeAgo + " " + tag + " ").append(Phrase.build(phrase, Color.DARK_AQUA + rbFormat + resultUser + Color.WHITE + rbFormat, Color.DARK_AQUA + rbFormat + target + Color.WHITE + coordinateInfo, selector)).append("\n");
                PluginChannelListener.getInstance().sendData(commandSender, resultTime, phrase, selector, resultUser, target, -1, x, y, z, worldId, rbFormat, false, tag.contains("+"));
            }

            resultText = resultTextBuilder.toString();
            results.close();
            int totalPages = (int) Math.ceil(count / (limit + 0.0));

            if (found) {
                if (count > limit) {
                    String pageInfo = Color.WHITE + "-----\n";
                    pageInfo = pageInfo + ChatUtils.getPageNavigation(command, page, totalPages) + "\n";
                    resultText = resultText + pageInfo;
                }
            }
            else {
                if (rowMax > count && count > 0) {
                    resultText = Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.NO_RESULTS_PAGE, Selector.SECOND);
                }
                else {
                    // resultText = Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Color.WHITE + "No block data found at " + Color.ITALIC + "x" + x + "/y" + y + "/z" + z + ".";
                    resultText = Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.NO_DATA_LOCATION, Selector.FIRST);
                    if (entitySpawnRowId == null && !blockName.equals("air") && !blockName.equals("cave_air")) {
                        resultText = Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.NO_DATA, Color.ITALIC + block.getType().name().toLowerCase(Locale.ROOT) + Color.WHITE) + "\n";
                    }
                }
            }

            ConfigHandler.lookupPage.put(commandSender.getName(), page);
            ConfigHandler.lookupType.put(commandSender.getName(), 2);
            ConfigHandler.lookupCommand.put(commandSender.getName(), x + "." + y + "." + z + "." + worldId + ".0." + limit + (entitySpawnRowId == null ? "" : "." + entitySpawnRowId));
        }
        catch (Exception e) {
            ErrorReporter.report(e);
        }
        return resultText;
    }

    private static String entityBlockRowIds(Statement statement, int entitySpawnRowId) throws SQLException {
        StringJoiner ids = new StringJoiner(",");
        long killRowId = 0L;
        try (ResultSet results = statement.executeQuery("SELECT block_rowid,kill_rowid FROM " + ConfigHandler.prefix + "entity_spawn WHERE rowid=" + entitySpawnRowId)) {
            if (results.next()) {
                long blockRowId = results.getLong("block_rowid");
                killRowId = results.getLong("kill_rowid");
                if (blockRowId > 0L) {
                    ids.add(Long.toString(blockRowId));
                }
            }
        }
        if (killRowId > 0L) {
            try (ResultSet results = statement.executeQuery("SELECT rowid AS id FROM " + ConfigHandler.prefix + "block WHERE action=" + LookupActions.ENTITY_KILL + " AND data=" + killRowId)) {
                while (results.next()) {
                    ids.add(Long.toString(results.getLong("id")));
                }
            }
        }
        return ids.length() == 0 ? "0" : ids.toString();
    }

    private static String placedEntityTypeIds() {
        StringJoiner ids = new StringJoiner(",");
        for (Integer id : EntitySpawnTracking.getPlacedEntityTypeIds()) {
            ids.add(Integer.toString(id));
        }
        return ids.length() == 0 ? "0" : ids.toString();
    }

}
