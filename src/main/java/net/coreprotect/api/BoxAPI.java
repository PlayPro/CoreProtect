package net.coreprotect.api;

import net.coreprotect.api.result.ContainerResult;
import net.coreprotect.config.Config;
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.database.Database;
import net.coreprotect.database.statement.UserStatement;
import net.coreprotect.utility.BlockUtils;
import net.coreprotect.utility.StringUtils;
import net.coreprotect.utility.WorldUtils;
import org.bukkit.World;
import org.bukkit.util.BoundingBox;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Provides API methods for area-based lookups using bounding boxes in the CoreProtect database.
 */
public class BoxAPI {

    /**
     * Maximum number of results to return from area queries to prevent performance issues.
     */
    private static final int MAX_RESULTS = 10000;

    /**
     * Maximum bounding box volume to prevent excessive database queries.
     */
    private static final long MAX_BOUNDING_BOX_VOLUME = 1000000; // 100x100x100 blocks

    /**
     * Private constructor to prevent instantiation.
     * This is a utility class with static methods only.
     */
    private BoxAPI() {
        throw new IllegalStateException("API class");
    }

    /**
     * Functional interface for processing result sets into specific types.
     */
    @FunctionalInterface
    private interface ResultProcessor<T> {
        T processResult(ResultSet rs, Connection connection) throws SQLException;
    }

    /**
     * Validates bounding box size to prevent performance issues.
     */
    private static boolean isValidBoundingBoxSize(BoundingBox boundingBox) {
        double volume = boundingBox.getVolume();
        return volume > 0 && volume <= MAX_BOUNDING_BOX_VOLUME;
    }

    /**
     * Creates a prepared statement with WHERE clause for area-based lookups.
     * Fixed coordinate conversion to handle BoundingBox boundaries correctly.
     */
    private static PreparedStatement createAreaQuery(Connection connection, String tableType, String selectClause,
                                                    World world, BoundingBox boundingBox, int checkTime, String orderBy) throws Exception {
        // BoundingBox: min is inclusive, max is exclusive
        int minX = (int) Math.floor(boundingBox.getMinX());
        int maxX = (int) Math.floor(boundingBox.getMaxX()) - 1;
        int minY = (int) Math.floor(boundingBox.getMinY());
        int maxY = (int) Math.floor(boundingBox.getMaxY()) - 1;
        int minZ = (int) Math.floor(boundingBox.getMinZ());
        int maxZ = (int) Math.floor(boundingBox.getMaxZ()) - 1;
        int worldId = WorldUtils.getWorldId(world.getName());

        // Fix SQL spacing issue
        String query = "SELECT " + selectClause + " FROM " + ConfigHandler.prefix + tableType + " " +
                WorldUtils.getWidIndex(tableType) + " " +
                "WHERE wid = ? AND x BETWEEN ? AND ? AND y BETWEEN ? AND ? AND z BETWEEN ? AND ? AND time > ? " +
                "ORDER BY " + orderBy + " LIMIT " + MAX_RESULTS;

        PreparedStatement statement = connection.prepareStatement(query);
        statement.setInt(1, worldId);
        statement.setInt(2, minX);
        statement.setInt(3, maxX);
        statement.setInt(4, minY);
        statement.setInt(5, maxY);
        statement.setInt(6, minZ);
        statement.setInt(7, maxZ);
        statement.setInt(8, checkTime);

        return statement;
    }

