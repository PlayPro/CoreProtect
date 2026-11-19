package net.coreprotect.bukkit;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import org.bukkit.Color;
import org.bukkit.DyeColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.block.ChiseledBookshelf;
import org.bukkit.block.Sign;
import org.bukkit.block.TileState;
import org.bukkit.block.sign.Side;
import org.bukkit.block.sign.SignSide;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionType;
import org.bukkit.util.Vector;

import net.coreprotect.model.BlockGroup;

/**
 * Bukkit adapter implementation for Minecraft 1.20.
 * Provides version-specific implementations for the BukkitInterface
 * to handle features introduced in the 1.20 update, including:
 * - Updated sign handling with front/back sides
 * - New block types (chiseled bookshelf, decorated pot)
 * - Suspicious blocks (sand/gravel)
 * - Arrow potion handling
 */
public class Bukkit_v1_20 extends Bukkit_v1_19 {

    private static final boolean HAS_SIGN_TILE_UPDATE = hasPublicMethod(Player.class, "sendBlockUpdate", Location.class, TileState.class);
    private static final boolean HAS_CLICKED_POSITION = hasPublicMethod(PlayerInteractEvent.class, "getClickedPosition")
            && hasPublicMethod(ChiseledBookshelf.class, "getSlot", Vector.class);
    private static final boolean HAS_BASE_POTION_TYPE = hasPublicMethod(Arrow.class, "getBasePotionType")
            && hasPublicMethod(PotionMeta.class, "setBasePotionType", PotionType.class);
    private static final boolean HAS_ITEM_NAME = hasPublicMethod(ItemMeta.class, "getItemName")
            && hasPublicMethod(ItemMeta.class, "hasItemName");
    private static final boolean HAS_SPAWN_FUNCTION = hasPublicMethod(World.class, "spawn", Location.class, Class.class, Consumer.class);

    private volatile boolean missingSignTargetSide;

    /**
     * Initializes the Bukkit_v1_20 adapter with 1.20-specific block groups and mappings.
     * Sets up collections of blocks with similar behavior for efficient handling.
     */
    public Bukkit_v1_20() {
        initializeBlockGroups();
        initializeTaggedBlocks();
    }

    /**
     * Initializes the block groups specific to Minecraft 1.20.
     */
    private void initializeBlockGroups() {
        BlockGroup.CONTAINERS = new HashSet<>(Arrays.asList(Material.JUKEBOX, Material.DISPENSER, Material.CHEST, Material.FURNACE, Material.BREWING_STAND, Material.TRAPPED_CHEST, Material.HOPPER, Material.DROPPER, Material.ARMOR_STAND, Material.ITEM_FRAME, Material.SHULKER_BOX, Material.BLACK_SHULKER_BOX, Material.BLUE_SHULKER_BOX, Material.BROWN_SHULKER_BOX, Material.CYAN_SHULKER_BOX, Material.GRAY_SHULKER_BOX, Material.GREEN_SHULKER_BOX, Material.LIGHT_BLUE_SHULKER_BOX, Material.LIME_SHULKER_BOX, Material.MAGENTA_SHULKER_BOX, Material.ORANGE_SHULKER_BOX, Material.PINK_SHULKER_BOX, Material.PURPLE_SHULKER_BOX, Material.RED_SHULKER_BOX, Material.LIGHT_GRAY_SHULKER_BOX, Material.WHITE_SHULKER_BOX, Material.YELLOW_SHULKER_BOX, Material.BARREL, Material.BLAST_FURNACE, Material.SMOKER, Material.LECTERN, Material.CHISELED_BOOKSHELF, Material.DECORATED_POT));

        BlockGroup.UPDATE_STATE = new HashSet<>(Arrays.asList(Material.TORCH, Material.WALL_TORCH, Material.REDSTONE_WIRE, Material.RAIL, Material.POWERED_RAIL, Material.DETECTOR_RAIL, Material.FURNACE, Material.BLAST_FURNACE, Material.SMOKER, Material.LEVER, Material.REDSTONE_TORCH, Material.REDSTONE_WALL_TORCH, Material.GLOWSTONE, Material.JACK_O_LANTERN, Material.REPEATER, Material.REDSTONE_LAMP, Material.BEACON, Material.COMPARATOR, Material.DAYLIGHT_DETECTOR, Material.REDSTONE_BLOCK, Material.HOPPER, Material.CHEST, Material.TRAPPED_CHEST, Material.ACTIVATOR_RAIL, Material.SOUL_TORCH, Material.SOUL_WALL_TORCH, Material.SHROOMLIGHT, Material.RESPAWN_ANCHOR, Material.CRYING_OBSIDIAN, Material.TARGET, Material.SMALL_AMETHYST_BUD, Material.MEDIUM_AMETHYST_BUD, Material.LARGE_AMETHYST_BUD, Material.AMETHYST_CLUSTER, Material.CAVE_VINES, Material.CAVE_VINES_PLANT, Material.GLOW_LICHEN, Material.LIGHT, Material.LAVA_CAULDRON, Material.CHISELED_BOOKSHELF));
    }

