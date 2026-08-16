package net.coreprotect.database.clickhouse;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import net.coreprotect.config.Config;
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.database.ConsumerWriteBatch.ReferenceKind;

public final class ClickHouseDatabase implements AutoCloseable {

    private static final int MINIMUM_SERVER_MAJOR = 25;
    private static final int MINIMUM_SERVER_MINOR = 6;

    private final ClickHouseJdbc jdbc;
    private final String database;
    private final String prefix;
    private final UUID datasetId;
    private final ClickHouseIdentityAllocator identityAllocator;
    private final ClickHouseIdentityReservation identifierReservation;
    private final ClickHouseNativeClient nativeClient;
    private final ClickHouseBatchPublisher publisher;
    private final ClickHouseHighWaterPublisher highWaterPublisher;
    private final ClickHouseRetention retention;
    private final ClickHouseTargetResolver targetResolver;
    private final ClickHouseWriterRegistration writerRegistration;
    private boolean closed;

    private ClickHouseDatabase(ClickHouseJdbc jdbc, ClickHouseNativeClient nativeClient, String database, String prefix, UUID datasetId,
            ClickHouseIdentityAllocator identityAllocator, ClickHouseIdentityReservation identifierReservation, ClickHouseWriterRegistration writerRegistration) {
        this.jdbc = jdbc;
        this.nativeClient = nativeClient;
        this.database = database;
        this.prefix = prefix;
        this.datasetId = datasetId;
        this.identityAllocator = identityAllocator;
        this.identifierReservation = identifierReservation;
        this.writerRegistration = writerRegistration;
        publisher = new ClickHouseBatchPublisher(jdbc, nativeClient, writerRegistration, database, prefix);
        highWaterPublisher = new ClickHouseHighWaterPublisher(jdbc, nativeClient, writerRegistration, database, prefix);
        retention = new ClickHouseRetention(jdbc, database, prefix);
        targetResolver = new ClickHouseTargetResolver(jdbc, database, prefix, datasetId);
    }

    public static ClickHouseDatabase initialize(ClickHouseJdbcConfig config, String prefix) throws SQLException {
        return initialize(config, prefix, Paths.get(ConfigHandler.path));
    }

    public static ClickHouseDatabase initialize(ClickHouseJdbcConfig config, String prefix, Path controlDirectory) throws SQLException {
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(controlDirectory, "controlDirectory");
        String validatedPrefix = validatePrefix(prefix);

        ClickHouseJdbc jdbc = new ClickHouseJdbc(config);
        ClickHouseNativeClient nativeClient = null;
        ClickHouseWriterRegistration writerRegistration = null;
        try {
            writerRegistration = new ClickHouseWriterRegistration(controlDirectory);
            writerRegistration.acquire();
            UUID writerId = UUID.randomUUID();
            nativeClient = new ClickHouseNativeClient(config);
            List<String> statements = ClickHouseSchema.createStatements(config.getDatabase(), validatedPrefix);
            ClickHouseStorageIdentity storageIdentity;
            ClickHouseHighWaterMarks remoteMarks;
            try (Connection connection = jdbc.openConnection()) {
                requireServerVersion(connection);
                requirePersistentDatabaseIdentity(connection, config.getDatabase());
                for (int index = 0; index < ClickHouseSchema.PHYSICAL_TABLE_COUNT; index++) {
                    jdbc.executeDdl(connection, statements.get(index));
                }
                ClickHouseSchema.validatePhysicalSchema(connection, config.getDatabase(), validatedPrefix);
                storageIdentity = loadStorageIdentity(connection, config.getDatabase(), validatedPrefix);
                for (int index = ClickHouseSchema.PHYSICAL_TABLE_COUNT; index < statements.size(); index++) {
                    jdbc.executeDdl(connection, statements.get(index));
                }
                remoteMarks = ClickHouseStartupReconciler.readRemote(connection, config.getDatabase(), validatedPrefix);
            }
            ClickHouseIdentityReservation reservation = new ClickHouseIdentityReservation(jdbc, config.getDatabase(), validatedPrefix, writerId);
            ClickHouseIdentityAllocator identityAllocator = new ClickHouseIdentityAllocator(storageIdentity.getDatasetId(), reservation, remoteMarks);
            return new ClickHouseDatabase(jdbc, nativeClient, config.getDatabase(), validatedPrefix, storageIdentity.getDatasetId(), identityAllocator, reservation,
                    writerRegistration);
        }
        catch (SQLException | RuntimeException exception) {
            try {
                if (writerRegistration != null) {
                    writerRegistration.close();
                }
            }
            catch (SQLException closeException) {
                exception.addSuppressed(closeException);
            }
            finally {
                try {
                    if (nativeClient != null) {
                        nativeClient.close();
                    }
                }
                finally {
                    jdbc.close();
                }
            }
            throw exception;
        }
    }

