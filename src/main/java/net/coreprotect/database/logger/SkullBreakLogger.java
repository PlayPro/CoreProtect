package net.coreprotect.database.logger;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Locale;

import org.bukkit.block.BlockState;
import org.bukkit.block.Skull;

import net.coreprotect.config.ConfigHandler;
import net.coreprotect.database.statement.SkullStatement;
import net.coreprotect.utility.Util;

public class SkullBreakLogger {

    public static void log(PreparedStatement preparedStmt, PreparedStatement preparedStmt2, int batchCount, String user, BlockState block) {
        try {
            if (ConfigHandler.blacklist.get(user.toLowerCase(Locale.ROOT)) != null || block == null) {
                return;
            }
            int time = (int) (System.currentTimeMillis() / 1000L);
            int type = Util.getBlockId(block.getType().name(), true);
            Skull skull = (Skull) block;
            String skullOwner;
            int skullKey = 0;
            if (skull.hasOwner()) {
                skullOwner = skull.getOwningPlayer().getUniqueId().toString();
                SkullStatement.insert(preparedStmt2, time, skullOwner);
                ResultSet keys = preparedStmt2.getGeneratedKeys();
                keys.next();
                skullKey = keys.getInt(1);
                keys.close();
            }

            BlockBreakLogger.log(preparedStmt, batchCount, user, block.getLocation(), type, skullKey, null, block.getBlockData().getAsString());
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

}
