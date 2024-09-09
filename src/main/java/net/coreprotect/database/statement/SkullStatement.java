package net.coreprotect.database.statement;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

import org.bukkit.block.BlockState;
import org.bukkit.block.Skull;

import net.coreprotect.database.Database;
import net.coreprotect.paper.PaperAdapter;

public class SkullStatement {

    private SkullStatement() {
        throw new IllegalStateException("Database class");
    }

    public static ResultSet insert(PreparedStatement preparedStmt, int time, String owner, String skin) {
        try {
            preparedStmt.setInt(1, time);
            preparedStmt.setString(2, owner);
            preparedStmt.setString(3, skin);
            if (Database.hasReturningKeys()) {
                return preparedStmt.executeQuery();
            }
            else {
                preparedStmt.executeUpdate();
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        return null;
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
                if (owner != null && skin != null && skin.length() > 0) {
                    PaperAdapter.ADAPTER.setSkullSkin(skull, skin);
                }
            }

            resultSet.close();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }
}
