package net.coreprotect.command.parser;

import java.util.Locale;

public final class ParserUtils {

    private ParserUtils() {
        throw new IllegalStateException("Utility class");
    }

    public static String stripQuotesAndBackslashes(String argument) {
        if (argument == null) {
            return "";
        }
        return argument.trim().replace("\\", "").replace("'", "");
    }

    public static String normalize(String argument) {
        return stripQuotesAndBackslashes(argument).toLowerCase(Locale.ROOT);
    }

    public static boolean equalsAny(String argument, String... candidates) {
        for (String candidate : candidates) {
            if (argument.equals(candidate)) {
                return true;
            }
        }
        return false;
    }

    public static String stripPrefix(String argument, String normalizedArgument, String... prefixes) {
        for (String prefix : prefixes) {
            if (normalizedArgument.startsWith(prefix)) {
                return argument.substring(prefix.length());
            }
        }
        return null;
    }

    public static String stripPrefix(String normalizedArgument, String... prefixes) {
        return stripPrefix(normalizedArgument, normalizedArgument, prefixes);
    }

    public static boolean isSignedDecimal(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        int start = (value.charAt(0) == '-') ? 1 : 0;
        return isUnsignedDecimalRange(value, start);
    }

    public static boolean isUnsignedDecimal(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        return isUnsignedDecimalRange(value, 0);
    }

    private static boolean isUnsignedDecimalRange(String value, int start) {
        if (start >= value.length()) {
            return false;
        }
        boolean hasDigit = false;
        boolean hasDot = false;
        for (int i = start; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c >= '0' && c <= '9') {
                hasDigit = true;
            }
            else if (c == '.' && !hasDot) {
                hasDot = true;
            }
            else {
                return false;
            }
        }
        return hasDigit;
    }
}
