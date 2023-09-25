package net.coreprotect.database.statement;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.block.BlockState;
import org.bukkit.block.Skull;

import net.coreprotect.database.Database;

public class SkullStatement {

    private SkullStatement() {
        throw new IllegalStateException("Database class");
    }

    public static ResultSet insert(PreparedStatement preparedStmt, int time, String owner) {
        try {
            preparedStmt.setInt(1, time);
            preparedStmt.setString(2, owner);
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
                if (owner != null && owner.length() >= 32) {
                    skull.setOwningPlayer(Bukkit.getOfflinePlayer(UUID.fromString(owner)));
                }
            }

            resultSet.close();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }
}
