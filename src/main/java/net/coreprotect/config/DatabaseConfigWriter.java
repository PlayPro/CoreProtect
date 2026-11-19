package net.coreprotect.config;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.DosFileAttributeView;
import java.nio.file.attribute.DosFileAttributes;
import java.nio.file.attribute.FileOwnerAttributeView;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFileAttributes;
import java.util.Arrays;
import java.util.Locale;

import net.coreprotect.database.DatabaseType;

public final class DatabaseConfigWriter {

    private DatabaseConfigWriter() {
        throw new IllegalStateException("Utility class");
    }

    public static synchronized void persistDatabaseType(DatabaseType type) throws IOException {
        Path configFile = Paths.get(ConfigHandler.path).resolve(ConfigFile.CONFIG);
        persistDatabaseType(configFile, type);
    }

    static synchronized void persistDatabaseType(Path configFile, DatabaseType type) throws IOException {
        if (!Files.isRegularFile(configFile, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("CoreProtect config is not a regular file: " + configFile);
        }

        byte[] original = Files.readAllBytes(configFile);
        byte[] updated = updateDatabaseType(original, type);
        if (Arrays.equals(original, updated)) {
            return;
        }

        writeAtomically(configFile, updated);
    }

    public static void writeAtomically(Path target, byte[] content) throws IOException {
        Path normalized = target.toAbsolutePath();
        Path directory = normalized.getParent();
        if (directory == null) {
            throw new IOException("Target file has no parent directory: " + target);
        }
        Files.createDirectories(directory);

        Path fileName = normalized.getFileName();
        String prefix = "." + (fileName != null ? fileName.toString() : "coreprotect") + ".";
        Path temporary = Files.createTempFile(directory, prefix, ".tmp");
        boolean moved = false;
        try {
            Files.write(temporary, content, StandardOpenOption.TRUNCATE_EXISTING);
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                channel.force(true);
            }
            if (Files.exists(normalized, LinkOption.NOFOLLOW_LINKS)) {
                copyAttributes(normalized, temporary);
            }
            moveWithFallback(temporary, normalized);
            moved = true;
            forceDirectory(directory);
        }
        finally {
            if (!moved) {
                Files.deleteIfExists(temporary);
            }
        }
    }

