package net.coreprotect.database;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public final class DuckDBNativeSupport {

    private static final String NATIVE_CLASS = "org.duckdb.DuckDBNative";

    private DuckDBNativeSupport() {
        throw new IllegalStateException("DuckDBNativeSupport class");
    }

    public static void verifyAvailable() throws Exception {
        Class.forName("org.duckdb.DuckDBDriver");
        try (Connection connection = openDatabase(() -> DriverManager.getConnection("jdbc:duckdb:"))) {
            if (connection == null) {
                throw new IllegalStateException("DuckDB JDBC driver did not create an in-memory connection");
            }
        }
    }

    public static <T> T openDatabase(DatabaseOpener<T> opener) throws SQLException {
        Thread thread = Thread.currentThread();
        String originalName = thread.getName();
        thread.setName("CoreProtect-DuckDB");
        try {
            return opener.open();
        }
        finally {
            thread.setName(originalName);
        }
    }

    @FunctionalInterface
    public interface DatabaseOpener<T> {
        T open() throws SQLException;
    }

    public static boolean isNativeUnavailable(Throwable failure) {
        Throwable cause = failure;
        for (int depth = 0; cause != null && depth < 32; depth++, cause = cause.getCause()) {
            if (cause instanceof UnsatisfiedLinkError) {
                return true;
            }
            String message = cause.getMessage();
            if (cause instanceof FileNotFoundException && message != null && message.startsWith("DuckDB JNI library not found, path:")) {
                return true;
            }
            if (cause instanceof IOException && isNativeLoadFramePresent(cause)) {
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

    private static boolean isNativeLoadFramePresent(Throwable failure) {
        for (StackTraceElement frame : failure.getStackTrace()) {
            if (frame.getClassName().equals(NATIVE_CLASS) && (frame.getMethodName().equals("unpackAndLoad") || frame.getMethodName().equals("loadFromCurrentJarDir"))) {
                return true;
            }
        }
        return false;
    }
}
