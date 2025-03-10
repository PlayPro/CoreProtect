package net.coreprotect.bukkit;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.Keyed;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.entity.EntityType;

import net.coreprotect.model.BlockGroup;

/**
 * Bukkit adapter implementation for Minecraft 1.21.
 * Provides version-specific implementations for the BukkitInterface
 * to handle features introduced in the 1.21 update, including:
 * - New block types (crafter)
 * - Registry handling for named objects
 * - Updated interaction blocks
 */
public class Bukkit_v1_21 extends Bukkit_v1_20 implements BukkitInterface {

    /**
     * Initializes the Bukkit_v1_21 adapter with 1.21-specific block groups and mappings.
     * Sets up collections of blocks with similar behavior for efficient handling.
     */
    public Bukkit_v1_21() {
        initializeBlockGroups();
        initializeTrapdoorBlocks();
    }

    /**
     * Initializes the block groups specific to Minecraft 1.21.
     * Updates container, interaction, and update state blocks with new 1.21 blocks.
     */
    private void initializeBlockGroups() {
        // Container blocks in 1.21 (added CRAFTER)
        BlockGroup.CONTAINERS = new HashSet<>(Arrays.asList(Material.JUKEBOX, Material.DISPENSER, Material.CHEST, Material.FURNACE, Material.BREWING_STAND, Material.TRAPPED_CHEST, Material.HOPPER, Material.DROPPER, Material.ARMOR_STAND, Material.ITEM_FRAME, Material.SHULKER_BOX, Material.BLACK_SHULKER_BOX, Material.BLUE_SHULKER_BOX, Material.BROWN_SHULKER_BOX, Material.CYAN_SHULKER_BOX, Material.GRAY_SHULKER_BOX, Material.GREEN_SHULKER_BOX, Material.LIGHT_BLUE_SHULKER_BOX, Material.LIME_SHULKER_BOX, Material.MAGENTA_SHULKER_BOX, Material.ORANGE_SHULKER_BOX, Material.PINK_SHULKER_BOX, Material.PURPLE_SHULKER_BOX, Material.RED_SHULKER_BOX, Material.LIGHT_GRAY_SHULKER_BOX, Material.WHITE_SHULKER_BOX, Material.YELLOW_SHULKER_BOX, Material.BARREL, Material.BLAST_FURNACE, Material.SMOKER, Material.LECTERN, Material.CHISELED_BOOKSHELF, Material.DECORATED_POT, Material.CRAFTER));

        // Interaction blocks in 1.21 (comprehensive list including CRAFTER)
        BlockGroup.INTERACT_BLOCKS = new HashSet<>(Arrays.asList(Material.SPRUCE_FENCE_GATE, Material.BIRCH_FENCE_GATE, Material.JUNGLE_FENCE_GATE, Material.DARK_OAK_FENCE_GATE, Material.MANGROVE_FENCE_GATE, Material.ACACIA_FENCE_GATE, Material.DISPENSER, Material.NOTE_BLOCK, Material.CHEST, Material.FURNACE, Material.LEVER, Material.REPEATER, Material.MANGROVE_TRAPDOOR, Material.ACACIA_TRAPDOOR, Material.BIRCH_TRAPDOOR, Material.DARK_OAK_TRAPDOOR, Material.JUNGLE_TRAPDOOR, Material.SPRUCE_TRAPDOOR, Material.OAK_TRAPDOOR, Material.OAK_FENCE_GATE, Material.BREWING_STAND, Material.ANVIL, Material.CHIPPED_ANVIL, Material.DAMAGED_ANVIL, Material.ENDER_CHEST, Material.TRAPPED_CHEST, Material.COMPARATOR, Material.HOPPER, Material.DROPPER, Material.SHULKER_BOX, Material.BLACK_SHULKER_BOX, Material.BLUE_SHULKER_BOX, Material.BROWN_SHULKER_BOX, Material.CYAN_SHULKER_BOX, Material.GRAY_SHULKER_BOX, Material.GREEN_SHULKER_BOX, Material.LIGHT_BLUE_SHULKER_BOX, Material.LIME_SHULKER_BOX, Material.MAGENTA_SHULKER_BOX, Material.ORANGE_SHULKER_BOX, Material.PINK_SHULKER_BOX, Material.PURPLE_SHULKER_BOX, Material.RED_SHULKER_BOX, Material.LIGHT_GRAY_SHULKER_BOX, Material.WHITE_SHULKER_BOX, Material.YELLOW_SHULKER_BOX, Material.BARREL, Material.BLAST_FURNACE, Material.GRINDSTONE, Material.LOOM, Material.SMOKER, Material.CRAFTING_TABLE, Material.CARTOGRAPHY_TABLE, Material.ENCHANTING_TABLE, Material.SMITHING_TABLE, Material.STONECUTTER, Material.CRIMSON_FENCE_GATE, Material.WARPED_FENCE_GATE, Material.CRIMSON_TRAPDOOR, Material.WARPED_TRAPDOOR, Material.CRAFTER));

        // Update state blocks in 1.21 (added CRAFTER to redstone responsive blocks)
        BlockGroup.UPDATE_STATE = new HashSet<>(Arrays.asList(Material.TORCH, Material.WALL_TORCH, Material.REDSTONE_WIRE, Material.RAIL, Material.POWERED_RAIL, Material.DETECTOR_RAIL, Material.FURNACE, Material.BLAST_FURNACE, Material.SMOKER, Material.LEVER, Material.REDSTONE_TORCH, Material.REDSTONE_WALL_TORCH, Material.GLOWSTONE, Material.JACK_O_LANTERN, Material.REPEATER, Material.REDSTONE_LAMP, Material.BEACON, Material.COMPARATOR, Material.DAYLIGHT_DETECTOR, Material.REDSTONE_BLOCK, Material.HOPPER, Material.CHEST, Material.TRAPPED_CHEST, Material.ACTIVATOR_RAIL, Material.SOUL_TORCH, Material.SOUL_WALL_TORCH, Material.SHROOMLIGHT, Material.RESPAWN_ANCHOR, Material.CRYING_OBSIDIAN, Material.TARGET, Material.SMALL_AMETHYST_BUD, Material.MEDIUM_AMETHYST_BUD, Material.LARGE_AMETHYST_BUD, Material.AMETHYST_CLUSTER, Material.CAVE_VINES, Material.CAVE_VINES_PLANT, Material.GLOW_LICHEN, Material.LIGHT, Material.LAVA_CAULDRON, Material.CHISELED_BOOKSHELF, Material.CRAFTER));
    }