    /**
     * Initializes blocks based on Bukkit Tags for 1.20.
     */
    private void initializeTaggedBlocks() {
        // Clear and update button blocks from tag
        BlockGroup.BUTTONS.clear();
        BlockGroup.BUTTONS.addAll(Tag.BUTTONS.getValues());

        // Clear and update pressure plate blocks from tag
        BlockGroup.PRESSURE_PLATES.clear();
        BlockGroup.PRESSURE_PLATES.addAll(Tag.PRESSURE_PLATES.getValues());

        // Add all doors from tag
        addMissingTaggedBlocks(Tag.DOORS.getValues(), BlockGroup.DOORS);

        // Add all fence gates to interaction blocks
        addMissingTaggedBlocks(Tag.FENCE_GATES.getValues(), BlockGroup.INTERACT_BLOCKS);
        addMissingTaggedBlocks(Tag.FENCE_GATES.getValues(), BlockGroup.SAFE_INTERACT_BLOCKS);

        // Add all wooden trapdoors to interaction blocks
        addMissingTaggedBlocks(Tag.WOODEN_TRAPDOORS.getValues(), BlockGroup.INTERACT_BLOCKS);
        addMissingTaggedBlocks(Tag.WOODEN_TRAPDOORS.getValues(), BlockGroup.SAFE_INTERACT_BLOCKS);

        // Add hanging signs to bottom-tracked blocks
        addMissingTaggedBlocks(Tag.CEILING_HANGING_SIGNS.getValues(), BlockGroup.TRACK_BOTTOM);

        // Add wall signs to side-tracked blocks
        addMissingTaggedBlocks(Tag.WALL_SIGNS.getValues(), BlockGroup.TRACK_SIDE);

        // Add saplings to top-tracked and non-attachable blocks
        addMissingTaggedBlocks(Tag.SAPLINGS.getValues(), BlockGroup.TRACK_TOP);
        addMissingTaggedBlocks(Tag.SAPLINGS.getValues(), BlockGroup.NON_ATTACHABLE);

        // Add flowers to top-tracked and non-attachable blocks
        addMissingTaggedBlocks(Tag.FLOWERS.getValues(), BlockGroup.TRACK_TOP);
        addMissingTaggedBlocks(Tag.FLOWERS.getValues(), BlockGroup.NON_ATTACHABLE);

        // Add signs (except wall signs) to top-tracked blocks
        for (Material value : Tag.SIGNS.getValues()) {
            if (!Tag.WALL_SIGNS.isTagged(value) && !BlockGroup.TRACK_TOP.contains(value)) {
                BlockGroup.TRACK_TOP.add(value);
            }
        }
    }

    /**
     * Helper method to add missing blocks from a tag to a block group.
     * 
     * @param taggedBlocks
     *            The collection of blocks from a tag
     * @param targetGroup
     *            The block group to add missing blocks to
     */
    private void addMissingTaggedBlocks(Iterable<Material> taggedBlocks, Set<Material> targetGroup) {
        for (Material value : taggedBlocks) {
            if (!targetGroup.contains(value)) {
                targetGroup.add(value);
            }
        }
    }

    @Override
    public void sendSignUpdate(Player player, Location location, Sign sign) {
        if (HAS_SIGN_TILE_UPDATE) {
            player.sendBlockUpdate(location, sign);
        }
        else {
            super.sendSignUpdate(player, location, sign);
        }
    }

    @Override
    public Boolean getSignInteractionSide(Sign sign, Player player) {
        if (missingSignTargetSide) {
            return null;
        }

        SignSide target;
        try {
            target = sign.getTargetSide(player);
        }
        catch (NoSuchMethodError e) {
            missingSignTargetSide = true;
            return null;
        }
        return target == null ? null : target == sign.getSide(Side.FRONT);
    }

