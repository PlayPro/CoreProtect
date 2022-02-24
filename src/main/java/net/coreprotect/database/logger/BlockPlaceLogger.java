package net.coreprotect.database.logger;

import java.sql.PreparedStatement;
import java.util.List;
import java.util.Locale;

import org.bukkit.Material;
import org.bukkit.block.BlockState;

import net.coreprotect.CoreProtect;
import net.coreprotect.bukkit.BukkitAdapter;
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.database.statement.BlockStatement;
import net.coreprotect.database.statement.UserStatement;
import net.coreprotect.event.CoreProtectPreLogEvent;
import net.coreprotect.thread.CacheHandler;
import net.coreprotect.utility.Util;

public class BlockPlaceLogger {

    private BlockPlaceLogger() {
        throw new IllegalStateException("Database class");
    }

    public static void log(PreparedStatement preparedStmt, int batchCount, String user, BlockState block, int replacedType, int replacedData, Material forceType, int forceData, boolean force, List<Object> meta, String blockData, String replaceBlockData) {
        try {
            if (user == null || ConfigHandler.blacklist.get(user.toLowerCase(Locale.ROOT)) != null) {
                return;
            }
            Material type = block.getType();
            if (blockData == null && (forceType == null || (!forceType.equals(Material.WATER)) && (!forceType.equals(Material.LAVA)))) {
                blockData = block.getBlockData().getAsString();
                if (blockData.equals("minecraft:air")) {
                    blockData = null;
                }
            }
            int data = 0;
            if (forceType != null && force) {
                type = forceType;
                if (BukkitAdapter.ADAPTER.isItemFrame(type) || type.equals(Material.SPAWNER) || type.equals(Material.PAINTING) || type.equals(Material.SKELETON_SKULL) || type.equals(Material.SKELETON_WALL_SKULL) || type.equals(Material.WITHER_SKELETON_SKULL) || type.equals(Material.WITHER_SKELETON_WALL_SKULL) || type.equals(Material.ZOMBIE_HEAD) || type.equals(Material.ZOMBIE_WALL_HEAD) || type.equals(Material.PLAYER_HEAD) || type.equals(Material.PLAYER_WALL_HEAD) || type.equals(Material.CREEPER_HEAD) || type.equals(Material.CREEPER_WALL_HEAD) || type.equals(Material.DRAGON_HEAD) || type.equals(Material.DRAGON_WALL_HEAD) || type.equals(Material.ARMOR_STAND) || type.equals(Material.END_CRYSTAL)) {
                    data = forceData; // mob spawner, skull
                }
                else if (user.startsWith("#")) {
                    data = forceData;
                }
            }
            else if (forceType != null && !type.equals(forceType)) {
                type = forceType;
                data = forceData;
            }

            if (type.equals(Material.AIR) || type.equals(Material.CAVE_AIR)) {
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

            if (event.getUser().length() > 0) {
                CacheHandler.lookupCache.put("" + x + "." + y + "." + z + "." + wid + "", new Object[] { time, event.getUser(), type });
            }

            int internalType = Util.getBlockId(type.name(), true);

            if (replacedType > 0 && Util.getType(replacedType) != Material.AIR && Util.getType(replacedType) != Material.CAVE_AIR) {
                BlockStatement.insert(preparedStmt, batchCount, time, userId, wid, x, y, z, replacedType, replacedData, null, replaceBlockData, 0, 0);
            }

            BlockStatement.insert(preparedStmt, batchCount, time, userId, wid, x, y, z, internalType, data, meta, blockData, 1, 0);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

}
