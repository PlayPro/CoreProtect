package net.coreprotect.database;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import net.coreprotect.CoreProtect;
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
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EntityType;

import net.coreprotect.bukkit.BukkitAdapter;
import net.coreprotect.config.Config;
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.consumer.Queue;
import net.coreprotect.database.statement.UserStatement;
import net.coreprotect.utility.EntityUtils;
import net.coreprotect.utility.MaterialUtils;
import net.coreprotect.utility.WorldUtils;
import org.jetbrains.annotations.Nullable;
import net.coreprotect.model.action.LookupActions;
import net.coreprotect.model.action.SignActions;
import net.coreprotect.model.item.ItemTransactionActions;
import net.coreprotect.model.lookup.LookupRollbackState;
import net.coreprotect.model.lookup.LookupSummaryRow;
import net.coreprotect.utility.ErrorReporter;

public class LookupRaw extends Queue {

    protected static LookupResult<?> performLookup(Statement statement, CommandSender user, List<String> checkUuids, List<String> checkUsers, List<Object> restrictList, Map<Object, Boolean> excludeList, List<String> excludeUserList, List<Integer> actionList, Location location, Integer[] radius, Long[] rowData, long startTime, long endTime, int limitOffset, int limitCount, boolean restrictWorld, boolean lookup, boolean countRows) {
        return performLookup(statement, user, checkUuids, checkUsers, restrictList, excludeList, excludeUserList, actionList, Collections.emptyList(), location, radius, rowData, startTime, endTime, limitOffset, limitCount, restrictWorld, lookup, countRows);
    }

    protected static LookupResult<?> performLookup(Statement statement, CommandSender user, List<String> checkUuids, List<String> checkUsers, List<Object> restrictList, Map<Object, Boolean> excludeList, List<String> excludeUserList, List<Integer> actionList, List<String> messageFilters, Location location, Integer[] radius, Long[] rowData, long startTime, long endTime, int limitOffset, int limitCount, boolean restrictWorld, boolean lookup, boolean countRows) {
        return performLookup(statement, user, checkUuids, checkUsers, restrictList, excludeList, excludeUserList, actionList, messageFilters, location, radius, rowData, startTime, endTime, limitOffset, limitCount, restrictWorld, lookup, countRows, LookupRollbackState.ANY);
    }

