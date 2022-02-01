package net.coreprotect.database;

import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EntityType;

import net.coreprotect.bukkit.BukkitAdapter;
import net.coreprotect.config.Config;
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.consumer.Consumer;
import net.coreprotect.consumer.Queue;
import net.coreprotect.database.logger.ItemLogger;
import net.coreprotect.database.statement.UserStatement;
import net.coreprotect.thread.CacheHandler;
import net.coreprotect.utility.Util;

public class Lookup extends Queue {

    static List<String[]> convertRawLookup(Statement statement, List<Object[]> list) {
        List<String[]> newList = new ArrayList<>();

        if (list == null) {
            return null;
        }

        for (Object[] map : list) {
            int newLength = map.length - 1;
            String[] results = new String[newLength];

            for (int i = 0; i < map.length; i++) {
                try {
                    int newId = i - 1;
                    if (i == 2) {
                        if (map[i] instanceof Integer) {
                            int userId = (Integer) map[i];
                            if (ConfigHandler.playerIdCacheReversed.get(userId) == null) {
                                UserStatement.loadName(statement.getConnection(), userId);
                            }
                            String userResult = ConfigHandler.playerIdCacheReversed.get(userId);
                            results[newId] = userResult;
                        }
                        else {
                            results[newId] = (String) map[i];
                        }
                    }
                    else if (i == 13 && map[i] instanceof Byte[]) {
                        results[newId] = Util.byteDataToString((byte[]) map[i], (int) map[6]);
                    }
                    else if (i > 0) {
                        if (map[i] instanceof Integer) {
                            results[newId] = map[i].toString();
                        }
                        else if (map[i] instanceof String) {
                            results[newId] = (String) map[i];
                        }
                    }
                }
                catch (Exception e) {
                    e.printStackTrace();
                }
            }
            newList.add(results);
        }

        return newList;
    }

