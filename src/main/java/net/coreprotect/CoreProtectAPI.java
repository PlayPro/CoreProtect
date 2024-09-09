package net.coreprotect;

import java.sql.Connection;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import net.coreprotect.api.BlockAPI;
import net.coreprotect.api.QueueLookup;
import net.coreprotect.api.SessionLookup;
import net.coreprotect.config.Config;
import net.coreprotect.consumer.Queue;
import net.coreprotect.database.Database;
import net.coreprotect.database.Lookup;
import net.coreprotect.database.rollback.Rollback;
import net.coreprotect.language.Phrase;
import net.coreprotect.listener.player.InventoryChangeListener;
import net.coreprotect.utility.Chat;
import net.coreprotect.utility.Util;

public class CoreProtectAPI extends Queue {

    public class ParseResult {
        String[] parse;

        public ParseResult(String[] data) {
            parse = data;
        }

        public int getActionId() {
            return Integer.parseInt(parse[7]);
        }

        public String getActionString() {
            int actionID = Integer.parseInt(parse[7]);
            if (parse.length < 13 && Integer.parseInt(parse[6]) == SessionLookup.ID) {
                switch (actionID) {
                    case 0:
                        return "logout";
                    case 1:
                        return "login";
                    default:
                        return "unknown";
                }
            }

            String result = "unknown";
            if (actionID == 0) {
                result = "break";
            }
            else if (actionID == 1) {
                result = "place";
            }
            else if (actionID == 2) {
                result = "click";
            }
            else if (actionID == 3) {
                result = "kill";
            }

            return result;
        }

        @Deprecated
        public int getData() {
            return Integer.parseInt(parse[6]);
        }

        public String getPlayer() {
            return parse[1];
        }

        @Deprecated
        public int getTime() {
            return Integer.parseInt(parse[0]);
        }

        public long getTimestamp() {
            return Long.parseLong(parse[0]) * 1000L;
        }

        public Material getType() {
            if (parse.length < 13) {
                return null;
            }

            int actionID = this.getActionId();
            int type = Integer.parseInt(parse[5]);
            String typeName;

            if (actionID == 3) {
                typeName = Util.getEntityType(type).name();
            }
            else {
                typeName = Util.getType(type).name().toLowerCase(Locale.ROOT);
                typeName = Util.nameFilter(typeName, this.getData());
            }

            return Util.getType(typeName);
        }

        public BlockData getBlockData() {
            if (parse.length < 13) {
                return null;
            }

            String blockData = parse[12];
            if (blockData == null || blockData.length() == 0) {
                return getType().createBlockData();
            }
            return Bukkit.getServer().createBlockData(blockData);
        }

        public int getX() {
            return Integer.parseInt(parse[2]);
        }

        public int getY() {
            return Integer.parseInt(parse[3]);
        }

        public int getZ() {
            return Integer.parseInt(parse[4]);
        }

        public boolean isRolledBack() {
            if (parse.length < 13) {
                return false;
            }

            return (Integer.parseInt(parse[8]) == 1 || Integer.parseInt(parse[8]) == 3);
        }

        public String worldName() {
            return Util.getWorldName(Integer.parseInt(parse.length < 13 ? parse[5] : parse[9]));
        }
    }

    private static Map<Object, Boolean> parseList(List<Object> list) {
        Map<Object, Boolean> result = new HashMap<>();

        if (list != null) {
            for (Object value : list) {
                if (value instanceof Material || value instanceof EntityType) {
                    result.put(value, false);
                }
                else if (value instanceof Integer) {
                    Material material = Util.getType((Integer) value);
                    result.put(material, false);
                }
            }
        }

        return result;
    }

    public int APIVersion() {
        return 10;
    }

    public List<String[]> blockLookup(Block block, int time) {
        if (Config.getGlobal().API_ENABLED) {
            return BlockAPI.performLookup(block, time);
        }
        return null;
    }

    public List<String[]> queueLookup(Block block) {
        return QueueLookup.performLookup(block);
    }

    public List<String[]> sessionLookup(String user, int time) {
        return SessionLookup.performLookup(user, time);
    }

