package net.coreprotect;

import java.sql.Connection;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
import net.coreprotect.utility.MaterialUtils;

/**
 * The main API class for CoreProtect.
 * <p>
 * This class provides methods for interacting with the CoreProtect database,
 * including lookups, rollbacks, and logging operations.
 */
public class CoreProtectAPI extends Queue {

    /**
     * Current version of the API
     */
    private static final int API_VERSION = 10;

    public static class ParseResult extends net.coreprotect.api.result.ParseResult {

        /**
         * Creates a new ParseResult from string array data.
         * 
         * @param data
         *            The string array data to parse
         */
        public ParseResult(String[] data) {
            super(data);
        }
    }

    /**
     * Converts a list of objects to a map for internal processing
     * 
     * @param list
     *            List of objects to convert
     * @return Map with objects as keys and Boolean false as values
     */
    private static Map<Object, Boolean> parseList(List<Object> list) {
        Map<Object, Boolean> result = new HashMap<>();

        if (list != null) {
            for (Object value : list) {
                if (value instanceof Material || value instanceof EntityType) {
                    result.put(value, false);
                }
                else if (value instanceof Integer) {
                    Material material = MaterialUtils.getType((Integer) value);
                    result.put(material, false);
                }
            }
        }

        return result;
    }

    /**
     * Returns the current API version.
     * 
     * @return The API version as an integer
     */
    public int APIVersion() {
        return API_VERSION;
    }

    /**
     * Performs a block lookup at the specified block.
     * 
     * @param block
     *            The block to look up
     * @param time
     *            Time constraint in seconds
     * @return List of results or null if API is disabled
     */
    public List<String[]> blockLookup(Block block, int time) {
        if (isEnabled()) {
            return BlockAPI.performLookup(block, time);
        }
        return null;
    }

    /**
     * Performs a lookup on the queue data for the specified block.
     * 
     * @param block
     *            The block to look up
     * @return List of results
     */
    public List<String[]> queueLookup(Block block) {
        return QueueLookup.performLookup(block);
    }

    /**
     * Performs a lookup on session data for the specified user.
     * 
     * @param user
     *            The user to look up
     * @param time
     *            Time constraint in seconds
     * @return List of results
     */
    public List<String[]> sessionLookup(String user, int time) {
        return SessionLookup.performLookup(user, time);
    }

    /**
     * Determines if a user has placed a block at the specified location.
     * 
     * @param user
     *            The username to check
     * @param block
     *            The block to check
     * @param time
     *            Time constraint in seconds
     * @param offset
     *            Offset in seconds for the check
     * @return True if the user has placed the block within the specified time frame
     */
    public boolean hasPlaced(String user, Block block, int time, int offset) {
        if (!isEnabled()) {
            return false;
        }

        long timestamp = getCurrentTimeMillis();
        long offsetTime = timestamp - offset * 1000L;
        List<String[]> check = blockLookup(block, time);

        for (String[] value : check) {
            ParseResult result = parseResult(value);
            if (user.equalsIgnoreCase(result.getPlayer()) && result.getActionId() == 1 && result.getTimestamp() <= offsetTime) {
                return true;
            }
        }

        return false;
    }

    /**
     * Determines if a user has removed a block at the specified location.
     * 
     * @param user
     *            The username to check
     * @param block
     *            The block to check
     * @param time
     *            Time constraint in seconds
     * @param offset
     *            Offset in seconds for the check
     * @return True if the user has removed the block within the specified time frame
     */
    public boolean hasRemoved(String user, Block block, int time, int offset) {
        if (!isEnabled()) {
            return false;
        }

        long timestamp = getCurrentTimeMillis();
        long offsetTime = timestamp - offset * 1000L;
        List<String[]> check = blockLookup(block, time);

        for (String[] value : check) {
            ParseResult result = parseResult(value);
            if (user.equalsIgnoreCase(result.getPlayer()) && result.getActionId() == 0 && result.getTimestamp() <= offsetTime) {
                return true;
            }
        }

        return false;
    }

    /**
     * Gets the current time in milliseconds. Protected to allow mocking in tests.
     * 
     * @return Current time in milliseconds
     */
    protected long getCurrentTimeMillis() {
        return System.currentTimeMillis();
    }

    /**
     * Checks if the CoreProtect API is enabled.
     * 
     * @return True if the API is enabled
     */
    public boolean isEnabled() {
        return Config.getGlobal().API_ENABLED;
    }

