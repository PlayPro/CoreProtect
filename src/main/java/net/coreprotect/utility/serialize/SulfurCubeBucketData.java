package net.coreprotect.utility.serialize;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

public final class SulfurCubeBucketData {

    private static final String SULFUR_CUBE_CONTENT_KEY = "sulfurCubeContent";
    private static volatile ComponentAccess componentAccess;
    private static volatile boolean componentAccessUnavailable;

    private SulfurCubeBucketData() {
        throw new IllegalStateException("Utility class");
    }

    public static void appendMetadata(ItemStack item, List<List<Map<String, Object>>> metadata) {
        if (!isSulfurCubeBucket(item)) {
            return;
        }

        byte[] absorbedItemData = getAbsorbedItemData(item);
        if (absorbedItemData == null) {
            return;
        }

        Map<String, Object> map = new HashMap<>();
        map.put(SULFUR_CUBE_CONTENT_KEY, absorbedItemData);

        List<Map<String, Object>> list = new ArrayList<>();
        list.add(map);
        metadata.add(list);
    }

    public static boolean apply(ItemStack item, Map<String, Object> mapData) {
        if (!mapData.containsKey(SULFUR_CUBE_CONTENT_KEY)) {
            return false;
        }

        Object value = mapData.get(SULFUR_CUBE_CONTENT_KEY);
        if (isSulfurCubeBucket(item) && value instanceof byte[]) {
            ItemStack absorbedItem = deserializeItem((byte[]) value);
            if (absorbedItem != null) {
                setAbsorbedItem(item, absorbedItem);
            }
        }

        return true;
    }

    private static byte[] getAbsorbedItemData(ItemStack item) {
        ComponentAccess access = getComponentAccess();
        if (access == null) {
            return null;
        }

        try {
            if (!Boolean.TRUE.equals(access.hasData.invoke(item, access.sulfurCubeContentType))) {
                return null;
            }

            Object content = access.getData.invoke(item, access.sulfurCubeContentType);
            if (content == null) {
                return null;
            }

            Object absorbedItem = access.absorbedItem.invoke(content);
            if (!(absorbedItem instanceof ItemStack)) {
                return null;
            }

            return (byte[]) access.serializeAsBytes.invoke(absorbedItem);
        }
        catch (ReflectiveOperationException | LinkageError | ClassCastException | IllegalArgumentException exception) {
            return null;
        }
    }

    private static ItemStack deserializeItem(byte[] itemData) {
        ComponentAccess access = getComponentAccess();
        if (access == null) {
            return null;
        }

        try {
            Object item = access.deserializeBytes.invoke(null, itemData);
            return (item instanceof ItemStack) ? (ItemStack) item : null;
        }
        catch (ReflectiveOperationException | LinkageError | ClassCastException | IllegalArgumentException exception) {
            return null;
        }
    }

    private static void setAbsorbedItem(ItemStack item, ItemStack absorbedItem) {
        ComponentAccess access = getComponentAccess();
        if (access == null) {
            return;
        }

        try {
            Object content = access.sulfurCubeContent.invoke(null, absorbedItem);
            access.setData.invoke(item, access.sulfurCubeContentType, content);
        }
        catch (ReflectiveOperationException | LinkageError | ClassCastException | IllegalArgumentException exception) {
            // Ignore missing or incompatible Paper data component APIs.
        }
    }

    private static boolean isSulfurCubeBucket(ItemStack item) {
        if (item == null) {
            return false;
        }

        Material type = item.getType();
        return type != null && "SULFUR_CUBE_BUCKET".equals(type.name());
    }

    private static ComponentAccess getComponentAccess() {
        if (componentAccessUnavailable) {
            return null;
        }

        ComponentAccess access = componentAccess;
        if (access != null) {
            return access;
        }

        try {
            Class<?> dataComponentTypes = Class.forName("io.papermc.paper.datacomponent.DataComponentTypes");
            Class<?> dataComponentType = Class.forName("io.papermc.paper.datacomponent.DataComponentType");
            Class<?> valuedDataComponentType = Class.forName("io.papermc.paper.datacomponent.DataComponentType$Valued");
            Class<?> sulfurCubeContent = Class.forName("io.papermc.paper.datacomponent.item.SulfurCubeContent");

            Object sulfurCubeContentType = dataComponentTypes.getField("SULFUR_CUBE_CONTENT").get(null);
            access = new ComponentAccess(
                    sulfurCubeContentType,
                    ItemStack.class.getMethod("hasData", dataComponentType),
                    ItemStack.class.getMethod("getData", valuedDataComponentType),
                    ItemStack.class.getMethod("setData", valuedDataComponentType, Object.class),
                    sulfurCubeContent.getMethod("absorbedItem"),
                    sulfurCubeContent.getMethod("sulfurCubeContent", ItemStack.class),
                    ItemStack.class.getMethod("serializeAsBytes"),
                    ItemStack.class.getMethod("deserializeBytes", byte[].class));
            componentAccess = access;
            return access;
        }
        catch (ReflectiveOperationException | LinkageError exception) {
            componentAccessUnavailable = true;
            return null;
        }
    }

    private static final class ComponentAccess {

        private final Object sulfurCubeContentType;
        private final Method hasData;
        private final Method getData;
        private final Method setData;
        private final Method absorbedItem;
        private final Method sulfurCubeContent;
        private final Method serializeAsBytes;
        private final Method deserializeBytes;

        private ComponentAccess(Object sulfurCubeContentType, Method hasData, Method getData, Method setData,
                Method absorbedItem, Method sulfurCubeContent, Method serializeAsBytes, Method deserializeBytes) {
            this.sulfurCubeContentType = sulfurCubeContentType;
            this.hasData = hasData;
            this.getData = getData;
            this.setData = setData;
            this.absorbedItem = absorbedItem;
            this.sulfurCubeContent = sulfurCubeContent;
            this.serializeAsBytes = serializeAsBytes;
            this.deserializeBytes = deserializeBytes;
        }
    }
}
