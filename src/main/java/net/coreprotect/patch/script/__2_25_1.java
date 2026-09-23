package net.coreprotect.patch.script;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;

import net.coreprotect.config.Config;
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.database.clickhouse.ClickHouseIdentifiers;
import net.coreprotect.database.clickhouse.ClickHouseJdbc;
import net.coreprotect.database.clickhouse.ClickHouseJdbcConfig;
import net.coreprotect.utility.ErrorReporter;

public class __2_25_1 {

    protected static boolean patchClickHouse(Statement statement) {
        try {
            Config config = Config.getGlobal();
            ClickHouseJdbcConfig jdbcConfig = new ClickHouseJdbcConfig(config.CLICKHOUSE_HOST, config.CLICKHOUSE_PORT,
                    config.CLICKHOUSE_DATABASE, config.CLICKHOUSE_USERNAME, config.CLICKHOUSE_PASSWORD, config.CLICKHOUSE_TLS);
            try (Connection connection = ClickHouseJdbc.openPatchConnection(jdbcConfig)) {
                upgradeEntityIndex(connection, config.CLICKHOUSE_DATABASE, ConfigHandler.prefix);
            }
            return true;
        }
        catch (Exception e) {
            ErrorReporter.report(e);
            return false;
        }
    }

    private static void upgradeEntityIndex(Connection connection, String database, String prefix) throws SQLException {
        String tableName = prefix + "event_data";
        String table = ClickHouseIdentifiers.qualified(database, tableName);
        try (Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE " + table + " ADD INDEX IF NOT EXISTS entity_spawn_rowid_idx entity_spawn_rowid TYPE bloom_filter(0.01) GRANULARITY 1");
            String query = "SELECT type_full,expr,granularity FROM system.data_skipping_indices WHERE database=? AND table=? AND name=? LIMIT 2";
            try (PreparedStatement indexStatement = connection.prepareStatement(query)) {
                indexStatement.setString(1, database);
                indexStatement.setString(2, tableName);
                indexStatement.setString(3, "entity_spawn_rowid_idx");
                try (ResultSet result = indexStatement.executeQuery()) {
                    if (!result.next()) {
                        throw new SQLException("ClickHouse entity inspection index is missing: " + tableName);
                    }
                    boolean matches = "bloom_filter(0.01)".equals(normalizeIndexDefinition(result.getString(1)))
                            && "entity_spawn_rowid".equals(normalizeIndexDefinition(result.getString(2)))
                            && result.getLong(3) == 1;
                    if (!matches || result.next()) {
                        throw new SQLException("ClickHouse entity inspection index has an incompatible definition: " + tableName);
                    }
                }
            }
            statement.execute("ALTER TABLE " + table + " MATERIALIZE INDEX entity_spawn_rowid_idx SETTINGS mutations_sync=1");
        }
    }

    private static String normalizeIndexDefinition(String value) {
        return value == null ? "" : value.replaceAll("[\\s`]", "").toLowerCase(Locale.ROOT);
    }

    protected static boolean patchDuckDB(Statement statement) {
        return true;
    }

    protected static boolean patch(Statement statement) {
        try {
            if (Config.getGlobal().MYSQL) {
                statement.executeUpdate("ALTER TABLE " + ConfigHandler.prefix + "sign MODIFY line_1 TEXT, MODIFY line_2 TEXT, MODIFY line_3 TEXT, MODIFY line_4 TEXT, MODIFY line_5 TEXT, MODIFY line_6 TEXT, MODIFY line_7 TEXT, MODIFY line_8 TEXT");
            }
            return true;
        }
        catch (Exception e) {
            ErrorReporter.report(e);
            return false;
        }
    }

}
