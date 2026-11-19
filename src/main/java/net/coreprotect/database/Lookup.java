package net.coreprotect.database;

import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.command.CommandSender;

import net.coreprotect.consumer.Consumer;
import net.coreprotect.consumer.Queue;
import net.coreprotect.model.action.EntityActionFilter;
import net.coreprotect.model.action.LookupActions;
import net.coreprotect.model.lookup.EntityLookupContext;
import net.coreprotect.model.lookup.LookupCursor;
import net.coreprotect.model.lookup.LookupPage;
import net.coreprotect.model.lookup.LookupRollbackState;
import net.coreprotect.model.lookup.LookupSummaryPage;
import net.coreprotect.model.lookup.LookupSummaryRow;

public class Lookup extends Queue {

    public static long countLookupRows(Statement statement, CommandSender user, List<String> checkUuids, List<String> checkUsers, List<Object> restrictList, Map<Object, Boolean> excludeList, List<String> excludeUserList, List<Integer> actionList, Location location, Integer[] radius, Long[] rowData, long startTime, long endTime, boolean restrictWorld, boolean lookup) {
        return countLookupRows(statement, user, checkUuids, checkUsers, restrictList, excludeList, excludeUserList, actionList, Collections.emptyList(), location, radius, rowData, startTime, endTime, restrictWorld, lookup);
    }

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
        return countLookupRows(statement, user, checkUuids, checkUsers, restrictList, excludeList, excludeUserList, actionList, entityActionFilter, messageFilters, loadedEntityUuids, loadedEntityCandidates, location, radius, rowData, startTime, endTime, restrictWorld, lookup, entityContainerId, LookupRollbackState.ANY);
    }

    public static long countLookupRows(Statement statement, CommandSender user, List<String> checkUuids, List<String> checkUsers, List<Object> restrictList, Map<Object, Boolean> excludeList, List<String> excludeUserList, List<Integer> actionList, EntityActionFilter entityActionFilter, List<String> messageFilters, Set<UUID> loadedEntityUuids, Set<UUID> loadedEntityCandidates, Location location, Integer[] radius, Long[] rowData, long startTime, long endTime, boolean restrictWorld, boolean lookup, Integer entityContainerId, LookupRollbackState rollbackState) {
        EntityLookupContext entityContext = EntityLookupContext.legacy(loadedEntityUuids, loadedEntityCandidates);
        return countLookupRows(statement, user, checkUuids, checkUsers, restrictList, excludeList, excludeUserList, actionList, entityActionFilter, messageFilters, entityContext, location, radius, rowData, startTime, endTime, restrictWorld, lookup, entityContainerId, rollbackState);
    }

    public static long countLookupRows(Statement statement, CommandSender user, List<String> checkUuids, List<String> checkUsers, List<Object> restrictList, Map<Object, Boolean> excludeList, List<String> excludeUserList, List<Integer> actionList, EntityActionFilter entityActionFilter, List<String> messageFilters, EntityLookupContext entityContext, Location location, Integer[] radius, Long[] rowData, long startTime, long endTime, boolean restrictWorld, boolean lookup, Integer entityContainerId, LookupRollbackState rollbackState) {
        return countLookupRows(statement, user, checkUuids, checkUsers, restrictList, excludeList, excludeUserList, actionList, entityActionFilter, messageFilters, entityContext, location, radius, rowData, startTime, endTime, restrictWorld, lookup, entityContainerId, rollbackState, 0);
    }

    public static long countLookupRows(Statement statement, CommandSender user, List<String> checkUuids, List<String> checkUsers, List<Object> restrictList, Map<Object, Boolean> excludeList, List<String> excludeUserList, List<Integer> actionList, EntityActionFilter entityActionFilter, List<String> messageFilters, EntityLookupContext entityContext, Location location, Integer[] radius, Long[] rowData, long startTime, long endTime, boolean restrictWorld, boolean lookup, Integer entityContainerId, LookupRollbackState rollbackState, int explicitWorldId) {
        return countLookupRows(statement, user, checkUuids, checkUsers, restrictList, excludeList, excludeUserList, actionList, entityActionFilter, messageFilters, entityContext, location, radius, rowData, startTime, endTime, restrictWorld, lookup, entityContainerId, rollbackState, explicitWorldId, false);
    }

    public static long countLookupRows(Statement statement, CommandSender user, List<String> checkUuids, List<String> checkUsers, List<Object> restrictList, Map<Object, Boolean> excludeList, List<String> excludeUserList, List<Integer> actionList, EntityActionFilter entityActionFilter, List<String> messageFilters, EntityLookupContext entityContext, Location location, Integer[] radius, Long[] rowData, long startTime, long endTime, boolean restrictWorld, boolean lookup, Integer entityContainerId, LookupRollbackState rollbackState, int explicitWorldId, boolean failOnError) {
        Long rows = 0L;
        boolean paused = false;

        try {
            paused = Consumer.claimLookupPause();

            try (ResultSet results = LookupRaw.rawLookupResultSet(statement, user, checkUuids, checkUsers, restrictList, excludeList, excludeUserList, actionList, entityActionFilter, messageFilters, entityContext, location, radius, null, startTime, endTime, -1, -1, restrictWorld, lookup, true, entityContainerId, rollbackState, explicitWorldId)) {
                if (!hasQueryResult(results, failOnError)) {
                    return 0L;
                }
                while (results.next()) {
                    int resultTable = results.getInt("tbl");
                    long count = results.getLong("count");
                    if (rowData != null && resultTable >= 0 && resultTable < rowData.length) {
                        rowData[resultTable] = count;
                    }
                    rows += count;
                }
            }
        }
        catch (Exception e) {
            handleQueryFailure(e, failOnError);
        }
        finally {
            if (paused) {
                Consumer.releaseOperationPause();
            }
        }

        return rows;
    }

    public static long countSummaryRows(Statement statement, CommandSender user, List<String> checkUuids, List<String> checkUsers, List<Object> restrictList, Map<Object, Boolean> excludeList, List<String> excludeUserList, List<Integer> actionList, EntityActionFilter entityActionFilter, Set<UUID> loadedEntityUuids, Set<UUID> loadedEntityCandidates, Location location, Integer[] radius, long startTime, long endTime, boolean restrictWorld, Integer entityContainerId) {
        return countSummaryRows(statement, user, checkUuids, checkUsers, restrictList, excludeList, excludeUserList, actionList, entityActionFilter, loadedEntityUuids, loadedEntityCandidates, location, radius, startTime, endTime, restrictWorld, entityContainerId, LookupRollbackState.ANY);
    }

    public static long countSummaryRows(Statement statement, CommandSender user, List<String> checkUuids, List<String> checkUsers, List<Object> restrictList, Map<Object, Boolean> excludeList, List<String> excludeUserList, List<Integer> actionList, EntityActionFilter entityActionFilter, Set<UUID> loadedEntityUuids, Set<UUID> loadedEntityCandidates, Location location, Integer[] radius, long startTime, long endTime, boolean restrictWorld, Integer entityContainerId, LookupRollbackState rollbackState) {
        EntityLookupContext entityContext = EntityLookupContext.legacy(loadedEntityUuids, loadedEntityCandidates);
        return countSummaryRows(statement, user, checkUuids, checkUsers, restrictList, excludeList, excludeUserList, actionList, entityActionFilter, entityContext, location, radius, startTime, endTime, restrictWorld, entityContainerId, rollbackState);
    }

    public static long countSummaryRows(Statement statement, CommandSender user, List<String> checkUuids, List<String> checkUsers, List<Object> restrictList, Map<Object, Boolean> excludeList, List<String> excludeUserList, List<Integer> actionList, EntityActionFilter entityActionFilter, EntityLookupContext entityContext, Location location, Integer[] radius, long startTime, long endTime, boolean restrictWorld, Integer entityContainerId, LookupRollbackState rollbackState) {
        return countSummaryRows(statement, user, checkUuids, checkUsers, restrictList, excludeList, excludeUserList, actionList, entityActionFilter, entityContext, location, radius, startTime, endTime, restrictWorld, entityContainerId, rollbackState, 0);
    }

    public static long countSummaryRows(Statement statement, CommandSender user, List<String> checkUuids, List<String> checkUsers, List<Object> restrictList, Map<Object, Boolean> excludeList, List<String> excludeUserList, List<Integer> actionList, EntityActionFilter entityActionFilter, EntityLookupContext entityContext, Location location, Integer[] radius, long startTime, long endTime, boolean restrictWorld, Integer entityContainerId, LookupRollbackState rollbackState, int explicitWorldId) {
        return countSummaryRows(statement, user, checkUuids, checkUsers, restrictList, excludeList, excludeUserList, actionList, entityActionFilter, entityContext, location, radius, startTime, endTime, restrictWorld, entityContainerId, rollbackState, explicitWorldId, false);
    }

    public static long countSummaryRows(Statement statement, CommandSender user, List<String> checkUuids, List<String> checkUsers, List<Object> restrictList, Map<Object, Boolean> excludeList, List<String> excludeUserList, List<Integer> actionList, EntityActionFilter entityActionFilter, EntityLookupContext entityContext, Location location, Integer[] radius, long startTime, long endTime, boolean restrictWorld, Integer entityContainerId, LookupRollbackState rollbackState, int explicitWorldId, boolean failOnError) {
        if (!hasSummaryActions(actionList)) {
            return 0L;
        }

        boolean paused = false;
        try {
            paused = Consumer.claimLookupPause();
            try (ResultSet results = LookupRaw.rawSummaryResultSet(statement, user, checkUuids, checkUsers, restrictList, excludeList, excludeUserList, actionList, entityActionFilter, entityContext, location, radius, startTime, endTime, -1, -1, restrictWorld, entityContainerId, true, rollbackState, explicitWorldId)) {
                return hasQueryResult(results, failOnError) && results.next() ? results.getLong("count") : 0L;
            }
        }
        catch (Exception e) {
            handleQueryFailure(e, failOnError);
            return 0L;
        }
        finally {
            if (paused) {
                Consumer.releaseOperationPause();
            }
        }
    }

    public static List<LookupSummaryRow> performSummaryLookup(Statement statement, CommandSender user, List<String> checkUuids, List<String> checkUsers, List<Object> restrictList, Map<Object, Boolean> excludeList, List<String> excludeUserList, List<Integer> actionList, EntityActionFilter entityActionFilter, Set<UUID> loadedEntityUuids, Set<UUID> loadedEntityCandidates, Location location, Integer[] radius, long startTime, long endTime, int limitOffset, int limitCount, boolean restrictWorld, Integer entityContainerId) {
        return performSummaryLookup(statement, user, checkUuids, checkUsers, restrictList, excludeList, excludeUserList, actionList, entityActionFilter, loadedEntityUuids, loadedEntityCandidates, location, radius, startTime, endTime, limitOffset, limitCount, restrictWorld, entityContainerId, LookupRollbackState.ANY);
    }

    public static List<LookupSummaryRow> performSummaryLookup(Statement statement, CommandSender user, List<String> checkUuids, List<String> checkUsers, List<Object> restrictList, Map<Object, Boolean> excludeList, List<String> excludeUserList, List<Integer> actionList, EntityActionFilter entityActionFilter, Set<UUID> loadedEntityUuids, Set<UUID> loadedEntityCandidates, Location location, Integer[] radius, long startTime, long endTime, int limitOffset, int limitCount, boolean restrictWorld, Integer entityContainerId, LookupRollbackState rollbackState) {
        EntityLookupContext entityContext = EntityLookupContext.legacy(loadedEntityUuids, loadedEntityCandidates);
        return performSummaryLookup(statement, user, checkUuids, checkUsers, restrictList, excludeList, excludeUserList, actionList, entityActionFilter, entityContext, location, radius, startTime, endTime, limitOffset, limitCount, restrictWorld, entityContainerId, rollbackState);
    }

    public static List<LookupSummaryRow> performSummaryLookup(Statement statement, CommandSender user, List<String> checkUuids, List<String> checkUsers, List<Object> restrictList, Map<Object, Boolean> excludeList, List<String> excludeUserList, List<Integer> actionList, EntityActionFilter entityActionFilter, EntityLookupContext entityContext, Location location, Integer[] radius, long startTime, long endTime, int limitOffset, int limitCount, boolean restrictWorld, Integer entityContainerId, LookupRollbackState rollbackState) {
        return performSummaryLookup(statement, user, checkUuids, checkUsers, restrictList, excludeList, excludeUserList, actionList, entityActionFilter, entityContext, location, radius, startTime, endTime, limitOffset, limitCount, restrictWorld, entityContainerId, rollbackState, 0);
    }

    public static List<LookupSummaryRow> performSummaryLookup(Statement statement, CommandSender user, List<String> checkUuids, List<String> checkUsers, List<Object> restrictList, Map<Object, Boolean> excludeList, List<String> excludeUserList, List<Integer> actionList, EntityActionFilter entityActionFilter, EntityLookupContext entityContext, Location location, Integer[] radius, long startTime, long endTime, int limitOffset, int limitCount, boolean restrictWorld, Integer entityContainerId, LookupRollbackState rollbackState, int explicitWorldId) {
        return performSummaryLookup(statement, user, checkUuids, checkUsers, restrictList, excludeList, excludeUserList, actionList, entityActionFilter, entityContext, location, radius, startTime, endTime, limitOffset, limitCount, restrictWorld, entityContainerId, rollbackState, explicitWorldId, false);
    }

    public static List<LookupSummaryRow> performSummaryLookup(Statement statement, CommandSender user, List<String> checkUuids, List<String> checkUsers, List<Object> restrictList, Map<Object, Boolean> excludeList, List<String> excludeUserList, List<Integer> actionList, EntityActionFilter entityActionFilter, EntityLookupContext entityContext, Location location, Integer[] radius, long startTime, long endTime, int limitOffset, int limitCount, boolean restrictWorld, Integer entityContainerId, LookupRollbackState rollbackState, int explicitWorldId, boolean failOnError) {
        if (!hasSummaryActions(actionList)) {
            return Collections.emptyList();
        }

        List<LookupSummaryRow> rows = new ArrayList<>();
        boolean paused = false;
        try {
            paused = Consumer.claimLookupPause();
            try (ResultSet results = LookupRaw.rawSummaryResultSet(statement, user, checkUuids, checkUsers, restrictList, excludeList, excludeUserList, actionList, entityActionFilter, entityContext, location, radius, startTime, endTime, limitOffset, limitCount, restrictWorld, entityContainerId, false, rollbackState, explicitWorldId)) {
                if (!hasQueryResult(results, failOnError)) {
                    return rows;
                }
                while (results.next()) {
                    rows.add(summaryRow(results));
                }
            }
        }
        catch (Exception e) {
            handleQueryFailure(e, failOnError);
        }
        finally {
            if (paused) {
                Consumer.releaseOperationPause();
            }
        }
        return rows;
    }

    public static LookupSummaryPage performSummaryLookupPage(Statement statement, CommandSender user, List<String> checkUuids, List<String> checkUsers, List<Object> restrictList, Map<Object, Boolean> excludeList, List<String> excludeUserList, List<Integer> actionList, EntityActionFilter entityActionFilter, Set<UUID> loadedEntityUuids, Set<UUID> loadedEntityCandidates, Location location, Integer[] radius, long startTime, long endTime, int limitOffset, int limitCount, boolean restrictWorld, Integer entityContainerId) {
        return performSummaryLookupPage(statement, user, checkUuids, checkUsers, restrictList, excludeList, excludeUserList, actionList, entityActionFilter, loadedEntityUuids, loadedEntityCandidates, location, radius, startTime, endTime, limitOffset, limitCount, restrictWorld, entityContainerId, LookupRollbackState.ANY);
    }

    public static LookupSummaryPage performSummaryLookupPage(Statement statement, CommandSender user, List<String> checkUuids, List<String> checkUsers, List<Object> restrictList, Map<Object, Boolean> excludeList, List<String> excludeUserList, List<Integer> actionList, EntityActionFilter entityActionFilter, Set<UUID> loadedEntityUuids, Set<UUID> loadedEntityCandidates, Location location, Integer[] radius, long startTime, long endTime, int limitOffset, int limitCount, boolean restrictWorld, Integer entityContainerId, LookupRollbackState rollbackState) {
        EntityLookupContext entityContext = EntityLookupContext.legacy(loadedEntityUuids, loadedEntityCandidates);
        return performSummaryLookupPage(statement, user, checkUuids, checkUsers, restrictList, excludeList, excludeUserList, actionList, entityActionFilter, entityContext, location, radius, startTime, endTime, limitOffset, limitCount, restrictWorld, entityContainerId, rollbackState);
    }

    public static LookupSummaryPage performSummaryLookupPage(Statement statement, CommandSender user, List<String> checkUuids, List<String> checkUsers, List<Object> restrictList, Map<Object, Boolean> excludeList, List<String> excludeUserList, List<Integer> actionList, EntityActionFilter entityActionFilter, EntityLookupContext entityContext, Location location, Integer[] radius, long startTime, long endTime, int limitOffset, int limitCount, boolean restrictWorld, Integer entityContainerId, LookupRollbackState rollbackState) {
        return performSummaryLookupPage(statement, user, checkUuids, checkUsers, restrictList, excludeList, excludeUserList, actionList, entityActionFilter, entityContext, location, radius, startTime, endTime, limitOffset, limitCount, restrictWorld, entityContainerId, rollbackState, 0);
    }

    public static LookupSummaryPage performSummaryLookupPage(Statement statement, CommandSender user, List<String> checkUuids, List<String> checkUsers, List<Object> restrictList, Map<Object, Boolean> excludeList, List<String> excludeUserList, List<Integer> actionList, EntityActionFilter entityActionFilter, EntityLookupContext entityContext, Location location, Integer[] radius, long startTime, long endTime, int limitOffset, int limitCount, boolean restrictWorld, Integer entityContainerId, LookupRollbackState rollbackState, int explicitWorldId) {
        return performSummaryLookupPage(statement, user, checkUuids, checkUsers, restrictList, excludeList, excludeUserList, actionList, entityActionFilter, entityContext, location, radius, startTime, endTime, limitOffset, limitCount, restrictWorld, entityContainerId, rollbackState, explicitWorldId, false);
    }

    public static LookupSummaryPage performSummaryLookupPage(Statement statement, CommandSender user, List<String> checkUuids, List<String> checkUsers, List<Object> restrictList, Map<Object, Boolean> excludeList, List<String> excludeUserList, List<Integer> actionList, EntityActionFilter entityActionFilter, EntityLookupContext entityContext, Location location, Integer[] radius, long startTime, long endTime, int limitOffset, int limitCount, boolean restrictWorld, Integer entityContainerId, LookupRollbackState rollbackState, int explicitWorldId, boolean failOnError) {
        if (!hasSummaryActions(actionList)) {
            return new LookupSummaryPage(0L, Collections.emptyList());
        }

        List<LookupSummaryRow> rows = new ArrayList<>();
        long totalRows = 0L;
        boolean paused = false;
        try {
            paused = Consumer.claimLookupPause();
            try (ResultSet results = LookupRaw.rawSummaryPageResultSet(statement, user, checkUuids, checkUsers, restrictList, excludeList, excludeUserList, actionList, entityActionFilter, entityContext, location, radius, startTime, endTime, limitOffset, limitCount, restrictWorld, entityContainerId, rollbackState, explicitWorldId)) {
                if (!hasQueryResult(results, failOnError)) {
                    return new LookupSummaryPage(0L, rows);
                }
                while (results.next()) {
                    if (rows.isEmpty()) {
                        totalRows = results.getLong("total_count");
                    }
                    rows.add(summaryRow(results));
                }
            }
        }
        catch (Exception e) {
            handleQueryFailure(e, failOnError);
        }
        finally {
            if (paused) {
                Consumer.releaseOperationPause();
            }
        }
        return new LookupSummaryPage(totalRows, rows);
    }

    public static boolean supportsSummaryWindowFunctions(Statement statement) {
        try {
            DatabaseMetaData metadata = statement.getConnection().getMetaData();
            String product = metadata.getDatabaseProductName().toLowerCase(java.util.Locale.ROOT);
            if (product.contains("clickhouse") || product.contains("duckdb")) {
                return true;
            }
            int major = metadata.getDatabaseMajorVersion();
            int minor = metadata.getDatabaseMinorVersion();
            if (product.contains("sqlite")) {
                return major > 3 || (major == 3 && minor >= 25);
            }
            if (product.contains("mariadb")) {
                return major > 10 || (major == 10 && minor >= 2);
            }
            return product.contains("mysql") && major >= 8;
        }
        catch (Exception e) {
            return false;
        }
    }

    private static LookupSummaryRow summaryRow(ResultSet results) throws Exception {
        return new LookupSummaryRow(results.getInt("user"), results.getInt("type"), results.getLong("removed_amount"), results.getLong("placed_amount"), results.getLong("amount"));
    }

    private static boolean hasSummaryActions(List<Integer> actionList) {
        if (actionList.contains(LookupActions.CONTAINER) || actionList.contains(LookupActions.INSPECT_CONTAINER)) {
            return true;
        }
        if (actionList.contains(LookupActions.CHAT) || actionList.contains(LookupActions.COMMAND) || actionList.contains(LookupActions.SESSION) || actionList.contains(LookupActions.USERNAME) || actionList.contains(LookupActions.SIGN)) {
            return false;
        }
        return actionList.isEmpty() || actionList.contains(LookupActions.BLOCK_BREAK) || actionList.contains(LookupActions.BLOCK_PLACE) || actionList.contains(LookupActions.ITEM);
    }

    public static List<String[]> performLookup(Statement statement, CommandSender user, List<String> checkUuids, List<String> checkUsers, List<Object> restrictList, Map<Object, Boolean> excludeList, List<String> excludeUserList, List<Integer> actionList, Location location, Integer[] radius, long startTime, long endTime, boolean restrictWorld, boolean lookup) {
        List<String[]> newList = new ArrayList<>();

        try {
            List<Object[]> lookupList = LookupRaw.performLookupRaw(statement, user, checkUuids, checkUsers, restrictList, excludeList, excludeUserList, actionList, location, radius, null, startTime, endTime, -1, -1, restrictWorld, lookup);
            newList = LookupConverter.convertRawLookup(statement, lookupList);
        }
        catch (Exception e) {
            Database.reportDatabaseFailure(e);
        }

        return newList;
    }

    public static List<String[]> performPartialLookup(Statement statement, CommandSender user, List<String> checkUuids, List<String> checkUsers, List<Object> restrictList, Map<Object, Boolean> excludeList, List<String> excludeUserList, List<Integer> actionList, Location location, Integer[] radius, Long[] rowData, long startTime, long endTime, int limitOffset, int limitCount, boolean restrictWorld, boolean lookup) {
        return performPartialLookup(statement, user, checkUuids, checkUsers, restrictList, excludeList, excludeUserList, actionList, Collections.emptyList(), location, radius, rowData, startTime, endTime, limitOffset, limitCount, restrictWorld, lookup);
    }

    public static List<String[]> performPartialLookup(Statement statement, CommandSender user, List<String> checkUuids, List<String> checkUsers, List<Object> restrictList, Map<Object, Boolean> excludeList, List<String> excludeUserList, List<Integer> actionList, List<String> messageFilters, Location location, Integer[] radius, Long[] rowData, long startTime, long endTime, int limitOffset, int limitCount, boolean restrictWorld, boolean lookup) {
        return performPartialLookup(statement, user, checkUuids, checkUsers, restrictList, excludeList, excludeUserList, actionList, messageFilters, Collections.emptySet(), Collections.emptySet(), location, radius, rowData, startTime, endTime, limitOffset, limitCount, restrictWorld, lookup);
    }

    public static List<String[]> performPartialLookup(Statement statement, CommandSender user, List<String> checkUuids, List<String> checkUsers, List<Object> restrictList, Map<Object, Boolean> excludeList, List<String> excludeUserList, List<Integer> actionList, List<String> messageFilters, Set<UUID> loadedEntityUuids, Set<UUID> loadedEntityCandidates, Location location, Integer[] radius, Long[] rowData, long startTime, long endTime, int limitOffset, int limitCount, boolean restrictWorld, boolean lookup) {
        return performPartialLookup(statement, user, checkUuids, checkUsers, restrictList, excludeList, excludeUserList, actionList, EntityActionFilter.DEFAULT, messageFilters, loadedEntityUuids, loadedEntityCandidates, location, radius, rowData, startTime, endTime, limitOffset, limitCount, restrictWorld, lookup);
    }

    public static List<String[]> performPartialLookup(Statement statement, CommandSender user, List<String> checkUuids, List<String> checkUsers, List<Object> restrictList, Map<Object, Boolean> excludeList, List<String> excludeUserList, List<Integer> actionList, EntityActionFilter entityActionFilter, List<String> messageFilters, Set<UUID> loadedEntityUuids, Set<UUID> loadedEntityCandidates, Location location, Integer[] radius, Long[] rowData, long startTime, long endTime, int limitOffset, int limitCount, boolean restrictWorld, boolean lookup) {
        return performPartialLookup(statement, user, checkUuids, checkUsers, restrictList, excludeList, excludeUserList, actionList, entityActionFilter, messageFilters, loadedEntityUuids, loadedEntityCandidates, location, radius, rowData, startTime, endTime, limitOffset, limitCount, restrictWorld, lookup, null);
    }

    public static List<String[]> performPartialLookup(Statement statement, CommandSender user, List<String> checkUuids, List<String> checkUsers, List<Object> restrictList, Map<Object, Boolean> excludeList, List<String> excludeUserList, List<Integer> actionList, EntityActionFilter entityActionFilter, List<String> messageFilters, Set<UUID> loadedEntityUuids, Set<UUID> loadedEntityCandidates, Location location, Integer[] radius, Long[] rowData, long startTime, long endTime, int limitOffset, int limitCount, boolean restrictWorld, boolean lookup, Integer entityContainerId) {
        return performPartialLookup(statement, user, checkUuids, checkUsers, restrictList, excludeList, excludeUserList, actionList, entityActionFilter, messageFilters, loadedEntityUuids, loadedEntityCandidates, location, radius, rowData, startTime, endTime, limitOffset, limitCount, restrictWorld, lookup, entityContainerId, LookupRollbackState.ANY);
    }

    public static List<String[]> performPartialLookup(Statement statement, CommandSender user, List<String> checkUuids, List<String> checkUsers, List<Object> restrictList, Map<Object, Boolean> excludeList, List<String> excludeUserList, List<Integer> actionList, EntityActionFilter entityActionFilter, List<String> messageFilters, Set<UUID> loadedEntityUuids, Set<UUID> loadedEntityCandidates, Location location, Integer[] radius, Long[] rowData, long startTime, long endTime, int limitOffset, int limitCount, boolean restrictWorld, boolean lookup, Integer entityContainerId, LookupRollbackState rollbackState) {
        EntityLookupContext entityContext = EntityLookupContext.legacy(loadedEntityUuids, loadedEntityCandidates);
        return performPartialLookup(statement, user, checkUuids, checkUsers, restrictList, excludeList, excludeUserList, actionList, entityActionFilter, messageFilters, entityContext, location, radius, rowData, startTime, endTime, limitOffset, limitCount, restrictWorld, lookup, entityContainerId, rollbackState);
    }

    public static List<String[]> performPartialLookup(Statement statement, CommandSender user, List<String> checkUuids, List<String> checkUsers, List<Object> restrictList, Map<Object, Boolean> excludeList, List<String> excludeUserList, List<Integer> actionList, EntityActionFilter entityActionFilter, List<String> messageFilters, EntityLookupContext entityContext, Location location, Integer[] radius, Long[] rowData, long startTime, long endTime, int limitOffset, int limitCount, boolean restrictWorld, boolean lookup, Integer entityContainerId, LookupRollbackState rollbackState) {
        return performPartialLookup(statement, user, checkUuids, checkUsers, restrictList, excludeList, excludeUserList, actionList, entityActionFilter, messageFilters, entityContext, location, radius, rowData, startTime, endTime, limitOffset, limitCount, restrictWorld, lookup, entityContainerId, rollbackState, 0);
    }

    public static List<String[]> performPartialLookup(Statement statement, CommandSender user, List<String> checkUuids, List<String> checkUsers, List<Object> restrictList, Map<Object, Boolean> excludeList, List<String> excludeUserList, List<Integer> actionList, EntityActionFilter entityActionFilter, List<String> messageFilters, EntityLookupContext entityContext, Location location, Integer[] radius, Long[] rowData, long startTime, long endTime, int limitOffset, int limitCount, boolean restrictWorld, boolean lookup, Integer entityContainerId, LookupRollbackState rollbackState, int explicitWorldId) {
        return performPartialLookup(statement, user, checkUuids, checkUsers, restrictList, excludeList, excludeUserList, actionList, entityActionFilter, messageFilters, entityContext, location, radius, rowData, startTime, endTime, limitOffset, limitCount, restrictWorld, lookup, entityContainerId, rollbackState, explicitWorldId, false);
    }

    public static List<String[]> performPartialLookup(Statement statement, CommandSender user, List<String> checkUuids, List<String> checkUsers, List<Object> restrictList, Map<Object, Boolean> excludeList, List<String> excludeUserList, List<Integer> actionList, EntityActionFilter entityActionFilter, List<String> messageFilters, EntityLookupContext entityContext, Location location, Integer[] radius, Long[] rowData, long startTime, long endTime, int limitOffset, int limitCount, boolean restrictWorld, boolean lookup, Integer entityContainerId, LookupRollbackState rollbackState, int explicitWorldId, boolean failOnError) {
        List<String[]> newList = new ArrayList<>();

        try {
            List<Object[]> lookupList = LookupRaw.performLookupRaw(statement, user, checkUuids, checkUsers, restrictList, excludeList, excludeUserList, actionList, entityActionFilter, messageFilters, entityContext, location, radius, rowData, startTime, endTime, limitOffset, limitCount, restrictWorld, lookup, entityContainerId, rollbackState, explicitWorldId);
            newList = hasQueryResult(lookupList, failOnError) ? LookupConverter.convertRawLookup(statement, lookupList) : null;
        }
        catch (Exception e) {
            handleQueryFailure(e, failOnError);
        }

        return newList;
    }

    public static LookupPage performDuckDBLookupPage(Statement statement, CommandSender user, List<String> checkUuids, List<String> checkUsers, List<Object> restrictList, Map<Object, Boolean> excludeList, List<String> excludeUserList, List<Integer> actionList, EntityActionFilter entityActionFilter, List<String> messageFilters, Set<UUID> loadedEntityUuids, Set<UUID> loadedEntityCandidates, Location location, Integer[] radius, Long[] rowData, long startTime, long endTime, int limitCount, boolean restrictWorld, boolean lookup, Integer entityContainerId, LookupRollbackState rollbackState) {
        EntityLookupContext entityContext = EntityLookupContext.legacy(loadedEntityUuids, loadedEntityCandidates);
        return performDuckDBLookupPage(statement, user, checkUuids, checkUsers, restrictList, excludeList, excludeUserList, actionList, entityActionFilter, messageFilters, entityContext, location, radius, rowData, startTime, endTime, limitCount, restrictWorld, lookup, entityContainerId, rollbackState);
    }

    public static LookupPage performDuckDBLookupPage(Statement statement, CommandSender user, List<String> checkUuids, List<String> checkUsers, List<Object> restrictList, Map<Object, Boolean> excludeList, List<String> excludeUserList, List<Integer> actionList, EntityActionFilter entityActionFilter, List<String> messageFilters, EntityLookupContext entityContext, Location location, Integer[] radius, Long[] rowData, long startTime, long endTime, int limitCount, boolean restrictWorld, boolean lookup, Integer entityContainerId, LookupRollbackState rollbackState) {
        return performDuckDBLookupPage(statement, user, checkUuids, checkUsers, restrictList, excludeList, excludeUserList, actionList, entityActionFilter, messageFilters, entityContext, location, radius, rowData, startTime, endTime, limitCount, restrictWorld, lookup, entityContainerId, rollbackState, 0);
    }

    public static LookupPage performDuckDBLookupPage(Statement statement, CommandSender user, List<String> checkUuids, List<String> checkUsers, List<Object> restrictList, Map<Object, Boolean> excludeList, List<String> excludeUserList, List<Integer> actionList, EntityActionFilter entityActionFilter, List<String> messageFilters, EntityLookupContext entityContext, Location location, Integer[] radius, Long[] rowData, long startTime, long endTime, int limitCount, boolean restrictWorld, boolean lookup, Integer entityContainerId, LookupRollbackState rollbackState, int explicitWorldId) {
        return performDuckDBLookupPage(statement, user, checkUuids, checkUsers, restrictList, excludeList, excludeUserList, actionList, entityActionFilter, messageFilters, entityContext, location, radius, rowData, startTime, endTime, limitCount, restrictWorld, lookup, entityContainerId, rollbackState, explicitWorldId, false);
    }

    public static LookupPage performDuckDBLookupPage(Statement statement, CommandSender user, List<String> checkUuids, List<String> checkUsers, List<Object> restrictList, Map<Object, Boolean> excludeList, List<String> excludeUserList, List<Integer> actionList, EntityActionFilter entityActionFilter, List<String> messageFilters, EntityLookupContext entityContext, Location location, Integer[] radius, Long[] rowData, long startTime, long endTime, int limitCount, boolean restrictWorld, boolean lookup, Integer entityContainerId, LookupRollbackState rollbackState, int explicitWorldId, boolean failOnError) {
        return performLookupPage(statement, user, checkUuids, checkUsers, restrictList, excludeList, excludeUserList, actionList, entityActionFilter, messageFilters, entityContext, location, radius, rowData, startTime, endTime, 0, limitCount, -1L, null, restrictWorld, lookup, entityContainerId, rollbackState, explicitWorldId, failOnError);
    }

    public static LookupPage performLookupPage(Statement statement, CommandSender user, List<String> checkUuids, List<String> checkUsers, List<Object> restrictList, Map<Object, Boolean> excludeList, List<String> excludeUserList, List<Integer> actionList, EntityActionFilter entityActionFilter, List<String> messageFilters, EntityLookupContext entityContext, Location location, Integer[] radius, Long[] rowData, long startTime, long endTime, int limitOffset, int limitCount, long knownTotalRows, LookupCursor cursor, boolean restrictWorld, boolean lookup, Integer entityContainerId, LookupRollbackState rollbackState) {
        return performLookupPage(statement, user, checkUuids, checkUsers, restrictList, excludeList, excludeUserList, actionList, entityActionFilter, messageFilters, entityContext, location, radius, rowData, startTime, endTime, limitOffset, limitCount, knownTotalRows, cursor, restrictWorld, lookup, entityContainerId, rollbackState, 0);
    }

    public static LookupPage performLookupPage(Statement statement, CommandSender user, List<String> checkUuids, List<String> checkUsers, List<Object> restrictList, Map<Object, Boolean> excludeList, List<String> excludeUserList, List<Integer> actionList, EntityActionFilter entityActionFilter, List<String> messageFilters, EntityLookupContext entityContext, Location location, Integer[] radius, Long[] rowData, long startTime, long endTime, int limitOffset, int limitCount, long knownTotalRows, LookupCursor cursor, boolean restrictWorld, boolean lookup, Integer entityContainerId, LookupRollbackState rollbackState, int explicitWorldId) {
        return performLookupPage(statement, user, checkUuids, checkUsers, restrictList, excludeList, excludeUserList, actionList, entityActionFilter, messageFilters, entityContext, location, radius, rowData, startTime, endTime, limitOffset, limitCount, knownTotalRows, cursor, restrictWorld, lookup, entityContainerId, rollbackState, explicitWorldId, false);
    }

    public static LookupPage performLookupPage(Statement statement, CommandSender user, List<String> checkUuids, List<String> checkUsers, List<Object> restrictList, Map<Object, Boolean> excludeList, List<String> excludeUserList, List<Integer> actionList, EntityActionFilter entityActionFilter, List<String> messageFilters, EntityLookupContext entityContext, Location location, Integer[] radius, Long[] rowData, long startTime, long endTime, int limitOffset, int limitCount, long knownTotalRows, LookupCursor cursor, boolean restrictWorld, boolean lookup, Integer entityContainerId, LookupRollbackState rollbackState, int explicitWorldId, boolean failOnError) {
        LookupRaw.RawLookupPage page = LookupRaw.performLookupPage(statement, user, checkUuids, checkUsers, restrictList, excludeList, excludeUserList, actionList, entityActionFilter, messageFilters, entityContext, location, radius, rowData, startTime, endTime, limitOffset, limitCount, knownTotalRows, cursor, restrictWorld, lookup, entityContainerId, rollbackState, explicitWorldId, failOnError);
        List<String[]> rows = LookupConverter.convertRawLookup(statement, page.getRows());
        return new LookupPage(page.getTotalRows(), rows, page.getNextCursor());
    }

    static boolean hasQueryResult(Object result, boolean failOnError) {
        if (result == null && failOnError) {
            throw new QueryFailure(null);
        }
        return result != null;
    }

    private static void handleQueryFailure(Exception failure, boolean failOnError) {
        if (failure instanceof QueryFailure) {
            throw (QueryFailure) failure;
        }
        if (failOnError) {
            throw new QueryFailure(failure);
        }
        Database.reportDatabaseFailure(failure);
    }

    public static final class QueryFailure extends RuntimeException {
        private static final long serialVersionUID = 1L;

        QueryFailure(Throwable cause) {
            super(cause);
        }
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
        return LookupRaw.performLookupRaw(statement, user, checkUuids, checkUsers, restrictList, excludeList, excludeUserList, actionList, location, radius, rowData, startTime, endTime, limitOffset, limitCount, restrictWorld, lookup);
    }

    protected static List<Object[]> performLookupRaw(Statement statement, CommandSender user, List<String> checkUuids, List<String> checkUsers, List<Object> restrictList, Map<Object, Boolean> excludeList, List<String> excludeUserList, List<Integer> actionList, Set<UUID> loadedEntityUuids, Set<UUID> loadedEntityCandidates, Location location, Integer[] radius, Long[] rowData, long startTime, long endTime, int limitOffset, int limitCount, boolean restrictWorld, boolean lookup) {
        return LookupRaw.performLookupRaw(statement, user, checkUuids, checkUsers, restrictList, excludeList, excludeUserList, actionList, Collections.emptyList(), loadedEntityUuids, loadedEntityCandidates, location, radius, rowData, startTime, endTime, limitOffset, limitCount, restrictWorld, lookup);
    }

    protected static List<Object[]> performLookupRaw(Statement statement, CommandSender user, List<String> checkUuids, List<String> checkUsers, List<Object> restrictList, Map<Object, Boolean> excludeList, List<String> excludeUserList, List<Integer> actionList, EntityActionFilter entityActionFilter, Set<UUID> loadedEntityUuids, Set<UUID> loadedEntityCandidates, Location location, Integer[] radius, Long[] rowData, long startTime, long endTime, int limitOffset, int limitCount, boolean restrictWorld, boolean lookup) {
        return LookupRaw.performLookupRaw(statement, user, checkUuids, checkUsers, restrictList, excludeList, excludeUserList, actionList, entityActionFilter, Collections.emptyList(), loadedEntityUuids, loadedEntityCandidates, location, radius, rowData, startTime, endTime, limitOffset, limitCount, restrictWorld, lookup);
    }

    protected static List<Object[]> performLookupRaw(Statement statement, CommandSender user, List<String> checkUuids, List<String> checkUsers, List<Object> restrictList, Map<Object, Boolean> excludeList, List<String> excludeUserList, List<Integer> actionList, EntityActionFilter entityActionFilter, Set<UUID> loadedEntityUuids, Set<UUID> loadedEntityCandidates, Location location, Integer[] radius, Long[] rowData, long startTime, long endTime, int limitOffset, int limitCount, boolean restrictWorld, boolean lookup, Integer entityContainerId) {
        return LookupRaw.performLookupRaw(statement, user, checkUuids, checkUsers, restrictList, excludeList, excludeUserList, actionList, entityActionFilter, Collections.emptyList(), loadedEntityUuids, loadedEntityCandidates, location, radius, rowData, startTime, endTime, limitOffset, limitCount, restrictWorld, lookup, entityContainerId);
    }

    protected static List<Object[]> performLookupRaw(Statement statement, CommandSender user, List<String> checkUuids, List<String> checkUsers, List<Object> restrictList, Map<Object, Boolean> excludeList, List<String> excludeUserList, List<Integer> actionList, EntityActionFilter entityActionFilter, EntityLookupContext entityContext, Location location, Integer[] radius, Long[] rowData, long startTime, long endTime, int limitOffset, int limitCount, boolean restrictWorld, boolean lookup, Integer entityContainerId) {
        return LookupRaw.performLookupRaw(statement, user, checkUuids, checkUsers, restrictList, excludeList, excludeUserList, actionList, entityActionFilter, Collections.emptyList(), entityContext, location, radius, rowData, startTime, endTime, limitOffset, limitCount, restrictWorld, lookup, entityContainerId, LookupRollbackState.ANY);
    }

}