    /**
     * Generic method for performing area-based lookups.
     */
    private static <T> List<T> performAreaQuery(World world, BoundingBox boundingBox, int offset,
                                              String tableType, String selectClause, String orderBy, ResultProcessor<T> processor) {
        List<T> result = new ArrayList<>();

        if (!Config.getGlobal().API_ENABLED || world == null || boundingBox == null) {
            return result;
        }

        // Validate bounding box size
        if (!isValidBoundingBoxSize(boundingBox)) {
            System.err.println("BoxAPI: Bounding box too large or invalid, volume: " + boundingBox.getVolume());
            return result;
        }

        try (Connection connection = Database.getConnection(false, 1000)) {
            if (connection == null) {
                return result;
            }

            int time = (int) (System.currentTimeMillis() / 1000L);
            int checkTime = offset > 0 ? time - offset : 0;

            try (PreparedStatement statement = createAreaQuery(connection, tableType, selectClause, world, boundingBox, checkTime, orderBy)) {
                try (ResultSet rs = statement.executeQuery()) {
                    while (rs.next()) {
                        result.add(processor.processResult(rs, connection));
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return result;
    }

    /**
     * Process block lookup results into String arrays.
     */
    private static String[] processBlockResult(ResultSet rs, Connection connection) throws SQLException {
        String resultTime = rs.getString("time");
        int resultUserId = rs.getInt("user");
        String resultAction = rs.getString("action");
        int resultType = rs.getInt("type");
        String resultData = rs.getString("data");
        byte[] resultBlockData = rs.getBytes("blockdata");
        String resultRolledBack = rs.getString("rolled_back");
        int x = rs.getInt("x");
        int y = rs.getInt("y");
        int z = rs.getInt("z");
        int worldId = rs.getInt("wid");

        if (ConfigHandler.playerIdCacheReversed.get(resultUserId) == null) {
            UserStatement.loadName(connection, resultUserId);
        }

        String resultUser = ConfigHandler.playerIdCacheReversed.get(resultUserId);
        String blockData = BlockUtils.byteDataToString(resultBlockData, resultType);

        String[] lookupData = new String[]{resultTime, resultUser, String.valueOf(x), String.valueOf(y),
                String.valueOf(z), String.valueOf(resultType), resultData, resultAction, resultRolledBack,
                String.valueOf(worldId), blockData};

        return StringUtils.toStringArray(lookupData);
    }

    /**
     * Process container lookup results into ContainerResult objects.
     */
    private static ContainerResult processContainerResult(ResultSet rs, Connection connection, World world) throws SQLException {
        int resultUserId = rs.getInt("user");
        int resultAction = rs.getInt("action");
        int resultType = rs.getInt("type");
        int resultData = rs.getInt("data");
        long resultTime = rs.getLong("time");
        int resultAmount = rs.getInt("amount");
        int resultRolledBack = rs.getInt("rolled_back");
        byte[] resultMetadata = rs.getBytes("metadata");
        int x = rs.getInt("x");
        int y = rs.getInt("y");
        int z = rs.getInt("z");

        if (ConfigHandler.playerIdCacheReversed.get(resultUserId) == null) {
            UserStatement.loadName(connection, resultUserId);
        }

        String resultUser = ConfigHandler.playerIdCacheReversed.get(resultUserId);

        return new ContainerResult(resultTime, resultUser, world.getName(), x, y, z,
                resultType, resultData, resultAmount, resultMetadata, resultAction, resultRolledBack);
    }

    /**
     * Performs a lookup of block-related actions within the specified bounding box.
     * Note: Uses rowid DESC ordering to maintain compatibility with original BlockAPI.
     */
    public static List<String[]> performAreaLookup(World world, BoundingBox boundingBox, int offset) {
        return performAreaQuery(world, boundingBox, offset, "block",
                "time,user,action,type,data,blockdata,rolled_back,x,y,z,wid",
                "rowid DESC",  // Maintain compatibility with original BlockAPI
                BoxAPI::processBlockResult);
    }

    /**
     * Performs a lookup of container-related actions within the specified bounding box.
     */
    public static List<ContainerResult> performAreaContainerLookup(World world, BoundingBox boundingBox, int offset) {
        return performAreaQuery(world, boundingBox, offset, "container",
                "time,user,type,data,amount,metadata,action,rolled_back,x,y,z",
                "rowid DESC",
                (rs, conn) -> processContainerResult(rs, conn, world));
    }

    /**
     * Performs a lookup of block-related actions within a cubic area around a center point.
     * Note: This creates a cubic area, not a spherical radius.
     */
    public static List<String[]> performCubicLookup(World world, int centerX, int centerY, int centerZ, int radius, int offset) {
        if (world == null || radius < 0) {
            return new ArrayList<>();
        }

        BoundingBox boundingBox = new BoundingBox(
                centerX - radius, centerY - radius, centerZ - radius,
                centerX + radius + 1, centerY + radius + 1, centerZ + radius + 1
        );

        return performAreaLookup(world, boundingBox, offset);
    }

    /**
     * Performs a lookup of container-related actions within a cubic area around a center point.
     * Note: This creates a cubic area, not a spherical radius.
     */
    public static List<ContainerResult> performCubicContainerLookup(World world, int centerX, int centerY, int centerZ, int radius, int offset) {
        if (world == null || radius < 0) {
            return new ArrayList<>();
        }

        BoundingBox boundingBox = new BoundingBox(
                centerX - radius, centerY - radius, centerZ - radius,
                centerX + radius + 1, centerY + radius + 1, centerZ + radius + 1
        );

        return performAreaContainerLookup(world, boundingBox, offset);
    }

    /**
     * Performs a lookup of block-related actions within a spherical radius around a center point.
     * This filters results to only include blocks within the actual spherical distance.
     */
    public static List<String[]> performSphericalLookup(World world, int centerX, int centerY, int centerZ, int radius, int offset) {
        if (world == null || radius < 0) {
            return new ArrayList<>();
        }

        // First get all blocks in the cubic area
        List<String[]> cubicResults = performCubicLookup(world, centerX, centerY, centerZ, radius, offset);

        // Filter to only include blocks within spherical distance
        List<String[]> sphericalResults = new ArrayList<>();
        double radiusSquared = radius * radius;

        for (String[] result : cubicResults) {
            try {
                int x = Integer.parseInt(result[2]);
                int y = Integer.parseInt(result[3]);
                int z = Integer.parseInt(result[4]);

                double distanceSquared = Math.pow(x - centerX, 2) + Math.pow(y - centerY, 2) + Math.pow(z - centerZ, 2);
                if (distanceSquared <= radiusSquared) {
                    sphericalResults.add(result);
                }
            } catch (NumberFormatException e) {
                // Skip malformed results
                continue;
            }
        }

        return sphericalResults;
    }

    /**
     * Performs a lookup of container-related actions within a spherical radius around a center point.
     * This filters results to only include containers within the actual spherical distance.
     */
    public static List<ContainerResult> performSphericalContainerLookup(World world, int centerX, int centerY, int centerZ, int radius, int offset) {
        if (world == null || radius < 0) {
            return new ArrayList<>();
        }

        // First get all containers in the cubic area
        List<ContainerResult> cubicResults = performCubicContainerLookup(world, centerX, centerY, centerZ, radius, offset);

        // Filter to only include containers within spherical distance
        List<ContainerResult> sphericalResults = new ArrayList<>();
        double radiusSquared = radius * radius;

        for (ContainerResult result : cubicResults) {
            double distanceSquared = Math.pow(result.getX() - centerX, 2) +
                                   Math.pow(result.getY() - centerY, 2) +
                                   Math.pow(result.getZ() - centerZ, 2);
            if (distanceSquared <= radiusSquared) {
                sphericalResults.add(result);
            }
        }

        return sphericalResults;
    }
}