    protected static LookupResult<?> performLookup(Statement statement, CommandSender user, List<String> checkUuids, List<String> checkUsers, List<Object> restrictList, Map<Object, Boolean> excludeList, List<String> excludeUserList, List<Integer> actionList, List<String> messageFilters, Location location, Integer[] radius, Long[] rowData, long startTime, long endTime, int limitOffset, int limitCount, boolean restrictWorld, boolean lookup, boolean countRows, LookupRollbackState rollbackState) {
        List<Integer> invalidRollbackActions = new ArrayList<>();
        invalidRollbackActions.add(LookupActions.INTERACTION);

        if (!Config.getGlobal().ROLLBACK_ENTITIES && !actionList.contains(LookupActions.ENTITY_KILL)) {
            invalidRollbackActions.add(LookupActions.ENTITY_KILL);
        }

        if (LookupActions.isInventoryLookup(actionList)) {
            invalidRollbackActions.clear();
        }

        try (final ResultSet results = rawLookupResultSet(statement, user, checkUuids, checkUsers, restrictList, excludeList, excludeUserList, actionList, messageFilters, location, radius, rowData, startTime, endTime, limitOffset, limitCount, restrictWorld, lookup, countRows, rollbackState)) {
            if (results == null) {
                return null;
            }

            long rowCount = 0;

            if (actionList.contains(LookupActions.CHAT) || actionList.contains(LookupActions.COMMAND)) {
                final List<ChatLookupData> data = new ArrayList<>();

                while (results.next()) {
                    if (countRows) {
                        rowCount = results.getLong("count");
                    }
                    long resultId = results.getLong("id");
                    int resultTime = results.getInt("time");
                    int resultUserId = results.getInt("user");
                    String resultMessage = results.getString("message");

                    int resultWorldId = results.getInt("wid");
                    int resultX = results.getInt("x");
                    int resultY = results.getInt("y");
                    int resultZ = results.getInt("z");

                    boolean cancelled = results.getBoolean("cancelled");

                    data.add(new ChatLookupData(resultId, resultTime, resultUserId, resultMessage, cancelled, resultWorldId, resultX, resultY, resultZ));
                }

                return new ChatLookupResult(rowCount, data);
            } else if (actionList.contains(LookupActions.SESSION)) {
                final List<SessionLookupData> data = new ArrayList<>();

                while (results.next()) {
                    if (countRows) {
                        rowCount = results.getLong("count");
                    }
                    long resultId = results.getLong("id");
                    long resultTime = results.getLong("time");
                    int resultUserId = results.getInt("user");
                    int resultWorldId = results.getInt("wid");
                    int resultX = results.getInt("x");
                    int resultY = results.getInt("y");
                    int resultZ = results.getInt("z");
                    int resultAction = results.getInt("action");

                    data.add(new SessionLookupData(resultId, resultTime, resultUserId, resultWorldId, resultX, resultY, resultZ, resultAction));
                }

                return new SessionLookupResult(rowCount, data);
            } else if (actionList.contains(LookupActions.USERNAME)) {
                final List<UsernameHistoryData> data = new ArrayList<>();

                while (results.next()) {
                    if (countRows) {
                        rowCount = results.getLong("count");
                    }
                    long resultId = results.getLong("id");
                    long resultTime = results.getLong("time");
                    String resultUuid = results.getString("uuid");
                    String resultUser = results.getString("user");

                    data.add(new UsernameHistoryData(resultId, resultTime, resultUuid, resultUser));
                }

                return new UsernameHistoryLookupResult(rowCount, data);
            } else if (actionList.contains(LookupActions.SIGN)) {
                final List<SignLookupData> data = new ArrayList<>();

                while (results.next()) {
                    if (countRows) {
                        rowCount = results.getLong("count");
                    }
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
                    if (isFront && line1 != null && !line1.isEmpty()) {
                        message.append(line1);
                        if (!line1.endsWith(" ")) {
                            message.append(" ");
                        }
                    }
                    if (isFront && line2 != null && !line2.isEmpty()) {
                        message.append(line2);
                        if (!line2.endsWith(" ")) {
                            message.append(" ");
                        }
                    }
                    if (isFront && line3 != null && !line3.isEmpty()) {
                        message.append(line3);
                        if (!line3.endsWith(" ")) {
                            message.append(" ");
                        }
                    }
                    if (isFront && line4 != null && !line4.isEmpty()) {
                        message.append(line4);
                        if (!line4.endsWith(" ")) {
                            message.append(" ");
                        }
                    }
                    if (!isFront && line5 != null && !line5.isEmpty()) {
                        message.append(line5);
                        if (!line5.endsWith(" ")) {
                            message.append(" ");
                        }
                    }
                    if (!isFront && line6 != null && !line6.isEmpty()) {
                        message.append(line6);
                        if (!line6.endsWith(" ")) {
                            message.append(" ");
                        }
                    }
                    if (!isFront && line7 != null && !line7.isEmpty()) {
                        message.append(line7);
                        if (!line7.endsWith(" ")) {
                            message.append(" ");
                        }
                    }
                    if (!isFront && line8 != null && !line8.isEmpty()) {
                        message.append(line8);
                        if (!line8.endsWith(" ")) {
                            message.append(" ");
                        }
                    }

                    data.add(new SignLookupData(resultId, resultTime, resultUserId, resultWorldId, resultX, resultY, resultZ, message.toString()));
                }

                return new SignLookupResult(rowCount, data);
            } else {
                List<CommonLookupData> data = new ArrayList<>();

                while (results.next()) {
                    if (countRows) {
                        rowCount = results.getLong("count");
                    }
                    int resultData = 0;
                    int resultAmount = -1;
                    Integer resultTable = null;
                    String resultMeta;
                    String resultBlockData = null;
                    long resultId = results.getLong("id");
                    int resultUserId = results.getInt("user");
                    int resultAction = results.getInt("action");
                    int resultRolledBack = results.getInt("rolled_back");
                    int resultType = results.getInt("type");
                    long resultTime = results.getLong("time");
                    int resultX = results.getInt("x");
                    int resultY = results.getInt("y");
                    int resultZ = results.getInt("z");
                    int resultWorldId = results.getInt("wid");
                    int version = results.getInt("version");

                    if ((lookup && actionList.isEmpty()) || actionList.contains(LookupActions.CONTAINER) || actionList.contains(5) || actionList.contains(LookupActions.ITEM)) {
                        resultData = results.getInt("data");
                        resultAmount = results.getInt("amount");
                        resultMeta = results.getString("metadata");
                        resultTable = results.getInt("tbl");
                    } else {
                        resultData = results.getInt("data");
                        resultMeta = results.getString("meta");
                        resultBlockData = results.getString("blockdata");
                    }

                    boolean valid = true;
                    if (!lookup && invalidRollbackActions.contains(resultAction)) {
                        valid = false;
                    }

                    if (valid) {
                        data.add(new CommonLookupData(resultId, resultTime, resultUserId, resultX, resultY, resultZ, resultType, resultData, resultAction, resultRolledBack, resultWorldId, resultAmount, resultMeta, resultBlockData, resultTable, version));
                    }
                }

                return new CommonLookupResult(rowCount, data);
            }
        }
        catch (SQLException e) {
            ErrorReporter.report(e);
        }

        return null;
    }

