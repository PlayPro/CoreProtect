package net.coreprotect.database.clickhouse;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Properties;

public final class ClickHouseJdbcConfig {

    private static final String SERVER_SETTING_PREFIX = "clickhouse_setting_";

    private final String host;
    private final int port;
    private final String database;
    private final String username;
    private final String password;
    private final boolean tls;
    private final String jdbcUrl;
    private final Properties properties;

    public ClickHouseJdbcConfig(String host, int port, String database, String username, String password, boolean tls) {
        String validatedHost = validateHost(host);
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("ClickHouse port must be between 1 and 65535");
        }
        String validatedDatabase = ClickHouseIdentifiers.requireIdentifier(database, "ClickHouse database");
        if (username == null || username.isEmpty()) {
            throw new IllegalArgumentException("ClickHouse username cannot be empty");
        }
        if (password == null) {
            throw new IllegalArgumentException("ClickHouse password cannot be null");
        }

        this.host = validatedHost;
        this.port = port;
        this.database = validatedDatabase;
        this.username = username;
        this.password = password;
        this.tls = tls;
        jdbcUrl = createJdbcUrl(validatedHost, port, validatedDatabase, tls);
        properties = new Properties();
        properties.setProperty("user", username);
        properties.setProperty("password", password);
        properties.setProperty("connection_timeout", "10000");
        properties.setProperty("socket_timeout", "300000");
        properties.setProperty("retry", "0");
        properties.setProperty(SERVER_SETTING_PREFIX + "async_insert", "0");
        properties.setProperty(SERVER_SETTING_PREFIX + "do_not_merge_across_partitions_select_final", "1");
        properties.setProperty(SERVER_SETTING_PREFIX + "wait_end_of_query", "1");
        properties.setProperty("ssl", Boolean.toString(tls));
        properties.setProperty("jdbc_ignore_unsupported_values", "false");
    }

    public String getJdbcUrl() {
        return jdbcUrl;
    }

    public String getDatabase() {
        return database;
    }

    String getEndpoint() {
        return (tls ? "https" : "http") + "://" + formatHost(host) + ":" + port;
    }

    String getUsername() {
        return username;
    }

    String getPassword() {
        return password;
    }

    public ClickHouseJdbcConfig withDatabase(String database) {
        return new ClickHouseJdbcConfig(host, port, database, username, password, tls);
    }

    public Properties getProperties() {
        Properties copy = new Properties();
        copy.putAll(properties);
        return copy;
    }

    private static String validateHost(String host) {
        if (host == null || host.isEmpty() || !host.equals(host.trim())) {
            throw new IllegalArgumentException("ClickHouse host cannot be empty or contain surrounding whitespace");
        }
        String normalized = host;
        if (host.startsWith("[") && host.endsWith("]")) {
            normalized = host.substring(1, host.length() - 1);
        }
        try {
            URI uri = new URI("http", null, normalized, 1, null, null, null);
            if (uri.getHost() == null) {
                throw new IllegalArgumentException("Invalid ClickHouse host: " + host);
            }
        }
        catch (URISyntaxException exception) {
            throw new IllegalArgumentException("Invalid ClickHouse host: " + host, exception);
        }
        return normalized;
    }

    private static String createJdbcUrl(String host, int port, String database, boolean tls) {
        try {
            URI endpoint = new URI(tls ? "https" : "http", null, host, port, "/" + database, null, null);
            return "jdbc:clickhouse:" + endpoint.toASCIIString();
        }
        catch (URISyntaxException exception) {
            throw new IllegalArgumentException("Invalid ClickHouse connection configuration", exception);
        }
    }

    private static String formatHost(String host) {
        return host.indexOf(':') >= 0 ? "[" + host + "]" : host;
    }
}
