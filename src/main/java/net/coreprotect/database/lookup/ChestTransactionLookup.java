package net.coreprotect.database.lookup;

import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.command.CommandSender;

import net.coreprotect.config.ConfigHandler;
import net.coreprotect.database.statement.EntitySpawnStatement;
import net.coreprotect.database.statement.UserStatement;
import net.coreprotect.language.Phrase;
import net.coreprotect.language.Selector;
import net.coreprotect.listener.channel.PluginChannelListener;
import net.coreprotect.model.entity.EntitySpawnRecord;
import net.coreprotect.utility.ChatUtils;
import net.coreprotect.utility.Color;
import net.coreprotect.utility.ItemUtils;
import net.coreprotect.utility.MaterialUtils;
import net.coreprotect.utility.WorldUtils;
import net.coreprotect.utility.ErrorReporter;
import net.coreprotect.utility.EntitySpawnTracking;

public class ChestTransactionLookup {

    public static List<String> performLookup(String command, Statement statement, Location l, CommandSender commandSender, int page, int limit, boolean exact) {
        return performLookup(command, statement, l, commandSender, page, limit, exact, null);
    }

    public static List<String> performLookup(String command, Statement statement, Location l, CommandSender commandSender, int page, int limit, boolean exact, Integer entitySpawnRowId) {
        List<String> result = new ArrayList<>();
        if (entitySpawnRowId == null) {
            ConfigHandler.lookupEntityContainer.remove(commandSender.getName());
        }

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
            int displayWorldId = worldId;
            int displayX = l.getBlockX();
            int displayY = l.getBlockY();
            int displayZ = l.getBlockZ();
            int count = 0;
            int rowMax = page * limit;
            int pageStart = rowMax - limit;

            String table = ConfigHandler.prefix + "container";
            String where = "wid = '" + worldId + "' AND (x = '" + x + "' OR x = '" + x2 + "') AND (z = '" + z + "' OR z = '" + z2 + "') AND y = '" + y + "'";
            String index = WorldUtils.getWidIndex("container");
            String order = "rowid DESC";
            if (entitySpawnRowId != null) {
                table = ConfigHandler.prefix + "entity_container";
                where = "entity_spawn_rowid = '" + entitySpawnRowId + "'";
                index = "";
                order = "time DESC,rowid DESC";

                EntitySpawnRecord record = EntitySpawnStatement.loadLocationRecords(statement.getConnection(), Collections.singleton(entitySpawnRowId)).get(entitySpawnRowId);
                if (record != null) {
                    displayWorldId = record.getWorldId();
                    displayX = (int) Math.floor(record.getX());
                    displayY = (int) Math.floor(record.getY());
                    displayZ = (int) Math.floor(record.getZ());
                    Map<UUID, Location> loadedLocations = EntitySpawnTracking.findLoadedLocations(Collections.singleton(record.getUuid()));
                    Location loadedLocation = loadedLocations.get(record.getUuid());
                    if (loadedLocation != null && loadedLocation.getWorld() != null) {
                        displayWorldId = WorldUtils.getWorldId(loadedLocation.getWorld().getName());
                        displayX = loadedLocation.getBlockX();
                        displayY = loadedLocation.getBlockY();
                        displayZ = loadedLocation.getBlockZ();
                    }
                }
            }
            else if (exact) {
                where = "wid = '" + worldId + "' AND x = '" + l.getBlockX() + "' AND z = '" + l.getBlockZ() + "' AND y = '" + y + "'";
            }

            String query = "SELECT COUNT(*) as count FROM " + table + " " + index + "WHERE " + where + " LIMIT 0, 1";
            ResultSet results = statement.executeQuery(query);

            while (results.next()) {
                count = results.getInt("count");
            }
            results.close();

            int totalPages = (int) Math.ceil(count / (limit + 0.0));

            query = "SELECT time,user,wid,x,y,z,action,type,data,amount,metadata,rolled_back FROM " + table + " " + index + "WHERE " + where + " ORDER BY " + order + " LIMIT " + pageStart + ", " + limit;
            results = statement.executeQuery(query);
            while (results.next()) {
                int resultUserId = results.getInt("user");
                int resultAction = results.getInt("action");
                int resultType = results.getInt("type");
                int resultData = results.getInt("data");
                long resultTime = results.getLong("time");
                int resultAmount = results.getInt("amount");
                int resultRolledBack = results.getInt("rolled_back");
                int resultWorldId = results.getInt("wid");
                int resultX = results.getInt("x");
                int resultY = results.getInt("y");
                int resultZ = results.getInt("z");
                byte[] resultMetadata = results.getBytes("metadata");
                String tooltip = ItemUtils.getEnchantments(resultMetadata, resultType, resultAmount);

                if (ConfigHandler.playerIdCacheReversed.get(resultUserId) == null) {
                    UserStatement.loadName(statement.getConnection(), resultUserId);
                }

                String resultUser = ConfigHandler.playerIdCacheReversed.get(resultUserId);
                String timeAgo = ChatUtils.getTimeSince(resultTime, time, true);

                if (!found) {
                    result.add(Color.WHITE + "----- " + Color.DARK_AQUA + Phrase.build(Phrase.CONTAINER_HEADER) + Color.WHITE + " ----- " + ChatUtils.getCoordinates(command, displayWorldId, displayX, displayY, displayZ, false, false));
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
                    target = "minecraft:" + target.toLowerCase(Locale.ROOT) + "";
                }

                // Hide "minecraft:" for now.
                if (target.startsWith("minecraft:")) {
                    target = target.split(":")[1];
                }

                String coordinateInfo = "";
                if (entitySpawnRowId != null && (displayWorldId != resultWorldId || displayX != resultX || displayY != resultY || displayZ != resultZ)) {
                    coordinateInfo = ChatUtils.getCoordinateTooltip(resultWorldId, resultX, resultY, resultZ, Phrase.build(Phrase.LOOKUP_ENTITY_ORIGIN), true);
                }
                result.add(timeAgo + " " + tag + " " + Phrase.build(Phrase.LOOKUP_CONTAINER, Color.DARK_AQUA + rbFormat + resultUser + Color.WHITE + rbFormat, "x" + resultAmount, ChatUtils.createTooltip(Color.DARK_AQUA + rbFormat + target, tooltip) + coordinateInfo + Color.WHITE, selector));
                PluginChannelListener.getInstance().sendData(commandSender, resultTime, Phrase.LOOKUP_CONTAINER, selector, resultUser, target, resultAmount, displayX, displayY, displayZ, displayWorldId, rbFormat, true, tag.contains("+"));
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
                    result.add(Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.NO_DATA_LOCATION, Selector.SECOND));
                }
            }

            ConfigHandler.lookupType.put(commandSender.getName(), 1);
            ConfigHandler.lookupPage.put(commandSender.getName(), page);
            String lookupCommand = x + "." + y + "." + z + "." + worldId + "." + x2 + "." + y2 + "." + z2 + "." + limit;
            if (entitySpawnRowId != null) {
                lookupCommand = displayX + "." + displayY + "." + displayZ + "." + displayWorldId + "." + displayX + "." + displayY + "." + displayZ + "." + limit;
            }
            ConfigHandler.lookupCommand.put(commandSender.getName(), lookupCommand);
            if (entitySpawnRowId != null) {
                ConfigHandler.lookupEntityContainer.put(commandSender.getName(), entitySpawnRowId);
            }
        }
        catch (Exception e) {
            ErrorReporter.report(e);
        }

        return result;
    }

}