    /**
     * Initializes trapdoor blocks for interaction handling.
     * Excludes iron trapdoors as they cannot be directly interacted with.
     */
    private void initializeTrapdoorBlocks() {
        for (Material value : Tag.TRAPDOORS.getValues()) {
            // Iron trapdoors are not directly interactable
            if (value == Material.IRON_TRAPDOOR) {
                continue;
            }

            // Add to interaction blocks if not already present
            addToBlockGroupIfMissing(value, BlockGroup.INTERACT_BLOCKS);
            addToBlockGroupIfMissing(value, BlockGroup.SAFE_INTERACT_BLOCKS);
        }
    }

    /**
     * Helper method to add a block to a block group if it's not already present.
     * 
     * @param block
     *            The block to add
     * @param group
     *            The group to add the block to
     */
    private void addToBlockGroupIfMissing(Material block, Set<Material> group) {
        if (!group.contains(block)) {
            group.add(block);
        }
    }

    /**
     * Gets the EntityType corresponding to a Material.
     * Maps Material to its equivalent EntityType for entity handling.
     * 
     * @param material
     *            The material to convert
     * @return The corresponding EntityType, or UNKNOWN if not mappable
     */
    @Override
    public EntityType getEntityType(Material material) {
        switch (material) {
            case END_CRYSTAL:
                return EntityType.valueOf("END_CRYSTAL");
            default:
                return EntityType.UNKNOWN;
        }
    }

    /**
     * Gets a registry key string from a keyed object.
     * Used for serializing objects that implement Keyed.
     * 
     * @param value
     *            The keyed object
     * @return The string representation of the registry key
     */
    @Override
    public Object getRegistryKey(Object value) {
        return ((Keyed) value).getKey().toString();
    }

    /**
     * Gets a registry value from a key string and class.
     * Used for deserializing registry objects.
     * 
     * @param key
     *            The registry key as a string
     * @param tClass
     *            The class of the registry
     * @return The registry value
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Override
    public Object getRegistryValue(String key, Object tClass) {
        NamespacedKey namespacedKey = NamespacedKey.fromString(key);
        // return RegistryAccess.registryAccess().getRegistry(RegistryKey.CAT_VARIANT).get((NamespacedKey)value);
        return Bukkit.getRegistry((Class) tClass).get(namespacedKey);
    }
}
