package net.coreprotect.database.clickhouse;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

final class ClickHouseIdentityReservation {

    static final String BATCH_SEQUENCE = "batch_sequence";

    private static final String CLAIM_ORDER = "_block_number,block_start,toString(writer_id)";

    private static final long BLOCK_SIZE = 4_096L;
    private static final long RESERVATION_TIMEOUT_NANOS = TimeUnit.SECONDS.toNanos(30);

    private final ClickHouseJdbc jdbc;
    private final String table;
    private final UUID writerId;
    private final Map<String, Sequence> sequences = new ConcurrentHashMap<>();

    ClickHouseIdentityReservation(ClickHouseJdbc jdbc, String database, String prefix, UUID writerId) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        this.writerId = Objects.requireNonNull(writerId, "writerId");
        table = ClickHouseIdentifiers.qualified(database, prefix + "identity_reservation");
    }

    long next(String sequence) throws SQLException {
        return next(sequence, 0L);
    }

    long next(String sequence, long floor) throws SQLException {
        Objects.requireNonNull(sequence, "sequence");
        if (floor < 0) {
            throw new IllegalArgumentException("ClickHouse identity floors cannot be negative");
        }
        return sequences.computeIfAbsent(sequence, Sequence::new).next(floor);
    }

    void observe(String sequence, long id) {
        Objects.requireNonNull(sequence, "sequence");
        if (id < 0) {
            throw new IllegalArgumentException("ClickHouse identities cannot be negative");
        }
        sequences.computeIfAbsent(sequence, Sequence::new).observe(id);
    }

    long canonicalId(String identity, CandidateAllocator allocator) throws SQLException {
        Objects.requireNonNull(allocator, "allocator");
        Long claimed = claimedId(identity);
        if (claimed != null) {
            return claimed;
        }
        return claimCanonicalId(identity, allocator.next());
    }

    long canonicalId(String identity, long candidate) throws SQLException {
        Long claimed = claimedId(identity);
        if (claimed != null) {
            return claimed;
        }
        return claimCanonicalId(identity, candidate);
    }

    private long claimCanonicalId(String identity, long candidate) throws SQLException {
        if (candidate < 1) {
            throw new SQLException("ClickHouse canonical identity allocator returned an invalid ID: " + candidate);
        }
        long deadline = reservationDeadline();
        claimCanonical(identity, candidate, deadline);
        Long claimed = claimedId(identity, deadline);
        if (claimed == null) {
            throw new SQLException("ClickHouse canonical identity is not visible after being claimed");
        }
        return claimed;
    }

    Long claimedId(String identity) throws SQLException {
        return claimedId(identity, reservationDeadline());
    }

    private Long claimedId(String identity, long deadline) throws SQLException {
        if (identity == null || identity.isEmpty()) {
            throw new IllegalArgumentException("ClickHouse canonical identities cannot be empty");
        }
        String sql = "SELECT block_start FROM " + table + " WHERE sequence=? ORDER BY " + CLAIM_ORDER + " LIMIT 1";
        try (Connection connection = jdbc.openAuxiliaryConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            setQueryTimeout(statement, deadline, identity);
            statement.setString(1, identity);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getLong(1) : null;
            }
        }
    }

    private Long readTopBlockEnd(String sequence, long deadline) throws SQLException {
        String sql = "SELECT block_start FROM " + table + " WHERE sequence=? ORDER BY block_start DESC LIMIT 1";
        try (Connection connection = jdbc.openAuxiliaryConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            setQueryTimeout(statement, deadline, sequence);
            statement.setString(1, sequence);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return null;
                }
                long blockStart = resultSet.getLong(1);
                if (blockStart < 0 || blockStart > Long.MAX_VALUE - BLOCK_SIZE) {
                    throw new SQLException("ClickHouse identity reservation for " + sequence + " exceeds the supported range");
                }
                return blockStart + BLOCK_SIZE;
            }
        }
    }

    private UUID readBlockOwner(String sequence, long blockStart, long deadline) throws SQLException {
        String sql = "SELECT " + winner("toString(writer_id)") + " FROM " + table + " WHERE sequence=? AND block_start=? GROUP BY block_start";
        try (Connection connection = jdbc.openAuxiliaryConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            setQueryTimeout(statement, deadline, sequence);
            statement.setString(1, sequence);
            statement.setLong(2, blockStart);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new SQLException("ClickHouse identity block " + sequence + "@" + blockStart + " is not visible after being claimed");
                }
                return parseWriterId(sequence, resultSet.getString(1));
            }
        }
    }

    private void claimCanonical(String sequence, long blockStart, long deadline) throws SQLException {
        String sql = "INSERT INTO " + table + " (sequence,block_start,writer_id) VALUES (?,?,?)";
        try (Connection connection = jdbc.openAuxiliaryConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            setQueryTimeout(statement, deadline, sequence);
            statement.setString(1, sequence);
            statement.setLong(2, blockStart);
            statement.setObject(3, writerId);
            statement.execute();
        }
    }

    private static void setQueryTimeout(PreparedStatement statement, long deadline, String sequence) throws SQLException {
        long remaining = deadline - System.nanoTime();
        if (remaining <= 0) {
            throw new SQLException("Timed out reserving ClickHouse identity block " + sequence);
        }
        long seconds = Math.max(1L, TimeUnit.NANOSECONDS.toSeconds(remaining));
        statement.setQueryTimeout((int) Math.min(seconds, Integer.MAX_VALUE));
    }

    private static long reservationDeadline() {
        return System.nanoTime() + RESERVATION_TIMEOUT_NANOS;
    }

    private static String winner(String column) {
        return "argMin(" + column + ",(" + CLAIM_ORDER + "))";
    }

    private static UUID parseWriterId(String sequence, String value) throws SQLException {
        try {
            return UUID.fromString(value);
        }
        catch (IllegalArgumentException | NullPointerException exception) {
            throw new SQLException("ClickHouse identity reservation for " + sequence + " has an invalid writer ID", exception);
        }
    }

    private final class Sequence {

        private final String name;
        private long next;
        private long end;

        private Sequence(String name) {
            this.name = name;
        }

        private synchronized long next(long floor) throws SQLException {
            observe(floor);
            if (next >= end) {
                reserve();
            }
            return next++;
        }

        private synchronized void observe(long id) {
            if (id >= next) {
                if (id == Long.MAX_VALUE) {
                    throw new IllegalStateException("ClickHouse " + name + " identity range is exhausted");
                }
                next = id + 1;
            }
        }

        private void reserve() throws SQLException {
            long deadline = reservationDeadline();
            int contentions = 0;
            Long topEnd = readTopBlockEnd(name, deadline);
            while (true) {
                requireReservationActive(deadline);
                long blockStart = Math.max(topEnd == null ? 0L : topEnd,
                        containingBlockStart(next - 1));
                if (blockStart > Long.MAX_VALUE - BLOCK_SIZE - 1) {
                    throw new SQLException("ClickHouse " + name + " identity range is exhausted");
                }
                claimCanonical(name, blockStart, deadline);
                boolean owned = writerId.equals(readBlockOwner(name, blockStart, deadline));
                requireReservationActive(deadline);
                if (owned) {
                    next = Math.max(next, blockStart + 1);
                    end = blockStart + BLOCK_SIZE + 1;
                    return;
                }
                waitBeforeRetry(deadline, contentions++);
                topEnd = readTopBlockEnd(name, deadline);
            }
        }

        private void waitBeforeRetry(long deadline, int contentions) throws SQLException {
            requireReservationActive(deadline);
            long remaining = deadline - System.nanoTime();
            long maximumDelay = 1L << Math.min(contentions, 3);
            long delay = Math.min(remaining, TimeUnit.MILLISECONDS.toNanos(ThreadLocalRandom.current().nextLong(1, maximumDelay + 1)));
            try {
                TimeUnit.NANOSECONDS.sleep(delay);
            }
            catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new SQLException("Interrupted while reserving ClickHouse identity block " + name, exception);
            }
            requireReservationActive(deadline);
        }

        private void requireReservationActive(long deadline) throws SQLException {
            if (Thread.currentThread().isInterrupted()) {
                throw new SQLException("Interrupted while reserving ClickHouse identity block " + name);
            }
            if (deadline - System.nanoTime() <= 0) {
                throw new SQLException("Timed out reserving ClickHouse identity block " + name);
            }
        }

        private long containingBlockStart(long id) {
            return id - id % BLOCK_SIZE;
        }
    }

    @FunctionalInterface
    interface CandidateAllocator {

        long next() throws SQLException;
    }

}
