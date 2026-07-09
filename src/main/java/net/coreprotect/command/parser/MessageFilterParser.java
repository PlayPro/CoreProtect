package net.coreprotect.command.parser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

public final class MessageFilterParser {
    public static final int MINIMUM_FILTER_CODE_POINTS = 3;

    private static final List<String> VALUE_KEYS = List.of(
            "a:",
            "action:",
            "b:",
            "block:",
            "blocks:",
            "c:",
            "coord:",
            "coords:",
            "cord:",
            "cords:",
            "coordinate:",
            "coordinates:",
            "cordinate:",
            "cordinates:",
            "e:",
            "exclude:",
            "f:",
            "filter:",
            "i:",
            "include:",
            "item:",
            "items:",
            "location:",
            "p:",
            "page:",
            "position:",
            "r:",
            "radius:",
            "rows:",
            "t:",
            "time:",
            "u:",
            "user:",
            "users:"
    );

    private MessageFilterParser() {
        throw new IllegalStateException("Parser class");
    }

    public static ParseResult parse(String[] inputArguments) {
        if (inputArguments == null || inputArguments.length == 0) {
            return new ParseResult(new String[0], Collections.emptyList(), false);
        }

        List<String> arguments = new ArrayList<>(inputArguments.length);
        Set<String> filters = new LinkedHashSet<>();
        boolean specified = false;

        for (int index = 0; index < inputArguments.length; index++) {
            String argument = Objects.toString(inputArguments[index], "").trim();
            if (!isFilterParameter(argument)) {
                arguments.add(argument);
                continue;
            }

            specified = true;
            StringBuilder collected = new StringBuilder(argument);
            while (index + 1 < inputArguments.length && !isLookupTerminator(inputArguments[index + 1])) {
                String next = Objects.toString(inputArguments[++index], "").trim();
                if (!next.isEmpty()) {
                    collected.append(' ').append(next);
                }
            }

            String merged = collected.toString();
            arguments.add(merged);
            addFilters(filters, value(merged));
        }

        return new ParseResult(arguments.toArray(new String[0]), new ArrayList<>(filters), specified);
    }

    public static boolean isLookupTerminator(String raw) {
        String normalized = normalize(raw);
        if (isControl(normalized)) {
            return true;
        }
        for (String key : VALUE_KEYS) {
            if (normalized.startsWith(key)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isFilterParameter(String raw) {
        String normalized = normalize(raw);
        return normalized.equals("f:") || normalized.startsWith("f:") || normalized.equals("filter:") || normalized.startsWith("filter:");
    }

    private static boolean isControl(String normalized) {
        return normalized.equals("#count") || normalized.equals("#sum") || normalized.equals("count") || normalized.equals("sum")
                || normalized.equals("n") || normalized.equals("noisy") || normalized.equals("v") || normalized.equals("verbose")
                || normalized.equals("#v") || normalized.equals("#verbose") || normalized.equals("#silent");
    }

    private static String normalize(String raw) {
        return Objects.toString(raw, "").trim().toLowerCase(Locale.ROOT).replace("\\", "").replace("'", "");
    }

    private static String value(String argument) {
        int separator = argument.indexOf(':');
        return separator < 0 ? "" : argument.substring(separator + 1);
    }

    private static void addFilters(Set<String> filters, String value) {
        for (String part : value.split(",", -1)) {
            String filter = part.trim();
            if (!filter.isEmpty()) {
                filters.add(filter);
            }
        }
    }

    public static final class ParseResult {
        private final String[] arguments;
        private final List<String> filters;
        private final boolean specified;

        private ParseResult(String[] arguments, List<String> filters, boolean specified) {
            this.arguments = arguments;
            this.filters = Collections.unmodifiableList(filters);
            this.specified = specified;
        }

        public String[] getArguments() {
            return arguments.clone();
        }

        public List<String> getFilters() {
            return filters;
        }

        public boolean isSpecified() {
            return specified;
        }

        public boolean hasInvalidLength() {
            if (filters.isEmpty()) {
                return true;
            }
            for (String filter : filters) {
                if (filter.codePointCount(0, filter.length()) < MINIMUM_FILTER_CODE_POINTS) {
                    return true;
                }
            }
            return false;
        }
    }
}
