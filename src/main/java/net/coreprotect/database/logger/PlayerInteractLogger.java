package net.coreprotect.database.logger;

import java.sql.PreparedStatement;
import java.util.Locale;

import org.bukkit.Material;
import org.bukkit.block.BlockState;

import net.coreprotect.config.ConfigHandler;
import net.coreprotect.database.statement.BlockStatement;
import net.coreprotect.utility.Util;

public class PlayerInteractLogger {

    public static void log(PreparedStatement preparedStmt, int batchCount, String user, BlockState block) {
        try {
            int type = Util.getBlockId(block.getType().name(), true);
            if (ConfigHandler.blacklist.get(user.toLowerCase(Locale.ROOT)) != null || Util.getType(type).equals(Material.AIR) || Util.getType(type).equals(Material.CAVE_AIR)) {
                return;
            }
            int wid = Util.getWorldId(block.getWorld().getName());
            int userId = ConfigHandler.playerIdCache.get(user.toLowerCase(Locale.ROOT));
            int time = (int) (System.currentTimeMillis() / 1000L);
            int x = block.getX();
            int y = block.getY();
            int z = block.getZ();
            int data = 0;
            BlockStatement.insert(preparedStmt, batchCount, time, userId, wid, x, y, z, type, data, null, null, 2, 0);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

}
