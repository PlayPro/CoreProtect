package net.coreprotect.database.clickhouse;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import com.clickhouse.jdbc.DataSourceImpl;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

public final class ClickHouseJdbc implements AutoCloseable {

    private static final Set<String> DDL_OPERATIONS = Set.of("ALTER", "CREATE", "DROP", "RENAME", "TRUNCATE");

    private final HikariDataSource dataSource;
    private final HikariDataSource auxiliaryDataSource;

    public ClickHouseJdbc(ClickHouseJdbcConfig config) {
        Objects.requireNonNull(config, "config");
        dataSource = pool(config, "CoreProtect-ClickHouse", 10);
        auxiliaryDataSource = pool(config, "CoreProtect-ClickHouse-Identity", 4);
    }

    private static HikariDataSource pool(ClickHouseJdbcConfig config, String name, int maximumPoolSize) {
        HikariConfig pool = new HikariConfig();
        pool.setDataSource(new HikariClickHouseDataSource(config));
        pool.setPoolName(name);
        pool.setMinimumIdle(0);
        pool.setMaximumPoolSize(maximumPoolSize);
        pool.setConnectionTimeout(10_000L);
        pool.setIdleTimeout(60_000L);
        pool.setInitializationFailTimeout(-1L);
        return new HikariDataSource(pool);
    }

    public Connection openConnection() throws SQLException {
        return dataSource.getConnection();
    }

    Connection openAuxiliaryConnection() throws SQLException {
        return auxiliaryDataSource.getConnection();
    }

    public void executeDdl(String ddl) throws SQLException {
        try (Connection connection = openConnection()) {
            executeDdl(connection, ddl);
        }
    }

    public void executeDdl(Connection connection, String ddl) throws SQLException {
        Objects.requireNonNull(connection, "connection");
        String statementSql = requireSingleDdl(ddl);
        try (Statement statement = connection.createStatement()) {
            statement.execute(statementSql);
        }
    }

    @Override
    public void close() {
        try {
            auxiliaryDataSource.close();
        }
        finally {
            dataSource.close();
        }
    }

    private static String requireSingleDdl(String ddl) {
        if (ddl == null) {
            throw new IllegalArgumentException("ClickHouse DDL cannot be null");
        }
        String normalized = ddl.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("ClickHouse DDL cannot be empty");
        }
        if (normalized.indexOf(';') >= 0) {
            throw new IllegalArgumentException("Execute one ClickHouse DDL statement at a time without a semicolon");
        }
        int separator = 0;
        while (separator < normalized.length() && !Character.isWhitespace(normalized.charAt(separator))) {
            separator++;
        }
        String operation = normalized.substring(0, separator).toUpperCase(Locale.ROOT);
        if (!DDL_OPERATIONS.contains(operation)) {
            throw new IllegalArgumentException("Unsupported ClickHouse DDL operation: " + operation);
        }
        return normalized;
    }

    private static final class HikariClickHouseDataSource extends DataSourceImpl {

        private HikariClickHouseDataSource(ClickHouseJdbcConfig config) {
            super(config.getJdbcUrl(), config.getProperties());
        }

        @Override
        public void setLoginTimeout(int seconds) {
            // ClickHouse enforces connection_timeout directly.
        }
    }
}
