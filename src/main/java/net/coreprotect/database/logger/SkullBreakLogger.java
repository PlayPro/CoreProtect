package net.coreprotect.database.logger;

import java.sql.PreparedStatement;

import org.bukkit.block.BlockState;
import org.bukkit.block.Skull;

import net.coreprotect.config.ConfigHandler;
import net.coreprotect.database.statement.SkullStatement;
import net.coreprotect.paper.PaperAdapter;
import net.coreprotect.utility.MaterialUtils;
import net.coreprotect.utility.ErrorReporter;

public class SkullBreakLogger {

    private SkullBreakLogger() {
        throw new IllegalStateException("Database class");
    }

    public static void log(PreparedStatement preparedStmt, PreparedStatement preparedStmt2, int batchCount, String user, BlockState block) {
        try {
            if (ConfigHandler.isBlacklisted(user) || block == null) {
                return;
            }
            int time = (int) (System.currentTimeMillis() / 1000L);
            int type = MaterialUtils.getBlockId(block.getType().name(), true);
            Skull skull = (Skull) block;
            String skullOwner = "";
            String skullSkin = null;
            long skullKey = 0;
            skullOwner = PaperAdapter.ADAPTER.getSkullOwner(skull);
            skullSkin = PaperAdapter.ADAPTER.getSkullSkin(skull);
            if ((skullOwner != null && skullOwner.length() > 0) || (skullSkin != null && skullSkin.length() > 0)) {
                skullKey = SkullStatement.insert(preparedStmt2, time, skullOwner, skullSkin);
            }

            BlockBreakLogger.log(preparedStmt, batchCount, user, block.getLocation(), type, skullKey, null, block.getBlockData().getAsString(), null);
        }
        catch (Exception e) {
            ErrorReporter.report(e);
        }
    }

}