    public static int countLookupRows(Statement statement, CommandSender user, List<String> checkUuids, List<String> checkUsers, List<Object> restrictList, List<Object> excludeList, List<String> excludeUserList, List<Integer> actionList, Location location, Integer[] radius, long checkTime, boolean restrictWorld, boolean lookup) {
        int rows = 0;

        try {
            while (Consumer.isPaused) {
                Thread.sleep(1);
            }
            Consumer.isPaused = true;

            ResultSet results = rawLookupResultSet(statement, user, checkUuids, checkUsers, restrictList, excludeList, excludeUserList, actionList, location, radius, checkTime, -1, -1, restrictWorld, lookup, true);
            while (results.next()) {
                rows += results.getInt("count");
            }
            results.close();
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        Consumer.isPaused = false;

        return rows;
    }

    public static List<String[]> performLookup(Statement statement, CommandSender user, List<String> checkUuids, List<String> checkUsers, List<Object> restrictList, List<Object> excludeList, List<String> excludeUserList, List<Integer> actionList, Location location, Integer[] radius, long checkTime, boolean restrictWorld, boolean lookup) {
        List<String[]> newList = new ArrayList<>();

        try {
            List<Object[]> lookupList = performLookupRaw(statement, user, checkUuids, checkUsers, restrictList, excludeList, excludeUserList, actionList, location, radius, checkTime, -1, -1, restrictWorld, lookup);
            newList = convertRawLookup(statement, lookupList);
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        return newList;
    }

    static List<Object[]> performLookupRaw(Statement statement, CommandSender user, List<String> checkUuids, List<String> checkUsers, List<Object> restrictList, List<Object> excludeList, List<String> excludeUserList, List<Integer> actionList, Location location, Integer[] radius, long checkTime, int limitOffset, int limitCount, boolean restrictWorld, boolean lookup) {
        List<Object[]> list = new ArrayList<>();
        List<Integer> invalidRollbackActions = new ArrayList<>();
        invalidRollbackActions.add(2);

        if (!Config.getGlobal().ROLLBACK_ENTITIES && !actionList.contains(3)) {
            invalidRollbackActions.add(3);
        }

        try {
            while (Consumer.isPaused) {
                Thread.sleep(1);
            }

            Consumer.isPaused = true;

            ResultSet results = rawLookupResultSet(statement, user, checkUuids, checkUsers, restrictList, excludeList, excludeUserList, actionList, location, radius, checkTime, limitOffset, limitCount, restrictWorld, lookup, false);

            while (results.next()) {
                if (actionList.contains(6) || actionList.contains(7)) {
                    long resultId = results.getLong("id");
                    int resultTime = results.getInt("time");
                    int resultUserId = results.getInt("user");
                    String resultMessage = results.getString("message");

                    Object[] dataArray = new Object[] { resultId, resultTime, resultUserId, resultMessage };
                    list.add(dataArray);
                }
                else if (actionList.contains(8)) {
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
                else if (actionList.contains(9)) {
                    long resultId = results.getLong("id");
                    int resultTime = results.getInt("time");
                    String resultUuid = results.getString("uuid");
                    String resultUser = results.getString("user");

                    Object[] dataArray = new Object[] { resultId, resultTime, resultUuid, resultUser };
                    list.add(dataArray);
                }
                else if (actionList.contains(10)) {
                    long resultId = results.getLong("id");
                    int resultTime = results.getInt("time");
                    int resultUserId = results.getInt("user");
                    int resultWorldId = results.getInt("wid");
                    int resultX = results.getInt("x");
                    int resultY = results.getInt("y");
                    int resultZ = results.getInt("z");
                    String line1 = results.getString("line_1");
                    String line2 = results.getString("line_2");
                    String line3 = results.getString("line_3");
                    String line4 = results.getString("line_4");

                    StringBuilder message = new StringBuilder();
                    if (line1 != null && line1.length() > 0) {
                        message.append(line1);
                        if (!line1.endsWith(" ")) {
                            message.append(" ");
                        }
                    }
                    if (line2 != null && line2.length() > 0) {
                        message.append(line2);
                        if (!line2.endsWith(" ")) {
                            message.append(" ");
                        }
                    }
                    if (line3 != null && line3.length() > 0) {
                        message.append(line3);
                        if (!line3.endsWith(" ")) {
                            message.append(" ");
                        }
                    }
                    if (line4 != null && line4.length() > 0) {
                        message.append(line4);
                        if (!line4.endsWith(" ")) {
                            message.append(" ");
                        }
                    }

                    Object[] dataArray = new Object[] { resultId, resultTime, resultUserId, resultWorldId, resultX, resultY, resultZ, message.toString() };
                    list.add(dataArray);
                }
                else {
                    int resultData = 0;
                    int resultAmount = -1;
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

                    if ((lookup && actionList.size() == 0) || actionList.contains(4) || actionList.contains(5) || actionList.contains(11)) {
                        resultData = results.getInt("data");
                        resultAmount = results.getInt("amount");
                        resultMeta = results.getBytes("metadata");
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
                        Object[] dataArray = new Object[] { resultId, resultTime, resultUserId, resultX, resultY, resultZ, resultType, resultData, resultAction, resultRolledBack, resultWorldId, resultAmount, resultMeta, resultBlockData };
                        list.add(dataArray);
                    }
                }
            }
            results.close();
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        Consumer.isPaused = false;
        return list;
    }

    public static List<String[]> performPartialLookup(Statement statement, CommandSender user, List<String> checkUuids, List<String> checkUsers, List<Object> restrictList, List<Object> excludeList, List<String> excludeUserList, List<Integer> actionList, Location location, Integer[] radius, long checkTime, int limitOffset, int limitCount, boolean restrictWorld, boolean lookup) {
        List<String[]> newList = new ArrayList<>();

        try {
            List<Object[]> lookupList = performLookupRaw(statement, user, checkUuids, checkUsers, restrictList, excludeList, excludeUserList, actionList, location, radius, checkTime, limitOffset, limitCount, restrictWorld, lookup);
            newList = convertRawLookup(statement, lookupList);
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        return newList;
    }

    private static ResultSet rawLookupResultSet(Statement statement, CommandSender user, List<String> checkUuids, List<String> checkUsers, List<Object> restrictList, List<Object> excludeList, List<String> excludeUserList, List<Integer> actionList, Location location, Integer[] radius, long checkTime, int limitOffset, int limitCount, boolean restrictWorld, boolean lookup, boolean count) {
        ResultSet results = null;

        try {
            List<Integer> validActions = Arrays.asList(0, 1, 2, 3);
            if (radius != null) {
                restrictWorld = true;
            }

            boolean validAction = false;
            String queryBlock = "";
            String queryEntity = "";
            String queryLimit = "";
            String queryTable = "block";
            String unionLimit = "";
            String action = "";
            String includeBlock = "";
            String includeEntity = "";
            String excludeBlock = "";
            String excludeEntity = "";
            String users = "";
            String uuids = "";
            String excludeUsers = "";
            String index = "";
            String query = "";

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
                            includeListMaterial = includeListMaterial.append(Util.getBlockId(targetName, false));
                        }
                        else {
                            includeListMaterial.append(",").append(Util.getBlockId(targetName, false));
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
                            includeListEntity = includeListEntity.append(Util.getEntityId(targetName, false));
                        }
                        else {
                            includeListEntity.append(",").append(Util.getEntityId(targetName, false));
                        }
                    }
                }

                includeBlock = includeListMaterial.toString();
                includeEntity = includeListEntity.toString();
            }

            if (excludeList.size() > 0) {
                StringBuilder excludeListMaterial = new StringBuilder();
                StringBuilder excludeListEntity = new StringBuilder();

                for (Object restrictTarget : excludeList) {
                    String targetName = "";

                    if (restrictTarget instanceof Material) {
                        targetName = ((Material) restrictTarget).name();
                        if (excludeListMaterial.length() == 0) {
                            excludeListMaterial = excludeListMaterial.append(Util.getBlockId(targetName, false));
                        }
                        else {
                            excludeListMaterial.append(",").append(Util.getBlockId(targetName, false));
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
                            excludeListEntity = excludeListEntity.append(Util.getEntityId(targetName, false));
                        }
                        else {
                            excludeListEntity.append(",").append(Util.getEntityId(targetName, false));
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

            if (!actionList.isEmpty()) {
                StringBuilder actionText = new StringBuilder();
                for (Integer actionTarget : actionList) {
                    if (validActions.contains(actionTarget)) {
                        // If just looking up drops/pickups, remap the actions to the correct values
                        if (actionList.contains(11) && !actionList.contains(4)) {
                            if (actionTarget == ItemLogger.ITEM_REMOVE && !actionList.contains(ItemLogger.ITEM_DROP)) {
                                actionTarget = ItemLogger.ITEM_DROP;
                            }
                            else if (actionTarget == ItemLogger.ITEM_ADD && !actionList.contains(ItemLogger.ITEM_PICKUP)) {
                                actionTarget = ItemLogger.ITEM_PICKUP;
                            }
                        }

                        if (actionText.length() == 0) {
                            actionText = actionText.append(actionTarget);
                        }
                        else {
                            actionText.append(",").append(actionTarget);
                        }

                        // If selecting from co_item & co_container, add in actions for both transaction types
                        if (actionList.contains(11) && actionList.contains(4)) {
                            if (actionTarget == ItemLogger.ITEM_REMOVE) {
                                actionText.append(",").append(ItemLogger.ITEM_DROP);
                                actionText.append(",").append(ItemLogger.ITEM_ADD_ENDER);
                            }
                            if (actionTarget == ItemLogger.ITEM_ADD) {
                                actionText.append(",").append(ItemLogger.ITEM_PICKUP);
                                actionText.append(",").append(ItemLogger.ITEM_REMOVE_ENDER);
                            }
                        }
                        // If just looking up drops/pickups, include ender chest transactions
                        else if (actionList.contains(11) && !actionList.contains(4)) {
                            if (actionTarget == ItemLogger.ITEM_DROP) {
                                actionText.append(",").append(ItemLogger.ITEM_ADD_ENDER);
                            }
                            if (actionTarget == ItemLogger.ITEM_PICKUP) {
                                actionText.append(",").append(ItemLogger.ITEM_REMOVE_ENDER);
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

            if (restrictWorld) {
                int wid = Util.getWorldId(location.getWorld().getName());
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
                int worldId = Util.getWorldId(location.getWorld().getName());
                int x = (int) Math.floor(location.getX());
                int z = (int) Math.floor(location.getZ());
                int x2 = (int) Math.ceil(location.getX());
                int z2 = (int) Math.ceil(location.getZ());

                queryBlock = queryBlock + " wid=" + worldId + " AND (x = '" + x + "' OR x = '" + x2 + "') AND (z = '" + z + "' OR z = '" + z2 + "') AND y = '" + location.getBlockY() + "' AND";
            }

            if (validAction) {
                queryBlock = queryBlock + " action IN(" + action + ") AND";
            }
            else if (includeBlock.length() > 0 || includeEntity.length() > 0 || excludeBlock.length() > 0 || excludeEntity.length() > 0) {
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

            if (checkTime > 0) {
                queryBlock = queryBlock + " time > '" + checkTime + "' AND";
            }

            if (actionList.contains(10)) {
                queryBlock = queryBlock + " action = '1' AND (LENGTH(line_1) > 0 OR LENGTH(line_2) > 0 OR LENGTH(line_3) > 0 OR LENGTH(line_4) > 0) AND";
            }

            if (queryBlock.length() > 0) {
                queryBlock = queryBlock.substring(0, queryBlock.length() - 4);
            }

            if (queryBlock.length() == 0) {
                queryBlock = " 1";
            }

            queryEntity = queryBlock;
            if (includeBlock.length() > 0 || includeEntity.length() > 0) {
                queryEntity = queryEntity.replace("type IN(" + (includeBlock.length() > 0 ? includeBlock : "0") + ")", "type IN(" + (includeEntity.length() > 0 ? includeEntity : "0") + ")");
            }
            if (excludeBlock.length() > 0 || excludeEntity.length() > 0) {
                queryEntity = queryEntity.replace("type NOT IN(" + (excludeBlock.length() > 0 ? excludeBlock : "0") + ")", "type NOT IN(" + (excludeEntity.length() > 0 ? excludeEntity : "0") + ")");
            }

            String baseQuery = ((!includeEntity.isEmpty() || !excludeEntity.isEmpty()) ? queryEntity : queryBlock);
            if (limitOffset > -1 && limitCount > -1) {
                queryLimit = " LIMIT " + limitOffset + ", " + limitCount + "";
                unionLimit = " ORDER BY time DESC, id DESC LIMIT " + (limitOffset + limitCount) + "";
            }

            String rows = "rowid as id,time,user,wid,x,y,z,action,type,data,meta,blockdata,rolled_back";
            String queryOrder = " ORDER BY rowid DESC";

            if (actionList.contains(4) || actionList.contains(5)) {
                queryTable = "container";
                rows = "rowid as id,time,user,wid,x,y,z,action,type,data,rolled_back,amount,metadata";
            }
            else if (actionList.contains(6) || actionList.contains(7)) {
                queryTable = "chat";
                rows = "rowid as id,time,user,message";

                if (actionList.contains(7)) {
                    queryTable = "command";
                }
            }
            else if (actionList.contains(8)) {
                queryTable = "session";
                rows = "rowid as id,time,user,wid,x,y,z,action";
            }
            else if (actionList.contains(9)) {
                queryTable = "username_log";
                rows = "rowid as id,time,uuid,user";
            }
            else if (actionList.contains(10)) {
                queryTable = "sign";
                rows = "rowid as id,time,user,wid,x,y,z,line_1,line_2,line_3,line_4";
            }
            else if (actionList.contains(11)) {
                queryTable = "item";
                rows = "rowid as id,time,user,wid,x,y,z,type,data as metadata,0 as data,amount,action,0 as rolled_back";
            }

            if (count) {
                rows = "COUNT(*) as count";
                queryLimit = " LIMIT 0, 3";
                unionLimit = "";
                queryOrder = "";
            }

            if (Config.getGlobal().MYSQL) {
                if (radius == null || users.length() > 0 || includeBlock.length() > 0 || includeEntity.length() > 0) {
                    // index_mysql = "IGNORE INDEX(wid) ";
                    if (users.length() > 0) {
                        // index_mysql = "IGNORE INDEX(wid,type,action) ";
                    }
                }

                if (queryTable.equals("block")) {
                    if (includeBlock.length() > 0 || includeEntity.length() > 0) {
                        index = "USE INDEX(type) ";
                    }
                    if (users.length() > 0) {
                        index = "USE INDEX(user) ";
                    }
                    if ((index.equals("") && restrictWorld)) {
                        index = "USE INDEX(wid) ";
                    }
                    if ((radius != null || actionList.contains(5))) {
                        index = "";
                    }
                }
            }
            else {
                if (queryTable.equals("block")) {
                    if (includeBlock.length() > 0 || includeEntity.length() > 0) {
                        index = "INDEXED BY block_type_index ";
                    }
                    if (users.length() > 0) {
                        index = "INDEXED BY block_user_index ";
                    }
                    if ((index.equals("") && restrictWorld)) {
                        index = "INDEXED BY block_index ";
                    }
                    if ((radius != null || actionList.contains(5))) {
                        index = "";
                    }
                }
            }

            boolean itemLookup = (actionList.contains(4) && actionList.contains(11));
            if (lookup && actionList.size() == 0) {
                if (!count) {
                    rows = "rowid as id,time,user,wid,x,y,z,type,meta as metadata,data,-1 as amount,action,rolled_back";
                }

                if (includeBlock.length() > 0 || excludeBlock.length() > 0) {
                    baseQuery = baseQuery.replace("action NOT IN(-1)", "action NOT IN(3)"); // if block specified for include/exclude, filter out entity data
                }

                query = "(SELECT " + rows + " FROM " + ConfigHandler.prefix + queryTable + " " + index + "WHERE" + baseQuery + unionLimit + ") UNION ALL ";
                itemLookup = true;
            }

            if (itemLookup) {
                if (!count) {
                    rows = "rowid as id,time,user,wid,x,y,z,type,metadata,data,amount,action,rolled_back";
                }
                query = query + "(SELECT " + rows + " FROM " + ConfigHandler.prefix + "container " + index + "WHERE" + queryBlock + unionLimit + ") UNION ALL ";

                if (!count) {
                    rows = "rowid as id,time,user,wid,x,y,z,type,data as metadata,0 as data,amount,action,0 as rolled_back";
                    queryOrder = " ORDER BY time DESC, id DESC";
                }
                query = query + "(SELECT " + rows + " FROM " + ConfigHandler.prefix + "item " + index + "WHERE" + queryBlock + unionLimit + ")";
            }

            if (query.length() == 0) {
                query = "SELECT " + rows + " FROM " + ConfigHandler.prefix + queryTable + " " + index + "WHERE" + baseQuery;
            }

            query = query + queryOrder + queryLimit + "";
            results = statement.executeQuery(query);
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        return results;
    }

    public static String whoPlaced(Statement statement, BlockState block) {
        String result = "";

        try {
            if (block == null) {
                return result;
            }

            int x = block.getX();
            int y = block.getY();
            int z = block.getZ();
            int time = (int) (System.currentTimeMillis() / 1000L);
            int worldId = Util.getWorldId(block.getWorld().getName());
            String query = "SELECT user,type FROM " + ConfigHandler.prefix + "block WHERE wid = '" + worldId + "' AND x = '" + x + "' AND z = '" + z + "' AND y = '" + y + "' AND rolled_back = '0' AND action='1' ORDER BY rowid DESC LIMIT 0, 1";

            ResultSet results = statement.executeQuery(query);
            while (results.next()) {
                int resultUserId = results.getInt("user");
                int resultType = results.getInt("type");

                if (ConfigHandler.playerIdCacheReversed.get(resultUserId) == null) {
                    UserStatement.loadName(statement.getConnection(), resultUserId);
                }

                result = ConfigHandler.playerIdCacheReversed.get(resultUserId);
                if (result.length() > 0) {
                    Material resultMaterial = Util.getType(resultType);
                    CacheHandler.lookupCache.put("" + x + "." + y + "." + z + "." + worldId + "", new Object[] { time, result, resultMaterial });
                }
            }
            results.close();
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        return result;
    }

    public static String whoPlacedCache(Block block) {
        if (block == null) {
            return "";
        }

        return whoPlacedCache(block.getState());
    }

    public static String whoPlacedCache(BlockState block) {
        String result = "";

        try {
            if (block == null) {
                return result;
            }

            int x = block.getX();
            int y = block.getY();
            int z = block.getZ();
            int worldId = Util.getWorldId(block.getWorld().getName());

            String cords = "" + x + "." + y + "." + z + "." + worldId + "";
            Object[] data = CacheHandler.lookupCache.get(cords);

            if (data != null) {
                result = (String) data[1];
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        return result;
    }

    public static String whoRemovedCache(BlockState block) {
        /*
         * Performs a lookup on who removed a block, from memory. Only searches through the last 30 seconds of block removal data.
         */
        String result = "";

        try {
            if (block != null) {
                int x = block.getX();
                int y = block.getY();
                int z = block.getZ();
                int worldId = Util.getWorldId(block.getWorld().getName());

                String cords = "" + x + "." + y + "." + z + "." + worldId + "";
                Object[] data = CacheHandler.breakCache.get(cords);

                if (data != null) {
                    result = (String) data[1];
                }
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        return result;
    }
}
