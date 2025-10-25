package net.coreprotect.command.parser;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GivableItemIdParser {

    protected static final Pattern PATTERN = Pattern.compile("#([0-9]+)");

    public static Integer parseGivableItemId(String[] inputArguments) {
        for (String argument : inputArguments) {
            Matcher matcher = PATTERN.matcher(argument);
            if (matcher.find()) {
                return Integer.parseInt(matcher.group(1));
            }
        }
        return null;
    }
}
