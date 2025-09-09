package net.coreprotect.database.statement;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;

import net.coreprotect.bukkit.BukkitAdapter;
import net.coreprotect.utility.BlockUtils;

public class SignStatement {

    private SignStatement() {
        throw new IllegalStateException("Database class");
    }

    public static void insert(PreparedStatement preparedStmt, int batchCount, int time, int id, int wid, int x, int y, int z, int action, int color, int colorSecondary, int data, int waxed, int face, String line1, String line2, String line3, String line4, String line5, String line6, String line7, String line8) {
        try {
            preparedStmt.setInt(1, time);
            preparedStmt.setInt(2, id);
            preparedStmt.setInt(3, wid);
            preparedStmt.setInt(4, x);
            preparedStmt.setInt(5, y);
            preparedStmt.setInt(6, z);
            preparedStmt.setInt(7, action);
            preparedStmt.setInt(8, color);
            preparedStmt.setInt(9, colorSecondary);
            preparedStmt.setInt(10, data);
            preparedStmt.setInt(11, waxed);
            preparedStmt.setInt(12, face);
            preparedStmt.setString(13, line1);
            preparedStmt.setString(14, line2);
            preparedStmt.setString(15, line3);
            preparedStmt.setString(16, line4);
            preparedStmt.setString(17, line5);
            preparedStmt.setString(18, line6);
            preparedStmt.setString(19, line7);
            preparedStmt.setString(20, line8);
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
                int colorSecondary = resultSet.getInt("color_secondary");
                int data = resultSet.getInt("data");
                boolean isWaxed = resultSet.getInt("waxed") == 1;
                // boolean isFront = resultSet.getInt("face") == 0;
                String line1 = resultSet.getString("line_1");
                String line2 = resultSet.getString("line_2");
                String line3 = resultSet.getString("line_3");
                String line4 = resultSet.getString("line_4");
                String line5 = resultSet.getString("line_5");
                String line6 = resultSet.getString("line_6");
                String line7 = resultSet.getString("line_7");
                String line8 = resultSet.getString("line_8");

                if (color > 0) {
                    BukkitAdapter.ADAPTER.setColor(sign, true, color);
                }
                if (colorSecondary > 0) {
                    BukkitAdapter.ADAPTER.setColor(sign, false, colorSecondary);
                }

                boolean frontGlowing = BlockUtils.isSideGlowing(true, data);
                boolean backGlowing = BlockUtils.isSideGlowing(false, data);
                BukkitAdapter.ADAPTER.setGlowing(sign, true, frontGlowing);
                BukkitAdapter.ADAPTER.setGlowing(sign, false, backGlowing);
                BukkitAdapter.ADAPTER.setLine(sign, 0, line1);
                BukkitAdapter.ADAPTER.setLine(sign, 1, line2);
                BukkitAdapter.ADAPTER.setLine(sign, 2, line3);
                BukkitAdapter.ADAPTER.setLine(sign, 3, line4);
                BukkitAdapter.ADAPTER.setLine(sign, 4, line5);
                BukkitAdapter.ADAPTER.setLine(sign, 5, line6);
                BukkitAdapter.ADAPTER.setLine(sign, 6, line7);
                BukkitAdapter.ADAPTER.setLine(sign, 7, line8);
                BukkitAdapter.ADAPTER.setWaxed(sign, isWaxed);
            }

            resultSet.close();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }
}