    public boolean hasPlaced(String user, Block block, int time, int offset) {
        // Determine if a user has placed a block at this location in the last # of seconds.
        boolean match = false;

        if (Config.getGlobal().API_ENABLED) {
            long timestamp = System.currentTimeMillis();
            long offsetTime = timestamp - offset * 1000L;
            List<String[]> check = blockLookup(block, time);

            for (String[] value : check) {
                ParseResult result = parseResult(value);
                if (user.equalsIgnoreCase(result.getPlayer()) && result.getActionId() == 1 && result.getTimestamp() <= offsetTime) {
                    match = true;
                    break;
                }
            }
        }

        return match;
    }

    public boolean hasRemoved(String user, Block block, int time, int offset) {
        // Determine if a user has removed a block at this location in the last # of seconds.
        boolean match = false;

        if (Config.getGlobal().API_ENABLED) {
            long timestamp = System.currentTimeMillis();
            long offsetTime = timestamp - offset * 1000L;
            List<String[]> check = blockLookup(block, time);

            for (String[] value : check) {
                ParseResult result = parseResult(value);
                if (user.equalsIgnoreCase(result.getPlayer()) && result.getActionId() == 0 && result.getTimestamp() <= offsetTime) {
                    match = true;
                    break;
                }
            }
        }

        return match;
    }

    public boolean isEnabled() {
        return Config.getGlobal().API_ENABLED;
    }

    public boolean logChat(Player player, String message) {
        if (Config.getGlobal().API_ENABLED && player != null && Config.getConfig(player.getWorld()).PLAYER_MESSAGES) {
            if (message != null) {
                if (message.length() > 0 && !message.startsWith("/")) {
                    long timestamp = System.currentTimeMillis() / 1000L;

                    Queue.queuePlayerChat(player, message, timestamp);
                    return true;
                }
            }
        }

        return false;
    }

    public boolean logCommand(Player player, String command) {
        if (Config.getGlobal().API_ENABLED && player != null && Config.getConfig(player.getWorld()).PLAYER_COMMANDS) {
            if (command != null) {
                if (command.length() > 0 && command.startsWith("/")) {
                    long timestamp = System.currentTimeMillis() / 1000L;

                    Queue.queuePlayerCommand(player, command, timestamp);
                    return true;
                }
            }
        }

        return false;
    }

    public boolean logInteraction(String user, Location location) {
        if (Config.getGlobal().API_ENABLED) {
            if (user != null && location != null) {
                if (user.length() > 0) {
                    Queue.queuePlayerInteraction(user, location.getBlock().getState(), location.getBlock().getType());
                    return true;
                }
            }
        }

        return false;
    }

    public boolean logContainerTransaction(String user, Location location) {
        if (Config.getGlobal().API_ENABLED) {
            return InventoryChangeListener.inventoryTransaction(user, location, null);
        }
        return false;
    }

    public boolean logPlacement(String user, BlockState blockState) {
        if (!Config.getGlobal().API_ENABLED) {
            return false;
        }

        if (blockState == null || user == null || user.length() == 0) {
            return false;
        }

        Queue.queueBlockPlace(user, blockState, blockState.getType(), null, blockState.getType(), -1, 0, blockState.getBlockData().getAsString());
        return true;
    }

    public boolean logPlacement(String user, Location location, Material type, BlockData blockData) {
        if (Config.getGlobal().API_ENABLED) {
            if (user != null && location != null) {
                if (user.length() > 0) {
                    Block block = location.getBlock();
                    BlockState blockState = block.getState();
                    String blockDataString = null;

                    if (blockData != null) {
                        blockDataString = blockData.getAsString();
                    }

                    Queue.queueBlockPlace(user, blockState, block.getType(), null, type, -1, 0, blockDataString);
                    return true;
                }
            }
        }
        return false;
    }

    @Deprecated
    public boolean logPlacement(String user, Location location, Material type, byte data) {
        if (Config.getGlobal().API_ENABLED) {
            if (user != null && location != null) {
                if (user.length() > 0) {
                    Queue.queueBlockPlace(user, location.getBlock().getState(), location.getBlock().getType(), null, type, data, 1, null);
                    return true;
                }
            }
        }

        return false;
    }

    public boolean logRemoval(String user, BlockState blockState) {
        if (!Config.getGlobal().API_ENABLED) {
            return false;
        }

        if (blockState == null || user == null || user.length() == 0) {
            return false;
        }

        Queue.queueBlockBreak(user, blockState, blockState.getType(), blockState.getBlockData().getAsString(), 0);
        return true;
    }

