package net.coreprotect.bukkit;

import java.util.Arrays;
import java.util.HashSet;

import org.bukkit.Color;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.BlockState;
import org.bukkit.block.ChiseledBookshelf;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.entity.Arrow;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionType;

import net.coreprotect.model.BlockGroup;

public class Bukkit_v1_20 extends Bukkit_v1_19 implements BukkitInterface {

    private Boolean hasClickedPosition = null;
    private Boolean hasBasePotionType = null;

    public Bukkit_v1_20() {
        BlockGroup.CONTAINERS = new HashSet<>(Arrays.asList(Material.JUKEBOX, Material.DISPENSER, Material.CHEST, Material.FURNACE, Material.BREWING_STAND, Material.TRAPPED_CHEST, Material.HOPPER, Material.DROPPER, Material.ARMOR_STAND, Material.ITEM_FRAME, Material.SHULKER_BOX, Material.BLACK_SHULKER_BOX, Material.BLUE_SHULKER_BOX, Material.BROWN_SHULKER_BOX, Material.CYAN_SHULKER_BOX, Material.GRAY_SHULKER_BOX, Material.GREEN_SHULKER_BOX, Material.LIGHT_BLUE_SHULKER_BOX, Material.LIME_SHULKER_BOX, Material.MAGENTA_SHULKER_BOX, Material.ORANGE_SHULKER_BOX, Material.PINK_SHULKER_BOX, Material.PURPLE_SHULKER_BOX, Material.RED_SHULKER_BOX, Material.LIGHT_GRAY_SHULKER_BOX, Material.WHITE_SHULKER_BOX, Material.YELLOW_SHULKER_BOX, Material.BARREL, Material.BLAST_FURNACE, Material.SMOKER, Material.LECTERN, Material.CHISELED_BOOKSHELF, Material.DECORATED_POT));
        BlockGroup.UPDATE_STATE = new HashSet<>(Arrays.asList(Material.TORCH, Material.WALL_TORCH, Material.REDSTONE_WIRE, Material.RAIL, Material.POWERED_RAIL, Material.DETECTOR_RAIL, Material.FURNACE, Material.BLAST_FURNACE, Material.SMOKER, Material.LEVER, Material.REDSTONE_TORCH, Material.REDSTONE_WALL_TORCH, Material.GLOWSTONE, Material.JACK_O_LANTERN, Material.REPEATER, Material.REDSTONE_LAMP, Material.BEACON, Material.COMPARATOR, Material.DAYLIGHT_DETECTOR, Material.REDSTONE_BLOCK, Material.HOPPER, Material.CHEST, Material.TRAPPED_CHEST, Material.ACTIVATOR_RAIL, Material.SOUL_TORCH, Material.SOUL_WALL_TORCH, Material.SHROOMLIGHT, Material.RESPAWN_ANCHOR, Material.CRYING_OBSIDIAN, Material.TARGET, Material.SMALL_AMETHYST_BUD, Material.MEDIUM_AMETHYST_BUD, Material.LARGE_AMETHYST_BUD, Material.AMETHYST_CLUSTER, Material.CAVE_VINES, Material.CAVE_VINES_PLANT, Material.GLOW_LICHEN, Material.LIGHT, Material.LAVA_CAULDRON, Material.CHISELED_BOOKSHELF));

        BlockGroup.BUTTONS.clear();
        BlockGroup.BUTTONS.addAll(Tag.BUTTONS.getValues());
        BlockGroup.PRESSURE_PLATES.clear();
        BlockGroup.PRESSURE_PLATES.addAll(Tag.PRESSURE_PLATES.getValues());

        for (Material value : Tag.DOORS.getValues()) {
            if (!BlockGroup.DOORS.contains(value)) {
                BlockGroup.DOORS.add(value);
            }
        }
        for (Material value : Tag.FENCE_GATES.getValues()) {
            if (!BlockGroup.INTERACT_BLOCKS.contains(value)) {
                BlockGroup.INTERACT_BLOCKS.add(value);
            }
            if (!BlockGroup.SAFE_INTERACT_BLOCKS.contains(value)) {
                BlockGroup.SAFE_INTERACT_BLOCKS.add(value);
            }
        }
        for (Material value : Tag.WOODEN_TRAPDOORS.getValues()) {
            if (!BlockGroup.INTERACT_BLOCKS.contains(value)) {
                BlockGroup.INTERACT_BLOCKS.add(value);
            }
            if (!BlockGroup.SAFE_INTERACT_BLOCKS.contains(value)) {
                BlockGroup.SAFE_INTERACT_BLOCKS.add(value);
            }
        }
        for (Material value : Tag.CEILING_HANGING_SIGNS.getValues()) {
            if (!BlockGroup.TRACK_BOTTOM.contains(value)) {
                BlockGroup.TRACK_BOTTOM.add(value);
            }
        }
        for (Material value : Tag.WALL_SIGNS.getValues()) {
            if (!BlockGroup.TRACK_SIDE.contains(value)) {
                BlockGroup.TRACK_SIDE.add(value);
            }
        }
        for (Material value : Tag.SAPLINGS.getValues()) {
            if (!BlockGroup.TRACK_TOP.contains(value)) {
                BlockGroup.TRACK_TOP.add(value);
            }
            if (!BlockGroup.NON_ATTACHABLE.contains(value)) {
                BlockGroup.NON_ATTACHABLE.add(value);
            }
        }
        for (Material value : Tag.FLOWERS.getValues()) {
            if (!BlockGroup.TRACK_TOP.contains(value)) {
                BlockGroup.TRACK_TOP.add(value);
            }
            if (!BlockGroup.NON_ATTACHABLE.contains(value)) {
                BlockGroup.NON_ATTACHABLE.add(value);
            }
        }
        for (Material value : Tag.SIGNS.getValues()) {
            if (!Tag.WALL_SIGNS.isTagged(value) && !BlockGroup.TRACK_TOP.contains(value)) {
                BlockGroup.TRACK_TOP.add(value);
            }
        }
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
    public String parseLegacyName(String name) {
        switch (name) {
            case "GRASS_PATH":
                name = "DIRT_PATH";
                break;
            case "GRASS":
                name = "SHORT_GRASS";
                break;
            case "SCUTE":
                name = "TURTLE_SCUTE";
                break;
            default:
                break;
        }

        // fallback until this method is moved up into v1_21
        if (name.equals("SHORT_GRASS") && Material.getMaterial(name) == null) {
            name = "GRASS";
        }

        return name;
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
    public boolean isDecoratedPot(Material material) {
        return material == Material.DECORATED_POT;
    }

    @Override
    public boolean isSuspiciousBlock(Material material) {
        return material == Material.SUSPICIOUS_GRAVEL || material == Material.SUSPICIOUS_SAND;
    }

    @Override
    public boolean isSign(Material material) {
        return Tag.ALL_SIGNS.isTagged(material);
    }

    @Override
    public boolean isChiseledBookshelf(Material material) {
        return material == Material.CHISELED_BOOKSHELF;
    }

    @Override
    public boolean isBookshelfBook(Material material) {
        return Tag.ITEMS_BOOKSHELF_BOOKS.isTagged(material);
    }

    @Override
    public ItemStack getChiseledBookshelfBook(BlockState blockState, PlayerInteractEvent event) {
        try {
            if (hasClickedPosition == null) {
                hasClickedPosition = true;
                PlayerInteractEvent.class.getMethod("getClickedPosition"); // Bukkit 1.20.1+
            }
            else if (Boolean.FALSE.equals(hasClickedPosition)) {
                return null;
            }

            ChiseledBookshelf chiseledBookshelf = (ChiseledBookshelf) blockState;
            ItemStack book = chiseledBookshelf.getInventory().getItem(chiseledBookshelf.getSlot(event.getClickedPosition()));
            return book == null ? new ItemStack(Material.AIR) : book;
        }
        catch (Exception e) {
            hasClickedPosition = false;
            return null;
        }
    }

    @Override
    public String getLine(Sign sign, int line) {
        if (line < 4) {
            return sign.getSide(Side.FRONT).getLine(line);
        }
        else {
            return sign.getSide(Side.BACK).getLine(line - 4);
        }
    }

    @Override
    public void setLine(Sign sign, int line, String string) {
        if (string == null) {
            string = "";
        }

        if (line < 4) {
            sign.getSide(Side.FRONT).setLine(line, string);
        }
        else {
            sign.getSide(Side.BACK).setLine(line - 4, string);
        }
    }

    @Override
    public boolean isSignFront(SignChangeEvent event) {
        return event.getSide().equals(Side.FRONT);
    }

    @Override
    public ItemStack getArrowMeta(Arrow arrow, ItemStack itemStack) {
        try {
            if (hasBasePotionType == null) {
                hasBasePotionType = true;
                Arrow.class.getMethod("getBasePotionType"); // Bukkit 1.20.2+
            }
            else if (Boolean.FALSE.equals(hasBasePotionType)) {
                return super.getArrowMeta(arrow, itemStack);
            }

            PotionType potionType = arrow.getBasePotionType();
            Color color = arrow.getColor();
            if (potionType != null || color != null) {
                itemStack = new ItemStack(Material.TIPPED_ARROW);
                PotionMeta meta = (PotionMeta) itemStack.getItemMeta();
                meta.setBasePotionType(potionType);
                meta.setColor(color);
                for (PotionEffect effect : arrow.getCustomEffects()) {
                    meta.addCustomEffect(effect, false);
                }
                itemStack.setItemMeta(meta);
            }

            return itemStack;
        }
        catch (Exception e) {
            hasBasePotionType = false;
            return super.getArrowMeta(arrow, itemStack);
        }
    }

}
