package net.coreprotect.bukkit;

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
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.PitcherCrop;
import org.bukkit.block.sign.Side;
import org.bukkit.block.sign.SignSide;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.AbstractArrow;
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
import net.coreprotect.utility.MaterialUtils;

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

    private static final int PITCHER_CROP_DOUBLE_AGE = 3;
    private static final boolean HAS_SIGN_TILE_UPDATE = hasPublicMethod(Player.class, "sendBlockUpdate", Location.class, TileState.class);
    private static final boolean HAS_CLICKED_POSITION = hasPublicMethod(PlayerInteractEvent.class, "getClickedPosition")
            && hasPublicMethod(ChiseledBookshelf.class, "getSlot", Vector.class);
    private static final boolean HAS_BASE_POTION_TYPE = hasPublicMethod(Arrow.class, "getBasePotionType")
            && hasPublicMethod(PotionMeta.class, "setBasePotionType", PotionType.class);
    private static final boolean HAS_ARROW_ITEM = hasPublicMethod(AbstractArrow.class, "getItem");
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
        BlockGroup.TRACK_TOP.addAll(Set.of(Material.TORCHFLOWER_CROP, Material.PITCHER_CROP));
        BlockGroup.LOGGED_BY_LOWER_HALF.addAll(Set.of(Material.PITCHER_CROP, Material.PITCHER_PLANT));
        BlockGroup.DIRECTIONAL_BLOCKS.addAll(Set.of(Material.PIGLIN_HEAD, Material.PIGLIN_WALL_HEAD));
        BlockGroup.SCULK.add(Material.CALIBRATED_SCULK_SENSOR);
    }

    /**
     * Initializes the block groups specific to Minecraft 1.20.
     */
    private void initializeBlockGroups() {
        BlockGroup.CONTAINERS.addAll(List.of(Material.CHISELED_BOOKSHELF, Material.DECORATED_POT));

        BlockGroup.UPDATE_STATE.add(Material.CHISELED_BOOKSHELF);
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
        BlockGroup.DOORS.addAll(Tag.DOORS.getValues());

        // Add all fence gates to interaction blocks
        BlockGroup.INTERACT_BLOCKS.addAll(Tag.FENCE_GATES.getValues());
        BlockGroup.SAFE_INTERACT_BLOCKS.addAll(Tag.FENCE_GATES.getValues());

        // Add all wooden trapdoors to interaction blocks
        BlockGroup.INTERACT_BLOCKS.addAll(Tag.WOODEN_TRAPDOORS.getValues());
        BlockGroup.SAFE_INTERACT_BLOCKS.addAll(Tag.WOODEN_TRAPDOORS.getValues());

        // Add hanging signs to bottom-tracked blocks
        BlockGroup.TRACK_BOTTOM.addAll(Tag.CEILING_HANGING_SIGNS.getValues());

        // Add wall signs to side-tracked blocks
        BlockGroup.TRACK_SIDE.addAll(Tag.WALL_SIGNS.getValues());

        // Add saplings to top-tracked and non-attachable blocks
        BlockGroup.TRACK_TOP.addAll(Tag.SAPLINGS.getValues());
        BlockGroup.NON_ATTACHABLE.addAll(Tag.SAPLINGS.getValues());

        for (Material flower : Tag.FLOWERS.getValues()) {
            if (!Tag.LEAVES.isTagged(flower) && flower != Material.SPORE_BLOSSOM && flower != Material.CHORUS_FLOWER && flower != Material.MANGROVE_PROPAGULE) {
                BlockGroup.TRACK_TOP.add(flower);
                BlockGroup.NON_ATTACHABLE.add(flower);
            }
        }

        // Add signs (except wall signs) to top-tracked blocks
        for (Material value : Tag.SIGNS.getValues()) {
            if (!Tag.WALL_SIGNS.isTagged(value)) {
                BlockGroup.TRACK_TOP.add(value);
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
            case "GRASS":
            case "SHORT_GRASS":
                return Material.getMaterial("SHORT_GRASS") != null ? "SHORT_GRASS" : "GRASS";
            case "SCUTE":
            case "TURTLE_SCUTE":
                return Material.getMaterial("TURTLE_SCUTE") != null ? "TURTLE_SCUTE" : "SCUTE";
            default:
                return super.parseLegacyName(name);
        }
    }

    @Override
    public int getLegacyBlockId(Material material) {
        switch (material.name()) {
            case "SHORT_GRASS":
                return MaterialUtils.getBlockId("GRASS", false);
            case "TURTLE_SCUTE":
                return MaterialUtils.getBlockId("SCUTE", false);
            default:
                return super.getLegacyBlockId(material);
        }
    }

    @Override
    public void setColor(Sign sign, boolean isFront, int color) {
        DyeColor dye = getSignDyeColor(color);
        if (dye != null) {
            sign.getSide(isFront ? Side.FRONT : Side.BACK).setColor(dye);
        }
    }

    @Override
    public void setWaxed(Sign sign, boolean isWaxed) {
        sign.setWaxed(isWaxed);
    }

    @Override
    public int getColor(Sign sign, boolean isFront) {
        Side side = isFront ? Side.FRONT : Side.BACK;
        DyeColor dye = sign.getSide(side).getColor();
        return dye == null ? 0 : dye.getColor().asRGB();
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
    public boolean isSingleBlockPitcherCrop(BlockData blockData) {
        return blockData instanceof PitcherCrop && ((PitcherCrop) blockData).getAge() < PITCHER_CROP_DOUBLE_AGE;
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
        if (line < 0 || line >= 8) {
            return "";
        }
        if (line < 4) {
            return sign.getSide(Side.FRONT).getLine(line);
        }
        else {
            return sign.getSide(Side.BACK).getLine(line - 4);
        }
    }

    @Override
    public void setLine(Sign sign, int line, String string) {
        if (line < 0 || line >= 8) {
            return;
        }
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
    public ItemStack getArrowItem(AbstractArrow arrow) {
        return HAS_ARROW_ITEM ? arrow.getItem() : null;
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
