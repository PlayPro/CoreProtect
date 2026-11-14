package net.coreprotect.patch.script;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import net.coreprotect.config.ConfigHandler;
import net.coreprotect.database.DuckDBSpatialIndex;
import net.coreprotect.utility.ErrorReporter;

public class __2_25_2 {

    protected static boolean patchClickHouse(Statement statement) {
        return true;
    }

    protected static boolean patch(Statement statement) {
        return true;
    }

    protected static boolean patchDuckDB(Statement statement) {
        Connection connection = null;
        boolean restoreAutoCommit = false;
        boolean transactionStarted = false;
        try {
            connection = statement.getConnection();
            String prefix = ConfigHandler.prefix;
            String table = prefix + "entity_spawn";
            if (hasUniqueConstraint(connection, table)) {
                long nextRowId = nextSequenceStart(statement, table);
                String sequence = table + "_rowid_seq";
                String stagingTable = table + "_new";
                if (connection.getAutoCommit()) {
                    connection.setAutoCommit(false);
                    restoreAutoCommit = true;
                }
                transactionStarted = true;

                statement.executeUpdate("CREATE SEQUENCE IF NOT EXISTS " + sequence + " START " + nextRowId);
                statement.executeUpdate("DROP TABLE IF EXISTS " + stagingTable);
                statement.executeUpdate("CREATE TABLE " + stagingTable + " (rowid INTEGER NOT NULL DEFAULT nextval('" + sequence + "'), time INTEGER, block_rowid BIGINT, kill_rowid INTEGER, uuid VARCHAR, wid INTEGER, current_wid INTEGER, origin_x DOUBLE, origin_y DOUBLE, origin_z DOUBLE, x DOUBLE, y DOUBLE, z DOUBLE, yaw FLOAT, pitch FLOAT, data BLOB, removed TINYINT)");
                statement.executeUpdate("INSERT INTO " + stagingTable + " SELECT rowid,time,block_rowid,kill_rowid,uuid,wid,current_wid,origin_x,origin_y,origin_z,x,y,z,yaw,pitch,data,removed FROM " + table);
                statement.executeUpdate("DROP TABLE " + table);
                statement.executeUpdate("ALTER TABLE " + stagingTable + " RENAME TO " + table);
                connection.commit();
                transactionStarted = false;
                if (restoreAutoCommit) {
                    connection.setAutoCommit(true);
                    restoreAutoCommit = false;
                }
            }

            DuckDBSpatialIndex.repairEmptyGaps(connection, prefix);
            return true;
        }
        catch (Exception e) {
            if (transactionStarted) {
                rollback(connection);
            }
            ErrorReporter.report(e);
            return false;
        }
        finally {
            if (restoreAutoCommit) {
                restoreAutoCommit(connection, true);
            }
        }
    }

    private static boolean hasUniqueConstraint(Connection connection, String table) throws SQLException {
        String query = "SELECT 1 FROM duckdb_constraints() WHERE schema_name = current_schema() AND table_name = ? AND constraint_type = 'UNIQUE' LIMIT 1";
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, table);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    private static long nextSequenceStart(Statement statement, String table) throws SQLException {
        try (ResultSet resultSet = statement.executeQuery("SELECT COALESCE(MAX(rowid), 0) + 1 FROM " + table)) {
            return resultSet.next() ? Math.max(1L, resultSet.getLong(1)) : 1L;
        }
    }

    private static void rollback(Connection connection) {
        if (connection == null) {
            return;
        }
        try {
            connection.rollback();
        }
        catch (Exception e) {
            ErrorReporter.report(e);
        }
    }

    private static void restoreAutoCommit(Connection connection, boolean autoCommit) {
        if (connection == null) {
            return;
        }
        try {
            connection.setAutoCommit(autoCommit);
        }
        catch (Exception e) {
            ErrorReporter.report(e);
        }
    }
}
