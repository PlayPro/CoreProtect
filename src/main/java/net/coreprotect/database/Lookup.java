package net.coreprotect.database;

import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
import net.coreprotect.model.lookup.LookupRollbackState;
import net.coreprotect.model.lookup.LookupSummaryRow;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.command.CommandSender;

import net.coreprotect.consumer.Consumer;
import net.coreprotect.consumer.Queue;
import net.coreprotect.model.action.EntityActionFilter;
import net.coreprotect.utility.ErrorReporter;

public class Lookup extends Queue {

    @Deprecated
    public static long countLookupRows(Statement statement, CommandSender user, List<String> checkUuids, List<String> checkUsers, List<Object> restrictList, Map<Object, Boolean> excludeList, List<String> excludeUserList, List<Integer> actionList, Location location, Integer[] radius, Long[] rowData, long startTime, long endTime, boolean restrictWorld, boolean lookup) {
        return countLookupRows(statement, user, checkUuids, checkUsers, restrictList, excludeList, excludeUserList, actionList, Collections.emptyList(), location, radius, rowData, startTime, endTime, restrictWorld, lookup);
    }

    @Deprecated
    public static long countLookupRows(Statement statement, CommandSender user, List<String> checkUuids, List<String> checkUsers, List<Object> restrictList, Map<Object, Boolean> excludeList, List<String> excludeUserList, List<Integer> actionList, List<String> messageFilters, Location location, Integer[] radius, Long[] rowData, long startTime, long endTime, boolean restrictWorld, boolean lookup) {
        return countLookupRows(statement, user, checkUuids, checkUsers, restrictList, excludeList, excludeUserList, actionList, messageFilters, Collections.emptySet(), Collections.emptySet(), location, radius, rowData, startTime, endTime, restrictWorld, lookup);
    }

    public static long countLookupRows(Statement statement, CommandSender user, List<String> checkUuids, List<String> checkUsers, List<Object> restrictList, Map<Object, Boolean> excludeList, List<String> excludeUserList, List<Integer> actionList, List<String> messageFilters, Set<UUID> loadedEntityUuids, Set<UUID> loadedEntityCandidates, Location location, Integer[] radius, Long[] rowData, long startTime, long endTime, boolean restrictWorld, boolean lookup) {
        return countLookupRows(statement, user, checkUuids, checkUsers, restrictList, excludeList, excludeUserList, actionList, EntityActionFilter.DEFAULT, messageFilters, loadedEntityUuids, loadedEntityCandidates, location, radius, rowData, startTime, endTime, restrictWorld, lookup);
    }

    public static long countLookupRows(Statement statement, CommandSender user, List<String> checkUuids, List<String> checkUsers, List<Object> restrictList, Map<Object, Boolean> excludeList, List<String> excludeUserList, List<Integer> actionList, EntityActionFilter entityActionFilter, List<String> messageFilters, Set<UUID> loadedEntityUuids, Set<UUID> loadedEntityCandidates, Location location, Integer[] radius, Long[] rowData, long startTime, long endTime, boolean restrictWorld, boolean lookup) {
        return countLookupRows(statement, user, checkUuids, checkUsers, restrictList, excludeList, excludeUserList, actionList, entityActionFilter, messageFilters, loadedEntityUuids, loadedEntityCandidates, location, radius, rowData, startTime, endTime, restrictWorld, lookup, null);
    }

    public static long countLookupRows(Statement statement, CommandSender user, List<String> checkUuids, List<String> checkUsers, List<Object> restrictList, Map<Object, Boolean> excludeList, List<String> excludeUserList, List<Integer> actionList, EntityActionFilter entityActionFilter, List<String> messageFilters, Set<UUID> loadedEntityUuids, Set<UUID> loadedEntityCandidates, Location location, Integer[] radius, Long[] rowData, long startTime, long endTime, boolean restrictWorld, boolean lookup, Integer entityContainerId) {
        Long rows = 0L;

        try {
            while (Consumer.isPaused) {
                Thread.sleep(1);
            }
            Consumer.isPaused = true;

            ResultSet results = LookupRaw.rawLookupResultSet(statement, user, checkUuids, checkUsers, restrictList, excludeList, excludeUserList, actionList, entityActionFilter, messageFilters, loadedEntityUuids, loadedEntityCandidates, location, radius, null, startTime, endTime, -1, -1, restrictWorld, lookup, true, entityContainerId);
            while (results.next()) {
                int resultTable = results.getInt("tbl");
                long count = results.getLong("count");
                rowData[resultTable] = count;
                rows += count;
            }
            results.close();
        }
        catch (Exception e) {
            ErrorReporter.report(e);
        }

        Consumer.isPaused = false;

        return rows;
    }

