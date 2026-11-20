package net.coreprotect.patch.script;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.StringJoiner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class MySqlColumnPatch {

    private static final Pattern COLUMN = Pattern.compile("(?i)^([`\"]?[a-z0-9_]+[`\"]?)\\s+((?:var)?char\\(\\d+\\)|(?:tiny|medium|long)?(?:text|blob))(?=\\s|$)");
    private static final Pattern DEFAULT_LITERAL = Pattern.compile("(?i)DEFAULT\\s+(?:[a-z0-9_]+)?(?=')");
    private static final Pattern HEX_VALUE = Pattern.compile("[0-9A-Fa-f]*");
    private static final String NO_BACKSLASH_ESCAPES = "NO_BACKSLASH_ESCAPES";

    @FunctionalInterface
    private interface SqlModeRestore extends AutoCloseable {
        @Override
        void close() throws SQLException;
    }

    private MySqlColumnPatch() {
    }

    static String type(Statement statement, String table, String column) throws SQLException {
        try (ResultSet rows = statement.executeQuery("SHOW COLUMNS FROM " + table + " LIKE '" + column + "'")) {
            if (rows.next()) {
                String type = rows.getString("Type");
                if (type != null) {
                    return type.strip().toLowerCase(Locale.ROOT);
                }
            }
        }
        throw new SQLException("Missing MySQL column type for " + table + "." + column);
    }

    static void widenBlob(Statement statement, String table, String column) throws SQLException {
        String type = type(statement, table, column);
        if (type.equals("mediumblob") || type.equals("longblob")) {
            return;
        }
        if (!type.equals("tinyblob") && !type.equals("blob")) {
            throw new SQLException("Unsupported MySQL " + table + "." + column + " type: " + type);
        }
        widen(statement, table, column, type, "MEDIUMBLOB");
    }

    static void widen(Statement statement, String table, String column, String sourceType, String targetType) throws SQLException {
        widen(statement, table, Map.of(column, sourceType), targetType);
    }

    static void widen(Statement statement, String table, Map<String, String> sourceTypes, String targetType) throws SQLException {
        if (sourceTypes.isEmpty()) {
            return;
        }

        List<String> modes = sqlModes(statement);
        if (!modes.contains(NO_BACKSLASH_ESCAPES)) {
            widenWithBackslashEscapes(statement, table, sourceTypes, targetType);
            return;
        }

        // MariaDB uses backslash escapes in SHOW CREATE TABLE even when this flag is set.
        List<String> replayModes = new ArrayList<>(modes);
        replayModes.remove(NO_BACKSLASH_ESCAPES);
        try (PreparedStatement setMode = statement.getConnection().prepareStatement("SET SESSION sql_mode = ?")) {
            setSqlMode(setMode, replayModes);
            try (SqlModeRestore ignored = () -> setSqlMode(setMode, modes)) {
                widenWithBackslashEscapes(statement, table, sourceTypes, targetType);
            }
        }
    }

    private static void setSqlMode(PreparedStatement statement, List<String> modes) throws SQLException {
        statement.setString(1, String.join(",", modes));
        statement.execute();
    }

    private static void widenWithBackslashEscapes(Statement statement, String table, Map<String, String> sourceTypes, String targetType) throws SQLException {
        String ddl = tableDefinition(statement, table);
        int opening = ddl.indexOf('(');
        if (opening < 0) {
            throw new SQLException("Missing MySQL table definition for " + table);
        }

        StringJoiner changes = new StringJoiner(", ");
        int count = 0;
        int depth = 0;
        int start = opening + 1;
        for (int index = start; index < ddl.length(); index++) {
            char character = ddl.charAt(index);
            if (isQuote(character)) {
                index = endQuote(ddl, index);
            }
            else if (ddl.startsWith("/*", index)) {
                index = endComment(ddl, index);
            }
            else if (depth == 0 && (character == ',' || character == ')')) {
                if (addModification(statement, changes, ddl.substring(start, index).strip(), sourceTypes, targetType)) {
                    count++;
                }
                if (character == ')') {
                    if (count != sourceTypes.size()) {
                        break;
                    }
                    statement.executeUpdate("ALTER TABLE " + table + " " + changes);
                    return;
                }
                start = index + 1;
            }
            else if (character == '(') {
                depth++;
            }
            else if (character == ')') {
                depth--;
            }
        }
        throw new SQLException("Could not read MySQL column definitions for " + table + ": " + sourceTypes.keySet());
    }

    private static boolean addModification(Statement statement, StringJoiner changes, String definition, Map<String, String> sourceTypes, String targetType) throws SQLException {
        Matcher column = COLUMN.matcher(definition);
        if (!column.find()) {
            return false;
        }
        String name = column.group(1).replace("`", "").replace("\"", "");
        String sourceType = sourceTypes.get(name.toLowerCase(Locale.ROOT));
        if (sourceType == null) {
            return false;
        }
        if (!sourceType.equalsIgnoreCase(column.group(2))) {
            throw new SQLException("MySQL column type changed while reading its definition: " + name);
        }
        String attributes = definition.substring(column.end());
        if (targetType.equals("TEXT") || targetType.equals("MEDIUMBLOB")) {
            attributes = expressionDefault(statement, attributes, targetType.equals("TEXT"));
        }
        changes.add("MODIFY `" + name + "` " + targetType + attributes);
        return true;
    }

    private static String expressionDefault(Statement statement, String attributes, boolean text) throws SQLException {
        Matcher literal = DEFAULT_LITERAL.matcher(attributes);
        int depth = 0;
        for (int index = 0; index < attributes.length(); index++) {
            char character = attributes.charAt(index);
            if (isQuote(character)) {
                index = endQuote(attributes, index);
            }
            else if (attributes.startsWith("/*", index)) {
                index = endComment(attributes, index);
            }
            else if (character == '(') {
                depth++;
            }
            else if (character == ')') {
                depth--;
            }
            else if (depth == 0 && (character == 'D' || character == 'd') && index > 0 && Character.isWhitespace(attributes.charAt(index - 1))
                    && literal.region(index, attributes.length()).lookingAt()) {
                int start = index + "DEFAULT".length();
                while (Character.isWhitespace(attributes.charAt(start))) {
                    start++;
                }
                int end = endQuote(attributes, literal.end()) + 1;
                String value = attributes.substring(start, end);
                if (attributes.indexOf('\'', literal.end() + 1) < end - 1 || value.indexOf('\\') >= 0 || value.chars().anyMatch(Character::isISOControl)) {
                    value = modeNeutralLiteral(statement, value, text);
                }
                return attributes.substring(0, start) + "(" + value + ")" + attributes.substring(end);
            }
        }
        return attributes;
    }

    private static String modeNeutralLiteral(Statement statement, String literal, boolean text) throws SQLException {
        String value = text ? "CONVERT(" + literal + " USING utf8mb4)" : literal;
        try (ResultSet rows = statement.executeQuery("SELECT HEX(" + value + ")")) {
            if (rows.next()) {
                String hex = rows.getString(1);
                if (hex != null && hex.length() % 2 == 0 && HEX_VALUE.matcher(hex).matches()) {
                    String binary = "X'" + hex + "'";
                    return text ? "CONVERT(" + binary + " USING utf8mb4)" : binary;
                }
            }
        }
        throw new SQLException("Could not encode MySQL column default");
    }

    private static boolean isQuote(char character) {
        return character == '\'' || character == '"' || character == '`';
    }

    private static int endQuote(String sql, int start) throws SQLException {
        char quote = sql.charAt(start);
        for (int index = start + 1; index < sql.length(); index++) {
            char character = sql.charAt(index);
            if (character == '\\' && quote == '\'') {
                index++;
            }
            else if (character == quote) {
                if (index + 1 >= sql.length() || sql.charAt(index + 1) != quote) {
                    return index;
                }
                index++;
            }
        }
        throw new SQLException("Unterminated quote in MySQL table definition");
    }

    private static int endComment(String sql, int start) throws SQLException {
        int end = sql.indexOf("*/", start + 2);
        if (end < 0) {
            throw new SQLException("Unterminated comment in MySQL table definition");
        }
        return end + 1;
    }

    private static List<String> sqlModes(Statement statement) throws SQLException {
        try (ResultSet rows = statement.executeQuery("SELECT @@SESSION.sql_mode, VERSION()")) {
            if (rows.next()) {
                String mode = rows.getString(1);
                String version = rows.getString(2);
                if (mode != null && version != null) {
                    List<String> modes = Arrays.asList(mode.split(","));
                    boolean mariadb = version.toLowerCase(Locale.ROOT).contains("mariadb");
                    if (modes.contains("NO_FIELD_OPTIONS") || (mariadb && modes.stream()
                            .anyMatch(List.of("ANSI", "DB2", "POSTGRESQL", "MSSQL", "MAXDB", "ORACLE")::contains))) {
                        throw new SQLException("The MySQL session SQL mode hides column attributes from SHOW CREATE TABLE: " + mode);
                    }
                    return modes;
                }
            }
        }
        throw new SQLException("Could not read MySQL session SQL mode");
    }

    private static String tableDefinition(Statement statement, String table) throws SQLException {
        try (ResultSet rows = statement.executeQuery("SHOW CREATE TABLE " + table)) {
            if (rows.next()) {
                String definition = rows.getString(2);
                if (definition != null) {
                    return definition;
                }
            }
        }
        throw new SQLException("Could not read MySQL table definition for " + table);
    }
}
