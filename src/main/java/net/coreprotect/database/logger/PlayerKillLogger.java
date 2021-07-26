package net.coreprotect.database.logger;

import java.sql.PreparedStatement;
import java.util.Locale;

import org.bukkit.block.BlockState;

import net.coreprotect.config.ConfigHandler;
import net.coreprotect.database.statement.BlockStatement;
import net.coreprotect.database.statement.UserStatement;
import net.coreprotect.utility.Util;

public class PlayerKillLogger {

    private PlayerKillLogger() {
        throw new IllegalStateException("Database class");
    }

    public static void log(PreparedStatement preparedStmt, int batchCount, String user, BlockState block, String player) {
        try {
            if (ConfigHandler.blacklist.get(user.toLowerCase(Locale.ROOT)) != null) {
                return;
            }
            int wid = Util.getWorldId(block.getWorld().getName());
            int time = (int) (System.currentTimeMillis() / 1000L);
            int x = block.getX();
            int y = block.getY();
            int z = block.getZ();
            int userId = ConfigHandler.playerIdCache.get(user.toLowerCase(Locale.ROOT));
            if (ConfigHandler.playerIdCache.get(player.toLowerCase(Locale.ROOT)) == null) {
                UserStatement.loadId(preparedStmt.getConnection(), player, null);
            }
            int playerId = ConfigHandler.playerIdCache.get(player.toLowerCase(Locale.ROOT));
            BlockStatement.insert(preparedStmt, batchCount, time, userId, wid, x, y, z, 0, playerId, null, null, 3, 0);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

}
