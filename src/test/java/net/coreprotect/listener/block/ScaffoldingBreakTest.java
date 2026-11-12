package net.coreprotect.listener.block;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.type.Scaffolding;
import org.bukkit.event.block.BlockFadeEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import net.coreprotect.config.Config;
import net.coreprotect.consumer.Queue;
import net.coreprotect.thread.CacheHandler;

class ScaffoldingBreakTest extends Queue {
    private World world;
    private Config config;
    private MockedStatic<Config> configs;
    private MockedStatic<Queue> queue;
    private final BlockFadeListener listener = new BlockFadeListener();

    @BeforeEach
    void setUp() {
        world = mock(World.class);
        when(world.getUID()).thenReturn(UUID.randomUUID());
        config = new Config();
        config.NATURAL_BREAK = true;
        configs = mockStatic(Config.class);
        configs.when(() -> Config.getConfig(any(World.class))).thenReturn(config);
        queue = mockStatic(Queue.class);
        CacheHandler.lookupCache.clear();
    }

    @AfterEach
    void tearDown() {
        queue.close();
        configs.close();
        CacheHandler.lookupCache.clear();
    }

    private Block scaffold(int x, int y, boolean waterlogged) {
        Block block = mock(Block.class);
        BlockState state = mock(BlockState.class);
        Scaffolding data = mock(Scaffolding.class);
        when(block.getWorld()).thenReturn(world);
        when(block.getLocation()).thenAnswer(invocation -> new Location(world, x, y, 0));
        when(block.getType()).thenReturn(Material.SCAFFOLDING);
        when(block.getState()).thenReturn(state);
        when(state.getBlockData()).thenReturn(data);
        when(data.isWaterlogged()).thenReturn(waterlogged);
        when(data.getAsString()).thenReturn("minecraft:scaffolding[waterlogged=" + waterlogged + "]");
        return block;
    }

    private BlockFadeEvent fade(Block block) {
        BlockState replacement = mock(BlockState.class);
        when(replacement.getType()).thenReturn(Material.AIR);
        return new BlockFadeEvent(block, replacement);
    }

    private void verifyBreak(String user, Block block) {
        BlockState state = block.getState();
        queue.verify(() -> queueBlockBreak(user, state, Material.SCAFFOLDING, state.getBlockData().getAsString(), 0));
    }

    @Test
    void supportBreakAttributesScaffoldingWithoutLoggingItPrematurely() {
        Block support = mock(Block.class);
        BlockState supportState = mock(BlockState.class);
        when(support.getType()).thenReturn(Material.STONE);
        when(support.getWorld()).thenReturn(world);
        when(support.getY()).thenReturn(64);
        when(support.getState()).thenReturn(supportState);

        Block air = mock(Block.class);
        when(air.getType()).thenReturn(Material.AIR);
        when(world.getBlockAt(any(Location.class))).thenReturn(air);
        Block above = scaffold(0, 65, false);
        when(world.getBlockAt(argThat((Location location) -> location.getBlockY() == 65))).thenReturn(above);

        BlockBreakListener.processBlockBreak(null, "Breaker", support, false, BlockUtil.NONE);
        queue.verifyNoInteractions();
        listener.onBlockFade(fade(above));
        verifyBreak("Breaker", above);
    }

    @Test
    void attributesEntireTowerToPlayerBreakingSupport() {
        track("Breaker", new Location(world, 0, 65, 0));
        for (int y = 65; y < 129; y++) {
            Block block = scaffold(0, y, false);
            listener.onBlockFade(fade(block));
            verifyBreak("Breaker", block);
        }
    }

    private static void track(String user, Location location) {
        BlockFadeListener.trackScaffoldingBreak(user, location);
    }

    @Test
    void propagatesAttributionToHorizontalDependents() {
        Block first = scaffold(0, 65, false);
        track("Breaker", first.getLocation());
        listener.onBlockFade(fade(first));
        Block side = scaffold(1, 65, false);
        listener.onBlockFade(fade(side));
        verifyBreak("Breaker", side);
    }

    @Test
    void trackingSurvivingScaffoldingDoesNotLogABreak() {
        track("Breaker", scaffold(0, 65, false).getLocation());
        queue.verifyNoInteractions();
    }

    @Test
    void cancelledCollapseDoesNotLogOrPropagate() {
        Block block = scaffold(0, 65, false);
        track("Breaker", block.getLocation());
        BlockFadeEvent event = fade(block);
        event.setCancelled(true);
        listener.onBlockFade(event);
        queue.verifyNoInteractions();
        Block above = scaffold(0, 66, false);
        listener.onBlockFade(fade(above));
        verifyBreak("#physics", above);
    }

    @Test
    void respectsNaturalBreakSetting() {
        config.NATURAL_BREAK = false;
        listener.onBlockFade(fade(scaffold(0, 65, false)));
        queue.verifyNoInteractions();
    }

    @Test
    void ignoresStateChangesThatKeepTheScaffolding() {
        Block block = scaffold(0, 65, false);
        BlockState replacement = mock(BlockState.class);
        when(replacement.getType()).thenReturn(Material.SCAFFOLDING);
        listener.onBlockFade(new BlockFadeEvent(block, replacement));
        queue.verifyNoInteractions();
    }

    @Test
    void ignoresExpiredAttribution() {
        Block block = scaffold(0, 65, false);
        track("OldPlayer", block.getLocation());
        CacheHandler.lookupCache.values().forEach(value -> value[0] = (int) (System.currentTimeMillis() / 1000L) - 31);
        listener.onBlockFade(fade(block));
        verifyBreak("#physics", block);
    }

    @Test
    void preservesWaterReleasedByWaterloggedScaffolding() {
        Block block = scaffold(0, 65, true);
        track("Breaker", block.getLocation());
        listener.onBlockFade(fade(block));
        verifyBreak("Breaker", block);
        queue.verify(() -> queueBlockPlace("Breaker", block.getState(), Material.SCAFFOLDING, null, Material.WATER, -1, 0, null));
    }

    @Test
    void dryScaffoldingDoesNotLogWater() {
        Block block = scaffold(0, 65, false);
        listener.onBlockFade(fade(block));
        queue.verify(() -> queueBlockPlace("#physics", block.getState(), Material.SCAFFOLDING, null, Material.WATER, -1, 0, null), never());
    }
}