    /**
     * Logs a chat message for a player.
     * 
     * @param player
     *            The player who sent the message
     * @param message
     *            The chat message
     * @return True if the message was logged
     */
    public boolean logChat(Player player, String message) {
        if (!isEnabledForPlayer(player) || !Config.getConfig(player.getWorld()).PLAYER_MESSAGES) {
            return false;
        }

        if (message == null || message.isEmpty() || message.startsWith("/")) {
            return false;
        }

        long timestamp = System.currentTimeMillis() / 1000L;
        Queue.queuePlayerChat(player, message, timestamp);
        return true;
    }

    /**
     * Logs a command executed by a player.
     * 
     * @param player
     *            The player who executed the command
     * @param command
     *            The command
     * @return True if the command was logged
     */
    public boolean logCommand(Player player, String command) {
        if (!isEnabledForPlayer(player) || !Config.getConfig(player.getWorld()).PLAYER_COMMANDS) {
            return false;
        }

        if (command == null || command.isEmpty() || !command.startsWith("/")) {
            return false;
        }

        long timestamp = System.currentTimeMillis() / 1000L;
        Queue.queuePlayerCommand(player, command, timestamp);
        return true;
    }

    /**
     * Logs an interaction by a user at a location.
     * 
     * @param user
     *            The username
     * @param location
     *            The location
     * @return True if the interaction was logged
     */
    public boolean logInteraction(String user, Location location) {
        if (!isEnabled() || !isValidUserAndLocation(user, location)) {
            return false;
        }

        Queue.queuePlayerInteraction(user, location.getBlock().getState(), location.getBlock().getType());
        return true;
    }

    /**
     * Logs a container transaction by a user at a location.
     * 
     * @param user
     *            The username
     * @param location
     *            The location
     * @return True if the transaction was logged
     */
    public boolean logContainerTransaction(String user, Location location) {
        if (!isEnabled()) {
            return false;
        }

        return InventoryChangeListener.inventoryTransaction(user, location, null);
    }

    /**
     * Logs a block placement by a user.
     * 
     * @param user
     *            The username
     * @param blockState
     *            The state of the block being placed
     * @return True if the placement was logged
     */
    public boolean logPlacement(String user, BlockState blockState) {
        if (!isEnabled() || blockState == null || user == null || user.isEmpty()) {
            return false;
        }

        Queue.queueBlockPlace(user, blockState, blockState.getType(), null, blockState.getType(), -1, 0, blockState.getBlockData().getAsString());
        return true;
    }

    /**
     * Logs a block placement by a user with a specific material and block data.
     * 
     * @param user
     *            The username
     * @param location
     *            The location
     * @param type
     *            The material type
     * @param blockData
     *            The block data
     * @return True if the placement was logged
     */
    public boolean logPlacement(String user, Location location, Material type, BlockData blockData) {
        if (!isEnabled() || !isValidUserAndLocation(user, location)) {
            return false;
        }

        Block block = location.getBlock();
        BlockState blockState = block.getState();
        String blockDataString = null;

        if (blockData != null) {
            blockDataString = blockData.getAsString();
        }

        Queue.queueBlockPlace(user, blockState, block.getType(), null, type, -1, 0, blockDataString);
        return true;
    }

    /**
     * Logs a block placement by a user with a specific material and data value.
     * 
     * @param user
     *            The username
     * @param location
     *            The location
     * @param type
     *            The material type
     * @param data
     *            The data value
     * @return True if the placement was logged
     * @deprecated Use {@link #logPlacement(String, Location, Material, BlockData)} instead
     */
    @Deprecated
    public boolean logPlacement(String user, Location location, Material type, byte data) {
        if (!isEnabled() || !isValidUserAndLocation(user, location)) {
            return false;
        }

        Queue.queueBlockPlace(user, location.getBlock().getState(), location.getBlock().getType(), null, type, data, 1, null);
        return true;
    }

    /**
     * Logs a block removal by a user.
     * 
     * @param user
     *            The username
     * @param blockState
     *            The state of the block being removed
     * @return True if the removal was logged
     */
    public boolean logRemoval(String user, BlockState blockState) {
        if (!isEnabled() || blockState == null || user == null || user.isEmpty()) {
            return false;
        }

        Queue.queueBlockBreak(user, blockState, blockState.getType(), blockState.getBlockData().getAsString(), 0);
        return true;
    }

