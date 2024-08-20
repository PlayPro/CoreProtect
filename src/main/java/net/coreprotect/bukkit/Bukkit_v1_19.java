package net.coreprotect.bukkit;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Frog;
import org.bukkit.entity.Goat;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Tadpole;

import net.coreprotect.model.BlockGroup;

public class Bukkit_v1_19 extends Bukkit_v1_18 implements BukkitInterface {

    public Bukkit_v1_19() {
        BlockGroup.TRACK_TOP = new HashSet<>(Arrays.asList(Material.TORCH, Material.REDSTONE_TORCH, Material.BAMBOO, Material.BAMBOO_SAPLING, Material.CORNFLOWER, Material.LILY_OF_THE_VALLEY, Material.WITHER_ROSE, Material.SWEET_BERRY_BUSH, Material.SCAFFOLDING, Material.OAK_SAPLING, Material.SPRUCE_SAPLING, Material.BIRCH_SAPLING, Material.JUNGLE_SAPLING, Material.MANGROVE_PROPAGULE, Material.ACACIA_SAPLING, Material.DARK_OAK_SAPLING, Material.POWERED_RAIL, Material.DETECTOR_RAIL, Material.FERN, Material.DEAD_BUSH, Material.DANDELION, Material.POPPY, Material.BLUE_ORCHID, Material.ALLIUM, Material.AZURE_BLUET, Material.RED_TULIP, Material.ORANGE_TULIP, Material.WHITE_TULIP, Material.PINK_TULIP, Material.OXEYE_DAISY, Material.BROWN_MUSHROOM, Material.RED_MUSHROOM, Material.REDSTONE_WIRE, Material.WHEAT, Material.MANGROVE_SIGN, Material.ACACIA_SIGN, Material.BIRCH_SIGN, Material.DARK_OAK_SIGN, Material.JUNGLE_SIGN, Material.OAK_SIGN, Material.SPRUCE_SIGN, Material.WHITE_BANNER, Material.ORANGE_BANNER, Material.MAGENTA_BANNER, Material.LIGHT_BLUE_BANNER, Material.YELLOW_BANNER, Material.LIME_BANNER, Material.PINK_BANNER, Material.GRAY_BANNER, Material.LIGHT_GRAY_BANNER, Material.CYAN_BANNER, Material.PURPLE_BANNER, Material.BLUE_BANNER, Material.BROWN_BANNER, Material.GREEN_BANNER, Material.RED_BANNER, Material.BLACK_BANNER, Material.RAIL, Material.IRON_DOOR, Material.SNOW, Material.CACTUS, Material.SUGAR_CANE, Material.REPEATER, Material.PUMPKIN_STEM, Material.MELON_STEM, Material.CARROT, Material.POTATO, Material.COMPARATOR, Material.ACTIVATOR_RAIL, Material.SUNFLOWER, Material.LILAC, Material.TALL_GRASS, Material.LARGE_FERN, Material.ROSE_BUSH, Material.PEONY, Material.NETHER_WART, Material.CHORUS_PLANT, Material.CHORUS_FLOWER, Material.KELP, Material.SOUL_TORCH, Material.TWISTING_VINES, Material.CRIMSON_FUNGUS, Material.WARPED_FUNGUS, Material.CRIMSON_ROOTS, Material.WARPED_ROOTS, Material.NETHER_SPROUTS, Material.CRIMSON_SIGN, Material.WARPED_SIGN, Material.AZALEA, Material.FLOWERING_AZALEA, Material.SMALL_DRIPLEAF, Material.BIG_DRIPLEAF));
        BlockGroup.TRACK_SIDE = new HashSet<>(Arrays.asList(Material.WALL_TORCH, Material.REDSTONE_WALL_TORCH, Material.RAIL, Material.POWERED_RAIL, Material.DETECTOR_RAIL, Material.ACTIVATOR_RAIL, Material.WHITE_BED, Material.ORANGE_BED, Material.MAGENTA_BED, Material.LIGHT_BLUE_BED, Material.YELLOW_BED, Material.LIME_BED, Material.PINK_BED, Material.GRAY_BED, Material.LIGHT_GRAY_BED, Material.CYAN_BED, Material.PURPLE_BED, Material.BLUE_BED, Material.BROWN_BED, Material.GREEN_BED, Material.RED_BED, Material.BLACK_BED, Material.LADDER, Material.MANGROVE_WALL_SIGN, Material.ACACIA_WALL_SIGN, Material.BIRCH_WALL_SIGN, Material.DARK_OAK_WALL_SIGN, Material.JUNGLE_WALL_SIGN, Material.OAK_WALL_SIGN, Material.SPRUCE_WALL_SIGN, Material.VINE, Material.COCOA, Material.TRIPWIRE_HOOK, Material.WHITE_WALL_BANNER, Material.ORANGE_WALL_BANNER, Material.MAGENTA_WALL_BANNER, Material.LIGHT_BLUE_WALL_BANNER, Material.YELLOW_WALL_BANNER, Material.LIME_WALL_BANNER, Material.PINK_WALL_BANNER, Material.GRAY_WALL_BANNER, Material.LIGHT_GRAY_WALL_BANNER, Material.CYAN_WALL_BANNER, Material.PURPLE_WALL_BANNER, Material.BLUE_WALL_BANNER, Material.BROWN_WALL_BANNER, Material.GREEN_WALL_BANNER, Material.RED_WALL_BANNER, Material.BLACK_WALL_BANNER, Material.SOUL_WALL_TORCH, Material.CRIMSON_WALL_SIGN, Material.WARPED_WALL_SIGN));
        BlockGroup.DOORS = new HashSet<>(Arrays.asList(Material.OAK_DOOR, Material.SPRUCE_DOOR, Material.BIRCH_DOOR, Material.JUNGLE_DOOR, Material.MANGROVE_DOOR, Material.ACACIA_DOOR, Material.DARK_OAK_DOOR, Material.CRIMSON_DOOR, Material.WARPED_DOOR));
        BlockGroup.BUTTONS = new HashSet<>(Arrays.asList(Material.STONE_BUTTON, Material.OAK_BUTTON, Material.MANGROVE_BUTTON, Material.ACACIA_BUTTON, Material.BIRCH_BUTTON, Material.DARK_OAK_BUTTON, Material.JUNGLE_BUTTON, Material.SPRUCE_BUTTON, Material.POLISHED_BLACKSTONE_BUTTON, Material.CRIMSON_BUTTON, Material.WARPED_BUTTON));
        BlockGroup.PRESSURE_PLATES = new HashSet<>(Arrays.asList(Material.STONE_PRESSURE_PLATE, Material.MANGROVE_PRESSURE_PLATE, Material.ACACIA_PRESSURE_PLATE, Material.BIRCH_PRESSURE_PLATE, Material.DARK_OAK_PRESSURE_PLATE, Material.HEAVY_WEIGHTED_PRESSURE_PLATE, Material.JUNGLE_PRESSURE_PLATE, Material.LIGHT_WEIGHTED_PRESSURE_PLATE, Material.OAK_PRESSURE_PLATE, Material.SPRUCE_PRESSURE_PLATE, Material.CRIMSON_PRESSURE_PLATE, Material.WARPED_PRESSURE_PLATE, Material.POLISHED_BLACKSTONE_PRESSURE_PLATE));
        BlockGroup.INTERACT_BLOCKS = new HashSet<>(Arrays.asList(Material.SPRUCE_FENCE_GATE, Material.BIRCH_FENCE_GATE, Material.JUNGLE_FENCE_GATE, Material.DARK_OAK_FENCE_GATE, Material.MANGROVE_FENCE_GATE, Material.ACACIA_FENCE_GATE, Material.DISPENSER, Material.NOTE_BLOCK, Material.CHEST, Material.FURNACE, Material.LEVER, Material.REPEATER, Material.MANGROVE_TRAPDOOR, Material.ACACIA_TRAPDOOR, Material.BIRCH_TRAPDOOR, Material.DARK_OAK_TRAPDOOR, Material.JUNGLE_TRAPDOOR, Material.SPRUCE_TRAPDOOR, Material.OAK_TRAPDOOR, Material.OAK_FENCE_GATE, Material.BREWING_STAND, Material.ANVIL, Material.CHIPPED_ANVIL, Material.DAMAGED_ANVIL, Material.ENDER_CHEST, Material.TRAPPED_CHEST, Material.COMPARATOR, Material.HOPPER, Material.DROPPER, Material.SHULKER_BOX, Material.BLACK_SHULKER_BOX, Material.BLUE_SHULKER_BOX, Material.BROWN_SHULKER_BOX, Material.CYAN_SHULKER_BOX, Material.GRAY_SHULKER_BOX, Material.GREEN_SHULKER_BOX, Material.LIGHT_BLUE_SHULKER_BOX, Material.LIME_SHULKER_BOX, Material.MAGENTA_SHULKER_BOX, Material.ORANGE_SHULKER_BOX, Material.PINK_SHULKER_BOX, Material.PURPLE_SHULKER_BOX, Material.RED_SHULKER_BOX, Material.LIGHT_GRAY_SHULKER_BOX, Material.WHITE_SHULKER_BOX, Material.YELLOW_SHULKER_BOX, Material.BARREL, Material.BLAST_FURNACE, Material.GRINDSTONE, Material.LOOM, Material.SMOKER, Material.CRAFTING_TABLE, Material.CARTOGRAPHY_TABLE, Material.ENCHANTING_TABLE, Material.SMITHING_TABLE, Material.STONECUTTER, Material.CRIMSON_FENCE_GATE, Material.WARPED_FENCE_GATE, Material.CRIMSON_TRAPDOOR, Material.WARPED_TRAPDOOR));
        BlockGroup.SAFE_INTERACT_BLOCKS = new HashSet<>(Arrays.asList(Material.LEVER, Material.MANGROVE_TRAPDOOR, Material.ACACIA_TRAPDOOR, Material.BIRCH_TRAPDOOR, Material.DARK_OAK_TRAPDOOR, Material.JUNGLE_TRAPDOOR, Material.SPRUCE_TRAPDOOR, Material.OAK_TRAPDOOR, Material.OAK_FENCE_GATE, Material.SPRUCE_FENCE_GATE, Material.BIRCH_FENCE_GATE, Material.JUNGLE_FENCE_GATE, Material.DARK_OAK_FENCE_GATE, Material.MANGROVE_FENCE_GATE, Material.ACACIA_FENCE_GATE, Material.CRIMSON_FENCE_GATE, Material.WARPED_FENCE_GATE, Material.CRIMSON_TRAPDOOR, Material.WARPED_TRAPDOOR));
        BlockGroup.NON_ATTACHABLE = new HashSet<>(Arrays.asList(Material.AIR, Material.CAVE_AIR, Material.BARRIER, Material.CORNFLOWER, Material.LILY_OF_THE_VALLEY, Material.WITHER_ROSE, Material.SWEET_BERRY_BUSH, Material.OAK_SAPLING, Material.SPRUCE_SAPLING, Material.BIRCH_SAPLING, Material.JUNGLE_SAPLING, Material.MANGROVE_PROPAGULE, Material.ACACIA_SAPLING, Material.DARK_OAK_SAPLING, Material.WATER, Material.LAVA, Material.POWERED_RAIL, Material.DETECTOR_RAIL, Material.FERN, Material.DEAD_BUSH, Material.DANDELION, Material.POPPY, Material.BLUE_ORCHID, Material.ALLIUM, Material.AZURE_BLUET, Material.RED_TULIP, Material.ORANGE_TULIP, Material.WHITE_TULIP, Material.PINK_TULIP, Material.OXEYE_DAISY, Material.BROWN_MUSHROOM, Material.RED_MUSHROOM, Material.TORCH, Material.WALL_TORCH, Material.REDSTONE_WIRE, Material.LADDER, Material.RAIL, Material.LEVER, Material.REDSTONE_TORCH, Material.REDSTONE_WALL_TORCH, Material.SNOW, Material.SUGAR_CANE, Material.NETHER_PORTAL, Material.REPEATER, Material.KELP, Material.CHORUS_FLOWER, Material.CHORUS_PLANT, Material.SOUL_TORCH, Material.SOUL_WALL_TORCH, Material.LIGHT, Material.SMALL_DRIPLEAF, Material.BIG_DRIPLEAF, Material.BIG_DRIPLEAF_STEM, Material.GLOW_LICHEN, Material.HANGING_ROOTS));
        BlockGroup.SCULK = new HashSet<>(Arrays.asList(Material.SCULK, Material.SCULK_VEIN, Material.SCULK_SENSOR, Material.SCULK_SHRIEKER));
    }