    private static void moveWithFallback(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        }
        catch (AtomicMoveNotSupportedException ignored) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static byte[] updateDatabaseType(byte[] data, DatabaseType type) {
        String content = new String(data, StandardCharsets.UTF_8);
        String databaseType = type.name().toLowerCase(Locale.ROOT);
        String lineSeparator = detectLineSeparator(content);
        boolean terminalLineSeparator = content.endsWith("\n") || content.endsWith("\r");
        boolean foundDatabaseType = false;
        StringBuilder updated = new StringBuilder(content.length() + 32);

        int offset = 0;
        while (offset < content.length()) {
            int lineEnd = offset;
            while (lineEnd < content.length() && content.charAt(lineEnd) != '\r' && content.charAt(lineEnd) != '\n') {
                lineEnd++;
            }
            int nextOffset = lineEnd;
            if (nextOffset < content.length()) {
                char separator = content.charAt(nextOffset++);
                if (separator == '\r' && nextOffset < content.length() && content.charAt(nextOffset) == '\n') {
                    nextOffset++;
                }
            }

            String line = content.substring(offset, lineEnd);
            String replaced = replaceScalar(line, "database-type", databaseType);
            if (replaced != null) {
                foundDatabaseType = true;
                updated.append(replaced);
            }
            else {
                replaced = replaceScalar(line, "use-mysql", Boolean.toString(type.isMySQL()));
                updated.append(replaced == null ? line : replaced);
            }
            updated.append(content, lineEnd, nextOffset);
            offset = nextOffset;
        }

        if (!foundDatabaseType) {
            if (updated.length() > 0 && updated.charAt(updated.length() - 1) != '\n' && updated.charAt(updated.length() - 1) != '\r') {
                updated.append(lineSeparator);
            }
            updated.append("database-type: ").append(databaseType);
            if (terminalLineSeparator) {
                updated.append(lineSeparator);
            }
        }
        return updated.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static String replaceScalar(String line, String expectedKey, String value) {
        int first = 0;
        while (first < line.length() && Character.isWhitespace(line.charAt(first))) {
            first++;
        }
        if (first == line.length() || line.charAt(first) == '#') {
            return null;
        }

        int colon = line.indexOf(':', first);
        if (colon < 0 || !line.substring(first, colon).trim().equalsIgnoreCase(expectedKey)) {
            return null;
        }

        int valueStart = colon + 1;
        while (valueStart < line.length() && Character.isWhitespace(line.charAt(valueStart))) {
            valueStart++;
        }
        int comment = findComment(line, valueStart);
        int valueEnd = comment < 0 ? line.length() : comment;
        while (valueEnd > valueStart && Character.isWhitespace(line.charAt(valueEnd - 1))) {
            valueEnd--;
        }

        String replacement = value;
        if (valueStart < valueEnd && (line.charAt(valueStart) == '\'' || line.charAt(valueStart) == '"')) {
            char quote = line.charAt(valueStart);
            replacement = quote + value + quote;
        }
        String whitespace = line.substring(colon + 1, valueStart);
        if (whitespace.isEmpty()) {
            whitespace = " ";
        }
        String suffix = line.substring(valueEnd);
        if (comment == valueStart && suffix.startsWith("#")) {
            suffix = " " + suffix;
        }
        return line.substring(0, colon + 1) + whitespace + replacement + suffix;
    }

    static int findComment(String line, int start) {
        char quote = start < line.length() ? line.charAt(start) : '\0';
        boolean quoted = quote == '\'' || quote == '"';
        for (int index = quoted ? start + 1 : start; index < line.length(); index++) {
            char character = line.charAt(index);
            if (quoted) {
                if (quote == '"' && character == '\\') {
                    index++;
                }
                else if (character == quote) {
                    if (quote == '\'' && index + 1 < line.length() && line.charAt(index + 1) == quote) {
                        index++;
                    }
                    else {
                        quoted = false;
                    }
                }
            }
            else if (character == '#' && (index == start || Character.isWhitespace(line.charAt(index - 1)))) {
                return index;
            }
        }
        return -1;
    }

    private static String detectLineSeparator(String content) {
        int newline = content.indexOf('\n');
        if (newline >= 0) {
            return newline > 0 && content.charAt(newline - 1) == '\r' ? "\r\n" : "\n";
        }
        return content.indexOf('\r') >= 0 ? "\r" : System.lineSeparator();
    }

    private static void copyAttributes(Path source, Path target) throws IOException {
        boolean ownerCopied = false;
        PosixFileAttributeView sourcePosix = Files.getFileAttributeView(source, PosixFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
        PosixFileAttributeView targetPosix = Files.getFileAttributeView(target, PosixFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
        if (sourcePosix != null && targetPosix != null) {
            PosixFileAttributes attributes = sourcePosix.readAttributes();
            try {
                targetPosix.setOwner(attributes.owner());
                ownerCopied = true;
            }
            catch (FileSystemException | UnsupportedOperationException ignored) {
            }
            try {
                targetPosix.setGroup(attributes.group());
            }
            catch (FileSystemException | UnsupportedOperationException ignored) {
            }
            targetPosix.setPermissions(attributes.permissions());
        }

        AclFileAttributeView sourceAcl = Files.getFileAttributeView(source, AclFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
        AclFileAttributeView targetAcl = Files.getFileAttributeView(target, AclFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
        if (sourceAcl != null && targetAcl != null) {
            try {
                targetAcl.setOwner(sourceAcl.getOwner());
                ownerCopied = true;
            }
            catch (FileSystemException | UnsupportedOperationException ignored) {
            }
            try {
                targetAcl.setAcl(sourceAcl.getAcl());
            }
            catch (FileSystemException | UnsupportedOperationException ignored) {
            }
        }

        if (!ownerCopied) {
            FileOwnerAttributeView sourceOwner = Files.getFileAttributeView(source, FileOwnerAttributeView.class, LinkOption.NOFOLLOW_LINKS);
            FileOwnerAttributeView targetOwner = Files.getFileAttributeView(target, FileOwnerAttributeView.class, LinkOption.NOFOLLOW_LINKS);
            if (sourceOwner != null && targetOwner != null) {
                try {
                    targetOwner.setOwner(sourceOwner.getOwner());
                }
                catch (FileSystemException | UnsupportedOperationException ignored) {
                }
            }
        }

        DosFileAttributeView sourceDos = Files.getFileAttributeView(source, DosFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
        DosFileAttributeView targetDos = Files.getFileAttributeView(target, DosFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
        if (sourceDos != null && targetDos != null) {
            DosFileAttributes attributes = sourceDos.readAttributes();
            targetDos.setArchive(attributes.isArchive());
            targetDos.setHidden(attributes.isHidden());
            targetDos.setSystem(attributes.isSystem());
            targetDos.setReadOnly(attributes.isReadOnly());
        }
    }

    private static void forceDirectory(Path directory) {
        try (FileChannel channel = FileChannel.open(directory, StandardOpenOption.READ)) {
            channel.force(true);
        }
        catch (IOException | UnsupportedOperationException ignored) {
        }
    }
}
