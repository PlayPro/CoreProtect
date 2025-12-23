package net.coreprotect.database.statement;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

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
        return insert(preparedStmt, time, data, 0);
    }

    public static ResultSet insert(PreparedStatement preparedStmt, int time, List<Object> data, int entityType) {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            GZIPOutputStream gzip = new GZIPOutputStream(bos);
            BukkitObjectOutputStream oos = new BukkitObjectOutputStream(gzip);
            oos.writeObject(data);
            oos.flush();
            oos.close();
            gzip.close();
            bos.close();

            byte[] byte_data = bos.toByteArray();
            preparedStmt.setInt(1, time);
            preparedStmt.setObject(2, byte_data);
            preparedStmt.setInt(3, entityType);
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
                InputStream inputStream;

                // Check for GZIP magic bytes (0x1F 0x8B) to detect compressed data
                // Maintains backwards compatibility with legacy uncompressed data
                if (data.length >= 2 && (data[0] & 0xFF) == 0x1F && (data[1] & 0xFF) == 0x8B) {
                    inputStream = new GZIPInputStream(new ByteArrayInputStream(data));
                }
                else {
                    inputStream = new ByteArrayInputStream(data);
                }

                BukkitObjectInputStream ins = new BukkitObjectInputStream(inputStream);
                @SuppressWarnings("unchecked")
                List<Object> input = (List<Object>) ins.readObject();
                ins.close();
                inputStream.close();
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
