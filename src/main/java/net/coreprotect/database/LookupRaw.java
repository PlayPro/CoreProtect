package net.coreprotect.database;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
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
import net.coreprotect.database.statement.UserStatement;
import net.coreprotect.listener.channel.PluginChannelHandshakeListener;
import net.coreprotect.model.action.EntityActionFilter;
import net.coreprotect.model.action.LookupActions;
import net.coreprotect.model.action.SignActions;
import net.coreprotect.model.item.InventorySources;
import net.coreprotect.model.item.ItemTransactionActions;
import net.coreprotect.utility.EntityUtils;
import net.coreprotect.utility.EntitySpawnTracking;
import net.coreprotect.utility.MaterialUtils;
import net.coreprotect.utility.WorldUtils;
import net.coreprotect.utility.ErrorReporter;

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

        try {
            while (Consumer.isPaused) {
                Thread.sleep(1);
            }

            Consumer.isPaused = true;

            ResultSet results = rawLookupResultSet(statement, user, checkUuids, checkUsers, restrictList, excludeList, excludeUserList, actionList, entityActionFilter, messageFilters, loadedEntityUuids, loadedEntityCandidates, location, radius, rowData, startTime, endTime, limitOffset, limitCount, restrictWorld, lookup, false, entityContainerId);

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
                    if ((lookup && actionList.size() == 0) || actionList.contains(LookupActions.CONTAINER) || actionList.contains(5) || actionList.contains(LookupActions.ITEM)) {
                        resultData = results.getInt("data");
                        resultAmount = results.getInt("amount");
                        resultMeta = results.getBytes("metadata");
                        resultTable = results.getInt("tbl");
                        resultEntitySpawnId = results.getInt("entity_spawn_rowid");
                        hasTbl = true;
                    }
                    else {
                        resultData = results.getInt("data");
                        resultMeta = results.getBytes("meta");
                        resultBlockData = results.getBytes("blockdata");
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
        }

        Consumer.isPaused = false;
        return list;
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
        return rawLookupResultSet(statement, user, checkUuids, checkUsers, restrictList, excludeList, excludeUserList, actionList, entityActionFilter, messageFilters, loadedEntityUuids, loadedEntityCandidates, location, radius, rowData, startTime, endTime, limitOffset, limitCount, restrictWorld, lookup, count, entityContainerId, false, false, false);
    }

    static ResultSet rawSummaryResultSet(Statement statement, CommandSender user, List<String> checkUuids, List<String> checkUsers, List<Object> restrictList, Map<Object, Boolean> excludeList, List<String> excludeUserList, List<Integer> actionList, EntityActionFilter entityActionFilter, Set<UUID> loadedEntityUuids, Set<UUID> loadedEntityCandidates, Location location, Integer[] radius, long startTime, long endTime, int limitOffset, int limitCount, boolean restrictWorld, Integer entityContainerId, boolean countGroups) {
        return rawLookupResultSet(statement, user, checkUuids, checkUsers, restrictList, excludeList, excludeUserList, actionList, entityActionFilter, Collections.emptyList(), loadedEntityUuids, loadedEntityCandidates, location, radius, null, startTime, endTime, limitOffset, limitCount, restrictWorld, true, false, entityContainerId, true, countGroups, false);
    }

    static ResultSet rawSummaryPageResultSet(Statement statement, CommandSender user, List<String> checkUuids, List<String> checkUsers, List<Object> restrictList, Map<Object, Boolean> excludeList, List<String> excludeUserList, List<Integer> actionList, EntityActionFilter entityActionFilter, Set<UUID> loadedEntityUuids, Set<UUID> loadedEntityCandidates, Location location, Integer[] radius, long startTime, long endTime, int limitOffset, int limitCount, boolean restrictWorld, Integer entityContainerId) {
        return rawLookupResultSet(statement, user, checkUuids, checkUsers, restrictList, excludeList, excludeUserList, actionList, entityActionFilter, Collections.emptyList(), loadedEntityUuids, loadedEntityCandidates, location, radius, null, startTime, endTime, limitOffset, limitCount, restrictWorld, true, false, entityContainerId, true, false, true);
    }

    private static ResultSet rawLookupResultSet(Statement statement, CommandSender user, List<String> checkUuids, List<String> checkUsers, List<Object> restrictList, Map<Object, Boolean> excludeList, List<String> excludeUserList, List<Integer> actionList, EntityActionFilter entityActionFilter, List<String> messageFilters, Set<UUID> loadedEntityUuids, Set<UUID> loadedEntityCandidates, Location location, Integer[] radius, Long[] rowData, long startTime, long endTime, int limitOffset, int limitCount, boolean restrictWorld, boolean lookup, boolean count, Integer entityContainerId, boolean summary, boolean countGroups, boolean includeGroupCount) {
        ResultSet results = null;

        try {
            List<Integer> validActions = Arrays.asList(LookupActions.BLOCK_BREAK, LookupActions.BLOCK_PLACE, LookupActions.INTERACTION, LookupActions.ENTITY_KILL, LookupActions.ENTITY_SPAWN);
            if (radius != null) {
                restrictWorld = true;
            }

            boolean inventoryQuery = LookupActions.isInventoryLookup(actionList);
            boolean includeEntityContainers = entityContainerId != null || actionList.contains(LookupActions.CONTAINER) || inventoryQuery || (lookup && actionList.isEmpty());
            boolean includeEntitySpawnLocations = entityActionFilter.includesAnySpawn(actionList, lookup || Config.getGlobal().ROLLBACK_ENTITIES);
            boolean entitySpawnLocation = restrictWorld && location != null && location.getWorld() != null && includeEntitySpawnLocations;
            boolean entityContainerLocation = restrictWorld && location != null && location.getWorld() != null && includeEntityContainers;
            boolean entitySpawnRadius = entitySpawnLocation && radius != null;
            boolean entityContainerRadius = entityContainerLocation && radius != null;
            boolean currentEntityRadius = !lookup && (entitySpawnRadius || entityContainerRadius);
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
            String unionLimit = "";
            String index = "";
            String query = "";
            String entitySpawnLocationQuery = "";
            String entityContainerLocationQuery = "";
            String standardLocationQuery = "";
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
                        if (ConfigHandler.playerIdCache.get(checkUser.toLowerCase(Locale.ROOT)) == null) {
                            UserStatement.loadId(statement.getConnection(), checkUser, null);
                        }

                        int userId = ConfigHandler.playerIdCache.get(checkUser.toLowerCase(Locale.ROOT));
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
                    if (ConfigHandler.playerIdCache.get(excludeTarget.toLowerCase(Locale.ROOT)) == null) {
                        UserStatement.loadId(statement.getConnection(), excludeTarget, null);
                    }

                    int userId = ConfigHandler.playerIdCache.get(excludeTarget.toLowerCase(Locale.ROOT));
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
                bounds = "x >= '" + xmin + "' AND x <= '" + xmax + "' AND z >= '" + zmin + "' AND z <= '" + zmax + "'";

                if (ymin != null && ymax != null) {
                    bounds += " AND y >= '" + ymin + "' AND y <= '" + ymax + "'";
                }
            }

            if (entitySpawnLocation || entityContainerLocation) {
                int wid = WorldUtils.getWorldId(location.getWorld().getName());
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
                    entityBounds = "x >= '" + entityMinX + "' AND x < '" + entityMaxX + "' AND z >= '" + entityMinZ + "' AND z < '" + entityMaxZ + "'";
                    if (!currentEntityRadius && radius[3] != null && radius[4] != null) {
                        entityBounds += " AND y >= '" + radius[3] + "' AND y < '" + ((long) radius[4] + 1L) + "'";
                    }
                }
                String databaseLocation = "(current_wid=" + wid + (entityBounds.isEmpty() ? "" : " AND " + entityBounds) + ")";
                if (!loadedEntityCandidates.isEmpty()) {
                    databaseLocation = "(" + databaseLocation + " AND uuid NOT IN(" + uuidList(loadedEntityCandidates) + "))";
                }
                if (!loadedEntityUuids.isEmpty()) {
                    databaseLocation += " OR uuid IN(" + uuidList(loadedEntityUuids) + ")";
                }

                if (entitySpawnLocation) {
                    String entitySpawnRows = "SELECT block_rowid FROM " + ConfigHandler.prefix + "entity_spawn WHERE (" + databaseLocation + ")" + entitySpawnTimeQuery(startTime, endTime);
                    String spawnLocation = "rowid IN(" + entitySpawnRows + ")";
                    if (lookup) {
                        spawnLocation = "(" + originalLocation + " OR " + spawnLocation + ")";
                    }
                    entitySpawnLocationQuery = "((action=" + LookupActions.ENTITY_SPAWN + " AND " + spawnLocation + ") OR (action!=" + LookupActions.ENTITY_SPAWN + " AND " + originalLocation + "))";
                }

                if (entityContainerLocation) {
                    String entitySpawnRows = "SELECT rowid FROM " + ConfigHandler.prefix + "entity_spawn WHERE (" + databaseLocation + ")";
                    String currentLocation = "entity_spawn_rowid IN(" + entitySpawnRows + ")";
                    entityContainerLocationQuery = lookup ? "(" + originalLocation + " OR " + currentLocation + ")" : currentLocation;
                }

                standardLocationQuery = originalLocation;
                queryBlock = queryBlock + " " + (entitySpawnLocation ? entitySpawnLocationQuery : originalLocation) + " AND";
            }
            else {
                if (restrictWorld) {
                    int wid = WorldUtils.getWorldId(location.getWorld().getName());
                    queryBlock = queryBlock + " wid=" + wid + " AND";
                }
                if (!bounds.isEmpty()) {
                    queryBlock = queryBlock + " " + bounds + " AND";
                }
            }

            if (radius == null && actionList.contains(5) && entityContainerId == null) {
                int worldId = WorldUtils.getWorldId(location.getWorld().getName());
                int x = (int) Math.floor(location.getX());
                int z = (int) Math.floor(location.getZ());
                int x2 = (int) Math.ceil(location.getX());
                int z2 = (int) Math.ceil(location.getZ());

                queryBlock = queryBlock + " wid=" + worldId + " AND (x = '" + x + "' OR x = '" + x2 + "') AND (z = '" + z + "' OR z = '" + z2 + "') AND y = '" + location.getBlockY() + "' AND";
            }

            if (validAction) {
                queryBlock = queryBlock + " " + buildActionPredicate(action, actionList, entityActionFilter) + " AND";
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
                queryBlock = queryBlock + " user IN(" + users + ") AND";
            }

            if (excludeUsers.length() > 0) {
                queryBlock = queryBlock + " user NOT IN(" + excludeUsers + ") AND";
            }

            if (startTime > 0) {
                queryBlock = queryBlock + " time > '" + startTime + "' AND";
            }

            if (endTime > 0) {
                queryBlock = queryBlock + " time <= '" + endTime + "' AND";
            }

            if (actionList.contains(LookupActions.SIGN)) {
                queryBlock = queryBlock + " action = '" + SignActions.PLACE + "' AND (LENGTH(line_1) > 0 OR LENGTH(line_2) > 0 OR LENGTH(line_3) > 0 OR LENGTH(line_4) > 0 OR LENGTH(line_5) > 0 OR LENGTH(line_6) > 0 OR LENGTH(line_7) > 0 OR LENGTH(line_8) > 0) AND";
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

            String baseQuery = ((!includeEntity.isEmpty() || !excludeEntity.isEmpty()) ? queryEntity : queryBlock);
            if (!summary && limitOffset > -1 && limitCount > -1) {
                queryLimit = " LIMIT " + limitOffset + ", " + limitCount + "";
                unionLimit = " ORDER BY time DESC, id DESC LIMIT " + (limitOffset + limitCount) + "";
            }

            String rows = summary ? "rowid as id,time,user,wid,x,y,z,type,meta as metadata,data,-1 as amount,action,rolled_back,0 as entity_spawn_rowid" : "rowid as id,time,user,wid,x,y,z,action,type,data,meta,blockdata,rolled_back";
            String queryOrder = " ORDER BY rowid DESC";

            if (actionList.contains(LookupActions.CONTAINER) || actionList.contains(5)) {
                queryTable = "container";
                rows = "rowid as id,time,user,wid,x,y,z,action,type,data,rolled_back,amount,metadata,0 as entity_spawn_rowid";
            }
            else if (actionList.contains(LookupActions.CHAT) || actionList.contains(LookupActions.COMMAND)) {
                queryTable = "chat";
                rows = "rowid as id,time,user,message";
                if (PluginChannelHandshakeListener.getInstance().isPluginChannelPlayer(user)) {
                    rows += ",wid,x,y,z";
                }

                if (!actionList.contains(LookupActions.CHAT) && actionList.contains(LookupActions.COMMAND)) {
                    queryTable = "command";
                }
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
            }
            else if (actionList.contains(LookupActions.ITEM)) {
                queryTable = "item";
                rows = "rowid as id,time,user,wid,x,y,z,type,data as metadata,0 as data,amount,action,0 as rolled_back,0 as entity_spawn_rowid";
            }

            if (count) {
                rows = "COUNT(*) as count";
                queryLimit = " LIMIT 0, 4";
                queryOrder = "";
                unionLimit = "";
            }

            String unionSelect = "SELECT * FROM (";
            if (Config.getGlobal().MYSQL) {
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
            else {
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
                query = unionSelect + "SELECT '0' as tbl," + rows + " FROM " + ConfigHandler.prefix + "chat WHERE" + chatQuery + unionLimit + ") UNION ALL ";
                query += unionSelect + "SELECT '1' as tbl," + rows + " FROM " + ConfigHandler.prefix + "command WHERE" + commandQuery + unionLimit + ")";
                if (!count) {
                    queryOrder = " ORDER BY time DESC, id DESC";
                }
            }
            else if (chatLookup || commandLookup) {
                baseQuery = appendMessageFilters(baseQuery, messageFilters, queryTable, messageFilterBindings);
            }

            boolean itemLookup = inventoryQuery;
            if ((lookup && actionList.size() == 0) || (itemLookup && !actionList.contains(LookupActions.BLOCK_BREAK))) {
                if (!count) {
                    rows = "rowid as id,time,user,wid,x,y,z,type,meta as metadata,data,-1 as amount,action,rolled_back,0 as entity_spawn_rowid";
                }

                if (inventoryQuery) {
                    if (validAction) {
                        baseQuery = baseQuery.replace("action IN(" + action + ")", "action IN(" + LookupActions.BLOCK_PLACE + ")");
                    }
                    else {
                        baseQuery = baseQuery.replace("action NOT IN(-1)", "action IN(" + LookupActions.BLOCK_PLACE + ")");
                    }

                    if (!count) {
                        rows = "rowid as id,time,user,wid,x,y,z,type,meta as metadata,data,1 as amount,action,rolled_back,0 as entity_spawn_rowid";
                    }
                }

                if (includeBlock.length() > 0 || excludeBlock.length() > 0) {
                    baseQuery = baseQuery.replace("action NOT IN(-1)", "action NOT IN(" + LookupActions.ENTITY_KILL + "," + LookupActions.ENTITY_SPAWN + ")"); // if block specified for include/exclude, filter out entity data
                }

                query = unionSelect + "SELECT " + "'0' as tbl," + rows + " FROM " + ConfigHandler.prefix + "block " + index + "WHERE" + baseQuery + unionLimit + ") UNION ALL ";
                itemLookup = true;
            }

            if (itemLookup) {
                if (!count) {
                    rows = "rowid as id,time,user,wid,x,y,z,type,metadata,data,amount,action,rolled_back,0 as entity_spawn_rowid";
                }
                query = query + unionSelect + "SELECT " + "'1' as tbl," + rows + " FROM " + ConfigHandler.prefix + "container WHERE" + queryNonBlock + unionLimit + ") UNION ALL ";

                if (!count) {
                    rows = "rowid as id,time,user,wid,x,y,z,type,metadata,data,amount,action,rolled_back,entity_spawn_rowid";
                }
                query = query + unionSelect + "SELECT '" + InventorySources.ENTITY_CONTAINER + "' as tbl," + rows + " FROM " + ConfigHandler.prefix + "entity_container WHERE" + queryEntityContainer + unionLimit + ") UNION ALL ";

                if (!count) {
                    rows = "rowid as id,time,user,wid,x,y,z,type,data as metadata,0 as data,amount,action,rolled_back,0 as entity_spawn_rowid";
                    queryOrder = " ORDER BY time DESC, tbl DESC, id DESC";
                }

                if (actionExclude.length() > 0) {
                    queryNonBlock = queryNonBlock.replace("action NOT IN(-1)", "action NOT IN(" + actionExclude + ")");
                }

                query = query + unionSelect + "SELECT " + "'2' as tbl," + rows + " FROM " + ConfigHandler.prefix + "item WHERE" + queryNonBlock + unionLimit + ")";
            }

            if (!itemLookup && (actionList.contains(LookupActions.CONTAINER) || actionList.contains(5))) {
                if (!count) {
                    rows = "rowid as id,time,user,wid,x,y,z,type,metadata,data,amount,action,rolled_back,0 as entity_spawn_rowid";
                }
                if (entityContainerId == null) {
                    query = unionSelect + "SELECT '0' as tbl," + rows + " FROM " + ConfigHandler.prefix + "container WHERE" + queryNonBlock + unionLimit + ")";
                }
                if (includeEntityContainers) {
                    if (!query.isEmpty()) {
                        query += " UNION ALL ";
                    }
                    if (!count) {
                        rows = "rowid as id,time,user,wid,x,y,z,type,metadata,data,amount,action,rolled_back,entity_spawn_rowid";
                    }
                    query += unionSelect + "SELECT '" + InventorySources.ENTITY_CONTAINER + "' as tbl," + rows + " FROM " + ConfigHandler.prefix + "entity_container WHERE" + queryEntityContainer + unionLimit + ")";
                }
                if (!count) {
                    queryOrder = " ORDER BY time DESC, tbl DESC, id DESC";
                }
            }

            if (query.length() == 0) {
                if (actionExclude.length() > 0) {
                    baseQuery = baseQuery.replace("action NOT IN(-1)", "action NOT IN(" + actionExclude + ")");
                }

                query = "SELECT " + "'0' as tbl," + rows + " FROM " + ConfigHandler.prefix + queryTable + " " + index + "WHERE" + baseQuery;
            }

            if (summary) {
                query = buildSummaryQuery(query, inventoryQuery, countGroups, includeGroupCount, limitOffset, limitCount);
            }
            else {
                query = query + queryOrder + queryLimit + "";
            }
            results = executeQuery(statement, query, messageFilterBindings);
        }
        catch (Exception e) {
            ErrorReporter.report(e);
        }

        return results;
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
            query.append(" AND time > '").append(startTime).append("'");
        }
        if (endTime > 0) {
            query.append(" AND time <= '").append(endTime).append("'");
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

        String alias = table + "FilterRows";
        StringBuilder query = new StringBuilder(baseQuery)
                .append(" AND rowid IN (SELECT ").append(alias).append(".rowid FROM ")
                .append(ConfigHandler.prefix).append(table).append(" ").append(alias).append(" WHERE (");
        for (int index = 0; index < messageFilters.size(); index++) {
            if (index > 0) {
                query.append(" OR ");
            }

            String prefixExpression = Config.getGlobal().MYSQL ? alias + ".message" : "substr(" + alias + ".message,1,16)";
            query.append("(").append(prefixExpression).append(" LIKE ? ESCAPE '~' AND ")
                    .append(alias).append(".message LIKE ? ESCAPE '~')");

            String filter = messageFilters.get(index) == null ? "" : messageFilters.get(index);
            bindings.add(escapeLike(firstCodePoints(filter, 16)) + "%");
            bindings.add(escapeLike(filter) + "%");
        }
        return query.append("))").toString();
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

    private static String escapeLike(String value) {
        return value.replace("~", "~~").replace("%", "~%").replace("_", "~_");
    }

    private static String buildSummaryQuery(String sourceQuery, boolean inventoryQuery, boolean countGroups, boolean includeGroupCount, int limitOffset, int limitCount) {
        int blockPositiveAction = inventoryQuery ? LookupActions.BLOCK_BREAK : LookupActions.BLOCK_PLACE;
        int transactionPositiveAction = inventoryQuery ? ItemTransactionActions.REMOVE : ItemTransactionActions.ADD;
        String positiveActions = transactionPositiveAction + "," + ItemTransactionActions.PICKUP + "," + ItemTransactionActions.REMOVE_ENDER + "," + ItemTransactionActions.CREATE + "," + ItemTransactionActions.BUY;
        String delta = "CASE WHEN amount=-1 THEN CASE WHEN action=" + blockPositiveAction + " THEN 1 ELSE -1 END "
                + "WHEN action IN(" + positiveActions + ") THEN amount ELSE -amount END";
        String eligible = "((amount=-1 AND action IN(" + LookupActions.BLOCK_BREAK + "," + LookupActions.BLOCK_PLACE + ")) OR "
                + "(amount<>-1 AND action BETWEEN " + ItemTransactionActions.REMOVE + " AND " + ItemTransactionActions.BUY + "))";
        String contributions = "SELECT user,type," + delta + " AS delta FROM (" + sourceQuery + ") summary_source WHERE " + eligible;
        String grouped = "SELECT user,type,SUM(CASE WHEN delta<0 THEN -delta ELSE 0 END) AS removed_amount,"
                + "SUM(CASE WHEN delta>0 THEN delta ELSE 0 END) AS placed_amount,SUM(delta) AS net_amount "
                + "FROM (" + contributions + ") summary_contributions GROUP BY user,type";
        if (countGroups) {
            return "SELECT COUNT(*) AS count FROM (" + grouped + ") summary_groups";
        }

        String totalCount = includeGroupCount ? ",COUNT(*) OVER() AS total_count" : "";
        String query = "SELECT user,type,removed_amount,placed_amount,net_amount AS amount" + totalCount + " FROM (" + grouped + ") summary_groups ORDER BY ABS(net_amount) DESC,user ASC,type ASC";
        if (limitOffset > -1 && limitCount > -1) {
            query += " LIMIT " + limitOffset + ", " + limitCount;
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
