package net.coreprotect.database.logger;

import java.util.List;
import java.util.function.IntSupplier;

import org.bukkit.Location;
import org.bukkit.Material;

import net.coreprotect.bukkit.BukkitAdapter;
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.database.Database;
import net.coreprotect.database.ConsumerWriteBatch;
import net.coreprotect.database.statement.BlockStatement;
import net.coreprotect.database.statement.UserStatement;
import net.coreprotect.event.CoreProtectPreLogEvent;
import net.coreprotect.model.action.LookupActions;
import net.coreprotect.thread.CacheHandler;
import net.coreprotect.utility.BlockTypeUtils;
import net.coreprotect.utility.MaterialUtils;
import net.coreprotect.utility.WorldUtils;

public class BlockBreakLogger {

    private BlockBreakLogger() {
        throw new IllegalStateException("Database class");
    }

    public static void log(ConsumerWriteBatch preparedStmt, int batchCount, String user, Location location, int type, int data, List<Object> meta, String blockData, String overrideData) {
        log(preparedStmt, batchCount, user, location, MaterialUtils.getType(type), type, () -> data, meta, blockData, overrideData);
    }

    public static void log(ConsumerWriteBatch preparedStmt, int batchCount, String user, Location location, Material type, int data, List<Object> meta, String blockData, String overrideData) {
        log(preparedStmt, batchCount, user, location, type, 0, () -> data, meta, blockData, overrideData);
    }

    static void log(ConsumerWriteBatch preparedStmt, int batchCount, String user, Location location, Material checkType, int type, IntSupplier data, List<Object> meta, String blockData, String overrideData) {
        try {
            String blockKey = BlockTypeUtils.getBlockDataKey(blockData);
            if (blockKey.length() == 0 && checkType != null) {
                blockKey = checkType.getKey().toString();
            }
            else if (checkType != null && (checkType == Material.PAINTING || BukkitAdapter.ADAPTER.isItemFrame(checkType) || checkType == Material.ARMOR_STAND || checkType == Material.END_CRYSTAL)) {
                blockKey = checkType.getKey().toString();
            }
            if (checkType != null && blockKey.length() > 0) {
                Material blockDataType = MaterialUtils.getType(blockKey);
                if (blockDataType != null && !blockDataType.equals(checkType)) {
                    blockKey = checkType.getKey().toString();
                    blockData = null;
                }
            }

            if (checkType == null && blockKey.length() == 0) {
                return;
            }
            else if (checkType != null && (checkType.equals(Material.AIR) || checkType.equals(Material.CAVE_AIR)) && BlockTypeUtils.isAir(blockKey)) {
                return;
            }

            if (ConfigHandler.isBlacklisted(user, blockKey)) {
                return;
            }

            if (checkType == Material.LECTERN && blockData != null) {
                blockData = blockData.replace("has_book=true", "has_book=false");
            }
            else if (checkType != null && (checkType == Material.PAINTING || BukkitAdapter.ADAPTER.isItemFrame(checkType))) {
                blockData = overrideData;
            }

            CoreProtectPreLogEvent event = LoggerPreLog.fire(user, location, CoreProtectPreLogEvent.Action.BLOCK_BREAK, LookupActions.BLOCK_BREAK, checkType, null, null);
            if (event == null || ConfigHandler.isFilterBlacklisted(event.getUser(), blockKey)) {
                return;
            }

            int effectiveData = data.getAsInt();
            if (!user.startsWith("#")) {
                String cacheId = location.getBlockX() + "." + location.getBlockY() + "." + location.getBlockZ() + "." + WorldUtils.getWorldId(location.getWorld().getName());
                CacheHandler.spreadCache.remove(cacheId);
            }
            int userId = UserStatement.getId(preparedStmt, event.getUser(), true);
            Location eventLocation = event.getLocation();
            int wid = WorldUtils.getWorldId(eventLocation.getWorld().getName());
            int time = (int) (System.currentTimeMillis() / 1000L);
            int x = eventLocation.getBlockX();
            int y = eventLocation.getBlockY();
            int z = eventLocation.getBlockZ();
            int internalType = blockKey.length() > 0 ? MaterialUtils.getBlockId(blockKey, true) : type;
            CacheHandler.breakCache.put(x + "." + y + "." + z + "." + wid, new Object[] { time, event.getUser(), internalType });

            BlockStatement.insert(preparedStmt, batchCount, time, userId, wid, x, y, z, internalType, effectiveData, meta, blockData, LookupActions.BLOCK_BREAK, 0);
        }
        catch (Exception e) {
            Database.handleWriteFailure(e);
        }
    }

}
