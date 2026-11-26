package net.coreprotect.command.parser;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GivableItemIdParser {

    protected static final Pattern PATTERN = Pattern.compile("#([0-9]+)");

    public static Integer parseGivableItemId(String[] inputArguments) {
        for (int index = 1; index < inputArguments.length; index++) {
            String argument = ParserUtils.normalize(inputArguments[index]);
            Matcher matcher = PATTERN.matcher(argument);
            if (matcher.matches()) {
                try {
                    return Integer.parseInt(matcher.group(1));
                }
                catch (NumberFormatException e) {
                    return null;
                }
            }
        }
        return null;
    }
}