    public boolean logRemoval(String user, Location location, Material type, BlockData blockData) {
        if (Config.getGlobal().API_ENABLED) {
            if (user != null && location != null) {
                if (user.length() > 0) {
                    String blockDataString = null;

                    if (blockData != null) {
                        blockDataString = blockData.getAsString();
                    }

                    Block block = location.getBlock();
                    Database.containerBreakCheck(user, block.getType(), block, null, location);
                    Queue.queueBlockBreak(user, location.getBlock().getState(), type, blockDataString, 0);
                    return true;
                }
            }
        }
        return false;
    }

    @Deprecated
    public boolean logRemoval(String user, Location location, Material type, byte data) {
        if (Config.getGlobal().API_ENABLED) {
            if (user != null && location != null) {
                if (user.length() > 0) {
                    Queue.queueBlockBreak(user, location.getBlock().getState(), type, type.createBlockData().getAsString(), data);
                    return true;
                }
            }
        }

        return false;
    }

    public ParseResult parseResult(String[] results) {
        return new ParseResult(results);
    }

    public List<String[]> performLookup(int time, List<String> restrictUsers, List<String> excludeUsers, List<Object> restrictBlocks, List<Object> excludeBlocks, List<Integer> actionList, int radius, Location radiusLocation) {
        if (Config.getGlobal().API_ENABLED) {
            return processData(time, radius, radiusLocation, parseList(restrictBlocks), parseList(excludeBlocks), restrictUsers, excludeUsers, actionList, 0, 1, -1, -1, false);
        }
        return null;
    }

    @Deprecated
    public List<String[]> performLookup(String user, int time, int radius, Location location, List<Object> restrict, List<Object> exclude) {
        if (Config.getGlobal().API_ENABLED) {
            return processData(user, time, radius, location, parseList(restrict), parseList(exclude), 0, 1, -1, -1, false);
        }
        return null;
    }

    public List<String[]> performPartialLookup(int time, List<String> restrictUsers, List<String> excludeUsers, List<Object> restrictBlocks, List<Object> excludeBlocks, List<Integer> actionList, int radius, Location radiusLocation, int limitOffset, int limitCount) {
        if (Config.getGlobal().API_ENABLED) {
            return processData(time, radius, radiusLocation, parseList(restrictBlocks), parseList(excludeBlocks), restrictUsers, excludeUsers, actionList, 0, 1, limitOffset, limitCount, true);
        }
        return null;
    }

    @Deprecated
    public List<String[]> performPartialLookup(String user, int time, int radius, Location location, List<Object> restrict, List<Object> exclude, int limitOffset, int limitCount) {
        if (Config.getGlobal().API_ENABLED) {
            return processData(user, time, radius, location, parseList(restrict), parseList(exclude), 0, 1, limitOffset, limitCount, true);
        }
        return null;
    }

    public void performPurge(int time) {
        Server server = Bukkit.getServer();
        server.dispatchCommand(server.getConsoleSender(), "co purge t:" + time + "s");
    }

    public List<String[]> performRestore(int time, List<String> restrictUsers, List<String> excludeUsers, List<Object> restrictBlocks, List<Object> excludeBlocks, List<Integer> actionList, int radius, Location radiusLocation) {
        if (Config.getGlobal().API_ENABLED) {
            return processData(time, radius, radiusLocation, parseList(restrictBlocks), parseList(excludeBlocks), restrictUsers, excludeUsers, actionList, 1, 2, -1, -1, false);
        }
        return null;
    }

    @Deprecated
    public List<String[]> performRestore(String user, int time, int radius, Location location, List<Object> restrict, List<Object> exclude) {
        if (Config.getGlobal().API_ENABLED) {
            return processData(user, time, radius, location, parseList(restrict), parseList(exclude), 1, 2, -1, -1, false);
        }
        return null;
    }

    public List<String[]> performRollback(int time, List<String> restrictUsers, List<String> excludeUsers, List<Object> restrictBlocks, List<Object> excludeBlocks, List<Integer> actionList, int radius, Location radiusLocation) {
        if (Config.getGlobal().API_ENABLED) {
            return processData(time, radius, radiusLocation, parseList(restrictBlocks), parseList(excludeBlocks), restrictUsers, excludeUsers, actionList, 0, 2, -1, -1, false);
        }
        return null;
    }

    @Deprecated
    public List<String[]> performRollback(String user, int time, int radius, Location location, List<Object> restrict, List<Object> exclude) {
        if (Config.getGlobal().API_ENABLED) {
            return processData(user, time, radius, location, parseList(restrict), parseList(exclude), 0, 2, -1, -1, false);
        }
        return null;
    }

