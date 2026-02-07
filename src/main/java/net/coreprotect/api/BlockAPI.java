package net.coreprotect.api;

import net.coreprotect.api.result.ContainerResult;
import net.coreprotect.config.Config;
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.database.Database;
import net.coreprotect.database.statement.UserStatement;
import net.coreprotect.utility.BlockUtils;
import net.coreprotect.utility.StringUtils;
import net.coreprotect.utility.WorldUtils;
import org.bukkit.Location;
import org.bukkit.block.Block;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

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
     * Creates a prepared statement with WHERE clause for block/container lookups.
     *
     * @param connection   Database connection
     * @param tableType    Either "block" or "container"
     * @param selectClause The SELECT portion of the query
     * @param location     The block to look up
     * @param checkTime    The minimum time constraint
     * @return PreparedStatement with parameters set
     * @throws Exception if there's an error creating the statement
     */
    private static PreparedStatement createLocationQuery(Connection connection, String tableType, String selectClause, Location location, int checkTime) throws Exception {
        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();
        int worldId = WorldUtils.getWorldId(location.getWorld().getName());

        String query = "SELECT " + selectClause + " FROM " + ConfigHandler.prefix + tableType + " " +
                WorldUtils.getWidIndex(tableType) +
                " WHERE wid = ? AND x = ? AND z = ? AND y = ? AND time > ? ORDER BY rowid DESC";

        PreparedStatement statement = connection.prepareStatement(query);
        statement.setInt(1, worldId);
        statement.setInt(2, x);
        statement.setInt(3, z);
        statement.setInt(4, y);
        statement.setInt(5, checkTime);

        return statement;
    }

    /**
     * Performs a lookup of block-related actions at the specified block.
     *
     * @param block  The block to look up
     * @param offset Time constraint in seconds (0 means no time constraint)
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

            try (PreparedStatement statement = createLocationQuery(connection, "block", "time,user,action,type,data,blockdata,rolled_back", block.getLocation(), checkTime)) {
                try (ResultSet results = statement.executeQuery()) {
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

                        String[] lookupData = new String[]{resultTime, resultUser, String.valueOf(x), String.valueOf(y), String.valueOf(z), String.valueOf(resultType), resultData, resultAction, resultRolledBack, String.valueOf(worldId), blockData};

                        result.add(StringUtils.toStringArray(lookupData));
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return result;
    }

    /**
     * Performs a lookup of container-related actions at the specified location.
     *
     * @param location The location to look up
     * @param offset   Time constraint in seconds (0 means no time constraint)
     * @return List of results in a ContainerResult array format
     */
    public static List<ContainerResult> performContainerLookup(Location location, int offset) {
        List<ContainerResult> result = new ArrayList<>();

        if (!Config.getGlobal().API_ENABLED) {
            return result;
        }

        if (location == null) {
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
            int checkTime = 0;

            if (offset > 0) {
                checkTime = time - offset;
            }

            try (PreparedStatement statement = createLocationQuery(connection, "container", "time,user,type,data,amount,metadata,action,rolled_back", location, checkTime)) {
                try (ResultSet results = statement.executeQuery()) {
                    while (results.next()) {
                        int resultUserId = results.getInt("user");
                        int resultAction = results.getInt("action");
                        int resultType = results.getInt("type");
                        int resultData = results.getInt("data");
                        long resultTime = results.getLong("time");
                        int resultAmount = results.getInt("amount");
                        int resultRolledBack = results.getInt("rolled_back");
                        byte[] resultMetadata = results.getBytes("metadata");

                        if (ConfigHandler.playerIdCacheReversed.get(resultUserId) == null) {
                            UserStatement.loadName(connection, resultUserId);
                        }

                        String resultUser = ConfigHandler.playerIdCacheReversed.get(resultUserId);

                        ContainerResult containerResult = new ContainerResult(
                                resultTime, resultUser, location.getWorld().getName(), x, y, z,
                                resultType, resultData, resultAmount, resultMetadata,
                                resultAction, resultRolledBack
                        );

                        result.add(containerResult);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return result;
    }
}
