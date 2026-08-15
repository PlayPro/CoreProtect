package net.coreprotect.database;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EntityType;

import net.coreprotect.bukkit.BukkitAdapter;
import net.coreprotect.config.Config;
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.consumer.Consumer;
import net.coreprotect.consumer.Queue;
import net.coreprotect.database.statement.EntitySpawnStatement;
import net.coreprotect.database.statement.UserStatement;
import net.coreprotect.listener.channel.PluginChannelHandshakeListener;
import net.coreprotect.model.action.EntityActionFilter;
import net.coreprotect.model.action.LookupActions;
import net.coreprotect.model.action.SignActions;
import net.coreprotect.model.item.InventorySources;
import net.coreprotect.model.item.ItemTransactionActions;
import net.coreprotect.model.lookup.EntityLookupContext;
import net.coreprotect.model.lookup.LookupCursor;
import net.coreprotect.model.lookup.LookupRollbackState;
import net.coreprotect.utility.EntitySpawnTracking;
import net.coreprotect.utility.EntityUtils;
import net.coreprotect.utility.ErrorReporter;
import net.coreprotect.utility.DatabaseUtils;
import net.coreprotect.utility.MaterialUtils;
import net.coreprotect.utility.WorldUtils;

public class LookupRaw extends Queue {

    protected static List<Object[]> performLookupRaw(Statement statement, CommandSender user, List<String> checkUuids, List<String> checkUsers, List<Object> restrictList, Map<Object, Boolean> excludeList, List<String> excludeUserList, List<Integer> actionList, Location location, Integer[] radius, Long[] rowData, long startTime, long endTime, int limitOffset, int limitCount, boolean restrictWorld, boolean lookup) {
        return performLookupRaw(statement, user, checkUuids, checkUsers, restrictList, excludeList, excludeUserList, actionList, Collections.emptyList(), location, radius, rowData, startTime, endTime, limitOffset, limitCount, restrictWorld, lookup);
    }

    protected static List<Object[]> performLookupRaw(Statement statement, CommandSender user, List<String> checkUuids, List<String> checkUsers, List<Object> restrictList, Map<Object, Boolean> excludeList, List<String> excludeUserList, List<Integer> actionList, List<String> messageFilters, Location location, Integer[] radius, Long[] rowData, long startTime, long endTime, int limitOffset, int limitCount, boolean restrictWorld, boolean lookup) {
        return performLookupRaw(statement, user, checkUuids, checkUsers, restrictList, excludeList, excludeUserList, actionList, messageFilters, Collections.emptySet(), Collections.emptySet(), location, radius, rowData, startTime, endTime, limitOffset, limitCount, restrictWorld, lookup);
    }

    protected static List<Object[]> performLookupRaw(Statement statement, CommandSender user, List<String> checkUuids, List<String> checkUsers, List<Object> restrictList, Map<Object, Boolean> excludeList, List<String> excludeUserList, List<Integer> actionList, List<String> messageFilters, Set<UUID> loadedEntityUuids, Set<UUID> loadedEntityCandidates, Location location, Integer[] radius, Long[] rowData, long startTime, long endTime, int limitOffset, int limitCount, boolean restrictWorld, boolean lookup) {
        return performLookupRaw(statement, user, checkUuids, checkUsers, restrictList, excludeList, excludeUserList, actionList, EntityActionFilter.DEFAULT, messageFilters, loadedEntityUuids, loadedEntityCandidates, location, radius, rowData, startTime, endTime, limitOffset, limitCount, restrictWorld, lookup);
    }

    protected static List<Object[]> performLookupRaw(Statement statement, CommandSender user, List<String> checkUuids, List<String> checkUsers, List<Object> restrictList, Map<Object, Boolean> excludeList, List<String> excludeUserList, List<Integer> actionList, EntityActionFilter entityActionFilter, List<String> messageFilters, Set<UUID> loadedEntityUuids, Set<UUID> loadedEntityCandidates, Location location, Integer[] radius, Long[] rowData, long startTime, long endTime, int limitOffset, int limitCount, boolean restrictWorld, boolean lookup) {
        return performLookupRaw(statement, user, checkUuids, checkUsers, restrictList, excludeList, excludeUserList, actionList, entityActionFilter, messageFilters, loadedEntityUuids, loadedEntityCandidates, location, radius, rowData, startTime, endTime, limitOffset, limitCount, restrictWorld, lookup, null);
    }

    protected static List<Object[]> performLookupRaw(Statement statement, CommandSender user, List<String> checkUuids, List<String> checkUsers, List<Object> restrictList, Map<Object, Boolean> excludeList, List<String> excludeUserList, List<Integer> actionList, EntityActionFilter entityActionFilter, List<String> messageFilters, Set<UUID> loadedEntityUuids, Set<UUID> loadedEntityCandidates, Location location, Integer[] radius, Long[] rowData, long startTime, long endTime, int limitOffset, int limitCount, boolean restrictWorld, boolean lookup, Integer entityContainerId) {
        return performLookupRaw(statement, user, checkUuids, checkUsers, restrictList, excludeList, excludeUserList, actionList, entityActionFilter, messageFilters, loadedEntityUuids, loadedEntityCandidates, location, radius, rowData, startTime, endTime, limitOffset, limitCount, restrictWorld, lookup, entityContainerId, LookupRollbackState.ANY);
    }

    protected static List<Object[]> performLookupRaw(Statement statement, CommandSender user, List<String> checkUuids, List<String> checkUsers, List<Object> restrictList, Map<Object, Boolean> excludeList, List<String> excludeUserList, List<Integer> actionList, EntityActionFilter entityActionFilter, List<String> messageFilters, Set<UUID> loadedEntityUuids, Set<UUID> loadedEntityCandidates, Location location, Integer[] radius, Long[] rowData, long startTime, long endTime, int limitOffset, int limitCount, boolean restrictWorld, boolean lookup, Integer entityContainerId, LookupRollbackState rollbackState) {
        EntityLookupContext entityContext = EntityLookupContext.legacy(loadedEntityUuids, loadedEntityCandidates);
        return performLookupRaw(statement, user, checkUuids, checkUsers, restrictList, excludeList, excludeUserList, actionList, entityActionFilter, messageFilters, entityContext, location, radius, rowData, startTime, endTime, limitOffset, limitCount, restrictWorld, lookup, entityContainerId, rollbackState, null, true);
    }

    static RawLookupPage performDuckDBLookupPage(Statement statement, CommandSender user, List<String> checkUuids, List<String> checkUsers, List<Object> restrictList, Map<Object, Boolean> excludeList, List<String> excludeUserList, List<Integer> actionList, EntityActionFilter entityActionFilter, List<String> messageFilters, Set<UUID> loadedEntityUuids, Set<UUID> loadedEntityCandidates, Location location, Integer[] radius, Long[] rowData, long startTime, long endTime, int limitCount, boolean restrictWorld, boolean lookup, Integer entityContainerId, LookupRollbackState rollbackState) {
        EntityLookupContext entityContext = EntityLookupContext.legacy(loadedEntityUuids, loadedEntityCandidates);
        return performDuckDBLookupPage(statement, user, checkUuids, checkUsers, restrictList, excludeList, excludeUserList, actionList, entityActionFilter, messageFilters, entityContext, location, radius, rowData, startTime, endTime, limitCount, restrictWorld, lookup, entityContainerId, rollbackState);
    }

    static RawLookupPage performDuckDBLookupPage(Statement statement, CommandSender user, List<String> checkUuids, List<String> checkUsers, List<Object> restrictList, Map<Object, Boolean> excludeList, List<String> excludeUserList, List<Integer> actionList, EntityActionFilter entityActionFilter, List<String> messageFilters, EntityLookupContext entityContext, Location location, Integer[] radius, Long[] rowData, long startTime, long endTime, int limitCount, boolean restrictWorld, boolean lookup, Integer entityContainerId, LookupRollbackState rollbackState) {
        return performDuckDBLookupPage(statement, user, checkUuids, checkUsers, restrictList, excludeList, excludeUserList, actionList, entityActionFilter, messageFilters, entityContext, location, radius, rowData, startTime, endTime, 0, limitCount, -1L, null, restrictWorld, lookup, entityContainerId, rollbackState);
    }

    static RawLookupPage performDuckDBLookupPage(Statement statement, CommandSender user, List<String> checkUuids, List<String> checkUsers, List<Object> restrictList, Map<Object, Boolean> excludeList, List<String> excludeUserList, List<Integer> actionList, EntityActionFilter entityActionFilter, List<String> messageFilters, EntityLookupContext entityContext, Location location, Integer[] radius, Long[] rowData, long startTime, long endTime, int limitOffset, int limitCount, long knownTotalRows, LookupCursor cursor, boolean restrictWorld, boolean lookup, Integer entityContainerId, LookupRollbackState rollbackState) {
        if (!ConfigHandler.databaseType.isDuckDB()) {
            throw new IllegalStateException("DuckDB lookup pages require DuckDB");
        }

        boolean paused = false;
        try {
            while (Consumer.isPaused && !Consumer.isPersistenceHalted()) {
                Thread.sleep(1);
            }
            Consumer.isPaused = true;
            paused = true;

            Map<Integer, List<Long>> pageRows = new HashMap<>();
            long totalRows = Math.max(knownTotalRows, 0L);
            long cursorTime = 0L;
            long cursorRowId = 0L;
            int cursorSource = 0;
            boolean orderByTime = false;
            boolean foundPageRow = false;
            try (ResultSet results = rawLookupResultSet(statement, user, checkUuids, checkUsers, restrictList, excludeList, excludeUserList, actionList, entityActionFilter, messageFilters, entityContext, location, radius, rowData, startTime, endTime, 0, limitCount, restrictWorld, lookup, false, entityContainerId, false, false, false, rollbackState, null, true, limitOffset, knownTotalRows, cursor)) {
                boolean first = true;
                while (results.next()) {
                    if (first) {
                        totalRows = results.getLong("total_count");
                        if (knownTotalRows < 0L && rowData != null) {
                            for (int source = 0; source < rowData.length && source <= InventorySources.ENTITY_INTERACTION; source++) {
                                rowData[source] = results.getLong("count_" + source);
                            }
                        }
                        orderByTime = results.getBoolean("order_by_time");
                        first = false;
                    }
                    Object sourceValue = results.getObject("tbl");
                    Object rowIdValue = results.getObject("id");
                    if (sourceValue instanceof Number && rowIdValue instanceof Number) {
                        int source = ((Number) sourceValue).intValue();
                        long rowId = ((Number) rowIdValue).longValue();
                        pageRows.computeIfAbsent(source, ignored -> new ArrayList<>()).add(rowId);
                        cursorSource = source;
                        cursorRowId = rowId;
                        cursorTime = results.getLong("sort_time");
                        foundPageRow = true;
                    }
                }
            }

            if (totalRows == 0L || pageRows.isEmpty()) {
                return new RawLookupPage(totalRows, Collections.emptyList(), null);
            }
            List<Object[]> rows = performLookupRaw(statement, user, checkUuids, checkUsers, restrictList, excludeList, excludeUserList, actionList, entityActionFilter, messageFilters, entityContext, location, radius, rowData, startTime, endTime, -1, -1, restrictWorld, lookup, entityContainerId, rollbackState, pageRows, false);
            int currentPage = limitCount > 0 ? (limitOffset / limitCount) + 1 : 1;
            LookupCursor nextCursor = foundPageRow ? new LookupCursor(currentPage + 1, limitCount, cursorTime, cursorSource, cursorRowId, orderByTime) : null;
            return new RawLookupPage(totalRows, rows == null ? Collections.emptyList() : rows, nextCursor);
        }
        catch (Exception exception) {
            ErrorReporter.report(exception);
            return new RawLookupPage(knownTotalRows > 0L ? knownTotalRows : 0L, Collections.emptyList(), null);
        }
        finally {
            if (paused && !Consumer.isPersistenceHalted()) {
                Consumer.isPaused = false;
            }
        }
    }

