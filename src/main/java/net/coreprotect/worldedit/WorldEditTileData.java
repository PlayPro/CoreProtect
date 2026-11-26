package net.coreprotect.worldedit;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.block.banner.Pattern;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.sk89q.jnbt.CompoundTag;
import com.sk89q.jnbt.ListTag;
import com.sk89q.jnbt.StringTag;
import com.sk89q.jnbt.Tag;
import com.sk89q.worldedit.blocks.BaseItemStack;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.world.block.BaseBlock;
import com.sk89q.worldedit.world.item.ItemType;

import net.coreprotect.model.BlockGroup;
import net.coreprotect.model.SignState;
import net.coreprotect.utility.BlockUtils;
import net.coreprotect.utility.EntityUtils;
import net.coreprotect.utility.ErrorReporter;
import net.coreprotect.utility.ItemUtils;
import net.coreprotect.utility.SkullProfileUtils;

final class WorldEditTileData {

    private static final Set<String> TILE_COORDINATES = Set.of("id", "x", "y", "z");
    private static final int MAX_INVENTORY_SLOTS = 256;

    private WorldEditTileData() {
    }

    static CompoundTag read(BaseBlock block) {
        return block == null ? null : block.getNbtData();
    }

    static boolean changed(CompoundTag before, CompoundTag after) {
        if (after == null || before == after) {
            return false;
        }
        Map<String, ?> previous = before == null ? Map.of() : before.getValue();
        return !equalEntries(previous, after.getValue(), TILE_COORDINATES);
    }

    private static boolean equalEntries(Map<?, ?> first, Map<?, ?> second, Set<String> ignored) {
        for (Map.Entry<?, ?> entry : first.entrySet()) {
            if (!ignored.contains(entry.getKey()) && !equalValue(entry.getValue(), second.get(entry.getKey()))) {
                return false;
            }
        }
        return second.keySet().stream().allMatch(key -> ignored.contains(key) || first.containsKey(key));
    }

    private static boolean equalValue(Object first, Object second) {
        if (first instanceof Tag && second instanceof Tag) {
            return first.getClass() == second.getClass() && equalValue(((Tag<?, ?>) first).getValue(), ((Tag<?, ?>) second).getValue());
        }
        if (first instanceof Map && second instanceof Map) {
            return equalEntries((Map<?, ?>) first, (Map<?, ?>) second, Set.of());
        }
        if (first instanceof List && second instanceof List) {
            List<?> left = (List<?>) first;
            List<?> right = (List<?>) second;
            if (left.size() != right.size()) {
                return false;
            }
            for (int index = 0; index < left.size(); index++) {
                if (!equalValue(left.get(index), right.get(index))) {
                    return false;
                }
            }
            return true;
        }
        return Objects.deepEquals(first, second);
    }

    static SignState readSign(CompoundTag tag) {
        String[] lines = new String[8];
        CompoundTag front = child(tag, "front_text");
        CompoundTag back = child(tag, "back_text");
        if (front == null && back == null) {
            for (int index = 0; index < 4; index++) {
                lines[index] = text(tag.getString("Text" + (index + 1)));
            }
            return new SignState(lines, signColor(tag, "Color"), DyeColor.BLACK.getColor().asRGB(), tag.asInt("GlowingText") != 0, false, tag.asInt("is_waxed") != 0);
        }
        readSide(front, lines, 0);
        readSide(back, lines, 4);
        return new SignState(lines, signColor(front, "color"), signColor(back, "color"), front != null && front.asInt("has_glowing_text") != 0,
                back != null && back.asInt("has_glowing_text") != 0, tag.asInt("is_waxed") != 0);
    }

    private static void readSide(CompoundTag side, String[] lines, int offset) {
        if (side == null) {
            return;
        }
        List<? extends Tag<?, ?>> messages = side.getList("messages");
        for (int index = 0; index < Math.min(messages.size(), 4); index++) {
            Tag<?, ?> message = messages.get(index);
            lines[offset + index] = message instanceof StringTag ? text((String) message.getValue()) : text(message);
        }
    }

    private static int signColor(CompoundTag tag, String key) {
        DyeColor color = tag == null ? null : dyeColor(tag.getString(key));
        return (color == null ? DyeColor.BLACK : color).getColor().asRGB();
    }