    @Override
    public void setGlowing(Sign sign, boolean isFront, boolean isGlowing) {
        Side side = isFront ? Side.FRONT : Side.BACK;
        sign.getSide(side).setGlowingText(isGlowing);
    }

    @Override
    public String parseLegacyName(String name) {
        switch (name) {
            case "GRASS_PATH":
                return "DIRT_PATH";
            case "GRASS":
                return "SHORT_GRASS";
            case "SCUTE":
                return "TURTLE_SCUTE";
            default:
                // Fallback until this method is moved up into v1_21
                if ("SHORT_GRASS".equals(name) && Material.getMaterial(name) == null) {
                    return "GRASS";
                }
                return name;
        }
    }

    @Override
    public void setColor(Sign sign, boolean isFront, int color) {
        Side side = isFront ? Side.FRONT : Side.BACK;
        sign.getSide(side).setColor(DyeColor.getByColor(Color.fromRGB(color)));
    }

    @Override
    public void setWaxed(Sign sign, boolean isWaxed) {
        sign.setWaxed(isWaxed);
    }

    @Override
    public int getColor(Sign sign, boolean isFront) {
        Side side = isFront ? Side.FRONT : Side.BACK;
        return sign.getSide(side).getColor().getColor().asRGB();
    }

    @Override
    public boolean isGlowing(Sign sign, boolean isFront) {
        Side side = isFront ? Side.FRONT : Side.BACK;
        return sign.getSide(side).isGlowingText();
    }

    @Override
    public boolean isWaxed(Sign sign) {
        return sign.isWaxed();
    }

    @Override
    public Material getPlantSeeds(Material material) {
        switch (material) {
            case TORCHFLOWER_CROP:
                return Material.TORCHFLOWER_SEEDS;
            case PITCHER_CROP:
                return Material.PITCHER_POD;
            default:
                return super.getPlantSeeds(material);
        }
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
        if (!HAS_CLICKED_POSITION || !(blockState instanceof ChiseledBookshelf) || event == null) {
            return null;
        }

        Vector clickedPosition = event.getClickedPosition();
        if (clickedPosition == null) {
            return null;
        }

        ChiseledBookshelf bookshelf = (ChiseledBookshelf) blockState;
        int slot = bookshelf.getSlot(clickedPosition);
        if (slot < 0) {
            return null;
        }

        ItemStack book = bookshelf.getInventory().getItem(slot);
        return book == null ? new ItemStack(Material.AIR) : book;
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
        string = string == null ? "" : string;

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
    public String getItemName(ItemMeta itemMeta) {
        return HAS_ITEM_NAME && itemMeta.hasItemName() ? itemMeta.getItemName() : "";
    }

    @Override
    public <T extends Entity> T spawn(World world, Location location, Class<T> entityClass, Consumer<? super T> function) {
        return HAS_SPAWN_FUNCTION ? world.spawn(location, entityClass, function) : super.spawn(world, location, entityClass, function);
    }

    @Override
    public ItemStack getArrowMeta(Arrow arrow, ItemStack itemStack) {
        if (!HAS_BASE_POTION_TYPE) {
            return super.getArrowMeta(arrow, itemStack);
        }

        PotionType potionType = arrow.getBasePotionType();
        Color color = arrow.getColor();
        if (potionType == null && color == null && !arrow.hasCustomEffects()) {
            return itemStack;
        }

        ItemStack tippedArrow = new ItemStack(Material.TIPPED_ARROW);
        PotionMeta meta = (PotionMeta) tippedArrow.getItemMeta();
        meta.setBasePotionType(potionType);
        meta.setColor(color);
        for (PotionEffect effect : arrow.getCustomEffects()) {
            meta.addCustomEffect(effect, false);
        }

        tippedArrow.setItemMeta(meta);
        return tippedArrow;
    }

    @Override
    public Material getExplodedBlock(BlockExplodeEvent event){
        // accoding to the Bukkit docs this will always return air
        return event.getExplodedBlockState().getType();
    }

    @Override
    public void addMerchantRecipeMeta(MerchantRecipe recipe, List<Object> recipeData) {
        recipeData.add(recipe.getDemand());
    }

    @Override
    public void setMerchantRecipeMeta(MerchantRecipe recipe, List<?> recipeData) {
        if (recipeData.size() > 7 && recipeData.get(7) instanceof Number) {
            recipe.setDemand(((Number) recipeData.get(7)).intValue());
        }
    }

}