    protected static List<Object[]> performLookupRaw(Statement statement, CommandSender user, List<String> checkUuids, List<String> checkUsers, List<Object> restrictList, Map<Object, Boolean> excludeList, List<String> excludeUserList, List<Integer> actionList, EntityActionFilter entityActionFilter, List<String> messageFilters, EntityLookupContext entityContext, Location location, Integer[] radius, Long[] rowData, long startTime, long endTime, int limitOffset, int limitCount, boolean restrictWorld, boolean lookup, Integer entityContainerId, LookupRollbackState rollbackState) {
        return performLookupRaw(statement, user, checkUuids, checkUsers, restrictList, excludeList, excludeUserList, actionList, entityActionFilter, messageFilters, entityContext, location, radius, rowData, startTime, endTime, limitOffset, limitCount, restrictWorld, lookup, entityContainerId, rollbackState, null, true);
    }

    private static List<Object[]> performLookupRaw(Statement statement, CommandSender user, List<String> checkUuids, List<String> checkUsers, List<Object> restrictList, Map<Object, Boolean> excludeList, List<String> excludeUserList, List<Integer> actionList, EntityActionFilter entityActionFilter, List<String> messageFilters, EntityLookupContext entityContext, Location location, Integer[] radius, Long[] rowData, long startTime, long endTime, int limitOffset, int limitCount, boolean restrictWorld, boolean lookup, Integer entityContainerId, LookupRollbackState rollbackState, Map<Integer, List<Long>> pageRows, boolean managePause) {
        List<Object[]> list = new ArrayList<>();
        List<Integer> invalidRollbackActions = new ArrayList<>();
        invalidRollbackActions.add(LookupActions.INTERACTION);
        if (!entityActionFilter.includesAnySpawn(actionList, Config.getGlobal().ROLLBACK_ENTITIES)) {
            invalidRollbackActions.add(LookupActions.ENTITY_SPAWN);
        }

        if (!entityActionFilter.includesAnyKill(actionList, Config.getGlobal().ROLLBACK_ENTITIES)) {
            invalidRollbackActions.add(LookupActions.ENTITY_KILL);
        }

        if (LookupActions.isInventoryLookup(actionList)) {
            invalidRollbackActions.clear();
        }

        boolean paused = false;
        try {
            while (managePause && Consumer.isPaused && !Consumer.isPersistenceHalted()) {
                Thread.sleep(1);
            }
            if (managePause) {
                Consumer.isPaused = true;
                paused = true;
            }

            ResultSet results = rawLookupResultSet(statement, user, checkUuids, checkUsers, restrictList, excludeList, excludeUserList, actionList, entityActionFilter, messageFilters, entityContext, location, radius, rowData, startTime, endTime, limitOffset, limitCount, restrictWorld, lookup, false, entityContainerId, false, false, false, rollbackState, pageRows, false);
            if (results == null) {
                return null;
            }

            while (results.next()) {
                if (actionList.contains(LookupActions.CHAT) || actionList.contains(LookupActions.COMMAND)) {
                    long resultId = results.getLong("id");
                    int resultTime = results.getInt("time");
                    int resultUserId = results.getInt("user");
                    String resultMessage = results.getString("message");

                    Object[] dataArray = new Object[] { resultId, resultTime, resultUserId, resultMessage };
                    if (PluginChannelHandshakeListener.getInstance().isPluginChannelPlayer(user)) {
                        int resultWorldId = results.getInt("wid");
                        int resultX = results.getInt("x");
                        int resultY = results.getInt("y");
                        int resultZ = results.getInt("z");
                        dataArray = new Object[] { resultId, resultTime, resultUserId, resultMessage, resultWorldId, resultX, resultY, resultZ };
                    }
                    list.add(dataArray);
                }
                else if (actionList.contains(LookupActions.SESSION)) {
                    long resultId = results.getLong("id");
                    int resultTime = results.getInt("time");
                    int resultUserId = results.getInt("user");
                    int resultWorldId = results.getInt("wid");
                    int resultX = results.getInt("x");
                    int resultY = results.getInt("y");
                    int resultZ = results.getInt("z");
                    int resultAction = results.getInt("action");

                    Object[] dataArray = new Object[] { resultId, resultTime, resultUserId, resultWorldId, resultX, resultY, resultZ, resultAction };
                    list.add(dataArray);
                }
                else if (actionList.contains(LookupActions.USERNAME)) {
                    long resultId = results.getLong("id");
                    int resultTime = results.getInt("time");
                    String resultUuid = results.getString("uuid");
                    String resultUser = results.getString("user");

                    Object[] dataArray = new Object[] { resultId, resultTime, resultUuid, resultUser };
                    list.add(dataArray);
                }
                else if (actionList.contains(LookupActions.SIGN)) {
                    long resultId = results.getLong("id");
                    int resultTime = results.getInt("time");
                    int resultUserId = results.getInt("user");
                    int resultWorldId = results.getInt("wid");
                    int resultX = results.getInt("x");
                    int resultY = results.getInt("y");
                    int resultZ = results.getInt("z");
                    boolean isFront = results.getInt("face") == 0;
                    String line1 = results.getString("line_1");
                    String line2 = results.getString("line_2");
                    String line3 = results.getString("line_3");
                    String line4 = results.getString("line_4");
                    String line5 = results.getString("line_5");
                    String line6 = results.getString("line_6");
                    String line7 = results.getString("line_7");
                    String line8 = results.getString("line_8");

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

                    Object[] dataArray = new Object[] { resultId, resultTime, resultUserId, resultWorldId, resultX, resultY, resultZ, message.toString() };
                    list.add(dataArray);
                }
                else {
                    int resultData = 0;
                    int resultAmount = -1;
                    int resultTable = 0;
                    int resultEntitySpawnId = 0;
                    byte[] resultMeta = null;
                    byte[] resultBlockData = null;
                    long resultId = results.getLong("id");
                    int resultUserId = results.getInt("user");
                    int resultAction = results.getInt("action");
                    int resultRolledBack = results.getInt("rolled_back");
                    int resultType = results.getInt("type");
                    int resultTime = results.getInt("time");
                    int resultX = results.getInt("x");
                    int resultY = results.getInt("y");
                    int resultZ = results.getInt("z");
                    int resultWorldId = results.getInt("wid");

                    boolean hasTbl = false;
                    if ((lookup && actionList.size() == 0) || actionList.contains(LookupActions.INTERACTION) || actionList.contains(LookupActions.CONTAINER) || actionList.contains(5) || actionList.contains(LookupActions.ITEM)) {
                        resultData = results.getInt("data");
                        resultAmount = results.getInt("amount");
                        resultMeta = DatabaseUtils.getBytes(results, "metadata");
                        resultTable = results.getInt("tbl");
                        resultEntitySpawnId = results.getInt("entity_spawn_rowid");
                        hasTbl = true;
                    }
                    else {
                        resultData = results.getInt("data");
                        resultMeta = DatabaseUtils.getBytes(results, "meta");
                        resultBlockData = DatabaseUtils.getBytes(results, "blockdata");
                    }

                    boolean valid = true;
                    if (!lookup) {
                        if (invalidRollbackActions.contains(resultAction)) {
                            valid = false;
                        }
                    }

                    if (valid) {
                        Object[] dataArray = new Object[] { resultId, resultTime, resultUserId, resultX, resultY, resultZ, resultType, resultData, resultAction, resultRolledBack, resultWorldId, resultAmount, resultMeta, resultBlockData, resultTable, resultEntitySpawnId };
                        list.add(dataArray);
                    }
                }
            }
            results.close();
        }
        catch (Exception e) {
            ErrorReporter.report(e);
            return null;
        }
        finally {
            if (paused && !Consumer.isPersistenceHalted()) {
                Consumer.isPaused = false;
            }
        }
        return list;
    }

    static final class RawLookupPage {

        private final long totalRows;
        private final List<Object[]> rows;
        private final LookupCursor nextCursor;

        private RawLookupPage(long totalRows, List<Object[]> rows, LookupCursor nextCursor) {
            this.totalRows = totalRows;
            this.rows = rows;
            this.nextCursor = nextCursor;
        }

        long getTotalRows() {
            return totalRows;
        }

        List<Object[]> getRows() {
            return rows;
        }

        LookupCursor getNextCursor() {
            return nextCursor;
        }
    }

    static ResultSet rawLookupResultSet(Statement statement, CommandSender user, List<String> checkUuids, List<String> checkUsers, List<Object> restrictList, Map<Object, Boolean> excludeList, List<String> excludeUserList, List<Integer> actionList, Location location, Integer[] radius, Long[] rowData, long startTime, long endTime, int limitOffset, int limitCount, boolean restrictWorld, boolean lookup, boolean count) {
        return rawLookupResultSet(statement, user, checkUuids, checkUsers, restrictList, excludeList, excludeUserList, actionList, Collections.emptyList(), location, radius, rowData, startTime, endTime, limitOffset, limitCount, restrictWorld, lookup, count);
    }

    static ResultSet rawLookupResultSet(Statement statement, CommandSender user, List<String> checkUuids, List<String> checkUsers, List<Object> restrictList, Map<Object, Boolean> excludeList, List<String> excludeUserList, List<Integer> actionList, List<String> messageFilters, Location location, Integer[] radius, Long[] rowData, long startTime, long endTime, int limitOffset, int limitCount, boolean restrictWorld, boolean lookup, boolean count) {
        return rawLookupResultSet(statement, user, checkUuids, checkUsers, restrictList, excludeList, excludeUserList, actionList, messageFilters, Collections.emptySet(), Collections.emptySet(), location, radius, rowData, startTime, endTime, limitOffset, limitCount, restrictWorld, lookup, count);
    }

    static ResultSet rawLookupResultSet(Statement statement, CommandSender user, List<String> checkUuids, List<String> checkUsers, List<Object> restrictList, Map<Object, Boolean> excludeList, List<String> excludeUserList, List<Integer> actionList, List<String> messageFilters, Set<UUID> loadedEntityUuids, Set<UUID> loadedEntityCandidates, Location location, Integer[] radius, Long[] rowData, long startTime, long endTime, int limitOffset, int limitCount, boolean restrictWorld, boolean lookup, boolean count) {
        return rawLookupResultSet(statement, user, checkUuids, checkUsers, restrictList, excludeList, excludeUserList, actionList, EntityActionFilter.DEFAULT, messageFilters, loadedEntityUuids, loadedEntityCandidates, location, radius, rowData, startTime, endTime, limitOffset, limitCount, restrictWorld, lookup, count);
    }

