package net.coreprotect.command.lookup;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
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


import net.coreprotect.config.ConfigHandler;
import net.coreprotect.database.Database;
import net.coreprotect.database.Lookup;
import net.coreprotect.database.lookup.EntityInteractionLookup;
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
import net.coreprotect.model.lookup.EntityLookupContext;
import net.coreprotect.model.lookup.LookupCursor;
import net.coreprotect.model.lookup.LookupSession;
import net.coreprotect.model.lookup.StandardLookupRequest;
import net.coreprotect.model.lookup.LookupOutputMode;
import net.coreprotect.model.lookup.LookupPage;
import net.coreprotect.model.lookup.LookupRollbackState;
import net.coreprotect.model.lookup.LookupSummaryPage;
import net.coreprotect.model.lookup.LookupSummaryRow;
import net.coreprotect.utility.Chat;
import net.coreprotect.utility.ChatMessage;
import net.coreprotect.utility.ChatUtils;
import net.coreprotect.utility.Color;
import net.coreprotect.utility.EntityUtils;
import net.coreprotect.utility.EntitySpawnTracking;
import net.coreprotect.utility.ItemUtils;
import net.coreprotect.utility.MaterialUtils;
import net.coreprotect.utility.StringUtils;
import net.coreprotect.utility.WorldUtils;
import net.coreprotect.utility.ErrorReporter;
import net.coreprotect.utility.LookupThrottle;

public class StandardLookupThread implements Runnable {
    private static final int SUMMARY_QUERY_TIMEOUT_SECONDS = 30;
    private static final AtomicBoolean SUMMARY_LOOKUP_ACTIVE = new AtomicBoolean(false);

    private final CommandSender player;
    private final Command command;
    private final StandardLookupRequest request;
    private final LookupSession previousSession;

    public StandardLookupThread(CommandSender player, Command command, StandardLookupRequest request, LookupSession previousSession) {
        this.player = player;
        this.command = command;
        this.request = request;
        this.previousSession = previousSession;
    }

