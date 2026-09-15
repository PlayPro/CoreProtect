package net.coreprotect.model.item;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.List;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.block.ShulkerBox;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.Test;

class ItemSearchFilterTest {
    private ItemStack item(Material type, ItemMeta meta) {
        ItemStack item = mock(ItemStack.class);
        when(item.getType()).thenReturn(type);
        when(item.getAmount()).thenReturn(1);
        when(item.getItemMeta()).thenReturn(meta);
        return item;
    }
    @Test void nameSearchIgnoresColorAndCaseButSupportsExactMatching() {
        ItemMeta meta = mock(ItemMeta.class);
        when(meta.hasDisplayName()).thenReturn(true);
        when(meta.getDisplayName()).thenReturn("\u00a76Ancient Excavator");
        ItemStack item = item(Material.DIAMOND_PICKAXE, meta);
        assertTrue(new ItemSearchFilter(Map.of("name", "excavator")).matches(item));
        assertFalse(new ItemSearchFilter(Map.of("name", "excavator", "exact", "true")).matches(item));
        assertTrue(new ItemSearchFilter(Map.of("name", "ancient excavator", "exact", "true")).matches(item));
    }
    @Test void nestedBundleAndShulkerSearchReportsTheActualSlotPath() {
        ItemStack diamond = item(Material.DIAMOND, null);
        BundleMeta bundle = mock(BundleMeta.class);
        when(bundle.getItems()).thenReturn(List.of(diamond));
        ItemStack nested = item(Material.BUNDLE, bundle);
        Inventory inventory = mock(Inventory.class);
        when(inventory.getContents()).thenReturn(new ItemStack[] {null, nested});
        ShulkerBox shulker = mock(ShulkerBox.class);
        when(shulker.getInventory()).thenReturn(inventory);
        BlockStateMeta meta = mock(BlockStateMeta.class);
        when(meta.getBlockState()).thenReturn(shulker);
        ItemStack box = item(Material.SHULKER_BOX, meta);
        ItemSearchFilter filter = new ItemSearchFilter(Map.of("i", "diamond"));
        assertEquals(List.of("slot[4]/minecraft:shulker_box[1]/minecraft:bundle[0]: 1x minecraft:diamond"), filter.find(box, "slot[4]", true));
        assertTrue(filter.find(box, "slot[4]", false).isEmpty());
    }
    @Test void recursiveContentsFailExplicitly() {
        BundleMeta meta = mock(BundleMeta.class);
        ItemStack bundle = item(Material.BUNDLE, meta);
        when(meta.getItems()).thenReturn(List.of(bundle));
        assertThrows(IllegalArgumentException.class, () -> new ItemSearchFilter(Map.of("name", "test")).find(bundle, "slot", true));
    }
    @Test void rejectsInvalidFilters() {
        assertThrows(IllegalArgumentException.class, () -> new ItemSearchFilter(Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new ItemSearchFilter(Map.of("i", "not_an_item")));
        assertThrows(IllegalArgumentException.class, () -> new ItemSearchFilter(Map.of("enchant", "fortune=0")));
    }
}