    static ResultSet rawLookupResultSet(Statement statement, CommandSender user, List<String> checkUuids, List<String> checkUsers, List<Object> restrictList, Map<Object, Boolean> excludeList, List<String> excludeUserList, List<Integer> actionList, EntityActionFilter entityActionFilter, List<String> messageFilters, Set<UUID> loadedEntityUuids, Set<UUID> loadedEntityCandidates, Location location, Integer[] radius, Long[] rowData, long startTime, long endTime, int limitOffset, int limitCount, boolean restrictWorld, boolean lookup, boolean count) {
        return rawLookupResultSet(statement, user, checkUuids, checkUsers, restrictList, excludeList, excludeUserList, actionList, entityActionFilter, messageFilters, loadedEntityUuids, loadedEntityCandidates, location, radius, rowData, startTime, endTime, limitOffset, limitCount, restrictWorld, lookup, count, null);
    }

    static ResultSet rawLookupResultSet(Statement statement, CommandSender user, List<String> checkUuids, List<String> checkUsers, List<Object> restrictList, Map<Object, Boolean> excludeList, List<String> excludeUserList, List<Integer> actionList, EntityActionFilter entityActionFilter, List<String> messageFilters, Set<UUID> loadedEntityUuids, Set<UUID> loadedEntityCandidates, Location location, Integer[] radius, Long[] rowData, long startTime, long endTime, int limitOffset, int limitCount, boolean restrictWorld, boolean lookup, boolean count, Integer entityContainerId) {
        return rawLookupResultSet(statement, user, checkUuids, checkUsers, restrictList, excludeList, excludeUserList, actionList, entityActionFilter, messageFilters, loadedEntityUuids, loadedEntityCandidates, location, radius, rowData, startTime, endTime, limitOffset, limitCount, restrictWorld, lookup, count, entityContainerId, LookupRollbackState.ANY);
    }

    static ResultSet rawLookupResultSet(Statement statement, CommandSender user, List<String> checkUuids, List<String> checkUsers, List<Object> restrictList, Map<Object, Boolean> excludeList, List<String> excludeUserList, List<Integer> actionList, EntityActionFilter entityActionFilter, List<String> messageFilters, Set<UUID> loadedEntityUuids, Set<UUID> loadedEntityCandidates, Location location, Integer[] radius, Long[] rowData, long startTime, long endTime, int limitOffset, int limitCount, boolean restrictWorld, boolean lookup, boolean count, Integer entityContainerId, LookupRollbackState rollbackState) {
        return rawLookupResultSet(statement, user, checkUuids, checkUsers, restrictList, excludeList, excludeUserList, actionList, entityActionFilter, messageFilters, loadedEntityUuids, loadedEntityCandidates, location, radius, rowData, startTime, endTime, limitOffset, limitCount, restrictWorld, lookup, count, entityContainerId, false, false, false, rollbackState, null, false);
    }

    static ResultSet rawLookupResultSet(Statement statement, CommandSender user, List<String> checkUuids, List<String> checkUsers, List<Object> restrictList, Map<Object, Boolean> excludeList, List<String> excludeUserList, List<Integer> actionList, EntityActionFilter entityActionFilter, List<String> messageFilters, EntityLookupContext entityContext, Location location, Integer[] radius, Long[] rowData, long startTime, long endTime, int limitOffset, int limitCount, boolean restrictWorld, boolean lookup, boolean count, Integer entityContainerId, LookupRollbackState rollbackState) {
        return rawLookupResultSet(statement, user, checkUuids, checkUsers, restrictList, excludeList, excludeUserList, actionList, entityActionFilter, messageFilters, entityContext, location, radius, rowData, startTime, endTime, limitOffset, limitCount, restrictWorld, lookup, count, entityContainerId, false, false, false, rollbackState, null, false);
    }

    static ResultSet rawSummaryResultSet(Statement statement, CommandSender user, List<String> checkUuids, List<String> checkUsers, List<Object> restrictList, Map<Object, Boolean> excludeList, List<String> excludeUserList, List<Integer> actionList, EntityActionFilter entityActionFilter, Set<UUID> loadedEntityUuids, Set<UUID> loadedEntityCandidates, Location location, Integer[] radius, long startTime, long endTime, int limitOffset, int limitCount, boolean restrictWorld, Integer entityContainerId, boolean countGroups) {
        return rawSummaryResultSet(statement, user, checkUuids, checkUsers, restrictList, excludeList, excludeUserList, actionList, entityActionFilter, loadedEntityUuids, loadedEntityCandidates, location, radius, startTime, endTime, limitOffset, limitCount, restrictWorld, entityContainerId, countGroups, LookupRollbackState.ANY);
    }

    static ResultSet rawSummaryResultSet(Statement statement, CommandSender user, List<String> checkUuids, List<String> checkUsers, List<Object> restrictList, Map<Object, Boolean> excludeList, List<String> excludeUserList, List<Integer> actionList, EntityActionFilter entityActionFilter, Set<UUID> loadedEntityUuids, Set<UUID> loadedEntityCandidates, Location location, Integer[] radius, long startTime, long endTime, int limitOffset, int limitCount, boolean restrictWorld, Integer entityContainerId, boolean countGroups, LookupRollbackState rollbackState) {
        return rawLookupResultSet(statement, user, checkUuids, checkUsers, restrictList, excludeList, excludeUserList, actionList, entityActionFilter, Collections.emptyList(), loadedEntityUuids, loadedEntityCandidates, location, radius, null, startTime, endTime, limitOffset, limitCount, restrictWorld, true, false, entityContainerId, true, countGroups, false, rollbackState, null, false);
    }

    static ResultSet rawSummaryResultSet(Statement statement, CommandSender user, List<String> checkUuids, List<String> checkUsers, List<Object> restrictList, Map<Object, Boolean> excludeList, List<String> excludeUserList, List<Integer> actionList, EntityActionFilter entityActionFilter, EntityLookupContext entityContext, Location location, Integer[] radius, long startTime, long endTime, int limitOffset, int limitCount, boolean restrictWorld, Integer entityContainerId, boolean countGroups, LookupRollbackState rollbackState) {
        return rawLookupResultSet(statement, user, checkUuids, checkUsers, restrictList, excludeList, excludeUserList, actionList, entityActionFilter, Collections.emptyList(), entityContext, location, radius, null, startTime, endTime, limitOffset, limitCount, restrictWorld, true, false, entityContainerId, true, countGroups, false, rollbackState, null, false);
    }

    static ResultSet rawSummaryPageResultSet(Statement statement, CommandSender user, List<String> checkUuids, List<String> checkUsers, List<Object> restrictList, Map<Object, Boolean> excludeList, List<String> excludeUserList, List<Integer> actionList, EntityActionFilter entityActionFilter, Set<UUID> loadedEntityUuids, Set<UUID> loadedEntityCandidates, Location location, Integer[] radius, long startTime, long endTime, int limitOffset, int limitCount, boolean restrictWorld, Integer entityContainerId) {
        return rawSummaryPageResultSet(statement, user, checkUuids, checkUsers, restrictList, excludeList, excludeUserList, actionList, entityActionFilter, loadedEntityUuids, loadedEntityCandidates, location, radius, startTime, endTime, limitOffset, limitCount, restrictWorld, entityContainerId, LookupRollbackState.ANY);
    }

    static ResultSet rawSummaryPageResultSet(Statement statement, CommandSender user, List<String> checkUuids, List<String> checkUsers, List<Object> restrictList, Map<Object, Boolean> excludeList, List<String> excludeUserList, List<Integer> actionList, EntityActionFilter entityActionFilter, Set<UUID> loadedEntityUuids, Set<UUID> loadedEntityCandidates, Location location, Integer[] radius, long startTime, long endTime, int limitOffset, int limitCount, boolean restrictWorld, Integer entityContainerId, LookupRollbackState rollbackState) {
        return rawLookupResultSet(statement, user, checkUuids, checkUsers, restrictList, excludeList, excludeUserList, actionList, entityActionFilter, Collections.emptyList(), loadedEntityUuids, loadedEntityCandidates, location, radius, null, startTime, endTime, limitOffset, limitCount, restrictWorld, true, false, entityContainerId, true, false, true, rollbackState, null, false);
    }

    static ResultSet rawSummaryPageResultSet(Statement statement, CommandSender user, List<String> checkUuids, List<String> checkUsers, List<Object> restrictList, Map<Object, Boolean> excludeList, List<String> excludeUserList, List<Integer> actionList, EntityActionFilter entityActionFilter, EntityLookupContext entityContext, Location location, Integer[] radius, long startTime, long endTime, int limitOffset, int limitCount, boolean restrictWorld, Integer entityContainerId, LookupRollbackState rollbackState) {
        return rawLookupResultSet(statement, user, checkUuids, checkUsers, restrictList, excludeList, excludeUserList, actionList, entityActionFilter, Collections.emptyList(), entityContext, location, radius, null, startTime, endTime, limitOffset, limitCount, restrictWorld, true, false, entityContainerId, true, false, true, rollbackState, null, false);
    }

    private static ResultSet rawLookupResultSet(Statement statement, CommandSender user, List<String> checkUuids, List<String> checkUsers, List<Object> restrictList, Map<Object, Boolean> excludeList, List<String> excludeUserList, List<Integer> actionList, EntityActionFilter entityActionFilter, List<String> messageFilters, Set<UUID> loadedEntityUuids, Set<UUID> loadedEntityCandidates, Location location, Integer[] radius, Long[] rowData, long startTime, long endTime, int limitOffset, int limitCount, boolean restrictWorld, boolean lookup, boolean count, Integer entityContainerId, boolean summary, boolean countGroups, boolean includeGroupCount, LookupRollbackState rollbackState, Map<Integer, List<Long>> pageRows, boolean selectPageRows) {
        EntityLookupContext entityContext = EntityLookupContext.legacy(loadedEntityUuids, loadedEntityCandidates);
        return rawLookupResultSet(statement, user, checkUuids, checkUsers, restrictList, excludeList, excludeUserList, actionList, entityActionFilter, messageFilters, entityContext, location, radius, rowData, startTime, endTime, limitOffset, limitCount, restrictWorld, lookup, count, entityContainerId, summary, countGroups, includeGroupCount, rollbackState, pageRows, selectPageRows);
    }

    private static ResultSet rawLookupResultSet(Statement statement, CommandSender user, List<String> checkUuids, List<String> checkUsers, List<Object> restrictList, Map<Object, Boolean> excludeList, List<String> excludeUserList, List<Integer> actionList, EntityActionFilter entityActionFilter, List<String> messageFilters, EntityLookupContext entityContext, Location location, Integer[] radius, Long[] rowData, long startTime, long endTime, int limitOffset, int limitCount, boolean restrictWorld, boolean lookup, boolean count, Integer entityContainerId, boolean summary, boolean countGroups, boolean includeGroupCount, LookupRollbackState rollbackState, Map<Integer, List<Long>> pageRows, boolean selectPageRows) {
        return rawLookupResultSet(statement, user, checkUuids, checkUsers, restrictList, excludeList, excludeUserList, actionList, entityActionFilter, messageFilters, entityContext, location, radius, rowData, startTime, endTime, limitOffset, limitCount, restrictWorld, lookup, count, entityContainerId, summary, countGroups, includeGroupCount, rollbackState, pageRows, selectPageRows, 0, -1L, null);
    }

