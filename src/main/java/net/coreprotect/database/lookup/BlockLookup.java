package net.coreprotect.database.lookup;

import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Locale;

import org.bukkit.Material;
import org.bukkit.block.BlockState;
import org.bukkit.command.CommandSender;

import net.coreprotect.config.ConfigHandler;
import net.coreprotect.database.statement.UserStatement;
import net.coreprotect.language.Phrase;
import net.coreprotect.language.Selector;
import net.coreprotect.listener.channel.PluginChannelListener;
import net.coreprotect.utility.ChatUtils;
import net.coreprotect.utility.Color;
import net.coreprotect.utility.EntityUtils;
import net.coreprotect.utility.MaterialUtils;
import net.coreprotect.utility.StringUtils;
import net.coreprotect.utility.Util;
import net.coreprotect.utility.WorldUtils;

public class BlockLookup {

    public static String performLookup(String command, Statement statement, BlockState block, CommandSender commandSender, int offset, int page, int limit) {
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

            String query = "SELECT COUNT(*) as count from " + ConfigHandler.prefix + "block " + WorldUtils.getWidIndex("block") + "WHERE wid = '" + worldId + "' AND x = '" + x + "' AND z = '" + z + "' AND y = '" + y + "' AND action IN(0,1) AND time >= '" + checkTime + "' LIMIT 0, 1";
            ResultSet results = statement.executeQuery(query);
            while (results.next()) {
                count = results.getInt("count");
            }
            results.close();
            int totalPages = (int) Math.ceil(count / (limit + 0.0));

            query = "SELECT time,user,action,type,data,rolled_back FROM " + ConfigHandler.prefix + "block " + WorldUtils.getWidIndex("block") + "WHERE wid = '" + worldId + "' AND x = '" + x + "' AND z = '" + z + "' AND y = '" + y + "' AND action IN(0,1) AND time >= '" + checkTime + "' ORDER BY rowid DESC LIMIT " + page_start + ", " + limit + "";
            results = statement.executeQuery(query);

            StringBuilder resultTextBuilder = new StringBuilder();

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
                    resultTextBuilder = new StringBuilder(Color.WHITE + "----- " + Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "----- " + ChatUtils.getCoordinates(command, worldId, x, y, z, false, false) + "\n");
                }
                found = true;

                Phrase phrase = Phrase.LOOKUP_BLOCK;
                String selector = Selector.FIRST;
                String tag = Color.WHITE + "-";
                if (resultAction == 2 || resultAction == 3) {
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
                if (resultAction == 3) {
                    target = EntityUtils.getEntityType(resultType).name();
                }
                else {
                    Material resultMaterial = MaterialUtils.getType(resultType);
                    if (resultMaterial == null) {
                        resultMaterial = Material.AIR;
                    }
                    target = StringUtils.nameFilter(resultMaterial.name().toLowerCase(Locale.ROOT), resultData);
                    target = "minecraft:" + target.toLowerCase(Locale.ROOT);
                }
                if (target.length() > 0) {
                    target = "" + target + "";
                }

                // Hide "minecraft:" for now.
                if (target.startsWith("minecraft:")) {
                    target = target.split(":")[1];
                }

                resultTextBuilder.append(timeAgo + " " + tag + " ").append(Phrase.build(phrase, Color.DARK_AQUA + rbFormat + resultUser + Color.WHITE + rbFormat, Color.DARK_AQUA + rbFormat + target + Color.WHITE, selector)).append("\n");
                PluginChannelListener.getInstance().sendData(commandSender, resultTime, phrase, selector, resultUser, target, -1, x, y, z, worldId, rbFormat, false, tag.contains("+"));
            }

            resultText = resultTextBuilder.toString();
            results.close();

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
                    if (!blockName.equals("air") && !blockName.equals("cave_air")) {
                        resultText = Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.NO_DATA, Color.ITALIC + block.getType().name().toLowerCase(Locale.ROOT)) + "\n";
                    }
                }
            }

            ConfigHandler.lookupPage.put(commandSender.getName(), page);
            ConfigHandler.lookupType.put(commandSender.getName(), 2);
            ConfigHandler.lookupCommand.put(commandSender.getName(), x + "." + y + "." + z + "." + worldId + ".0." + limit);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        return resultText;
    }

}
