package net.coreprotect.database;

import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EntityType;

import net.coreprotect.bukkit.BukkitAdapter;
import net.coreprotect.config.Config;
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.consumer.Consumer;
import net.coreprotect.consumer.Queue;
import net.coreprotect.database.logger.ItemLogger;
import net.coreprotect.database.statement.UserStatement;
import net.coreprotect.listener.channel.PluginChannelHandshakeListener;
import net.coreprotect.utility.EntityUtils;
import net.coreprotect.utility.MaterialUtils;
import net.coreprotect.utility.WorldUtils;

public class LookupRaw extends Queue {

    protected static List<Object[]> performLookupRaw(Statement statement, CommandSender user, List<String> checkUuids, List<String> checkUsers, List<Object> restrictList, Map<Object, Boolean> excludeList, List<String> excludeUserList, List<Integer> actionList, Location location, Integer[] radius, Long[] rowData, long startTime, long endTime, int limitOffset, int limitCount, boolean restrictWorld, boolean lookup) {
        List<Object[]> list = new ArrayList<>();
        List<Integer> invalidRollbackActions = new ArrayList<>();
        invalidRollbackActions.add(2);

        if (!Config.getGlobal().ROLLBACK_ENTITIES && !actionList.contains(3)) {
            invalidRollbackActions.add(3);
        }

        if (actionList.contains(4) && actionList.contains(11)) {
            invalidRollbackActions.clear();
        }

        try {
            while (Consumer.isPaused) {
                Thread.sleep(1);
            }

            Consumer.isPaused = true;

            ResultSet results = rawLookupResultSet(statement, user, checkUuids, checkUsers, restrictList, excludeList, excludeUserList, actionList, location, radius, rowData, startTime, endTime, limitOffset, limitCount, restrictWorld, lookup, false);

            while (results.next()) {
                if (actionList.contains(6) || actionList.contains(7)) {
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
                    if ((lookup && actionList.size() == 0) || actionList.contains(4) || actionList.contains(5) || actionList.contains(11)) {
                        resultData = results.getInt("data");
                        resultAmount = results.getInt("amount");
                        resultMeta = results.getBytes("metadata");
                        resultTable = results.getInt("tbl");
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
                        if (hasTbl) {
                            Object[] dataArray = new Object[] { resultId, resultTime, resultUserId, resultX, resultY, resultZ, resultType, resultData, resultAction, resultRolledBack, resultWorldId, resultAmount, resultMeta, resultBlockData, resultTable };
                            list.add(dataArray);
                        }
                        else {
                            Object[] dataArray = new Object[] { resultId, resultTime, resultUserId, resultX, resultY, resultZ, resultType, resultData, resultAction, resultRolledBack, resultWorldId, resultAmount, resultMeta, resultBlockData };
                            list.add(dataArray);
                        }
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

    static ResultSet rawLookupResultSet(Statement statement, CommandSender user, List<String> checkUuids, List<String> checkUsers, List<Object> restrictList, Map<Object, Boolean> excludeList, List<String> excludeUserList, List<Integer> actionList, Location location, Integer[] radius, Long[] rowData, long startTime, long endTime, int limitOffset, int limitCount, boolean restrictWorld, boolean lookup, boolean count) {
        ResultSet results = null;

        try {
            List<Integer> validActions = Arrays.asList(0, 1, 2, 3);
            if (radius != null) {
                restrictWorld = true;
            }

            boolean inventoryQuery = (actionList.contains(4) && actionList.contains(11));
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
            if ((lookup && actionList.size() == 0) || (actionList.contains(11) && actionList.size() == 1)) {
                StringBuilder actionText = new StringBuilder();
                actionText = actionText.append(ItemLogger.ITEM_BREAK);
                actionText.append(",").append(ItemLogger.ITEM_DESTROY);
                actionText.append(",").append(ItemLogger.ITEM_CREATE);
                actionText.append(",").append(ItemLogger.ITEM_SELL);
                actionText.append(",").append(ItemLogger.ITEM_BUY);
                actionExclude = actionText.toString();
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
                                actionText.append(",").append(ItemLogger.ITEM_PICKUP);
                                actionText.append(",").append(ItemLogger.ITEM_REMOVE_ENDER);
                                actionText.append(",").append(ItemLogger.ITEM_CREATE);
                                actionText.append(",").append(ItemLogger.ITEM_BUY);
                            }
                            if (actionTarget == ItemLogger.ITEM_ADD) {
                                actionText.append(",").append(ItemLogger.ITEM_DROP);
                                actionText.append(",").append(ItemLogger.ITEM_ADD_ENDER);
                                actionText.append(",").append(ItemLogger.ITEM_THROW);
                                actionText.append(",").append(ItemLogger.ITEM_SHOOT);
                                actionText.append(",").append(ItemLogger.ITEM_BREAK);
                                actionText.append(",").append(ItemLogger.ITEM_DESTROY);
                                actionText.append(",").append(ItemLogger.ITEM_SELL);
                            }
                        }
                        // If just looking up drops/pickups, include ender chest transactions
                        else if (actionList.contains(11) && !actionList.contains(4)) {
                            if (actionTarget == ItemLogger.ITEM_DROP) {
                                actionText.append(",").append(ItemLogger.ITEM_ADD_ENDER);
                                actionText.append(",").append(ItemLogger.ITEM_THROW);
                                actionText.append(",").append(ItemLogger.ITEM_SHOOT);
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

            if (actionList.contains(10)) {
                queryBlock = queryBlock + " action = '1' AND (LENGTH(line_1) > 0 OR LENGTH(line_2) > 0 OR LENGTH(line_3) > 0 OR LENGTH(line_4) > 0 OR LENGTH(line_5) > 0 OR LENGTH(line_6) > 0 OR LENGTH(line_7) > 0 OR LENGTH(line_8) > 0) AND";
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
                if (PluginChannelHandshakeListener.getInstance().isPluginChannelPlayer(user)) {
                    rows += ",wid,x,y,z";
                }

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
                rows = "rowid as id,time,user,wid,x,y,z,face,line_1,line_2,line_3,line_4,line_5,line_6,line_7,line_8";
            }
            else if (actionList.contains(11)) {
                queryTable = "item";
                rows = "rowid as id,time,user,wid,x,y,z,type,data as metadata,0 as data,amount,action,0 as rolled_back";
            }

            if (count) {
                rows = "COUNT(*) as count";
                queryLimit = " LIMIT 0, 3";
                queryOrder = "";
                unionLimit = "";
            }

            String unionSelect = "SELECT * FROM (";
            if (Config.getGlobal().MYSQL) {
                if (queryTable.equals("block")) {
                    if (includeBlock.length() > 0 || includeEntity.length() > 0) {
                        index = "USE INDEX(type) IGNORE INDEX(user,wid) ";
                    }
                    if (users.length() > 0) {
                        index = "USE INDEX(user) IGNORE INDEX(type,wid) ";
                    }
                    if (radius != null && (radius[2] - radius[1]) <= 50 && (radius[6] - radius[5]) <= 50) {
                        index = "USE INDEX(wid) IGNORE INDEX(type,user) ";
                    }
                    if ((restrictWorld && (users.length() > 0 || includeBlock.length() > 0 || includeEntity.length() > 0))) {
                        index = "IGNORE INDEX(PRIMARY) ";
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
                    if (radius != null && (radius[2] - radius[1]) <= 50 && (radius[6] - radius[5]) <= 50) {
                        index = "INDEXED BY block_index ";
                    }
                    if ((restrictWorld && (users.length() > 0 || includeBlock.length() > 0 || includeEntity.length() > 0))) {
                        index = "";
                    }
                }
            }

            boolean itemLookup = inventoryQuery;
            if ((lookup && actionList.size() == 0) || (itemLookup && !actionList.contains(0))) {
                if (!count) {
                    rows = "rowid as id,time,user,wid,x,y,z,type,meta as metadata,data,-1 as amount,action,rolled_back";
                }

                if (inventoryQuery) {
                    if (validAction) {
                        baseQuery = baseQuery.replace("action IN(" + action + ")", "action IN(1)");
                    }
                    else {
                        baseQuery = baseQuery.replace("action NOT IN(-1)", "action IN(1)");
                    }

                    if (!count) {
                        rows = "rowid as id,time,user,wid,x,y,z,type,meta as metadata,data,1 as amount,action,rolled_back";
                    }
                }

                if (includeBlock.length() > 0 || excludeBlock.length() > 0) {
                    baseQuery = baseQuery.replace("action NOT IN(-1)", "action NOT IN(3)"); // if block specified for include/exclude, filter out entity data
                }

                query = unionSelect + "SELECT " + "'0' as tbl," + rows + " FROM " + ConfigHandler.prefix + "block " + index + "WHERE" + baseQuery + unionLimit + ") UNION ALL ";
                itemLookup = true;
            }

            if (itemLookup) {
                if (!count) {
                    rows = "rowid as id,time,user,wid,x,y,z,type,metadata,data,amount,action,rolled_back";
                }
                query = query + unionSelect + "SELECT " + "'1' as tbl," + rows + " FROM " + ConfigHandler.prefix + "container WHERE" + queryBlock + unionLimit + ") UNION ALL ";

                if (!count) {
                    rows = "rowid as id,time,user,wid,x,y,z,type,data as metadata,0 as data,amount,action,rolled_back";
                    queryOrder = " ORDER BY time DESC, tbl DESC, id DESC";
                }

                if (actionExclude.length() > 0) {
                    queryBlock = queryBlock.replace("action NOT IN(-1)", "action NOT IN(" + actionExclude + ")");
                }

                query = query + unionSelect + "SELECT " + "'2' as tbl," + rows + " FROM " + ConfigHandler.prefix + "item WHERE" + queryBlock + unionLimit + ")";
            }

            if (query.length() == 0) {
                if (actionExclude.length() > 0) {
                    baseQuery = baseQuery.replace("action NOT IN(-1)", "action NOT IN(" + actionExclude + ")");
                }

                query = "SELECT " + "'0' as tbl," + rows + " FROM " + ConfigHandler.prefix + queryTable + " " + index + "WHERE" + baseQuery;
            }

            query = query + queryOrder + queryLimit + "";
            results = statement.executeQuery(query);
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        return results;
    }

}