    private static ResultSet rawLookupResultSet(Statement statement, CommandSender user, List<String> checkUuids, List<String> checkUsers, List<Object> restrictList, Map<Object, Boolean> excludeList, List<String> excludeUserList, List<Integer> actionList, EntityActionFilter entityActionFilter, List<String> messageFilters, EntityLookupContext entityContext, Location location, Integer[] radius, Long[] rowData, long startTime, long endTime, int limitOffset, int limitCount, boolean restrictWorld, boolean lookup, boolean count, Integer entityContainerId, boolean summary, boolean countGroups, boolean includeGroupCount, LookupRollbackState rollbackState, Map<Integer, List<Long>> pageRows, boolean selectPageRows, int pageOffset, long knownTotalRows, LookupCursor cursor) {
        ResultSet results = null;

        try {
            Set<UUID> loadedEntityUuids = entityContext.getLoadedEntityUuids();
            Set<UUID> loadedEntityCandidates = entityContext.getLoadedEntityCandidates();
            List<Integer> validActions = Arrays.asList(LookupActions.BLOCK_BREAK, LookupActions.BLOCK_PLACE, LookupActions.INTERACTION, LookupActions.ENTITY_KILL, LookupActions.ENTITY_SPAWN);
            if (radius != null) {
                restrictWorld = true;
            }
            boolean requiresLocationWorld = restrictWorld || (radius == null && actionList.contains(5) && entityContainerId == null);
            int locationWorldId = -1;
            if (requiresLocationWorld) {
                if (location == null || location.getWorld() == null) {
                    throw new IllegalArgumentException("Unable to resolve lookup world");
                }
                locationWorldId = WorldUtils.getWorldId(location.getWorld().getName());
            }
            Integer[] sourceBounds = radius;
            if (sourceBounds == null && actionList.contains(5) && entityContainerId == null) {
                sourceBounds = new Integer[] {
                        0,
                        (int) Math.floor(location.getX()),
                        (int) Math.ceil(location.getX()),
                        location.getBlockY(),
                        location.getBlockY(),
                        (int) Math.floor(location.getZ()),
                        (int) Math.ceil(location.getZ())
                };
            }

            boolean inventoryQuery = LookupActions.isInventoryLookup(actionList);
            boolean standardActionLookup = !actionList.contains(LookupActions.CONTAINER) && !actionList.contains(5) && !actionList.contains(LookupActions.ITEM) && !actionList.contains(LookupActions.CHAT) && !actionList.contains(LookupActions.COMMAND) && !actionList.contains(LookupActions.SESSION) && !actionList.contains(LookupActions.USERNAME) && !actionList.contains(LookupActions.SIGN);
            boolean includeEntityInteractions = !summary && lookup && standardActionLookup && (actionList.isEmpty() || actionList.contains(LookupActions.INTERACTION));
            boolean includeEntityContainers = entityContainerId != null || actionList.contains(LookupActions.CONTAINER) || inventoryQuery || (lookup && actionList.isEmpty());
            boolean includeEntitySpawnLocations = entityActionFilter.includesAnySpawn(actionList, lookup || Config.getGlobal().ROLLBACK_ENTITIES);
            boolean entitySpawnLocation = restrictWorld && locationWorldId > 0 && includeEntitySpawnLocations;
            boolean entityContainerLocation = restrictWorld && locationWorldId > 0 && includeEntityContainers;
            boolean entityInteractionLocation = restrictWorld && locationWorldId > 0 && includeEntityInteractions;
            boolean entitySpawnRadius = entitySpawnLocation && radius != null;
            boolean entityContainerRadius = entityContainerLocation && radius != null;
            boolean currentEntityRadius = !lookup && (entitySpawnRadius || entityContainerRadius);
            if (ConfigHandler.databaseType.isDuckDB()
                    && pageRows == null
                    && radius != null
                    && location != null
                    && location.getWorld() != null
                    && (includeEntitySpawnLocations || includeEntityContainers || includeEntityInteractions)
                    && !entityContext.isReusable()
                    && loadedEntityUuids.isEmpty()
                    && loadedEntityCandidates.isEmpty()) {
                entityContext = includeEntityContainers || includeEntityInteractions
                        ? EntitySpawnStatement.loadLookupContext(statement.getConnection(), location, radius)
                        : EntitySpawnStatement.loadLookupContext(statement.getConnection(), location, radius, startTime, endTime);
                loadedEntityUuids = entityContext.getLoadedEntityUuids();
                loadedEntityCandidates = entityContext.getLoadedEntityCandidates();
            }
            boolean validAction = false;
            String queryBlock = "";
            String queryEntity = "";
            String queryLimit = "";
            String queryTable = "block";
            String action = "";
            String actionExclude = "";
            String includeBlock = "";
            String includeEntity = "";
            String excludeBlock = "";
            String excludeEntity = "";
            String users = "";
            String uuids = "";
            String excludeUsers = "";
            String userColumn = ConfigHandler.databaseType.getUserColumn();
            String unionLimit = "";
            String index = "";
            String query = "";
            String entitySpawnLocationQuery = "";
            String entityContainerLocationQuery = "";
            String entityInteractionLocationQuery = "";
            String standardLocationQuery = "";
            String entityLocationCte = "";
            List<String> messageFilterBindings = new ArrayList<>();

            if (checkUuids.size() > 0) {
                String list = "";

                for (String value : checkUuids) {
                    if (list.length() == 0) {
                        list = "'" + value + "'";
                    }
                    else {
                        list = list + ",'" + value + "'";
                    }
                }

                uuids = list;
            }

            if (!checkUsers.contains("#global")) {
                StringBuilder checkUserText = new StringBuilder();

                for (String checkUser : checkUsers) {
                    if (!checkUser.equals("#container")) {
                        int userId = UserStatement.findId(statement.getConnection(), checkUser);
                        if (checkUserText.length() == 0) {
                            checkUserText = checkUserText.append(userId);
                        }
                        else {
                            checkUserText.append(",").append(userId);
                        }
                    }
                }
                users = checkUserText.toString();
            }

            if (restrictList.size() > 0) {
                StringBuilder includeListMaterial = new StringBuilder();
                StringBuilder includeListEntity = new StringBuilder();

                for (Object restrictTarget : restrictList) {
                    String targetName = "";

                    if (restrictTarget instanceof Material) {
                        targetName = ((Material) restrictTarget).name();
                        if (includeListMaterial.length() == 0) {
                            includeListMaterial = includeListMaterial.append(MaterialUtils.getBlockId(targetName, false));
                        }
                        else {
                            includeListMaterial.append(",").append(MaterialUtils.getBlockId(targetName, false));
                        }

                        /* Include legacy IDs */
                        int legacyId = BukkitAdapter.ADAPTER.getLegacyBlockId((Material) restrictTarget);
                        if (legacyId > 0) {
                            includeListMaterial.append(",").append(legacyId);
                        }
                    }
                    else if (restrictTarget instanceof EntityType) {
                        targetName = ((EntityType) restrictTarget).name();
                        if (includeListEntity.length() == 0) {
                            includeListEntity = includeListEntity.append(EntityUtils.getEntityId(targetName, false));
                        }
                        else {
                            includeListEntity.append(",").append(EntityUtils.getEntityId(targetName, false));
                        }
                    }
                    else if (restrictTarget instanceof String) {
                        int blockId = MaterialUtils.getBlockId((String) restrictTarget, false);
                        if (includeListMaterial.length() == 0) {
                            includeListMaterial = includeListMaterial.append(blockId);
                        }
                        else {
                            includeListMaterial.append(",").append(blockId);
                        }
                    }
                }

                includeBlock = includeListMaterial.toString();
                includeEntity = includeListEntity.toString();
            }

            if (excludeList.size() > 0) {
                StringBuilder excludeListMaterial = new StringBuilder();
                StringBuilder excludeListEntity = new StringBuilder();

                for (Object restrictTarget : excludeList.keySet()) {
                    String targetName = "";

                    if (restrictTarget instanceof Material) {
                        targetName = ((Material) restrictTarget).name();
                        if (excludeListMaterial.length() == 0) {
                            excludeListMaterial = excludeListMaterial.append(MaterialUtils.getBlockId(targetName, false));
                        }
                        else {
                            excludeListMaterial.append(",").append(MaterialUtils.getBlockId(targetName, false));
                        }

                        /* Include legacy IDs */
                        int legacyId = BukkitAdapter.ADAPTER.getLegacyBlockId((Material) restrictTarget);
                        if (legacyId > 0) {
                            excludeListMaterial.append(",").append(legacyId);
                        }
                    }
                    else if (restrictTarget instanceof EntityType) {
                        targetName = ((EntityType) restrictTarget).name();
                        if (excludeListEntity.length() == 0) {
                            excludeListEntity = excludeListEntity.append(EntityUtils.getEntityId(targetName, false));
                        }
                        else {
                            excludeListEntity.append(",").append(EntityUtils.getEntityId(targetName, false));
                        }
                    }
                    else if (restrictTarget instanceof String) {
                        int blockId = MaterialUtils.getBlockId((String) restrictTarget, false);
                        if (blockId > -1) {
                            if (excludeListMaterial.length() == 0) {
                                excludeListMaterial = excludeListMaterial.append(blockId);
                            }
                            else {
                                excludeListMaterial.append(",").append(blockId);
                            }
                        }
                    }
                }

                excludeBlock = excludeListMaterial.toString();
                excludeEntity = excludeListEntity.toString();
            }

            if (excludeUserList.size() > 0) {
                StringBuilder excludeUserText = new StringBuilder();

                for (String excludeTarget : excludeUserList) {
                    int userId = UserStatement.findId(statement.getConnection(), excludeTarget);
                    if (userId < 0) {
                        continue;
                    }
                    if (excludeUserText.length() == 0) {
                        excludeUserText = excludeUserText.append(userId);
                    }
                    else {
                        excludeUserText.append(",").append(userId);
                    }
                }

                excludeUsers = excludeUserText.toString();
            }

            // Specify actions to exclude from a:item
            if ((lookup && actionList.size() == 0) || (actionList.contains(LookupActions.ITEM) && actionList.size() == 1)) {
                StringBuilder actionText = new StringBuilder();
                actionText = actionText.append(ItemTransactionActions.BREAK);
                actionText.append(",").append(ItemTransactionActions.DESTROY);
                actionText.append(",").append(ItemTransactionActions.CREATE);
                actionText.append(",").append(ItemTransactionActions.SELL);
                actionText.append(",").append(ItemTransactionActions.BUY);
                actionExclude = actionText.toString();
            }

            if (!actionList.isEmpty()) {
                StringBuilder actionText = new StringBuilder();
                for (Integer actionTarget : actionList) {
                    if (validActions.contains(actionTarget)) {
                        // If just looking up drops/pickups, remap the actions to the correct values
                        if (actionList.contains(LookupActions.ITEM) && !actionList.contains(LookupActions.CONTAINER)) {
                            if (actionTarget == ItemTransactionActions.REMOVE && !actionList.contains(ItemTransactionActions.DROP)) {
                                actionTarget = ItemTransactionActions.DROP;
                            }
                            else if (actionTarget == ItemTransactionActions.ADD && !actionList.contains(ItemTransactionActions.PICKUP)) {
                                actionTarget = ItemTransactionActions.PICKUP;
                            }
                        }

                        if (actionText.length() == 0) {
                            actionText = actionText.append(actionTarget);
                        }
                        else {
                            actionText.append(",").append(actionTarget);
                        }

                        // If selecting from co_item & co_container, add in actions for both transaction types
                        if (LookupActions.isInventoryLookup(actionList)) {
                            if (actionTarget == ItemTransactionActions.REMOVE) {
                                actionText.append(",").append(ItemTransactionActions.PICKUP);
                                actionText.append(",").append(ItemTransactionActions.REMOVE_ENDER);
                                actionText.append(",").append(ItemTransactionActions.CREATE);
                                actionText.append(",").append(ItemTransactionActions.BUY);
                            }
                            if (actionTarget == ItemTransactionActions.ADD) {
                                actionText.append(",").append(ItemTransactionActions.DROP);
                                actionText.append(",").append(ItemTransactionActions.ADD_ENDER);
                                actionText.append(",").append(ItemTransactionActions.THROW);
                                actionText.append(",").append(ItemTransactionActions.SHOOT);
                                actionText.append(",").append(ItemTransactionActions.BREAK);
                                actionText.append(",").append(ItemTransactionActions.DESTROY);
                                actionText.append(",").append(ItemTransactionActions.SELL);
                            }
                        }
                        // If just looking up drops/pickups, include ender chest transactions
                        else if (actionList.contains(LookupActions.ITEM) && !actionList.contains(LookupActions.CONTAINER)) {
                            if (actionTarget == ItemTransactionActions.DROP) {
                                actionText.append(",").append(ItemTransactionActions.ADD_ENDER);
                                actionText.append(",").append(ItemTransactionActions.THROW);
                                actionText.append(",").append(ItemTransactionActions.SHOOT);
                            }
                            if (actionTarget == ItemTransactionActions.PICKUP) {
                                actionText.append(",").append(ItemTransactionActions.REMOVE_ENDER);
                            }
                        }
                    }
                }

                action = actionText.toString();
            }

            for (Integer value : actionList) {
                if (validActions.contains(value)) {
                    validAction = true;
                }
            }

            String bounds = "";
            if (radius != null) {
                Integer xmin = radius[1];
                Integer xmax = radius[2];
                Integer ymin = radius[3];
                Integer ymax = radius[4];
                Integer zmin = radius[5];
                Integer zmax = radius[6];
                bounds = "x >= " + xmin + " AND x <= " + xmax + " AND z >= " + zmin + " AND z <= " + zmax;

                if (ymin != null && ymax != null) {
                    bounds += " AND y >= " + ymin + " AND y <= " + ymax;
                }
            }

            if (entitySpawnLocation || entityContainerLocation || entityInteractionLocation) {
                int wid = locationWorldId;
                String originalLocation = "(wid=" + wid + (bounds.isEmpty() ? "" : " AND " + bounds) + ")";
                String entityBounds = "";
                if (radius != null) {
                    long entityMinX = radius[1];
                    long entityMaxX = (long) radius[2] + 1L;
                    long entityMinZ = radius[5];
                    long entityMaxZ = (long) radius[6] + 1L;
                    if (currentEntityRadius) {
                        entityMinX = Math.floorDiv(radius[1], 16) << 4;
                        entityMaxX = ((long) Math.floorDiv(radius[2], 16) << 4) + 16L;
                        entityMinZ = Math.floorDiv(radius[5], 16) << 4;
                        entityMaxZ = ((long) Math.floorDiv(radius[6], 16) << 4) + 16L;
                    }
                    entityBounds = "x >= " + entityMinX + " AND x < " + entityMaxX + " AND z >= " + entityMinZ + " AND z < " + entityMaxZ;
                    if (!currentEntityRadius && radius[3] != null && radius[4] != null) {
                        entityBounds += " AND y >= " + radius[3] + " AND y < " + ((long) radius[4] + 1L);
                    }
                }
                String databaseLocation = "(current_wid=" + wid + (entityBounds.isEmpty() ? "" : " AND " + entityBounds) + ")";
                if (!loadedEntityCandidates.isEmpty()) {
                    databaseLocation = "(" + databaseLocation + " AND uuid NOT IN(" + uuidList(loadedEntityCandidates) + "))";
                }
                if (!loadedEntityUuids.isEmpty()) {
                    databaseLocation += " OR uuid IN(" + uuidList(loadedEntityUuids) + ")";
                }
                boolean materializeEntityLocations = ConfigHandler.databaseType.isDuckDB() && pageRows == null;
                if (materializeEntityLocations) {
                    if (entityContext.isReusable()) {
                        entityLocationCte = entityLocationCte(entityContext, databaseLocation);
                    }
                    else {
                        entityLocationCte = "entity_location_rows AS MATERIALIZED (SELECT rowid,block_rowid,time FROM " + ConfigHandler.prefix + "entity_spawn WHERE (" + databaseLocation + "))";
                    }
                }

                if (entitySpawnLocation) {
                    String entitySpawnRows = materializeEntityLocations
                            ? "SELECT block_rowid FROM entity_location_rows WHERE 1" + entitySpawnTimeQuery(startTime, endTime)
                            : "SELECT block_rowid FROM " + ConfigHandler.prefix + "entity_spawn WHERE (" + databaseLocation + ")" + entitySpawnTimeQuery(startTime, endTime);
                    String spawnLocation = "rowid IN(" + entitySpawnRows + ")";
                    if (lookup) {
                        spawnLocation = "(" + originalLocation + " OR " + spawnLocation + ")";
                    }
                    entitySpawnLocationQuery = "((action=" + LookupActions.ENTITY_SPAWN + " AND " + spawnLocation + ") OR (action!=" + LookupActions.ENTITY_SPAWN + " AND " + originalLocation + "))";
                }

                if (entityContainerLocation) {
                    String entitySpawnRows = materializeEntityLocations ? "SELECT rowid FROM entity_location_rows" : "SELECT rowid FROM " + ConfigHandler.prefix + "entity_spawn WHERE (" + databaseLocation + ")";
                    String currentLocation = "entity_spawn_rowid IN(" + entitySpawnRows + ")";
                    entityContainerLocationQuery = lookup ? "(" + originalLocation + " OR " + currentLocation + ")" : currentLocation;
                }

                if (entityInteractionLocation) {
                    String entitySpawnRows = materializeEntityLocations ? "SELECT rowid FROM entity_location_rows" : "SELECT rowid FROM " + ConfigHandler.prefix + "entity_spawn WHERE (" + databaseLocation + ")";
                    String currentLocation = "entity_spawn_rowid IN(" + entitySpawnRows + ")";
                    entityInteractionLocationQuery = "(" + originalLocation + " OR " + currentLocation + ")";
                }

                standardLocationQuery = originalLocation;
                queryBlock = queryBlock + " " + (entitySpawnLocation ? entitySpawnLocationQuery : originalLocation) + " AND";
            }
            else {
                if (restrictWorld) {
                    queryBlock = queryBlock + " wid=" + locationWorldId + " AND";
                }
                if (!bounds.isEmpty()) {
                    queryBlock = queryBlock + " " + bounds + " AND";
                }
            }

            if (radius == null && actionList.contains(5) && entityContainerId == null) {
                int worldId = locationWorldId;
                int x = sourceBounds[1];
                int z = sourceBounds[5];
                int x2 = sourceBounds[2];
                int z2 = sourceBounds[6];

                queryBlock = queryBlock + " wid=" + worldId + " AND (x = " + x + " OR x = " + x2 + ") AND (z = " + z + " OR z = " + z2 + ") AND y = " + location.getBlockY() + " AND";
            }

            String actionPredicate = "";
            if (validAction) {
                actionPredicate = buildActionPredicate(action, actionList, entityActionFilter);
                queryBlock = queryBlock + " " + actionPredicate + " AND";
            }
            else if (inventoryQuery || actionExclude.length() > 0 || includeBlock.length() > 0 || includeEntity.length() > 0 || excludeBlock.length() > 0 || excludeEntity.length() > 0) {
                queryBlock = queryBlock + " action NOT IN(-1) AND";
            }

            if (includeBlock.length() > 0 || includeEntity.length() > 0) {
                queryBlock = queryBlock + " type IN(" + (includeBlock.length() > 0 ? includeBlock : "0") + ") AND";
            }

            if (excludeBlock.length() > 0 || excludeEntity.length() > 0) {
                queryBlock = queryBlock + " type NOT IN(" + (excludeBlock.length() > 0 ? excludeBlock : "0") + ") AND";
            }

            if (uuids.length() > 0) {
                queryBlock = queryBlock + " uuid IN(" + uuids + ") AND";
            }

            if (users.length() > 0) {
                queryBlock = queryBlock + " " + userColumn + " IN(" + users + ") AND";
            }

            if (excludeUsers.length() > 0) {
                queryBlock = queryBlock + " " + userColumn + " NOT IN(" + excludeUsers + ") AND";
            }

            if (startTime > 0) {
                queryBlock = queryBlock + " time > " + startTime + " AND";
            }

            if (endTime > 0) {
                queryBlock = queryBlock + " time <= " + endTime + " AND";
            }

            String rollbackPredicate = buildRollbackPredicate(rollbackState, actionList.contains(LookupActions.ITEM));
            if (!rollbackPredicate.isEmpty()) {
                queryBlock = queryBlock + " " + rollbackPredicate + " AND";
            }

            if (actionList.contains(LookupActions.SIGN)) {
                queryBlock = queryBlock + " action = " + SignActions.PLACE + " AND (LENGTH(line_1) > 0 OR LENGTH(line_2) > 0 OR LENGTH(line_3) > 0 OR LENGTH(line_4) > 0 OR LENGTH(line_5) > 0 OR LENGTH(line_6) > 0 OR LENGTH(line_7) > 0 OR LENGTH(line_8) > 0) AND";
            }

            if (queryBlock.length() > 0) {
                queryBlock = queryBlock.substring(0, queryBlock.length() - 4);
            }

            if (queryBlock.length() == 0) {
                queryBlock = " 1";
            }

            String queryNonBlock = queryBlock;
            if (!entitySpawnLocationQuery.isEmpty()) {
                queryNonBlock = queryNonBlock.replace(entitySpawnLocationQuery, standardLocationQuery);
            }
            String queryEntityContainer = queryNonBlock;
            if (!entityContainerLocationQuery.isEmpty()) {
                queryEntityContainer = queryEntityContainer.replace(standardLocationQuery, entityContainerLocationQuery);
            }
            if (entityContainerId != null) {
                queryEntityContainer = "(" + queryEntityContainer + ") AND entity_spawn_rowid=" + entityContainerId;
            }

            queryEntity = queryBlock;
            if (includeBlock.length() > 0 || includeEntity.length() > 0) {
                queryEntity = queryEntity.replace("type IN(" + (includeBlock.length() > 0 ? includeBlock : "0") + ")", "type IN(" + (includeEntity.length() > 0 ? includeEntity : "0") + ")");
            }
            if (excludeBlock.length() > 0 || excludeEntity.length() > 0) {
                queryEntity = queryEntity.replace("type NOT IN(" + (excludeBlock.length() > 0 ? excludeBlock : "0") + ")", "type NOT IN(" + (excludeEntity.length() > 0 ? excludeEntity : "0") + ")");
            }
            String queryEntityInteraction = queryEntity;
            if (!entitySpawnLocationQuery.isEmpty()) {
                queryEntityInteraction = queryEntityInteraction.replace(entitySpawnLocationQuery, standardLocationQuery);
            }
            if (!entityInteractionLocationQuery.isEmpty()) {
                queryEntityInteraction = queryEntityInteraction.replace(standardLocationQuery, entityInteractionLocationQuery);
            }
            if (!actionPredicate.isEmpty()) {
                queryEntityInteraction = queryEntityInteraction.replace(actionPredicate, "1");
            }
            String baseQuery = ((!includeEntity.isEmpty() || !excludeEntity.isEmpty()) ? queryEntity : queryBlock);
            String blockSourceQuery = actionList.size() == 1 && actionList.contains(LookupActions.INTERACTION) ? queryBlock : baseQuery;
            if (!summary && !selectPageRows && limitOffset > -1 && limitCount > -1) {
                queryLimit = " LIMIT " + limitCount + " OFFSET " + limitOffset;
                unionLimit = " ORDER BY time DESC, id DESC LIMIT " + (limitOffset + limitCount) + "";
            }

            String rows = summary ? "rowid as id,time," + userColumn + ",wid,x,y,z,type,meta as metadata,data,-1 as amount,action,rolled_back,0 as entity_spawn_rowid" : "rowid as id,time," + userColumn + ",wid,x,y,z,action,type,data,meta,blockdata,rolled_back";
            String queryOrder = " ORDER BY rowid DESC";

            if (actionList.contains(LookupActions.CONTAINER) || actionList.contains(5)) {
                queryTable = "container";
                rows = "rowid as id,time," + userColumn + ",wid,x,y,z,action,type,data,rolled_back,amount,metadata,0 as entity_spawn_rowid";
            }
            else if (actionList.contains(LookupActions.CHAT) || actionList.contains(LookupActions.COMMAND)) {
                queryTable = "chat";
                rows = "rowid as id,time," + userColumn + ",message";
                if (PluginChannelHandshakeListener.getInstance().isPluginChannelPlayer(user)) {
                    rows += ",wid,x,y,z";
                }

                if (!actionList.contains(LookupActions.CHAT) && actionList.contains(LookupActions.COMMAND)) {
                    queryTable = "command";
                }
            }
            else if (actionList.contains(LookupActions.SESSION)) {
                queryTable = "session";
                rows = "rowid as id,time," + userColumn + ",wid,x,y,z,action";
            }
            else if (actionList.contains(LookupActions.USERNAME)) {
                queryTable = "username_log";
                rows = "rowid as id,time,uuid," + userColumn;
            }
            else if (actionList.contains(LookupActions.SIGN)) {
                queryTable = "sign";
                rows = "rowid as id,time," + userColumn + ",wid,x,y,z,face,line_1,line_2,line_3,line_4,line_5,line_6,line_7,line_8";
            }
            else if (actionList.contains(LookupActions.ITEM)) {
                queryTable = "item";
                rows = "rowid as id,time," + userColumn + ",wid,x,y,z,type,data as metadata,0 as data,amount,action,rolled_back,0 as entity_spawn_rowid";
            }

            if (selectPageRows) {
                rows = "rowid as id,time";
                queryLimit = "";
                unionLimit = "";
            }
            if (count) {
                rows = "COUNT(*) as count";
                queryLimit = " LIMIT 5 OFFSET 0";
                queryOrder = "";
                unionLimit = "";
            }

            String unionSelect = "SELECT * FROM (";
            if (ConfigHandler.databaseType.isMySQL()) {
                if (queryTable.equals("block")) {
                    if (!entitySpawnRadius && radius != null && (radius[2] - radius[1]) <= 50 && (radius[6] - radius[5]) <= 50) {
                        index = "USE INDEX(wid) ";
                    }
                    else if (users.length() > 0) {
                        index = "USE INDEX(user) ";
                    }
                    else if (includeBlock.length() > 0 || includeEntity.length() > 0) {
                        index = "USE INDEX(type) ";
                    }
                    else if (restrictWorld && !entitySpawnLocation) {
                        index = "USE INDEX(wid) ";
                    }
                }

                unionSelect = "(";
            }
            else if (ConfigHandler.databaseType.isSQLite()) {
                if (queryTable.equals("block")) {
                    if (includeBlock.length() > 0 || includeEntity.length() > 0) {
                        index = "INDEXED BY block_type_index ";
                    }
                    if (users.length() > 0) {
                        index = "INDEXED BY block_user_index ";
                    }
                    if (!entitySpawnRadius && radius != null && (radius[2] - radius[1]) <= 50 && (radius[6] - radius[5]) <= 50) {
                        index = "INDEXED BY block_index ";
                    }
                    if ((restrictWorld && (users.length() > 0 || includeBlock.length() > 0 || includeEntity.length() > 0))) {
                        index = "";
                    }
                }
            }

            boolean chatLookup = actionList.contains(LookupActions.CHAT);
            boolean commandLookup = actionList.contains(LookupActions.COMMAND);
            if (chatLookup && commandLookup) {
                String chatQuery = appendMessageFilters(baseQuery, messageFilters, "chat", messageFilterBindings);
                String commandQuery = appendMessageFilters(baseQuery, messageFilters, "command", messageFilterBindings);
                chatQuery = restrictSource(chatQuery, pageRows, 0);
                commandQuery = restrictSource(commandQuery, pageRows, 1);
                String chatTable = sourceTable(statement, "chat", locationWorldId, sourceBounds, entityContext, false, pageRows);
                String commandTable = sourceTable(statement, "command", locationWorldId, sourceBounds, entityContext, false, pageRows);
                query = unionSelect + "SELECT '0' as tbl," + rows + " FROM " + chatTable + " WHERE" + chatQuery + unionLimit + ") UNION ALL ";
                query += unionSelect + "SELECT '1' as tbl," + rows + " FROM " + commandTable + " WHERE" + commandQuery + unionLimit + ")";
                if (!count) {
                    queryOrder = " ORDER BY time DESC, tbl DESC, id DESC";
                }
            }
            else if (chatLookup || commandLookup) {
                baseQuery = appendMessageFilters(baseQuery, messageFilters, queryTable, messageFilterBindings);
            }
            else if (actionList.contains(LookupActions.SIGN)) {
                baseQuery = appendSignMessageFilters(baseQuery, messageFilters, messageFilterBindings);
            }

            boolean itemLookup = inventoryQuery;
            if ((lookup && actionList.size() == 0) || (itemLookup && !actionList.contains(LookupActions.BLOCK_BREAK))) {
                if (!count && !selectPageRows) {
                    rows = "rowid as id,time," + userColumn + ",wid,x,y,z,type,meta as metadata,data,-1 as amount,action,rolled_back,0 as entity_spawn_rowid";
                }

                if (inventoryQuery) {
                    if (validAction) {
                        baseQuery = baseQuery.replace("action IN(" + action + ")", "action IN(" + LookupActions.BLOCK_PLACE + ")");
                    }
                    else {
                        baseQuery = baseQuery.replace("action NOT IN(-1)", "action IN(" + LookupActions.BLOCK_PLACE + ")");
                    }

                    if (!count && !selectPageRows) {
                        rows = "rowid as id,time," + userColumn + ",wid,x,y,z,type,meta as metadata,data,1 as amount,action,rolled_back,0 as entity_spawn_rowid";
                    }
                }

                if (includeBlock.length() > 0 || excludeBlock.length() > 0) {
                    baseQuery = baseQuery.replace("action NOT IN(-1)", "action NOT IN(" + LookupActions.ENTITY_KILL + "," + LookupActions.ENTITY_SPAWN + ")"); // if block specified for include/exclude, filter out entity data
                }

                String sourceQuery = restrictSource(baseQuery, pageRows, InventorySources.BLOCK);
                String sourceTable = sourceTable(statement, "block", locationWorldId, sourceBounds, entityContext, entitySpawnLocation, pageRows);
                query = unionSelect + "SELECT " + "'0' as tbl," + rows + " FROM " + sourceTable + " " + index + "WHERE" + sourceQuery + unionLimit + ") UNION ALL ";
                itemLookup = true;
            }

            if (itemLookup) {
                if (!count && !selectPageRows) {
                    rows = "rowid as id,time," + userColumn + ",wid,x,y,z,type,metadata,data,amount,action,rolled_back,0 as entity_spawn_rowid";
                }
                String containerSourceQuery = restrictSource(queryNonBlock, pageRows, InventorySources.CONTAINER);
                String containerTable = sourceTable(statement, "container", locationWorldId, sourceBounds, entityContext, false, pageRows);
                query = query + unionSelect + "SELECT " + "'1' as tbl," + rows + " FROM " + containerTable + " WHERE" + containerSourceQuery + unionLimit + ") UNION ALL ";

                if (!count && !selectPageRows) {
                    rows = "rowid as id,time," + userColumn + ",wid,x,y,z,type,metadata,data,amount,action,rolled_back,entity_spawn_rowid";
                }
                String entityContainerSourceQuery = restrictSource(queryEntityContainer, pageRows, InventorySources.ENTITY_CONTAINER);
                String entityContainerTable = sourceTable(statement, "entity_container", locationWorldId, sourceBounds, entityContext, entityContainerLocation, pageRows, entityContainerId);
                query = query + unionSelect + "SELECT '" + InventorySources.ENTITY_CONTAINER + "' as tbl," + rows + " FROM " + entityContainerTable + " WHERE" + entityContainerSourceQuery + unionLimit + ") UNION ALL ";

                if (!count && !selectPageRows) {
                    rows = "rowid as id,time," + userColumn + ",wid,x,y,z,type,data as metadata,0 as data,amount,action,rolled_back,0 as entity_spawn_rowid";
                }
                if (!count) {
                    queryOrder = " ORDER BY time DESC, tbl DESC, id DESC";
                }

                if (actionExclude.length() > 0) {
                    queryNonBlock = queryNonBlock.replace("action NOT IN(-1)", "action NOT IN(" + actionExclude + ")");
                }

                String itemSourceQuery = restrictSource(queryNonBlock, pageRows, InventorySources.ITEM);
                String itemTable = sourceTable(statement, "item", locationWorldId, sourceBounds, entityContext, false, pageRows);
                query = query + unionSelect + "SELECT " + "'2' as tbl," + rows + " FROM " + itemTable + " WHERE" + itemSourceQuery + unionLimit + ")";
            }

            if (!itemLookup && (actionList.contains(LookupActions.CONTAINER) || actionList.contains(5))) {
                if (!count && !selectPageRows) {
                    rows = "rowid as id,time," + userColumn + ",wid,x,y,z,type,metadata,data,amount,action,rolled_back,0 as entity_spawn_rowid";
                }
                if (entityContainerId == null) {
                    String sourceQuery = restrictSource(queryNonBlock, pageRows, 0);
                    String sourceTable = sourceTable(statement, "container", locationWorldId, sourceBounds, entityContext, false, pageRows);
                    query = unionSelect + "SELECT '0' as tbl," + rows + " FROM " + sourceTable + " WHERE" + sourceQuery + unionLimit + ")";
                }
                if (includeEntityContainers) {
                    if (!query.isEmpty()) {
                        query += " UNION ALL ";
                    }
                    if (!count && !selectPageRows) {
                        rows = "rowid as id,time," + userColumn + ",wid,x,y,z,type,metadata,data,amount,action,rolled_back,entity_spawn_rowid";
                    }
                    String sourceQuery = restrictSource(queryEntityContainer, pageRows, InventorySources.ENTITY_CONTAINER);
                    String sourceTable = sourceTable(statement, "entity_container", locationWorldId, sourceBounds, entityContext, entityContainerLocation, pageRows, entityContainerId);
                    query += unionSelect + "SELECT '" + InventorySources.ENTITY_CONTAINER + "' as tbl," + rows + " FROM " + sourceTable + " WHERE" + sourceQuery + unionLimit + ")";
                }
                if (!count) {
                    queryOrder = " ORDER BY time DESC, tbl DESC, id DESC";
                }
            }

            if (includeEntityInteractions) {
                if (query.isEmpty()) {
                    if (!count && !selectPageRows) {
                        rows = "rowid as id,time," + userColumn + ",wid,x,y,z,type,meta as metadata,data,-1 as amount,action,rolled_back,0 as entity_spawn_rowid";
                    }
                    String sourceQuery = restrictSource(blockSourceQuery, pageRows, InventorySources.BLOCK);
                    String sourceTable = sourceTable(statement, "block", locationWorldId, sourceBounds, entityContext, entitySpawnLocation, pageRows);
                    query = unionSelect + "SELECT '0' as tbl," + rows + " FROM " + sourceTable + " " + index + "WHERE" + sourceQuery + unionLimit + ")";
                }

                if (!count && !selectPageRows) {
                    rows = "rowid as id,time," + userColumn + ",wid,x,y,z,type,metadata,action as data,-1 as amount," + LookupActions.INTERACTION + " as action,rolled_back,entity_spawn_rowid";
                }
                String sourceQuery = restrictSource(queryEntityInteraction, pageRows, InventorySources.ENTITY_INTERACTION);
                String sourceTable = sourceTable(statement, "entity_interaction", locationWorldId, sourceBounds, entityContext, entityInteractionLocation, pageRows);
                query += " UNION ALL " + unionSelect + "SELECT '" + InventorySources.ENTITY_INTERACTION + "' as tbl," + rows + " FROM " + sourceTable + " WHERE" + sourceQuery + unionLimit + ")";
                if (!count) {
                    queryOrder = " ORDER BY time DESC, tbl DESC, id DESC";
                }
            }

            if (query.length() == 0) {
                if (actionExclude.length() > 0) {
                    baseQuery = baseQuery.replace("action NOT IN(-1)", "action NOT IN(" + actionExclude + ")");
                }

                boolean entityFallback = queryTable.equals("block") ? entitySpawnLocation
                        : queryTable.equals("entity_container") ? entityContainerLocation
                                : queryTable.equals("entity_interaction") && entityInteractionLocation;
                baseQuery = restrictSource(baseQuery, pageRows, 0);
                Integer exactEntitySpawnRowId = queryTable.equals("entity_container") ? entityContainerId : null;
                String sourceTable = sourceTable(statement, queryTable, locationWorldId, sourceBounds, entityContext, entityFallback, pageRows, exactEntitySpawnRowId);
                query = "SELECT " + "'0' as tbl," + rows + " FROM " + sourceTable + " " + index + "WHERE" + baseQuery;
            }

            if (selectPageRows) {
                query = buildDuckDBPageQuery(query, entityLocationCte, pageOffset, limitCount, knownTotalRows, cursor, queryOrder.contains("time DESC"));
            }
            else if (summary) {
                query = buildSummaryQuery(query, inventoryQuery, countGroups, includeGroupCount, limitOffset, limitCount);
            }
            else {
                query = query + queryOrder + queryLimit + "";
            }
            if (!selectPageRows && !entityLocationCte.isEmpty()) {
                query = "WITH " + entityLocationCte + " " + query;
            }
            if (pageRows != null) {
                messageFilterBindings.clear();
            }
            results = executeQuery(statement, query, messageFilterBindings);
        }
        catch (Exception e) {
            ErrorReporter.report(e);
        }

        return results;
    }

