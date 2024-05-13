package net.coreprotect.consumer.process;

import java.sql.Statement;

import org.bukkit.block.BlockState;

import net.coreprotect.database.StatementUtils;
import net.coreprotect.database.statement.SkullStatement;
import net.coreprotect.utility.Util;

class SkullUpdateProcess {

    static void process(Statement statement, Object object, int rowId) {
        /*
         * We're switching blocks around quickly.
         * This block could already be removed again by the time the server tries to modify it.
         * Ignore any errors.
         */
        if (object instanceof BlockState) {
            BlockState block = (BlockState) object;
            String query = "SELECT owner FROM " + StatementUtils.getTableName("skull") + " WHERE rowid='" + rowId + "' LIMIT 1";
            SkullStatement.getData(statement, block, query);
            Util.updateBlock(block);
        }
    }
}
