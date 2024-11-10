package net.coreprotect.bukkit;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.PointedDripstone;
import org.bukkit.entity.Axolotl;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.GlowItemFrame;
import org.bukkit.entity.Goat;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.inventory.meta.ItemMeta;

import net.coreprotect.model.BlockGroup;
import net.coreprotect.utility.Util;

public class Bukkit_v1_17 extends BukkitAdapter implements BukkitInterface {

    public Bukkit_v1_17() {
        BlockGroup.TRACK_ANY = new HashSet<>(Arrays.asList(Material.PISTON_HEAD, Material.LEVER, Material.BELL, Material.SMALL_AMETHYST_BUD, Material.MEDIUM_AMETHYST_BUD, Material.LARGE_AMETHYST_BUD, Material.AMETHYST_CLUSTER, Material.GLOW_LICHEN));
        BlockGroup.TRACK_TOP = new HashSet<>(Arrays.asList(Material.TORCH, Material.REDSTONE_TORCH, Material.BAMBOO, Material.BAMBOO_SAPLING, Material.CORNFLOWER, Material.LILY_OF_THE_VALLEY, Material.WITHER_ROSE, Material.SWEET_BERRY_BUSH, Material.SCAFFOLDING, Material.OAK_SAPLING, Material.SPRUCE_SAPLING, Material.BIRCH_SAPLING, Material.JUNGLE_SAPLING, Material.ACACIA_SAPLING, Material.DARK_OAK_SAPLING, Material.POWERED_RAIL, Material.DETECTOR_RAIL, Material.FERN, Material.DEAD_BUSH, Material.DANDELION, Material.POPPY, Material.BLUE_ORCHID, Material.ALLIUM, Material.AZURE_BLUET, Material.RED_TULIP, Material.ORANGE_TULIP, Material.WHITE_TULIP, Material.PINK_TULIP, Material.OXEYE_DAISY, Material.BROWN_MUSHROOM, Material.RED_MUSHROOM, Material.REDSTONE_WIRE, Material.WHEAT, Material.ACACIA_SIGN, Material.BIRCH_SIGN, Material.DARK_OAK_SIGN, Material.JUNGLE_SIGN, Material.OAK_SIGN, Material.SPRUCE_SIGN, Material.WHITE_BANNER, Material.ORANGE_BANNER, Material.MAGENTA_BANNER, Material.LIGHT_BLUE_BANNER, Material.YELLOW_BANNER, Material.LIME_BANNER, Material.PINK_BANNER, Material.GRAY_BANNER, Material.LIGHT_GRAY_BANNER, Material.CYAN_BANNER, Material.PURPLE_BANNER, Material.BLUE_BANNER, Material.BROWN_BANNER, Material.GREEN_BANNER, Material.RED_BANNER, Material.BLACK_BANNER, Material.RAIL, Material.IRON_DOOR, Material.SNOW, Material.CACTUS, Material.SUGAR_CANE, Material.REPEATER, Material.PUMPKIN_STEM, Material.MELON_STEM, Material.CARROT, Material.POTATO, Material.COMPARATOR, Material.ACTIVATOR_RAIL, Material.SUNFLOWER, Material.LILAC, Material.TALL_GRASS, Material.LARGE_FERN, Material.ROSE_BUSH, Material.PEONY, Material.NETHER_WART, Material.CHORUS_PLANT, Material.CHORUS_FLOWER, Material.KELP, Material.SOUL_TORCH, Material.TWISTING_VINES, Material.CRIMSON_FUNGUS, Material.WARPED_FUNGUS, Material.CRIMSON_ROOTS, Material.WARPED_ROOTS, Material.NETHER_SPROUTS, Material.CRIMSON_SIGN, Material.WARPED_SIGN, Material.AZALEA, Material.FLOWERING_AZALEA, Material.SMALL_DRIPLEAF, Material.BIG_DRIPLEAF));
        BlockGroup.TRACK_TOP_BOTTOM = new HashSet<>(Arrays.asList(Material.POINTED_DRIPSTONE, Material.BIG_DRIPLEAF_STEM));
        BlockGroup.TRACK_BOTTOM = new HashSet<>(Arrays.asList(Material.WEEPING_VINES, Material.CAVE_VINES, Material.CAVE_VINES_PLANT, Material.HANGING_ROOTS));
        BlockGroup.VINES = new HashSet<>(Arrays.asList(Material.VINE, Material.WEEPING_VINES, Material.TWISTING_VINES, Material.CAVE_VINES));
        BlockGroup.CANDLES = new HashSet<>(Arrays.asList(Material.CANDLE, Material.BLACK_CANDLE, Material.BLUE_CANDLE, Material.BROWN_CANDLE, Material.CYAN_CANDLE, Material.GRAY_CANDLE, Material.GREEN_CANDLE, Material.LIGHT_BLUE_CANDLE, Material.LIGHT_GRAY_CANDLE, Material.LIME_CANDLE, Material.MAGENTA_CANDLE, Material.ORANGE_CANDLE, Material.PINK_CANDLE, Material.PURPLE_CANDLE, Material.RED_CANDLE, Material.WHITE_CANDLE, Material.YELLOW_CANDLE, Material.CANDLE_CAKE, Material.BLACK_CANDLE_CAKE, Material.BLUE_CANDLE_CAKE, Material.BROWN_CANDLE_CAKE, Material.CYAN_CANDLE_CAKE, Material.GRAY_CANDLE_CAKE, Material.GREEN_CANDLE_CAKE, Material.LIGHT_BLUE_CANDLE_CAKE, Material.LIGHT_GRAY_CANDLE_CAKE, Material.LIME_CANDLE_CAKE, Material.MAGENTA_CANDLE_CAKE, Material.ORANGE_CANDLE_CAKE, Material.PINK_CANDLE_CAKE, Material.PURPLE_CANDLE_CAKE, Material.RED_CANDLE_CAKE, Material.WHITE_CANDLE_CAKE, Material.YELLOW_CANDLE_CAKE));
        BlockGroup.AMETHYST = new HashSet<>(Arrays.asList(Material.SMALL_AMETHYST_BUD, Material.MEDIUM_AMETHYST_BUD, Material.LARGE_AMETHYST_BUD, Material.AMETHYST_CLUSTER));
        BlockGroup.UPDATE_STATE = new HashSet<>(Arrays.asList(Material.TORCH, Material.WALL_TORCH, Material.REDSTONE_WIRE, Material.RAIL, Material.POWERED_RAIL, Material.DETECTOR_RAIL, Material.FURNACE, Material.BLAST_FURNACE, Material.SMOKER, Material.LEVER, Material.REDSTONE_TORCH, Material.REDSTONE_WALL_TORCH, Material.GLOWSTONE, Material.JACK_O_LANTERN, Material.REPEATER, Material.REDSTONE_LAMP, Material.BEACON, Material.COMPARATOR, Material.DAYLIGHT_DETECTOR, Material.REDSTONE_BLOCK, Material.HOPPER, Material.CHEST, Material.TRAPPED_CHEST, Material.ACTIVATOR_RAIL, Material.SOUL_TORCH, Material.SOUL_WALL_TORCH, Material.SHROOMLIGHT, Material.RESPAWN_ANCHOR, Material.CRYING_OBSIDIAN, Material.TARGET, Material.SMALL_AMETHYST_BUD, Material.MEDIUM_AMETHYST_BUD, Material.LARGE_AMETHYST_BUD, Material.AMETHYST_CLUSTER, Material.CAVE_VINES, Material.CAVE_VINES_PLANT, Material.GLOW_LICHEN, Material.LIGHT, Material.LAVA_CAULDRON));
        BlockGroup.NON_ATTACHABLE = new HashSet<>(Arrays.asList(Material.AIR, Material.CAVE_AIR, Material.BARRIER, Material.CORNFLOWER, Material.LILY_OF_THE_VALLEY, Material.WITHER_ROSE, Material.SWEET_BERRY_BUSH, Material.OAK_SAPLING, Material.SPRUCE_SAPLING, Material.BIRCH_SAPLING, Material.JUNGLE_SAPLING, Material.ACACIA_SAPLING, Material.DARK_OAK_SAPLING, Material.WATER, Material.LAVA, Material.POWERED_RAIL, Material.DETECTOR_RAIL, Material.FERN, Material.DEAD_BUSH, Material.DANDELION, Material.POPPY, Material.BLUE_ORCHID, Material.ALLIUM, Material.AZURE_BLUET, Material.RED_TULIP, Material.ORANGE_TULIP, Material.WHITE_TULIP, Material.PINK_TULIP, Material.OXEYE_DAISY, Material.BROWN_MUSHROOM, Material.RED_MUSHROOM, Material.TORCH, Material.WALL_TORCH, Material.REDSTONE_WIRE, Material.LADDER, Material.RAIL, Material.LEVER, Material.REDSTONE_TORCH, Material.REDSTONE_WALL_TORCH, Material.SNOW, Material.SUGAR_CANE, Material.NETHER_PORTAL, Material.REPEATER, Material.KELP, Material.CHORUS_FLOWER, Material.CHORUS_PLANT, Material.SOUL_TORCH, Material.SOUL_WALL_TORCH, Material.LIGHT, Material.SMALL_DRIPLEAF, Material.BIG_DRIPLEAF, Material.BIG_DRIPLEAF_STEM, Material.GLOW_LICHEN, Material.HANGING_ROOTS));
        BlockGroup.VERTICAL_TOP_BOTTOM = new HashSet<>(Arrays.asList(Material.BIG_DRIPLEAF_STEM));
    }

