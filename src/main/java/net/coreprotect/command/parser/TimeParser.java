package net.coreprotect.command.parser;

import net.coreprotect.language.Phrase;
import net.coreprotect.language.Selector;

import java.math.BigDecimal;
import java.util.Locale;

/**
 * Parser for time-related command arguments
 */
public class TimeParser {

    /**
     * Parse time from command arguments
     * 
     * @param inputArguments
     *            The command arguments
     * @return An array of two longs - [time1, time2]
     */
    public static long[] parseTime(String[] inputArguments) {
        String[] argumentArray = inputArguments.clone();
        long timeStart = 0;
        long timeEnd = 0;
        int count = 0;
        int next = 0;
        boolean range = false;
        double w = 0;
        double d = 0;
        double h = 0;
        double m = 0;
        double s = 0;
        for (String argument : argumentArray) {
            if (count > 0) {
                argument = argument.trim().toLowerCase(Locale.ROOT);
                argument = argument.replaceAll("\\\\", "");
                argument = argument.replaceAll("'", "");

                if (argument.equals("t:") || argument.equals("time:")) {
                    next = 1;
                }
                else if (next == 1 || argument.startsWith("t:") || argument.startsWith("time:")) {
                    // time arguments
                    argument = argument.replaceAll("time:", "");
                    argument = argument.replaceAll("t:", "");
                    argument = argument.replaceAll("y", "y:");
                    argument = argument.replaceAll("m", "m:");
                    argument = argument.replaceAll("w", "w:");
                    argument = argument.replaceAll("d", "d:");
                    argument = argument.replaceAll("h", "h:");
                    argument = argument.replaceAll("s", "s:");
                    range = argument.contains("-");

                    int argCount = 0;
                    String[] i2 = argument.split(":");
                    for (String i3 : i2) {
                        if (range && argCount > 0 && timeStart == 0 && i3.startsWith("-")) {
                            timeStart = (long) (((w * 7 * 24 * 60 * 60) + (d * 24 * 60 * 60) + (h * 60 * 60) + (m * 60) + s));
                            w = 0;
                            d = 0;
                            h = 0;
                            m = 0;
                            s = 0;
                        }

                        if (i3.endsWith("w") && w == 0) {
                            String i4 = i3.replaceAll("[^0-9.]", "");
                            if (i4.length() > 0 && i4.replaceAll("[^0-9]", "").length() > 0 && i4.indexOf('.') == i4.lastIndexOf('.')) {
                                w = Double.parseDouble(i4);
                            }
                        }
                        else if (i3.endsWith("d") && d == 0) {
                            String i4 = i3.replaceAll("[^0-9.]", "");
                            if (i4.length() > 0 && i4.replaceAll("[^0-9]", "").length() > 0 && i4.indexOf('.') == i4.lastIndexOf('.')) {
                                d = Double.parseDouble(i4);
                            }
                        }
                        else if (i3.endsWith("h") && h == 0) {
                            String i4 = i3.replaceAll("[^0-9.]", "");
                            if (i4.length() > 0 && i4.replaceAll("[^0-9]", "").length() > 0 && i4.indexOf('.') == i4.lastIndexOf('.')) {
                                h = Double.parseDouble(i4);
                            }
                        }
                        else if (i3.endsWith("m") && m == 0) {
                            String i4 = i3.replaceAll("[^0-9.]", "");
                            if (i4.length() > 0 && i4.replaceAll("[^0-9]", "").length() > 0 && i4.indexOf('.') == i4.lastIndexOf('.')) {
                                m = Double.parseDouble(i4);
                            }
                        }
                        else if (i3.endsWith("s") && s == 0) {
                            String i4 = i3.replaceAll("[^0-9.]", "");
                            if (i4.length() > 0 && i4.replaceAll("[^0-9]", "").length() > 0 && i4.indexOf('.') == i4.lastIndexOf('.')) {
                                s = Double.parseDouble(i4);
                            }
                        }

                        argCount++;
                    }
                    if (timeStart > 0) {
                        timeEnd = (long) (((w * 7 * 24 * 60 * 60) + (d * 24 * 60 * 60) + (h * 60 * 60) + (m * 60) + s));
                    }
                    else {
                        timeStart = (long) (((w * 7 * 24 * 60 * 60) + (d * 24 * 60 * 60) + (h * 60 * 60) + (m * 60) + s));
                    }
                    next = 0;
                }
                else {
                    next = 0;
                }
            }
            count++;
        }

        if (timeEnd >= timeStart) {
            return new long[] { timeEnd, timeStart };
        }
        else {
            return new long[] { timeStart, timeEnd };
        }
    }