    protected static long countSummaryRows(Statement statement, CommandSender user, List<String> checkUuids, List<String> checkUsers, List<Object> restrictList, Map<Object, Boolean> excludeList, List<String> excludeUserList, List<Integer> actionList, Location location, Integer[] radius, long startTime, long endTime, boolean restrictWorld, LookupRollbackState rollbackState) {
        try (ResultSet results = rawSummaryResultSet(statement, user, checkUuids, checkUsers, restrictList, excludeList, excludeUserList, actionList, location, radius, startTime, endTime, -1, -1, restrictWorld, true, true, rollbackState)) {
            if (results != null && results.next()) {
                return results.getLong("count");
            }
        }
        catch (SQLException e) {
            ErrorReporter.report(e);
        }
        return 0;
    }

    protected static List<LookupSummaryRow> performSummaryLookup(Statement statement, CommandSender user, List<String> checkUuids, List<String> checkUsers, List<Object> restrictList, Map<Object, Boolean> excludeList, List<String> excludeUserList, List<Integer> actionList, Location location, Integer[] radius, long startTime, long endTime, int limitOffset, int limitCount, boolean restrictWorld, LookupRollbackState rollbackState) {
        List<LookupSummaryRow> rows = new ArrayList<>();
        try (ResultSet results = rawSummaryResultSet(statement, user, checkUuids, checkUsers, restrictList, excludeList, excludeUserList, actionList, location, radius, startTime, endTime, limitOffset, limitCount, restrictWorld, true, false, rollbackState)) {
            if (results == null) {
                return rows;
            }

            while (results.next()) {
                rows.add(new LookupSummaryRow(results.getInt("user"), results.getInt("type"), results.getLong("removed_amount"), results.getLong("placed_amount"), results.getLong("amount")));
            }
        }
        catch (SQLException e) {
            ErrorReporter.report(e);
        }
        return rows;
    }

    private static @Nullable ResultSet rawSummaryResultSet(Statement statement, CommandSender user, List<String> checkUuids, List<String> checkUsers, List<Object> restrictList, Map<Object, Boolean> excludeList, List<String> excludeUserList, List<Integer> actionList, Location location, Integer[] radius, long startTime, long endTime, int limitOffset, int limitCount, boolean restrictWorld, boolean lookup, boolean countGroups) {
        return rawSummaryResultSet(statement, user, checkUuids, checkUsers, restrictList, excludeList, excludeUserList, actionList, location, radius, startTime, endTime, limitOffset, limitCount, restrictWorld, lookup, countGroups, LookupRollbackState.ANY);
    }

    private static @Nullable ResultSet rawSummaryResultSet(Statement statement, CommandSender user, List<String> checkUuids, List<String> checkUsers, List<Object> restrictList, Map<Object, Boolean> excludeList, List<String> excludeUserList, List<Integer> actionList, Location location, Integer[] radius, long startTime, long endTime, int limitOffset, int limitCount, boolean restrictWorld, boolean lookup, boolean countGroups, LookupRollbackState rollbackState) {
        return rawLookupResultSet(statement, user, checkUuids, checkUsers, restrictList, excludeList, excludeUserList, actionList, Collections.emptyList(), location, radius, null, startTime, endTime, limitOffset, limitCount, restrictWorld, lookup, false, rollbackState, true, countGroups);
    }

