package net.coreprotect.database.logger;

import java.sql.PreparedStatement;
import java.util.Locale;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.BlockState;

import net.coreprotect.CoreProtect;
import net.coreprotect.config.Config;
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.database.statement.BlockStatement;
import net.coreprotect.database.statement.UserStatement;
import net.coreprotect.event.CoreProtectPreLogEvent;
import net.coreprotect.utility.MaterialUtils;
import net.coreprotect.utility.WorldUtils;

public class PlayerInteractLogger {

    private PlayerInteractLogger() {
        throw new IllegalStateException("Database class");
    }

    public static void log(PreparedStatement preparedStmt, int batchCount, String user, BlockState block, Material blockType) {
        try {
            int type = MaterialUtils.getBlockId(blockType.name(), true);
            if (ConfigHandler.blacklist.get(user.toLowerCase(Locale.ROOT)) != null || MaterialUtils.getType(type).equals(Material.AIR) || MaterialUtils.getType(type).equals(Material.CAVE_AIR)) {
                return;
            }

            CoreProtectPreLogEvent event = new CoreProtectPreLogEvent(user);
            if (Config.getGlobal().API_ENABLED && !Bukkit.isPrimaryThread()) {
                CoreProtect.getInstance().getServer().getPluginManager().callEvent(event);
            }

            if (event.isCancelled()) {
                return;
            }

            int userId = UserStatement.getId(preparedStmt, event.getUser(), true);
            int wid = WorldUtils.getWorldId(block.getWorld().getName());
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
