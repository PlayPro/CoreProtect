package net.coreprotect.api;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

import org.bukkit.block.Block;

import net.coreprotect.config.ConfigHandler;
import net.coreprotect.database.Database;
import net.coreprotect.database.StatementUtils;
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

            try (PreparedStatement ps = connection.prepareStatement("SELECT time, \"user\", action, type, data, blockdata, rolled_back FROM " + StatementUtils.getTableName("block") + " " + Util.getWidIndex("block") + "WHERE wid = ? AND x = ? AND z = ? AND y = ? AND time > ? ORDER BY rowid DESC")) {
                ps.setInt(1, worldId);
                ps.setInt(2, x);
                ps.setInt(3, z);
                ps.setInt(4, y);
                ps.setInt(5, checkTime);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String resultTime = rs.getString("time");
                        int resultUserId = rs.getInt("user");
                        String resultAction = rs.getString("action");
                        int resultType = rs.getInt("type");
                        String resultData = rs.getString("data");
                        byte[] resultBlockData = rs.getBytes("blockdata");
                        String resultRolledBack = rs.getString("rolled_back");
                        if (ConfigHandler.playerIdCacheReversed.get(resultUserId) == null) {
                            UserStatement.loadName(connection, resultUserId);
                        }
                        String resultUser = ConfigHandler.playerIdCacheReversed.get(resultUserId);
                        String blockData = Util.byteDataToString(resultBlockData, resultType);

                        String[] lookupData = new String[] { resultTime, resultUser, String.valueOf(x), String.valueOf(y), String.valueOf(z), String.valueOf(resultType), resultData, resultAction, resultRolledBack, String.valueOf(worldId), blockData };
                        String[] lineData = Util.toStringArray(lookupData);
                        result.add(lineData);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return result;
    }

}