    /**
     * Logs a block removal by a user with a specific material and block data.
     * 
     * @param user
     *            The username
     * @param location
     *            The location
     * @param type
     *            The material type
     * @param blockData
     *            The block data
     * @return True if the removal was logged
     */
    public boolean logRemoval(String user, Location location, Material type, BlockData blockData) {
        if (!isEnabled() || !isValidUserAndLocation(user, location)) {
            return false;
        }

        String blockDataString = null;
        if (blockData != null) {
            blockDataString = blockData.getAsString();
        }

        Block block = location.getBlock();
        Database.containerBreakCheck(user, block.getType(), block, null, location);
        Queue.queueBlockBreak(user, location.getBlock().getState(), type, blockDataString, 0);
        return true;
    }

    /**
     * Logs a block removal by a user with a specific material and data value.
     * 
     * @param user
     *            The username
     * @param location
     *            The location
     * @param type
     *            The material type
     * @param data
     *            The data value
     * @return True if the removal was logged
     * @deprecated Use {@link #logRemoval(String, Location, Material, BlockData)} instead
     */
    @Deprecated
    public boolean logRemoval(String user, Location location, Material type, byte data) {
        if (!isEnabled() || !isValidUserAndLocation(user, location)) {
            return false;
        }

        Queue.queueBlockBreak(user, location.getBlock().getState(), type, type.createBlockData().getAsString(), data);
        return true;
    }

    /**
     * Parses lookup results into a ParseResult object.
     * 
     * @param results
     *            The results to parse
     * @return A ParseResult object containing the parsed data
     */
    public ParseResult parseResult(String[] results) {
        return new ParseResult(results);
    }

    /**
     * Performs a lookup operation with various filters.
     * 
     * @param time
     *            Time constraint in seconds
     * @param restrictUsers
     *            List of users to include in the lookup
     * @param excludeUsers
     *            List of users to exclude from the lookup
     * @param restrictBlocks
     *            List of blocks to include in the lookup
     * @param excludeBlocks
     *            List of blocks to exclude from the lookup
     * @param actionList
     *            List of actions to include in the lookup
     * @param radius
     *            Radius to search within
     * @param radiusLocation
     *            Center location for the radius search
     * @return List of results or null if the API is disabled
     */
    public List<String[]> performLookup(int time, List<String> restrictUsers, List<String> excludeUsers, List<Object> restrictBlocks, List<Object> excludeBlocks, List<Integer> actionList, int radius, Location radiusLocation) {
        if (!isEnabled()) {
            return null;
        }

        return processData(time, radius, radiusLocation, parseList(restrictBlocks), parseList(excludeBlocks), restrictUsers, excludeUsers, actionList, 0, 1, -1, -1, false);
    }

    /**
     * Performs a lookup operation with basic filters.
     * 
     * @param user
     *            The user to include in the lookup
     * @param time
     *            Time constraint in seconds
     * @param radius
     *            Radius to search within
     * @param location
     *            Center location for the radius search
     * @param restrict
     *            List of blocks to include in the lookup
     * @param exclude
     *            List of blocks to exclude from the lookup
     * @return List of results or null if the API is disabled
     * @deprecated Use {@link #performLookup(int, List, List, List, List, List, int, Location)} instead
     */
    @Deprecated
    public List<String[]> performLookup(String user, int time, int radius, Location location, List<Object> restrict, List<Object> exclude) {
        if (!isEnabled()) {
            return null;
        }

        return processData(user, time, radius, location, parseList(restrict), parseList(exclude), 0, 1, -1, -1, false);
    }

    /**
     * Performs a partial lookup operation with various filters and pagination support.
     * 
     * @param time
     *            Time constraint in seconds
     * @param restrictUsers
     *            List of users to include in the lookup
     * @param excludeUsers
     *            List of users to exclude from the lookup
     * @param restrictBlocks
     *            List of blocks to include in the lookup
     * @param excludeBlocks
     *            List of blocks to exclude from the lookup
     * @param actionList
     *            List of actions to include in the lookup
     * @param radius
     *            Radius to search within
     * @param radiusLocation
     *            Center location for the radius search
     * @param limitOffset
     *            Offset for pagination
     * @param limitCount
     *            Maximum number of results to return
     * @return List of results or null if the API is disabled
     */
    public List<String[]> performPartialLookup(int time, List<String> restrictUsers, List<String> excludeUsers, List<Object> restrictBlocks, List<Object> excludeBlocks, List<Integer> actionList, int radius, Location radiusLocation, int limitOffset, int limitCount) {
        if (!isEnabled()) {
            return null;
        }

        return processData(time, radius, radiusLocation, parseList(restrictBlocks), parseList(excludeBlocks), restrictUsers, excludeUsers, actionList, 0, 1, limitOffset, limitCount, true);
    }

