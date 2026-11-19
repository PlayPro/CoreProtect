package net.coreprotect.bukkit;

import java.util.List;
import java.util.Locale;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.MangrovePropagule;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Frog;
import org.bukkit.entity.Goat;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Tadpole;

import net.coreprotect.model.BlockGroup;

/**
 * Bukkit adapter implementation for Minecraft 1.19.
 * Provides version-specific implementations for the BukkitInterface
 * to handle features introduced in the 1.19 update, including:
 * - New block types (mangrove)
 * - New entity types (frogs, tadpoles)
 * - Updates to existing entities (goats)
 * - Sculk blocks
 */
public class Bukkit_v1_19 extends Bukkit_v1_18 {

    private static final boolean HAS_FIREWORK_RIDER = hasPublicMethod(Firework.class, "getAttachedTo");

    @Override
    public LivingEntity getFireworkRider(Firework firework) {
        return HAS_FIREWORK_RIDER ? firework.getAttachedTo() : null;
    }

    /**
     * Initializes the Bukkit_v1_19 adapter with 1.19-specific block groups.
     * Sets up collections of blocks with similar behavior for efficient handling.
     */
    public Bukkit_v1_19() {
        initializeBlockGroups();
    }

    /**
     * Initializes all the block groups for Minecraft 1.19.
     * This organizes blocks into functional categories for efficient lookups and operations.
     */
    private void initializeBlockGroups() {
        // Blocks that need tracking from the top face
        initializeTrackTopBlocks();

        // Blocks that need tracking from the side
        initializeTrackSideBlocks();

        // Door blocks
        initializeDoorBlocks();

        // Button blocks
        initializeButtonBlocks();

        // Pressure plate blocks
        initializePressurePlateBlocks();

        // Blocks that can be interacted with
        initializeInteractBlocks();

        // Blocks that can be safely interacted with (won't cause block updates)
        initializeSafeInteractBlocks();

        // Blocks that can't have other blocks attached to them
        initializeNonAttachableBlocks();

        // Sculk blocks (new in 1.19)
        initializeSculkBlocks();
    }

    /**
     * Initializes blocks that need tracking from their top face.
     */
    private void initializeTrackTopBlocks() {
        BlockGroup.TRACK_TOP.addAll(List.of(Material.MANGROVE_PROPAGULE, Material.MANGROVE_SIGN));
        BlockGroup.TRACK_BOTTOM.add(Material.MANGROVE_PROPAGULE);
    }

    @Override
    public boolean isAttached(Block block, Block scanBlock, BlockData blockData, int scanMin) {
        if (blockData instanceof MangrovePropagule) {
            return scanMin == (((MangrovePropagule) blockData).isHanging() ? 6 : 5);
        }
        return super.isAttached(block, scanBlock, blockData, scanMin);
    }

    /**
     * Initializes blocks that need tracking from their side.
     */
    private void initializeTrackSideBlocks() {
        BlockGroup.TRACK_SIDE.add(Material.MANGROVE_WALL_SIGN);
    }

    /**
     * Initializes door blocks.
     */
    private void initializeDoorBlocks() {
        BlockGroup.DOORS.add(Material.MANGROVE_DOOR);
    }

    /**
     * Initializes button blocks.
     */
    private void initializeButtonBlocks() {
        BlockGroup.BUTTONS.add(Material.MANGROVE_BUTTON);
    }

    /**
     * Initializes pressure plate blocks.
     */
    private void initializePressurePlateBlocks() {
        BlockGroup.PRESSURE_PLATES.add(Material.MANGROVE_PRESSURE_PLATE);
    }

    /**
     * Initializes blocks that can be interacted with.
     */
    private void initializeInteractBlocks() {
        BlockGroup.INTERACT_BLOCKS.addAll(List.of(Material.MANGROVE_FENCE_GATE, Material.MANGROVE_TRAPDOOR));
    }

    /**
     * Initializes blocks that can be safely interacted with.
     */
    private void initializeSafeInteractBlocks() {
        BlockGroup.SAFE_INTERACT_BLOCKS.addAll(List.of(Material.MANGROVE_TRAPDOOR, Material.MANGROVE_FENCE_GATE));
    }

