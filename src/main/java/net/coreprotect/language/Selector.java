package net.coreprotect.language;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import net.coreprotect.utility.Color;

public class Selector {

    final public static String FIRST = "\u0000{1}";
    final public static String SECOND = "\u0000{2}";
    final public static String THIRD = "\u0000{3}";
    final public static String FOURTH = "\u0000{4}";

    final protected static Set<String> SELECTORS = new HashSet<>(Arrays.asList(Selector.FIRST, Selector.SECOND, Selector.THIRD, Selector.FOURTH));

    private Selector() {
        throw new IllegalStateException("Utility class");
    }

    protected static String processSelection(String output, String param, String color) {
        if (output == null || output.isEmpty()) {
            return output;
        }

        int selector = getSelectorIndex(param);
        if (selector < 0) {
            return output;
        }

        int searchFrom = 0;
        while (searchFrom < output.length()) {
            int openIndex = output.indexOf('{', searchFrom);
            if (openIndex == -1) {
                break;
            }
            int closeIndex = output.indexOf('}', openIndex + 1);
            if (closeIndex == -1) {
                break;
            }
            String inside = output.substring(openIndex + 1, closeIndex);
            int nestedOpen = inside.indexOf('{');
            if (nestedOpen != -1) {
                searchFrom = openIndex + 1 + nestedOpen;
                continue;
            }
            if (inside.contains("|")) {
                String[] options = inside.split("\\|", -1);
                String selected = selector < options.length ? options[selector] : options[0];
                String replacement = (color != null && !color.isEmpty()) ? color + selected + Color.WHITE : selected;
                return new StringBuilder(output.length() + replacement.length())
                        .append(output, 0, openIndex)
                        .append(replacement)
                        .append(output, closeIndex + 1, output.length())
                        .toString();
            }
            searchFrom = closeIndex + 1;
        }

        return output;
    }

    private static int getSelectorIndex(String param) {
        if (FIRST.equals(param) || "{1}".equals(param)) {
            return 0;
        }
        if (SECOND.equals(param) || "{2}".equals(param)) {
            return 1;
        }
        if (THIRD.equals(param) || "{3}".equals(param)) {
            return 2;
        }
        if (FOURTH.equals(param) || "{4}".equals(param)) {
            return 3;
        }
        return -1;
    }

}
