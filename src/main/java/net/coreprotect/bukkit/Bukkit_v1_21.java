package net.coreprotect.bukkit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.bukkit.Art;
import org.bukkit.Bukkit;
import org.bukkit.ExplosionResult;
import org.bukkit.Keyed;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockType;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.SideChaining;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Painting;
import org.bukkit.event.Event;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;


import net.coreprotect.model.BlockGroup;
import net.coreprotect.utility.AdapterMethods;
import net.coreprotect.utility.BlockTypeUtils;

/**
 * Bukkit adapter implementation for Minecraft 1.21.
 * Provides version-specific implementations for the BukkitInterface
 * to handle features introduced in the 1.21 update, including:
 * - New block types (crafter)
 * - Registry handling for named objects
 * - Updated interaction blocks
 */
public class Bukkit_v1_21 extends Bukkit_v1_20 {

    private final Set<Material> copperChests = blockMaterials("copper_chests", "COPPER_CHEST");
    private final Set<Material> shelves = blockMaterials("wooden_shelves", "_SHELF");

    @Override
    public boolean hasBodyItemSlot() {
        return true;
    }

    @Override
    public ItemStack getBodyItem(EntityEquipment equipment) {
        return equipment.getItem(EquipmentSlot.BODY);
    }

    @Override
    public void setBodyItem(EntityEquipment equipment, ItemStack item) {
        equipment.setItem(EquipmentSlot.BODY, item);
    }

    /**
     * Initializes the Bukkit_v1_21 adapter with 1.21-specific block groups and mappings.
     * Sets up collections of blocks with similar behavior for efficient handling.
     */
    public Bukkit_v1_21() {
        initializeBlockGroups();
        BlockGroup.LANTERNS.addAll(blockMaterials("lanterns", "COPPER_LANTERN"));
        initializeTrapdoorBlocks();
        BlockGroup.INTERACT_BLOCKS.addAll(copperChestMaterials());
        BlockGroup.CONTAINERS.addAll(copperChestMaterials());
        BlockGroup.UPDATE_STATE.addAll(copperChestMaterials());
        BlockGroup.CONTAINERS.addAll(shelfMaterials());
    }