    private List<String[]> processData(int time, int radius, Location location, Map<Object, Boolean> restrictBlocksMap, Map<Object, Boolean> excludeBlocks, List<String> restrictUsers, List<String> excludeUsers, List<Integer> actionList, int action, int lookup, int offset, int rowCount, boolean useLimit) {
        // You need to either specify time/radius or time/user
        List<String[]> result = new ArrayList<>();
        List<String> uuids = new ArrayList<>();

        if (restrictUsers == null) {
            restrictUsers = new ArrayList<>();
        }

        if (excludeUsers == null) {
            excludeUsers = new ArrayList<>();
        }

        if (actionList == null) {
            actionList = new ArrayList<>();
        }

        List<Object> restrictBlocks = new ArrayList<>(restrictBlocksMap.keySet());
        if (actionList.size() == 0 && restrictBlocks.size() > 0) {
            boolean addedMaterial = false;
            boolean addedEntity = false;

            for (Object argBlock : restrictBlocks) {
                if (argBlock instanceof Material && !addedMaterial) {
                    actionList.add(0);
                    actionList.add(1);
                    addedMaterial = true;
                }
                else if (argBlock instanceof EntityType && !addedEntity) {
                    actionList.add(3);
                    addedEntity = true;
                }
            }
        }

        if (actionList.size() == 0) {
            actionList.add(0);
            actionList.add(1);
        }

        actionList.removeIf(actionListItem -> actionListItem > 3);

        if (restrictUsers.size() == 0) {
            restrictUsers.add("#global");
        }

        long timestamp = System.currentTimeMillis() / 1000L;
        long startTime = timestamp - time;
        long endTime = 0;

        if (radius < 1) {
            radius = -1;
        }

        if (restrictUsers.contains("#global") && radius == -1) {
            return null;
        }

        if (radius > -1 && location == null) {
            return null;
        }

        try (Connection connection = Database.getConnection(false, 1000)) {
            if (connection != null) {
                Statement statement = connection.createStatement();
                boolean restrictWorld = false;

                if (radius > 0) {
                    restrictWorld = true;
                }

                if (location == null) {
                    restrictWorld = false;
                }

                Integer[] argRadius = null;
                if (location != null && radius > 0) {
                    int xMin = location.getBlockX() - radius;
                    int xMax = location.getBlockX() + radius;
                    int zMin = location.getBlockZ() - radius;
                    int zMax = location.getBlockZ() + radius;
                    argRadius = new Integer[] { radius, xMin, xMax, null, null, zMin, zMax, 0 };
                }

                if (lookup == 1) {
                    if (location != null) {
                        restrictWorld = true;
                    }

                    if (useLimit) {
                        result = Lookup.performPartialLookup(statement, null, uuids, restrictUsers, restrictBlocks, excludeBlocks, excludeUsers, actionList, location, argRadius, null, startTime, endTime, offset, rowCount, restrictWorld, true);
                    }
                    else {
                        result = Lookup.performLookup(statement, null, uuids, restrictUsers, restrictBlocks, excludeBlocks, excludeUsers, actionList, location, argRadius, startTime, endTime, restrictWorld, true);
                    }
                }
                else {
                    if (!Bukkit.isPrimaryThread()) {
                        boolean verbose = false;
                        result = Rollback.performRollbackRestore(statement, null, uuids, restrictUsers, null, restrictBlocks, excludeBlocks, excludeUsers, actionList, location, argRadius, startTime, endTime, restrictWorld, false, verbose, action, 0);
                    }
                    else {
                        Chat.console(Phrase.build(Phrase.PRIMARY_THREAD_ERROR));
                    }
                }

                statement.close();
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        return result;
    }

    @Deprecated
    private List<String[]> processData(String user, int time, int radius, Location location, Map<Object, Boolean> restrictBlocks, Map<Object, Boolean> excludeBlocks, int action, int lookup, int offset, int rowCount, boolean useLimit) {
        ArrayList<String> restrictUsers = new ArrayList<>();
        if (user != null) {
            restrictUsers.add(user);
        }

        return processData(time, radius, location, restrictBlocks, excludeBlocks, restrictUsers, null, null, action, lookup, offset, rowCount, useLimit);
    }

    public void testAPI() {
        Chat.console(Phrase.build(Phrase.API_TEST));
    }

}