    static ClickHouseStorageIdentity loadStorageIdentity(Connection connection, String database, String prefix) throws SQLException {
        ClickHouseStorageIdentity storageIdentity = ClickHouseStorageIdentity.load(connection, database, prefix);
        if (storageIdentity != null) {
            return storageIdentity;
        }
        try {
            ClickHouseSchema.requireUnownedTablesEmpty(connection, database, prefix);
        }
        catch (SQLException unownedTables) {
            storageIdentity = ClickHouseStorageIdentity.load(connection, database, prefix);
            if (storageIdentity == null) {
                throw unownedTables;
            }
            return storageIdentity;
        }
        return ClickHouseStorageIdentity.loadOrCreate(connection, database, prefix);
    }

    public Connection openConnection() throws SQLException {
        ensureOpen();
        return jdbc.openConnection();
    }

    Connection openAuxiliaryConnection() throws SQLException {
        ensureOpen();
        return jdbc.openAuxiliaryConnection();
    }

    public ClickHouseWriteBatch newWriteBatch() throws SQLException {
        ensureOpen();
        return new ClickHouseWriteBatch(identityAllocator.nextBatchIdentity(), identityAllocator);
    }

    public ClickHouseBatchReceipt publish(ClickHouseWriteBatch batch) throws SQLException {
        ensureOpen();
        return publisher.publish(batch);
    }

    public int nextIdentifierId(ReferenceKind kind, String value, int currentMaximum) throws SQLException {
        ensureOpen();
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(value, "value");
        if (currentMaximum < 0) {
            throw new IllegalArgumentException("ClickHouse identifier maximums cannot be negative");
        }
        String family = ClickHouseConsumerWriteBatch.referenceFamily(kind).getTableName();
        String identity = "canonical:" + family + ":" + value;
        long id = identifierReservation.canonicalId(identity, () -> identifierReservation.next(family, currentMaximum));
        return toIdentifier(id, family);
    }