    /**
     * Initializes the block groups specific to Minecraft 1.21.
     * Updates container, interaction, and update state blocks with new 1.21 blocks.
     */
    private void initializeBlockGroups() {
        BlockGroup.CONTAINERS.add(Material.CRAFTER);
        BlockGroup.INTERACT_BLOCKS.add(Material.CRAFTER);
        BlockGroup.UPDATE_STATE.add(Material.CRAFTER);

        Material paleHangingMoss = Material.getMaterial("PALE_HANGING_MOSS");
        if (paleHangingMoss != null) {
            BlockGroup.TRACK_BOTTOM.add(paleHangingMoss);
            BlockGroup.VERTICAL_BOTTOM.add(paleHangingMoss);
            BlockGroup.NON_ATTACHABLE.add(paleHangingMoss);
        }

        Material resinClump = Material.getMaterial("RESIN_CLUMP");
        if (resinClump != null) {
            BlockGroup.TRACK_ANY.add(resinClump);
            BlockGroup.NON_ATTACHABLE.add(resinClump);
        }

        Material copperTorch = Material.getMaterial("COPPER_TORCH");
        if (copperTorch != null) {
            BlockGroup.TRACK_TOP.add(copperTorch);
            BlockGroup.NON_ATTACHABLE.add(copperTorch);
            BlockGroup.UPDATE_STATE.add(copperTorch);
        }
        Material copperWallTorch = Material.getMaterial("COPPER_WALL_TORCH");
        if (copperWallTorch != null) {
            BlockGroup.TRACK_SIDE.add(copperWallTorch);
            BlockGroup.NON_ATTACHABLE.add(copperWallTorch);
            BlockGroup.UPDATE_STATE.add(copperWallTorch);
        }
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
            BlockGroup.INTERACT_BLOCKS.add(value);
            BlockGroup.SAFE_INTERACT_BLOCKS.add(value);
        }
    }

    @Override
    public boolean hasBlockType(String key) {
        return getBlockType(key) != null;
    }

    @Override
    public BlockData createBlockData(String key) {
        try {
            BlockType blockType = getBlockType(key);
            return blockType == null ? null : blockType.createBlockData();
        }
        catch (Exception e) {
            return null;
        }
    }

    @Override
    public BlockData createBlockDataFromString(String blockData) {
        try {
            BlockType blockType = getBlockType(BlockTypeUtils.getBlockDataKey(blockData));
            if (blockType == null) {
                return null;
            }

            String states = BlockTypeUtils.getBlockDataStates(blockData);
            return states == null ? blockType.createBlockData() : blockType.createBlockData(states);
        }
        catch (Exception e) {
            return null;
        }
    }

    private BlockType getBlockType(String key) {
        NamespacedKey namespacedKey = NamespacedKey.fromString(BlockTypeUtils.normalizeKey(key));
        return namespacedKey == null ? null : Registry.BLOCK.get(namespacedKey);
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
        if (!(value instanceof Keyed)) {
            return null;
        }
        try {
            NamespacedKey key = ((Keyed) value).getKey();
            return key == null ? null : key.toString();
        }
        catch (IllegalStateException e) {
            return null;
        }
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
        if (key == null || !(tClass instanceof Class<?>) || !Keyed.class.isAssignableFrom((Class<?>) tClass)) {
            return null;
        }
        NamespacedKey namespacedKey = NamespacedKey.fromString(key);
        if (namespacedKey == null) {
            return null;
        }
        Registry<?> registry = Bukkit.getRegistry((Class) tClass);
        return registry == null ? null : registry.get(namespacedKey);
    }

    @Override
    public String getPaintingArtKey(Painting painting) {
        try {
            NamespacedKey key = Registry.ART.getKey(painting.getArt());
            if (key != null) {
                return normalizePaintingArtKey(key.toString());
            }
        }
        catch (Exception e) {
        }

        return normalizePaintingArtKey(super.getPaintingArtKey(painting));
    }

    @Override
    public Art getPaintingArt(String name) {
        NamespacedKey key = NamespacedKey.fromString(normalizePaintingArtLookupKey(name));
        if (key != null) {
            Art art = Registry.ART.get(key);
            if (art != null) {
                return art;
            }
        }

        return super.getPaintingArt(name);
    }

    private String normalizePaintingArtKey(String name) {
        if (name == null) {
            return "";
        }

        String normalized = name.toLowerCase(Locale.ROOT).trim();
        if (normalized.startsWith("minecraft:")) {
            return normalized.substring("minecraft:".length());
        }

        return normalized;
    }

    private String normalizePaintingArtLookupKey(String name) {
        String normalized = normalizePaintingArtKey(name);
        return normalized.contains(":") ? normalized : "minecraft:" + normalized;
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
        org.bukkit.entity.Wolf.Variant variant = resolveRegistryValue(this, value, org.bukkit.entity.Wolf.Variant.class);
        if (variant != null) {
            wolf.setVariant(variant);
        }
    }

    @Override
    public boolean isCrafter(InventoryType type) {
        return type == InventoryType.CRAFTER;
    }

    @Override
    public boolean isCopperChest(Material material) {
        return material != Material.CHEST && copperChests.contains(material);
    }

    @Override
    public boolean isShelf(Material material) {
        return shelves.contains(material);
    }

    @Override
    public Set<Material> copperChestMaterials() {
        return copperChests;
    }

    @Override
    public Set<Material> shelfMaterials() {
        return shelves;
    }

    static Set<Material> blockMaterials(String tagName, String fallbackSuffix) {
        Set<Material> materials = new HashSet<>();
        Tag<Material> tag = getMaterialTag(Tag.REGISTRY_BLOCKS, tagName);
        if (tag != null) {
            materials.addAll(tag.getValues());
            materials.remove(null);
        }
        if (materials.isEmpty()) {
            addMaterialsEndingWith(materials, fallbackSuffix);
        }
        return Collections.unmodifiableSet(materials);
    }

    @Override
    public List<Location> getShelfInteractionLocations(Block block, BlockFace blockFace) {
        List<Location> locations = new ArrayList<>();
        BlockData blockData = block.getBlockData();
        BlockFace facing = getShelfFacing(blockData);
        if (facing == null) {
            locations.add(block.getLocation());
            return locations;
        }

        if (blockFace != facing) {
            return locations;
        }

        String sideChain = getShelfSideChain(blockData);
        if ("UNCONNECTED".equals(sideChain)) {
            locations.add(block.getLocation());
            return locations;
        }

        Block center = block;
        int directionX = facing.getModX();
        int directionZ = facing.getModZ();
        if ("LEFT".equals(sideChain)) {
            center = center.getRelative(directionZ, 0, -directionX);
        }
        else if ("RIGHT".equals(sideChain)) {
            center = center.getRelative(-directionZ, 0, directionX);
        }

        String centerSideChain = getShelfSideChain(center.getBlockData());
        if (centerSideChain == null) {
            locations.add(block.getLocation());
            return locations;
        }

        locations.add(center.getLocation());
        if (!"CENTER".equals(centerSideChain)) {
            locations.add(block.getLocation());
            return locations;
        }

        locations.add(center.getRelative(-directionZ, 0, directionX).getLocation());
        locations.add(center.getRelative(directionZ, 0, -directionX).getLocation());
        return locations;
    }

    private static Tag<Material> getMaterialTag(String registry, String key) {
        return Bukkit.getTag(registry, NamespacedKey.minecraft(key), Material.class);
    }

    private static void addMaterialsEndingWith(Set<Material> materials, String suffix) {
        for (Material material : Material.values()) {
            if (material.name().endsWith(suffix)) {
                materials.add(material);
            }
        }
    }

    private static BlockFace getShelfFacing(BlockData blockData) {
        return blockData instanceof Directional ? ((Directional) blockData).getFacing() : null;
    }

    private static String getShelfSideChain(BlockData blockData) {
        try {
            if (blockData instanceof SideChaining) {
                SideChaining.ChainPart part = ((SideChaining) blockData).getSideChain();
                return part == null ? null : part.name();
            }
        }
        catch (LinkageError e) {
        }
        try {
            Object part = AdapterMethods.invokeNoArguments(blockData, "getSideChain");
            return part instanceof Enum<?> ? ((Enum<?>) part).name() : null;
        }
        catch (ReflectiveOperationException | LinkageError e) {
            return null;
        }
    }

    @Override
    public boolean shouldLogExplosion(Event event){
        ExplosionResult result = null;

        if (event instanceof EntityExplodeEvent){
            result = ((EntityExplodeEvent)event).getExplosionResult();
        } else if (event instanceof BlockExplodeEvent){
            result = ((BlockExplodeEvent)event).getExplosionResult();
        }
        return !(result == ExplosionResult.KEEP ||
                 result == ExplosionResult.TRIGGER_BLOCK
        );
    }
}
