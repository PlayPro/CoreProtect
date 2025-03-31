package net.coreprotect.command.lookup;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Statement;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

import com.google.common.base.Strings;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.coreprotect.CoreProtect;
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.database.Database;
import net.coreprotect.database.Lookup;
import net.coreprotect.database.logger.ItemLogger;
import net.coreprotect.database.lookup.PlayerLookup;
import net.coreprotect.database.statement.UserStatement;
import net.coreprotect.language.Phrase;
import net.coreprotect.language.Selector;
import net.coreprotect.listener.channel.PluginChannelHandshakeListener;
import net.coreprotect.listener.channel.PluginChannelListener;
import net.coreprotect.utility.Chat;
import net.coreprotect.utility.ChatUtils;
import net.coreprotect.utility.Color;
import net.coreprotect.utility.EntityUtils;
import net.coreprotect.utility.ItemUtils;
import net.coreprotect.utility.MaterialUtils;
import net.coreprotect.utility.StringUtils;
import net.coreprotect.utility.WorldUtils;

public class StandardLookupThread implements Runnable {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int EXPORT_BATCH_SIZE = 500; // Batch size for fetching data during export

    private final CoreProtect plugin;
    private final CommandSender player;
    private final Command command;
    private final List<String> rollbackUsers;
    private final List<Object> blockList;
    private final Map<Object, Boolean> excludedBlocks;
    private final List<String> excludedUsers;
    private final List<Integer> actions;
    private final Integer[] radius;
    private final Location location;
    private final int x;
    private final int y;
    private final int z;
    private final int worldId;
    private final int argWorldId;
    private final long timeStart;
    private final long timeEnd;
    private final int noisy;
    private final int excluded;
    private final int restricted;
    private final int page;
    private final int displayResults;
    private final int typeLookup;
    private final String rtime;
    private final boolean count;
    private final boolean exportMode; // Added for export functionality

    public StandardLookupThread(CoreProtect plugin, CommandSender player, Command command, List<String> rollbackUsers, List<Object> blockList, Map<Object, Boolean> excludedBlocks, List<String> excludedUsers, List<Integer> actions, Integer[] radius, Location location, int x, int y, int z, int worldId, int argWorldId, long timeStart, long timeEnd, int noisy, int excluded, int restricted, int page, int displayResults, int typeLookup, String rtime, boolean count, boolean exportMode) {
        this.plugin = plugin;
        this.player = player;
        this.command = command;
        this.rollbackUsers = rollbackUsers;
        this.blockList = blockList;
        this.excludedBlocks = excludedBlocks;
        this.excludedUsers = excludedUsers;
        this.actions = actions;
        this.radius = radius;
        this.location = location;
        this.x = x;
        this.y = y;
        this.z = z;
        this.worldId = worldId;
        this.argWorldId = argWorldId;
        this.timeStart = timeStart;
        this.timeEnd = timeEnd;
        this.noisy = noisy;
        this.excluded = excluded;
        this.restricted = restricted;
        this.page = page;
        this.displayResults = displayResults;
        this.typeLookup = typeLookup;
        this.rtime = rtime;
        this.count = count;
        this.exportMode = exportMode;
    }

