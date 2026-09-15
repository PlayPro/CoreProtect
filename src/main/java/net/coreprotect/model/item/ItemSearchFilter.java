package net.coreprotect.model.item;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.ShulkerBox;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/** Matches item properties without changing the item or assigning it an identity. */
public final class ItemSearchFilter {
    private final Map<String, String> values;
    private final Material material;

    public ItemSearchFilter(Map<String, String> values) {
        this.values = Map.copyOf(values);
        material = values.containsKey("i") ? Material.matchMaterial(values.get("i")) : null;
        if (values.containsKey("i") && (material == null || isAir(material))) {
            throw new IllegalArgumentException("Unknown item material.");
        }
        if (!values.containsKey("i") && !values.containsKey("name") && !values.containsKey("enchant")
                && !values.containsKey("model") && !values.containsKey("data")) {
            throw new IllegalArgumentException("Specify i:, name:, enchant:, model:, or data:.");
        }
        if (values.containsKey("model")) Integer.parseInt(values.get("model"));
        if (values.containsKey("enchant")) {
            String[] parts = values.get("enchant").split("=");
            if (parts.length > 2 || NamespacedKey.fromString(parts[0]) == null) throw new IllegalArgumentException("Use enchant:fortune=3.");
            if (parts.length == 2 && Integer.parseInt(parts[1]) < 1) throw new IllegalArgumentException("Enchantment level must be positive.");
        }
        if (values.containsKey("data")) {
            String key = values.get("data").split("=", 2)[0];
            if (NamespacedKey.fromString(key) == null) throw new IllegalArgumentException("Use data:plugin:key=value.");
        }
        if (values.containsKey("exact") && !List.of("true", "false").contains(values.get("exact"))) throw new IllegalArgumentException("exact: must be true or false.");
    }

    public boolean matches(ItemStack item) {
        if (item == null || isAir(item.getType()) || item.getAmount() <= 0) return false;
        if (material != null && material != item.getType()) return false;
        ItemMeta meta = item.getItemMeta();
        if (values.containsKey("name")) {
            if (meta == null || !meta.hasDisplayName()) return false;
            String actual = normalize(meta.getDisplayName());
            String expected = normalize(values.get("name"));
            if (Boolean.parseBoolean(values.get("exact")) ? !actual.equals(expected) : !actual.contains(expected)) return false;
        }
        if (values.containsKey("model") && (meta == null || !meta.hasCustomModelData()
                || meta.getCustomModelData() != Integer.parseInt(values.get("model")))) return false;
        if (values.containsKey("enchant")) {
            String[] parts = values.get("enchant").split("=");
            NamespacedKey key = NamespacedKey.fromString(parts[0]);
            Map<Enchantment, Integer> enchantments = meta instanceof EnchantmentStorageMeta
                    ? ((EnchantmentStorageMeta) meta).getStoredEnchants() : item.getEnchantments();
            if (enchantments.entrySet().stream().noneMatch(entry -> entry.getKey().getKey().equals(key)
                    && (parts.length == 1 || entry.getValue() == Integer.parseInt(parts[1])))) return false;
        }
        if (values.containsKey("data")) {
            String[] parts = values.get("data").split("=", 2);
            NamespacedKey key = NamespacedKey.fromString(parts[0]);
            if (meta == null || !meta.getPersistentDataContainer().getKeys().contains(key)) return false;
            if (parts.length == 2 && (!meta.getPersistentDataContainer().has(key, PersistentDataType.STRING)
                    || !parts[1].equals(meta.getPersistentDataContainer().get(key, PersistentDataType.STRING)))) return false;
        }
        return true;
    }

    public List<String> find(ItemStack item, String path, boolean contents) {
        List<String> matches = new ArrayList<>();
        visit(item, path, contents, 0, new int[] {0}, matches);
        return matches;
    }

    private void visit(ItemStack item, String path, boolean contents, int depth, int[] visited, List<String> matches) {
        if (item == null || isAir(item.getType())) return;
        if (++visited[0] > 4096 || depth > 16) throw new IllegalArgumentException("Item contents exceed the search safety limit.");
        if (matches(item)) matches.add(path + ": " + item.getAmount() + "x " + item.getType().getKey());
        if (!contents) return;
        ItemMeta meta = item.getItemMeta();
        ItemStack[] children = null;
        if (meta instanceof BundleMeta) children = ((BundleMeta) meta).getItems().toArray(new ItemStack[0]);
        else if (meta instanceof BlockStateMeta && ((BlockStateMeta) meta).getBlockState() instanceof ShulkerBox) {
            children = ((ShulkerBox) ((BlockStateMeta) meta).getBlockState()).getInventory().getContents();
        }
        if (children != null) {
            for (int slot = 0; slot < children.length; slot++) visit(children[slot], path + "/" + item.getType().getKey() + "[" + slot + "]", true, depth + 1, visited, matches);
        }
    }

    private static boolean isAir(Material material) {
        return material == Material.AIR || material == Material.CAVE_AIR || material == Material.VOID_AIR;
    }

    private static String normalize(String value) {
        return ChatColor.stripColor(value).toLowerCase(Locale.ROOT);
    }
}
