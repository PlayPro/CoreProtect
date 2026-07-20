package net.coreprotect.database.clickhouse;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;
import java.util.UUID;

final class ClickHouseWriterRegistration implements AutoCloseable {

    private static final String WRITER_FILE = ".clickhouse-writer";
    private static final int MAX_WRITER_ID_BYTES = 64;

    private final ClickHouseJdbc jdbc;
    private final String table;
    private final UUID datasetId;
    private final UUID producerId;
    private final Path writerFile;
    private UUID writerId;
    private FileChannel writerChannel;
    private FileLock writerLock;
    private boolean owned;
    private boolean closed;

    ClickHouseWriterRegistration(ClickHouseJdbc jdbc, String database, String prefix, UUID datasetId, UUID producerId, Path controlDirectory) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        table = ClickHouseIdentifiers.qualified(database, prefix + "writer_registration");
        this.datasetId = Objects.requireNonNull(datasetId, "datasetId");
        this.producerId = Objects.requireNonNull(producerId, "producerId");
        writerFile = Objects.requireNonNull(controlDirectory, "controlDirectory").resolve(WRITER_FILE);
    }

    synchronized void acquire() throws SQLException {
        ensureOpen();
        if (owned) {
            return;
        }
        acquireLocalWriter();
        Registration registration = read();
        if (registration == null) {
            try {
                insert();
            }
            catch (SQLException insertionFailure) {
                try {
                    registration = read();
                }
                catch (SQLException reconciliationFailure) {
                    insertionFailure.addSuppressed(reconciliationFailure);
                    throw insertionFailure;
                }
                if (registration == null) {
                    throw insertionFailure;
                }
            }
            if (registration == null) {
                registration = read();
            }
        }
        if (registration == null) {
            throw new SQLException("ClickHouse writer registration is not visible after creation");
        }
        requireRegistration(registration);
        owned = true;
    }

    synchronized void verifyOwned() throws SQLException {
        ensureOpen();
        requireOwned();
        Registration registration = read();
        if (registration == null) {
            throw new OwnershipException("ClickHouse writer registration is not visible");
        }
        requireRegistration(registration);
    }

    private void requireRegistration(Registration registration) throws SQLException {
        if (!datasetId.equals(registration.datasetId) || !producerId.equals(registration.producerId)) {
            throw new OwnershipException("ClickHouse writer registration belongs to a different storage identity");
        }
        if (!writerId.equals(registration.writerId)) {
            throw new OwnershipException("ClickHouse dataset is registered to a different CoreProtect installation");
        }
    }

    synchronized void ensureOwned() throws SQLException {
        ensureOpen();
        requireOwned();
    }

    @Override
    public synchronized void close() throws SQLException {
        if (closed) {
            return;
        }
        IOException failure = null;
        try {
            if (writerLock != null) {
                writerLock.release();
            }
        }
        catch (IOException exception) {
            failure = exception;
        }
        try {
            if (writerChannel != null) {
                writerChannel.close();
            }
        }
        catch (IOException exception) {
            if (failure == null) {
                failure = exception;
            }
            else {
                failure.addSuppressed(exception);
            }
        }
        finally {
            writerLock = null;
            writerChannel = null;
            owned = false;
            closed = true;
        }
        if (failure != null) {
            throw new SQLException("Failed to close the local ClickHouse writer registration", failure);
        }
    }

    private void acquireLocalWriter() throws SQLException {
        if (writerLock != null && writerLock.isValid()) {
            return;
        }
        try {
            Files.createDirectories(writerFile.toAbsolutePath().getParent());
            writerChannel = FileChannel.open(writerFile, StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE);
            try {
                writerLock = writerChannel.tryLock();
            }
            catch (OverlappingFileLockException exception) {
                throw new SQLException("This CoreProtect installation already has an active ClickHouse writer", exception);
            }
            if (writerLock == null) {
                throw new SQLException("This CoreProtect installation already has an active ClickHouse writer");
            }
            writerId = readOrCreateWriterId();
        }
        catch (IOException exception) {
            throw new SQLException("Failed to open the local ClickHouse writer registration", exception);
        }
    }

    private UUID readOrCreateWriterId() throws IOException, SQLException {
        long size = writerChannel.size();
        if (size == 0) {
            UUID id = UUID.randomUUID();
            byte[] encoded = id.toString().getBytes(StandardCharsets.US_ASCII);
            writerChannel.position(0);
            writeFully(writerChannel, ByteBuffer.wrap(encoded));
            writerChannel.truncate(encoded.length);
            writerChannel.force(true);
            return id;
        }
        if (size > MAX_WRITER_ID_BYTES) {
            throw new SQLException("Local ClickHouse writer registration is invalid");
        }
        ByteBuffer encoded = ByteBuffer.allocate((int) size);
        writerChannel.position(0);
        while (encoded.hasRemaining()) {
            if (writerChannel.read(encoded) < 0) {
                throw new SQLException("Local ClickHouse writer registration is incomplete");
            }
        }
        String value = new String(encoded.array(), StandardCharsets.US_ASCII).trim();
        try {
            UUID id = UUID.fromString(value);
            if (!id.toString().equalsIgnoreCase(value)) {
                throw new IllegalArgumentException("Non-canonical UUID");
            }
            return id;
        }
        catch (IllegalArgumentException exception) {
            throw new SQLException("Local ClickHouse writer registration is invalid", exception);
        }
    }

    private Registration read() throws SQLException {
        String sql = "SELECT dataset_id,producer_id,writer_id FROM " + table
                + " ORDER BY registration_order,writer_id LIMIT 1";
        try (Connection connection = jdbc.openConnection(); PreparedStatement statement = connection.prepareStatement(sql); ResultSet resultSet = statement.executeQuery()) {
            if (!resultSet.next()) {
                return null;
            }
            return new Registration(UUID.fromString(resultSet.getString(1)), UUID.fromString(resultSet.getString(2)), UUID.fromString(resultSet.getString(3)));
        }
    }

    private void insert() throws SQLException {
        String sql = "INSERT INTO " + table
                + " (dataset_id,producer_id,writer_id,registered_at) VALUES (?,?,?,now64(3, 'UTC'))";
        try (Connection connection = jdbc.openConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, datasetId);
            statement.setObject(2, producerId);
            statement.setObject(3, writerId);
            statement.execute();
        }
    }

    private void requireOwned() throws SQLException {
        if (!owned || writerLock == null || !writerLock.isValid()) {
            throw new OwnershipException("ClickHouse writer registration is not owned by this CoreProtect installation");
        }
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("ClickHouse writer registration is closed");
        }
    }

    private static void writeFully(FileChannel channel, ByteBuffer data) throws IOException {
        while (data.hasRemaining()) {
            channel.write(data);
        }
    }

    private static final class Registration {

        private final UUID datasetId;
        private final UUID producerId;
        private final UUID writerId;

        private Registration(UUID datasetId, UUID producerId, UUID writerId) {
            this.datasetId = datasetId;
            this.producerId = producerId;
            this.writerId = writerId;
        }
    }

    static final class OwnershipException extends SQLException {

        private static final long serialVersionUID = 1L;

        private OwnershipException(String message) {
            super(message);
        }
    }

}
