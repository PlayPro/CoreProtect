package net.coreprotect.database.logger;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.Locale;

import org.bukkit.block.BlockState;

import net.coreprotect.config.ConfigHandler;
import net.coreprotect.database.statement.BlockStatement;
import net.coreprotect.database.statement.EntityStatement;
import net.coreprotect.utility.Util;

public class EntityKillLogger {

    private EntityKillLogger() {
        throw new IllegalStateException("Database class");
    }

    public static void log(PreparedStatement preparedStmt, PreparedStatement preparedStmt2, int batchCount, String user, BlockState block, List<Object> data, int type) {
        try {
            if (ConfigHandler.blacklist.get(user.toLowerCase(Locale.ROOT)) != null) {
                return;
            }
            int wid = Util.getWorldId(block.getWorld().getName());
            int time = (int) (System.currentTimeMillis() / 1000L);
            int x = block.getX();
            int y = block.getY();
            int z = block.getZ();
            int userid = ConfigHandler.playerIdCache.get(user.toLowerCase(Locale.ROOT));
            EntityStatement.insert(preparedStmt2, time, data);
            ResultSet keys = preparedStmt2.getGeneratedKeys();
            keys.next();
            int entity_key = keys.getInt(1);
            keys.close();
            BlockStatement.insert(preparedStmt, batchCount, time, userid, wid, x, y, z, type, entity_key, null, null, 3, 0);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

}
