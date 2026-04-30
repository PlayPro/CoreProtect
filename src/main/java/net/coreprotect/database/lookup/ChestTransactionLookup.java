package net.coreprotect.database.lookup;

import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.coreprotect.config.Config;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;

import net.coreprotect.config.ConfigHandler;
import net.coreprotect.database.statement.UserStatement;
import net.coreprotect.language.Phrase;
import net.coreprotect.language.Selector;
import net.coreprotect.listener.channel.PluginChannelListener;
import net.coreprotect.utility.ChatUtils;
import net.coreprotect.utility.Color;
import net.coreprotect.utility.ItemUtils;
import net.coreprotect.utility.MaterialUtils;
import net.coreprotect.utility.StringUtils;
import net.coreprotect.utility.WorldUtils;

public class ChestTransactionLookup {

    public static List<String> performLookup(String command, Statement statement, Location l, CommandSender commandSender, int page, int limit, boolean exact) {
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
            int x = (int) Math.floor(l.getX());
            int y = (int) Math.floor(l.getY());
            int z = (int) Math.floor(l.getZ());
            int x2 = (int) Math.ceil(l.getX());
            int y2 = (int) Math.ceil(l.getY());
            int z2 = (int) Math.ceil(l.getZ());
            long time = (System.currentTimeMillis() / 1000L);
            int worldId = WorldUtils.getWorldId(l.getWorld().getName());
            int count = 0;
            int rowMax = page * limit;
            int pageStart = rowMax - limit;

            String locationFilter = "(x = '" + x + "' OR x = '" + x2 + "') AND (z = '" + z + "' OR z = '" + z2 + "') AND y = '" + y + "'";
            if (exact) {
                locationFilter = "(x = '" + l.getBlockX() + "') AND (z = '" + l.getBlockZ() + "') AND y = '" + y + "'";
            }

            String query = "SELECT count(*) over () as count,time,user,action,type,data,amount,toString(metadata) as metadata,rolled_back FROM " + ConfigHandler.prefix + "container WHERE wid = '" + worldId + "' AND " + locationFilter + " ORDER BY rowid DESC LIMIT " + limit + " OFFSET " + pageStart + " SETTINGS output_format_json_quote_64bit_integers=0";

            if (Config.getGlobal().SELECT_USE_FINAL) {
                query += " SETTINGS final = 1";
            }

            try (ResultSet results = statement.executeQuery(query)) {
                while (results.next()) {
                    count = results.getInt("count");
                    int resultUserId = results.getInt("user");
                    int resultAction = results.getInt("action");
                    int resultType = results.getInt("type");
                    int resultData = results.getInt("data");
                    long resultTime = results.getLong("time");
                    int resultAmount = results.getInt("amount");
                    int resultRolledBack = results.getInt("rolled_back");
                    String resultMetadata = results.getString("metadata");
                    String hover = ItemUtils.getItemHover(resultMetadata, resultType, resultAmount);

                    if (ConfigHandler.playerIdCacheReversed.get(resultUserId) == null) {
                        UserStatement.loadName(statement.getConnection(), resultUserId);
                    }

                    String resultUser = ConfigHandler.playerIdCacheReversed.get(resultUserId);
                    String timeAgo = ChatUtils.getTimeSince(resultTime, time, true);

                    if (!found) {
                        result.add(Color.WHITE + "----- " + Color.DARK_AQUA + Phrase.build(Phrase.CONTAINER_HEADER) + Color.WHITE + " ----- " + ChatUtils.getCoordinates(command, worldId, x, y, z, false, false));
                    }
                    found = true;

                    String selector = (resultAction != 0 ? Selector.FIRST : Selector.SECOND);
                    String tag = (resultAction != 0 ? Color.GREEN + "+" : Color.RED + "-");
                    String rbFormat = "";
                    if (resultRolledBack == 1 || resultRolledBack == 3) {
                        rbFormat = Color.STRIKETHROUGH;
                    }

                    String target = MaterialUtils.getBlockDisplayName(resultType, resultData);
                    if (target.length() > 0 && !target.contains(":")) {
                        target = "minecraft:" + target.toLowerCase(Locale.ROOT);
                    }

                    // Hide "minecraft:" for now.
                    if (target.startsWith("minecraft:")) {
                        target = target.split(":")[1];
                    }

                    result.add(timeAgo + " " + tag + " " + Phrase.build(Phrase.LOOKUP_CONTAINER, Color.DARK_AQUA + rbFormat + resultUser + Color.WHITE + rbFormat, "x" + resultAmount, ItemUtils.createItemTooltip(Color.DARK_AQUA + rbFormat + target, hover) + Color.WHITE, selector));
                    PluginChannelListener.getInstance().sendData(commandSender, resultTime, Phrase.LOOKUP_CONTAINER, selector, resultUser, target, resultAmount, x, y, z, worldId, rbFormat, true, tag.contains("+"));
                }
            }

            int totalPages = (int) Math.ceil(count / (limit + 0.0));

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
                    result.add(Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.NO_DATA_LOCATION, Selector.SECOND));
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
