package net.coreprotect.utility;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;

import net.coreprotect.CoreProtect;
import net.coreprotect.bukkit.BukkitAdapter;
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.language.Phrase;
import net.coreprotect.model.BlockGroup;
import net.coreprotect.paper.PaperAdapter;
import net.coreprotect.thread.Scheduler;

public class Teleport {

    private Teleport() {
        throw new IllegalStateException("Utility class");
    }

    public static ConcurrentHashMap<Location, BlockData> revertBlocks = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Location, Long> REVERT_TOKENS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Location, Material> SAFETY_MATERIALS = new ConcurrentHashMap<>();
    private static final AtomicLong REVERT_SEQUENCE = new AtomicLong(0L);
    private static final Set<Material> UNSAFE_BLOCKS = createUnsafeBlocks();

    private static Set<Material> createUnsafeBlocks() {
        Set<Material> unsafe = new HashSet<>(Arrays.asList(
                Material.LAVA,
                Material.CAMPFIRE,
                Material.SOUL_CAMPFIRE,
                Material.MAGMA_BLOCK,
                Material.CACTUS,
                Material.SWEET_BERRY_BUSH,
                Material.WITHER_ROSE,
                Material.END_PORTAL,
                Material.END_GATEWAY,
                Material.NETHER_PORTAL));
        unsafe.addAll(BlockGroup.FIRE);
        addMaterialIfPresent(unsafe, "POWDER_SNOW");
        addMaterialIfPresent(unsafe, "POINTED_DRIPSTONE");
        return Collections.unmodifiableSet(unsafe);
    }

    private static void addMaterialIfPresent(Set<Material> target, String materialName) {
        Material material = Material.getMaterial(materialName);
        if (material != null) {
            target.add(material);
        }
    }

    private static boolean isUnsafeBlock(Material type) {
        return type != null && UNSAFE_BLOCKS.contains(type);
    }

    private static boolean isSafeLiquid(Material type) {
        return type == Material.WATER || type == Material.BUBBLE_COLUMN;
    }

    private static boolean isVulnerablePlayer(Player player) {
        GameMode gameMode = player.getGameMode();
        return gameMode != GameMode.CREATIVE && gameMode != GameMode.SPECTATOR && !player.isFlying();
    }

    private static Material defaultSafetyMaterial() {
        return ConfigHandler.isFolia ? Material.DIRT : Material.BARRIER;
    }

