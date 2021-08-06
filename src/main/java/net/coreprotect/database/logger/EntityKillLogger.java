package net.coreprotect.database.logger;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.Locale;

import org.bukkit.block.BlockState;

import net.coreprotect.CoreProtect;
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.database.statement.BlockStatement;
import net.coreprotect.database.statement.EntityStatement;
import net.coreprotect.database.statement.UserStatement;
import net.coreprotect.event.CoreProtectPreLogEvent;
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

            CoreProtectPreLogEvent event = new CoreProtectPreLogEvent(user);
            CoreProtect.getInstance().getServer().getPluginManager().callEvent(event);

            int userId = UserStatement.getId(preparedStmt, event.getUser(), true);
            int wid = Util.getWorldId(block.getWorld().getName());
            int time = (int) (System.currentTimeMillis() / 1000L);
            int x = block.getX();
            int y = block.getY();
            int z = block.getZ();
            EntityStatement.insert(preparedStmt2, time, data);
            ResultSet keys = preparedStmt2.getGeneratedKeys();
            keys.next();
            int entity_key = keys.getInt(1);
            keys.close();
            BlockStatement.insert(preparedStmt, batchCount, time, userId, wid, x, y, z, type, entity_key, null, null, 3, 0);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

}
