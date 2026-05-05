package net.coreprotect.api;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import org.bukkit.Location;
import org.bukkit.block.Block;

import net.coreprotect.api.result.ContainerResult;
import net.coreprotect.config.Config;
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.database.Database;
import net.coreprotect.database.statement.UserStatement;
import net.coreprotect.utility.BlockUtils;
import net.coreprotect.utility.StringUtils;
import net.coreprotect.utility.WorldUtils;

/**
 * Provides API methods for block-related lookups in the CoreProtect database.
 */
public class BlockAPI {

    /**
     * Private constructor to prevent instantiation.
     * This is a utility class with static methods only.
     */
    private BlockAPI() {
        throw new IllegalStateException("API class");
    }

    /**
     * Performs a lookup of block-related actions at the specified block.
     * 
     * @param block
     *            The block to look up
     * @param offset
     *            Time constraint in seconds (0 means no time constraint)
     * @return List of results in a String array format
     */
    public static List<String[]> performLookup(Block block, int offset) {
        List<String[]> result = new ArrayList<>();

        if (!Config.getGlobal().API_ENABLED) {
            return result;
        }

        if (block == null) {
            return result;
        }

        try (Connection connection = Database.getConnection(false, 1000)) {
            if (connection == null) {
                return result;
            }

            int x = block.getX();
            int y = block.getY();
            int z = block.getZ();
            int time = (int) (System.currentTimeMillis() / 1000L);
            int worldId = WorldUtils.getWorldId(block.getWorld().getName());
            int checkTime = 0;

            if (offset > 0) {
                checkTime = time - offset;
            }

            try (Statement statement = connection.createStatement()) {
                String query = "SELECT time,user,action,type,data,blockdata,rolled_back FROM " + ConfigHandler.prefix + "block " + WorldUtils.getWidIndex("block") + "WHERE wid = '" + worldId + "' AND x = '" + x + "' AND z = '" + z + "' AND y = '" + y + "' AND time > '" + checkTime + "' ORDER BY rowid DESC";

                try (ResultSet results = statement.executeQuery(query)) {
                    while (results.next()) {
                        String resultTime = results.getString("time");
                        int resultUserId = results.getInt("user");
                        String resultAction = results.getString("action");
                        int resultType = results.getInt("type");
                        String resultData = results.getString("data");
                        byte[] resultBlockData = results.getBytes("blockdata");
                        String resultRolledBack = results.getString("rolled_back");

                        if (ConfigHandler.playerIdCacheReversed.get(resultUserId) == null) {
                            UserStatement.loadName(connection, resultUserId);
                        }

                        String resultUser = ConfigHandler.playerIdCacheReversed.get(resultUserId);
                        String blockData = BlockUtils.byteDataToString(resultBlockData, resultType);

                        String[] lookupData = new String[] { resultTime, resultUser, String.valueOf(x), String.valueOf(y), String.valueOf(z), String.valueOf(resultType), resultData, resultAction, resultRolledBack, String.valueOf(worldId), blockData };

                        result.add(StringUtils.toStringArray(lookupData));
                    }
                }
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        return result;
    }

    /**
     * Performs a lookup of container transactions at the specified location.
     * 
     * @param location
     *            The location to look up
     * @param offset
     *            Time constraint in seconds (0 means no time constraint)
     * @return List of results in a ContainerResult format
     */
    public static List<ContainerResult> performContainerLookup(Location location, int offset) {
        List<ContainerResult> result = new ArrayList<>();

        if (!Config.getGlobal().API_ENABLED) {
            return result;
        }

        if (location == null || location.getWorld() == null) {
            return result;
        }

        try (Connection connection = Database.getConnection(false, 1000)) {
            if (connection == null) {
                return result;
            }

            int x = location.getBlockX();
            int y = location.getBlockY();
            int z = location.getBlockZ();
            int time = (int) (System.currentTimeMillis() / 1000L);
            int worldId = WorldUtils.getWorldId(location.getWorld().getName());
            int checkTime = 0;

            if (offset > 0) {
                checkTime = time - offset;
            }

            String query = "SELECT time,user,action,type,data,amount,metadata,rolled_back FROM " + ConfigHandler.prefix + "container " + WorldUtils.getWidIndex("container") + "WHERE wid = ? AND x = ? AND z = ? AND y = ? AND time > ? ORDER BY rowid DESC";
            try (PreparedStatement statement = connection.prepareStatement(query)) {
                statement.setInt(1, worldId);
                statement.setInt(2, x);
                statement.setInt(3, z);
                statement.setInt(4, y);
                statement.setInt(5, checkTime);

                try (ResultSet results = statement.executeQuery()) {
                    while (results.next()) {
                        int resultUserId = results.getInt("user");
                        String resultUser = ConfigHandler.playerIdCacheReversed.get(resultUserId);
                        if (resultUser == null) {
                            resultUser = UserStatement.loadName(connection, resultUserId);
                        }

                        ContainerResult lookupData = new ContainerResult(
                                results.getLong("time"), resultUser, location.getWorld().getName(), x, y, z,
                                results.getInt("type"), results.getInt("data"), results.getInt("amount"), results.getBytes("metadata"),
                                results.getInt("action"), results.getInt("rolled_back")
                        );
                        result.add(lookupData);
                    }
                }
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        return result;
    }
}