    /**
     * Parse time string from command arguments for display
     * 
     * @param inputArguments
     *            The command arguments
     * @return A formatted time string
     */
    public static String parseTimeString(String[] inputArguments) {
        String[] argumentArray = inputArguments.clone();
        String time = "";
        int count = 0;
        int next = 0;
        boolean range = false;
        BigDecimal w = new BigDecimal(0);
        BigDecimal d = new BigDecimal(0);
        BigDecimal h = new BigDecimal(0);
        BigDecimal m = new BigDecimal(0);
        BigDecimal s = new BigDecimal(0);
        for (String argument : argumentArray) {
            if (count > 0) {
                argument = argument.trim().toLowerCase(Locale.ROOT);
                argument = argument.replaceAll("\\\\", "");
                argument = argument.replaceAll("'", "");

                if (argument.equals("t:") || argument.equals("time:")) {
                    next = 1;
                }
                else if (next == 1 || argument.startsWith("t:") || argument.startsWith("time:")) {
                    // time arguments
                    argument = argument.replaceAll("time:", "");
                    argument = argument.replaceAll("t:", "");
                    argument = argument.replaceAll("y", "y:");
                    argument = argument.replaceAll("m", "m:");
                    argument = argument.replaceAll("w", "w:");
                    argument = argument.replaceAll("d", "d:");
                    argument = argument.replaceAll("h", "h:");
                    argument = argument.replaceAll("s", "s:");
                    range = argument.contains("-");

                    int argCount = 0;
                    String[] i2 = argument.split(":");
                    for (String i3 : i2) {
                        if (range && argCount > 0 && !time.contains("-") && i3.startsWith("-")) {
                            w = new BigDecimal(0);
                            d = new BigDecimal(0);
                            h = new BigDecimal(0);
                            m = new BigDecimal(0);
                            s = new BigDecimal(0);
                            time = time + " -";
                        }

                        if (i3.endsWith("w") && w.intValue() == 0) {
                            String i4 = i3.replaceAll("[^0-9.]", "");
                            if (i4.length() > 0 && i4.replaceAll("[^0-9]", "").length() > 0 && i4.indexOf('.') == i4.lastIndexOf('.')) {
                                w = new BigDecimal(i4);
                                if (range) {
                                    time = time + " " + timeString(w) + "w";
                                }
                                else {
                                    time = time + " " + Phrase.build(Phrase.TIME_WEEKS, timeString(w), (w.doubleValue() == 1 ? Selector.FIRST : Selector.SECOND));
                                }
                            }
                        }
                        else if (i3.endsWith("d") && d.intValue() == 0) {
                            String i4 = i3.replaceAll("[^0-9.]", "");
                            if (i4.length() > 0 && i4.replaceAll("[^0-9]", "").length() > 0 && i4.indexOf('.') == i4.lastIndexOf('.')) {
                                d = new BigDecimal(i4);
                                if (range) {
                                    time = time + " " + timeString(d) + "d";
                                }
                                else {
                                    time = time + " " + Phrase.build(Phrase.TIME_DAYS, timeString(d), (d.doubleValue() == 1 ? Selector.FIRST : Selector.SECOND));
                                }
                            }
                        }
                        else if (i3.endsWith("h") && h.intValue() == 0) {
                            String i4 = i3.replaceAll("[^0-9.]", "");
                            if (i4.length() > 0 && i4.replaceAll("[^0-9]", "").length() > 0 && i4.indexOf('.') == i4.lastIndexOf('.')) {
                                h = new BigDecimal(i4);
                                if (range) {
                                    time = time + " " + timeString(h) + "h";
                                }
                                else {
                                    time = time + " " + Phrase.build(Phrase.TIME_HOURS, timeString(h), (h.doubleValue() == 1 ? Selector.FIRST : Selector.SECOND));
                                }
                            }
                        }
                        else if (i3.endsWith("m") && m.intValue() == 0) {
                            String i4 = i3.replaceAll("[^0-9.]", "");
                            if (i4.length() > 0 && i4.replaceAll("[^0-9]", "").length() > 0 && i4.indexOf('.') == i4.lastIndexOf('.')) {
                                m = new BigDecimal(i4);
                                if (range) {
                                    time = time + " " + timeString(m) + "m";
                                }
                                else {
                                    time = time + " " + Phrase.build(Phrase.TIME_MINUTES, timeString(m), (m.doubleValue() == 1 ? Selector.FIRST : Selector.SECOND));
                                }
                            }
                        }
                        else if (i3.endsWith("s") && s.intValue() == 0) {
                            String i4 = i3.replaceAll("[^0-9.]", "");
                            if (i4.length() > 0 && i4.replaceAll("[^0-9]", "").length() > 0 && i4.indexOf('.') == i4.lastIndexOf('.')) {
                                s = new BigDecimal(i4);
                                if (range) {
                                    time = time + " " + timeString(s) + "s";
                                }
                                else {
                                    time = time + " " + Phrase.build(Phrase.TIME_SECONDS, timeString(s), (s.doubleValue() == 1 ? Selector.FIRST : Selector.SECOND));
                                }
                            }
                        }

                        argCount++;
                    }
                    next = 0;
                }
                else {
                    next = 0;
                }
            }
            count++;
        }

        if (time.startsWith(" ")) {
            time = time.substring(1);
        }

        return time;
    }

    /**
     * Parse rows from command arguments
     * 
     * @param inputArguments
     *            The command arguments
     * @return The number of rows
     */
    public static int parseRows(String[] inputArguments) {
        String[] argumentArray = inputArguments.clone();
        int rows = 0;
        int count = 0;
        int next = 0;

        for (String argument : argumentArray) {
            if (count > 0) {
                argument = argument.trim().toLowerCase(Locale.ROOT);
                argument = argument.replaceAll("\\\\", "");
                argument = argument.replaceAll("'", "");

                if (argument.equals("rows:")) {
                    next = 1;
                }
                else if (next == 1 || argument.startsWith("rows:")) {
                    argument = argument.replaceAll("rows:", "").trim();
                    if (!argument.startsWith("-")) {
                        String i2 = argument.replaceAll("[^0-9]", "");
                        if (i2.length() > 0 && i2.length() < 10) {
                            rows = Integer.parseInt(i2);
                        }
                    }

                    next = 0;
                }
                else {
                    next = 0;
                }
            }
            count++;
        }

        return rows;
    }

    private static String timeString(BigDecimal input) {
        return input.stripTrailingZeros().toPlainString();
    }
}
