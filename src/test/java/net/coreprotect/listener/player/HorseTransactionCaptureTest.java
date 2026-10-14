package net.coreprotect.listener.player;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.ChestedHorse;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.Collections;
import org.mockito.MockedStatic;
import net.coreprotect.CoreProtect;
import net.coreprotect.config.Config;
import net.coreprotect.consumer.Queue;
import net.coreprotect.paper.PaperAdapter;
import net.coreprotect.paper.PaperInterface;
import net.coreprotect.testing.TestItemStack;
import net.coreprotect.utility.EntitySpawnTracking;
import net.coreprotect.utility.ItemUtils;

class HorseTransactionCaptureTest {
    private MockedStatic<Config> configs;
    private MockedStatic<CoreProtect> plugin;
    private MockedStatic<EntitySpawnTracking> tracking;
    private MockedStatic<Queue> queue;
    private PaperInterface previousAdapter;
    private ChestedHorse horse;
    private Inventory inventory;
    private ItemStack[] contents;
    private final List<Transfer> transfers = new ArrayList<>();
    private Runnable scheduled;
    private Runnable retired;

    @BeforeEach
    void setUp() throws Exception {
        pending().clear();
        previousAdapter = PaperAdapter.ADAPTER;
        PaperAdapter.ADAPTER = mock(PaperInterface.class);
        configs = mockStatic(Config.class);
        plugin = mockStatic(CoreProtect.class);
        tracking = mockStatic(EntitySpawnTracking.class);
        queue = mockStatic(Queue.class);
        Config config = new Config();
        config.ITEM_TRANSACTIONS = true;
        configs.when(() -> Config.getConfig(any(World.class))).thenReturn(config);
        horse = mock(ChestedHorse.class, RETURNS_DEEP_STUBS);
        inventory = horse.getInventory();
        World world = mock(World.class);
        when(horse.getWorld()).thenReturn(world);
        when(horse.getUniqueId()).thenReturn(UUID.randomUUID());
        when(horse.getLocation()).thenReturn(new Location(world, 1, 2, 3));
        when(horse.isCarryingChest()).thenReturn(true);
        when(horse.isValid()).thenReturn(true);
        when(PaperAdapter.ADAPTER.getHolder(inventory, false)).thenReturn(horse);
        when(inventory.getContents()).thenAnswer(invocation -> ItemUtils.getContainerState(contents));
        when(inventory.getSize()).thenReturn(3);
        when(PaperAdapter.ADAPTER.executeEntityTask(any(), eq(horse), any(), any(), eq(1L))).thenAnswer(invocation -> {
            scheduled = invocation.getArgument(2);
            retired = invocation.getArgument(3);
            return true;
        });
        queue.when(() -> Queue.queueEntityContainerTransaction(anyString(), eq(horse), any(ItemStack[].class), any(ItemStack[].class))).thenAnswer(invocation -> {
            transfers.add(new Transfer(invocation.getArgument(0), invocation.getArgument(2), invocation.getArgument(3)));
            return null;
        });
        setAmount(4);
    }

    @AfterEach
    void tearDown() throws Exception {
        pending().clear();
        PaperAdapter.ADAPTER = previousAdapter;
        queue.close();
        tracking.close();
        plugin.close();
        configs.close();
    }

    private Map<?, ?> pending() throws Exception {
        Field field = InventoryChangeListener.class.getDeclaredField("pendingEntityTransactions");
        field.setAccessible(true);
        return (Map<?, ?>) field.get(null);
    }

    private void setAmount(int amount) {
        contents = new ItemStack[] {null, null, amount == 0 ? null : new TestItemStack(Material.DIAMOND, amount)};
    }

    private void click(String name) {
        Player player = mock(Player.class);
        when(player.getName()).thenReturn(name);
        InventoryClickEvent event = mock(InventoryClickEvent.class);
        when(event.getWhoClicked()).thenReturn(player);
        when(event.getInventory()).thenReturn(inventory);
        when(event.getAction()).thenReturn(InventoryAction.PICKUP_ONE);
        new InventoryChangeListener().onHorseInventoryClick(event);
    }

    private void close() {
        InventoryCloseEvent event = mock(InventoryCloseEvent.class);
        when(event.getInventory()).thenReturn(inventory);
        new InventoryChangeListener().onInventoryClose(event);
    }

    @Test
    void secondViewerClosingDoesNotGetCreditForFirstPlayersRemoval() {
        click("Alice");
        setAmount(3);
        close(); // Bob's close flushes Alice's pending action, retaining the actor.
        close();
        assertEquals(1, transfers.size());
        assertEquals("Alice", transfers.get(0).user);
        assertEquals(4, transfers.get(0).before[2].getAmount());
        assertEquals(3, transfers.get(0).after[2].getAmount());
    }