    /**
     * Performs a partial lookup operation with basic filters and pagination support.
     * 
     * @param user
     *            The user to include in the lookup
     * @param time
     *            Time constraint in seconds
     * @param radius
     *            Radius to search within
     * @param location
     *            Center location for the radius search
     * @param restrict
     *            List of blocks to include in the lookup
     * @param exclude
     *            List of blocks to exclude from the lookup
     * @param limitOffset
     *            Offset for pagination
     * @param limitCount
     *            Maximum number of results to return
     * @return List of results or null if the API is disabled
     * @deprecated Use {@link #performPartialLookup(int, List, List, List, List, List, int, Location, int, int)} instead
     */
    @Deprecated
    public List<String[]> performPartialLookup(String user, int time, int radius, Location location, List<Object> restrict, List<Object> exclude, int limitOffset, int limitCount) {
        if (!isEnabled()) {
            return null;
        }

        return processData(user, time, radius, location, parseList(restrict), parseList(exclude), 0, 1, limitOffset, limitCount, true);
    }

    /**
     * Performs a database purge operation.
     * 
     * @param time
     *            Time in seconds for the purge operation
     */
    public void performPurge(int time) {
        Server server = Bukkit.getServer();
        server.dispatchCommand(server.getConsoleSender(), "co purge t:" + time + "s");
    }

    /**
     * Performs a restore operation with various filters.
     * 
     * @param time
     *            Time constraint in seconds
     * @param restrictUsers
     *            List of users to include in the restore
     * @param excludeUsers
     *            List of users to exclude from the restore
     * @param restrictBlocks
     *            List of blocks to include in the restore
     * @param excludeBlocks
     *            List of blocks to exclude from the restore
     * @param actionList
     *            List of actions to include in the restore
     * @param radius
     *            Radius to restore within
     * @param radiusLocation
     *            Center location for the radius restore
     * @return List of results or null if the API is disabled
     */
    public List<String[]> performRestore(int time, List<String> restrictUsers, List<String> excludeUsers, List<Object> restrictBlocks, List<Object> excludeBlocks, List<Integer> actionList, int radius, Location radiusLocation) {
        if (!isEnabled()) {
            return null;
        }

        return processData(time, radius, radiusLocation, parseList(restrictBlocks), parseList(excludeBlocks), restrictUsers, excludeUsers, actionList, 1, 2, -1, -1, false);
    }

    /**
     * Performs a restore operation with basic filters.
     * 
     * @param user
     *            The user to include in the restore
     * @param time
     *            Time constraint in seconds
     * @param radius
     *            Radius to restore within
     * @param location
     *            Center location for the radius restore
     * @param restrict
     *            List of blocks to include in the restore
     * @param exclude
     *            List of blocks to exclude from the restore
     * @return List of results or null if the API is disabled
     * @deprecated Use {@link #performRestore(int, List, List, List, List, List, int, Location)} instead
     */
    @Deprecated
    public List<String[]> performRestore(String user, int time, int radius, Location location, List<Object> restrict, List<Object> exclude) {
        if (!isEnabled()) {
            return null;
        }

        return processData(user, time, radius, location, parseList(restrict), parseList(exclude), 1, 2, -1, -1, false);
    }

    /**
     * Performs a rollback operation with various filters.
     * 
     * @param time
     *            Time constraint in seconds
     * @param restrictUsers
     *            List of users to include in the rollback
     * @param excludeUsers
     *            List of users to exclude from the rollback
     * @param restrictBlocks
     *            List of blocks to include in the rollback
     * @param excludeBlocks
     *            List of blocks to exclude from the rollback
     * @param actionList
     *            List of actions to include in the rollback
     * @param radius
     *            Radius to rollback within
     * @param radiusLocation
     *            Center location for the radius rollback
     * @return List of results or null if the API is disabled
     */
    public List<String[]> performRollback(int time, List<String> restrictUsers, List<String> excludeUsers, List<Object> restrictBlocks, List<Object> excludeBlocks, List<Integer> actionList, int radius, Location radiusLocation) {
        if (!isEnabled()) {
            return null;
        }

        return processData(time, radius, radiusLocation, parseList(restrictBlocks), parseList(excludeBlocks), restrictUsers, excludeUsers, actionList, 0, 2, -1, -1, false);
    }

