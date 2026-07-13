package net.coreprotect.utility;

import net.coreprotect.language.Phrase;
import net.coreprotect.language.Selector;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ChatUtils {
    private static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("#.##", new DecimalFormatSymbols(Locale.ROOT));

    private ChatUtils() {
        throw new IllegalStateException("Utility class");
    }

    public static String getCoordinates(String usedCommand, int worldId, int x, int y, int z, boolean displayWorld, boolean italic) {
        StringBuilder message = new StringBuilder("<hover:show_text:");

        StringBuilder worldDisplay = new StringBuilder();
        if (displayWorld) {
            worldDisplay.append("/").append(WorldUtils.getWorldName(worldId));
        }

        message.append("'/" + usedCommand + " teleport " + WorldUtils.getWorldName(worldId) + " " + x + " " + y + " " + z + "'");
        message.append(">");

        // command
        final String command = "'/" + usedCommand + " teleport wid:" + worldId + " " + DECIMAL_FORMAT.format(x + 0.50) + " " + y + " " + DECIMAL_FORMAT.format(z + 0.50) + "'";

        message.append("<click:run_command:").append(command).append(">");

        // chat output
        message.append(Color.GREY + (italic ? Color.ITALIC : "") + "(x" + x + "/y" + y + "/z" + z + worldDisplay + ")");

        return message.append("</click></hover>").toString();
    }

    public static String getCoordinateTooltip(int worldId, int x, int y, int z, String label, boolean italic) {
        String coordinates = "(x" + x + "/y" + y + "/z" + z + "/" + WorldUtils.getWorldName(worldId) + ")";
        String tooltip = Color.GREY + label + ": " + Color.WHITE + coordinates;
        return "<hover:show_text:'" + tooltip.replace("'", "\\'") + "'>" + Color.GREY + (italic ? Color.ITALIC : "") + " ⓘ</hover>";
    }

    // Use <extra> to insert text inside the hover
    public static String formatHoverCoordinates(String usedCommand, int worldId, int x, int y, int z) {
        final String command = "'/" + usedCommand + " teleport wid:" + worldId + " " + DECIMAL_FORMAT.format(x + 0.50) + " " + y + " " + DECIMAL_FORMAT.format(z + 0.50) + "'";

        return "<hover:show_text:'<gray>" + "(x" + x + "/y" + y + "/z" + z + "/" + WorldUtils.getWorldName(worldId) + ")'><click:run_command:" + command + "><extra></click></hover>";
    }

    public static String getPageNavigation(String usedCommand, int page, int totalPages) {
        StringBuilder message = new StringBuilder();

        // back arrow
        String backArrow = "";
        if (page > 1) {
            backArrow = "◀ ";
            final String command = "/" + usedCommand + " l " + (page - 1);
            backArrow = "<hover:show_text:'" + command + "'><click:run_command:" + command + ">" + backArrow + "</click></hover>";
        }

        // next arrow
        String nextArrow = " ";
        if (page < totalPages) {
            nextArrow = " ▶ ";
            final String command = "/" + usedCommand + " l " + (page + 1);
            nextArrow = "<hover:show_text:'" + command + "'><click:run_command:" + command + ">" + nextArrow + "</click></hover>";
        }

        StringBuilder pagination = new StringBuilder();
        if (totalPages > 1) {
            pagination.append(Color.GREY + "(");
            if (page > 3) {
                pagination.append(Color.WHITE + "<hover:show_text:'/" + usedCommand + " l 1'><click:run_command:/" + usedCommand + " l 1>1</click></hover> ");
                if (page > 4 && totalPages > 7) {
                    pagination.append("<hover:show_text:'/" + usedCommand + " l <page>'><click:suggest_command:/" + usedCommand + " l >" + Color.GREY + "...</click></hover> ");
                }
                else {
                    pagination.append(Color.GREY + "| ");
                }
            }

            int displayStart = (page - 2) < 1 ? 1 : (page - 2);
            int displayEnd = (page + 2) > totalPages ? totalPages : (page + 2);
            if (page > 999 || (page > 101 && totalPages > 99999)) { // limit to max 5 page numbers
                displayStart = (displayStart + 1) < displayEnd ? (displayStart + 1) : displayStart;
                displayEnd = (displayEnd - 1) > displayStart ? (displayEnd - 1) : displayEnd;
                if (displayStart > (totalPages - 3)) {
                    displayStart = (totalPages - 3) < 1 ? 1 : (totalPages - 3);
                }
            }
            else { // display at least 7 page numbers
                if (displayStart > (totalPages - 5)) {
                    displayStart = (totalPages - 5) < 1 ? 1 : (totalPages - 5);
                }
                if (displayEnd < 6) {
                    displayEnd = 6 > totalPages ? totalPages : 6;
                }
            }

            if (page > 99999) { // limit to max 3 page numbers
                displayStart = (displayStart + 1) < displayEnd ? (displayStart + 1) : displayStart;
                displayEnd = (displayEnd - 1) >= displayStart ? (displayEnd - 1) : displayEnd;
                if (page == (totalPages - 1)) {
                    displayEnd = totalPages - 1;
                }
                if (displayStart < displayEnd) {
                    displayStart = displayEnd;
                }
            }

            if (page > 3 && displayStart == 1) {
                displayStart = 2;
            }

            for (int displayPage = displayStart; displayPage <= displayEnd; displayPage++) {
                if (page != displayPage) {
                    final String command = "/" + usedCommand + " l " + displayPage;
                    pagination.append(Color.WHITE + "<hover:show_text:'" + command + "'><click:run_command:'" + command + "'>" + displayPage + (displayPage < totalPages ? " " : "") + "</click></hover>");
                }
                else {
                    pagination.append(Color.WHITE + Color.UNDERLINE + displayPage + Color.RESET + (displayPage < totalPages ? " " : ""));
                }
                if (displayPage < displayEnd) {
                    pagination.append(Color.GREY + "| ");
                }
            }

            if (displayEnd < totalPages) {
                if (displayEnd < (totalPages - 1)) {
                    pagination.append("<hover:show_text:'/" + usedCommand + " l <page>'><click:suggest_command:/" + usedCommand + " l >" + Color.GREY + "...</click></hover> ");
                }
                else {
                    pagination.append(Color.GREY + "| ");
                }
                if (page != totalPages) {
                    final String command = "/" + usedCommand + " l " + totalPages;
                    pagination.append(Color.WHITE + "<hover:show_text:'" + command + "'><click:run_command:" + command + ">" + totalPages + "</click></hover>");
                }
                else {
                    pagination.append(Color.WHITE + Color.UNDERLINE + totalPages);
                }
            }

            pagination.append(Color.GREY + ")");
        }

        return message.append(Color.WHITE + backArrow + Color.DARK_AQUA + Phrase.build(Phrase.LOOKUP_PAGE, Color.WHITE + page + "/" + totalPages) + nextArrow + pagination).toString();
    }

    public static String getTimeSince(long resultTime, long currentTime, boolean component) {
        StringBuilder message = new StringBuilder();
        double timeSince = currentTime - (resultTime + 0.00);
        if (timeSince < 0.00) {
            timeSince = 0.00;
        }

        DecimalFormat decimalFormat = new DecimalFormat("0.00");

        // minutes
        timeSince = timeSince / 60;
        if (timeSince < 60.0) {
            message.append(Phrase.build(Phrase.LOOKUP_TIME, decimalFormat.format(timeSince) + Phrase.build(Phrase.TIME_UNITS, Selector.FIRST)));
        }

        // hours
        if (message.length() == 0) {
            timeSince = timeSince / 60;
            if (timeSince < 24.0) {
                message.append(Phrase.build(Phrase.LOOKUP_TIME, decimalFormat.format(timeSince) + Phrase.build(Phrase.TIME_UNITS, Selector.SECOND)));
            }
        }

        // days
        if (message.length() == 0) {
            timeSince = timeSince / 24;
            message.append(Phrase.build(Phrase.LOOKUP_TIME, decimalFormat.format(timeSince) + Phrase.build(Phrase.TIME_UNITS, Selector.THIRD)));
        }

        if (component) {
            Date logDate = new Date(resultTime * 1000L);
            String formattedTimestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss z").format(logDate);

            return "<hover:show_text:'<gray>" + formattedTimestamp + "'><gray>" + message + "</hover>";
        }

        return message.toString();
    }
}
