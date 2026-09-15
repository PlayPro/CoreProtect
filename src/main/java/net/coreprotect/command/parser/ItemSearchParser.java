package net.coreprotect.command.parser;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ItemSearchParser {
    private ItemSearchParser() {}

    public static Map<String, String> parse(String[] arguments) {
        Map<String, String> values = new LinkedHashMap<>();
        for (int index = 1; index < arguments.length; index++) {
            String term = arguments[index];
            int colon = term.indexOf(':');
            if (colon <= 0) throw new IllegalArgumentException("Use key:value parameters.");
            String key = term.substring(0, colon).toLowerCase(Locale.ROOT);
            if (!List.of("i", "name", "exact", "enchant", "model", "data", "source", "t", "u", "contents", "page").contains(key)) {
                throw new IllegalArgumentException("Unknown search parameter: " + key);
            }
            String value = term.substring(colon + 1);
            if (value.startsWith("\"")) {
                while (!value.endsWith("\"") || value.length() == 1) {
                    if (++index >= arguments.length) throw new IllegalArgumentException("Unclosed quoted value.");
                    value += " " + arguments[index];
                }
                value = value.substring(1, value.length() - 1);
            }
            if (value.isEmpty() || values.putIfAbsent(key, value) != null) throw new IllegalArgumentException("Empty or duplicate parameter: " + key);
        }
        if (!List.of("online", "history").contains(values.getOrDefault("source", "history"))) throw new IllegalArgumentException("source: must be online or history.");
        if (!List.of("true", "false").contains(values.getOrDefault("contents", "true"))) throw new IllegalArgumentException("contents: must be true or false.");
        int page = Integer.parseInt(values.getOrDefault("page", "1"));
        if (page < 1 || page > 1000) throw new IllegalArgumentException("page: must be between 1 and 1000.");
        return values;
    }
}