    @Deprecated
    public static List<String[]> performLookup(Statement statement, CommandSender user, List<String> checkUuids, List<String> checkUsers, List<Object> restrictList, Map<Object, Boolean> excludeList, List<String> excludeUserList, List<Integer> actionList, Location location, Integer[] radius, long startTime, long endTime, boolean restrictWorld, boolean lookup) {
        List<String[]> newList = new ArrayList<>();

        try {
            LookupResult<?> lookupResult = LookupRaw.performLookup(statement, user, checkUuids, checkUsers, restrictList, excludeList, excludeUserList, actionList, location, radius, null, startTime, endTime, -1, -1, restrictWorld, lookup, false);
            newList = convertLookupResult(statement, lookupResult);
        }
        catch (Exception e) {
            ErrorReporter.report(e);
        }

        return newList;
    }

    public static LookupResult<?> performLookup(Statement statement, CommandSender user, List<String> checkUuids, List<String> checkUsers, List<Object> restrictList, Map<Object, Boolean> excludeList, List<String> excludeUserList, List<Integer> actionList, Location location, Integer[] radius, Long[] rowData, long startTime, long endTime, int limitOffset, int limitCount, boolean restrictWorld, boolean lookup, boolean countRows) {
        return performLookup(statement, user, checkUuids, checkUsers, restrictList, excludeList, excludeUserList, actionList, Collections.emptyList(), location, radius, rowData, startTime, endTime, limitOffset, limitCount, restrictWorld, lookup, countRows);
    }

    public static LookupResult<?> performLookup(Statement statement, CommandSender user, List<String> checkUuids, List<String> checkUsers, List<Object> restrictList, Map<Object, Boolean> excludeList, List<String> excludeUserList, List<Integer> actionList, List<String> messageFilters, Location location, Integer[] radius, Long[] rowData, long startTime, long endTime, int limitOffset, int limitCount, boolean restrictWorld, boolean lookup, boolean countRows) {
        return performLookup(statement, user, checkUuids, checkUsers, restrictList, excludeList, excludeUserList, actionList, messageFilters, location, radius, rowData, startTime, endTime, limitOffset, limitCount, restrictWorld, lookup, countRows, LookupRollbackState.ANY);
    }

    public static LookupResult<?> performLookup(Statement statement, CommandSender user, List<String> checkUuids, List<String> checkUsers, List<Object> restrictList, Map<Object, Boolean> excludeList, List<String> excludeUserList, List<Integer> actionList, List<String> messageFilters, Location location, Integer[] radius, Long[] rowData, long startTime, long endTime, int limitOffset, int limitCount, boolean restrictWorld, boolean lookup, boolean countRows, LookupRollbackState rollbackState) {
        return LookupRaw.performLookup(statement, user, checkUuids, checkUsers, restrictList, excludeList, excludeUserList, actionList, messageFilters, location, radius, rowData, startTime, endTime, limitOffset, limitCount, restrictWorld, lookup, countRows, rollbackState);
    }

    public static LookupResult<?> performLookup(Statement statement, CommandSender user, List<String> checkUuids, List<String> checkUsers, List<Object> restrictList, Map<Object, Boolean> excludeList, List<String> excludeUserList, List<Integer> actionList, EntityActionFilter entityActionFilter, List<String> messageFilters, Set<UUID> loadedEntityUuids, Set<UUID> loadedEntityCandidates, Location location, Integer[] radius, Long[] rowData, long startTime, long endTime, int limitOffset, int limitCount, boolean restrictWorld, boolean lookup, boolean countRows, LookupRollbackState rollbackState, Integer entityContainerId) {
        return LookupRaw.performLookup(statement, user, checkUuids, checkUsers, restrictList, excludeList, excludeUserList, actionList, entityActionFilter, messageFilters, loadedEntityUuids, loadedEntityCandidates, location, radius, rowData, startTime, endTime, limitOffset, limitCount, restrictWorld, lookup, countRows, rollbackState, entityContainerId);
    }

