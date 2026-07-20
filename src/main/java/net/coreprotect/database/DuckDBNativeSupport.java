package net.coreprotect.database;

import java.io.FileNotFoundException;
import java.sql.Connection;
import java.sql.DriverManager;

public final class DuckDBNativeSupport {

    private static final String NATIVE_CLASS = "org.duckdb.DuckDBNative";

    private DuckDBNativeSupport() {
        throw new IllegalStateException("DuckDBNativeSupport class");
    }

    public static void verifyAvailable() throws Exception {
        Class.forName("org.duckdb.DuckDBDriver");
        try (Connection connection = DriverManager.getConnection("jdbc:duckdb:")) {
            if (connection == null) {
                throw new IllegalStateException("DuckDB JDBC driver did not create an in-memory connection");
            }
        }
    }

    public static boolean isNativeUnavailable(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof UnsatisfiedLinkError) {
                return true;
            }
            String message = cause.getMessage();
            if (cause instanceof FileNotFoundException && message != null && message.startsWith("DuckDB JNI library not found, path:")) {
                return true;
            }
            if (cause instanceof IllegalStateException && message != null && (message.startsWith("Unsupported OS:") || message.startsWith("Unsupported system architecture:"))) {
                return true;
            }
            if (cause instanceof NoClassDefFoundError && message != null && message.contains(NATIVE_CLASS)) {
                return true;
            }
        }
        return false;
    }
}
