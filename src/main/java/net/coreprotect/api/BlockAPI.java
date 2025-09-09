package net.coreprotect.api;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import org.bukkit.block.Block;

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
}
