package net.coreprotect.database.lookup;

import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Locale;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;

import net.coreprotect.config.ConfigHandler;
import net.coreprotect.database.statement.UserStatement;
import net.coreprotect.language.Phrase;
import net.coreprotect.language.Selector;
import net.coreprotect.listener.channel.PluginChannelListener;
import net.coreprotect.utility.Color;
import net.coreprotect.utility.Util;

public class ChestTransactionLookup {

    public static String performLookup(String command, Statement statement, Location l, CommandSender commandSender, int page, int limit, boolean exact) {
        String result = "";

        try {
            if (l == null) {
                return result;
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
            int x = (int) Math.floor(l.getX());
            int y = (int) Math.floor(l.getY());
            int z = (int) Math.floor(l.getZ());
            int x2 = (int) Math.ceil(l.getX());
            int y2 = (int) Math.ceil(l.getY());
            int z2 = (int) Math.ceil(l.getZ());
            long time = (System.currentTimeMillis() / 1000L);
            int worldId = Util.getWorldId(l.getWorld().getName());
            int count = 0;
            int rowMax = page * limit;
            int pageStart = rowMax - limit;

            String query = "SELECT COUNT(*) as count from " + ConfigHandler.prefix + "container " + Util.getWidIndex("container") + "WHERE wid = '" + worldId + "' AND (x = '" + x + "' OR x = '" + x2 + "') AND (z = '" + z + "' OR z = '" + z2 + "') AND y = '" + y + "' LIMIT 0, 1";
            if (exact) {
                query = "SELECT COUNT(*) as count from " + ConfigHandler.prefix + "container " + Util.getWidIndex("container") + "WHERE wid = '" + worldId + "' AND (x = '" + l.getBlockX() + "') AND (z = '" + l.getBlockZ() + "') AND y = '" + y + "' LIMIT 0, 1";
            }
            ResultSet results = statement.executeQuery(query);

            while (results.next()) {
                count = results.getInt("count");
            }
            results.close();

            int totalPages = (int) Math.ceil(count / (limit + 0.0));

            query = "SELECT time,user,action,type,data,amount,rolled_back FROM " + ConfigHandler.prefix + "container " + Util.getWidIndex("container") + "WHERE wid = '" + worldId + "' AND (x = '" + x + "' OR x = '" + x2 + "') AND (z = '" + z + "' OR z = '" + z2 + "') AND y = '" + y + "' ORDER BY rowid DESC LIMIT " + pageStart + ", " + limit + "";
            if (exact) {
                query = "SELECT time,user,action,type,data,amount,rolled_back FROM " + ConfigHandler.prefix + "container " + Util.getWidIndex("container") + "WHERE wid = '" + worldId + "' AND (x = '" + l.getBlockX() + "') AND (z = '" + l.getBlockZ() + "') AND y = '" + y + "' ORDER BY rowid DESC LIMIT " + pageStart + ", " + limit + "";
            }
            results = statement.executeQuery(query);

            StringBuilder resultBuilder = new StringBuilder();
            while (results.next()) {
                int resultUserId = results.getInt("user");
                int resultAction = results.getInt("action");
                int resultType = results.getInt("type");
                int resultData = results.getInt("data");
                long resultTime = results.getLong("time");
                int resultAmount = results.getInt("amount");
                int resultRolledBack = results.getInt("rolled_back");

                if (ConfigHandler.playerIdCacheReversed.get(resultUserId) == null) {
                    UserStatement.loadName(statement.getConnection(), resultUserId);
                }

                String resultUser = ConfigHandler.playerIdCacheReversed.get(resultUserId);
                String timeAgo = Util.getTimeSince(resultTime, time, true);

                if (!found) {
                    resultBuilder = new StringBuilder(Color.WHITE + "----- " + Color.DARK_AQUA + Phrase.build(Phrase.CONTAINER_HEADER) + Color.WHITE + " ----- " + Util.getCoordinates(command, worldId, x, y, z, false, false) + "\n");
                }
                found = true;

                String selector = (resultAction != 0 ? Selector.FIRST : Selector.SECOND);
                String tag = (resultAction != 0 ? Color.GREEN + "+" : Color.RED + "-");
                String rbFormat = "";
                if (resultRolledBack == 1 || resultRolledBack == 3) {
                    rbFormat = Color.STRIKETHROUGH;
                }

                Material resultMaterial = Util.getType(resultType);
                if (resultMaterial == null) {
                    resultMaterial = Material.AIR;
                }
                String target = resultMaterial.name().toLowerCase(Locale.ROOT);
                target = Util.nameFilter(target, resultData);
                if (target.length() > 0) {
                    target = "minecraft:" + target.toLowerCase(Locale.ROOT) + "";
                }

                // Hide "minecraft:" for now.
                if (target.startsWith("minecraft:")) {
                    target = target.split(":")[1];
                }

                resultBuilder.append(timeAgo + " " + tag + " ").append(Phrase.build(Phrase.LOOKUP_CONTAINER, Color.DARK_AQUA + rbFormat + resultUser + Color.WHITE + rbFormat, "x" + resultAmount, Color.DARK_AQUA + rbFormat + target + Color.WHITE, selector)).append("\n");
                PluginChannelListener.getInstance().sendData(commandSender, resultTime, Phrase.LOOKUP_CONTAINER, selector, resultUser, target, resultAmount, x, y, z, worldId, rbFormat, true, tag.contains("+"));
            }
            result = resultBuilder.toString();
            results.close();

            if (found) {
                if (count > limit) {
                    String pageInfo = Color.WHITE + "-----\n";
                    pageInfo = pageInfo + Util.getPageNavigation(command, page, totalPages) + "\n";
                    result = result + pageInfo;
                }
            }
            else {
                if (rowMax > count && count > 0) {
                    result = Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.NO_RESULTS_PAGE, Selector.SECOND);
                }
                else {
                    result = Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.NO_DATA_LOCATION, Selector.SECOND);
                }
            }

            ConfigHandler.lookupType.put(commandSender.getName(), 1);
            ConfigHandler.lookupPage.put(commandSender.getName(), page);
            ConfigHandler.lookupCommand.put(commandSender.getName(), x + "." + y + "." + z + "." + worldId + "." + x2 + "." + y2 + "." + z2 + "." + limit);
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        return result;
    }

}