    /**
     * Initializes blocks that can't have other blocks attached to them.
     */
    private void initializeNonAttachableBlocks() {
        BlockGroup.NON_ATTACHABLE.add(Material.MANGROVE_PROPAGULE);
    }

    /**
     * Initializes sculk blocks (new in 1.19).
     */
    private void initializeSculkBlocks() {
        BlockGroup.SCULK.addAll(List.of(Material.SCULK, Material.SCULK_VEIN, Material.SCULK_SENSOR, Material.SCULK_SHRIEKER, Material.SCULK_CATALYST));
        BlockGroup.TRACK_ANY.add(Material.SCULK_VEIN);
        BlockGroup.NON_ATTACHABLE.add(Material.SCULK_VEIN);
    }

    /**
     * Gets metadata from a living entity specific to 1.19.
     * Handles Frog, Tadpole, and updated Goat entities.
     *
     * @param entity
     *            The living entity
     * @param info
     *            The list to populate with entity metadata
     * @return true if metadata was extracted, false otherwise
     */
    @Override
    public boolean getEntityMeta(LivingEntity entity, List<Object> info) {
        if (entity == null || info == null) {
            return false;
        }

        try {
            if (entity instanceof Frog) {
                Frog frog = (Frog) entity;
                info.add(BukkitAdapter.ADAPTER.getRegistryKey(frog.getVariant()));
                return true;
            }
            else if (entity instanceof Tadpole) {
                Tadpole tadpole = (Tadpole) entity;
                info.add(tadpole.getAge());
                return true;
            }
            else if (entity instanceof Goat) {
                Goat goat = (Goat) entity;
                info.add(goat.isScreaming());
                info.add(goat.hasLeftHorn());
                info.add(goat.hasRightHorn());
                return true;
            }

            // Fall back to parent implementation if not a known 1.19 entity
            return super.getEntityMeta(entity, info);
        }
        catch (Exception e) {
            // Log error or handle exception if needed
            return false;
        }
    }

    /**
     * Sets metadata on an entity specific to 1.19.
     * Handles Frog, Tadpole, and updated Goat entities.
     *
     * @param entity
     *            The entity
     * @param value
     *            The metadata value
     * @param count
     *            The index of the metadata property
     * @return true if metadata was set, false otherwise
     */
    @Override
    public boolean setEntityMeta(Entity entity, Object value, int count) {
        if (entity == null || value == null) {
            return false;
        }

        try {
            if (entity instanceof Frog) {
                Frog frog = (Frog) entity;
                if (count == 0) {
                    // Convert string registry key to variant if needed
                    if (value instanceof String) {
                        String key = (String) value;
                        value = BukkitAdapter.ADAPTER.getRegistryValue(key, Frog.Variant.class);
                        if (value == null) {
                            int separator = key.indexOf(':');
                            String name = separator < 0 ? key : key.substring(separator + 1).toUpperCase(Locale.ROOT).replace('.', '_').replace('-', '_');
                            value = BukkitAdapter.getLegacyEnumValue(Frog.Variant.class, name);
                        }
                        if (!(value instanceof Frog.Variant)) {
                            return false;
                        }
                    }
                    frog.setVariant((Frog.Variant) value);
                    return true;
                }
            }
            else if (entity instanceof Tadpole) {
                Tadpole tadpole = (Tadpole) entity;
                if (count == 0 && value instanceof Number) {
                    tadpole.setAge(((Number) value).intValue());
                    return true;
                }
            }
            else if (entity instanceof Goat) {
                if (!(value instanceof Boolean)) {
                    return false;
                }
                Goat goat = (Goat) entity;
                boolean boolValue = (Boolean) value;

                switch (count) {
                    case 0:
                        goat.setScreaming(boolValue);
                        return true;
                    case 1:
                        goat.setLeftHorn(boolValue);
                        return true;
                    case 2:
                        goat.setRightHorn(boolValue);
                        return true;
                    default:
                        // Invalid count for goat
                        return false;
                }
            }

            // Fall back to parent implementation if not a known 1.19 entity
            return super.setEntityMeta(entity, value, count);
        }
        catch (Exception e) {
            // Log error or handle exception if needed
            return false;
        }
    }
}