    @Override
    public void run() {
        try (Connection connection = Database.getConnection(true)) {
            ConfigHandler.lookupThrottle.put(player.getName(), new Object[] { true, System.currentTimeMillis() });

            List<String> uuidList = new ArrayList<>();
            Location finalLocation = location;
            boolean exists = false;
            String bc = x + "." + y + "." + z + "." + worldId + "." + timeStart + "." + timeEnd + "." + noisy + "." + excluded + "." + restricted + "." + argWorldId + "." + displayResults;
            ConfigHandler.lookupCommand.put(player.getName(), bc);
            ConfigHandler.lookupPage.put(player.getName(), page);
            ConfigHandler.lookupTime.put(player.getName(), rtime);
            ConfigHandler.lookupType.put(player.getName(), 5);
            ConfigHandler.lookupElist.put(player.getName(), excludedBlocks);
            ConfigHandler.lookupEUserlist.put(player.getName(), excludedUsers);
            ConfigHandler.lookupBlist.put(player.getName(), blockList);
            ConfigHandler.lookupUlist.put(player.getName(), rollbackUsers);
            ConfigHandler.lookupAlist.put(player.getName(), actions);
            ConfigHandler.lookupRadius.put(player.getName(), radius);

            if (connection != null) {
                Statement statement = connection.createStatement();
                String baduser = "";
                for (String check : rollbackUsers) {
                    if ((!check.equals("#global") && !check.equals("#container")) || actions.contains(9)) {
                        exists = PlayerLookup.playerExists(connection, check);
                        if (!exists) {
                            baduser = check;
                            break;
                        }
                        else if (actions.contains(9)) {
                            if (ConfigHandler.uuidCache.get(check.toLowerCase(Locale.ROOT)) != null) {
                                String uuid = ConfigHandler.uuidCache.get(check.toLowerCase(Locale.ROOT));
                                uuidList.add(uuid);
                            }
                        }
                    }
                    else {
                        exists = true;
                    }
                }
                if (exists) {
                    for (String check : excludedUsers) {
                        if (!check.equals("#global") && !check.equals("#hopper")) {
                            exists = PlayerLookup.playerExists(connection, check);
                            if (!exists) {
                                baduser = check;
                                break;
                            }
                        }
                        else if (check.equals("#global")) {
                            baduser = "#global";
                            exists = false;
                        }
                    }
                }

                if (exists) {
                    List<String> userList = new ArrayList<>();
                    if (!actions.contains(9)) {
                        userList = rollbackUsers;
                    }

                    int unixtimestamp = (int) (System.currentTimeMillis() / 1000L);
                    boolean restrict_world = false;
                    if (radius != null) {
                        restrict_world = true;
                    }
                    if (finalLocation == null) {
                        restrict_world = false;
                    }
                    if (argWorldId > 0) {
                        restrict_world = true;
                        finalLocation = new Location(Bukkit.getServer().getWorld(WorldUtils.getWorldName(argWorldId)), x, y, z);
                    }
                    else if (finalLocation != null) {
                        finalLocation = new Location(Bukkit.getServer().getWorld(WorldUtils.getWorldName(worldId)), x, y, z);
                    }

                    Long[] rowData = new Long[] { 0L, 0L, 0L, 0L };
                    long rowMax = (long) page * displayResults;
                    long pageStart = rowMax - displayResults;
                    long rows = 0L;
                    boolean checkRows = true;

                    if (typeLookup == 5 && page > 1) {
                        rowData = ConfigHandler.lookupRows.get(player.getName());
                        rows = rowData[3];

                        if (pageStart < rows) {
                            checkRows = false;
                        }
                    }

                    if (checkRows) {
                        rows = Lookup.countLookupRows(statement, player, uuidList, userList, blockList, excludedBlocks, excludedUsers, actions, finalLocation, radius, rowData, timeStart, timeEnd, restrict_world, true);
                        rowData[3] = rows;
                        ConfigHandler.lookupRows.put(player.getName(), rowData);
                    }

                    if (exportMode) {
                        // --- Export Mode --- //
                        List<Map<String, Object>> allResults = new ArrayList<>();
                        Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Color.ITALIC + Phrase.build(Phrase.EXPORT_GENERATING));

                        long currentPageStart = 0;
                        boolean dataFound = false;
                        try {
                            while (true) {
                                List<String[]> lookupList = Lookup.performPartialLookup(statement, player, uuidList, userList, blockList, excludedBlocks, excludedUsers, actions, finalLocation, radius, rowData, timeStart, timeEnd, (int) currentPageStart, EXPORT_BATCH_SIZE, restrict_world, true);
                                if (lookupList == null || lookupList.isEmpty()) {
                                    break;
                                }
                                dataFound = true;

                                for (String[] data : lookupList) {
                                    Map<String, Object> resultData = mapResultData(data, actions, connection);
                                    if (resultData != null) {
                                        allResults.add(resultData);
                                    }
                                }
                                currentPageStart += EXPORT_BATCH_SIZE;
                            }

                            if (!dataFound) {
                                Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.NO_RESULTS));
                            }
                            else {
                                // Ensure exports directory exists
                                File exportDir = new File(plugin.getDataFolder(), "exports");
                                if (!exportDir.exists()) {
                                    exportDir.mkdirs();
                                }

                                // Generate filename
                                String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
                                String filename = "export_" + timestamp + "_" + player.getName().replaceAll("[^a-zA-Z0-9_]", "") + ".json";
                                File exportFile = new File(exportDir, filename);

                                // Write JSON to file
                                try (FileWriter writer = new FileWriter(exportFile)) {
                                    GSON.toJson(allResults, writer);
                                    Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.EXPORT_SUCCESS, String.valueOf(allResults.size()), "exports/" + filename));
                                }
                                catch (IOException e) {
                                    Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Color.RED + Phrase.build(Phrase.EXPORT_FAILURE));
                                    e.printStackTrace();
                                }
                            }
                        }
                        catch (Exception e) {
                            Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Color.RED + Phrase.build(Phrase.EXPORT_ERROR));
                            e.printStackTrace();
                        }
                    }
                    else {
                        // --- Original Chat Output Mode --- //
                        if (count) {
                            String row_format = NumberFormat.getInstance().format(rows);
                            Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.LOOKUP_ROWS_FOUND, row_format, (rows == 1 ? Selector.FIRST : Selector.SECOND)));
                        }
                        else if (pageStart < rows) {
                            List<String[]> lookupList = Lookup.performPartialLookup(statement, player, uuidList, userList, blockList, excludedBlocks, excludedUsers, actions, finalLocation, radius, rowData, timeStart, timeEnd, (int) pageStart, displayResults, restrict_world, true);

                            Chat.sendMessage(player, Color.WHITE + "----- " + Color.DARK_AQUA + Phrase.build(Phrase.LOOKUP_HEADER, "CoreProtect" + Color.WHITE + " | " + Color.DARK_AQUA) + Color.WHITE + " -----");
                            if (actions.contains(6) || actions.contains(7)) { // Chat/command
                                for (String[] data : lookupList) {
                                    String time = data[0];
                                    String dplayer = data[1];
                                    String message = data[2];
                                    String timeago = ChatUtils.getTimeSince(Integer.parseInt(time), unixtimestamp, true);
                                    Chat.sendComponent(player, timeago + " " + Color.WHITE + "- " + Color.DARK_AQUA + dplayer + ": " + Color.WHITE, message);
                                    if (PluginChannelHandshakeListener.getInstance().isPluginChannelPlayer(player)) {
                                        int wid = Integer.parseInt(data[3]);
                                        int dataX = Integer.parseInt(data[4]);
                                        int dataY = Integer.parseInt(data[5]);
                                        int dataZ = Integer.parseInt(data[6]);
                                        PluginChannelListener.getInstance().sendMessageData(player, Integer.parseInt(time), dplayer, message, false, dataX, dataY, dataZ, wid);
                                    }
                                }
                            }
                            else if (actions.contains(8)) { // login/logouts
                                for (String[] data : lookupList) {
                                    String time = data[0];
                                    String dplayer = data[1];
                                    int wid = Integer.parseInt(data[2]);
                                    int dataX = Integer.parseInt(data[3]);
                                    int dataY = Integer.parseInt(data[4]);
                                    int dataZ = Integer.parseInt(data[5]);
                                    int action = Integer.parseInt(data[6]);
                                    String timeago = ChatUtils.getTimeSince(Integer.parseInt(time), unixtimestamp, true);
                                    int timeLength = 50 + (ChatUtils.getTimeSince(Integer.parseInt(time), unixtimestamp, false).replaceAll("[^0-9]", "").length() * 6);
                                    String leftPadding = Color.BOLD + Strings.padStart("", 10, ' ');
                                    if (timeLength % 4 == 0) {
                                        leftPadding = Strings.padStart("", timeLength / 4, ' ');
                                    }
                                    else {
                                        leftPadding = leftPadding + Color.WHITE + Strings.padStart("", (timeLength - 50) / 4, ' ');
                                    }

                                    String tag = (action != 0 ? Color.GREEN + "+" : Color.RED + "-");
                                    Chat.sendComponent(player, timeago + " " + tag + " " + Color.DARK_AQUA + Phrase.build(Phrase.LOOKUP_LOGIN, Color.DARK_AQUA + dplayer + Color.WHITE, (action != 0 ? Selector.FIRST : Selector.SECOND)));
                                    Chat.sendComponent(player, Color.WHITE + leftPadding + Color.GREY + "^ " + ChatUtils.getCoordinates(command.getName(), wid, dataX, dataY, dataZ, true, true) + "");
                                    PluginChannelListener.getInstance().sendInfoData(player, Integer.parseInt(time), Phrase.LOOKUP_LOGIN, (action != 0 ? Selector.FIRST : Selector.SECOND), dplayer, -1, dataX, dataY, dataZ, wid);
                                }
                            }
                            else if (actions.contains(9)) { // username-changes
                                for (String[] data : lookupList) {
                                    String time = data[0];
                                    String user = ConfigHandler.uuidCacheReversed.get(data[1]);
                                    String username = data[2];
                                    String timeago = ChatUtils.getTimeSince(Integer.parseInt(time), unixtimestamp, true);
                                    Chat.sendComponent(player, timeago + " " + Color.WHITE + "- " + Phrase.build(Phrase.LOOKUP_USERNAME, Color.DARK_AQUA + user + Color.WHITE, Color.DARK_AQUA + username + Color.WHITE));
                                    PluginChannelListener.getInstance().sendUsernameData(player, Integer.parseInt(time), user, username);
                                }
                            }
                            else if (actions.contains(10)) { // sign messages
                                for (String[] data : lookupList) {
                                    String time = data[0];
                                    String dplayer = data[1];
                                    int wid = Integer.parseInt(data[2]);
                                    int dataX = Integer.parseInt(data[3]);
                                    int dataY = Integer.parseInt(data[4]);
                                    int dataZ = Integer.parseInt(data[5]);
                                    String message = data[6];
                                    String timeago = ChatUtils.getTimeSince(Integer.parseInt(time), unixtimestamp, true);
                                    int timeLength = 50 + (ChatUtils.getTimeSince(Integer.parseInt(time), unixtimestamp, false).replaceAll("[^0-9]", "").length() * 6);
                                    String leftPadding = Color.BOLD + Strings.padStart("", 10, ' ');
                                    if (timeLength % 4 == 0) {
                                        leftPadding = Strings.padStart("", timeLength / 4, ' ');
                                    }
                                    else {
                                        leftPadding = leftPadding + Color.WHITE + Strings.padStart("", (timeLength - 50) / 4, ' ');
                                    }

                                    Chat.sendComponent(player, timeago + " " + Color.WHITE + "- " + Color.DARK_AQUA + dplayer + ": " + Color.WHITE, message);
                                    Chat.sendComponent(player, Color.WHITE + leftPadding + Color.GREY + "^ " + ChatUtils.getCoordinates(command.getName(), wid, dataX, dataY, dataZ, true, true) + "");
                                    PluginChannelListener.getInstance().sendMessageData(player, Integer.parseInt(time), dplayer, message, true, dataX, dataY, dataZ, wid);
                                }
                            }
                            else if (actions.contains(4) && actions.contains(11)) { // inventory transactions
                                for (String[] data : lookupList) {
                                    String time = data[0];
                                    String dplayer = data[1];
                                    int dtype = Integer.parseInt(data[5]);
                                    int ddata = Integer.parseInt(data[6]);
                                    int daction = Integer.parseInt(data[7]);
                                    int amount = Integer.parseInt(data[10]);
                                    int wid = Integer.parseInt(data[9]);
                                    int dataX = Integer.parseInt(data[2]);
                                    int dataY = Integer.parseInt(data[3]);
                                    int dataZ = Integer.parseInt(data[4]);
                                    String rbd = ((Integer.parseInt(data[8]) == 2 || Integer.parseInt(data[8]) == 3) ? Color.STRIKETHROUGH : "");
                                    String timeago = ChatUtils.getTimeSince(Integer.parseInt(time), unixtimestamp, true);
                                    Material blockType = ItemUtils.itemFilter(MaterialUtils.getType(dtype), (Integer.parseInt(data[13]) == 0));
                                    String dname = StringUtils.nameFilter(blockType.name().toLowerCase(Locale.ROOT), ddata);
                                    byte[] metadata = data[11] == null ? null : data[11].getBytes(StandardCharsets.ISO_8859_1);
                                    String tooltip = ItemUtils.getEnchantments(metadata, dtype, amount);

                                    String selector = Selector.FIRST;
                                    String tag = Color.WHITE + "-";
                                    if (daction == 2 || daction == 3) { // LOOKUP_ITEM
                                        selector = (daction != 2 ? Selector.FIRST : Selector.SECOND);
                                        tag = (daction != 2 ? Color.GREEN + "+" : Color.RED + "-");
                                    }
                                    else if (daction == 4 || daction == 5) { // LOOKUP_STORAGE
                                        selector = (daction == 4 ? Selector.FIRST : Selector.SECOND);
                                        tag = (daction == 4 ? Color.GREEN + "+" : Color.RED + "-");
                                    }
                                    else if (daction == 6 || daction == 7) { // LOOKUP_PROJECTILE
                                        selector = Selector.SECOND;
                                        tag = Color.RED + "-";
                                    }
                                    else if (daction == ItemLogger.ITEM_BREAK || daction == ItemLogger.ITEM_DESTROY || daction == ItemLogger.ITEM_CREATE) {
                                        selector = (daction == ItemLogger.ITEM_CREATE ? Selector.FIRST : Selector.SECOND);
                                        tag = (daction == ItemLogger.ITEM_CREATE ? Color.GREEN + "+" : Color.RED + "-");
                                    }
                                    else if (daction == ItemLogger.ITEM_SELL || daction == ItemLogger.ITEM_BUY) { // LOOKUP_TRADE
                                        selector = (daction == ItemLogger.ITEM_BUY ? Selector.FIRST : Selector.SECOND);
                                        tag = (daction == ItemLogger.ITEM_BUY ? Color.GREEN + "+" : Color.RED + "-");
                                    }
                                    else { // LOOKUP_CONTAINER
                                        selector = (daction == 0 ? Selector.FIRST : Selector.SECOND);
                                        tag = (daction == 0 ? Color.GREEN + "+" : Color.RED + "-");
                                    }

                                    Chat.sendComponent(player, timeago + " " + tag + " " + Phrase.build(Phrase.LOOKUP_CONTAINER, Color.DARK_AQUA + rbd + dplayer + Color.WHITE + rbd, "x" + amount, ChatUtils.createTooltip(Color.DARK_AQUA + rbd + dname, tooltip) + Color.WHITE, selector));
                                    PluginChannelListener.getInstance().sendData(player, Integer.parseInt(time), Phrase.LOOKUP_CONTAINER, selector, dplayer, dname, amount, dataX, dataY, dataZ, wid, rbd, true, tag.contains("+"));
                                }
                            }
                            else {
                                for (String[] data : lookupList) {
                                    int drb = Integer.parseInt(data[8]);
                                    String rbd = "";
                                    if (drb == 1 || drb == 3) {
                                        rbd = Color.STRIKETHROUGH;
                                    }

                                    String time = data[0];
                                    String dplayer = data[1];
                                    int dataX = Integer.parseInt(data[2]);
                                    int dataY = Integer.parseInt(data[3]);
                                    int dataZ = Integer.parseInt(data[4]);
                                    int dtype = Integer.parseInt(data[5]);
                                    int ddata = Integer.parseInt(data[6]);
                                    int daction = Integer.parseInt(data[7]);
                                    int wid = Integer.parseInt(data[9]);
                                    int amount = Integer.parseInt(data[10]);
                                    String tag = Color.WHITE + "-";

                                    String timeago = ChatUtils.getTimeSince(Integer.parseInt(time), unixtimestamp, true);
                                    int timeLength = 50 + (ChatUtils.getTimeSince(Integer.parseInt(time), unixtimestamp, false).replaceAll("[^0-9]", "").length() * 6);
                                    String leftPadding = Color.BOLD + Strings.padStart("", 10, ' ');
                                    if (timeLength % 4 == 0) {
                                        leftPadding = Strings.padStart("", timeLength / 4, ' ');
                                    }
                                    else {
                                        leftPadding = leftPadding + Color.WHITE + Strings.padStart("", (timeLength - 50) / 4, ' ');
                                    }

                                    String dname = "";
                                    boolean isPlayer = false;
                                    if (daction == 3 && !actions.contains(11) && amount == -1) {
                                        if (dtype == 0) {
                                            if (ConfigHandler.playerIdCacheReversed.get(ddata) == null) {
                                                UserStatement.loadName(connection, ddata);
                                            }
                                            dname = ConfigHandler.playerIdCacheReversed.get(ddata);
                                            isPlayer = true;
                                        }
                                        else {
                                            dname = EntityUtils.getEntityType(dtype).name();
                                        }
                                    }
                                    else {
                                        dname = MaterialUtils.getType(dtype).name().toLowerCase(Locale.ROOT);
                                        dname = StringUtils.nameFilter(dname, ddata);
                                    }
                                    if (dname.length() > 0 && !isPlayer) {
                                        dname = "minecraft:" + dname.toLowerCase(Locale.ROOT) + "";
                                    }

                                    // Hide "minecraft:" for now.
                                    if (dname.contains("minecraft:")) {
                                        String[] blockNameSplit = dname.split(":");
                                        dname = blockNameSplit[1];
                                    }

                                    // Functions.sendMessage(player2, timeago+" " + ChatColors.WHITE + "- " + ChatColors.DARK_AQUA+rbd+""+dplayer+" " + ChatColors.WHITE+rbd+""+a+" " + ChatColors.DARK_AQUA+rbd+"#"+dtype+ChatColors.WHITE + ". " + ChatColors.GREY + "(x"+x+"/y"+y+"/z"+z+")");

                                    Phrase phrase = Phrase.LOOKUP_BLOCK;
                                    String selector = Selector.FIRST;
                                    String action = "a:block";
                                    if (actions.contains(4) || actions.contains(5) || actions.contains(11) || amount > -1) {
                                        byte[] metadata = data[11] == null ? null : data[11].getBytes(StandardCharsets.ISO_8859_1);
                                        String tooltip = ItemUtils.getEnchantments(metadata, dtype, amount);

                                        if (daction == 2 || daction == 3) {
                                            phrase = Phrase.LOOKUP_ITEM; // {picked up|dropped}
                                            selector = (daction != 2 ? Selector.FIRST : Selector.SECOND);
                                            tag = (daction != 2 ? Color.GREEN + "+" : Color.RED + "-");
                                            action = "a:item";
                                        }
                                        else if (daction == 4 || daction == 5) {
                                            phrase = Phrase.LOOKUP_STORAGE; // {deposited|withdrew}
                                            selector = (daction != 4 ? Selector.FIRST : Selector.SECOND);
                                            tag = (daction != 4 ? Color.RED + "-" : Color.GREEN + "+");
                                            action = "a:item";
                                        }
                                        else if (daction == 6 || daction == 7) {
                                            phrase = Phrase.LOOKUP_PROJECTILE; // {threw|shot}
                                            selector = (daction != 7 ? Selector.FIRST : Selector.SECOND);
                                            tag = Color.RED + "-";
                                            action = "a:item";
                                        }
                                        else {
                                            phrase = Phrase.LOOKUP_CONTAINER; // {added|removed}
                                            selector = (daction != 0 ? Selector.FIRST : Selector.SECOND);
                                            tag = (daction != 0 ? Color.GREEN + "+" : Color.RED + "-");
                                            action = "a:container";
                                        }

                                        Chat.sendComponent(player, timeago + " " + tag + " " + Phrase.build(phrase, Color.DARK_AQUA + rbd + dplayer + Color.WHITE + rbd, "x" + amount, ChatUtils.createTooltip(Color.DARK_AQUA + rbd + dname, tooltip) + Color.WHITE, selector));
                                        PluginChannelListener.getInstance().sendData(player, Integer.parseInt(time), phrase, selector, dplayer, dname, (tag.contains("+") ? 1 : -1), dataX, dataY, dataZ, wid, rbd, action.contains("container"), tag.contains("+"));
                                    }
                                    else {
                                        if (daction == 2 || daction == 3) {
                                            phrase = Phrase.LOOKUP_INTERACTION; // {clicked|killed}
                                            selector = (daction != 3 ? Selector.FIRST : Selector.SECOND);
                                            tag = (daction != 3 ? Color.WHITE + "-" : Color.RED + "-");
                                            action = (daction == 2 ? "a:click" : "a:kill");
                                        }
                                        else {
                                            phrase = Phrase.LOOKUP_BLOCK; // {placed|broke}
                                            selector = (daction != 0 ? Selector.FIRST : Selector.SECOND);
                                            tag = (daction != 0 ? Color.GREEN + "+" : Color.RED + "-");
                                        }

                                        Chat.sendComponent(player, timeago + " " + tag + " " + Phrase.build(phrase, Color.DARK_AQUA + rbd + dplayer + Color.WHITE + rbd, Color.DARK_AQUA + rbd + dname + Color.WHITE, selector));
                                        PluginChannelListener.getInstance().sendData(player, Integer.parseInt(time), phrase, selector, dplayer, dname, (tag.contains("+") ? 1 : -1), dataX, dataY, dataZ, wid, rbd, false, tag.contains("+"));
                                    }

                                    action = (actions.size() == 0 ? " (" + action + ")" : "");
                                    Chat.sendComponent(player, Color.WHITE + leftPadding + Color.GREY + "^ " + ChatUtils.getCoordinates(command.getName(), wid, dataX, dataY, dataZ, true, true) + Color.GREY + Color.ITALIC + action);
                                }
                            }
                            if (rows > displayResults) {
                                int total_pages = (int) Math.ceil(rows / (displayResults + 0.0));
                                if (actions.contains(6) || actions.contains(7) || actions.contains(9) || (actions.contains(4) && actions.contains(11))) {
                                    Chat.sendMessage(player, "-----");
                                }
                                Chat.sendComponent(player, ChatUtils.getPageNavigation(command.getName(), page, total_pages));
                            }
                        }
                        else if (rows > 0) {
                            Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.NO_RESULTS_PAGE, Selector.FIRST));
                        }
                        else {
                            Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.NO_RESULTS));
                        }
                    }
                }
                else {
                    Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.USER_NOT_FOUND, baduser));
                }
                statement.close();
            }
            else {
                Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.DATABASE_BUSY));
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        ConfigHandler.lookupThrottle.put(player.getName(), new Object[] { false, System.currentTimeMillis() });
    }

    // Helper method to map String[] data to Map<String, Object>
    private Map<String, Object> mapResultData(String[] data, List<Integer> actions, Connection connection) {
        Map<String, Object> result = new HashMap<>();
        try {
            long time = Long.parseLong(data[0]);
            result.put("timestamp", time);
            result.put("player", data[1]);

            if (actions.contains(6) || actions.contains(7)) { // Chat/Command
                result.put("type", actions.contains(6) ? "chat" : "command");
                result.put("message", data[2]);
                result.put("world", WorldUtils.getWorldName(Integer.parseInt(data[3])));
                result.put("x", Integer.parseInt(data[4]));
                result.put("y", Integer.parseInt(data[5]));
                result.put("z", Integer.parseInt(data[6]));
            }
            else if (actions.contains(8)) { // Session (Login/Logout)
                result.put("type", "session");
                result.put("action", Integer.parseInt(data[6]) != 0 ? "login" : "logout");
                result.put("world", WorldUtils.getWorldName(Integer.parseInt(data[2])));
                result.put("x", Integer.parseInt(data[3]));
                result.put("y", Integer.parseInt(data[4]));
                result.put("z", Integer.parseInt(data[5]));
            }
            else if (actions.contains(9)) { // Username change
                result.put("type", "username_change");
                String uuid = data[1];
                String oldUser = ConfigHandler.uuidCacheReversed.get(uuid); // May need loading if not cached
                result.put("uuid", uuid);
                result.put("old_username", oldUser != null ? oldUser : "(unknown)");
                result.put("new_username", data[2]);
            }
            else if (actions.contains(10)) { // Sign text
                result.put("type", "sign");
                result.put("world", WorldUtils.getWorldName(Integer.parseInt(data[2])));
                result.put("x", Integer.parseInt(data[3]));
                result.put("y", Integer.parseInt(data[4]));
                result.put("z", Integer.parseInt(data[5]));
                result.put("text", data[6]);
            }
            else if (actions.contains(4) && actions.contains(11)) { // Inventory transactions
                result.put("type", "inventory");
                result.put("x", Integer.parseInt(data[2]));
                result.put("y", Integer.parseInt(data[3]));
                result.put("z", Integer.parseInt(data[4]));
                int matId = Integer.parseInt(data[5]);
                int matData = Integer.parseInt(data[6]);
                int actionCode = Integer.parseInt(data[7]);
                int amount = Integer.parseInt(data[10]);
                result.put("world", WorldUtils.getWorldName(Integer.parseInt(data[9])));
                result.put("rolled_back", (Integer.parseInt(data[8]) == 2 || Integer.parseInt(data[8]) == 3));
                Material material = ItemUtils.itemFilter(MaterialUtils.getType(matId), (Integer.parseInt(data[13]) == 0));
                result.put("material", material != null ? material.name() : "ID:" + matId);
                result.put("material_data", matData);
                result.put("amount", amount);
                result.put("action", mapInventoryAction(actionCode));
                // Metadata (byte[]) is skipped for JSON simplicity unless needed
            }
            else { // Block/Interaction/Item (non-inventory)
                result.put("x", Integer.parseInt(data[2]));
                result.put("y", Integer.parseInt(data[3]));
                result.put("z", Integer.parseInt(data[4]));
                int matId = Integer.parseInt(data[5]);
                int matData = Integer.parseInt(data[6]);
                int actionCode = Integer.parseInt(data[7]);
                int amount = Integer.parseInt(data[10]); // Amount is relevant for items
                result.put("world", WorldUtils.getWorldName(Integer.parseInt(data[9])));
                result.put("rolled_back", (Integer.parseInt(data[8]) == 1 || Integer.parseInt(data[8]) == 3));

                if (actionCode == 3 && !actions.contains(11) && amount == -1) { // Kill action
                    result.put("type", "kill");
                    if (matId == 0) { // Player kill
                        if (ConfigHandler.playerIdCacheReversed.get(matData) == null) {
                            UserStatement.loadName(connection, matData); // Ensure name is loaded
                        }
                        result.put("killed_player", ConfigHandler.playerIdCacheReversed.get(matData));
                    } else {
                        result.put("killed_entity", EntityUtils.getEntityType(matId).name());
                    }
                } else if (actionCode == 2 && !actions.contains(11) && amount == -1) { // Click action
                    result.put("type", "interaction");
                    result.put("clicked_material", StringUtils.nameFilter(MaterialUtils.getType(matId).name().toLowerCase(Locale.ROOT), matData));
                } else if (actions.contains(4) || actions.contains(5) || actions.contains(11) || amount > -1) { // Container/Item actions (non-inventory)
                    result.put("type", "item"); // Simplified type for export
                    result.put("action", mapItemAction(actionCode));
                    result.put("material", StringUtils.nameFilter(MaterialUtils.getType(matId).name().toLowerCase(Locale.ROOT), matData));
                    result.put("amount", amount);
                    // Metadata skipped
                } else { // Block actions
                    result.put("type", "block");
                    result.put("action", actionCode == 1 ? "place" : "break");
                    result.put("material", StringUtils.nameFilter(MaterialUtils.getType(matId).name().toLowerCase(Locale.ROOT), matData));
                }
            }
            return result;
        }
        catch (Exception e) {
            System.err.println("Error mapping result data: " + String.join(", ", data));
            e.printStackTrace();
            return null; // Skip this entry if mapping fails
        }
    }

    // Helper for inventory action codes
    private String mapInventoryAction(int actionCode) {
        switch (actionCode) {
            case 0: return "added_to_container";
            case 1: return "removed_from_container";
            case 2: return "added_item"; // e.g., picked up
            case 3: return "removed_item"; // e.g., dropped
            case 4: return "deposited_storage";
            case 5: return "withdrew_storage";
            case 6: return "threw_projectile";
            case 7: return "shot_projectile";
            case ItemLogger.ITEM_BREAK: return "item_break";
            case ItemLogger.ITEM_DESTROY: return "item_destroy";
            case ItemLogger.ITEM_CREATE: return "item_create";
            case ItemLogger.ITEM_SELL: return "item_sell";
            case ItemLogger.ITEM_BUY: return "item_buy";
            default: return "unknown_" + actionCode;
        }
    }

     // Helper for non-inventory item action codes
    private String mapItemAction(int actionCode) {
         switch (actionCode) {
            case 0: return "added_to_container"; // From block context
            case 1: return "removed_from_container"; // From block context
            case 2: return "picked_up";
            case 3: return "dropped";
            case 4: return "deposited_storage";
            case 5: return "withdrew_storage";
            case 6: return "threw_projectile";
            case 7: return "shot_projectile";
            default: return "unknown_" + actionCode;
        }
    }
}
