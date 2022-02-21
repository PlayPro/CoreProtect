package net.coreprotect.database.lookup;

import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import org.bukkit.Location;
import org.bukkit.command.CommandSender;

import net.coreprotect.config.ConfigHandler;
import net.coreprotect.database.statement.UserStatement;
import net.coreprotect.language.Phrase;
import net.coreprotect.language.Selector;
import net.coreprotect.utility.Color;
import net.coreprotect.utility.Util;

public class SignMessageLookup {

    public static List<String> performLookup(String command, Statement statement, Location l, CommandSender commandSender, int page, int limit) {
        List<String> result = new ArrayList<>();

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
            int x = l.getBlockX();
            int y = l.getBlockY();
            int z = l.getBlockZ();
            long time = (System.currentTimeMillis() / 1000L);
            int worldId = Util.getWorldId(l.getWorld().getName());
            int count = 0;
            int rowMax = page * limit;
            int pageStart = rowMax - limit;

            String query = "SELECT COUNT(*) as count from " + ConfigHandler.prefix + "sign WHERE wid = '" + worldId + "' AND x = '" + x + "' AND z = '" + z + "' AND y = '" + y + "' AND action = '1' AND (LENGTH(line_1) > 0 OR LENGTH(line_2) > 0 OR LENGTH(line_3) > 0 OR LENGTH(line_4) > 0) LIMIT 0, 1";
            ResultSet results = statement.executeQuery(query);

            while (results.next()) {
                count = results.getInt("count");
            }
            results.close();

            int totalPages = (int) Math.ceil(count / (limit + 0.0));

            query = "SELECT time,user,line_1,line_2,line_3,line_4 FROM " + ConfigHandler.prefix + "sign WHERE wid = '" + worldId + "' AND x = '" + x + "' AND z = '" + z + "' AND y = '" + y + "' AND action = '1' AND (LENGTH(line_1) > 0 OR LENGTH(line_2) > 0 OR LENGTH(line_3) > 0 OR LENGTH(line_4) > 0) ORDER BY rowid DESC LIMIT " + pageStart + ", " + limit + "";
            results = statement.executeQuery(query);

            while (results.next()) {
                long resultTime = results.getLong("time");
                int resultUserId = results.getInt("user");
                String line1 = results.getString("line_1");
                String line2 = results.getString("line_2");
                String line3 = results.getString("line_3");
                String line4 = results.getString("line_4");

                StringBuilder message = new StringBuilder();
                if (line1 != null && line1.length() > 0) {
                    message.append(line1);
                    if (!line1.endsWith(" ")) {
                        message.append(" ");
                    }
                }
                if (line2 != null && line2.length() > 0) {
                    message.append(line2);
                    if (!line2.endsWith(" ")) {
                        message.append(" ");
                    }
                }
                if (line3 != null && line3.length() > 0) {
                    message.append(line3);
                    if (!line3.endsWith(" ")) {
                        message.append(" ");
                    }
                }
                if (line4 != null && line4.length() > 0) {
                    message.append(line4);
                    if (!line4.endsWith(" ")) {
                        message.append(" ");
                    }
                }

                if (ConfigHandler.playerIdCacheReversed.get(resultUserId) == null) {
                    UserStatement.loadName(statement.getConnection(), resultUserId);
                }

                String resultUser = ConfigHandler.playerIdCacheReversed.get(resultUserId);
                String timeAgo = Util.getTimeSince(resultTime, time, true);

                if (!found) {
                    result.add(new StringBuilder(Color.WHITE + "----- " + Color.DARK_AQUA + Phrase.build(Phrase.SIGN_HEADER) + Color.WHITE + " ----- " + Util.getCoordinates(command, worldId, x, y, z, false, false) + "").toString());
                }
                found = true;
                result.add(timeAgo + Color.WHITE + " - " + Color.DARK_AQUA + resultUser + ": " + Color.WHITE + "\n" + message.toString() + Color.WHITE);
            }
            results.close();

            if (found) {
                if (count > limit) {
                    result.add(Color.WHITE + "-----");
                    result.add(Util.getPageNavigation(command, page, totalPages));
                }
            }
            else {
                if (rowMax > count && count > 0) {
                    result.add(Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.NO_RESULTS_PAGE, Selector.SECOND));
                }
                else {
                    result.add(Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.NO_DATA_LOCATION, Selector.FOURTH));
                }
            }

            ConfigHandler.lookupType.put(commandSender.getName(), 8);
            ConfigHandler.lookupPage.put(commandSender.getName(), page);
            ConfigHandler.lookupCommand.put(commandSender.getName(), x + "." + y + "." + z + "." + worldId + ".8." + limit);
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        return result;
    }

}
