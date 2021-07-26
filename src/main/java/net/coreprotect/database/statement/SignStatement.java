package net.coreprotect.database.statement;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

import org.bukkit.Color;
import org.bukkit.DyeColor;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;

import net.coreprotect.bukkit.BukkitAdapter;

public class SignStatement {

    private SignStatement() {
        throw new IllegalStateException("Database class");
    }

    public static void insert(PreparedStatement preparedStmt, int batchCount, int time, int id, int wid, int x, int y, int z, int action, int color, int data, String line1, String line2, String line3, String line4) {
        try {
            preparedStmt.setInt(1, time);
            preparedStmt.setInt(2, id);
            preparedStmt.setInt(3, wid);
            preparedStmt.setInt(4, x);
            preparedStmt.setInt(5, y);
            preparedStmt.setInt(6, z);
            preparedStmt.setInt(7, action);
            preparedStmt.setInt(8, color);
            preparedStmt.setInt(9, data);
            preparedStmt.setString(10, line1);
            preparedStmt.setString(11, line2);
            preparedStmt.setString(12, line3);
            preparedStmt.setString(13, line4);
            preparedStmt.addBatch();

            if (batchCount > 0 && batchCount % 1000 == 0) {
                preparedStmt.executeBatch();
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void getData(Statement statement, BlockState block, String query) {
        try {
            if (!(block instanceof Sign)) {
                return;
            }

            Sign sign = (Sign) block;
            ResultSet resultSet = statement.executeQuery(query);

            while (resultSet.next()) {
                int color = resultSet.getInt("color");
                int data = resultSet.getInt("data");
                String line1 = resultSet.getString("line_1");
                String line2 = resultSet.getString("line_2");
                String line3 = resultSet.getString("line_3");
                String line4 = resultSet.getString("line_4");

                if (color > 0) {
                    sign.setColor(DyeColor.getByColor(Color.fromRGB(color)));
                }

                if (data > 0) {
                    BukkitAdapter.ADAPTER.setGlowing(sign, (data == 1 ? true : false));
                }

                sign.setLine(0, line1);
                sign.setLine(1, line2);
                sign.setLine(2, line3);
                sign.setLine(3, line4);
            }

            resultSet.close();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }
}