    private static String restrictSource(String query, Map<Integer, List<Long>> pageRows, int source) {
        if (pageRows != null) {
            List<Long> rowIds = pageRows.get(source);
            if (rowIds == null || rowIds.isEmpty()) {
                return " 1=0";
            }
            StringJoiner values = new StringJoiner(",");
            for (Long rowId : rowIds) {
                values.add(Long.toString(rowId));
            }
            return " rowid IN(" + values + ")";
        }
        return query;
    }

    private static String sourceTable(Statement statement, String table, int worldId, Integer[] sourceBounds, EntityLookupContext entityContext, boolean entityFallback, Map<Integer, List<Long>> pageRows) throws Exception {
        return sourceTable(statement, table, worldId, sourceBounds, entityContext, entityFallback, pageRows, null);
    }

    private static String sourceTable(Statement statement, String table, int worldId, Integer[] sourceBounds, EntityLookupContext entityContext, boolean entityFallback, Map<Integer, List<Long>> pageRows, Integer exactEntitySpawnRowId) throws Exception {
        String tableName = ConfigHandler.prefix + table;
        if (!ConfigHandler.databaseType.isDuckDB() || pageRows != null) {
            return tableName;
        }
        if (exactEntitySpawnRowId != null) {
            return DuckDBSpatialIndex.entityTableExpression(statement.getConnection(), ConfigHandler.prefix, table, Collections.singleton(exactEntitySpawnRowId), "duckdb_spatial_rows");
        }
        if (sourceBounds == null) {
            return tableName;
        }
        if (entityFallback && !entityContext.isReusable()) {
            return tableName;
        }

        Set<Integer> entitySpawnRowIds = entityFallback ? entityContext.getEntitySpawnRowIds() : Collections.emptySet();
        Set<Long> blockRowIds = entityFallback && table.equals("block") ? entityContext.getBlockRowIds() : Collections.emptySet();
        return DuckDBSpatialIndex.tableExpression(
                statement.getConnection(),
                ConfigHandler.prefix,
                table,
                worldId,
                sourceBounds[1],
                sourceBounds[2],
                sourceBounds[5],
                sourceBounds[6],
                entitySpawnRowIds,
                blockRowIds
        );
    }

