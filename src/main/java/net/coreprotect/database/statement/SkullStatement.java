package net.coreprotect.database.statement;

import java.sql.ResultSet;
import java.sql.Statement;

import org.bukkit.block.BlockState;
import org.bukkit.block.Skull;

import net.coreprotect.database.ConsumerWriteBatch;
import net.coreprotect.database.Database;
import net.coreprotect.paper.PaperAdapter;
import net.coreprotect.utility.ErrorReporter;

public class SkullStatement {

    private SkullStatement() {
        throw new IllegalStateException("Database class");
    }

    public static int insert(ConsumerWriteBatch batch, int time, String owner, String skin) {
        try {
            return batch.addSkull(time, owner, skin);
        }
        catch (Exception e) {
            Database.handleWriteFailure(e);
        }

        return 0;
    }

    public static void getData(Statement statement, BlockState block, String query) {
        try {
            if (!(block instanceof Skull)) {
                return;
            }

            Skull skull = (Skull) block;
            ResultSet resultSet = statement.executeQuery(query);

            while (resultSet.next()) {
                String owner = resultSet.getString("owner");
                if (owner != null && owner.length() > 1) {
                    PaperAdapter.ADAPTER.setSkullOwner(skull, owner);
                }

                String skin = resultSet.getString("skin");
                if (skin != null && skin.length() > 0) {
                    PaperAdapter.ADAPTER.setSkullSkin(skull, skin);
                }
            }

            resultSet.close();
        }
        catch (Exception e) {
            ErrorReporter.report(e);
        }
    }
}
