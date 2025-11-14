package net.coreprotect.database;

import java.sql.ResultSet;
import java.sql.Statement;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;

import net.coreprotect.config.ConfigHandler;
import net.coreprotect.database.statement.UserStatement;
import net.coreprotect.thread.CacheHandler;
import net.coreprotect.utility.MaterialUtils;
import net.coreprotect.utility.WorldUtils;

public class BlockLookup {

    public static String whoPlaced(Statement statement, BlockState block) {
        String result = "";

        try {
            if (block == null) {
                return result;
            }

            int x = block.getX();
            int y = block.getY();
            int z = block.getZ();
            int time = (int) (System.currentTimeMillis() / 1000L);
            int worldId = WorldUtils.getWorldId(block.getWorld().getName());
            String query = "SELECT user,type FROM " + ConfigHandler.prefix + "block " + WorldUtils.getWidIndex("block") + "WHERE wid = '" + worldId + "' AND x = '" + x + "' AND z = '" + z + "' AND y = '" + y + "' AND rolled_back IN(0,2) AND action='1' ORDER BY rowid DESC LIMIT 0, 1";

            ResultSet results = statement.executeQuery(query);
            while (results.next()) {
                int resultUserId = results.getInt("user");
                int resultType = results.getInt("type");

                if (ConfigHandler.playerIdCacheReversed.get(resultUserId) == null) {
                    UserStatement.loadName(statement.getConnection(), resultUserId);
                }

                result = ConfigHandler.playerIdCacheReversed.get(resultUserId);
                if (result.length() > 0) {
                    Material resultMaterial = MaterialUtils.getType(resultType);
                    CacheHandler.lookupCache.put("" + x + "." + y + "." + z + "." + worldId + "", new Object[] { time, result, resultMaterial });
                }
            }
            results.close();
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        return result;
    }

    public static String whoPlacedCache(Block block) {
        if (block == null) {
            return "";
        }

        return whoPlacedCache(block.getState());
    }

    public static String whoPlacedCache(BlockState block) {
        String result = "";

        try {
            if (block == null) {
                return result;
            }

            int x = block.getX();
            int y = block.getY();
            int z = block.getZ();
            int worldId = WorldUtils.getWorldId(block.getWorld().getName());

            String cords = "" + x + "." + y + "." + z + "." + worldId + "";
            Object[] data = CacheHandler.lookupCache.get(cords);

            if (data != null) {
                result = (String) data[1];
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        return result;
    }

    public static String whoRemovedCache(BlockState block) {
        /*
         * Performs a lookup on who removed a block, from memory. Only searches through the last 30 seconds of block removal data.
         */
        String result = "";

        try {
            if (block != null) {
                int x = block.getX();
                int y = block.getY();
                int z = block.getZ();
                int worldId = WorldUtils.getWorldId(block.getWorld().getName());

                String cords = "" + x + "." + y + "." + z + "." + worldId + "";
                Object[] data = CacheHandler.breakCache.get(cords);

                if (data != null) {
                    result = (String) data[1];
                }
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        return result;
    }
}