    @Override
    public boolean getEntityMeta(LivingEntity entity, List<Object> info) {
        if (entity instanceof Frog) {
            Frog frog = (Frog) entity;
            info.add(BukkitAdapter.ADAPTER.getRegistryKey(frog.getVariant()));
        }
        else if (entity instanceof Tadpole) {
            Tadpole tadpole = (Tadpole) entity;
            info.add(tadpole.getAge());
        }
        else if (entity instanceof Goat) {
            Goat goat = (Goat) entity;
            info.add(goat.isScreaming());
            info.add(goat.hasLeftHorn());
            info.add(goat.hasRightHorn());
        }
        else if (super.getEntityMeta(entity, info)) {
            return true;
        }
        else {
            return false;
        }

        return true;
    }

    @Override
    public boolean setEntityMeta(Entity entity, Object value, int count) {
        if (entity instanceof Frog) {
            Frog frog = (Frog) entity;
            if (count == 0) {
                if (value instanceof String) {
                    value = BukkitAdapter.ADAPTER.getRegistryValue((String) value, Frog.Variant.class);
                }
                Frog.Variant set = (Frog.Variant) value;
                frog.setVariant(set);
            }
        }
        else if (entity instanceof Tadpole) {
            Tadpole tadpole = (Tadpole) entity;
            if (count == 0) {
                int set = (int) value;
                tadpole.setAge(set);
            }
        }
        else if (entity instanceof Goat) {
            Goat goat = (Goat) entity;
            boolean set = (Boolean) value;
            if (count == 0) {
                goat.setScreaming(set);
            }
            else if (count == 1) {
                goat.setLeftHorn(set);
            }
            else if (count == 2) {
                goat.setRightHorn(set);
            }
        }
        else if (super.setEntityMeta(entity, value, count)) {
            return true;
        }
        else {
            return false;
        }

        return true;
    }
}
