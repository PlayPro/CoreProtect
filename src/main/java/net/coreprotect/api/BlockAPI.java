package net.coreprotect.api;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import org.bukkit.block.Block;

import net.coreprotect.config.ConfigHandler;
import net.coreprotect.database.Database;
import net.coreprotect.database.statement.UserStatement;
import net.coreprotect.utility.Util;

public class BlockAPI {

    public static List<String[]> performLookup(Block block, int offset) {
        List<String[]> result = new ArrayList<>();

        try (Connection connection = Database.getConnection(false, 1000)) {
            if (block == null) {
                return result;
            }

            int x = block.getX();
            int y = block.getY();
            int z = block.getZ();
            int time = (int) (System.currentTimeMillis() / 1000L);
            int worldId = Util.getWorldId(block.getWorld().getName());
            int checkTime = 0;
            if (offset > 0) {
                checkTime = time - offset;
            }

            if (connection == null) {
                return result;
            }

            Statement statement = connection.createStatement();
            String query = "SELECT time,user,action,type,data,blockdata,rolled_back FROM " + ConfigHandler.prefix + "block WHERE wid = '" + worldId + "' AND x = '" + x + "' AND z = '" + z + "' AND y = '" + y + "' AND time > '" + checkTime + "' ORDER BY rowid DESC";
            ResultSet results = statement.executeQuery(query);

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
                String blockData = Util.byteDataToString(resultBlockData, resultType);

                String[] lookupData = new String[] { resultTime, resultUser, String.valueOf(x), String.valueOf(y), String.valueOf(z), String.valueOf(resultType), resultData, resultAction, resultRolledBack, String.valueOf(worldId), blockData };
                String[] lineData = Util.toStringArray(lookupData);
                result.add(lineData);
            }
            results.close();
            statement.close();
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        return result;
    }

}