    public static long countSummaryRows(Statement statement, CommandSender user, List<String> checkUuids, List<String> checkUsers, List<Object> restrictList, Map<Object, Boolean> excludeList, List<String> excludeUserList, List<Integer> actionList, Location location, Integer[] radius, long startTime, long endTime, boolean restrictWorld, LookupRollbackState rollbackState) {
        return LookupRaw.countSummaryRows(statement, user, checkUuids, checkUsers, restrictList, excludeList, excludeUserList, actionList, location, radius, startTime, endTime, restrictWorld, rollbackState);
    }

    public static List<LookupSummaryRow> performSummaryLookup(Statement statement, CommandSender user, List<String> checkUuids, List<String> checkUsers, List<Object> restrictList, Map<Object, Boolean> excludeList, List<String> excludeUserList, List<Integer> actionList, Location location, Integer[] radius, long startTime, long endTime, int limitOffset, int limitCount, boolean restrictWorld, LookupRollbackState rollbackState) {
        return LookupRaw.performSummaryLookup(statement, user, checkUuids, checkUsers, restrictList, excludeList, excludeUserList, actionList, location, radius, startTime, endTime, limitOffset, limitCount, restrictWorld, rollbackState);
    }

    @Deprecated
    public static List<String[]> performPartialLookup(Statement statement, CommandSender user, List<String> checkUuids, List<String> checkUsers, List<Object> restrictList, Map<Object, Boolean> excludeList, List<String> excludeUserList, List<Integer> actionList, Location location, Integer[] radius, Long[] rowData, long startTime, long endTime, int limitOffset, int limitCount, boolean restrictWorld, boolean lookup) {
        List<String[]> newList = new ArrayList<>();

        try {
            LookupResult<?> lookupResult = LookupRaw.performLookup(statement, user, checkUuids, checkUsers, restrictList, excludeList, excludeUserList, actionList, location, radius, rowData, startTime, endTime, limitOffset, limitCount, restrictWorld, lookup, false);
            newList = convertLookupResult(statement, lookupResult);
        }
        catch (Exception e) {
            ErrorReporter.report(e);
        }

        return newList;
    }

    public static String whoPlaced(Statement statement, BlockState block) {
        return BlockLookup.whoPlaced(statement, block);
    }

    public static String whoPlacedCache(Block block) {
        return BlockLookup.whoPlacedCache(block);
    }

    public static String whoPlacedCache(BlockState block) {
        return BlockLookup.whoPlacedCache(block);
    }

    public static String whoRemovedCache(BlockState block) {
        return BlockLookup.whoRemovedCache(block);
    }

    // Maintain backward compatibility
    protected static List<Object[]> performLookupRaw(Statement statement, CommandSender user, List<String> checkUuids, List<String> checkUsers, List<Object> restrictList, Map<Object, Boolean> excludeList, List<String> excludeUserList, List<Integer> actionList, Location location, Integer[] radius, Long[] rowData, long startTime, long endTime, int limitOffset, int limitCount, boolean restrictWorld, boolean lookup) {
        LookupResult<?> lookupResult = LookupRaw.performLookup(statement, user, checkUuids, checkUsers, restrictList, excludeList, excludeUserList, actionList, location, radius, rowData, startTime, endTime, limitOffset, limitCount, restrictWorld, lookup, false);
        return convertLookupResultRaw(lookupResult);
    }

    protected static List<Object[]> performLookupRaw(Statement statement, CommandSender user, List<String> checkUuids, List<String> checkUsers, List<Object> restrictList, Map<Object, Boolean> excludeList, List<String> excludeUserList, List<Integer> actionList, Set<UUID> loadedEntityUuids, Set<UUID> loadedEntityCandidates, Location location, Integer[] radius, Long[] rowData, long startTime, long endTime, int limitOffset, int limitCount, boolean restrictWorld, boolean lookup) {
        LookupResult<?> lookupResult = LookupRaw.performLookup(statement, user, checkUuids, checkUsers, restrictList, excludeList, excludeUserList, actionList, Collections.emptyList(), location, radius, rowData, startTime, endTime, limitOffset, limitCount, restrictWorld, lookup, false);
        return convertLookupResultRaw(lookupResult);
    }

