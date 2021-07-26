package net.coreprotect.language;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import net.coreprotect.utility.Color;

public class Selector {

    final public static String FIRST = "{1}";
    final public static String SECOND = "{2}";
    final public static String THIRD = "{3}";
    final public static String FOURTH = "{4}";

    final protected static Set<String> SELECTORS = new HashSet<>(Arrays.asList(Selector.FIRST, Selector.SECOND, Selector.THIRD, Selector.FOURTH));

    private Selector() {
        throw new IllegalStateException("Utility class");
    }

    protected static String processSelection(String output, String param, String color) {
        String substring = output;
        try {
            substring = substring.substring(substring.indexOf("{") + 1);
            substring = substring.substring(0, substring.indexOf("}"));
        }
        catch (Exception e) {
            substring = "";
        }

        if (substring.contains("|")) {
            int selector = Integer.parseInt(param.substring(1, 2)) - 1;
            int index = substring(substring, "|", selector);
            if (index == -1) {
                param = substring.substring(0, substring.indexOf("|"));
            }
            else {
                param = substring.substring(index + 1, (substring.lastIndexOf("|") > index ? substring(substring, "|", selector + 1) : substring.length()));
            }

            output = output.replace("{" + substring + "}", color + param + (color.length() > 0 ? Color.WHITE : color));
        }

        return output;
    }

    private static int substring(String string, String search, int index) {
        int result = string.indexOf("|");
        if (result == -1 || index == 0) {
            return -1;
        }

        for (int i = 1; i < index; i++) {
            result = string.indexOf("|", result + 1);
            if (result == -1) {
                return -1;
            }
        }

        return result;
    }

}
