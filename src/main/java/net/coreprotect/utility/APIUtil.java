package net.coreprotect.utility;

import org.bukkit.Material;
import org.bukkit.entity.EntityType;

import java.util.ArrayList;
import java.util.List;

public class APIUtil {

    public static List<Object> parseList(List<Object> list) {
        List<Object> result = new ArrayList<>();

        if (list != null) {
            for (Object value : list) {
                if (value instanceof Material || value instanceof EntityType) {
                    result.add(value);
                }
                else if (value instanceof Integer) {
                    Material material = Util.getType((Integer) value);
                    result.add(material);
                }
            }
        }

        return result;
    }

    public static String[] toBlockLookupResults(String[] array) {
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

    public static String[] toContainerLookupResults(String[] array) {
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
            String amount = array[10];
            return new String[] { time, user, x, y, z, type, data, action, rolledBack, wid, "", "", amount };
        }

        return null;
    }

}
