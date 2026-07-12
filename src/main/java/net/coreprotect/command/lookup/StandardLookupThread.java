package net.coreprotect.command.lookup;

import java.nio.charset.StandardCharsets;
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
import java.util.concurrent.atomic.AtomicBoolean;

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
import net.coreprotect.model.lookup.LookupOutputMode;
import net.coreprotect.model.lookup.LookupSummaryPage;
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
    private static final int SUMMARY_QUERY_TIMEOUT_SECONDS = 30;
    private static final AtomicBoolean SUMMARY_LOOKUP_ACTIVE = new AtomicBoolean(false);

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
    private final LookupOutputMode outputMode;

    public StandardLookupThread(CommandSender player, Command command, List<String> rollbackUsers, List<Object> blockList, Map<Object, Boolean> excludedBlocks, List<String> excludedUsers, List<Integer> actions, EntityActionFilter entityActionFilter, List<String> messageFilters, Integer[] radius, Location location, int x, int y, int z, int worldId, int argWorldId, long timeStart, long timeEnd, int noisy, int excluded, int restricted, int page, int displayResults, int typeLookup, String rtime, LookupOutputMode outputMode) {
        this.player = player;
        this.command = command;
        this.rollbackUsers = rollbackUsers;
        this.blockList = blockList;
        this.excludedBlocks = excludedBlocks;
        this.excludedUsers = excludedUsers;
        this.actions = actions;
        this.entityActionFilter = entityActionFilter;
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
        this.outputMode = outputMode;
    }

    @Override
    public void run() {
        boolean summaryLookup = outputMode == LookupOutputMode.SUMMARY;
        if (summaryLookup && !SUMMARY_LOOKUP_ACTIVE.compareAndSet(false, true)) {
            Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.DATABASE_BUSY));
            return;
        }

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
            ConfigHandler.lookupRadius.put(player.getName(), radius);
            ConfigHandler.lookupOutputMode.put(player.getName(), outputMode == LookupOutputMode.COUNT ? LookupOutputMode.DETAIL : outputMode);

            if (connection != null) {
                Statement statement = connection.createStatement();
                if (summaryLookup) {
                    statement.setQueryTimeout(SUMMARY_QUERY_TIMEOUT_SECONDS);
                }
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

                    Set<UUID> loadedEntityUuids = Collections.emptySet();
                    Set<UUID> loadedEntityCandidates = Collections.emptySet();
                    boolean includeEntitySpawns = entityActionFilter.includesAnySpawn(actions, true);
                    boolean includeEntityContainers = entityContainerId != null || actions.contains(LookupActions.CONTAINER) || LookupActions.isInventoryLookup(actions) || actions.isEmpty();
                    if ((includeEntitySpawns || includeEntityContainers) && radius != null && finalLocation != null && finalLocation.getWorld() != null) {
                        Set<UUID> databaseCandidates = includeEntityContainers ? EntitySpawnStatement.loadActiveUuids(connection, finalLocation, radius) : EntitySpawnStatement.loadActiveUuids(connection, finalLocation, radius, timeStart, timeEnd);
                        EntitySpawnTracking.LoadedEntityRadius loadedEntities = EntitySpawnTracking.findLoadedEntities(finalLocation, radius, databaseCandidates);
                        loadedEntityUuids = loadedEntities.getInside();
                        loadedEntityCandidates = loadedEntities.getLoadedCandidates();
                    }

                    Long[] rowData = new Long[] { 0L, 0L, 0L, 0L, 0L };
                    long rowMax = (long) page * displayResults;
                    long pageStart = rowMax - displayResults;
                    long rows = 0L;
                    long recordRows = 0L;
                    boolean checkRows = true;
                    List<LookupSummaryRow> summaryRows = null;

                    if (typeLookup == 5 && page > 1) {
                        rowData = ConfigHandler.lookupRows.get(player.getName());
                        if (rowData == null || rowData.length < 5) {
                            rowData = new Long[] { 0L, 0L, 0L, 0L, 0L };
                        }
                        rows = rowData[4];
                        if (outputMode == LookupOutputMode.SUMMARY) {
                            recordRows = rowData[0];
                        }

                        if (pageStart < rows) {
                            checkRows = false;
                        }
                    }

                    if (checkRows) {
                        if (outputMode == LookupOutputMode.SUMMARY) {
                            if (pageStart == 0 && Lookup.supportsSummaryWindowFunctions(statement)) {
                                LookupSummaryPage summaryPage = Lookup.performSummaryLookupPage(statement, player, uuidList, userList, blockList, excludedBlocks, excludedUsers, actions, entityActionFilter, loadedEntityUuids, loadedEntityCandidates, finalLocation, radius, timeStart, timeEnd, (int) pageStart, displayResults, restrict_world, entityContainerId);
                                rows = summaryPage.getTotalRows();
                                summaryRows = summaryPage.getRows();
                            }
                            else {
                                rows = Lookup.countSummaryRows(statement, player, uuidList, userList, blockList, excludedBlocks, excludedUsers, actions, entityActionFilter, loadedEntityUuids, loadedEntityCandidates, finalLocation, radius, timeStart, timeEnd, restrict_world, entityContainerId);
                            }
                            if (rows > 0) {
                                recordRows = Lookup.countLookupRows(statement, player, uuidList, userList, blockList, excludedBlocks, excludedUsers, actions, entityActionFilter, messageFilters, loadedEntityUuids, loadedEntityCandidates, finalLocation, radius, rowData, timeStart, timeEnd, restrict_world, true, entityContainerId);
                                rowData[0] = recordRows;
                                rowData[1] = 0L;
                                rowData[2] = 0L;
                                rowData[3] = 0L;
                            }
                        }
                        else {
                            rows = Lookup.countLookupRows(statement, player, uuidList, userList, blockList, excludedBlocks, excludedUsers, actions, entityActionFilter, messageFilters, loadedEntityUuids, loadedEntityCandidates, finalLocation, radius, rowData, timeStart, timeEnd, restrict_world, true, entityContainerId);
                        }
                        rowData[4] = rows;
                        ConfigHandler.lookupRows.put(player.getName(), rowData);
                    }
                    if (outputMode == LookupOutputMode.COUNT) {
                        String row_format = NumberFormat.getInstance().format(rows);
                        Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.LOOKUP_ROWS_FOUND, row_format, (rows == 1 ? Selector.FIRST : Selector.SECOND)));
                    }
                    else if (outputMode == LookupOutputMode.SUMMARY && pageStart < rows) {
                        if (summaryRows == null) {
                            summaryRows = Lookup.performSummaryLookup(statement, player, uuidList, userList, blockList, excludedBlocks, excludedUsers, actions, entityActionFilter, loadedEntityUuids, loadedEntityCandidates, finalLocation, radius, timeStart, timeEnd, (int) pageStart, displayResults, restrict_world, entityContainerId);
                        }
                        outputSummary(connection, summaryRows, rows, recordRows);
                    }
                    else if (pageStart < rows) {
                        List<String[]> lookupList = Lookup.performPartialLookup(statement, player, uuidList, userList, blockList, excludedBlocks, excludedUsers, actions, entityActionFilter, messageFilters, loadedEntityUuids, loadedEntityCandidates, finalLocation, radius, rowData, timeStart, timeEnd, (int) pageStart, displayResults, restrict_world, true, entityContainerId);

                        Map<Integer, EntitySpawnRecord> entitySpawnRecords = Collections.emptyMap();
                        Map<UUID, Location> loadedEntityLocations = Collections.emptyMap();
                        Set<Integer> entitySpawnRowIds = new HashSet<>();
                        for (String[] data : lookupList) {
                            if (data.length > 7 && data[6] != null && data[7] != null && Integer.parseInt(data[7]) == LookupActions.ENTITY_SPAWN) {
                                entitySpawnRowIds.add(Integer.parseInt(data[6]));
                            }
                            if (data.length > 14 && data[13] != null && data[14] != null && Integer.parseInt(data[13]) == InventorySources.ENTITY_CONTAINER) {
                                entitySpawnRowIds.add(Integer.parseInt(data[14]));
                            }
                        }
                        if (!entitySpawnRowIds.isEmpty()) {
                            entitySpawnRecords = EntitySpawnStatement.loadLocationRecords(connection, entitySpawnRowIds);
                            Set<UUID> entitySpawnUuids = new HashSet<>();
                            for (EntitySpawnRecord record : entitySpawnRecords.values()) {
                                entitySpawnUuids.add(record.getUuid());
                            }
                            try {
                                loadedEntityLocations = EntitySpawnTracking.findLoadedLocations(entitySpawnUuids);
                            }
                            catch (Exception e) {
                                ErrorReporter.report(e);
                            }
                        }
                        Map<String[], EntityDisplayLocation> entityDisplayLocations = new IdentityHashMap<>();
                        for (String[] data : lookupList) {
                            EntityDisplayLocation displayLocation = resolveEntityDisplayLocation(data, entitySpawnRecords, loadedEntityLocations);
                            if (displayLocation != null) {
                                entityDisplayLocations.put(data, displayLocation);
                            }
                        }

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
                                EntityDisplayLocation entityLocation = entityDisplayLocations.get(data);
                                if (entityLocation != null) {
                                    wid = entityLocation.worldId;
                                    dataX = entityLocation.x;
                                    dataY = entityLocation.y;
                                    dataZ = entityLocation.z;
                                }
                                String rbd = ((Integer.parseInt(data[8]) == 2 || Integer.parseInt(data[8]) == 3) ? Color.STRIKETHROUGH : "");
                                String timeago = ChatUtils.getTimeSince(Integer.parseInt(time), unixtimestamp, true);
                                Material blockType = ItemUtils.itemFilter(MaterialUtils.getType(dtype), (Integer.parseInt(data[13]) == 0));
                                String dname = StringUtils.nameFilter(blockType.name().toLowerCase(Locale.ROOT), ddata);
                                byte[] metadata = data[11] == null ? null : data[11].getBytes(StandardCharsets.ISO_8859_1);
                                String tooltip = ItemUtils.getEnchantments(metadata, dtype, amount);
                                Integer itemId = ItemUtils.makeGivableItem(ItemUtils.getItemStack(metadata, dtype, amount));

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

                                String coordinateInfo = entityLocation == null ? "" : entityLocation.getOriginTooltip();
                                Chat.sendComponent(player, timeago + " " + tag + " " + Phrase.build(Phrase.LOOKUP_CONTAINER, Color.DARK_AQUA + rbd + dplayer + Color.WHITE + rbd, "x" + amount, ChatUtils.createTooltip(Color.DARK_AQUA + rbd + dname, tooltip) + coordinateInfo + ChatUtils.filterComponent(player.hasPermission("coreprotect.give"), ChatUtils.createGiveItemComponent(Color.GREY + "(↓)", command.getName(), itemId)) + Color.WHITE, selector));
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
                                boolean placedEntitySpawn = daction == LookupActions.ENTITY_SPAWN && EntitySpawnTracking.isPlacedEntityType(EntityUtils.getEntityType(dtype));
                                boolean placedEntityKill = daction == LookupActions.ENTITY_KILL && EntitySpawnTracking.isPlacedEntityType(EntityUtils.getEntityType(dtype));
                                int wid = Integer.parseInt(data[9]);
                                int amount = Integer.parseInt(data[10]);
                                EntityDisplayLocation entityLocation = entityDisplayLocations.get(data);
                                if (entityLocation != null) {
                                    wid = entityLocation.worldId;
                                    dataX = entityLocation.x;
                                    dataY = entityLocation.y;
                                    dataZ = entityLocation.z;
                                }
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
                                if ((daction == LookupActions.ENTITY_KILL || daction == LookupActions.ENTITY_SPAWN) && !actions.contains(LookupActions.ITEM) && amount == -1) {
                                    if (daction == LookupActions.ENTITY_KILL && dtype == 0) {
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
                                    Integer itemId = ItemUtils.makeGivableItem(ItemUtils.getItemStack(metadata, dtype, amount));

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

                                    Chat.sendComponent(player, timeago + " " + tag + " " + Phrase.build(phrase, Color.DARK_AQUA + rbd + dplayer + Color.WHITE + rbd, "x" + amount, ChatUtils.createTooltip(Color.DARK_AQUA + rbd + dname, tooltip) + ChatUtils.filterComponent(player.hasPermission("coreprotect.give"), ChatUtils.createGiveItemComponent(Color.GREY + "(↓)", command.getName(), itemId)) + Color.WHITE, selector));
                                    PluginChannelListener.getInstance().sendData(player, Integer.parseInt(time), phrase, selector, dplayer, dname, (tag.contains("+") ? 1 : -1), dataX, dataY, dataZ, wid, rbd, action.contains("container"), tag.contains("+"));
                                }
                                else {
                                    if (daction == LookupActions.ENTITY_SPAWN) {
                                        phrase = placedEntitySpawn ? Phrase.LOOKUP_BLOCK : Phrase.LOOKUP_ENTITY_SPAWN;
                                        selector = Selector.FIRST;
                                        tag = Color.GREEN + "+";
                                        action = placedEntitySpawn ? "a:block" : "a:spawn";
                                    }
                                    else if (daction == LookupActions.INTERACTION || daction == LookupActions.ENTITY_KILL) {
                                        if (placedEntityKill) {
                                            phrase = Phrase.LOOKUP_BLOCK;
                                            selector = Selector.SECOND;
                                            tag = Color.RED + "-";
                                            action = "a:block";
                                        }
                                        else {
                                            phrase = Phrase.LOOKUP_INTERACTION; // {clicked|killed}
                                            selector = (daction != LookupActions.ENTITY_KILL ? Selector.FIRST : Selector.SECOND);
                                            tag = (daction != LookupActions.ENTITY_KILL ? Color.WHITE + "-" : Color.RED + "-");
                                            action = (daction == LookupActions.INTERACTION ? "a:click" : "a:kill");
                                        }
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
                                String coordinates = ChatUtils.getCoordinates(command.getName(), wid, dataX, dataY, dataZ, true, true);
                                String coordinateInfo = entityLocation == null ? "" : entityLocation.getOriginTooltip();
                                Chat.sendComponent(player, Color.WHITE + leftPadding + Color.GREY + "^ " + coordinates + Color.GREY + Color.ITALIC + action + coordinateInfo);
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
            ErrorReporter.report(e);
        }
        finally {
            if (summaryLookup) {
                SUMMARY_LOOKUP_ACTIVE.set(false);
            }
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
            if (materialName.startsWith("minecraft:")) {
                materialName = materialName.substring("minecraft:".length());
            }

            long removedAmount = row.getRemovedAmount();
            long placedAmount = row.getPlacedAmount();
            long netAmount = row.getAmount();
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
