package net.coreprotect.command.parser;

import java.util.Locale;

import net.coreprotect.model.lookup.LookupRollbackState;

public final class RollbackStateParser {

    private RollbackStateParser() {
        throw new IllegalStateException("Parser class");
    }

    public static ParseResult parse(String[] inputArguments) {
        boolean rolledBack = false;
        boolean restored = false;

        for (int index = 1; index < inputArguments.length; index++) {
            String argument = normalize(inputArguments[index]);
            if (isRolledBackModifier(argument)) {
                rolledBack = true;
            }
            else if (isRestoredModifier(argument)) {
                restored = true;
            }
        }

        LookupRollbackState state = LookupRollbackState.ANY;
        if (rolledBack) {
            state = LookupRollbackState.ROLLED_BACK;
        }
        else if (restored) {
            state = LookupRollbackState.NOT_ROLLED_BACK;
        }
        return new ParseResult(state, rolledBack && restored);
    }

    public static boolean isModifier(String argument) {
        String normalized = normalize(argument);
        return isRolledBackModifier(normalized) || isRestoredModifier(normalized);
    }

    private static boolean isRolledBackModifier(String argument) {
        return argument.equals("#rolledback") || argument.equals("#rollbacked");
    }

    private static boolean isRestoredModifier(String argument) {
        return argument.equals("#restored");
    }

    private static String normalize(String argument) {
        return argument.trim().toLowerCase(Locale.ROOT).replace("\\", "").replace("'", "");
    }

    public static final class ParseResult {
        private final LookupRollbackState state;
        private final boolean conflicting;

        private ParseResult(LookupRollbackState state, boolean conflicting) {
            this.state = state;
            this.conflicting = conflicting;
        }

        public LookupRollbackState getState() {
            return state;
        }

        public boolean isConflicting() {
            return conflicting;
        }
    }
}
