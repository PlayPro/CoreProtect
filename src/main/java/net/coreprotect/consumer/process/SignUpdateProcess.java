package net.coreprotect.consumer.process;

import java.sql.Statement;
import java.util.Locale;

import org.bukkit.block.BlockState;

import net.coreprotect.config.ConfigHandler;
import net.coreprotect.database.statement.SignStatement;
import net.coreprotect.utility.BlockUtils;
import net.coreprotect.utility.WorldUtils;

class SignUpdateProcess {

    static void process(Statement statement, Object object, String user, int action, int time) {
        /*
         * We're switching blocks around quickly.
         * This block could already be removed again by the time the server tries to modify it.
         * Ignore any errors.
         */
        if (object instanceof BlockState) {
            BlockState block = (BlockState) object;
            int x = block.getX();
            int y = block.getY();
            int z = block.getZ();
            int wid = WorldUtils.getWorldId(block.getWorld().getName());
            int userid = ConfigHandler.playerIdCache.get(user.toLowerCase(Locale.ROOT));
            String query = "";
            if (action == 0) {
                query = "SELECT color, color_secondary, data, waxed, face, line_1, line_2, line_3, line_4, line_5, line_6, line_7, line_8 FROM " + ConfigHandler.prefix + "sign WHERE user='" + userid + "' AND wid='" + wid + "' AND x='" + x + "' AND z='" + z + "' AND y='" + y + "' AND time < '" + time + "' ORDER BY rowid DESC LIMIT 0, 1";
            }
            else {
                query = "SELECT color, color_secondary, data, waxed, face, line_1, line_2, line_3, line_4, line_5, line_6, line_7, line_8 FROM " + ConfigHandler.prefix + "sign WHERE user='" + userid + "' AND wid='" + wid + "' AND x='" + x + "' AND z='" + z + "' AND y='" + y + "' AND time >= '" + time + "' ORDER BY rowid ASC LIMIT 0, 1";
            }
            SignStatement.getData(statement, block, query);
            BlockUtils.updateBlock(block);
        }
    }
}
