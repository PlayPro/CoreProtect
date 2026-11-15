package net.coreprotect.command.parser;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.coreprotect.language.Phrase;
import net.coreprotect.language.Selector;

/**
 * Parser for time-related command arguments
 */
public class TimeParser {

    private static final String[] TIME_PREFIXES = {
            "time:",
            "t:"
    };

    private static final Pattern DURATION_TOKEN = Pattern.compile("([0-9]*\\.?[0-9]+)(mo|[ywdhms])");

    private enum Unit {
        YEARS("y", 31536000L, Phrase.TIME_YEARS),
        MONTHS("mo", 2592000L, Phrase.TIME_MONTHS),
        WEEKS("w", 604800L, Phrase.TIME_WEEKS),
        DAYS("d", 86400L, Phrase.TIME_DAYS),
        HOURS("h", 3600L, Phrase.TIME_HOURS),
        MINUTES("m", 60L, Phrase.TIME_MINUTES),
        SECONDS("s", 1L, Phrase.TIME_SECONDS);

        private final String suffix;
        private final long seconds;
        private final Phrase phrase;

        Unit(String suffix, long seconds, Phrase phrase) {
            this.suffix = suffix;
            this.seconds = seconds;
            this.phrase = phrase;
        }

        static Unit fromSuffix(String suffix) {
            for (Unit unit : values()) {
                if (unit.suffix.equals(suffix)) {
                    return unit;
                }
            }
            return null;
        }
    }

    private static final class UnitEntry {
        private final Unit unit;
        private final BigDecimal amount;

        private UnitEntry(Unit unit, BigDecimal amount) {
            this.unit = unit;
            this.amount = amount;
        }
    }

    private static final class DurationSide {
        private final Set<Unit> seen = EnumSet.noneOf(Unit.class);
        private final List<UnitEntry> entries = new ArrayList<>();

        void merge(List<UnitEntry> parsedEntries) {
            for (UnitEntry entry : parsedEntries) {
                if (seen.add(entry.unit)) {
                    entries.add(entry);
                }
            }
        }

        long toSeconds() {
            double total = 0.0;
            for (UnitEntry entry : entries) {
                total += entry.amount.doubleValue() * entry.unit.seconds;
            }
            return (long) total;
        }

        void appendFormatted(StringBuilder builder, boolean compactRange) {
            for (UnitEntry entry : entries) {
                if (builder.length() > 0) {
                    builder.append(' ');
                }
                String formattedAmount = timeString(entry.amount);
                if (compactRange) {
                    builder.append(formattedAmount).append(entry.unit.suffix);
                }
                else {
                    String selector = (entry.amount.compareTo(BigDecimal.ONE) == 0) ? Selector.FIRST : Selector.SECOND;
                    builder.append(Phrase.build(entry.unit.phrase, formattedAmount, selector));
                }
            }
        }

        boolean isEmpty() {
            return entries.isEmpty();
        }
    }

    private static final class ParsedTime {
        private final DurationSide first = new DurationSide();
        private final DurationSide second = new DurationSide();
        private boolean range;
    }

    /**
     * Parse time from command arguments
     * 
     * @param inputArguments
     *            The command arguments
     * @return An array of two longs - [time1, time2]
     */
    public static long[] parseTime(String[] inputArguments) {
        ParsedTime parsed = parseDurationArguments(inputArguments);
        long timeStart = parsed.first.toSeconds();
        long timeEnd = parsed.range ? parsed.second.toSeconds() : 0L;

        if (timeEnd >= timeStart) {
            return new long[] { timeEnd, timeStart };
        }
        return new long[] { timeStart, timeEnd };
    }

