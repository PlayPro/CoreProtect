package net.coreprotect.consumer.process;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLException;

import org.junit.jupiter.api.Test;

import net.coreprotect.database.DatabaseType;

class ProcessTest {

    private static final String DUPLICATE_UUID = "Constraint Error: Duplicate key \"uuid: 2114a8b0-4978-4191-a5e0-14ca05436e83\" violates unique constraint.";

    @Test
    void discardsDuckDbEntityInteractionBlockedByDuplicateUuid() {
        SQLException failure = new SQLException(DUPLICATE_UUID);

        assertTrue(Process.shouldDiscardFailedEvent(DatabaseType.DUCKDB, Process.ENTITY_INTERACTION, failure));
    }

    @Test
    void findsDuplicateUuidInWrappedSqlFailure() {
        Exception failure = new Exception("Unable to log entity interaction", new SQLException(DUPLICATE_UUID));

        assertTrue(Process.shouldDiscardFailedEvent(DatabaseType.DUCKDB, Process.ENTITY_INTERACTION, failure));
    }

    @Test
    void retainsTransientDuckDbEntityInteractionFailure() {
        SQLException failure = new SQLException("Connection reset while inserting entity identity");

        assertFalse(Process.shouldDiscardFailedEvent(DatabaseType.DUCKDB, Process.ENTITY_INTERACTION, failure));
    }

    @Test
    void retainsDuplicateUuidFailureForOtherDatabase() {
        SQLException failure = new SQLException(DUPLICATE_UUID);

        assertFalse(Process.shouldDiscardFailedEvent(DatabaseType.SQLITE, Process.ENTITY_INTERACTION, failure));
    }

    @Test
    void retainsDuplicateUuidFailureForOtherAction() {
        SQLException failure = new SQLException(DUPLICATE_UUID);

        assertFalse(Process.shouldDiscardFailedEvent(DatabaseType.DUCKDB, Process.ENTITY_SPAWN_LOG, failure));
    }

    @Test
    void retainsDuckDbEntityInteractionFailureForDifferentUniqueKey() {
        SQLException failure = new SQLException("Constraint Error: Duplicate key \"kill_rowid: 42\" violates unique constraint.");

        assertFalse(Process.shouldDiscardFailedEvent(DatabaseType.DUCKDB, Process.ENTITY_INTERACTION, failure));
    }

    @Test
    void stopsAtCyclicExceptionCauses() {
        Exception first = new Exception("first");
        Exception second = new Exception("second");
        first.initCause(second);
        second.initCause(first);

        assertFalse(Process.shouldDiscardFailedEvent(DatabaseType.DUCKDB, Process.ENTITY_INTERACTION, first));
    }
}
