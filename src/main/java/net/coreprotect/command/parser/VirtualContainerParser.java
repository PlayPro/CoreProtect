package net.coreprotect.command.parser;

import java.util.Locale;

import net.coreprotect.model.lookup.VirtualContainerTarget;
import net.coreprotect.utility.VirtualContainerUtils;

/**
 * Parser for the container: lookup parameter, which selects a virtual container type or a single container
 */
public final class VirtualContainerParser {

    private static final String PARAMETER = "container:";

    private VirtualContainerParser() {
        throw new IllegalStateException("Parser class");
    }

    public static ParseResult parse(String[] inputArguments) {
        int count = 0;
        String value = null;
        boolean next = false;
        for (int index = 1; index < inputArguments.length; index++) {
            String argument = normalize(inputArguments[index]);
            if (next) {
                value = argument;
                next = false;
            }
            else if (argument.startsWith(PARAMETER)) {
                count++;
                value = argument.substring(PARAMETER.length());
                next = value.isEmpty();
            }
        }

        // a second container: would silently narrow the lookup to the last one
        VirtualContainerTarget target = count == 1 ? parseTarget(value) : null;
        return new ParseResult(count > 0, target);
    }

    public static boolean isParameter(String argument) {
        return normalize(argument).startsWith(PARAMETER);
    }

    private static VirtualContainerTarget parseTarget(String value) {
        if (value == null) {
            return null;
        }

        int separator = value.indexOf('/');
        if (separator < 0) {
            return VirtualContainerUtils.isValidType(value) ? new VirtualContainerTarget(value, null) : null;
        }

        String type = value.substring(0, separator);
        String id = value.substring(separator + 1);
        return VirtualContainerUtils.getKey(type, id) != null ? new VirtualContainerTarget(type, id) : null;
    }

    private static String normalize(String argument) {
        return argument.trim().toLowerCase(Locale.ROOT).replace("\\", "").replace("'", "");
    }

    public static final class ParseResult {
        private final boolean specified;
        private final VirtualContainerTarget target;

        private ParseResult(boolean specified, VirtualContainerTarget target) {
            this.specified = specified;
            this.target = target;
        }

        public boolean isSpecified() {
            return specified;
        }

        /**
         * @return the target, or null when container: is missing, repeated, or not a valid type or key
         */
        public VirtualContainerTarget getTarget() {
            return target;
        }
    }
}
