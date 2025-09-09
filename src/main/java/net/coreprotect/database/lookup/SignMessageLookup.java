package net.coreprotect.database.lookup;

import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bukkit.Location;
import org.bukkit.command.CommandSender;

import net.coreprotect.config.ConfigHandler;
import net.coreprotect.database.statement.UserStatement;
import net.coreprotect.language.Phrase;
import net.coreprotect.language.Selector;
import net.coreprotect.listener.channel.PluginChannelListener;
import net.coreprotect.utility.ChatUtils;
import net.coreprotect.utility.Color;
import net.coreprotect.utility.WorldUtils;

public class SignMessageLookup {

    static Pattern pattern = Pattern.compile("§x(§[a-fA-F0-9]){6}");

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
            int worldId = WorldUtils.getWorldId(l.getWorld().getName());
            int count = 0;
            int rowMax = page * limit;
            int pageStart = rowMax - limit;

            String query = "SELECT COUNT(*) as count from " + ConfigHandler.prefix + "sign " + WorldUtils.getWidIndex("sign") + "WHERE wid = '" + worldId + "' AND x = '" + x + "' AND z = '" + z + "' AND y = '" + y + "' AND action = '1' AND (LENGTH(line_1) > 0 OR LENGTH(line_2) > 0 OR LENGTH(line_3) > 0 OR LENGTH(line_4) > 0 OR LENGTH(line_5) > 0 OR LENGTH(line_6) > 0 OR LENGTH(line_7) > 0 OR LENGTH(line_8) > 0) LIMIT 0, 1";
            ResultSet results = statement.executeQuery(query);

            while (results.next()) {
                count = results.getInt("count");
            }
            results.close();

            int totalPages = (int) Math.ceil(count / (limit + 0.0));

            query = "SELECT time,user,face,line_1,line_2,line_3,line_4,line_5,line_6,line_7,line_8 FROM " + ConfigHandler.prefix + "sign " + WorldUtils.getWidIndex("sign") + "WHERE wid = '" + worldId + "' AND x = '" + x + "' AND z = '" + z + "' AND y = '" + y + "' AND action = '1' AND (LENGTH(line_1) > 0 OR LENGTH(line_2) > 0 OR LENGTH(line_3) > 0 OR LENGTH(line_4) > 0 OR LENGTH(line_5) > 0 OR LENGTH(line_6) > 0 OR LENGTH(line_7) > 0 OR LENGTH(line_8) > 0) ORDER BY rowid DESC LIMIT " + pageStart + ", " + limit + "";
            results = statement.executeQuery(query);

            while (results.next()) {
                long resultTime = results.getLong("time");
                int resultUserId = results.getInt("user");
                String line1 = results.getString("line_1");
                String line2 = results.getString("line_2");
                String line3 = results.getString("line_3");
                String line4 = results.getString("line_4");
                String line5 = results.getString("line_5");
                String line6 = results.getString("line_6");
                String line7 = results.getString("line_7");
                String line8 = results.getString("line_8");
                boolean isFront = results.getInt("face") == 0;

                StringBuilder message = new StringBuilder();
                if (isFront && line1 != null && line1.length() > 0) {
                    message.append(line1);
                    if (!line1.endsWith(" ")) {
                        message.append(" ");
                    }
                }
                if (isFront && line2 != null && line2.length() > 0) {
                    message.append(line2);
                    if (!line2.endsWith(" ")) {
                        message.append(" ");
                    }
                }
                if (isFront && line3 != null && line3.length() > 0) {
                    message.append(line3);
                    if (!line3.endsWith(" ")) {
                        message.append(" ");
                    }
                }
                if (isFront && line4 != null && line4.length() > 0) {
                    message.append(line4);
                    if (!line4.endsWith(" ")) {
                        message.append(" ");
                    }
                }
                if (!isFront && line5 != null && line5.length() > 0) {
                    message.append(line5);
                    if (!line5.endsWith(" ")) {
                        message.append(" ");
                    }
                }
                if (!isFront && line6 != null && line6.length() > 0) {
                    message.append(line6);
                    if (!line6.endsWith(" ")) {
                        message.append(" ");
                    }
                }
                if (!isFront && line7 != null && line7.length() > 0) {
                    message.append(line7);
                    if (!line7.endsWith(" ")) {
                        message.append(" ");
                    }
                }
                if (!isFront && line8 != null && line8.length() > 0) {
                    message.append(line8);
                    if (!line8.endsWith(" ")) {
                        message.append(" ");
                    }
                }

                String parsedMessage = message.toString();
                if (parsedMessage.contains("§x")) {
                    for (Matcher matcher = pattern.matcher(parsedMessage); matcher.find(); matcher = pattern.matcher(parsedMessage)) {
                        String color = parsedMessage.substring(matcher.start(), matcher.end());
                        parsedMessage = parsedMessage.replace(color, "");
                    }
                }

                if (ConfigHandler.playerIdCacheReversed.get(resultUserId) == null) {
                    UserStatement.loadName(statement.getConnection(), resultUserId);
                }

                String resultUser = ConfigHandler.playerIdCacheReversed.get(resultUserId);
                String timeAgo = ChatUtils.getTimeSince(resultTime, time, true);

                if (!found) {
                    result.add(new StringBuilder(Color.WHITE + "----- " + Color.DARK_AQUA + Phrase.build(Phrase.SIGN_HEADER) + Color.WHITE + " ----- " + ChatUtils.getCoordinates(command, worldId, x, y, z, false, false) + "").toString());
                }
                found = true;
                result.add(timeAgo + Color.WHITE + " - " + Color.DARK_AQUA + resultUser + ": " + Color.WHITE + "\n" + parsedMessage + Color.WHITE);
                PluginChannelListener.getInstance().sendMessageData(commandSender, resultTime, resultUser, message.toString(), true, x, y, z, worldId);
            }
            results.close();

            if (found) {
                if (count > limit) {
                    result.add(Color.WHITE + "-----");
                    result.add(ChatUtils.getPageNavigation(command, page, totalPages));
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
