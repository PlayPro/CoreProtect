package net.coreprotect.database.rollback;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Method;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import net.coreprotect.testing.TestItemStack;

class HorseContainerReplayTest {
    private boolean replay(ItemStack[] contents, Material material, int amount, int action, EntityType type) throws Exception {
        Method method = EntitySpawnRollbackHandler.class.getDeclaredMethod("modifyTransactionContents", ItemStack[].class, ItemStack.class, int.class, int.class, EntityType.class);
        method.setAccessible(true);
        return (Boolean) method.invoke(null, contents, new TestItemStack(material, amount), action, 64, type);
    }

    @ParameterizedTest
    @EnumSource(value = EntityType.class, names = {"DONKEY", "MULE", "LLAMA", "TRADER_LLAMA"})
    void ordinaryItemsUseChestStorage(EntityType type) throws Exception {
        ItemStack[] contents = new ItemStack[5];
        assertTrue(replay(contents, Material.DIAMOND, 64, 1, type));
        assertNull(contents[0]);
        assertNull(contents[1]);
        assertEquals(64, contents[2].getAmount());
        assertTrue(replay(contents, Material.DIAMOND, 64, 0, type));
        assertNull(contents[2]);
    }

    @ParameterizedTest
    @EnumSource(value = EntityType.class, names = {"DONKEY", "MULE", "LLAMA", "TRADER_LLAMA"})
    void fullChestDoesNotOverflowIntoEquipment(EntityType type) throws Exception {
        ItemStack[] contents = {null, null, new TestItemStack(Material.DIAMOND, 64)};
        // A full inventory is a best-effort replay, matching upstream behavior.
        assertTrue(replay(contents, Material.DIAMOND, 1, 1, type));
        assertNull(contents[0]);
        assertNull(contents[1]);
        assertEquals(64, contents[2].getAmount());
    }

    @ParameterizedTest
    @EnumSource(value = EntityType.class, names = {"DONKEY", "MULE"})
    void saddleUsesSaddleSlotAndPreservesOtherEquipment(EntityType type) throws Exception {
        ItemStack[] contents = new ItemStack[3];
        assertTrue(replay(contents, Material.SADDLE, 1, 1, type));
        assertEquals(Material.SADDLE, contents[0].getType());
        assertNull(contents[1]);
        assertTrue(replay(contents, Material.SADDLE, 1, 0, type));
        assertNull(contents[0]);
    }

    @ParameterizedTest
    @EnumSource(value = EntityType.class, names = {"LLAMA", "TRADER_LLAMA"})
    void decorIsLimitedToOneAndSaddlesStayInStorage(EntityType type) throws Exception {
        ItemStack[] contents = new ItemStack[4];
        assertTrue(replay(contents, Material.RED_CARPET, 2, 1, type));
        assertNull(contents[0]);
        assertEquals(1, contents[1].getAmount());
        assertEquals(1, contents[2].getAmount());
        assertTrue(replay(contents, Material.SADDLE, 1, 1, type));
        assertNull(contents[0]);
        assertEquals(Material.SADDLE, contents[3].getType());
    }

    @Test
    void mossIsStorageRatherThanLlamaDecor() throws Exception {
        ItemStack[] contents = new ItemStack[3];
        assertTrue(replay(contents, Material.MOSS_CARPET, 1, 1, EntityType.LLAMA));
        assertNull(contents[1]);
        assertEquals(Material.MOSS_CARPET, contents[2].getType());
    }

    @Test
    void genericContainerStillUsesFirstSlot() throws Exception {
        ItemStack[] contents = new ItemStack[1];
        assertTrue(replay(contents, Material.DIAMOND, 1, 1, EntityType.CHEST_MINECART));
        assertEquals(Material.DIAMOND, contents[0].getType());
    }
}