    static @Nullable ResultSet rawLookupResultSet(Statement statement, CommandSender user, List<String> checkUuids, List<String> checkUsers, List<Object> restrictList, Map<Object, Boolean> excludeList, List<String> excludeUserList, List<Integer> actionList, Location location, Integer[] radius, Long[] rowData, long startTime, long endTime, int limitOffset, int limitCount, boolean restrictWorld, boolean lookup, boolean countRows) {
        return rawLookupResultSet(statement, user, checkUuids, checkUsers, restrictList, excludeList, excludeUserList, actionList, Collections.emptyList(), location, radius, rowData, startTime, endTime, limitOffset, limitCount, restrictWorld, lookup, countRows);
    }

    static @Nullable ResultSet rawLookupResultSet(Statement statement, CommandSender user, List<String> checkUuids, List<String> checkUsers, List<Object> restrictList, Map<Object, Boolean> excludeList, List<String> excludeUserList, List<Integer> actionList, List<String> messageFilters, Location location, Integer[] radius, Long[] rowData, long startTime, long endTime, int limitOffset, int limitCount, boolean restrictWorld, boolean lookup, boolean countRows) {
        return rawLookupResultSet(statement, user, checkUuids, checkUsers, restrictList, excludeList, excludeUserList, actionList, messageFilters, location, radius, rowData, startTime, endTime, limitOffset, limitCount, restrictWorld, lookup, countRows, LookupRollbackState.ANY);
    }

    static @Nullable ResultSet rawLookupResultSet(Statement statement, CommandSender user, List<String> checkUuids, List<String> checkUsers, List<Object> restrictList, Map<Object, Boolean> excludeList, List<String> excludeUserList, List<Integer> actionList, List<String> messageFilters, Location location, Integer[] radius, Long[] rowData, long startTime, long endTime, int limitOffset, int limitCount, boolean restrictWorld, boolean lookup, boolean countRows, LookupRollbackState rollbackState) {
        return rawLookupResultSet(statement, user, checkUuids, checkUsers, restrictList, excludeList, excludeUserList, actionList, messageFilters, location, radius, rowData, startTime, endTime, limitOffset, limitCount, restrictWorld, lookup, countRows, rollbackState, false, false);
    }

