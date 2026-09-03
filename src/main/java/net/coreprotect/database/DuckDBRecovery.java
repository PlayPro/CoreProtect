package net.coreprotect.database;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

import net.coreprotect.config.ConfigHandler;
import net.coreprotect.consumer.Consumer;
import net.coreprotect.language.Phrase;
import net.coreprotect.utility.Chat;
import net.coreprotect.utility.Color;
import net.coreprotect.utility.ErrorReporter;

public final class DuckDBRecovery {

    private static final int MAXIMUM_RECOVERY_ATTEMPTS = 3;
    private static final long CONNECTION_DRAIN_TIMEOUT_MILLIS = 60_000L;
    private static final long SHUTDOWN_POLL_INTERVAL_MILLIS = 250L;
    private static final long RETRY_DELAY_NANOS = 5_000_000_000L;
    private static final Object STATE_LOCK = new Object();

    private static State state = State.HEALTHY;
    private static int recoveryAttempts;
    private static long nextAttemptNanos;

    private DuckDBRecovery() {
        throw new IllegalStateException("Database class");
    }

    public static boolean request(Throwable failure) {
        if (!ConfigHandler.databaseType.isDuckDB() || !requiresRestart(failure)) {
            return false;
        }

        boolean reportFailure = false;
        boolean recoveryExhausted = false;
        synchronized (STATE_LOCK) {
            if (state == State.HALTED) {
                return true;
            }
            if (state == State.HEALTHY) {
                reportFailure = true;
                if (recoveryAttempts >= MAXIMUM_RECOVERY_ATTEMPTS) {
                    state = State.HALTED;
                    recoveryExhausted = true;
                }
                else {
                    state = State.REQUESTED;
                    nextAttemptNanos = 0L;
                }
            }
        }

        ConfigHandler.databaseReachable = false;
        Consumer.requireDatabaseReload();
        if (reportFailure) {
            ErrorReporter.report(failure);
        }
        if (recoveryExhausted) {
            reportRecoveryFailure();
        }
        else if (reportFailure) {
            Chat.sendConsoleMessage(Color.YELLOW + "[CoreProtect] " + Phrase.build(Phrase.DATABASE_RECOVERY_STARTED));
        }
        if (isHalted()) {
            Consumer.haltPersistence();
        }
        return true;
    }

    public static boolean isPending() {
        synchronized (STATE_LOCK) {
            return state == State.REQUESTED || state == State.RECOVERING;
        }
    }

    public static void recoverIfRequested() {
        synchronized (STATE_LOCK) {
            if (state != State.REQUESTED || System.nanoTime() < nextAttemptNanos) {
                return;
            }
            state = State.RECOVERING;
            recoveryAttempts++;
        }

        Consumer.OperationStartResult startResult = Consumer.beginDatabaseRecovery();
        if (startResult != Consumer.OperationStartResult.STARTED) {
            if (startResult == Consumer.OperationStartResult.PERSISTENCE_HALTED
                    || startResult == Consumer.OperationStartResult.INTERRUPTED) {
                halt();
            }
            else {
                defer();
            }
            return;
        }

        boolean recovered = false;
        boolean connectionsDrained = false;
        Throwable failure = null;
        long deadline = System.nanoTime() + CONNECTION_DRAIN_TIMEOUT_MILLIS * 1_000_000L;
        CompletableFuture<Void> shutdownSignal = Consumer.databaseReloadShutdownSignal();
        try {
            if (!lockDatabaseReload(deadline, shutdownSignal)) {
                if (shutdownSignal.isDone()) {
                    halt();
                    return;
                }
                throw new SQLException("Timed out waiting for DuckDB database recovery lock");
            }
            if (!awaitConnectionDrain(deadline, shutdownSignal)) {
                if (shutdownSignal.isDone()) {
                    halt();
                    return;
                }
                throw new SQLException("Timed out waiting for active DuckDB connections to close");
            }
            connectionsDrained = true;
            if (shutdownSignal.isDone()) {
                halt();
                return;
            }
            ConfigHandler.loadDatabase();
            if (shutdownSignal.isDone()) {
                halt();
                return;
            }
            validateConnection();
            if (shutdownSignal.isDone()) {
                halt();
                return;
            }
            synchronized (STATE_LOCK) {
                state = State.HEALTHY;
                nextAttemptNanos = 0L;
            }
            ConfigHandler.databaseReachable = true;
            recovered = true;
        }
        catch (Exception exception) {
            failure = exception;
            ConfigHandler.databaseReachable = false;
            if (connectionsDrained) {
                Database.closeConnection();
            }
        }
        finally {
            Consumer.endDatabaseReload(recovered);
        }

        if (recovered) {
            Chat.sendConsoleMessage(Color.GREEN + "[CoreProtect] " + Phrase.build(Phrase.DATABASE_RECOVERY_SUCCESS));
            return;
        }
        if (isHalted()) {
            return;
        }

        if (failure != null) {
            ErrorReporter.report(failure);
        }
        synchronized (STATE_LOCK) {
            if (recoveryAttempts >= MAXIMUM_RECOVERY_ATTEMPTS) {
                state = State.HALTED;
            }
            else {
                state = State.REQUESTED;
                nextAttemptNanos = System.nanoTime() + RETRY_DELAY_NANOS;
            }
        }
        if (isHalted()) {
            reportRecoveryFailure();
            Consumer.haltPersistence();
        }
    }

