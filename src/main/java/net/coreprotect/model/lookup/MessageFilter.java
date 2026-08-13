package net.coreprotect.model.lookup;

import java.util.Objects;

/**
 * Syntax of a single message filter value, as supplied by the "f:&lt;filter&gt;" lookup parameter.
 */
public final class MessageFilter {

    /** Marks a filter as excluded, e.g. "f:-/co". */
    public static final String EXCLUDE = "-";

    /** Matches any number of characters within a filter, e.g. "f:*ban*". */
    public static final String WILDCARD = "*";

    private MessageFilter() {
        throw new IllegalStateException("Model class");
    }

    public static boolean isExcluded(String filter) {
        return Objects.toString(filter, "").startsWith(EXCLUDE);
    }

    public static String getTerm(String filter) {
        String value = Objects.toString(filter, "");
        return isExcluded(value) ? value.substring(EXCLUDE.length()) : value;
    }
}