    @Test
    void twoActorsInOneTickHaveSeparateDeltas() {
        click("Alice");
        setAmount(3);
        click("Bob");
        setAmount(2);
        scheduled.run();
        assertEquals(2, transfers.size());
        assertEquals("Alice", transfers.get(0).user);
        assertEquals("Bob", transfers.get(1).user);
        assertEquals(3, transfers.get(0).after[2].getAmount());
        assertEquals(3, transfers.get(1).before[2].getAmount());
        assertEquals(2, transfers.get(1).after[2].getAmount());
    }

    @Test
    void sameActorsRemovalAndInsertionInOneTickAreBothPreserved() {
        click("Alice");
        setAmount(3);
        click("Alice");
        setAmount(4);
        scheduled.run();
        assertEquals(2, transfers.size());
        assertEquals(3, transfers.get(0).after[2].getAmount());
        assertEquals(3, transfers.get(1).before[2].getAmount());
    }

    @Test
    void unchangedActionDoesNotCreateTransaction() {
        click("Alice");
        scheduled.run();
        assertTrue(transfers.isEmpty());
    }

    @Test
    void cancelledClickCannotTakeOwnershipOfPendingTransfer() {
        click("Alice");
        setAmount(3);
        InventoryClickEvent event = mock(InventoryClickEvent.class);
        when(event.isCancelled()).thenReturn(true);
        new InventoryChangeListener().onHorseInventoryClick(event);
        scheduled.run();
        assertEquals(1, transfers.size());
        assertEquals("Alice", transfers.get(0).user);
    }

    @Test
    void dragIntoHorseStorageRecordsItsActor() {
        Player player = mock(Player.class);
        when(player.getName()).thenReturn("Alice");
        InventoryDragEvent event = mock(InventoryDragEvent.class);
        when(event.getWhoClicked()).thenReturn(player);
        when(event.getInventory()).thenReturn(inventory);
        when(event.getRawSlots()).thenReturn(Collections.singleton(2));
        new InventoryChangeListener().onHorseInventoryDrag(event);
        setAmount(5);
        scheduled.run();
        assertEquals(1, transfers.size());
        assertEquals("Alice", transfers.get(0).user);
        assertEquals(5, transfers.get(0).after[2].getAmount());
    }

    @Test
    void damageFlushesTransferBeforeDeathDropsEmptyInventory() {
        click("Alice");
        setAmount(3);
        EntityDamageEvent event = mock(EntityDamageEvent.class);
        when(event.getEntity()).thenReturn(horse);
        new InventoryChangeListener().onHorseDamage(event);
        setAmount(0);
        close();
        scheduled.run();
        assertEquals(1, transfers.size());
        assertEquals(3, transfers.get(0).after[2].getAmount());
    }

    @Test
    void completedTransferSurvivesChestRemovalBeforeFlush() {
        click("Alice");
        setAmount(3);
        when(horse.isCarryingChest()).thenReturn(false);
        InventoryChangeListener.flushEntityContainer(horse);
        assertEquals(1, transfers.size());
    }

    @Test
    void retirementAfterInvalidationPreservesCompletedTransfer() throws Exception {
        click("Alice");
        setAmount(3);
        when(horse.isValid()).thenReturn(false);
        retired.run();
        assertEquals(1, transfers.size());
        assertTrue(pending().isEmpty());
    }

    @Test
    void shutdownFlushesWithoutWaitingForInventoryClose() throws Exception {
        click("Alice");
        setAmount(3);
        InventoryChangeListener.flushPendingTransactionsForShutdown();
        assertEquals(1, transfers.size());
        assertTrue(pending().isEmpty());
        scheduled.run();
        assertEquals(1, transfers.size());
    }

    @Test
    void oldRetirementCallbackCannotFlushNewAction() {
        click("Alice");
        setAmount(3);
        Runnable oldRetired = retired;
        close();
        click("Bob");
        setAmount(2);
        oldRetired.run();
        assertEquals(1, transfers.size());
        scheduled.run();
        assertEquals(2, transfers.size());
    }

    private static final class Transfer {
        private final String user;
        private final ItemStack[] before;
        private final ItemStack[] after;

        private Transfer(String user, ItemStack[] before, ItemStack[] after) {
            this.user = user;
            this.before = ItemUtils.getContainerState(before);
            this.after = ItemUtils.getContainerState(after);
        }
    }
}