    private static String entityLocationCte(EntityLookupContext context, String databaseLocation) {
        if (context.getRows().isEmpty()) {
            return "entity_location_rows AS MATERIALIZED (SELECT CAST(NULL AS BIGINT) AS rowid,CAST(NULL AS BIGINT) AS block_rowid,CAST(NULL AS BIGINT) AS time WHERE false)";
        }

        StringJoiner values = new StringJoiner(",");
        for (EntityLookupContext.Row row : context.getRows()) {
            String blockRowId = row.getBlockRowId() == null ? "CAST(NULL AS BIGINT)" : Long.toString(row.getBlockRowId());
            String time = row.getTime() == null ? "CAST(NULL AS BIGINT)" : Long.toString(row.getTime());
            String currentWorldId = row.getCurrentWorldId() == null ? "CAST(NULL AS INTEGER)" : Integer.toString(row.getCurrentWorldId());
            String x = nullableDoubleLiteral(row.getX());
            String y = nullableDoubleLiteral(row.getY());
            String z = nullableDoubleLiteral(row.getZ());
            values.add("(" + row.getRowId()
                    + "," + blockRowId
                    + "," + time
                    + ",'" + row.getUuid() + "'"
                    + "," + currentWorldId
                    + "," + x
                    + "," + y
                    + "," + z
                    + ")");
        }
        return "entity_location_rows AS MATERIALIZED (SELECT rowid,block_rowid,time FROM (VALUES " + values
                + ") AS entity_context(rowid,block_rowid,time,uuid,current_wid,x,y,z) WHERE (" + databaseLocation + "))";
    }

