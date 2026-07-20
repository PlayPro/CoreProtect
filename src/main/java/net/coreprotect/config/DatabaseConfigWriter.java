package net.coreprotect.config;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
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
        if (!Files.isRegularFile(configFile, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("CoreProtect config is not a regular file: " + configFile);
        }

        byte[] original = Files.readAllBytes(configFile);
        byte[] updated = updateDatabaseType(original, type);
        if (Arrays.equals(original, updated)) {
            return;
        }

        Path directory = configFile.toAbsolutePath().getParent();
        if (directory == null) {
            throw new IOException("CoreProtect config has no parent directory: " + configFile);
        }

        Path temporary = Files.createTempFile(directory, ".config.yml.", ".tmp");
        boolean moved = false;
        try {
            Files.write(temporary, updated, StandardOpenOption.TRUNCATE_EXISTING);
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                channel.force(true);
            }
            copyAttributes(configFile, temporary);
            try {
                Files.move(temporary, configFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            }
            catch (AtomicMoveNotSupportedException exception) {
                throw new IOException("The CoreProtect config filesystem does not support atomic replacement", exception);
            }
            moved = true;
            forceDirectory(directory);
        }
        finally {
            if (!moved) {
                Files.deleteIfExists(temporary);
            }
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

    private static int findComment(String line, int start) {
        boolean singleQuoted = false;
        boolean doubleQuoted = false;
        boolean escaped = false;
        for (int index = start; index < line.length(); index++) {
            char character = line.charAt(index);
            if (doubleQuoted && character == '\\' && !escaped) {
                escaped = true;
                continue;
            }
            if (character == '"' && !singleQuoted && !escaped) {
                doubleQuoted = !doubleQuoted;
            }
            else if (character == '\'' && !doubleQuoted) {
                singleQuoted = !singleQuoted;
            }
            else if (character == '#' && !singleQuoted && !doubleQuoted && (index == start || Character.isWhitespace(line.charAt(index - 1)))) {
                return index;
            }
            escaped = false;
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
            targetPosix.setOwner(attributes.owner());
            targetPosix.setGroup(attributes.group());
            targetPosix.setPermissions(attributes.permissions());
            ownerCopied = true;
        }

        AclFileAttributeView sourceAcl = Files.getFileAttributeView(source, AclFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
        AclFileAttributeView targetAcl = Files.getFileAttributeView(target, AclFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
        if (sourceAcl != null && targetAcl != null) {
            targetAcl.setOwner(sourceAcl.getOwner());
            targetAcl.setAcl(sourceAcl.getAcl());
            ownerCopied = true;
        }

        if (!ownerCopied) {
            FileOwnerAttributeView sourceOwner = Files.getFileAttributeView(source, FileOwnerAttributeView.class, LinkOption.NOFOLLOW_LINKS);
            FileOwnerAttributeView targetOwner = Files.getFileAttributeView(target, FileOwnerAttributeView.class, LinkOption.NOFOLLOW_LINKS);
            if (sourceOwner != null && targetOwner != null) {
                targetOwner.setOwner(sourceOwner.getOwner());
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
