package net.coreprotect.api;

import net.coreprotect.CoreProtect;
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.database.Database;
import net.coreprotect.database.statement.UserStatement;
import net.coreprotect.utility.Util;
import org.bukkit.block.Block;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

public final class ContainerTransactionsAPI {

    private static final int CONNECTION_WAIT_TIME = 1000;

    private ContainerTransactionsAPI() {
        throw new AssertionError();
    }

    public static List<String[]> performLookup(Block block, int offset) {
        List<String[]> result = new ArrayList<>();

        if (block == null) {
            return result;
        }

        try (Connection connection = Database.getConnection(false, CONNECTION_WAIT_TIME)) {
            final int x = block.getX();
            final int y = block.getY();
            final int z = block.getZ();
            final int now = (int) (System.currentTimeMillis() / 1000L);
            final int worldId = Util.getWorldId(block.getWorld().getName());
            final int timeFrom = offset > 0 ? now - offset : 0;

            if (connection == null) {
                return result;
            }

            try (Statement statement = connection.createStatement()) {
                String query = "SELECT time,user,action,type,data,rolled_back,amount,metadata FROM " +
                        ConfigHandler.prefix + "container " + Util.getWidIndex("container") +
                        "WHERE wid = '" + worldId + "' AND x = '" + x + "' AND z = '" + z + "' AND y = '" + y + "'" +
                        " AND time > '" + timeFrom + "' ORDER BY rowid DESC";
                try (ResultSet resultSet = statement.executeQuery(query)) {

                    while (resultSet.next()) {
                        final String resultTime = resultSet.getString("time");
                        final int resultUserId = resultSet.getInt("user");
                        final String resultAction = resultSet.getString("action");
                        final int resultType = resultSet.getInt("type");
                        final String resultData = resultSet.getString("data");
                        final String resultRolledBack = resultSet.getString("rolled_back");
                        final int resultAmount = resultSet.getInt("amount");
                        final byte[] resultMetadata = resultSet.getBytes("metadata");
                        if (ConfigHandler.playerIdCacheReversed.get(resultUserId) == null) {
                            UserStatement.loadName(connection, resultUserId);
                        }
                        String resultUser = ConfigHandler.playerIdCacheReversed.get(resultUserId);
                        final String metadata = resultMetadata != null ? new String(resultMetadata, StandardCharsets.ISO_8859_1) : "";

                        String[] resultElement = new String[]{ resultTime, resultUser,
                                String.valueOf(x), String.valueOf(y), String.valueOf(z), String.valueOf(resultType),
                                resultData, resultAction, resultRolledBack, String.valueOf(worldId),
                                String.valueOf(resultAmount), metadata, "" };
                        result.add(resultElement);
                    }
                }
            }
        }
        catch (SQLException e) {
            CoreProtect.getInstance().getLogger().log(Level.WARNING, e.toString(), e);
        }

        return result;
    }

}
