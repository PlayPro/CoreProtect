package net.coreprotect.utility;

import java.util.Locale;

public class StringUtils {

    public static final int MAX_USERNAME_LENGTH = 100;

    private StringUtils() {
        throw new IllegalStateException("Utility class");
    }

    public static String normalizeUsername(String user) {
        if (user == null || user.length() <= MAX_USERNAME_LENGTH) {
            return user;
        }
        if (user.codePointCount(0, user.length()) <= MAX_USERNAME_LENGTH) {
            return user;
        }
        return user.substring(0, user.offsetByCodePoints(0, MAX_USERNAME_LENGTH));
    }

    public static String capitalize(String string, boolean allWords) {
        if (string == null || string.isEmpty()) {
            return string;
        }

        if (string.length() <= 1) {
            return string.toUpperCase(Locale.ROOT);
        }

        string = string.toLowerCase(Locale.ROOT);

        if (allWords) {
            StringBuilder builder = new StringBuilder();
            for (String substring : string.split(" ")) {
                if (substring.length() >= 3 && !substring.equals("and") && !substring.equals("the")) {
                    substring = substring.substring(0, 1).toUpperCase(Locale.ROOT) + substring.substring(1);
                }
                if (builder.length() > 0) {
                    builder.append(" ");
                }
                builder.append(substring);
            }

            return builder.toString();
        }

        return string.substring(0, 1).toUpperCase(Locale.ROOT) + string.substring(1);
    }

    public static String nameFilter(String name, int data) {
        if (name.equals("stone")) {
            switch (data) {
                case 1:
                    name = "granite";
                    break;
                case 2:
                    name = "polished_granite";
                    break;
                case 3:
                    name = "diorite";
                    break;
                case 4:
                    name = "polished_diorite";
                    break;
                case 5:
                    name = "andesite";
                    break;
                case 6:
                    name = "polished_andesite";
                    break;
                default:
                    name = "stone";
                    break;
            }
        }

        return name;
    }

    public static String[] toStringArray(String[] array) {
        int size = array.length;
        if (size == 11) {
            String time = array[0];
            String user = array[1];
            String x = array[2];
            String y = array[3];
            String z = array[4];
            String type = array[5];
            String data = array[6];
            String action = array[7];
            String rolledBack = array[8];
            String wid = array[9];
            String blockData = array[10];
            return new String[] { time, user, x, y, z, type, data, action, rolledBack, wid, "", "", blockData };
        }

        return null;
    }

    public static String hoverCommandFilter(String string) {
        if (string == null) {
            return null;
        }
        StringBuilder command = new StringBuilder();

        String[] data = string.trim().toLowerCase(Locale.ROOT).split("\\s+");
        if (data.length > 2) {
            if (data[1].equals("l")) {
                data[1] = "page";
            }

            if (data[2].startsWith("wid:")) {
                String nameWid = data[2].substring("wid:".length());
                Integer worldId = tryParseNonNegativeInt(nameWid);
                if (worldId != null) {
                    String worldName = WorldUtils.getWorldName(worldId);
                    if (worldName != null && !worldName.isEmpty() && !containsWhitespace(worldName)) {
                        data[2] = worldName;
                    }
                }
            }

            if (data[1].equals("teleport") && data.length >= 6) {
                int coordStart = data.length - 3;
                Integer x = tryParseFloorCoordinate(data[coordStart], -0.50);
                Integer y = tryParseInt(data[coordStart + 1]);
                Integer z = tryParseFloorCoordinate(data[coordStart + 2], -0.50);
                if (x != null) {
                    data[coordStart] = Integer.toString(x);
                }
                if (y != null) {
                    data[coordStart + 1] = Integer.toString(y);
                }
                if (z != null) {
                    data[coordStart + 2] = Integer.toString(z);
                }
            }
        }

        for (String s : data) {
            if (s.isEmpty()) {
                continue;
            }

            if (command.length() > 0) {
                command.append(" ");
            }

            command.append(s);
        }

        return command.toString();
    }

    private static Integer tryParseNonNegativeInt(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c < '0' || c > '9') {
                return null;
            }
        }
        try {
            return Integer.valueOf(Integer.parseInt(value));
        }
        catch (NumberFormatException e) {
            return null;
        }
    }

    private static Integer tryParseInt(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            return Integer.valueOf(Integer.parseInt(value));
        }
        catch (NumberFormatException e) {
            return null;
        }
    }

    private static Integer tryParseFloorCoordinate(String value, double offset) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            double parsed = Double.parseDouble(value);
            if (!Double.isFinite(parsed)) {
                return null;
            }
            double shifted = Math.floor(parsed + offset);
            if (shifted < Integer.MIN_VALUE || shifted > Integer.MAX_VALUE) {
                return null;
            }
            return Integer.valueOf((int) shifted);
        }
        catch (NumberFormatException e) {
            return null;
        }
    }

    private static boolean containsWhitespace(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (Character.isWhitespace(value.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    public static Integer[] convertArray(String[] array) {
        java.util.List<Integer> list = new java.util.ArrayList<>();
        for (String item : array) {
            list.add(Integer.parseInt(item));
        }
        return list.toArray(new Integer[list.size()]);
    }
} 