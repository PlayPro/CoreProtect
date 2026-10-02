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
import java.sql.SQLException;
import java.util.Objects;
import java.util.UUID;

final class ClickHouseWriterRegistration implements AutoCloseable {

    private static final String WRITER_FILE = ".clickhouse-writer";

    private final Path writerFile;
    private FileChannel writerChannel;
    private FileLock writerLock;
    private boolean closed;

    ClickHouseWriterRegistration(Path controlDirectory) {
        writerFile = Objects.requireNonNull(controlDirectory, "controlDirectory").resolve(WRITER_FILE);
    }

    synchronized void acquire() throws SQLException {
        ensureOpen();
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
        }
        catch (IOException exception) {
            throw new SQLException("Failed to open the local ClickHouse writer registration", exception);
        }
    }

    synchronized UUID schemaOwner() throws SQLException {
        verifyOwned();
        try {
            if (writerChannel.size() == 0) {
                ByteBuffer value = StandardCharsets.US_ASCII.encode(UUID.randomUUID().toString());
                writerChannel.position(0);
                while (value.hasRemaining()) {
                    writerChannel.write(value);
                }
                writerChannel.force(true);
            }
            if (writerChannel.size() != 36) {
                throw new SQLException("Invalid ClickHouse schema owner identity in " + writerFile);
            }
            ByteBuffer value = ByteBuffer.allocate(36);
            writerChannel.position(0);
            while (value.hasRemaining() && writerChannel.read(value) >= 0) {
            }
            value.flip();
            UUID stored = UUID.fromString(StandardCharsets.US_ASCII.decode(value).toString());
            Object fileKey = Files.readAttributes(writerFile, java.nio.file.attribute.BasicFileAttributes.class).fileKey();
            String identity = stored + "\n" + writerFile.toRealPath() + "\n" + fileKey;
            return UUID.nameUUIDFromBytes(identity.getBytes(StandardCharsets.UTF_8));
        }
        catch (IOException | IllegalArgumentException exception) {
            throw new SQLException("Unable to read ClickHouse schema owner identity", exception);
        }
    }

    synchronized void verifyOwned() throws SQLException {
        ensureOpen();
        if (writerLock == null || !writerLock.isValid()) {
            throw new OwnershipException("ClickHouse writer registration is not owned by this CoreProtect installation");
        }
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
            closed = true;
        }
        if (failure != null) {
            throw new SQLException("Failed to close the local ClickHouse writer registration", failure);
        }
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("ClickHouse writer registration is closed");
        }
    }

    static final class OwnershipException extends SQLException {

        private static final long serialVersionUID = 1L;

        private OwnershipException(String message) {
            super(message);
        }
    }

}