    public String findIdentifierValue(ReferenceKind kind, int id) throws SQLException {
        ensureOpen();
        Objects.requireNonNull(kind, "kind");
        if (id < 0) {
            throw new IllegalArgumentException("ClickHouse identifiers cannot be negative");
        }
        ClickHouseFamily family = ClickHouseConsumerWriteBatch.referenceFamily(kind);
        String column = ClickHouseConsumerWriteBatch.referenceValueColumn(kind);
        String table = ClickHouseIdentifiers.qualified(database, prefix + family.getTableName());
        String sql = "SELECT id,`" + column + "` FROM " + table + " WHERE id=? GROUP BY id,`" + column + "` LIMIT 2";
        try (Connection connection = jdbc.openAuxiliaryConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, id);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return null;
                }
                String value = resultSet.getString(2);
                if (value == null) {
                    throw new SQLException("ClickHouse " + family.getTableName() + " identifier " + id + " has no value");
                }
                if (resultSet.next()) {
                    throw new SQLException("ClickHouse " + family.getTableName() + " identifier " + id + " resolves to multiple values");
                }
                return value;
            }
        }
    }

    public int findIdentifierId(ReferenceKind kind, String value) throws SQLException {
        ensureOpen();
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(value, "value");
        ClickHouseFamily family = ClickHouseConsumerWriteBatch.referenceFamily(kind);
        String column = ClickHouseConsumerWriteBatch.referenceValueColumn(kind);
        String table = ClickHouseIdentifiers.qualified(database, prefix + family.getTableName());
        String sql = "SELECT id FROM " + table + " WHERE `" + column + "`=? AND id IS NOT NULL GROUP BY id LIMIT 2";
        try (Connection connection = jdbc.openAuxiliaryConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, value);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return -1;
                }
                int id = toIdentifier(resultSet.getLong(1), family.getTableName());
                if (resultSet.next()) {
                    throw new SQLException("ClickHouse " + family.getTableName() + " value resolves to multiple identifiers: " + value);
                }
                return id;
            }
        }
    }

    int canonicalUserId(String user, String uuid, Integer existingId) throws SQLException {
        ensureOpen();
        String normalizedUser = Objects.requireNonNull(user, "user").toLowerCase(Locale.ROOT);
        String normalizedUuid = Objects.requireNonNull(uuid, "uuid");
        if (Config.getGlobal().DATABASE_LOCK) {
            long id = existingId != null ? existingId : identityAllocator.nextRowId(ClickHouseFamily.USER);
            return toIdentifier(id, ClickHouseFamily.USER.getTableName());
        }
        String nameIdentity = "canonical:user:name:" + normalizedUser;
        if (normalizedUuid.isEmpty()) {
            if (existingId != null) {
                return existingId;
            }
            return toIdentifier(identifierReservation.canonicalId(nameIdentity,
                    () -> identityAllocator.nextRowId(ClickHouseFamily.USER)),
                    ClickHouseFamily.USER.getTableName());
        }

        String uuidIdentity = "canonical:user:uuid:" + normalizedUuid;
        long uuidId = identifierReservation.canonicalId(uuidIdentity,
                () -> existingId != null
                        ? existingId
                        : identifierReservation.canonicalId(nameIdentity,
                                () -> identityAllocator.nextRowId(ClickHouseFamily.USER)));
        identifierReservation.canonicalId(nameIdentity, uuidId);
        return toIdentifier(uuidId, ClickHouseFamily.USER.getTableName());
    }

    private static int toIdentifier(long id, String family) throws SQLException {
        if (id < 1 || id > Integer.MAX_VALUE) {
            throw new SQLException("ClickHouse " + family + " identifier is outside CoreProtect's positive signed 32-bit range: " + id);
        }
        return (int) id;
    }

    public void preserveCompatibilityHighWaterMarks(Map<ClickHouseFamily, Long> highWaterMarks) throws SQLException {
        ensureOpen();
        Objects.requireNonNull(highWaterMarks, "highWaterMarks");
        EnumMap<ClickHouseFamily, Long> marks = new EnumMap<>(ClickHouseFamily.class);
        for (Map.Entry<ClickHouseFamily, Long> entry : highWaterMarks.entrySet()) {
            ClickHouseFamily family = Objects.requireNonNull(entry.getKey(), "event family");
            long rowId = Objects.requireNonNull(entry.getValue(), "compatibility row ID");
            identityAllocator.observeRowId(family, rowId);
            marks.merge(family, rowId, Math::max);
        }
        if (!marks.isEmpty()) {
            highWaterPublisher.publish(identityAllocator.nextBatchIdentity(), marks);
        }
    }

    public Map<Long, ClickHouseEventPointer> resolveEventPointers(ClickHouseFamily family, List<Long> rowIds) throws SQLException {
        ensureOpen();
        return targetResolver.resolve(family, rowIds);
    }

    Map<Long, ClickHouseEventPointer> resolveAvailableBlockTargets(List<Long> rowIds, Map<Long, Integer> actions) throws SQLException {
        ensureOpen();
        return targetResolver.resolveAvailableBlocks(rowIds, actions);
    }

    public long purge(long startTime, long endTime, int worldId, List<Integer> blockTypes, boolean optimize) throws SQLException {
        ensureOpen();
        writerRegistration.verifyOwned();
        return retention.purge(startTime, endTime, worldId, blockTypes, optimize);
    }

    public void cancelPurge() {
        retention.cancelPurge();
    }

    UUID getDatasetId() {
        return datasetId;
    }

    public void ensureCoreData(String currentVersion) throws SQLException {
        synchronizeCoreData(currentVersion, false);
    }

    public void updateCoreVersion(String currentVersion) throws SQLException {
        synchronizeCoreData(currentVersion, true);
    }

    private void synchronizeCoreData(String currentVersion, boolean updateVersion) throws SQLException {
        ensureOpen();
        String requiredVersion = requireCoreVersion(currentVersion);
        String versionTable = ClickHouseIdentifiers.qualified(database, prefix + ClickHouseFamily.VERSION.getTableName());
        String databaseLockTable = ClickHouseIdentifiers.qualified(database, prefix + ClickHouseFamily.DATABASE_LOCK.getTableName());
        CoreData coreData = readCoreData(versionTable, databaseLockTable);
        Long versionRowId = coreData.versionRowId;
        String storedVersion = coreData.version;
        boolean databaseLockExists = coreData.databaseLockExists;

        boolean versionSynchronized = versionRowId != null && (!updateVersion || requiredVersion.equals(storedVersion));
        if (versionSynchronized && databaseLockExists) {
            return;
        }

        int time = (int) (System.currentTimeMillis() / 1000L);
        try (ClickHouseWriteBatch batch = newWriteBatch()) {
            if (versionRowId == null) {
                batch.events().addVersion(time, requiredVersion);
            }
            else if (updateVersion && !requiredVersion.equals(storedVersion)) {
                batch.events().addVersionRevision(versionRowId, time, requiredVersion);
            }
            if (!databaseLockExists) {
                batch.events().addDatabaseLockVersion(1L, time, 0);
            }
            publish(batch);
        }
    }

    private CoreData readCoreData(String versionTable, String databaseLockTable) throws SQLException {
        Long versionRowId = null;
        String storedVersion = null;
        boolean databaseLockExists = false;
        try (Connection connection = jdbc.openConnection();
                PreparedStatement versionStatement = connection.prepareStatement("SELECT rowid,version FROM " + versionTable + " ORDER BY rowid LIMIT 2")) {
            try (ResultSet resultSet = versionStatement.executeQuery()) {
                if (resultSet.next()) {
                    versionRowId = resultSet.getLong(1);
                    storedVersion = resultSet.getString(2);
                    if (versionRowId != 1L || resultSet.next()) {
                        throw new SQLException("ClickHouse version data must contain only row ID 1");
                    }
                }
            }

            try (PreparedStatement lockStatement = connection.prepareStatement("SELECT rowid FROM " + databaseLockTable + " GROUP BY rowid")) {
                try (ResultSet resultSet = lockStatement.executeQuery()) {
                    while (resultSet.next()) {
                        long rowId = resultSet.getLong(1);
                        if (rowId != 1L) {
                            throw new SQLException("ClickHouse database lock data contains an unsupported row ID: " + rowId);
                        }
                        databaseLockExists = true;
                    }
                }
            }
        }
        return new CoreData(versionRowId, storedVersion, databaseLockExists);
    }

    @Override
    public synchronized void close() throws SQLException {
        if (closed) {
            return;
        }
        closed = true;
        try {
            writerRegistration.close();
        }
        finally {
            try {
                nativeClient.close();
            }
            finally {
                jdbc.close();
            }
        }
    }

    private synchronized void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("ClickHouse database is closed");
        }
    }

    private static final class CoreData {

        private final Long versionRowId;
        private final String version;
        private final boolean databaseLockExists;

        private CoreData(Long versionRowId, String version, boolean databaseLockExists) {
            this.versionRowId = versionRowId;
            this.version = version;
            this.databaseLockExists = databaseLockExists;
        }
    }

    private static String requireCoreVersion(String version) {
        if (version == null || !version.matches("\\d+\\.\\d+\\.\\d+")) {
            throw new IllegalArgumentException("ClickHouse requires a three-part CoreProtect internal database version");
        }
        return version;
    }

    private static void requireServerVersion(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT version()"); ResultSet resultSet = statement.executeQuery()) {
            if (!resultSet.next()) {
                throw new SQLException("ClickHouse did not return its server version");
            }
            String version = resultSet.getString(1);
            String[] parts = version == null ? new String[0] : version.split("\\.", 3);
            int major;
            int minor;
            try {
                major = parts.length > 0 ? Integer.parseInt(parts[0]) : -1;
                minor = parts.length > 1 ? Integer.parseInt(parts[1]) : -1;
            }
            catch (NumberFormatException exception) {
                throw new SQLException("Unsupported ClickHouse server version: " + version, exception);
            }
            if (major < MINIMUM_SERVER_MAJOR || (major == MINIMUM_SERVER_MAJOR && minor < MINIMUM_SERVER_MINOR)) {
                throw new SQLException("ClickHouse " + MINIMUM_SERVER_MAJOR + "." + MINIMUM_SERVER_MINOR + " or newer is required; found " + version);
            }
        }
    }

    private static void requirePersistentDatabaseIdentity(Connection connection, String database) throws SQLException {
        String sql = "SELECT engine,toString(uuid) FROM system.databases WHERE name=? LIMIT 2";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, database);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new SQLException("ClickHouse database " + database + " does not exist");
                }
                String engine = resultSet.getString(1);
                UUID databaseUuid;
                try {
                    databaseUuid = UUID.fromString(resultSet.getString(2));
                }
                catch (IllegalArgumentException | NullPointerException exception) {
                    throw new SQLException("ClickHouse database " + database + " did not return a valid persistent UUID", exception);
                }
                if (resultSet.next()) {
                    throw new SQLException("ClickHouse database " + database + " is ambiguous");
                }
                if (databaseUuid.getMostSignificantBits() == 0L && databaseUuid.getLeastSignificantBits() == 0L) {
                    throw new SQLException("ClickHouse database " + database + " uses engine " + engine
                            + ", but CoreProtect requires a database engine with persistent UUID-backed table identities (for example, Atomic)");
                }
            }
        }
    }

    private static String validatePrefix(String prefix) {
        if (prefix == null) {
            throw new IllegalArgumentException("ClickHouse table prefix cannot be null");
        }
        if (!prefix.isEmpty()) {
            ClickHouseIdentifiers.requireIdentifier(prefix, "ClickHouse table prefix");
        }
        return prefix;
    }

}
