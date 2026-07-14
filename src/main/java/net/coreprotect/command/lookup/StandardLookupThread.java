package net.coreprotect.command.lookup;

import java.sql.Connection;
import java.sql.Statement;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.coreprotect.data.lookup.LookupResult;
import net.coreprotect.data.lookup.result.ChatLookupResult;
import net.coreprotect.data.lookup.result.CommonLookupResult;
import net.coreprotect.data.lookup.result.SessionLookupResult;
import net.coreprotect.data.lookup.result.SignLookupResult;
import net.coreprotect.data.lookup.result.UsernameHistoryLookupResult;
import net.coreprotect.data.lookup.type.ChatLookupData;
import net.coreprotect.data.lookup.type.CommonLookupData;
import net.coreprotect.data.lookup.type.SessionLookupData;
import net.coreprotect.data.lookup.type.SignLookupData;
import net.coreprotect.data.lookup.type.UsernameHistoryData;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

import com.google.common.base.Strings;

import net.coreprotect.config.ConfigHandler;
import net.coreprotect.database.Database;
import net.coreprotect.database.Lookup;
import net.coreprotect.database.lookup.EntityInteractionLookup;
import net.coreprotect.database.lookup.PlayerLookup;
import net.coreprotect.database.statement.EntitySpawnStatement;
import net.coreprotect.database.statement.UserStatement;
import net.coreprotect.language.Phrase;
import net.coreprotect.language.Selector;
import net.coreprotect.listener.channel.PluginChannelHandshakeListener;
import net.coreprotect.listener.channel.PluginChannelListener;
import net.coreprotect.model.action.EntityActionFilter;
import net.coreprotect.model.action.LookupActions;
import net.coreprotect.model.action.SessionActions;
import net.coreprotect.model.entity.EntitySpawnRecord;
import net.coreprotect.model.item.InventorySources;
import net.coreprotect.model.item.ItemTransactionActions;
import net.coreprotect.model.lookup.LookupRollbackState;
import net.coreprotect.model.lookup.LookupSummaryRow;
import net.coreprotect.utility.Chat;
import net.coreprotect.utility.ChatUtils;
import net.coreprotect.utility.Color;
import net.coreprotect.utility.EntityUtils;
import net.coreprotect.utility.EntitySpawnTracking;
import net.coreprotect.utility.ItemUtils;
import net.coreprotect.utility.MaterialUtils;
import net.coreprotect.utility.StringUtils;
import net.coreprotect.utility.WorldUtils;
import net.coreprotect.utility.ErrorReporter;

public class StandardLookupThread implements Runnable {
    private final CommandSender player;
    private final Command command;
    private final List<String> rollbackUsers;
    private final List<Object> blockList;
    private final Map<Object, Boolean> excludedBlocks;
    private final List<String> excludedUsers;
    private final List<Integer> actions;
    private final EntityActionFilter entityActionFilter;
    private final List<String> messageFilters;
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
    private final boolean summary;
    private final LookupRollbackState rollbackState;

    public StandardLookupThread(CommandSender player, Command command, List<String> rollbackUsers, List<Object> blockList, Map<Object, Boolean> excludedBlocks, List<String> excludedUsers, List<Integer> actions, List<String> messageFilters, Integer[] radius, Location location, int x, int y, int z, int worldId, int argWorldId, long timeStart, long timeEnd, int noisy, int excluded, int restricted, int page, int displayResults, int typeLookup, String rtime, boolean count) {
        this(player, command, rollbackUsers, blockList, excludedBlocks, excludedUsers, actions, messageFilters, radius, location, x, y, z, worldId, argWorldId, timeStart, timeEnd, noisy, excluded, restricted, page, displayResults, typeLookup, rtime, count, false, LookupRollbackState.ANY);
    }

    public StandardLookupThread(CommandSender player, Command command, List<String> rollbackUsers, List<Object> blockList, Map<Object, Boolean> excludedBlocks, List<String> excludedUsers, List<Integer> actions, List<String> messageFilters, Integer[] radius, Location location, int x, int y, int z, int worldId, int argWorldId, long timeStart, long timeEnd, int noisy, int excluded, int restricted, int page, int displayResults, int typeLookup, String rtime, boolean count, LookupRollbackState rollbackState) {
        this(player, command, rollbackUsers, blockList, excludedBlocks, excludedUsers, actions, messageFilters, radius, location, x, y, z, worldId, argWorldId, timeStart, timeEnd, noisy, excluded, restricted, page, displayResults, typeLookup, rtime, count, false, rollbackState);
    }