    protected static List<Object[]> performLookupRaw(Statement statement, CommandSender user, List<String> checkUuids, List<String> checkUsers, List<Object> restrictList, Map<Object, Boolean> excludeList, List<String> excludeUserList, List<Integer> actionList, EntityActionFilter entityActionFilter, Set<UUID> loadedEntityUuids, Set<UUID> loadedEntityCandidates, Location location, Integer[] radius, Long[] rowData, long startTime, long endTime, int limitOffset, int limitCount, boolean restrictWorld, boolean lookup) {
        LookupResult<?> lookupResult = LookupRaw.performLookup(statement, user, checkUuids, checkUsers, restrictList, excludeList, excludeUserList, actionList, Collections.emptyList(), location, radius, rowData, startTime, endTime, limitOffset, limitCount, restrictWorld, lookup, false);
        return convertLookupResultRaw(lookupResult);
    }

    protected static List<Object[]> performLookupRaw(Statement statement, CommandSender user, List<String> checkUuids, List<String> checkUsers, List<Object> restrictList, Map<Object, Boolean> excludeList, List<String> excludeUserList, List<Integer> actionList, EntityActionFilter entityActionFilter, Set<UUID> loadedEntityUuids, Set<UUID> loadedEntityCandidates, Location location, Integer[] radius, Long[] rowData, long startTime, long endTime, int limitOffset, int limitCount, boolean restrictWorld, boolean lookup, Integer entityContainerId) {
        LookupResult<?> lookupResult = LookupRaw.performLookup(statement, user, checkUuids, checkUsers, restrictList, excludeList, excludeUserList, actionList, Collections.emptyList(), location, radius, rowData, startTime, endTime, limitOffset, limitCount, restrictWorld, lookup, false);
        return convertLookupResultRaw(lookupResult);
    }

    // Maintain backward compatibility
    private static ResultSet rawLookupResultSet(Statement statement, CommandSender user, List<String> checkUuids, List<String> checkUsers, List<Object> restrictList, Map<Object, Boolean> excludeList, List<String> excludeUserList, List<Integer> actionList, Location location, Integer[] radius, Long[] rowData, long startTime, long endTime, int limitOffset, int limitCount, boolean restrictWorld, boolean lookup, boolean count) {
        return LookupRaw.rawLookupResultSet(statement, user, checkUuids, checkUsers, restrictList, excludeList, excludeUserList, actionList, Collections.emptyList(), location, radius, rowData, startTime, endTime, limitOffset, limitCount, restrictWorld, lookup, count);
    }

    public static List<String[]> convertLookupResult(Statement statement, LookupResult<?> lookupResult) {
        return LookupConverter.convertRawLookup(statement, convertLookupResultRaw(lookupResult));
    }

    private static List<Object[]> convertLookupResultRaw(LookupResult<?> lookupResult) {
        if (lookupResult == null) {
            return null;
        }

        List<Object[]> result = new ArrayList<>();
        if (lookupResult instanceof CommonLookupResult commonLookupResult) {
            for (CommonLookupData data : commonLookupResult.data()) {
                if (data.table() == null) {
                    result.add(new Object[] { data.rowId(), (int) data.time(), data.userId(), data.x(), data.y(), data.z(), data.type(), data.data(), data.action(), data.rolledBack(), data.worldId(), data.amount(), data.metadata(), data.blockData() });
                }
                else {
                    result.add(new Object[] { data.rowId(), (int) data.time(), data.userId(), data.x(), data.y(), data.z(), data.type(), data.data(), data.action(), data.rolledBack(), data.worldId(), data.amount(), data.metadata(), data.blockData(), data.table() });
                }
            }
        }
        else if (lookupResult instanceof ChatLookupResult chatLookupResult) {
            for (ChatLookupData data : chatLookupResult.data()) {
                result.add(new Object[] { data.rowId(), (int) data.time(), data.userId(), data.message() });
            }
        }
        else if (lookupResult instanceof SessionLookupResult sessionLookupResult) {
            for (SessionLookupData data : sessionLookupResult.data()) {
                result.add(new Object[] { data.rowId(), (int) data.time(), data.userId(), data.worldId(), data.x(), data.y(), data.z(), data.action() });
            }
        }
        else if (lookupResult instanceof SignLookupResult signLookupResult) {
            for (SignLookupData data : signLookupResult.data()) {
                result.add(new Object[] { data.rowId(), (int) data.time(), data.userId(), data.worldId(), data.x(), data.y(), data.z(), data.text() });
            }
        }
        else if (lookupResult instanceof UsernameHistoryLookupResult usernameHistoryLookupResult) {
            for (UsernameHistoryData data : usernameHistoryLookupResult.data()) {
                result.add(new Object[] { data.rowId(), (int) data.time(), data.uuid(), data.username() });
            }
        }

        return result;
    }
}