    public static void performSafeTeleport(Player player, Location location, boolean enforceTeleport) {
        try {
            if (player == null || location == null || location.getWorld() == null) {
                return;
            }

            Location targetLocation = location.clone();
            World world = targetLocation.getWorld();
            int minHeight = BukkitAdapter.ADAPTER != null ? BukkitAdapter.ADAPTER.getMinHeight(world) : 0;
            int worldHeight = world.getMaxHeight();
            int playerX = targetLocation.getBlockX();
            int playerY = targetLocation.getBlockY();
            int playerZ = targetLocation.getBlockZ();
            int checkY = Math.max(minHeight, Math.min(playerY - 1, worldHeight));
            boolean safeBlock = false;
            boolean alert = false;

            while (!safeBlock) {
                int above = Math.min(checkY + 1, worldHeight);

                Block block1 = world.getBlockAt(playerX, checkY, playerZ);
                Block block2 = world.getBlockAt(playerX, above, playerZ);
                Material type1 = block1.getType();
                Material type2 = block2.getType();

                if (BlockUtils.passableBlock(block1) && BlockUtils.passableBlock(block2) && !isUnsafeBlock(type1) && !isUnsafeBlock(type2)) {
                    int below = checkY - 1;
                    Block blockBelow = below >= minHeight ? world.getBlockAt(playerX, below, playerZ) : null;
                    boolean belowHazardous = blockBelow == null
                            || isUnsafeBlock(blockBelow.getType())
                            || (enforceTeleport && BlockUtils.passableBlock(blockBelow) && !isSafeLiquid(blockBelow.getType()));

                    if (belowHazardous && isVulnerablePlayer(player)) {
                        if (checkY < worldHeight) {
                            boolean headClear = checkY + 2 >= worldHeight;
                            if (!headClear) {
                                Block blockAboveHead = world.getBlockAt(playerX, checkY + 2, playerZ);
                                headClear = BlockUtils.passableBlock(blockAboveHead) && !isUnsafeBlock(blockAboveHead.getType());
                            }
                            if (headClear) {
                                Material safetyMaterial = defaultSafetyMaterial();
                                Location revertLocation = block1.getLocation();
                                BlockData originalBlockData = block1.getBlockData().clone();
                                revertBlocks.computeIfAbsent(revertLocation, key -> originalBlockData);
                                long token = REVERT_SEQUENCE.incrementAndGet();
                                REVERT_TOKENS.put(revertLocation, token);
                                SAFETY_MATERIALS.put(revertLocation, safetyMaterial);
                                if (block1.getType() != safetyMaterial) {
                                    block1.setType(safetyMaterial);
                                }
                                alert = true;
                                checkY++;
                                safeBlock = true;

                                Scheduler.scheduleSyncDelayedTask(CoreProtect.getInstance(), () -> revertBlockIfUnchanged(revertLocation, token), revertLocation, 1200);
                            }
                        }
                    }
                    else {
                        safeBlock = true;
                    }
                }

                if (!safeBlock && (checkY >= worldHeight || player.getGameMode() == GameMode.SPECTATOR)) {
                    safeBlock = true;

                    if (checkY < worldHeight) {
                        checkY++;
                    }
                }

                if (safeBlock && (checkY > playerY || enforceTeleport || alert)) {
                    if (checkY > worldHeight) {
                        checkY = worldHeight;
                    }

                    if (checkY != playerY || enforceTeleport) {
                        double oldY = targetLocation.getY();
                        targetLocation.setY(checkY);
                        if (ConfigHandler.isFolia) {
                            PaperAdapter.ADAPTER.teleportAsync(player, targetLocation);
                        }
                        else {
                            player.teleport(targetLocation);
                        }

                        if (!enforceTeleport) {
                            // Only send a message if the player was moved by at least 1 block
                            if (targetLocation.getY() >= (oldY + 1.00)) {
                                Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.TELEPORTED_SAFETY));
                            }
                        }
                        else {
                            Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.TELEPORTED, "x" + playerX + "/y" + checkY + "/z" + playerZ + "/" + world.getName()));
                        }
                    }
                    if (alert) {
                        Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + Color.ITALIC + "- " + Phrase.build(Phrase.DIRT_BLOCK));
                    }
                }

                checkY++;
            }
        }
        catch (Exception e) {
            ErrorReporter.report(e);
        }
    }

    private static void revertBlockIfUnchanged(Location revertLocation, long expectedToken) {
        if (revertLocation == null || revertLocation.getWorld() == null) {
            return;
        }
        Long currentToken = REVERT_TOKENS.get(revertLocation);
        if (currentToken == null || currentToken.longValue() != expectedToken) {
            return;
        }
        if (!REVERT_TOKENS.remove(revertLocation, currentToken)) {
            return;
        }
        BlockData revertData = revertBlocks.remove(revertLocation);
        Material safetyMaterial = SAFETY_MATERIALS.remove(revertLocation);
        if (revertData == null) {
            return;
        }
        Material expectedSafety = safetyMaterial != null ? safetyMaterial : defaultSafetyMaterial();
        Block block = revertLocation.getBlock();
        if (block.getType() == expectedSafety) {
            block.setBlockData(revertData);
        }
    }

    public static void revertAllBlocks() {
        for (Location revertLocation : new ArrayList<>(revertBlocks.keySet())) {
            if (revertLocation == null || revertLocation.getWorld() == null) {
                revertBlocks.remove(revertLocation);
                REVERT_TOKENS.remove(revertLocation);
                SAFETY_MATERIALS.remove(revertLocation);
                continue;
            }
            if (PaperAdapter.ADAPTER != null && !PaperAdapter.ADAPTER.isOwnedByCurrentRegion(
                    revertLocation.getWorld(), revertLocation.getBlockX() >> 4, revertLocation.getBlockZ() >> 4)) {
                Long token = REVERT_TOKENS.get(revertLocation);
                CoreProtect plugin = CoreProtect.getInstance();
                if (token != null && plugin != null && plugin.isEnabled()) {
                    try {
                        Scheduler.runTask(plugin, () -> revertBlockIfUnchanged(revertLocation, token), revertLocation);
                        continue;
                    }
                    catch (Exception e) {
                        // Fall through to clean maps if scheduler is unavailable during shutdown
                    }
                }
                revertBlocks.remove(revertLocation);
                REVERT_TOKENS.remove(revertLocation);
                SAFETY_MATERIALS.remove(revertLocation);
                continue;
            }
            BlockData revertData = revertBlocks.remove(revertLocation);
            REVERT_TOKENS.remove(revertLocation);
            Material safetyMaterial = SAFETY_MATERIALS.remove(revertLocation);
            if (revertData == null) {
                continue;
            }
            Material expectedSafety = safetyMaterial != null ? safetyMaterial : defaultSafetyMaterial();
            Block block = revertLocation.getBlock();
            if (block.getType() == expectedSafety) {
                block.setBlockData(revertData);
            }
        }
    }

}