    public StandardLookupThread(CommandSender player, Command command, List<String> rollbackUsers, List<Object> blockList, Map<Object, Boolean> excludedBlocks, List<String> excludedUsers, List<Integer> actions, List<String> messageFilters, Integer[] radius, Location location, int x, int y, int z, int worldId, int argWorldId, long timeStart, long timeEnd, int noisy, int excluded, int restricted, int page, int displayResults, int typeLookup, String rtime, boolean count, boolean summary, LookupRollbackState rollbackState) {
        this(player, command, rollbackUsers, blockList, excludedBlocks, excludedUsers, actions, EntityActionFilter.DEFAULT, messageFilters, radius, location, x, y, z, worldId, argWorldId, timeStart, timeEnd, noisy, excluded, restricted, page, displayResults, typeLookup, rtime, count, summary, rollbackState);
    }

    public StandardLookupThread(CommandSender player, Command command, List<String> rollbackUsers, List<Object> blockList, Map<Object, Boolean> excludedBlocks, List<String> excludedUsers, List<Integer> actions, EntityActionFilter entityActionFilter, List<String> messageFilters, Integer[] radius, Location location, int x, int y, int z, int worldId, int argWorldId, long timeStart, long timeEnd, int noisy, int excluded, int restricted, int page, int displayResults, int typeLookup, String rtime, boolean count, boolean summary, LookupRollbackState rollbackState) {
        this.player = player;
        this.command = command;
        this.rollbackUsers = rollbackUsers;
        this.blockList = blockList;
        this.excludedBlocks = excludedBlocks;
        this.excludedUsers = excludedUsers;
        this.actions = actions;
        this.entityActionFilter = entityActionFilter == null ? EntityActionFilter.DEFAULT : entityActionFilter;
        this.messageFilters = messageFilters;
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
        this.summary = summary;
        this.rollbackState = rollbackState == null ? LookupRollbackState.ANY : rollbackState;
    }

    @Override
    public void run() {
        try (Connection connection = Database.getConnection(true)) {
            ConfigHandler.lookupThrottle.put(player.getName(), new Object[] { true, System.currentTimeMillis() });

            List<String> uuidList = new ArrayList<>();
            Integer entityContainerId = actions.contains(5) ? ConfigHandler.lookupEntityContainer.get(player.getName()) : null;
            if (!actions.contains(5)) {
                ConfigHandler.lookupEntityContainer.remove(player.getName());
            }
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
            ConfigHandler.lookupEntityActionFilter.put(player.getName(), entityActionFilter);
            ConfigHandler.lookupFlist.put(player.getName(), messageFilters);
            ConfigHandler.lookupSummary.put(player.getName(), summary);
            ConfigHandler.lookupRollbackState.put(player.getName(), rollbackState);
            ConfigHandler.lookupRadius.put(player.getName(), radius);

            if (connection == null) {
                Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.DATABASE_BUSY));
                return;
            }

            try (Statement statement = connection.createStatement()) {
                String baduser = "";
                for (String check : rollbackUsers) {
                    if ((!check.equals("#global") && !check.equals("#container")) || actions.contains(LookupActions.USERNAME)) {
                        exists = PlayerLookup.playerExists(connection, check);
                        if (!exists) {
                            baduser = check;
                            break;
                        } else if (actions.contains(LookupActions.USERNAME)) {
                            if (ConfigHandler.uuidCache.get(check.toLowerCase(Locale.ROOT)) != null) {
                                String uuid = ConfigHandler.uuidCache.get(check.toLowerCase(Locale.ROOT));
                                uuidList.add(uuid);
                            }
                        }
                    } else {
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
                        } else if (check.equals("#global")) {
                            baduser = "#global";
                            exists = false;
                        }
                    }
                }

