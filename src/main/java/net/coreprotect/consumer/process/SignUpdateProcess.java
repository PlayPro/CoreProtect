package net.coreprotect.consumer.process;

import java.sql.Connection;
import java.sql.SQLException;

import org.bukkit.block.Sign;

import net.coreprotect.database.statement.SignStatement;
import net.coreprotect.model.SignState;
import net.coreprotect.utility.BlockUtils;
import net.coreprotect.utility.WorldUtils;

class SignUpdateProcess {

    static void process(Connection connection, Object object, int userId, int action, int time) throws SQLException {
        if (!(object instanceof Sign) || userId <= 0) {
            return;
        }
        Sign sign = (Sign) object;
        int worldId = WorldUtils.getWorldId(sign.getWorld().getName());
        SignState state = SignStatement.loadBlockState(connection, userId, worldId, sign.getX(), sign.getY(), sign.getZ(), time, action == 0);
        if (state != null) {
            state.apply(sign);
            BlockUtils.updateBlock(sign);
        }
    }
}
