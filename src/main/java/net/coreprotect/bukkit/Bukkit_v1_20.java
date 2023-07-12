package net.coreprotect.bukkit;

import java.util.Arrays;
import java.util.HashSet;

import org.bukkit.Color;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;

import net.coreprotect.model.BlockGroup;

public class Bukkit_v1_20 extends Bukkit_v1_19 implements BukkitInterface {

    public Bukkit_v1_20() {
        BlockGroup.CONTAINERS = new HashSet<>(Arrays.asList(Material.JUKEBOX, Material.DISPENSER, Material.CHEST, Material.FURNACE, Material.BREWING_STAND, Material.TRAPPED_CHEST, Material.HOPPER, Material.DROPPER, Material.ARMOR_STAND, Material.ITEM_FRAME, Material.SHULKER_BOX, Material.BLACK_SHULKER_BOX, Material.BLUE_SHULKER_BOX, Material.BROWN_SHULKER_BOX, Material.CYAN_SHULKER_BOX, Material.GRAY_SHULKER_BOX, Material.GREEN_SHULKER_BOX, Material.LIGHT_BLUE_SHULKER_BOX, Material.LIME_SHULKER_BOX, Material.MAGENTA_SHULKER_BOX, Material.ORANGE_SHULKER_BOX, Material.PINK_SHULKER_BOX, Material.PURPLE_SHULKER_BOX, Material.RED_SHULKER_BOX, Material.LIGHT_GRAY_SHULKER_BOX, Material.WHITE_SHULKER_BOX, Material.YELLOW_SHULKER_BOX, Material.BARREL, Material.BLAST_FURNACE, Material.SMOKER, Material.LECTERN, Material.CHISELED_BOOKSHELF));
        BlockGroup.UPDATE_STATE = new HashSet<>(Arrays.asList(Material.TORCH, Material.WALL_TORCH, Material.REDSTONE_WIRE, Material.RAIL, Material.POWERED_RAIL, Material.DETECTOR_RAIL, Material.FURNACE, Material.BLAST_FURNACE, Material.SMOKER, Material.LEVER, Material.REDSTONE_TORCH, Material.REDSTONE_WALL_TORCH, Material.GLOWSTONE, Material.JACK_O_LANTERN, Material.REPEATER, Material.REDSTONE_LAMP, Material.BEACON, Material.COMPARATOR, Material.DAYLIGHT_DETECTOR, Material.REDSTONE_BLOCK, Material.HOPPER, Material.CHEST, Material.TRAPPED_CHEST, Material.ACTIVATOR_RAIL, Material.SOUL_TORCH, Material.SOUL_WALL_TORCH, Material.SHROOMLIGHT, Material.RESPAWN_ANCHOR, Material.CRYING_OBSIDIAN, Material.TARGET, Material.SMALL_AMETHYST_BUD, Material.MEDIUM_AMETHYST_BUD, Material.LARGE_AMETHYST_BUD, Material.AMETHYST_CLUSTER, Material.CAVE_VINES, Material.CAVE_VINES_PLANT, Material.GLOW_LICHEN, Material.LIGHT, Material.LAVA_CAULDRON, Material.CHISELED_BOOKSHELF));
    }

    @Override
    public void setGlowing(Sign sign, boolean isFront, boolean isGlowing) {
        if (isFront) {
            sign.getSide(Side.FRONT).setGlowingText(isGlowing);
        }
        else {
            sign.getSide(Side.BACK).setGlowingText(isGlowing);
        }
    }

    @Override
    public void setColor(Sign sign, boolean isFront, int color) {
        if (isFront) {
            sign.getSide(Side.FRONT).setColor(DyeColor.getByColor(Color.fromRGB(color)));
        }
        else {
            sign.getSide(Side.BACK).setColor(DyeColor.getByColor(Color.fromRGB(color)));
        }
    }

    @Override
    public void setWaxed(Sign sign, boolean isWaxed) {
        sign.setWaxed(isWaxed);
    }

    @Override
    public int getColor(Sign sign, boolean isFront) {
        if (isFront) {
            return sign.getSide(Side.FRONT).getColor().getColor().asRGB();
        }
        else {
            return sign.getSide(Side.BACK).getColor().getColor().asRGB();
        }
    }

    @Override
    public boolean isGlowing(Sign sign, boolean isFront) {
        if (isFront) {
            return sign.getSide(Side.FRONT).isGlowingText();
        }
        else {
            return sign.getSide(Side.BACK).isGlowingText();
        }
    }

    @Override
    public boolean isWaxed(Sign sign) {
        return sign.isWaxed();
    }

    @Override
    public Material getPlantSeeds(Material material) {
        switch (material) {
            case WHEAT:
                material = Material.WHEAT_SEEDS;
                break;
            case PUMPKIN_STEM:
                material = Material.PUMPKIN_SEEDS;
                break;
            case MELON_STEM:
                material = Material.MELON_SEEDS;
                break;
            case BEETROOTS:
                material = Material.BEETROOT_SEEDS;
                break;
            case TORCHFLOWER_CROP:
                material = Material.TORCHFLOWER_SEEDS;
                break;
            default:
        }

        return material;
    }

    @Override
    public boolean hasGravity(Material scanType) {
        return scanType.hasGravity() || scanType == Material.SUSPICIOUS_GRAVEL || scanType == Material.SUSPICIOUS_SAND;
    }

}
