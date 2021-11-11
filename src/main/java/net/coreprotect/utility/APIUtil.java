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

}
