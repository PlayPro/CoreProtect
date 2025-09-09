package net.coreprotect.database.lookup;

import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Locale;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;

import net.coreprotect.config.ConfigHandler;
import net.coreprotect.database.statement.UserStatement;
import net.coreprotect.language.Phrase;
import net.coreprotect.language.Selector;
import net.coreprotect.listener.channel.PluginChannelListener;
import net.coreprotect.utility.ChatUtils;
import net.coreprotect.utility.Color;
import net.coreprotect.utility.MaterialUtils;
import net.coreprotect.utility.StringUtils;
import net.coreprotect.utility.WorldUtils;

public class InteractionLookup {

    public static String performLookup(String command, Statement statement, Block block, CommandSender commandSender, int offset, int page, int limit) {
        String result = "";

        try {
            if (block == null) {
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
            int x = block.getX();
            int y = block.getY();
            int z = block.getZ();
            int time = (int) (System.currentTimeMillis() / 1000L);
            int worldId = WorldUtils.getWorldId(block.getWorld().getName());
            int checkTime = 0;
            int count = 0;
            int rowMax = page * limit;
            int pageStart = rowMax - limit;
            if (offset > 0) {
                checkTime = time - offset;
            }

            String query = "SELECT COUNT(*) as count from " + ConfigHandler.prefix + "block " + WorldUtils.getWidIndex("block") + "WHERE wid = '" + worldId + "' AND x = '" + x + "' AND z = '" + z + "' AND y = '" + y + "' AND action='2' AND time >= '" + checkTime + "' LIMIT 0, 1";
            ResultSet results = statement.executeQuery(query);

            while (results.next()) {
                count = results.getInt("count");
            }
            results.close();
            int totalPages = (int) Math.ceil(count / (limit + 0.0));

            query = "SELECT time,user,action,type,data,rolled_back FROM " + ConfigHandler.prefix + "block " + WorldUtils.getWidIndex("block") + "WHERE wid = '" + worldId + "' AND x = '" + x + "' AND z = '" + z + "' AND y = '" + y + "' AND action='2' AND time >= '" + checkTime + "' ORDER BY rowid DESC LIMIT " + pageStart + ", " + limit + "";
            results = statement.executeQuery(query);

            StringBuilder resultBuilder = new StringBuilder();
            while (results.next()) {
                int resultUserId = results.getInt("user");
                int resultAction = results.getInt("action");
                int resultType = results.getInt("type");
                int resultData = results.getInt("data");
                long resultTime = results.getLong("time");
                int resultRolledBack = results.getInt("rolled_back");

                if (ConfigHandler.playerIdCacheReversed.get(resultUserId) == null) {
                    UserStatement.loadName(statement.getConnection(), resultUserId);
                }

                String resultUser = ConfigHandler.playerIdCacheReversed.get(resultUserId);
                String timeAgo = ChatUtils.getTimeSince(resultTime, time, true);

                if (!found) {
                    resultBuilder = new StringBuilder(Color.WHITE + "----- " + Color.DARK_AQUA + Phrase.build(Phrase.INTERACTIONS_HEADER) + Color.WHITE + " ----- " + ChatUtils.getCoordinates(command, worldId, x, y, z, false, false) + "\n");
                }
                found = true;

                String rbFormat = "";
                if (resultRolledBack == 1 || resultRolledBack == 3) {
                    rbFormat = Color.STRIKETHROUGH;
                }

                Material resultMaterial = MaterialUtils.getType(resultType);
                if (resultMaterial == null) {
                    resultMaterial = Material.AIR;
                }
                String target = resultMaterial.name().toLowerCase(Locale.ROOT);
                target = StringUtils.nameFilter(target, resultData);
                if (target.length() > 0) {
                    target = "minecraft:" + target.toLowerCase(Locale.ROOT) + "";
                }

                // Hide "minecraft:" for now.
                if (target.startsWith("minecraft:")) {
                    target = target.split(":")[1];
                }

                resultBuilder.append(timeAgo + " " + Color.WHITE + "- ").append(Phrase.build(Phrase.LOOKUP_INTERACTION, Color.DARK_AQUA + rbFormat + resultUser + Color.WHITE + rbFormat, Color.DARK_AQUA + rbFormat + target + Color.WHITE, Selector.FIRST)).append("\n");
                PluginChannelListener.getInstance().sendData(commandSender, resultTime, Phrase.LOOKUP_INTERACTION, Selector.FIRST, resultUser, target, -1, x, y, z, worldId, rbFormat, false, false);
            }
            result = resultBuilder.toString();
            results.close();

            if (found) {
                if (count > limit) {
                    String pageInfo = Color.WHITE + "-----\n";
                    pageInfo = pageInfo + ChatUtils.getPageNavigation(command, page, totalPages) + "\n";
                    result = result + pageInfo;
                }
            }
            else {
                if (rowMax > count && count > 0) {
                    result = Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.NO_RESULTS_PAGE, Selector.SECOND);
                }
                else {
                    result = Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.NO_DATA_LOCATION, Selector.THIRD);
                }
            }

            ConfigHandler.lookupPage.put(commandSender.getName(), page);
            ConfigHandler.lookupType.put(commandSender.getName(), 7);
            ConfigHandler.lookupCommand.put(commandSender.getName(), x + "." + y + "." + z + "." + worldId + ".2." + limit);
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        return result;
    }

}
