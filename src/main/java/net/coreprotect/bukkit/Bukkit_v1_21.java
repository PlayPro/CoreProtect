package net.coreprotect.bukkit;

import java.util.List;
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
        addToBlockGroupIfMissing(Material.CRAFTER, BlockGroup.CONTAINERS);
        addToBlockGroupIfMissing(Material.CRAFTER, BlockGroup.INTERACT_BLOCKS);
        addToBlockGroupIfMissing(Material.CRAFTER, BlockGroup.UPDATE_STATE);
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

    /**
     * Gets the wolf variant and adds it to the info list.
     * This functionality is specific to Minecraft 1.21+.
     * 
     * @param wolf
     *            The wolf entity
     * @param info
     *            The list to add the variant information to
     */
    @Override
    public void getWolfVariant(org.bukkit.entity.Wolf wolf, List<Object> info) {
        // Add the variant to the info list
        info.add(getRegistryKey(wolf.getVariant()));
    }

    /**
     * Sets the wolf variant from the provided value.
     * This functionality is specific to Minecraft 1.21+.
     * 
     * @param wolf
     *            The wolf entity
     * @param value
     *            The variant value to set
     */
    @Override
    public void setWolfVariant(org.bukkit.entity.Wolf wolf, Object value) {
        if (value instanceof String) {
            value = getRegistryValue((String) value, org.bukkit.entity.Wolf.Variant.class);
        }
        org.bukkit.entity.Wolf.Variant variant = (org.bukkit.entity.Wolf.Variant) value;
        wolf.setVariant(variant);

    }
}