                if (!exists) {
                    Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.USER_NOT_FOUND, baduser));
                    return;
                }

                List<String> userList = new ArrayList<>();
                if (!actions.contains(LookupActions.USERNAME)) {
                    userList = rollbackUsers;
                }

                int currentUnixSeconds = (int) (System.currentTimeMillis() / 1000L);
                boolean restrict_world = radius != null;

                if (finalLocation == null) {
                    restrict_world = false;
                }

                if (argWorldId > 0) {
                    restrict_world = true;
                    finalLocation = new Location(Bukkit.getServer().getWorld(WorldUtils.getWorldName(argWorldId)), x, y, z);
                } else if (finalLocation != null) {
                    finalLocation = new Location(Bukkit.getServer().getWorld(WorldUtils.getWorldName(worldId)), x, y, z);
                }

                Long[] rowData = new Long[] { 0L, 0L, 0L, 0L, 0L };
                long rowMax = (long) page * displayResults;
                long pageStart = rowMax - displayResults;
                boolean checkRows = true;

                if (typeLookup == 5 && page > 1) {
                    rowData = ConfigHandler.lookupRows.get(player.getName());
                    if (rowData == null || rowData.length < 5) {
                        rowData = new Long[] { 0L, 0L, 0L, 0L, 0L };
                    }
                    long cachedRows = rowData[4];

                    if (pageStart < cachedRows) {
                        checkRows = false;
                    }
                }

                if (summary) {
                    long rows;
                    long recordRows = 0L;
                    if (checkRows) {
                        rows = Lookup.countSummaryRows(statement, player, uuidList, userList, blockList, excludedBlocks, excludedUsers, actions, finalLocation, radius, timeStart, timeEnd, restrict_world, rollbackState);
                        if (rows > 0) {
                            recordRows = Lookup.countLookupRows(statement, player, uuidList, userList, blockList, excludedBlocks, excludedUsers, actions, entityActionFilter, messageFilters, Collections.emptySet(), Collections.emptySet(), finalLocation, radius, rowData, timeStart, timeEnd, restrict_world, true, entityContainerId, rollbackState);
                            rowData[0] = recordRows;
                            rowData[1] = 0L;
                            rowData[2] = 0L;
                            rowData[3] = 0L;
                        }
                        rowData[4] = rows;
                        ConfigHandler.lookupRows.put(player.getName(), rowData);
                    }
                    else {
                        rows = rowData[4];
                        recordRows = rowData[0];
                    }

                    if (pageStart >= rows) {
                        if (rows > 0) {
                            Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.NO_RESULTS_PAGE, Selector.FIRST));
                        }
                        else {
                            Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.NO_RESULTS));
                        }
                        return;
                    }

                    List<LookupSummaryRow> summaryRows = Lookup.performSummaryLookup(statement, player, uuidList, userList, blockList, excludedBlocks, excludedUsers, actions, finalLocation, radius, timeStart, timeEnd, (int) pageStart, displayResults, restrict_world, rollbackState);
                    outputSummary(connection, summaryRows, rows, recordRows);
                    return;
                }

                final LookupResult<?> lookupResult = Lookup.performLookup(statement, player, uuidList, userList, blockList, excludedBlocks, excludedUsers, actions, entityActionFilter, messageFilters, Collections.emptySet(), Collections.emptySet(), finalLocation, radius, rowData, timeStart, timeEnd, (int) pageStart, displayResults, restrict_world, true, checkRows, rollbackState, entityContainerId);
                if (lookupResult == null) {
                    Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- An error occurred while processing this lookup.");
                    return;
                }

                long rows = lookupResult.totalResultSize();

                if (checkRows) {
                    rowData[4] = rows;
                    ConfigHandler.lookupRows.put(player.getName(), rowData);
                } else {
                    // retrieve cached rows
                    rows = rowData[4];
                }

                if (count) {
                    String row_format = NumberFormat.getInstance().format(rows);
                    Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.LOOKUP_ROWS_FOUND, row_format, (rows == 1 ? Selector.FIRST : Selector.SECOND)));
                    return;
                } else if (pageStart >= rows) {
                    if (rows > 0) {
                        Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.NO_RESULTS_PAGE, Selector.FIRST));
                    } else {
                        Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.NO_RESULTS));
                    }

                    return;
                }

                Chat.sendMessage(player, Color.WHITE + "----- " + Color.DARK_AQUA + Phrase.build(Phrase.LOOKUP_HEADER, "CoreProtect" + Color.WHITE + " | " + Color.DARK_AQUA) + Color.WHITE + " -----");
                switch (lookupResult) {
                    case ChatLookupResult chatResult -> {
                        for (ChatLookupData data : chatResult.data()) {
                            String timeAgo = ChatUtils.getTimeSince(data.time(), currentUnixSeconds, true);
                            final String dash = data.cancelled() ? Color.RED + "<hover:show_text:'This message was cancelled and was not sent'>-</hover>" : Color.WHITE + "-";

                            Chat.sendComponent(player, timeAgo + " " + dash + " " + Color.DARK_AQUA + data.playerName() + ": " + Color.WHITE + ChatUtils.formatHoverCoordinates(command.getName(), data.worldId(), data.x(), data.y(), data.z()), data.message());
                            if (PluginChannelHandshakeListener.getInstance().isPluginChannelPlayer(player)) {
                                PluginChannelListener.getInstance().sendMessageData(player, data.time(), data.playerName(), data.message(), false, data.x(), data.y(), data.z(), data.worldId());
                            }
                        }
                    }
                    case SessionLookupResult sessionResult -> {
                        for (SessionLookupData data : sessionResult.data()) {
                            String timeAgo = ChatUtils.getTimeSince(data.time(), currentUnixSeconds, true);
                            int timeLength = 50 + (ChatUtils.getTimeSince(data.time(), currentUnixSeconds, false).replaceAll("[^0-9]", "").length() * 6);
                            String leftPadding = Strings.padStart("", 10, ' ');

                            if (timeLength % 4 == 0) {
                                leftPadding = Strings.padStart("", timeLength / 4, ' ');
                            } else {
                                leftPadding = leftPadding + Color.WHITE + Strings.padStart("", (timeLength - 50) / 4, ' ');
                            }

                            final boolean isLogin = data.action() == SessionActions.LOGIN;
                            String tag = (isLogin ? Color.GREEN + "+" : Color.RED + "-");
                            Chat.sendComponent(player, timeAgo + " " + tag + " " + Color.DARK_AQUA + Phrase.build(Phrase.LOOKUP_LOGIN, Color.DARK_AQUA + data.playerName() + Color.WHITE, (isLogin ? Selector.FIRST : Selector.SECOND)));
                            Chat.sendComponent(player, Color.WHITE + leftPadding + Color.GREY + "^ " + ChatUtils.getCoordinates(command.getName(), data.worldId(), data.x(), data.y(), data.z(), true, true));

                            PluginChannelListener.getInstance().sendInfoData(player, data.time(), Phrase.LOOKUP_LOGIN, (isLogin ? Selector.FIRST : Selector.SECOND), data.playerName(), -1, data.x(), data.y(), data.z(), data.worldId());
                        }
                    }
                    case UsernameHistoryLookupResult usernameHistoryResult -> {
                        for (UsernameHistoryData data : usernameHistoryResult.data()) {
                            String user = ConfigHandler.uuidCacheReversed.get(data.uuid());
                            String timeAgo = ChatUtils.getTimeSince(data.time(), currentUnixSeconds, true);

                            Chat.sendComponent(player, timeAgo + " " + Color.WHITE + "- " + Phrase.build(Phrase.LOOKUP_USERNAME, Color.DARK_AQUA + user + Color.WHITE, Color.DARK_AQUA + data.username() + Color.WHITE));
                            PluginChannelListener.getInstance().sendUsernameData(player, data.time(), user, data.username());
                        }
                    }
                    case SignLookupResult signResult -> {
                        for (SignLookupData data : signResult.data()) {
                            String timeAgo = ChatUtils.getTimeSince(data.time(), currentUnixSeconds, true);
                            int timeLength = 50 + (ChatUtils.getTimeSince(data.time(), currentUnixSeconds, false).replaceAll("[^0-9]", "").length() * 6);
                            String leftPadding = Strings.padStart("", 10, ' ');
                            if (timeLength % 4 == 0) {
                                leftPadding = Strings.padStart("", timeLength / 4, ' ');
                            } else {
                                leftPadding = leftPadding + Color.WHITE + Strings.padStart("", (timeLength - 50) / 4, ' ');
                            }

                            Chat.sendComponent(player, timeAgo + " " + Color.WHITE + "- " + Color.DARK_AQUA + data.playerName() + ": " + Color.WHITE, data.text());
                            Chat.sendComponent(player, Color.WHITE + leftPadding + Color.GREY + "^ " + ChatUtils.getCoordinates(command.getName(), data.worldId(), data.x(), data.y(), data.z(), true, true));
                            PluginChannelListener.getInstance().sendMessageData(player, data.time(), data.playerName(), data.text(), true, data.x(), data.y(), data.z(), data.worldId());
                        }
                    }
                    case CommonLookupResult commonResult -> {
                        if (LookupActions.isInventoryLookup(actions)) { // inventory transactions
                            for (CommonLookupData data : commonResult.data()) {
                                String dplayer = data.playerName();
                                int dtype = data.type();
                                int ddata = data.data();
                                int daction = data.action();
                                int amount = data.amount();
                                int wid = data.worldId();
                                int dataX = data.x();
                                int dataY = data.y();
                                int dataZ = data.z();
                                String rollbackDecoration = ((data.rolledBack() == 2 || data.rolledBack() == 3) ? Color.STRIKETHROUGH : "");
                                String timeAgo = ChatUtils.getTimeSince(data.time(), currentUnixSeconds, true);
                                Material blockType = ItemUtils.itemFilter(MaterialUtils.getType(dtype), data.table() == 0);
                                String dname = StringUtils.nameFilter(blockType.name().toLowerCase(Locale.ROOT), ddata);
                                String itemData = data.metadata();
                                String hover = ItemUtils.getItemHover(itemData, dtype, amount);

                                String selector = Selector.FIRST;
                                String tag = Color.WHITE + "-";
                                if (daction == ItemTransactionActions.DROP || daction == ItemTransactionActions.PICKUP) {
                                    selector = (daction != ItemTransactionActions.DROP ? Selector.FIRST : Selector.SECOND);
                                    tag = (daction != ItemTransactionActions.DROP ? Color.GREEN + "+" : Color.RED + "-");
                                } else if (daction == ItemTransactionActions.REMOVE_ENDER || daction == ItemTransactionActions.ADD_ENDER) {
                                    selector = (daction == ItemTransactionActions.REMOVE_ENDER ? Selector.FIRST : Selector.SECOND);
                                    tag = (daction == ItemTransactionActions.REMOVE_ENDER ? Color.GREEN + "+" : Color.RED + "-");
                                } else if (daction == ItemTransactionActions.THROW || daction == ItemTransactionActions.SHOOT) {
                                    selector = Selector.SECOND;
                                    tag = Color.RED + "-";
                                } else if (daction == ItemTransactionActions.BREAK || daction == ItemTransactionActions.DESTROY || daction == ItemTransactionActions.CREATE) {
                                    selector = (daction == ItemTransactionActions.CREATE ? Selector.FIRST : Selector.SECOND);
                                    tag = (daction == ItemTransactionActions.CREATE ? Color.GREEN + "+" : Color.RED + "-");
                                }
                                else if (daction == ItemTransactionActions.SELL || daction == ItemTransactionActions.BUY) { // LOOKUP_TRADE
                                    selector = (daction == ItemTransactionActions.BUY ? Selector.FIRST : Selector.SECOND);
                                    tag = (daction == ItemTransactionActions.BUY ? Color.GREEN + "+" : Color.RED + "-");
                                } else { // LOOKUP_CONTAINER
                                    selector = (daction == ItemTransactionActions.REMOVE ? Selector.FIRST : Selector.SECOND);
                                    tag = (daction == ItemTransactionActions.REMOVE ? Color.GREEN + "+" : Color.RED + "-");
                                }

                                Chat.sendComponent(player, timeAgo + " " + tag + " " + Phrase.build(Phrase.LOOKUP_CONTAINER, Color.DARK_AQUA + rollbackDecoration + dplayer + Color.WHITE + rollbackDecoration, "x" + amount, ItemUtils.createItemTooltip(Color.DARK_AQUA + rollbackDecoration + dname, hover) + Color.WHITE, selector));
                                PluginChannelListener.getInstance().sendData(player, data.time(), Phrase.LOOKUP_CONTAINER, selector, dplayer, dname, amount, dataX, dataY, dataZ, wid, rollbackDecoration, true, tag.contains("+"));
                            }
                        } else {
                            for (CommonLookupData data : commonResult.data()) { // everything else
                                int rolledBack = data.rolledBack();
                                String rollbackDecoration = "";
                                if (rolledBack == 1 || rolledBack == 3) {
                                    rollbackDecoration = Color.STRIKETHROUGH;
                                }

                                String dplayer = data.playerName();
                                int dataX = data.x();
                                int dataY = data.y();
                                int dataZ = data.z();
                                int dtype = data.type();
                                int ddata = data.data();
                                int daction = data.action();
                                int worldId = data.worldId();
                                int amount = data.amount();
                                String tag = Color.WHITE + "-";

                                String timeago = ChatUtils.getTimeSince(data.time(), currentUnixSeconds, true);
                                int timeLength = 50 + (ChatUtils.getTimeSince(data.time(), currentUnixSeconds, false).replaceAll("[^0-9]", "").length() * 6);
                                String leftPadding = Strings.padStart("", 10, ' ');
                                if (timeLength % 4 == 0) {
                                    leftPadding = Strings.padStart("", timeLength / 4, ' ');
                                } else {
                                    leftPadding = leftPadding + Color.WHITE + Strings.padStart("", (timeLength - 50) / 4, ' ');
                                }

                                String dname = "";
                                boolean isPlayer = false;
                                boolean entityInteraction = Integer.valueOf(InventorySources.ENTITY_INTERACTION).equals(data.table());
                                if (entityInteraction) {
                                    dname = EntityInteractionLookup.entityName(dtype);
                                }
                                else if (daction == LookupActions.ENTITY_SPAWN) {
                                    dname = EntityInteractionLookup.entityName(dtype);
                                }
                                else if (daction == LookupActions.ENTITY_KILL && !actions.contains(LookupActions.ITEM) && amount == -1) {
                                    if (dtype == 0) {
                                        String playerName = ConfigHandler.playerIdCacheReversed.get(ddata);
                                        if (playerName == null) {
                                            playerName = UserStatement.loadName(connection, ddata);
                                        }

                                        dname = playerName;
                                        isPlayer = true;
                                    } else {
                                        dname = EntityUtils.getEntityType(dtype).name().toLowerCase(Locale.ROOT);
                                    }
                                } else {
                                    dname = MaterialUtils.getBlockDisplayName(dtype, ddata);
                                }

                                /* CH - don't add minecraft: just to remove it
                                if (!dname.isEmpty() && !isPlayer && !dname.contains(":")) {
                                    dname = "minecraft:" + dname.toLowerCase(Locale.ROOT);
                                }
                                */

                                // Hide "minecraft:" for now.
                                if (dname.contains("minecraft:")) {
                                    String[] blockNameSplit = dname.split(":", 2);
                                    dname = blockNameSplit[1];
                                }

                                Phrase phrase = Phrase.LOOKUP_BLOCK;
                                String selector = Selector.FIRST;
                                String action = "a:block";
                                if (actions.contains(LookupActions.CONTAINER) || actions.contains(5) || actions.contains(LookupActions.ITEM) || amount > -1) {
                                    String itemData = data.metadata();
                                    String hover = ItemUtils.getItemHover(itemData, dtype, amount); // todo: givable item

                                    if (daction == ItemTransactionActions.DROP || daction == ItemTransactionActions.PICKUP) {
                                        phrase = Phrase.LOOKUP_ITEM; // {picked up|dropped}
                                        selector = (daction != ItemTransactionActions.DROP ? Selector.FIRST : Selector.SECOND);
                                        tag = (daction != ItemTransactionActions.DROP ? Color.GREEN + "+" : Color.RED + "-");
                                        action = "a:item";
                                    } else if (daction == ItemTransactionActions.REMOVE_ENDER || daction == ItemTransactionActions.ADD_ENDER) {
                                        phrase = Phrase.LOOKUP_STORAGE; // {deposited|withdrew}
                                        selector = (daction != ItemTransactionActions.REMOVE_ENDER ? Selector.FIRST : Selector.SECOND);
                                        tag = (daction != ItemTransactionActions.REMOVE_ENDER ? Color.RED + "-" : Color.GREEN + "+");
                                        action = "a:item";
                                    } else if (daction == ItemTransactionActions.THROW || daction == ItemTransactionActions.SHOOT) {
                                        phrase = Phrase.LOOKUP_PROJECTILE; // {threw|shot}
                                        selector = (daction != ItemTransactionActions.SHOOT ? Selector.FIRST : Selector.SECOND);
                                        tag = Color.RED + "-";
                                        action = "a:item";
                                    } else {
                                        phrase = Phrase.LOOKUP_CONTAINER; // {added|removed}
                                        selector = (daction != ItemTransactionActions.REMOVE ? Selector.FIRST : Selector.SECOND);
                                        tag = (daction != ItemTransactionActions.REMOVE ? Color.GREEN + "+" : Color.RED + "-");
                                        action = "a:container";
                                    }

                                    Chat.sendComponent(player, timeago + " " + tag + " " + Phrase.build(phrase, Color.DARK_AQUA + rollbackDecoration + dplayer + Color.WHITE + rollbackDecoration, "x" + amount, ItemUtils.createItemTooltip(Color.DARK_AQUA + rollbackDecoration + dname, hover) + Color.WHITE, selector));
                                    PluginChannelListener.getInstance().sendData(player, data.time(), phrase, selector, dplayer, dname, (tag.contains("+") ? 1 : -1), dataX, dataY, dataZ, worldId, rollbackDecoration, action.contains("container"), tag.contains("+"));
                                } else {
                                    if (entityInteraction) {
                                        phrase = Phrase.LOOKUP_ENTITY_INTERACTION;
                                        selector = EntityInteractionLookup.actionSelector(ddata);
                                        tag = Color.WHITE + "-";
                                        action = "a:click";
                                    } else if (daction == LookupActions.ENTITY_SPAWN) {
                                        phrase = Phrase.LOOKUP_ENTITY_SPAWN;
                                        selector = Selector.FIRST;
                                        tag = Color.GREEN + "+";
                                        action = "a:entity";
                                    } else if (daction == LookupActions.INTERACTION || daction == LookupActions.ENTITY_KILL) {
                                        phrase = Phrase.LOOKUP_INTERACTION; // {clicked|killed}
                                        selector = (daction != LookupActions.ENTITY_KILL ? Selector.FIRST : Selector.SECOND);
                                        tag = (daction != LookupActions.ENTITY_KILL ? Color.WHITE + "-" : Color.RED + "-");
                                        action = (daction == LookupActions.INTERACTION ? "a:click" : "a:kill");
                                    } else {
                                        phrase = Phrase.LOOKUP_BLOCK; // {placed|broke}
                                        selector = (daction != LookupActions.BLOCK_BREAK ? Selector.FIRST : Selector.SECOND);
                                        tag = (daction != LookupActions.BLOCK_BREAK ? Color.GREEN + "+" : Color.RED + "-");
                                    }

                                    Chat.sendComponent(player, timeago + " " + tag + " " + Phrase.build(phrase, Color.DARK_AQUA + rollbackDecoration + dplayer + Color.WHITE + rollbackDecoration, Color.DARK_AQUA + rollbackDecoration + dname + Color.WHITE, selector));
                                    PluginChannelListener.getInstance().sendData(player, data.time(), phrase, selector, dplayer, dname, (tag.contains("+") ? 1 : -1), dataX, dataY, dataZ, worldId, rollbackDecoration, false, tag.contains("+"));
                                }

                                action = (actions.isEmpty() ? " (" + action + ")" : "");
                                Chat.sendComponent(player, Color.WHITE + leftPadding + Color.GREY + "^ " + ChatUtils.getCoordinates(command.getName(), worldId, dataX, dataY, dataZ, true, true) + Color.GREY + Color.ITALIC + action);
                            }
                        }
                    }
                    default -> throw new IllegalStateException("Unexpected value: " + lookupResult);
                }

                if (rows > displayResults) {
                    int total_pages = (int) Math.ceil(rows / (displayResults + 0.0));
                    if (actions.contains(6) || actions.contains(7) || actions.contains(9) || (actions.contains(4) && actions.contains(11))) {
                        Chat.sendMessage(player, "-----");
                    }
                    Chat.sendComponent(player, ChatUtils.getPageNavigation(command.getName(), page, total_pages));
                }
            }
        }
        catch (Exception e) {
            ErrorReporter.report(e);
        } finally {
            ConfigHandler.lookupThrottle.put(player.getName(), new Object[] { false, System.currentTimeMillis() });
        }
    }

    private void outputSummary(Connection connection, List<LookupSummaryRow> summaryRows, long totalRows, long recordRows) {
        if (summaryRows.isEmpty()) {
            Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.NO_RESULTS));
            return;
        }

        NumberFormat numberFormat = NumberFormat.getInstance();
        String rowsFound = Phrase.build(Phrase.LOOKUP_ROWS_FOUND, numberFormat.format(recordRows), recordRows == 1 ? Selector.FIRST : Selector.SECOND);
        Chat.sendMessage(player, Color.WHITE + "----- " + Color.DARK_AQUA + "CoreProtect" + Color.WHITE + " | " + Color.DARK_AQUA + rowsFound + Color.WHITE + " -----");

        for (LookupSummaryRow row : summaryRows) {
            String userName = UserStatement.loadName(connection, row.getUserId());
            if (userName == null || userName.isEmpty()) {
                userName = "unknown";
            }

            String materialName = MaterialUtils.getBlockDisplayName(row.getMaterialId(), 0);
            if (materialName == null || materialName.isEmpty()) {
                materialName = MaterialUtils.getBlockNameShort(row.getMaterialId());
            }
            if (materialName == null || materialName.isEmpty()) {
                materialName = "#" + row.getMaterialId();
            }
            if (materialName.contains("minecraft:")) {
                materialName = materialName.split(":", 2)[1];
            }

            long removedAmount = row.getRemovedAmount();
            long placedAmount = row.getPlacedAmount();
            long netAmount = row.getNetAmount();
            String formattedNetAmount = (netAmount >= 0 ? "+" : "") + numberFormat.format(netAmount);
            Chat.sendComponent(player, Color.DARK_AQUA + userName + Color.WHITE + ": " + Color.RED + "-" + numberFormat.format(removedAmount)
                    + Color.GREY + " / " + Color.GREEN + "+" + numberFormat.format(placedAmount) + Color.WHITE + " = " + formattedNetAmount
                    + Color.WHITE + " " + Color.DARK_AQUA + materialName + Color.WHITE);
        }

        if (totalRows > displayResults) {
            int totalPages = (int) Math.ceil(totalRows / (displayResults + 0.0));
            Chat.sendComponent(player, ChatUtils.getPageNavigation(command.getName(), page, totalPages));
        }
    }

    private static EntityDisplayLocation resolveEntityDisplayLocation(String[] data, Map<Integer, EntitySpawnRecord> records, Map<UUID, Location> loadedLocations) {
        if (data.length <= 14 || data[7] == null) {
            return null;
        }

        int trackingRowId = 0;
        if (data[13] != null && Integer.parseInt(data[13]) == InventorySources.ENTITY_CONTAINER && data[14] != null) {
            trackingRowId = Integer.parseInt(data[14]);
        }
        else if (data[6] != null && Integer.parseInt(data[7]) == LookupActions.ENTITY_SPAWN) {
            trackingRowId = Integer.parseInt(data[6]);
        }
        if (trackingRowId == 0) {
            return null;
        }

        EntitySpawnRecord record = records.get(trackingRowId);
        if (record == null) {
            return null;
        }

        int currentWorldId = record.getWorldId();
        int currentX = (int) Math.floor(record.getX());
        int currentY = (int) Math.floor(record.getY());
        int currentZ = (int) Math.floor(record.getZ());
        Location loadedLocation = loadedLocations.get(record.getUuid());
        if (loadedLocation != null && loadedLocation.getWorld() != null) {
            currentWorldId = WorldUtils.getWorldId(loadedLocation.getWorld().getName());
            currentX = loadedLocation.getBlockX();
            currentY = loadedLocation.getBlockY();
            currentZ = loadedLocation.getBlockZ();
        }

        return new EntityDisplayLocation(currentWorldId, currentX, currentY, currentZ, Integer.parseInt(data[9]), Integer.parseInt(data[2]), Integer.parseInt(data[3]), Integer.parseInt(data[4]));
    }

    private static final class EntityDisplayLocation {
        private final int worldId;
        private final int x;
        private final int y;
        private final int z;
        private final int originWorldId;
        private final int originX;
        private final int originY;
        private final int originZ;

        private EntityDisplayLocation(int worldId, int x, int y, int z, int originWorldId, int originX, int originY, int originZ) {
            this.worldId = worldId;
            this.x = x;
            this.y = y;
            this.z = z;
            this.originWorldId = originWorldId;
            this.originX = originX;
            this.originY = originY;
            this.originZ = originZ;
        }

        private String getOriginTooltip() {
            if (worldId == originWorldId && x == originX && y == originY && z == originZ) {
                return "";
            }
            return ChatUtils.getCoordinateTooltip(originWorldId, originX, originY, originZ, Phrase.build(Phrase.LOOKUP_ENTITY_ORIGIN), true);
        }
    }
}
