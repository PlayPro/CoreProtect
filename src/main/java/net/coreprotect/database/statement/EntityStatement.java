package net.coreprotect.database.statement;

import java.io.ByteArrayInputStream;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import net.coreprotect.CoreProtect;
import net.coreprotect.utility.serialize.Bytes;
import org.bukkit.block.BlockState;
import org.bukkit.util.io.BukkitObjectInputStream;

import net.coreprotect.config.ConfigHandler;

public class EntityStatement {

    private EntityStatement() {
        throw new IllegalStateException("Database class");
    }

    public static int insert(PreparedStatement preparedStmt, int time, String entityData) throws SQLException {
        final int rowid = CoreProtect.getInstance().rowNumbers().nextRowId("entity", preparedStmt.getConnection());
        preparedStmt.setInt(1, time);
        preparedStmt.setString(2, entityData);
        preparedStmt.setInt(3, rowid);
        preparedStmt.execute();

        return rowid;
    }

    @Deprecated // ch
    public static List<Object> getData(Statement statement, BlockState block, String query) {
        List<Object> result = new ArrayList<>();

        try {
            ResultSet resultSet = statement.executeQuery(query);
            while (resultSet.next()) {
                byte[] data = resultSet.getBytes("data");
                ByteArrayInputStream bais = new ByteArrayInputStream(data);
                BukkitObjectInputStream ins = new BukkitObjectInputStream(bais);
                @SuppressWarnings("unchecked")
                List<Object> input = (List<Object>) ins.readObject();
                ins.close();
                bais.close();
                result = input;
            }

            resultSet.close();
        }
        catch (Exception e) { // only display exception on development branch
            if (ConfigHandler.EDITION_BRANCH.contains("-dev")) {
                e.printStackTrace();
            }
        }

        return result;
    }
}
