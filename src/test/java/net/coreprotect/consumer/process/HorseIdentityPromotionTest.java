package net.coreprotect.consumer.process;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import net.coreprotect.model.entity.EntityContainerTransaction;
import net.coreprotect.consumer.Consumer;
import net.coreprotect.utility.EntitySpawnTracking;

class HorseIdentityPromotionTest {
    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void permanentPreparationFailureReleasesUnprocessedPromotion() throws Exception {
        UUID uuid = UUID.randomUUID();
        Location location = new Location(mock(World.class), 1, 2, 3);
        EntityContainerTransaction transaction = new EntityContainerTransaction(uuid, location, new ItemStack[0], new ItemStack[0]).withIdentityPromotion(true);
        Object[] event = {1, Process.ENTITY_CONTAINER_TRANSACTION};
        ArrayList<Object[]> events = new ArrayList<>();
        events.add(event);
        Map<Integer, String[]> users = new HashMap<>();
        users.put(1, new String[] {"Alice"});
        Map<Integer, Object> objects = new HashMap<>();
        objects.put(1, transaction);
        String[] mapNames = {"consumerStrings", "consumerSigns", "consumerContainers", "consumerInventories", "consumerBlockList", "consumerObjectArrayList", "consumerObjectList"};
        for (String name : mapNames) {
            ((Map) Consumer.class.getField(name).get(null)).put(99, new HashMap<>());
        }
        try (MockedStatic<EntitySpawnTracking> tracking = mockStatic(EntitySpawnTracking.class)) {
            Method method = Process.class.getDeclaredMethod("discardFailedConsumerData", int.class, ArrayList.class, Map.class, Map.class, Object[].class, Exception.class);
            method.setAccessible(true);
            method.invoke(null, 99, events, users, objects, event, new IllegalArgumentException("bad preparation"));
            tracking.verify(() -> EntitySpawnTracking.cancelDatabaseIdentityPromotion(uuid, location));
            assertTrue(events.isEmpty());
            assertTrue(objects.isEmpty());
            assertTrue(users.isEmpty());
        }
        finally {
            for (String name : mapNames) {
                ((Map) Consumer.class.getField(name).get(null)).remove(99);
            }
        }
    }

    @Test
    void discardedRetryRequiredEntriesReleaseEveryPromotionClaim() throws Exception {
        UUID uuid = UUID.randomUUID();
        Location location = new Location(mock(World.class), 1, 2, 3);
        EntityContainerTransaction transaction = new EntityContainerTransaction(uuid, location, new ItemStack[0], new ItemStack[0]).withIdentityPromotion(true);
        List<Object> pending = new ArrayList<>();
        pending.add(pending(transaction, true));
        pending.add(pending(transaction, false));
        try (MockedStatic<EntitySpawnTracking> tracking = mockStatic(EntitySpawnTracking.class)) {
            complete(pending, "DISCARDED");
            tracking.verify(() -> EntitySpawnTracking.verifyPendingDatabaseIdentity(uuid, location), times(1));
            tracking.verify(() -> EntitySpawnTracking.cancelDatabaseIdentityPromotion(uuid, location), times(2));
            assertTrue(pending.isEmpty());
        }
    }

    @Test
    void failedVerificationStillReleasesDiscardedClaim() throws Exception {
        UUID uuid = UUID.randomUUID();
        Location location = new Location(mock(World.class), 1, 2, 3);
        EntityContainerTransaction transaction = new EntityContainerTransaction(uuid, location, new ItemStack[0], new ItemStack[0]).withIdentityPromotion(true);
        List<Object> pending = new ArrayList<>();
        pending.add(pending(transaction, true));
        try (MockedStatic<EntitySpawnTracking> tracking = mockStatic(EntitySpawnTracking.class);
                MockedStatic<net.coreprotect.utility.ErrorReporter> errors = mockStatic(net.coreprotect.utility.ErrorReporter.class)) {
            tracking.when(() -> EntitySpawnTracking.verifyPendingDatabaseIdentity(uuid, location)).thenThrow(new IllegalStateException("unavailable"));
            complete(pending, "DISCARDED");
            tracking.verify(() -> EntitySpawnTracking.cancelDatabaseIdentityPromotion(uuid, location));
            assertTrue(pending.isEmpty());
        }
    }

    private Object pending(EntityContainerTransaction transaction, boolean retryRequired) throws Exception {
        Class<?> type = Class.forName(Process.class.getName() + "$PendingEntityContainerTransaction");
        Constructor<?> constructor = type.getDeclaredConstructor(String.class, EntityContainerTransaction.class, boolean.class, boolean.class);
        constructor.setAccessible(true);
        return constructor.newInstance("Alice", transaction, false, retryRequired);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void complete(List<Object> pending, String outcomeName) throws Exception {
        Class<? extends Enum> outcome = (Class<? extends Enum>) Class.forName(Process.class.getName() + "$TransactionOutcome");
        Method method = Process.class.getDeclaredMethod("completeEntityContainerTransactions", List.class, outcome);
        method.setAccessible(true);
        method.invoke(null, pending, Enum.valueOf(outcome, outcomeName));
    }
}
