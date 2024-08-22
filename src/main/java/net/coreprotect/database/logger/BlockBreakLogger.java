package net.coreprotect.database.logger;

import java.sql.PreparedStatement;
import java.util.List;
import java.util.Locale;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;

import net.coreprotect.CoreProtect;
import net.coreprotect.bukkit.BukkitAdapter;
import net.coreprotect.config.Config;
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.database.statement.BlockStatement;
import net.coreprotect.database.statement.UserStatement;
import net.coreprotect.event.CoreProtectBlockBreakPreLogEvent;
import net.coreprotect.event.CoreProtectPreLogEvent;
import net.coreprotect.thread.CacheHandler;
import net.coreprotect.utility.Util;

public class BlockBreakLogger {

    private BlockBreakLogger() {
        throw new IllegalStateException("Database class");
    }

    public static void log(PreparedStatement preparedStmt, int batchCount, String user, Location location, int type, int data, List<Object> meta, String blockData, String overrideData) {
        try {
            if (ConfigHandler.blacklist.get(user.toLowerCase(Locale.ROOT)) != null || location == null) {
                return;
            }

            Material checkType = Util.getType(type);
            if (checkType == null) {
                return;
            }
            else if (checkType.equals(Material.AIR) || checkType.equals(Material.CAVE_AIR)) {
                return;
            }

            if (ConfigHandler.blacklist.get(checkType.getKey().toString()) != null) {
                return;
            }

            if (!user.startsWith("#")) {
                String cacheId = location.getBlockX() + "." + location.getBlockY() + "." + location.getBlockZ() + "." + Util.getWorldId(location.getWorld().getName());
                CacheHandler.spreadCache.remove(cacheId);
            }

            if (checkType == Material.LECTERN) {
                blockData = blockData.replaceFirst("has_book=true", "has_book=false");
            }
            else if (checkType == Material.PAINTING || BukkitAdapter.ADAPTER.isItemFrame(checkType)) {
                blockData = overrideData;
            }


            // call events and fetch changed username, all other properties are readonly for now
            CoreProtectPreLogEvent coreProtectPreLogEvent = new CoreProtectPreLogEvent(user);
            if (Config.getGlobal().API_ENABLED && !Bukkit.isPrimaryThread()) {
                CoreProtect.getInstance().getServer().getPluginManager().callEvent(coreProtectPreLogEvent);
            }
            user = coreProtectPreLogEvent.getUser();

            CoreProtectBlockBreakPreLogEvent coreProtectBlockBreakPreLogEvent = new CoreProtectBlockBreakPreLogEvent(user, location);
            if (Config.getGlobal().API_ENABLED && !Bukkit.isPrimaryThread()) {
                CoreProtect.getInstance().getServer().getPluginManager().callEvent(coreProtectBlockBreakPreLogEvent);
            }
            user = coreProtectBlockBreakPreLogEvent.getUser();

            int userId = UserStatement.getId(preparedStmt, user, true);
            int wid = Util.getWorldId(location.getWorld().getName());
            int time = (int) (System.currentTimeMillis() / 1000L);
            int x = location.getBlockX();
            int y = location.getBlockY();
            int z = location.getBlockZ();
            CacheHandler.breakCache.put("" + x + "." + y + "." + z + "." + wid + "", new Object[]{time, user, type});

            if (coreProtectPreLogEvent.isCancelled() || coreProtectBlockBreakPreLogEvent.isCancelled()) {
                return;
            }

            BlockStatement.insert(preparedStmt, batchCount, time, userId, wid, x, y, z, type, data, meta, blockData, 0, 0);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

}
