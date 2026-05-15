package net.coreprotect.command.lookup;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Statement;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

import com.google.common.base.Strings;

import net.coreprotect.config.ConfigHandler;
import net.coreprotect.database.Database;
import net.coreprotect.database.Lookup;
import net.coreprotect.database.lookup.PlayerLookup;
import net.coreprotect.database.statement.UserStatement;
import net.coreprotect.language.Phrase;
import net.coreprotect.language.Selector;
import net.coreprotect.listener.channel.PluginChannelHandshakeListener;
import net.coreprotect.listener.channel.PluginChannelListener;
import net.coreprotect.model.action.LookupActions;
import net.coreprotect.model.action.SessionActions;
import net.coreprotect.model.item.ItemTransactionActions;
import net.coreprotect.utility.Chat;
import net.coreprotect.utility.ChatUtils;
import net.coreprotect.utility.Color;
import net.coreprotect.utility.EntityUtils;
import net.coreprotect.utility.ItemUtils;
import net.coreprotect.utility.MaterialUtils;
import net.coreprotect.utility.StringUtils;
import net.coreprotect.utility.WorldUtils;

public class StandardLookupThread implements Runnable {
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
    private final String messageFilter;

    public StandardLookupThread(CommandSender player, Command command, List<String> rollbackUsers, List<Object> blockList, Map<Object, Boolean> excludedBlocks, List<String> excludedUsers, List<Integer> actions, Integer[] radius, Location location, int x, int y, int z, int worldId, int argWorldId, long timeStart, long timeEnd, int noisy, int excluded, int restricted, int page, int displayResults, int typeLookup, String rtime, boolean count) {
        this(player, command, rollbackUsers, blockList, excludedBlocks, excludedUsers, actions, radius, location, x, y, z, worldId, argWorldId, timeStart, timeEnd, noisy, excluded, restricted, page, displayResults, typeLookup, rtime, count, "");
    }