    private static DyeColor dyeColor(String name) {
        try {
            return DyeColor.valueOf(name.toUpperCase(Locale.ROOT));
        }
        catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String text(String value) {
        String trimmed = value.trim();
        if (trimmed.isEmpty() || "{[\"'".indexOf(trimmed.charAt(0)) < 0) {
            return value;
        }
        try {
            return text(new JsonParser().parse(trimmed));
        }
        catch (JsonParseException e) {
            return value;
        }
    }

    private static String text(Object value) {
        if (value instanceof StringTag) {
            return (String) ((StringTag) value).getValue();
        }
        if (value instanceof ListTag) {
            StringBuilder result = new StringBuilder();
            for (Object element : ((ListTag<?, ?>) value).getValue()) {
                result.append(text(element));
            }
            return result.toString();
        }
        if (value instanceof CompoundTag) {
            Map<String, ?> component = ((CompoundTag) value).getValue();
            if (component.size() == 1 && component.containsKey("")) {
                return text(component.get(""));
            }
            Object literal = component.containsKey("text") ? component.get("text") : component.get("fallback");
            return text(literal) + text(component.get("extra"));
        }
        if (value instanceof JsonElement) {
            JsonElement element = (JsonElement) value;
            if (element.isJsonPrimitive()) {
                return element.getAsString();
            }
            if (element.isJsonArray()) {
                StringBuilder result = new StringBuilder();
                for (JsonElement child : element.getAsJsonArray()) {
                    result.append(text(child));
                }
                return result.toString();
            }
            if (element.isJsonObject()) {
                JsonObject component = element.getAsJsonObject();
                return text(component.has("text") ? component.get("text") : component.get("fallback")) + text(component.get("extra"));
            }
        }
        return "";
    }

    static int readSpawner(CompoundTag tag, int fallback) {
        CompoundTag spawn = child(tag, "SpawnData");
        if (spawn == null) {
            return fallback;
        }
        CompoundTag entity = child(spawn, "entity");
        String name = entity == null ? "" : entity.getString("id");
        if (name.isEmpty()) {
            name = spawn.getString("id");
        }
        if (name.startsWith("minecraft:")) {
            name = name.substring("minecraft:".length());
        }
        if (name.isEmpty() || name.indexOf(':') >= 0) {
            return fallback;
        }
        EntityType type = EntityType.fromName(name);
        if (type == null) {
            try {
                type = EntityType.valueOf(name.toUpperCase(Locale.ROOT));
            }
            catch (IllegalArgumentException e) {
                return fallback;
            }
        }
        return EntityUtils.getSpawnerType(type);
    }

    static boolean isCommandBlock(Material type) {
        return type == Material.COMMAND_BLOCK || type == Material.CHAIN_COMMAND_BLOCK || type == Material.REPEATING_COMMAND_BLOCK;
    }

    static boolean isBanner(Material type) {
        return type.name().endsWith("_BANNER");
    }

    static void captureBlockMetadata(WorldEditBlockState block, CompoundTag tag, ItemStack[] items) {
        if (tag == null) {
            return;
        }
        Material type = block.getType();
        if (BlockUtils.isSkull(type)) {
            captureSkull(block, tag);
        }
        else if (isCommandBlock(type)) {
            if (tag.getValue().get("Command") instanceof StringTag) {
                block.setCapturedMetadata(List.of(tag.getString("Command")));
            }
        }
        else if (isBanner(type)) {
            block.setCapturedMetadata(readBanner(type, tag));
        }
        else if (BlockGroup.SHULKER_BOXES.contains(type) && items != null) {
            List<Object> metadata = new ArrayList<>();
            for (int slot = 0; slot < items.length; slot++) {
                if (items[slot] != null) {
                    Map<Integer, Object> item = ItemUtils.serializeItemStackLegacy(items[slot], null, slot);
                    if (!item.isEmpty()) {
                        metadata.add(item);
                    }
                }
            }
            block.setCapturedMetadata(metadata);
        }
    }

    private static List<Object> readBanner(Material type, CompoundTag tag) {
        List<Object> metadata = new ArrayList<>();
        DyeColor base = dyeColor(type.name().replace("_WALL_BANNER", "").replace("_BANNER", ""));
        if (base != null) {
            metadata.add(base);
        }
        boolean modern = tag.containsKey("patterns");
        for (CompoundTag entry : tag.getList(modern ? "patterns" : "Patterns", CompoundTag.class)) {
            String key = entry.getString(modern ? "pattern" : "Pattern");
            DyeColor color = modern ? dyeColor(entry.getString("color")) : legacyBannerColor(entry);
            if (key.isEmpty() || color == null) {
                continue;
            }
            try {
                Pattern pattern = new Pattern(Map.of("color", color.name(), "pattern", key));
                if (pattern.getPattern() != null) {
                    metadata.add(pattern.serialize());
                }
            }
            catch (RuntimeException e) {
                // Patterns may belong to datapacks that are absent on this server.
            }
        }
        return metadata;
    }

    private static DyeColor legacyBannerColor(CompoundTag tag) {
        Integer color = integer(tag, "Color");
        return color != null && color >= 0 && color <= 15 ? DyeColor.getByWoolData(color.byteValue()) : null;
    }

    private static void captureSkull(WorldEditBlockState block, CompoundTag tag) {
        CompoundTag profile = child(tag, "profile");
        boolean modern = profile != null;
        if (profile == null) {
            profile = child(tag, "SkullOwner");
        }
        String owner = "";
        String skin = null;
        if (profile != null) {
            owner = readOwner(profile, modern ? "id" : "Id", modern ? "name" : "Name");
            CompoundTag legacyProperties = modern ? null : child(profile, "Properties");
            List<CompoundTag> textures = modern ? profile.getList("properties", CompoundTag.class)
                    : legacyProperties == null ? List.of() : legacyProperties.getList("textures", CompoundTag.class);
            for (CompoundTag property : textures) {
                if (modern && !"textures".equals(property.getString("name"))) {
                    continue;
                }
                String value = property.getString(modern ? "value" : "Value");
                if (!value.isBlank() && value.indexOf(':') < 0) {
                    String signature = property.getString(modern ? "signature" : "Signature");
                    skin = SkullProfileUtils.PROFILE_PROPERTY_PREFIX + value + (signature.isBlank() ? "" : ":" + signature);
                    break;
                }
            }
        }
        else {
            String name = tag.getString("profile");
            if (name.isEmpty()) {
                name = tag.getString("SkullOwner");
            }
            if (SkullProfileUtils.isOwnerName(name)) {
                owner = name;
            }
        }
        block.setSkullProfile(owner, skin);
    }

    private static String readOwner(CompoundTag profile, String idKey, String nameKey) {
        int[] id = profile.getIntArray(idKey);
        if (id.length == 4) {
            return new UUID(((long) id[0] << 32) | (id[1] & 0xffffffffL), ((long) id[2] << 32) | (id[3] & 0xffffffffL)).toString();
        }
        String uuid = profile.getString(idKey);
        if (SkullProfileUtils.parseOwnerUuid(uuid) != null) {
            return uuid;
        }
        String name = profile.getString(nameKey);
        return SkullProfileUtils.isOwnerName(name) ? name : "";
    }

    static ItemStack[] readInventory(CompoundTag tag) {
        if (tag == null) {
            return null;
        }
        ItemStack[] items = null;
        for (CompoundTag entry : tag.getList("Items", CompoundTag.class)) {
            Integer slot = integer(entry, "Slot");
            if (slot == null || slot < 0 || slot >= MAX_INVENTORY_SLOTS) {
                continue;
            }
            ItemStack item = readItem(entry);
            if (item != null) {
                if (items == null) {
                    items = new ItemStack[slot + 1];
                }
                else if (items.length <= slot) {
                    items = Arrays.copyOf(items, slot + 1);
                }
                items[slot] = item;
            }
        }
        if (items != null) {
            return items;
        }
        for (String key : List.of("Book", "RecordItem", "item")) {
            ItemStack item = readItem(child(tag, key));
            if (item != null) {
                return new ItemStack[] { item };
            }
        }
        return null;
    }

    private static ItemStack readItem(CompoundTag tag) {
        if (tag == null) {
            return null;
        }
        String id = tag.getString("id");
        int count = tag.asInt(tag.containsKey("count") ? "count" : "Count");
        if (id.isEmpty() || count <= 0) {
            return null;
        }
        try {
            ItemType type = ItemType.REGISTRY.get(id);
            CompoundTag data = child(tag, tag.containsKey("components") ? "components" : "tag");
            return type == null ? null : BukkitAdapter.adapt(new BaseItemStack(type, data, count));
        }
        catch (RuntimeException | LinkageError e) {
            ErrorReporter.report(e, false);
            return null;
        }
    }

    private static CompoundTag child(CompoundTag tag, String name) {
        Object child = tag == null ? null : tag.getValue().get(name);
        return child instanceof CompoundTag ? (CompoundTag) child : null;
    }

    private static Integer integer(CompoundTag tag, String key) {
        Object element = tag.getValue().get(key);
        Object value = element instanceof Tag ? ((Tag<?, ?>) element).getValue() : null;
        return value instanceof Byte || value instanceof Short || value instanceof Integer ? ((Number) value).intValue() : null;
    }
}