    private static String nullableDoubleLiteral(Double value) {
        return value == null ? "CAST(NULL AS DOUBLE)" : doubleLiteral(value);
    }

    private static String doubleLiteral(double value) {
        if (Double.isNaN(value)) {
            return "CAST('NaN' AS DOUBLE)";
        }
        if (value == Double.POSITIVE_INFINITY) {
            return "CAST('Infinity' AS DOUBLE)";
        }
        if (value == Double.NEGATIVE_INFINITY) {
            return "CAST('-Infinity' AS DOUBLE)";
        }
        return Double.toString(value);
    }

    private static String buildDuckDBPageQuery(String sourceQuery, String entityLocationCte, int offset, int limit, long knownTotalRows, LookupCursor cursor, boolean orderByTime) {
        if (limit <= 0) {
            throw new IllegalArgumentException("DuckDB lookup page size must be positive");
        }
        if (offset < 0) {
            throw new IllegalArgumentException("DuckDB lookup page offset must not be negative");
        }
        if (cursor != null && (knownTotalRows < 0L || cursor.isOrderByTime() != orderByTime)) {
            cursor = null;
        }

        StringBuilder query = new StringBuilder("WITH ");
        if (!entityLocationCte.isEmpty()) {
            query.append(entityLocationCte).append(',');
        }
        query.append("candidate_rows AS (SELECT CAST(tbl AS INTEGER) AS tbl,id,time FROM (")
                .append(sourceQuery)
                .append(") lookup_sources),");
        if (cursor != null) {
            query.append("page_candidates AS (SELECT * FROM candidate_rows WHERE ");
            if (orderByTime) {
                query.append("STRUCT_PACK(sort_time := COALESCE(time,-2147483648),tbl := tbl,id := id)<STRUCT_PACK(sort_time := ")
                        .append(cursor.getTime())
                        .append(",tbl := ")
                        .append(cursor.getSource())
                        .append(",id := ")
                        .append(cursor.getRowId())
                        .append(")");
            }
            else {
                query.append("id<").append(cursor.getRowId());
            }
            query.append("),");
        }
        boolean countRows = knownTotalRows < 0L;
        String aggregateSource = cursor == null ? "candidate_rows" : "page_candidates";
        query.append("page_aggregate AS (SELECT ");
        if (countRows) {
            query.append("COUNT(*) AS total_count");
            for (int source = 0; source <= InventorySources.ENTITY_INTERACTION; source++) {
                query.append(",COUNT_IF(tbl=").append(source).append(") AS count_").append(source);
            }
            query.append(',');
        }
        query.append("MAX_BY(STRUCT_PACK(tbl := tbl,id := id,sort_time := COALESCE(time,-2147483648)),");
        if (orderByTime) {
            query.append("STRUCT_PACK(sort_time := COALESCE(time,-2147483648),tbl := tbl,id := id)");
        }
        else {
            query.append("STRUCT_PACK(id := id)");
        }
        long requestedRows = cursor == null ? (long) offset + limit : limit;
        if (requestedRows > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("DuckDB lookup page is too deep");
        }
        query.append(',').append(requestedRows).append(") AS newest FROM ").append(aggregateSource).append(") ")
                .append("SELECT ").append(countRows ? "total_count" : Long.toString(knownTotalRows)).append(" AS total_count");
        for (int source = 0; source <= InventorySources.ENTITY_INTERACTION; source++) {
            query.append(',').append(countRows ? "count_" + source : "0").append(" AS count_").append(source);
        }
        query.append(',').append(orderByTime).append(" AS order_by_time,candidate.tbl,candidate.id,candidate.sort_time FROM page_aggregate ")
                .append("LEFT JOIN LATERAL UNNEST(newest) AS page_row(candidate) ON true ");
        if (orderByTime) {
            query.append("ORDER BY candidate.sort_time DESC NULLS LAST,candidate.tbl DESC NULLS LAST,candidate.id DESC NULLS LAST");
        }
        else {
            query.append("ORDER BY candidate.id DESC NULLS LAST");
        }
        if (cursor == null && offset > 0) {
            query.append(" LIMIT ").append(limit).append(" OFFSET ").append(offset);
        }
        return query.toString();
    }

    private static String buildRollbackPredicate(LookupRollbackState rollbackState, boolean inventoryRollback) {
        if (rollbackState == null || rollbackState == LookupRollbackState.ANY) {
            return "";
        }

        if (inventoryRollback) {
            return rollbackState == LookupRollbackState.ROLLED_BACK ? "rolled_back IN(2,3)" : "rolled_back IN(0,1)";
        }
        return rollbackState == LookupRollbackState.ROLLED_BACK ? "rolled_back IN(1,3)" : "rolled_back IN(0,2)";
    }