    public StandardLookupThread(CommandSender player, Command command, List<String> rollbackUsers, List<Object> blockList, Map<Object, Boolean> excludedBlocks, List<String> excludedUsers, List<Integer> actions, Integer[] radius, Location location, int x, int y, int z, int worldId, int argWorldId, long timeStart, long timeEnd, int noisy, int excluded, int restricted, int page, int displayResults, int typeLookup, String rtime, boolean count, String messageFilter) {
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
        this.messageFilter = messageFilter;
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
            ConfigHandler.lookupMessageFilter.put(player.getName(), messageFilter);
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
                    if ((!check.equals("#global") && !check.equals("#container")) || actions.contains(LookupActions.USERNAME)) {
                        exists = PlayerLookup.playerExists(connection, check);
                        if (!exists) {
                            baduser = check;
                            break;
                        }
                        else if (actions.contains(LookupActions.USERNAME)) {
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
                    if (!actions.contains(LookupActions.USERNAME)) {
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
                        rows = Lookup.countLookupRows(statement, player, uuidList, userList, blockList, excludedBlocks, excludedUsers, actions, finalLocation, radius, rowData, timeStart, timeEnd, restrict_world, true, messageFilter);
                        rowData[3] = rows;
                        ConfigHandler.lookupRows.put(player.getName(), rowData);
                    }
                    if (count) {
                        String row_format = NumberFormat.getInstance().format(rows);
                        Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.LOOKUP_ROWS_FOUND, row_format, (rows == 1 ? Selector.FIRST : Selector.SECOND)));
                    }
                    else if (pageStart < rows) {
                        List<String[]> lookupList = Lookup.performPartialLookup(statement, player, uuidList, userList, blockList, excludedBlocks, excludedUsers, actions, finalLocation, radius, rowData, timeStart, timeEnd, (int) pageStart, displayResults, restrict_world, true, messageFilter);

                        Chat.sendMessage(player, Color.WHITE + "----- " + Color.DARK_AQUA + Phrase.build(Phrase.LOOKUP_HEADER, "CoreProtect" + Color.WHITE + " | " + Color.DARK_AQUA) + Color.WHITE + " -----");
                        if (actions.contains(LookupActions.CHAT) || actions.contains(LookupActions.COMMAND)) {
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
                        else if (actions.contains(LookupActions.SESSION)) {
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

                                String tag = (action != SessionActions.LOGOUT ? Color.GREEN + "+" : Color.RED + "-");
                                Chat.sendComponent(player, timeago + " " + tag + " " + Color.DARK_AQUA + Phrase.build(Phrase.LOOKUP_LOGIN, Color.DARK_AQUA + dplayer + Color.WHITE, (action != SessionActions.LOGOUT ? Selector.FIRST : Selector.SECOND)));
                                Chat.sendComponent(player, Color.WHITE + leftPadding + Color.GREY + "^ " + ChatUtils.getCoordinates(command.getName(), wid, dataX, dataY, dataZ, true, true) + "");
                                PluginChannelListener.getInstance().sendInfoData(player, Integer.parseInt(time), Phrase.LOOKUP_LOGIN, (action != SessionActions.LOGOUT ? Selector.FIRST : Selector.SECOND), dplayer, -1, dataX, dataY, dataZ, wid);
                            }
                        }
                        else if (actions.contains(LookupActions.USERNAME)) {
                            for (String[] data : lookupList) {
                                String time = data[0];
                                String user = ConfigHandler.uuidCacheReversed.get(data[1]);
                                String username = data[2];
                                String timeago = ChatUtils.getTimeSince(Integer.parseInt(time), unixtimestamp, true);
                                Chat.sendComponent(player, timeago + " " + Color.WHITE + "- " + Phrase.build(Phrase.LOOKUP_USERNAME, Color.DARK_AQUA + user + Color.WHITE, Color.DARK_AQUA + username + Color.WHITE));
                                PluginChannelListener.getInstance().sendUsernameData(player, Integer.parseInt(time), user, username);
                            }
                        }
                        else if (actions.contains(LookupActions.SIGN)) {
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
                        else if (LookupActions.isInventoryLookup(actions)) {
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
                                if (daction == ItemTransactionActions.DROP || daction == ItemTransactionActions.PICKUP) {
                                    selector = (daction != ItemTransactionActions.DROP ? Selector.FIRST : Selector.SECOND);
                                    tag = (daction != ItemTransactionActions.DROP ? Color.GREEN + "+" : Color.RED + "-");
                                }
                                else if (daction == ItemTransactionActions.REMOVE_ENDER || daction == ItemTransactionActions.ADD_ENDER) {
                                    selector = (daction == ItemTransactionActions.REMOVE_ENDER ? Selector.FIRST : Selector.SECOND);
                                    tag = (daction == ItemTransactionActions.REMOVE_ENDER ? Color.GREEN + "+" : Color.RED + "-");
                                }
                                else if (daction == ItemTransactionActions.THROW || daction == ItemTransactionActions.SHOOT) {
                                    selector = Selector.SECOND;
                                    tag = Color.RED + "-";
                                }
                                else if (daction == ItemTransactionActions.BREAK || daction == ItemTransactionActions.DESTROY || daction == ItemTransactionActions.CREATE) {
                                    selector = (daction == ItemTransactionActions.CREATE ? Selector.FIRST : Selector.SECOND);
                                    tag = (daction == ItemTransactionActions.CREATE ? Color.GREEN + "+" : Color.RED + "-");
                                }
                                else if (daction == ItemTransactionActions.SELL || daction == ItemTransactionActions.BUY) { // LOOKUP_TRADE
                                    selector = (daction == ItemTransactionActions.BUY ? Selector.FIRST : Selector.SECOND);
                                    tag = (daction == ItemTransactionActions.BUY ? Color.GREEN + "+" : Color.RED + "-");
                                }
                                else { // LOOKUP_CONTAINER
                                    selector = (daction == ItemTransactionActions.REMOVE ? Selector.FIRST : Selector.SECOND);
                                    tag = (daction == ItemTransactionActions.REMOVE ? Color.GREEN + "+" : Color.RED + "-");
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
                                if (daction == LookupActions.ENTITY_KILL && !actions.contains(LookupActions.ITEM) && amount == -1) {
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
                                    dname = MaterialUtils.getBlockDisplayName(dtype, ddata);
                                }
                                if (dname.length() > 0 && !isPlayer && !dname.contains(":")) {
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
                                if (actions.contains(LookupActions.CONTAINER) || actions.contains(5) || actions.contains(LookupActions.ITEM) || amount > -1) {
                                    byte[] metadata = data[11] == null ? null : data[11].getBytes(StandardCharsets.ISO_8859_1);
                                    String tooltip = ItemUtils.getEnchantments(metadata, dtype, amount);

                                    if (daction == ItemTransactionActions.DROP || daction == ItemTransactionActions.PICKUP) {
                                        phrase = Phrase.LOOKUP_ITEM; // {picked up|dropped}
                                        selector = (daction != ItemTransactionActions.DROP ? Selector.FIRST : Selector.SECOND);
                                        tag = (daction != ItemTransactionActions.DROP ? Color.GREEN + "+" : Color.RED + "-");
                                        action = "a:item";
                                    }
                                    else if (daction == ItemTransactionActions.REMOVE_ENDER || daction == ItemTransactionActions.ADD_ENDER) {
                                        phrase = Phrase.LOOKUP_STORAGE; // {deposited|withdrew}
                                        selector = (daction != ItemTransactionActions.REMOVE_ENDER ? Selector.FIRST : Selector.SECOND);
                                        tag = (daction != ItemTransactionActions.REMOVE_ENDER ? Color.RED + "-" : Color.GREEN + "+");
                                        action = "a:item";
                                    }
                                    else if (daction == ItemTransactionActions.THROW || daction == ItemTransactionActions.SHOOT) {
                                        phrase = Phrase.LOOKUP_PROJECTILE; // {threw|shot}
                                        selector = (daction != ItemTransactionActions.SHOOT ? Selector.FIRST : Selector.SECOND);
                                        tag = Color.RED + "-";
                                        action = "a:item";
                                    }
                                    else {
                                        phrase = Phrase.LOOKUP_CONTAINER; // {added|removed}
                                        selector = (daction != ItemTransactionActions.REMOVE ? Selector.FIRST : Selector.SECOND);
                                        tag = (daction != ItemTransactionActions.REMOVE ? Color.GREEN + "+" : Color.RED + "-");
                                        action = "a:container";
                                    }

                                    Chat.sendComponent(player, timeago + " " + tag + " " + Phrase.build(phrase, Color.DARK_AQUA + rbd + dplayer + Color.WHITE + rbd, "x" + amount, ChatUtils.createTooltip(Color.DARK_AQUA + rbd + dname, tooltip) + Color.WHITE, selector));
                                    PluginChannelListener.getInstance().sendData(player, Integer.parseInt(time), phrase, selector, dplayer, dname, (tag.contains("+") ? 1 : -1), dataX, dataY, dataZ, wid, rbd, action.contains("container"), tag.contains("+"));
                                }
                                else {
                                    if (daction == LookupActions.INTERACTION || daction == LookupActions.ENTITY_KILL) {
                                        phrase = Phrase.LOOKUP_INTERACTION; // {clicked|killed}
                                        selector = (daction != LookupActions.ENTITY_KILL ? Selector.FIRST : Selector.SECOND);
                                        tag = (daction != LookupActions.ENTITY_KILL ? Color.WHITE + "-" : Color.RED + "-");
                                        action = (daction == LookupActions.INTERACTION ? "a:click" : "a:kill");
                                    }
                                    else {
                                        phrase = Phrase.LOOKUP_BLOCK; // {placed|broke}
                                        selector = (daction != LookupActions.BLOCK_BREAK ? Selector.FIRST : Selector.SECOND);
                                        tag = (daction != LookupActions.BLOCK_BREAK ? Color.GREEN + "+" : Color.RED + "-");
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
                            if (actions.contains(LookupActions.CHAT) || actions.contains(LookupActions.COMMAND) || actions.contains(LookupActions.USERNAME) || LookupActions.isInventoryLookup(actions)) {
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
}
