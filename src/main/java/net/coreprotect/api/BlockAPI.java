package net.coreprotect.api;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import org.bukkit.Location;
import org.bukkit.block.Block;

import net.coreprotect.api.result.BlockResult;
import net.coreprotect.api.result.ContainerResult;
import net.coreprotect.config.Config;
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.database.Database;
import net.coreprotect.database.statement.UserStatement;
import net.coreprotect.utility.BlockUtils;
import net.coreprotect.utility.DatabaseUtils;
import net.coreprotect.utility.StringUtils;
import net.coreprotect.utility.WorldUtils;
import net.coreprotect.utility.ErrorReporter;

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
                String query = "SELECT time," + ConfigHandler.databaseType.getUserColumn() + ",action,type,data,blockdata,rolled_back FROM " + ConfigHandler.prefix + "block " + WorldUtils.getWidIndex("block") + "WHERE wid = " + worldId + " AND x = " + x + " AND z = " + z + " AND y = " + y + " AND time > " + checkTime + " ORDER BY rowid DESC";

                try (ResultSet results = statement.executeQuery(query)) {
                    while (results.next()) {
                        String resultTime = results.getString("time");
                        int resultUserId = results.getInt("user");
                        String resultAction = results.getString("action");
                        int resultType = results.getInt("type");
                        String resultData = results.getString("data");
                        byte[] resultBlockData = DatabaseUtils.getBytes(results, "blockdata");
                        String resultRolledBack = results.getString("rolled_back");

                        String resultUser = UserStatement.getName(connection, resultUserId);
                        String blockData = BlockUtils.byteDataToString(resultBlockData, resultType);

                        String[] lookupData = new String[] { resultTime, resultUser, String.valueOf(x), String.valueOf(y), String.valueOf(z), String.valueOf(resultType), resultData, resultAction, resultRolledBack, String.valueOf(worldId), blockData };

                        result.add(StringUtils.toStringArray(lookupData));
                    }
                }
            }
        }
        catch (Exception e) {
            ErrorReporter.report(e);
        }

        return result;
    }

    /**
     * Performs a typed lookup of block-related actions at the specified block.
     *
     * @param block
     *            The block to look up
     * @param options
     *            Lookup options. User, time, and limit are applied; location and radius are ignored because the block supplies the exact location.
     * @return List of results in a BlockResult format
     */
    public static List<BlockResult> performLookup(Block block, LookupOptions options) {
        List<BlockResult> result = new ArrayList<>();

        if (!Config.getGlobal().API_ENABLED) {
            return result;
        }

        if (block == null || block.getWorld() == null) {
            return result;
        }

        if (options == null) {
            options = LookupOptions.builder().build();
        }

        try (Connection connection = Database.getConnection(false, 1000)) {
            if (connection == null) {
                return result;
            }

            Integer userId = MessageAPI.getUserId(connection, options.getUser());
            if (userId != null && userId == -1) {
                return result;
            }

            int checkTime = 0;
            if (options.getTime() > 0) {
                checkTime = (int) (System.currentTimeMillis() / 1000L) - options.getTime();
            }

            int x = block.getX();
            int y = block.getY();
            int z = block.getZ();
            String worldName = block.getWorld().getName();
            int worldId = WorldUtils.getWorldId(worldName);

            StringBuilder query = new StringBuilder("SELECT time," + ConfigHandler.databaseType.getUserColumn() + ",action,type,data,blockdata,rolled_back,wid,x,y,z FROM ");
            query.append(ConfigHandler.prefix).append("block ").append(WorldUtils.getWidIndex("block"));
            query.append("WHERE wid = ? AND x = ? AND z = ? AND y = ? AND time > ?");
            if (userId != null) {
                query.append(" AND ").append(ConfigHandler.databaseType.getUserColumn()).append(" = ?");
            }
            query.append(" ORDER BY rowid DESC");
            if (options.hasLimit()) {
                query.append(" LIMIT ").append(options.getLimitCount()).append(" OFFSET ").append(options.getLimitOffset());
            }

            try (PreparedStatement statement = connection.prepareStatement(query.toString())) {
                statement.setInt(1, worldId);
                statement.setInt(2, x);
                statement.setInt(3, z);
                statement.setInt(4, y);
                statement.setInt(5, checkTime);
                if (userId != null) {
                    statement.setInt(6, userId);
                }

                try (ResultSet results = statement.executeQuery()) {
                    while (results.next()) {
                        result.add(parseBlockResult(connection, results, worldName));
                    }
                }
            }
        }
        catch (Exception e) {
            ErrorReporter.report(e);
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
        if (location == null) {
            return new ArrayList<>();
        }

        return performContainerLookup(LookupOptions.builder().time(offset).location(location).build());
    }

    /**
     * Performs a lookup of container transactions using shared lookup options.
     *
     * @param options
     *            Lookup options. Tracked entity-container rows match their original or persisted current/final location and return the persisted
     *            current/final location.
     * @return List of results in a ContainerResult format
     */
    public static List<ContainerResult> performContainerLookup(LookupOptions options) {
        List<ContainerResult> result = new ArrayList<>();

        if (!Config.getGlobal().API_ENABLED) {
            return result;
        }

        try (Connection connection = Database.getConnection(false, 1000)) {
            if (connection == null) {
                return result;
            }

            LookupFilter filter = LookupFilter.fromOptions(connection, options);
            if (filter.hasInvalidUser() || filter.hasInvalidLocation()) {
                return result;
            }

            StringBuilder containerWhere = new StringBuilder();
            filter.appendWhere(containerWhere, "container_rows");
            StringBuilder entityWhere = new StringBuilder();
            filter.appendEntityContainerWhere(entityWhere, "entity_rows");

            StringBuilder query = new StringBuilder("SELECT * FROM (");
            query.append("SELECT 0 AS source,container_rows.rowid AS id,container_rows.time,container_rows.").append(ConfigHandler.databaseType.getUserColumn()).append(",container_rows.wid,container_rows.x,container_rows.y,container_rows.z,container_rows.action,container_rows.type,container_rows.data,container_rows.amount,container_rows.metadata,container_rows.rolled_back FROM ")
                    .append(ConfigHandler.prefix).append("container container_rows ").append(containerWhere);
            query.append(" UNION ALL ");
            query.append("SELECT 1 AS source,entity_rows.rowid AS id,entity_rows.time,entity_rows.").append(ConfigHandler.databaseType.getUserColumn()).append(",spawn_rows.current_wid AS wid,spawn_rows.x,spawn_rows.y,spawn_rows.z,entity_rows.action,entity_rows.type,entity_rows.data,entity_rows.amount,entity_rows.metadata,entity_rows.rolled_back FROM ")
                    .append(ConfigHandler.prefix).append("entity_container entity_rows JOIN ").append(ConfigHandler.prefix).append("entity_spawn spawn_rows ON spawn_rows.rowid=entity_rows.entity_spawn_rowid ").append(entityWhere);
            query.append(") AS container_lookup ORDER BY time DESC,source DESC,id DESC");
            filter.appendLimit(query);

            try (PreparedStatement statement = connection.prepareStatement(query.toString())) {
                int parameterIndex = filter.bind(statement);
                filter.bindEntityContainer(statement, parameterIndex);

                try (ResultSet results = statement.executeQuery()) {
                    while (results.next()) {
                        result.add(parseContainerResult(connection, results));
                    }
                }
            }
        }
        catch (Exception e) {
            ErrorReporter.report(e);
        }

        return result;
    }

    private static ContainerResult parseContainerResult(Connection connection, ResultSet results) throws Exception {
        int resultUserId = results.getInt("user");
        String resultUser = UserStatement.getName(connection, resultUserId);

        return new ContainerResult(
                results.getLong("time"), resultUser, WorldUtils.getWorldName(results.getInt("wid")), (int) Math.floor(results.getDouble("x")), (int) Math.floor(results.getDouble("y")), (int) Math.floor(results.getDouble("z")),
                results.getInt("type"), results.getInt("data"), results.getInt("amount"), DatabaseUtils.getBytes(results, "metadata"),
                results.getInt("action"), results.getInt("rolled_back")
        );
    }

    private static BlockResult parseBlockResult(Connection connection, ResultSet results, String worldName) throws Exception {
        int resultUserId = results.getInt("user");
        String resultUser = UserStatement.getName(connection, resultUserId);

        int resultType = results.getInt("type");
        String blockData = BlockUtils.byteDataToString(DatabaseUtils.getBytes(results, "blockdata"), resultType);

        return new BlockResult(
                results.getLong("time"), resultUser, worldName, results.getInt("x"), results.getInt("y"), results.getInt("z"),
                resultType, results.getInt("data"), blockData, results.getInt("action"), results.getInt("rolled_back")
        );
    }

}