    @Override
    public void run() {
        List<String> rollbackUsers = request.filters.users;
        List<Object> blockList = request.filters.includedBlocks;
        Map<Object, Boolean> excludedBlocks = request.filters.excludedBlocks;
        List<String> excludedUsers = request.filters.excludedUsers;
        List<Integer> actions = request.filters.actions;
        EntityActionFilter entityActionFilter = request.filters.entityActions;
        List<String> messageFilters = request.filters.messages;
        Integer[] radius = request.scope.radius();
        long timeStart = request.scope.startTime;
        long timeEnd = request.scope.endTime;
        int page = request.page;
        int displayResults = request.pageSize;
        LookupOutputMode outputMode = request.outputMode;
        LookupRollbackState rollbackState = request.rollbackState;
        boolean summaryLookup = outputMode == LookupOutputMode.SUMMARY;
        if (summaryLookup && !SUMMARY_LOOKUP_ACTIVE.compareAndSet(false, true)) {
            Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.DATABASE_BUSY));
            return;
        }

        if (!LookupThrottle.tryAcquire(player.getName(), 50)) {
            if (summaryLookup) {
                SUMMARY_LOOKUP_ACTIVE.set(false);
            }
            Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.DATABASE_BUSY));
            return;
        }

        try (Connection connection = Database.getConnection(true)) {
            if (previousSession != null && (ConfigHandler.lookupSessions.get(player.getName()) != previousSession
                    || !Integer.valueOf(LookupSession.STANDARD_LOOKUP).equals(ConfigHandler.lookupType.get(player.getName())))) {
                sendLookupBusy();
                return;
            }
            int scopedWorldId = request.scope.explicitWorldId;
            String pendingWorldName = request.scope.pendingWorldName;
            String scopedWorldName = null;
            if (pendingWorldName != null) {
                if (connection == null) {
                    Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.DATABASE_BUSY));
                    return;
                }
                try {
                    int historicalWorldId = WorldUtils.findHistoricalWorldId(pendingWorldName);
                    if (historicalWorldId > 0) {
                        scopedWorldId = historicalWorldId;
                        scopedWorldName = pendingWorldName;
                    }
                }
                catch (SQLException e) {
                    Database.reportDatabaseFailure(e);
                    Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.DATABASE_BUSY));
                    return;
                }
                if (scopedWorldId <= 0) {
                    Chat.sendMessage(player, new ChatMessage(Phrase.build(Phrase.WORLD_NOT_FOUND, pendingWorldName)).build());
                    return;
                }
            }

            List<String> uuidList = new ArrayList<>();
            Integer entityContainerId = actions.contains(LookupActions.INSPECT_CONTAINER) ? ConfigHandler.lookupEntityContainer.get(player.getName()) : null;
            Location finalLocation = request.scope.location();
            boolean exists = false;
            if (connection == null) {
                sendLookupBusy();
                return;
            }
            try (Statement statement = connection.createStatement()) {
                if (summaryLookup) {
                    statement.setQueryTimeout(SUMMARY_QUERY_TIMEOUT_SECONDS);
                }
                String baduser = "";
                for (String check : rollbackUsers) {
                    if ((!check.equals("#global") && !check.equals("#container")) || actions.contains(LookupActions.USERNAME)) {
                        exists = UserStatement.findId(connection, check) >= 0;
                        if (!exists) {
                            baduser = check;
                            break;
                        }
                        else if (actions.contains(LookupActions.USERNAME)) {
                            String uuid = UserStatement.getUuid(connection, check);
                            if (uuid != null) {
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
                            exists = UserStatement.findId(connection, check) >= 0;
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
                    if (scopedWorldId > 0) {
                        restrict_world = true;
                        String worldName = scopedWorldName != null ? scopedWorldName : WorldUtils.getWorldName(scopedWorldId);
                        finalLocation = new Location(Bukkit.getServer().getWorld(worldName), request.scope.x, request.scope.y, request.scope.z);
                    }
                    else if (finalLocation != null) {
                        finalLocation = new Location(Bukkit.getServer().getWorld(WorldUtils.getWorldName(request.scope.worldId)), request.scope.x, request.scope.y, request.scope.z);
                    }

                    EntityLookupContext entityContext = EntityLookupContext.legacy(Collections.emptySet(), Collections.emptySet());
                    boolean includeEntitySpawns = entityActionFilter.includesAnySpawn(actions, true);
                    boolean includeEntityContainers = entityContainerId != null || actions.contains(LookupActions.CONTAINER) || LookupActions.isInventoryLookup(actions) || actions.isEmpty();
                    boolean includeEntityInteractions = actions.isEmpty() || actions.contains(LookupActions.INTERACTION);
                    if (!ConfigHandler.databaseType.isDuckDB() && (includeEntitySpawns || includeEntityContainers || includeEntityInteractions) && radius != null && finalLocation != null && finalLocation.getWorld() != null) {
                        Set<UUID> databaseCandidates = includeEntityContainers || includeEntityInteractions ? EntitySpawnStatement.loadActiveUuids(connection, finalLocation, radius) : EntitySpawnStatement.loadActiveUuids(connection, finalLocation, radius, timeStart, timeEnd);
                        EntitySpawnTracking.LoadedEntityRadius loadedEntities = EntitySpawnTracking.findLoadedEntities(finalLocation, radius, databaseCandidates);
                        entityContext = EntityLookupContext.legacy(loadedEntities.getInside(), loadedEntities.getLoadedCandidates());
                    }

                    Long[] rowData = new Long[] { 0L, 0L, 0L, 0L, 0L };
                    long rowMax = (long) page * displayResults;
                    long pageStart = rowMax - displayResults;
                    long rows = 0L;
                    long recordRows = 0L;
                    boolean checkRows = true;
                    LookupPage lookupPage = null;
                    List<LookupSummaryRow> summaryRows = null;
                    LookupCursor cursor = previousSession != null && outputMode == LookupOutputMode.DETAIL && ConfigHandler.databaseType.isColumnar()
                            ? previousSession.cursor : null;

                    if (previousSession != null && page > 1) {
                        rowData = previousSession.rowCounts();
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
                            rows = 0L;
                            recordRows = Lookup.countLookupRows(statement, player, uuidList, userList, blockList, excludedBlocks, excludedUsers, actions, entityActionFilter, messageFilters, entityContext, finalLocation, radius, rowData, timeStart, timeEnd, restrict_world, true, entityContainerId, rollbackState, scopedWorldId, true);
                            if (recordRows > 0 && pageStart == 0 && Lookup.supportsSummaryWindowFunctions(statement)) {
                                LookupSummaryPage summaryPage = Lookup.performSummaryLookupPage(statement, player, uuidList, userList, blockList, excludedBlocks, excludedUsers, actions, entityActionFilter, entityContext, finalLocation, radius, timeStart, timeEnd, (int) pageStart, displayResults, restrict_world, entityContainerId, rollbackState, scopedWorldId, true);
                                rows = summaryPage.getTotalRows();
                                summaryRows = summaryPage.getRows();
                            }
                            else if (recordRows > 0) {
                                rows = Lookup.countSummaryRows(statement, player, uuidList, userList, blockList, excludedBlocks, excludedUsers, actions, entityActionFilter, entityContext, finalLocation, radius, timeStart, timeEnd, restrict_world, entityContainerId, rollbackState, scopedWorldId, true);
                            }
                            rowData[0] = recordRows;
                            rowData[1] = 0L;
                            rowData[2] = 0L;
                            rowData[3] = 0L;
                        }
                        else {
                            if (pageStart == 0 && outputMode == LookupOutputMode.DETAIL && ConfigHandler.databaseType.isDuckDB()) {
                                lookupPage = Lookup.performDuckDBLookupPage(statement, player, uuidList, userList, blockList, excludedBlocks, excludedUsers, actions, entityActionFilter, messageFilters, entityContext, finalLocation, radius, rowData, timeStart, timeEnd, displayResults, restrict_world, true, entityContainerId, rollbackState, scopedWorldId, true);
                                rows = lookupPage.getTotalRows();
                            }
                            else {
                                rows = Lookup.countLookupRows(statement, player, uuidList, userList, blockList, excludedBlocks, excludedUsers, actions, entityActionFilter, messageFilters, entityContext, finalLocation, radius, rowData, timeStart, timeEnd, restrict_world, true, entityContainerId, rollbackState, scopedWorldId, true);
                            }
                        }
                        rowData[4] = rows;
                    }
                    if (lookupPage == null && outputMode == LookupOutputMode.DETAIL && ConfigHandler.databaseType.isColumnar() && pageStart < rows) {
                        lookupPage = Lookup.performLookupPage(statement, player, uuidList, userList, blockList, excludedBlocks, excludedUsers, actions, entityActionFilter, messageFilters, entityContext, finalLocation, radius, rowData, timeStart, timeEnd, (int) pageStart, displayResults, rows, cursor, restrict_world, true, entityContainerId, rollbackState, scopedWorldId, true);
                    }
                    if (lookupPage != null) {
                        cursor = lookupPage.getNextCursor();
                    }
                    if (outputMode == LookupOutputMode.COUNT) {
                        outputRowCount(rows);
                    }
                    else if (outputMode == LookupOutputMode.SUMMARY && (pageStart < rows || (pageStart == 0 && recordRows > 0))) {
                        if (summaryRows == null) {
                            summaryRows = rows > 0
                                    ? Lookup.performSummaryLookup(statement, player, uuidList, userList, blockList, excludedBlocks, excludedUsers, actions, entityActionFilter, entityContext, finalLocation, radius, timeStart, timeEnd, (int) pageStart, displayResults, restrict_world, entityContainerId, rollbackState, scopedWorldId, true)
                                    : Collections.emptyList();
                        }
                        outputSummary(connection, summaryRows, rows, recordRows);
                    }
                    else if (pageStart < rows) {
                        List<String[]> lookupList = lookupPage == null
                                ? Lookup.performPartialLookup(statement, player, uuidList, userList, blockList, excludedBlocks, excludedUsers, actions, entityActionFilter, messageFilters, entityContext, finalLocation, radius, rowData, timeStart, timeEnd, (int) pageStart, displayResults, restrict_world, true, entityContainerId, rollbackState, scopedWorldId, true)
                                : lookupPage.getRows();
                        Map<Integer, EntitySpawnRecord> entitySpawnRecords = Collections.emptyMap();
                        Map<UUID, Location> loadedEntityLocations = Collections.emptyMap();
                        Set<Integer> entitySpawnRowIds = new HashSet<>();
                        for (String[] data : lookupList) {
                            int trackingRowId = trackingRowId(data);
                            if (trackingRowId != 0) {
                                entitySpawnRowIds.add(trackingRowId);
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
                                int time = Integer.parseInt(data[0]);
                                String dplayer = data[1];
                                String message = data[2];
                                String timeago = ChatUtils.getTimeSince(time, unixtimestamp, true);
                                Chat.sendComponent(player, timeago + " " + Color.WHITE + "- " + Color.DARK_AQUA + dplayer + ": " + Color.WHITE, message);
                                if (PluginChannelHandshakeListener.getInstance().isPluginChannelPlayer(player)) {
                                    int wid = Integer.parseInt(data[3]);
                                    int dataX = Integer.parseInt(data[4]);
                                    int dataY = Integer.parseInt(data[5]);
                                    int dataZ = Integer.parseInt(data[6]);
                                    PluginChannelListener.getInstance().sendMessageData(player, time, dplayer, message, false, dataX, dataY, dataZ, wid);
                                }
                            }
                        }
                        else if (actions.contains(LookupActions.SESSION)) {
                            for (String[] data : lookupList) {
                                int time = Integer.parseInt(data[0]);
                                String dplayer = data[1];
                                int wid = Integer.parseInt(data[2]);
                                int dataX = Integer.parseInt(data[3]);
                                int dataY = Integer.parseInt(data[4]);
                                int dataZ = Integer.parseInt(data[5]);
                                int action = Integer.parseInt(data[6]);
                                String timeago = ChatUtils.getTimeSince(time, unixtimestamp, true);
                                String leftPadding = computeLeftPadding(time, unixtimestamp);

                                String tag = (action != SessionActions.LOGOUT ? Color.GREEN + "+" : Color.RED + "-");
                                Chat.sendComponent(player, timeago + " " + tag + " " + Color.DARK_AQUA + Phrase.build(Phrase.LOOKUP_LOGIN, Color.DARK_AQUA + dplayer + Color.WHITE, (action != SessionActions.LOGOUT ? Selector.FIRST : Selector.SECOND)));
                                Chat.sendComponent(player, Color.WHITE + leftPadding + Color.GREY + "^ " + ChatUtils.getCoordinates(command.getName(), wid, dataX, dataY, dataZ, true, true) + "");
                                PluginChannelListener.getInstance().sendInfoData(player, time, Phrase.LOOKUP_LOGIN, (action != SessionActions.LOGOUT ? Selector.FIRST : Selector.SECOND), dplayer, -1, dataX, dataY, dataZ, wid);
                            }
                        }
                        else if (actions.contains(LookupActions.USERNAME)) {
                            Map<String, String> namesByUuid = new HashMap<>();
                            for (String[] data : lookupList) {
                                int time = Integer.parseInt(data[0]);
                                String uuid = data[1];
                                if (!namesByUuid.containsKey(uuid)) {
                                    namesByUuid.put(uuid, UserStatement.getNameByUuid(connection, uuid));
                                }
                                String user = namesByUuid.get(uuid);
                                String username = data[2];
                                String timeago = ChatUtils.getTimeSince(time, unixtimestamp, true);
                                Chat.sendComponent(player, timeago + " " + Color.WHITE + "- " + Phrase.build(Phrase.LOOKUP_USERNAME, Color.DARK_AQUA + user + Color.WHITE, Color.DARK_AQUA + username + Color.WHITE));
                                PluginChannelListener.getInstance().sendUsernameData(player, time, user, username);
                            }
                        }
                        else if (actions.contains(LookupActions.SIGN)) {
                            for (String[] data : lookupList) {
                                int time = Integer.parseInt(data[0]);
                                String dplayer = data[1];
                                int wid = Integer.parseInt(data[2]);
                                int dataX = Integer.parseInt(data[3]);
                                int dataY = Integer.parseInt(data[4]);
                                int dataZ = Integer.parseInt(data[5]);
                                String message = data[6];
                                String timeago = ChatUtils.getTimeSince(time, unixtimestamp, true);
                                String leftPadding = computeLeftPadding(time, unixtimestamp);

                                Chat.sendComponent(player, timeago + " " + Color.WHITE + "- " + Color.DARK_AQUA + dplayer + ": " + Color.WHITE, message);
                                Chat.sendComponent(player, Color.WHITE + leftPadding + Color.GREY + "^ " + ChatUtils.getCoordinates(command.getName(), wid, dataX, dataY, dataZ, true, true) + "");
                                PluginChannelListener.getInstance().sendMessageData(player, time, dplayer, message, true, dataX, dataY, dataZ, wid);
                            }
                        }
                        else if (LookupActions.isInventoryLookup(actions)) {
                            for (String[] data : lookupList) {
                                int time = Integer.parseInt(data[0]);
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
                                String rbd = MaterialUtils.rolledBack(Integer.parseInt(data[8]), true) == 1 ? Color.STRIKETHROUGH : "";
                                String timeago = ChatUtils.getTimeSince(time, unixtimestamp, true);
                                Material blockType = ItemUtils.itemFilter(MaterialUtils.getType(dtype), (Integer.parseInt(data[13]) == 0));
                                String dname = StringUtils.nameFilter(blockType.name().toLowerCase(Locale.ROOT), ddata);
                                byte[] metadata = data[11] == null ? null : data[11].getBytes(StandardCharsets.ISO_8859_1);
                                String tooltip = ItemUtils.getEnchantments(metadata, dtype, amount);
                                Integer itemId = ItemUtils.makeGivableItem(ItemUtils.getItemStack(metadata, dtype, amount));

                                String selector = Selector.FIRST;
                                String tag = Color.WHITE + "-";
                                boolean added = ItemTransactionActions.getInventoryActionId(daction) == ItemTransactionActions.ADD;
                                selector = added ? Selector.FIRST : Selector.SECOND;
                                tag = added ? Color.GREEN + "+" : Color.RED + "-";

                                String coordinateInfo = entityLocation == null ? "" : entityLocation.getOriginTooltip();
                                Chat.sendComponent(player, timeago + " " + tag + " " + Phrase.build(Phrase.LOOKUP_CONTAINER, Color.DARK_AQUA + rbd + dplayer + Color.WHITE + rbd, "x" + amount, ChatUtils.createTooltip(Color.DARK_AQUA + rbd + dname, tooltip) + coordinateInfo + ChatUtils.filterComponent(player.hasPermission("coreprotect.give"), ChatUtils.createGiveItemComponent(Color.GREY + "(↓)", command.getName(), itemId)) + Color.WHITE, selector));
                                PluginChannelListener.getInstance().sendData(player, time, Phrase.LOOKUP_CONTAINER, selector, dplayer, dname, amount, dataX, dataY, dataZ, wid, rbd, true, tag.contains("+"));
                            }
                        }
                        else {
                            for (String[] data : lookupList) {
                                int drb = Integer.parseInt(data[8]);
                                String rbd = "";
                                if (MaterialUtils.rolledBack(drb, actions.contains(LookupActions.ITEM)) == 1) {
                                    rbd = Color.STRIKETHROUGH;
                                }

                                int time = Integer.parseInt(data[0]);
                                String dplayer = data[1];
                                int dataX = Integer.parseInt(data[2]);
                                int dataY = Integer.parseInt(data[3]);
                                int dataZ = Integer.parseInt(data[4]);
                                int dtype = Integer.parseInt(data[5]);
                                int ddata = Integer.parseInt(data[6]);
                                int daction = Integer.parseInt(data[7]);
                                boolean entityInteraction = data[13] != null && Integer.parseInt(data[13]) == InventorySources.ENTITY_INTERACTION;
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

                                String timeago = ChatUtils.getTimeSince(time, unixtimestamp, true);
                                String leftPadding = computeLeftPadding(time, unixtimestamp);

                                String dname = "";
                                boolean isPlayer = false;
                                if (entityInteraction) {
                                    dname = EntityInteractionLookup.entityName(dtype);
                                }
                                else if ((daction == LookupActions.ENTITY_KILL || daction == LookupActions.ENTITY_SPAWN) && !actions.contains(LookupActions.ITEM) && amount == -1) {
                                    if (daction == LookupActions.ENTITY_KILL && dtype == 0) {
                                        dname = UserStatement.getName(connection, ddata);
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
                                if (actions.contains(LookupActions.CONTAINER) || actions.contains(LookupActions.INSPECT_CONTAINER) || actions.contains(LookupActions.ITEM) || amount > -1) {
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
                                        boolean added = daction == ItemTransactionActions.ADD || (daction != ItemTransactionActions.REMOVE
                                                && ItemTransactionActions.getInventoryActionId(daction) == ItemTransactionActions.ADD);
                                        selector = added ? Selector.FIRST : Selector.SECOND;
                                        tag = added ? Color.GREEN + "+" : Color.RED + "-";
                                        action = "a:container";
                                    }

                                    Chat.sendComponent(player, timeago + " " + tag + " " + Phrase.build(phrase, Color.DARK_AQUA + rbd + dplayer + Color.WHITE + rbd, "x" + amount, ChatUtils.createTooltip(Color.DARK_AQUA + rbd + dname, tooltip) + ChatUtils.filterComponent(player.hasPermission("coreprotect.give"), ChatUtils.createGiveItemComponent(Color.GREY + "(↓)", command.getName(), itemId)) + Color.WHITE, selector));
                                    PluginChannelListener.getInstance().sendData(player, time, phrase, selector, dplayer, dname, amount, dataX, dataY, dataZ, wid, rbd, action.contains("container"), tag.contains("+"));
                                }
                                else {
                                    if (entityInteraction) {
                                        phrase = Phrase.LOOKUP_ENTITY_INTERACTION;
                                        selector = EntityInteractionLookup.actionSelector(ddata);
                                        action = "a:click";
                                    }
                                    else if (daction == LookupActions.ENTITY_SPAWN) {
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
                                    PluginChannelListener.getInstance().sendData(player, time, phrase, selector, dplayer, dname, amount, dataX, dataY, dataZ, wid, rbd, false, tag.contains("+"));
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
                    else if (rows > 0 || (outputMode == LookupOutputMode.SUMMARY && recordRows > 0)) {
                        Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.NO_RESULTS_PAGE, Selector.FIRST));
                    }
                    else {
                        Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.NO_RESULTS));
                    }
                    saveSession(scopedWorldId, rowData, cursor);
                }
                else {
                    Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.USER_NOT_FOUND, baduser));
                }
            }
        }
        catch (Lookup.QueryFailure failure) {
            if (failure.getCause() != null) {
                Database.reportDatabaseFailure(failure.getCause());
            }
            sendLookupBusy();
        }
        catch (Exception failure) {
            if (failure instanceof SQLException) {
                Database.reportDatabaseFailure(failure);
            }
            else {
                ErrorReporter.report(failure);
            }
            sendLookupBusy();
        }
        finally {
            if (summaryLookup) {
                SUMMARY_LOOKUP_ACTIVE.set(false);
            }
            LookupThrottle.release(player.getName());
        }
    }

    private void sendLookupBusy() {
        Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.DATABASE_BUSY));
    }

    private void saveSession(int scopedWorldId, Long[] rowData, LookupCursor cursor) {
        String name = player.getName();
        LookupSession session = new LookupSession(request.forSession(scopedWorldId), rowData, cursor);
        if (previousSession == null) {
            ConfigHandler.lookupSessions.put(name, session);
        }
        else if (!ConfigHandler.lookupSessions.replace(name, previousSession, session)) {
            return;
        }
        ConfigHandler.lookupEntityInteraction.remove(name);
        if (!request.filters.actions.contains(LookupActions.INSPECT_CONTAINER)) {
            ConfigHandler.lookupEntityContainer.remove(name);
        }
        ConfigHandler.lookupType.put(name, LookupSession.STANDARD_LOOKUP);
    }

    private void outputRowCount(long rows) {
        String formattedRows = NumberFormat.getInstance().format(rows);
        Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.LOOKUP_ROWS_FOUND, formattedRows, rows == 1 ? Selector.FIRST : Selector.SECOND));
    }

    private void outputSummary(Connection connection, List<LookupSummaryRow> summaryRows, long totalRows, long recordRows) {
        if (summaryRows.isEmpty()) {
            if (request.page == 1 && recordRows > 0) {
                outputRowCount(recordRows);
            }
            else {
                Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.NO_RESULTS_PAGE, Selector.FIRST));
            }
            return;
        }

        NumberFormat numberFormat = NumberFormat.getInstance();
        String rowsFound = Phrase.build(Phrase.LOOKUP_ROWS_FOUND, numberFormat.format(recordRows), recordRows == 1 ? Selector.FIRST : Selector.SECOND);
        Chat.sendMessage(player, Color.WHITE + "----- " + Color.DARK_AQUA + "CoreProtect" + Color.WHITE + " | " + Color.DARK_AQUA + rowsFound + Color.WHITE + " -----");
        for (LookupSummaryRow row : summaryRows) {
            String userName = UserStatement.getName(connection, row.getUserId());
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

        if (totalRows > request.pageSize) {
            int totalPages = (int) Math.ceil(totalRows / (request.pageSize + 0.0));
            Chat.sendComponent(player, ChatUtils.getPageNavigation(command.getName(), request.page, totalPages));
        }
    }

    private static String computeLeftPadding(int time, int now) {
        String formatted = ChatUtils.getTimeSince(time, now, false);
        int digits = 0;
        for (int i = 0; i < formatted.length(); i++) {
            char character = formatted.charAt(i);
            if (character >= '0' && character <= '9') {
                digits++;
            }
        }
        int width = 50 + digits * 6;
        return width % 4 == 0 ? " ".repeat(width / 4) : Color.BOLD + " ".repeat(10) + Color.WHITE + " ".repeat((width - 50) / 4);
    }

    private static int trackingRowId(String[] data) {
        if (data.length > 14 && data[13] != null && data[14] != null) {
            int source = Integer.parseInt(data[13]);
            if (source == InventorySources.ENTITY_CONTAINER || source == InventorySources.ENTITY_INTERACTION) {
                return Integer.parseInt(data[14]);
            }
        }
        if (data.length > 7 && data[6] != null && data[7] != null && Integer.parseInt(data[7]) == LookupActions.ENTITY_SPAWN) {
            return Integer.parseInt(data[6]);
        }
        return 0;
    }

    private static EntityDisplayLocation resolveEntityDisplayLocation(String[] data, Map<Integer, EntitySpawnRecord> records, Map<UUID, Location> loadedLocations) {
        int trackingRowId = trackingRowId(data);
        if (trackingRowId == 0 || data.length <= 14) {
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

        boolean interactionOrigin = data[13] != null && Integer.parseInt(data[13]) == InventorySources.ENTITY_INTERACTION;
        return new EntityDisplayLocation(currentWorldId, currentX, currentY, currentZ, Integer.parseInt(data[9]), Integer.parseInt(data[2]), Integer.parseInt(data[3]), Integer.parseInt(data[4]), interactionOrigin);
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
        private final boolean interactionOrigin;

        private EntityDisplayLocation(int worldId, int x, int y, int z, int originWorldId, int originX, int originY, int originZ, boolean interactionOrigin) {
            this.worldId = worldId;
            this.x = x;
            this.y = y;
            this.z = z;
            this.originWorldId = originWorldId;
            this.originX = originX;
            this.originY = originY;
            this.originZ = originZ;
            this.interactionOrigin = interactionOrigin;
        }

        private String getOriginTooltip() {
            if (worldId == originWorldId && x == originX && y == originY && z == originZ) {
                return "";
            }
            Phrase originPhrase = interactionOrigin ? Phrase.LOOKUP_ENTITY_INTERACTION_ORIGIN : Phrase.LOOKUP_ENTITY_ORIGIN;
            return ChatUtils.getCoordinateTooltip(originWorldId, originX, originY, originZ, Phrase.build(originPhrase), true);
        }
    }
}