    public static void markHealthy() {
        synchronized (STATE_LOCK) {
            if (state == State.HEALTHY) {
                recoveryAttempts = 0;
                nextAttemptNanos = 0L;
            }
        }
    }

    public static void reset() {
        synchronized (STATE_LOCK) {
            state = State.HEALTHY;
            recoveryAttempts = 0;
            nextAttemptNanos = 0L;
        }
    }

    static State state() {
        synchronized (STATE_LOCK) {
            return state;
        }
    }

    static int recoveryAttempts() {
        synchronized (STATE_LOCK) {
            return recoveryAttempts;
        }
    }

    static boolean requiresRestart(Throwable failure) {
        Throwable current = failure;
        for (int depth = 0; current != null && depth < 16; depth++) {
            String message = current.getMessage();
            if (current instanceof SQLException && message != null) {
                String normalized = message.toLowerCase(Locale.ROOT);
                if (normalized.contains("database has been invalidated")
                        || normalized.contains("database must be restarted")
                        || normalized.contains("failed to rollback transaction")
                        || normalized.contains("out of memory error")) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }

    private static void validateConnection() throws Exception {
        try (Connection connection = Database.getConnection(true, 0)) {
            if (connection == null) {
                throw new SQLException("Unable to open DuckDB after database recovery");
            }
            try (Statement statement = connection.createStatement(); ResultSet resultSet = statement.executeQuery("SELECT 1")) {
                if (!resultSet.next() || resultSet.getInt(1) != 1) {
                    throw new SQLException("DuckDB recovery health check failed");
                }
            }
        }
    }

    private static long remainingMillis(long deadline) {
        return Math.max(0L, (deadline - System.nanoTime()) / 1_000_000L);
    }

    private static boolean lockDatabaseReload(long deadline, CompletableFuture<Void> shutdownSignal) throws InterruptedException {
        while (!shutdownSignal.isDone()) {
            long remaining = remainingMillis(deadline);
            if (remaining == 0L) {
                return false;
            }
            if (Consumer.lockDatabaseReload(Math.min(remaining, SHUTDOWN_POLL_INTERVAL_MILLIS))) {
                return true;
            }
        }
        return false;
    }

    private static boolean awaitConnectionDrain(long deadline, CompletableFuture<Void> shutdownSignal) throws InterruptedException {
        while (!shutdownSignal.isDone()) {
            long remaining = remainingMillis(deadline);
            if (remaining == 0L) {
                return false;
            }
            if (Database.awaitConnectionDrain(Math.min(remaining, SHUTDOWN_POLL_INTERVAL_MILLIS))) {
                return true;
            }
        }
        return false;
    }

    private static void defer() {
        synchronized (STATE_LOCK) {
            state = State.REQUESTED;
            recoveryAttempts--;
            nextAttemptNanos = System.nanoTime() + RETRY_DELAY_NANOS;
        }
    }

    private static void halt() {
        synchronized (STATE_LOCK) {
            state = State.HALTED;
        }
        Consumer.haltPersistence();
    }

    private static void reportRecoveryFailure() {
        Chat.sendConsoleMessage(Color.RED + "[CoreProtect] "
                + Phrase.build(Phrase.DATABASE_RECOVERY_FAILED, Integer.toString(MAXIMUM_RECOVERY_ATTEMPTS)));
    }

    private static boolean isHalted() {
        synchronized (STATE_LOCK) {
            return state == State.HALTED;
        }
    }

    enum State {
        HEALTHY,
        REQUESTED,
        RECOVERING,
        HALTED
    }
}
