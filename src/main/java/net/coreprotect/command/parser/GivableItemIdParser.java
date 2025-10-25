package net.coreprotect.command.parser;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GivableItemIdParser {

    protected static final Pattern PATTERN = Pattern.compile("#([0-9]+)");

    public static Integer parseGivableItemId(String[] inputArguments) {
        if (inputArguments.length > 0) {
            Matcher matcher = PATTERN.matcher(inputArguments[0]);
            if (matcher.find()) {
                return Integer.parseInt(matcher.group(1));
            }
        }
        return null;
    }
}