    /**
     * Performs a rollback operation with basic filters.
     * 
     * @param user
     *            The user to include in the rollback
     * @param time
     *            Time constraint in seconds
     * @param radius
     *            Radius to rollback within
     * @param location
     *            Center location for the radius rollback
     * @param restrict
     *            List of blocks to include in the rollback
     * @param exclude
     *            List of blocks to exclude from the rollback
     * @return List of results or null if the API is disabled
     * @deprecated Use {@link #performRollback(int, List, List, List, List, List, int, Location)} instead
     */
    @Deprecated
    public List<String[]> performRollback(String user, int time, int radius, Location location, List<Object> restrict, List<Object> exclude) {
        if (!isEnabled()) {
            return null;
        }

        return processData(user, time, radius, location, parseList(restrict), parseList(exclude), 0, 2, -1, -1, false);
    }

    /**
     * Processes a data request with various filters.
     * 
     * @param time
     *            Time constraint in seconds
     * @param radius
     *            Radius for the operation
     * @param location
     *            Center location for the radius
     * @param restrictBlocksMap
     *            Map of blocks to include in the operation
     * @param excludeBlocks
     *            Map of blocks to exclude from the operation
     * @param restrictUsers
     *            List of users to include in the operation
     * @param excludeUsers
     *            List of users to exclude from the operation
     * @param actionList
     *            List of actions to include in the operation
     * @param action
     *            Action type for the operation
     * @param lookup
     *            Lookup type for the operation
     * @param offset
     *            Offset for pagination
     * @param rowCount
     *            Maximum number of results to return
     * @param useLimit
     *            Whether to use pagination limits
     * @return List of results or null if the parameters are invalid
     */
    private List<String[]> processData(int time, int radius, Location location, Map<Object, Boolean> restrictBlocksMap, Map<Object, Boolean> excludeBlocks, List<String> restrictUsers, List<String> excludeUsers, List<Integer> actionList, int action, int lookup, int offset, int rowCount, boolean useLimit) {
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
        if (actionList.isEmpty() && !restrictBlocks.isEmpty()) {
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

        if (actionList.isEmpty()) {
            actionList.add(0);
            actionList.add(1);
        }

        actionList.removeIf(actionListItem -> actionListItem > 3);

        if (restrictUsers.isEmpty()) {
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

    /**
     * Processes a data request with basic filters.
     * 
     * @param user
     *            The user to include in the operation
     * @param time
     *            Time constraint in seconds
     * @param radius
     *            Radius for the operation
     * @param location
     *            Center location for the radius
     * @param restrictBlocks
     *            Map of blocks to include in the operation
     * @param excludeBlocks
     *            Map of blocks to exclude from the operation
     * @param action
     *            Action type for the operation
     * @param lookup
     *            Lookup type for the operation
     * @param offset
     *            Offset for pagination
     * @param rowCount
     *            Maximum number of results to return
     * @param useLimit
     *            Whether to use pagination limits
     * @return List of results
     * @deprecated Use {@link #processData(int, int, Location, Map, Map, List, List, List, int, int, int, int, boolean)} instead
     */
    @Deprecated
    private List<String[]> processData(String user, int time, int radius, Location location, Map<Object, Boolean> restrictBlocks, Map<Object, Boolean> excludeBlocks, int action, int lookup, int offset, int rowCount, boolean useLimit) {
        ArrayList<String> restrictUsers = new ArrayList<>();
        if (user != null) {
            restrictUsers.add(user);
        }

        return processData(time, radius, location, restrictBlocks, excludeBlocks, restrictUsers, null, null, action, lookup, offset, rowCount, useLimit);
    }

    /**
     * Tests the API functionality.
     */
    public void testAPI() {
        Chat.console(Phrase.build(Phrase.API_TEST));
    }

    /**
     * Helper method to check if the API is enabled and the player is not null.
     * 
     * @param player
     *            The player to check
     * @return True if the API is enabled and the player is not null
     */
    private boolean isEnabledForPlayer(Player player) {
        return isEnabled() && player != null;
    }

    /**
     * Helper method to check if a user and location are valid.
     * 
     * @param user
     *            The username to check
     * @param location
     *            The location to check
     * @return True if the user and location are valid
     */
    private boolean isValidUserAndLocation(String user, Location location) {
        return user != null && location != null && !user.isEmpty();
    }
}