    private static @Nullable ResultSet rawLookupResultSet(Statement statement, CommandSender user, List<String> checkUuids, List<String> checkUsers, List<Object> restrictList, Map<Object, Boolean> excludeList, List<String> excludeUserList, List<Integer> actionList, List<String> messageFilters, Location location, Integer[] radius, Long[] rowData, long startTime, long endTime, int limitOffset, int limitCount, boolean restrictWorld, boolean lookup, boolean countRows, LookupRollbackState rollbackState, boolean summary, boolean countGroups) {
        String query = "";

        try {
            List<Integer> validActions = Arrays.asList(LookupActions.BLOCK_BREAK, LookupActions.BLOCK_PLACE, LookupActions.INTERACTION, LookupActions.ENTITY_KILL);
            if (radius != null) {
                restrictWorld = true;
            }

            boolean inventoryQuery = LookupActions.isInventoryLookup(actionList);
            boolean validAction = false;
            String queryBlock = "";
            String queryEntity = "";
            String queryLimit = "";
            String queryLimitOffset = "";
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
            String unionLimit = "";
            String index = "";

            if (!checkUuids.isEmpty()) {
                String list = "";

                for (String value : checkUuids) {
                    if (list.isEmpty()) {
                        list = "'" + value + "'";
                    }
                    else {
                        list += ",'" + value + "'";
                    }
                }

                uuids = list;
            }

            if (!checkUsers.contains("#global")) {
                final StringBuilder checkUserText = new StringBuilder();

                for (String checkUser : checkUsers) {
                    if (!checkUser.equals("#container")) {
                        int userId = UserStatement.getId(statement.getConnection(), checkUser, true);

                        if (checkUserText.isEmpty()) {
                            checkUserText.append(userId);
                        }
                        else {
                            checkUserText.append(",").append(userId);
                        }
                    }
                }
                users = checkUserText.toString();
            }

            if (!restrictList.isEmpty()) {
                final StringBuilder includeListMaterial = new StringBuilder();
                final StringBuilder includeListEntity = new StringBuilder();

                for (Object restrictTarget : restrictList) {
                    String targetName = "";

                    if (restrictTarget instanceof Material) {
                        targetName = ((Material) restrictTarget).name();
                        if (includeListMaterial.isEmpty()) {
                            includeListMaterial.append(MaterialUtils.getBlockId(targetName, false));
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
                        if (includeListEntity.isEmpty()) {
                            includeListEntity.append(EntityUtils.getEntityId(targetName, false));
                        }
                        else {
                            includeListEntity.append(",").append(EntityUtils.getEntityId(targetName, false));
                        }
                    }
                    else if (restrictTarget instanceof String) {
                        int blockId = MaterialUtils.getBlockId((String) restrictTarget, false);
                        if (includeListMaterial.length() == 0) {
                            includeListMaterial.append(blockId);
                        }
                        else {
                            includeListMaterial.append(",").append(blockId);
                        }
                    }
                }

                includeBlock = includeListMaterial.toString();
                includeEntity = includeListEntity.toString();
            }

            if (!excludeList.isEmpty()) {
                final StringBuilder excludeListMaterial = new StringBuilder();
                final StringBuilder excludeListEntity = new StringBuilder();

                for (Object restrictTarget : excludeList.keySet()) {
                    String targetName = "";

                    if (restrictTarget instanceof Material) {
                        targetName = ((Material) restrictTarget).name();
                        if (excludeListMaterial.isEmpty()) {
                            excludeListMaterial.append(MaterialUtils.getBlockId(targetName, false));
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
                        if (excludeListEntity.isEmpty()) {
                            excludeListEntity.append(EntityUtils.getEntityId(targetName, false));
                        }
                        else {
                            excludeListEntity.append(",").append(EntityUtils.getEntityId(targetName, false));
                        }
                    }
                    else if (restrictTarget instanceof String) {
                        int blockId = MaterialUtils.getBlockId((String) restrictTarget, false);
                        if (blockId > -1) {
                            if (excludeListMaterial.length() == 0) {
                                excludeListMaterial.append(blockId);
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

            if (!excludeUserList.isEmpty()) {
                final StringBuilder excludeUserText = new StringBuilder();

                for (String excludeTarget : excludeUserList) {
                    int userId = UserStatement.getId(statement.getConnection(), excludeTarget, true);

                    if (excludeUserText.isEmpty()) {
                        excludeUserText.append(userId);
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

                        if (actionText.isEmpty()) {
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
                    break;
                }
            }

            if (restrictWorld) {
                int wid = WorldUtils.getWorldId(location.getWorld().getName());
                queryBlock = queryBlock + " wid=" + wid + " AND";
            }

            if (radius != null) {
                Integer xmin = radius[1];
                Integer xmax = radius[2];
                Integer ymin = radius[3];
                Integer ymax = radius[4];
                Integer zmin = radius[5];
                Integer zmax = radius[6];
                String queryY = "";

                if (ymin != null && ymax != null) {
                    queryY = " y >= '" + ymin + "' AND y <= '" + ymax + "' AND";
                }

                queryBlock = queryBlock + " x >= '" + xmin + "' AND x <= '" + xmax + "' AND z >= '" + zmin + "' AND z <= '" + zmax + "' AND" + queryY;
            }
            else if (actionList.contains(5)) {
                int worldId = WorldUtils.getWorldId(location.getWorld().getName());
                int x = (int) Math.floor(location.getX());
                int z = (int) Math.floor(location.getZ());
                int x2 = (int) Math.ceil(location.getX());
                int z2 = (int) Math.ceil(location.getZ());

                queryBlock = queryBlock + " wid=" + worldId + " AND (x = '" + x + "' OR x = '" + x2 + "') AND (z = '" + z + "' OR z = '" + z2 + "') AND y = '" + location.getBlockY() + "' AND";
            }

            if (validAction) {
                queryBlock = queryBlock + " action IN(" + action + ") AND";
            }
            else if (inventoryQuery || !actionExclude.isEmpty() || !includeBlock.isEmpty() || !includeEntity.isEmpty() || !excludeBlock.isEmpty() || !excludeEntity.isEmpty()) {
                queryBlock = queryBlock + " action NOT IN(-1) AND";
            }

            if (!includeBlock.isEmpty() || !includeEntity.isEmpty()) {
                queryBlock = queryBlock + " type IN(" + (!includeBlock.isEmpty() ? includeBlock : "0") + ") AND";
            }

            if (!excludeBlock.isEmpty() || !excludeEntity.isEmpty()) {
                queryBlock = queryBlock + " type NOT IN(" + (!excludeBlock.isEmpty() ? excludeBlock : "0") + ") AND";
            }

            if (!uuids.isEmpty()) {
                queryBlock = queryBlock + " uuid IN(" + uuids + ") AND";
            }

            // CH - handle a:death
            if (!users.isEmpty()) {
                if (!actionList.contains(999)) {
                queryBlock = queryBlock + " user IN(" + users + ") AND";
                } else {
                    queryBlock += " type = 0 AND data IN(" + users + ") AND";
                }
            }

            if (!excludeUsers.isEmpty()) {
                queryBlock = queryBlock + " user NOT IN(" + excludeUsers + ") AND";
            }

            if (startTime > 0) {
                queryBlock = queryBlock + " time > '" + startTime + "' AND";
            }

            if (endTime > 0) {
                queryBlock = queryBlock + " time <= '" + endTime + "' AND";
            }

            String rollbackPredicate = buildRollbackPredicate(rollbackState, actionList.contains(LookupActions.ITEM));
            if (!rollbackPredicate.isEmpty()) {
                queryBlock = queryBlock + " " + rollbackPredicate + " AND";
            }

            if (actionList.contains(LookupActions.SIGN)) {
                queryBlock = queryBlock + " action = '" + SignActions.PLACE + "' AND (LENGTH(line_1) > 0 OR LENGTH(line_2) > 0 OR LENGTH(line_3) > 0 OR LENGTH(line_4) > 0 OR LENGTH(line_5) > 0 OR LENGTH(line_6) > 0 OR LENGTH(line_7) > 0 OR LENGTH(line_8) > 0) AND";
            }

            if (!queryBlock.isEmpty()) {
                queryBlock = queryBlock.substring(0, queryBlock.length() - 4);
            }

            if (queryBlock.isEmpty()) {
                queryBlock = " 1";
            }

            queryEntity = queryBlock;
            if (!includeBlock.isEmpty() || !includeEntity.isEmpty()) {
                queryEntity = queryEntity.replace("type IN(" + (!includeBlock.isEmpty() ? includeBlock : "0") + ")", "type IN(" + (!includeEntity.isEmpty() ? includeEntity : "0") + ")");
            }
            if (!excludeBlock.isEmpty() || !excludeEntity.isEmpty()) {
                queryEntity = queryEntity.replace("type NOT IN(" + (!excludeBlock.isEmpty() ? excludeBlock : "0") + ")", "type NOT IN(" + (!excludeEntity.isEmpty() ? excludeEntity : "0") + ")");
            }

            String baseQuery = ((!includeEntity.isEmpty() || !excludeEntity.isEmpty()) ? queryEntity : queryBlock);
            if (limitOffset > -1 && limitCount > -1) {
                queryLimit = " LIMIT " + limitCount;
                unionLimit = countRows ? "" : (" ORDER BY time DESC LIMIT " + (limitCount + limitOffset)); // Do not add limits inside unions when rows need to be counted, otherwise the count breaks
                queryLimitOffset = queryLimit + (limitOffset > 0 ? (" OFFSET " + limitOffset) : "");
            }

            String rows = "rowid as id,time,user,wid,x,y,z,action,type,toString(data) as data,toString(meta) as meta,blockdata,rolled_back,version";
            String queryOrder = " ORDER BY rowid DESC";

            if (actionList.contains(LookupActions.CONTAINER) || actionList.contains(5)) {
                queryTable = "container";
                rows = "rowid as id,time,user,wid,x,y,z,action,type,toString(data) as data,rolled_back,amount,toString(metadata) as metadata,version";
            }
            else if (actionList.contains(LookupActions.CHAT) || actionList.contains(LookupActions.COMMAND)) {
                queryTable = actionList.contains(LookupActions.COMMAND) ? "command" : "chat";
                rows = "rowid as id,time,user,message,wid,x,y,z,cancelled";
                baseQuery = appendMessageFilters(baseQuery, messageFilters);
            }
            else if (actionList.contains(LookupActions.SESSION)) {
                queryTable = "session";
                rows = "rowid as id,time,user,wid,x,y,z,action";
            }
            else if (actionList.contains(LookupActions.USERNAME)) {
                queryTable = "username_log";
                rows = "rowid as id,time,uuid,user";
            }
            else if (actionList.contains(LookupActions.SIGN)) {
                queryTable = "sign";
                rows = "rowid as id,time,user,wid,x,y,z,face,line_1,line_2,line_3,line_4,line_5,line_6,line_7,line_8";
                baseQuery = appendSignMessageFilters(baseQuery, messageFilters);
            }
            else if (actionList.contains(LookupActions.ITEM)) {
                queryTable = "item";
                rows = "rowid as id,time,user,wid,x,y,z,type,toString(" + ConfigHandler.prefix + "item.data) as metadata,'0' as data,amount,action,rolled_back,version";
            }

            String unionSelect = "SELECT * FROM (";

            boolean itemLookup = inventoryQuery;
            if ((lookup && actionList.isEmpty()) || (itemLookup && !actionList.contains(LookupActions.BLOCK_BREAK))) {
                rows = "rowid as id,time,user,wid,x,y,z,type,toString(meta) as metadata,toString(data) as data,-1 as amount,action,rolled_back,version";

                if (inventoryQuery) {
                    if (validAction) {
                        baseQuery = baseQuery.replace("action IN(" + action + ")", "action IN(" + LookupActions.BLOCK_PLACE + ")");
                    }
                    else {
                        baseQuery = baseQuery.replace("action NOT IN(-1)", "action IN(" + LookupActions.BLOCK_PLACE + ")");
                    }

                    rows = "rowid as id,time,user,wid,x,y,z,type,toString(meta) as metadata,toString(data) as data,1 as amount,action,rolled_back,version";
                }

                if (!includeBlock.isEmpty() || !excludeBlock.isEmpty()) {
                    baseQuery = baseQuery.replace("action NOT IN(-1)", "action NOT IN(" + LookupActions.ENTITY_KILL + ")"); // if block specified for include/exclude, filter out entity data
                }

                query = unionSelect + "(SELECT " + "'0' as tbl," + rows + " FROM " + ConfigHandler.prefix + "block " + index + "WHERE" + baseQuery + unionLimit + ") UNION ALL ";
                itemLookup = true;
            }

            if (itemLookup) {
                rows = "rowid as id,time,user,wid,x,y,z,type,toString(metadata) as metadata,toString(data) as data,amount,action,rolled_back,version";
                query += (query.isEmpty() ? unionSelect + "(" : unionSelect) + "SELECT '1' as tbl," + rows + " FROM " + ConfigHandler.prefix + "container WHERE" + queryBlock + unionLimit + ") UNION ALL ";

                rows = "rowid as id,time,user,wid,x,y,z,type,toString(" + ConfigHandler.prefix + "item.data) as metadata,'0' as data,amount,action,rolled_back,version";
                queryOrder = " ORDER BY time DESC, tbl DESC, id DESC";

                if (!actionExclude.isEmpty()) {
                    queryBlock = queryBlock.replace("action NOT IN(-1)", "action NOT IN(" + actionExclude + ")");
                }

                query = query + unionSelect + "SELECT " + "'2' as tbl," + rows + " FROM " + ConfigHandler.prefix + "item WHERE" + queryBlock + unionLimit + ")";
            }

            if (query.isEmpty()) {
                if (!actionExclude.isEmpty()) {
                    baseQuery = baseQuery.replace("action NOT IN(-1)", "action NOT IN(" + actionExclude + ")");
                }

                query = "SELECT '0' as tbl," + rows + " FROM " + ConfigHandler.prefix + queryTable + " " + index + "WHERE" + baseQuery;
            }

            query = query.replace(" action NOT IN(-1) AND", ""); // Remove placeholders
            if (query.startsWith(unionSelect)) {
                query += ")";
            }

            String unboundedQuery = query;
            String resultQuery = query + queryOrder + queryLimitOffset;
            if (summary) {
                query = buildSummaryQuery(unboundedQuery, inventoryQuery, countGroups, limitOffset, limitCount);
            }
            else if (countRows) {
                query = "SELECT totals.count AS count, results.* FROM (" + resultQuery + ") AS results "
                        + "CROSS JOIN (SELECT count() AS count FROM (" + unboundedQuery + ")) AS totals";
            }
            else {
                query = resultQuery;
            }

            query += " SETTINGS output_format_json_quote_64bit_integers=0";
            if (Config.getGlobal().SELECT_USE_FINAL) {
                query += ", final=1";
            }

            return statement.executeQuery(query);
        }
        catch (Exception e) {
            CoreProtect.getInstance().getSLF4JLogger().warn("An exception occurred while executing query '{}'", query, e);
            return null;
        }
    }

    private static String appendMessageFilters(String query, List<String> messageFilters) {
        if (messageFilters == null || messageFilters.isEmpty()) {
            return query;
        }

        StringBuilder result = new StringBuilder(query);
        for (String rawFilter : messageFilters) {
            String filter = rawFilter == null ? "" : rawFilter.trim();
            if (!filter.isEmpty()) {
                result.append(" AND lowerUTF8(message) LIKE '%").append(escapeSqlLike(filter.toLowerCase(Locale.ROOT))).append("%'");
            }
        }
        return result.toString();
    }

    private static String appendSignMessageFilters(String query, List<String> messageFilters) {
        if (messageFilters == null || messageFilters.isEmpty()) {
            return query;
        }

        StringBuilder result = new StringBuilder(query);
        for (String rawFilter : messageFilters) {
            String filter = rawFilter == null ? "" : rawFilter.trim();
            if (filter.isEmpty()) {
                continue;
            }

            String escaped = escapeSqlLike(filter.toLowerCase(Locale.ROOT));
            result.append(" AND (");
            for (int line = 1; line <= 8; line++) {
                if (line > 1) {
                    result.append(" OR ");
                }
                result.append("lowerUTF8(line_").append(line).append(") LIKE '%").append(escaped).append("%'");
            }
            result.append(")");
        }
        return result.toString();
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

    private static String buildSummaryQuery(String sourceQuery, boolean inventoryQuery, boolean countGroups, int limitOffset, int limitCount) {
        int blockPositiveAction = inventoryQuery ? LookupActions.BLOCK_BREAK : LookupActions.BLOCK_PLACE;
        int transactionPositiveAction = inventoryQuery ? ItemTransactionActions.REMOVE : ItemTransactionActions.ADD;
        String positiveActions = transactionPositiveAction + "," + ItemTransactionActions.PICKUP + "," + ItemTransactionActions.REMOVE_ENDER + "," + ItemTransactionActions.CREATE + "," + ItemTransactionActions.BUY;
        String delta = "CASE WHEN amount=-1 THEN CASE WHEN action=" + blockPositiveAction + " THEN 1 ELSE -1 END "
                + "WHEN action IN(" + positiveActions + ") THEN amount ELSE -amount END";
        String eligible = "((amount=-1 AND action IN(" + LookupActions.BLOCK_BREAK + "," + LookupActions.BLOCK_PLACE + ")) OR "
                + "(amount<>-1 AND action BETWEEN " + ItemTransactionActions.REMOVE + " AND " + ItemTransactionActions.BUY + "))";
        String contributions = "SELECT user,type," + delta + " AS delta FROM (" + sourceQuery + ") summary_source WHERE " + eligible;
        String grouped = "SELECT user,type,sum(CASE WHEN delta<0 THEN -delta ELSE 0 END) AS removed_amount,"
                + "sum(CASE WHEN delta>0 THEN delta ELSE 0 END) AS placed_amount,sum(delta) AS amount "
                + "FROM (" + contributions + ") summary_contributions GROUP BY user,type";

        if (countGroups) {
            return "SELECT count() AS count FROM (" + grouped + ") summary_groups";
        }

        String query = "SELECT user,type,removed_amount,placed_amount,amount FROM (" + grouped + ") summary_groups ORDER BY abs(amount) DESC,user ASC,type ASC";
        if (limitOffset > -1 && limitCount > -1) {
            query += " LIMIT " + limitCount + (limitOffset > 0 ? (" OFFSET " + limitOffset) : "");
        }
        return query;
    }

    private static String escapeSqlLike(String value) {
        return value.replace("\\", "\\\\").replace("'", "''").replace("%", "\\%").replace("_", "\\_");
    }
}