    /**
     * Parse time string from command arguments for display
     * 
     * @param inputArguments
     *            The command arguments
     * @return A formatted time string
     */
    public static String parseTimeString(String[] inputArguments) {
        ParsedTime parsed = parseDurationArguments(inputArguments);
        if (parsed.first.isEmpty()) {
            return "";
        }

        StringBuilder builder = new StringBuilder();
        parsed.first.appendFormatted(builder, parsed.range);
        if (parsed.range && !parsed.second.isEmpty()) {
            builder.append(" -");
            parsed.second.appendFormatted(builder, true);
        }
        return builder.toString();
    }

    private static ParsedTime parseDurationArguments(String[] inputArguments) {
        ParsedTime parsed = new ParsedTime();
        int next = 0;

        for (int index = 1; index < inputArguments.length; index++) {
            String argument = ParserUtils.normalize(inputArguments[index]);
            if (ParserUtils.equalsAny(argument, TIME_PREFIXES)) {
                next = 1;
                continue;
            }

            String stripped = ParserUtils.stripPrefix(argument, TIME_PREFIXES);
            if (next == 1 || stripped != null) {
                String spec = (stripped != null) ? stripped : argument;
                next = 0;

                if (spec.isEmpty() || spec.startsWith("-") || spec.endsWith("-")) {
                    continue;
                }

                int dashIndex = spec.indexOf('-');
                if (dashIndex >= 0) {
                    if (spec.indexOf('-', dashIndex + 1) >= 0) {
                        continue;
                    }
                    List<UnitEntry> startEntries = parseSide(spec.substring(0, dashIndex));
                    List<UnitEntry> endEntries = parseSide(spec.substring(dashIndex + 1));
                    if (startEntries != null && endEntries != null) {
                        parsed.first.merge(startEntries);
                        parsed.second.merge(endEntries);
                        parsed.range = true;
                    }
                }
                else {
                    List<UnitEntry> entries = parseSide(spec);
                    if (entries != null) {
                        if (parsed.range) {
                            parsed.second.merge(entries);
                        }
                        else {
                            parsed.first.merge(entries);
                        }
                    }
                }
            }
            else {
                next = 0;
            }
        }

        return parsed;
    }

    private static List<UnitEntry> parseSide(String spec) {
        if (spec.isEmpty()) {
            return null;
        }
        List<UnitEntry> entries = new ArrayList<>();
        Matcher matcher = DURATION_TOKEN.matcher(spec);
        int position = 0;
        while (position < spec.length()) {
            if (spec.charAt(position) == ',') {
                position++;
                continue;
            }
            matcher.region(position, spec.length());
            if (!matcher.lookingAt()) {
                return null;
            }
            String numberText = matcher.group(1);
            if (!ParserUtils.isUnsignedDecimal(numberText)) {
                return null;
            }
            Unit unit = Unit.fromSuffix(matcher.group(2));
            if (unit == null) {
                return null;
            }
            entries.add(new UnitEntry(unit, new BigDecimal(numberText)));
            position = matcher.end();
        }
        return entries.isEmpty() ? null : entries;
    }

    /**
     * Parse rows from command arguments
     * 
     * @param inputArguments
     *            The command arguments
     * @return The number of rows
     */
    public static int parseRows(String[] inputArguments) {
        int rows = 0;
        int next = 0;

        for (int index = 1; index < inputArguments.length; index++) {
            String argument = ParserUtils.normalize(inputArguments[index]);
            if (argument.equals("rows:")) {
                next = 1;
                continue;
            }

            String stripped = ParserUtils.stripPrefix(argument, "rows:");
            if (next == 1 || stripped != null) {
                String rowSpec = ((stripped != null) ? stripped : argument).trim();
                next = 0;
                if (isPositiveRowCount(rowSpec)) {
                    rows = Integer.parseInt(rowSpec);
                }
            }
            else {
                next = 0;
            }
        }

        return rows;
    }

    private static boolean isPositiveRowCount(String value) {
        if (value.isEmpty() || value.length() >= 10) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
        }
        return true;
    }

    private static String timeString(BigDecimal input) {
        return input.stripTrailingZeros().toPlainString();
    }
}
