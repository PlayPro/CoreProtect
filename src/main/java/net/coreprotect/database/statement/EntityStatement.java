package net.coreprotect.database.statement;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import org.bukkit.block.BlockState;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import net.coreprotect.config.ConfigHandler;
import net.coreprotect.database.Database;

public class EntityStatement {

    private EntityStatement() {
        throw new IllegalStateException("Database class");
    }

    public static ResultSet insert(PreparedStatement preparedStmt, int time, List<Object> data) {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            BukkitObjectOutputStream oos = new BukkitObjectOutputStream(bos);
            oos.writeObject(data);
            oos.flush();
            oos.close();
            bos.close();

            byte[] byte_data = bos.toByteArray();
            preparedStmt.setInt(1, time);
            preparedStmt.setObject(2, byte_data);
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
            if (!ConfigHandler.EDITION_BRANCH.contains("-dev")) {
                e.printStackTrace();
            }
        }

        return result;
    }
}