    private static String buildActionPredicate(String actions, List<Integer> actionList, EntityActionFilter entityActionFilter) {
        if (entityActionFilter == EntityActionFilter.DEFAULT) {
            return "action IN(" + actions + ")";
        }

        List<String> predicates = new ArrayList<>();
        StringJoiner nonEntityActions = new StringJoiner(",");
        for (String action : actions.split(",")) {
            if (!action.equals(Integer.toString(LookupActions.ENTITY_KILL)) && !action.equals(Integer.toString(LookupActions.ENTITY_SPAWN))) {
                nonEntityActions.add(action);
            }
        }
        if (nonEntityActions.length() > 0) {
            predicates.add("action IN(" + nonEntityActions + ")");
        }

        String placedEntityTypes = placedEntityTypeIds();
        addEntityActionPredicate(predicates, LookupActions.ENTITY_SPAWN, entityActionFilter.includesPlacedSpawn(actionList, false), entityActionFilter.includesSpawnedEntity(actionList, false), placedEntityTypes);
        addEntityActionPredicate(predicates, LookupActions.ENTITY_KILL, entityActionFilter.includesVehicleKill(actionList, false), entityActionFilter.includesKilledEntity(actionList, false), placedEntityTypes);
        if (predicates.isEmpty()) {
            return "action IN(-1)";
        }
        return "(" + String.join(" OR ", predicates) + ")";
    }

    private static void addEntityActionPredicate(List<String> predicates, int action, boolean includePlaced, boolean includeOther, String placedEntityTypes) {
        if (includePlaced && includeOther) {
            predicates.add("action=" + action);
        }
        else if (includePlaced) {
            predicates.add("(action=" + action + " AND type IN(" + placedEntityTypes + "))");
        }
        else if (includeOther) {
            predicates.add("(action=" + action + " AND type NOT IN(" + placedEntityTypes + "))");
        }
    }

    private static String placedEntityTypeIds() {
        StringJoiner ids = new StringJoiner(",");
        for (Integer id : EntitySpawnTracking.getPlacedEntityTypeIds()) {
            ids.add(Integer.toString(id));
        }
        return ids.length() == 0 ? "0" : ids.toString();
    }

    private static String entitySpawnTimeQuery(long startTime, long endTime) {
        StringBuilder query = new StringBuilder();
        if (startTime > 0) {
            query.append(" AND time > ").append(startTime);
        }
        if (endTime > 0) {
            query.append(" AND time <= ").append(endTime);
        }
        return query.toString();
    }

    private static String uuidList(Set<UUID> uuids) {
        StringJoiner result = new StringJoiner(",");
        for (UUID uuid : uuids) {
            result.add("'" + uuid + "'");
        }
        return result.toString();
    }

    private static String appendMessageFilters(String baseQuery, List<String> messageFilters, String table, List<String> bindings) {
        if (messageFilters == null || messageFilters.isEmpty()) {
            return baseQuery;
        }

        if (ConfigHandler.databaseType.isDuckDB()) {
            StringBuilder query = new StringBuilder(baseQuery).append(" AND (");
            for (int index = 0; index < messageFilters.size(); index++) {
                if (index > 0) {
                    query.append(" OR ");
                }
                query.append("message ILIKE ? ESCAPE '~'");
                String filter = messageFilters.get(index) == null ? "" : messageFilters.get(index);
                bindings.add(escapeLike(filter) + "%");
            }
            return query.append(')').toString();
        }

        String alias = table + "FilterRows";
        String likeOperator = ConfigHandler.databaseType.isColumnar() ? " ILIKE " : " LIKE ";
        String escapeClause = ConfigHandler.databaseType.isClickHouse() ? "" : " ESCAPE '~'";
        StringBuilder query = new StringBuilder(baseQuery)
                .append(" AND rowid IN (SELECT ").append(alias).append(".rowid FROM ")
                .append(ConfigHandler.prefix).append(table).append(" ").append(alias).append(" WHERE (");
        for (int index = 0; index < messageFilters.size(); index++) {
            if (index > 0) {
                query.append(" OR ");
            }

            String prefixExpression = messagePrefix(alias + ".message");
            query.append("(").append(prefixExpression).append(likeOperator).append("?").append(escapeClause).append(" AND ")
                    .append(alias).append(".message").append(likeOperator).append("?").append(escapeClause).append(")");

            String filter = messageFilters.get(index) == null ? "" : messageFilters.get(index);
            bindings.add(escapeLike(firstCodePoints(filter, 16)) + "%");
            bindings.add(escapeLike(filter) + "%");
        }
        return query.append("))").toString();
    }

    private static String appendSignMessageFilters(String baseQuery, List<String> messageFilters, List<String> bindings) {
        if (messageFilters == null || messageFilters.isEmpty()) {
            return baseQuery;
        }

        if (ConfigHandler.databaseType.isDuckDB()) {
            StringBuilder query = new StringBuilder(baseQuery).append(" AND (");
            for (int filterIndex = 0; filterIndex < messageFilters.size(); filterIndex++) {
                if (filterIndex > 0) {
                    query.append(" OR ");
                }
                query.append("((face=0 AND (");
                appendDuckDBSignLines(query, 1, 4);
                query.append(")) OR (face<>0 AND (");
                appendDuckDBSignLines(query, 5, 8);
                query.append(")))");

                String filter = messageFilters.get(filterIndex) == null ? "" : messageFilters.get(filterIndex);
                String message = escapeLike(filter) + "%";
                for (int line = 1; line <= 8; line++) {
                    bindings.add(message);
                }
            }
            return query.append(')').toString();
        }

        String alias = "signFilterRows";
        String likeOperator = ConfigHandler.databaseType.isColumnar() ? " ILIKE " : " LIKE ";
        String escapeClause = ConfigHandler.databaseType.isClickHouse() ? "" : " ESCAPE '~'";
        StringBuilder query = new StringBuilder(baseQuery).append(" AND rowid IN (");
        boolean union = false;
        for (String filter : messageFilters) {
            String prefix = escapeLike(firstCodePoints(filter, 16)) + "%";
            String message = escapeLike(filter) + "%";
            for (int line = 1; line <= 8; line++) {
                if (union) {
                    query.append(" UNION ALL ");
                }

                String column = "line_" + line;
                String prefixExpression = messagePrefix(alias + "." + column);
                query.append("SELECT ").append(alias).append(".rowid FROM ")
                        .append(ConfigHandler.prefix).append("sign ").append(alias)
                        .append(" WHERE ").append(alias).append(line <= 4 ? ".face = 0" : ".face <> 0")
                        .append(" AND (").append(prefixExpression).append(likeOperator).append("?").append(escapeClause).append(" AND ")
                        .append(alias).append(".").append(column).append(likeOperator).append("?").append(escapeClause).append(")");
                bindings.add(prefix);
                bindings.add(message);
                union = true;
            }
        }
        return query.append(")").toString();
    }

    private static void appendDuckDBSignLines(StringBuilder query, int firstLine, int lastLine) {
        for (int line = firstLine; line <= lastLine; line++) {
            if (line > firstLine) {
                query.append(" OR ");
            }
            query.append("line_").append(line).append(" ILIKE ? ESCAPE '~'");
        }
    }

    private static String firstCodePoints(String value, int maximum) {
        if (value == null) {
            return "";
        }
        int codePoints = value.codePointCount(0, value.length());
        if (codePoints <= maximum) {
            return value;
        }
        return value.substring(0, value.offsetByCodePoints(0, maximum));
    }

    private static String messagePrefix(String column) {
        if (ConfigHandler.databaseType.isMySQL()) {
            return column;
        }
        if (ConfigHandler.databaseType.isClickHouse()) {
            return "substringUTF8(" + column + ",1,16)";
        }
        return "substr(" + column + ",1,16)";
    }

    private static String escapeLike(String value) {
        if (ConfigHandler.databaseType.isClickHouse()) {
            return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        }
        return value.replace("~", "~~").replace("%", "~%").replace("_", "~_");
    }

    private static String buildSummaryQuery(String sourceQuery, boolean inventoryQuery, boolean countGroups, boolean includeGroupCount, int limitOffset, int limitCount) {
        String userColumn = ConfigHandler.databaseType.getUserColumn();
        int blockPositiveAction = inventoryQuery ? LookupActions.BLOCK_BREAK : LookupActions.BLOCK_PLACE;
        int transactionPositiveAction = inventoryQuery ? ItemTransactionActions.REMOVE : ItemTransactionActions.ADD;
        String positiveActions = transactionPositiveAction + "," + ItemTransactionActions.PICKUP + "," + ItemTransactionActions.REMOVE_ENDER + "," + ItemTransactionActions.CREATE + "," + ItemTransactionActions.BUY;
        String delta = "CASE WHEN amount=-1 THEN CASE WHEN action=" + blockPositiveAction + " THEN 1 ELSE -1 END "
                + "WHEN action IN(" + positiveActions + ") THEN amount ELSE -amount END";
        String eligible = "((amount=-1 AND action IN(" + LookupActions.BLOCK_BREAK + "," + LookupActions.BLOCK_PLACE + ")) OR "
                + "(amount<>-1 AND action BETWEEN " + ItemTransactionActions.REMOVE + " AND " + ItemTransactionActions.BUY + "))";
        String countedSource = includeGroupCount
                ? "SELECT summary_source.*,COUNT(*) OVER() AS record_count FROM (" + sourceQuery + ") summary_source"
                : sourceQuery;
        String recordCount = includeGroupCount ? ",record_count" : "";
        String contributions = "SELECT " + userColumn + ",type," + delta + " AS delta" + recordCount + " FROM (" + countedSource + ") summary_counted_source WHERE " + eligible;
        String grouped = "SELECT " + userColumn + ",type,SUM(CASE WHEN delta<0 THEN -delta ELSE 0 END) AS removed_amount,"
                + "SUM(CASE WHEN delta>0 THEN delta ELSE 0 END) AS placed_amount,SUM(delta) AS net_amount"
                + (includeGroupCount ? ",MAX(record_count) AS record_count " : " ")
                + "FROM (" + contributions + ") summary_contributions GROUP BY " + userColumn + ",type";
        if (countGroups) {
            return "SELECT COUNT(*) AS count FROM (" + grouped + ") summary_groups";
        }

        String totalCount = includeGroupCount ? ",COUNT(*) OVER() AS total_count,record_count" : "";
        String query = "SELECT " + userColumn + ",type,removed_amount,placed_amount,net_amount AS amount" + totalCount + " FROM (" + grouped + ") summary_groups ORDER BY ABS(net_amount) DESC," + userColumn + " ASC,type ASC";
        if (limitOffset > -1 && limitCount > -1) {
            query += " LIMIT " + limitCount + " OFFSET " + limitOffset;
        }
        return query;
    }

    private static ResultSet executeQuery(Statement statement, String query, List<String> bindings) throws Exception {
        if (bindings.isEmpty()) {
            return statement.executeQuery(query);
        }

        PreparedStatement preparedStatement = statement.getConnection().prepareStatement(query);
        try {
            int queryTimeout = statement.getQueryTimeout();
            if (queryTimeout > 0) {
                preparedStatement.setQueryTimeout(queryTimeout);
            }
            for (int index = 0; index < bindings.size(); index++) {
                preparedStatement.setString(index + 1, bindings.get(index));
            }
            preparedStatement.closeOnCompletion();
            return preparedStatement.executeQuery();
        }
        catch (Exception e) {
            preparedStatement.close();
            throw e;
        }
    }

}