    @Override
    public String parseLegacyName(String name) {
        switch (name) {
            case "GRASS_PATH":
                name = "DIRT_PATH";
                break;
            default:
                break;
        }

        return name;
    }

    @Override
    public int getLegacyBlockId(Material material) {
        switch (material) {
            case DIRT_PATH:
                return Util.getBlockId("GRASS_PATH", false);
            default:
                return -1;
        }
    }

    @Override
    public boolean getEntityMeta(LivingEntity entity, List<Object> info) {
        if (entity instanceof Axolotl) {
            Axolotl axolotl = (Axolotl) entity;
            info.add(axolotl.getVariant());
        }
        else if (entity instanceof Goat) {
            Goat goat = (Goat) entity;
            info.add(goat.isScreaming());
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
        if (entity instanceof Axolotl) {
            Axolotl axolotl = (Axolotl) entity;
            if (count == 0) {
                org.bukkit.entity.Axolotl.Variant set = (org.bukkit.entity.Axolotl.Variant) value;
                axolotl.setVariant(set);
            }
        }
        else if (entity instanceof Goat) {
            Goat goat = (Goat) entity;
            if (count == 0) {
                boolean set = (Boolean) value;
                goat.setScreaming(set);
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

    @Override
    public boolean getItemMeta(ItemMeta itemMeta, List<Map<String, Object>> list, List<List<Map<String, Object>>> metadata, int slot) {
        if (itemMeta instanceof BundleMeta) {
            BundleMeta meta = (BundleMeta) itemMeta;
            BundleMeta subMeta = (BundleMeta) meta.clone();
            meta.setItems(null);
            list.add(meta.serialize());
            metadata.add(list);

            if (subMeta.hasItems()) {
                list = new ArrayList<>();
                for (ItemStack itemStack : subMeta.getItems()) {
                    Map<String, Object> itemMap = Util.serializeItemStack(itemStack, null, slot);
                    if (itemMap.size() > 0) {
                        list.add(itemMap);
                    }
                }
                metadata.add(list);
            }
        }
        else if (super.getItemMeta(itemMeta, list, metadata, slot)) {
            return true;
        }
        else {
            return false;
        }

        return true;
    }

    @Override
    public boolean setItemMeta(Material rowType, ItemStack itemstack, List<Map<String, Object>> map) {
        if ((rowType == Material.BUNDLE)) {
            BundleMeta meta = (BundleMeta) itemstack.getItemMeta();
            for (Map<String, Object> itemData : map) {
                ItemStack itemStack = Util.unserializeItemStack(itemData);
                if (itemStack != null) {
                    meta.addItem(itemStack);
                }
            }
            itemstack.setItemMeta(meta);
        }
        else if (super.setItemMeta(rowType, itemstack, map)) {
            return true;
        }
        else {
            return false;
        }

        return true;
    }

    @Override
    public boolean isAttached(Block block, Block scanBlock, BlockData blockData, int scanMin) {
        if (blockData instanceof PointedDripstone) {
            PointedDripstone pointedDripstone = (PointedDripstone) blockData;
            BlockFace blockFace = pointedDripstone.getVerticalDirection();
            boolean adjacent = scanBlock.getRelative(blockFace.getOppositeFace()).getLocation().equals(block.getLocation());
            if (!adjacent) {
                return false;
            }
        }
        else if (!super.isAttached(block, scanBlock, blockData, scanMin)) {
            return false;
        }

        return true;
    }

    @Override
    public int getMinHeight(World world) {
        return world.getMinHeight();
    }

    @Override
    public Material getBucketContents(Material material) {
        return (material == Material.POWDER_SNOW_BUCKET ? Material.POWDER_SNOW : Material.AIR);
    }

    @Override
    public boolean isItemFrame(Material material) {
        return (material == Material.ITEM_FRAME || material == Material.GLOW_ITEM_FRAME);
    }

    @Override
    public Material getFrameType(Entity entity) {
        return (entity instanceof GlowItemFrame) ? Material.GLOW_ITEM_FRAME : Material.ITEM_FRAME;
    }

    @Override
    public Material getFrameType(EntityType type) {
        switch (type) {
            case ITEM_FRAME:
                return Material.ITEM_FRAME;
            case GLOW_ITEM_FRAME:
                return Material.GLOW_ITEM_FRAME;
            default:
                return null;
        }
    }

    @Override
    public Class<?> getFrameClass(Material material) {
        return (material == Material.GLOW_ITEM_FRAME) ? GlowItemFrame.class : ItemFrame.class;
    }

    @Override
    public boolean isGlowing(Sign sign, boolean isFront) {
        if (!isFront) {
            return false;
        }

        return sign.isGlowingText();
    }

    @Override
    public void setGlowing(Sign sign, boolean isFront, boolean isGlowing) {
        if (!isFront) {
            return;
        }

        sign.setGlowingText(isGlowing);
    }

    @Override
    public boolean isInvisible(Material material) {
        return material.isAir() || material == Material.LIGHT;
    }

}
