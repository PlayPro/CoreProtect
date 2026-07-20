package net.coreprotect.database.clickhouse;

import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;

import com.clickhouse.client.api.Client;
import com.clickhouse.client.api.ClientFaultCause;
import com.clickhouse.client.api.insert.InsertResponse;
import com.clickhouse.client.api.insert.InsertSettings;
import com.clickhouse.data.ClickHouseFormat;

final class ClickHouseNativeClient implements AutoCloseable {

    private final Client client;

    ClickHouseNativeClient(ClickHouseJdbcConfig config) {
        Objects.requireNonNull(config, "config");
        client = new Client.Builder()
                .addEndpoint(config.getEndpoint())
                .setUsername(config.getUsername())
                .setPassword(config.getPassword())
                .setDefaultDatabase(config.getDatabase())
                .setConnectTimeout(10, ChronoUnit.SECONDS)
                .setSocketTimeout(5, ChronoUnit.MINUTES)
                .setMaxRetries(0)
                .retryOnFailures(ClientFaultCause.None)
                .useAsyncRequests(false)
                .compressClientRequest(true)
                .serverSetting("async_insert", "0")
                .serverSetting("max_insert_block_size", "1000000")
                .serverSetting("max_insert_block_size_bytes", "0")
                .serverSetting("wait_end_of_query", "1")
                .build();
    }

    void insert(String table, List<String> columns, InputStream rows, ClickHouseBatchIdentity identity, String streamName) throws SQLException {
        Objects.requireNonNull(columns, "columns");
        Objects.requireNonNull(rows, "rows");
        Objects.requireNonNull(identity, "identity");
        String stream = ClickHouseIdentifiers.requireIdentifier(streamName, "ClickHouse batch stream");
        InsertSettings settings = new InsertSettings()
                .setDeduplicationToken(identity.getDeduplicationToken() + ":" + stream)
                .setQueryId(identity.getBatchId() + "-" + stream)
                .compressClientRequest(true)
                .serverSetting("async_insert", "0")
                .serverSetting("max_insert_block_size", "1000000")
                .serverSetting("max_insert_block_size_bytes", "0")
                .serverSetting("wait_end_of_query", "1");
        try (InputStream input = rows; InsertResponse ignored = client.insert(table, columns, input, ClickHouseFormat.RowBinary, settings).get()) {
        }
        catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new SQLException("Interrupted while publishing a ClickHouse batch", exception);
        }
        catch (IOException exception) {
            throw new SQLException("Failed to close ClickHouse batch publication stream", exception);
        }
        catch (ExecutionException | RuntimeException exception) {
            Throwable cause = exception instanceof ExecutionException && exception.getCause() != null ? exception.getCause() : exception;
            throw new SQLException("ClickHouse batch publication failed", cause);
        }
    }

    @Override
    public void close() {
        client.close();
    }
}
