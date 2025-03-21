package net.coreprotect.bukkit;

import java.util.List;
import java.util.Map;

import org.bukkit.Color;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.FaceAttachable;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionData;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionType;

import net.coreprotect.config.ConfigHandler;
import net.coreprotect.utility.BlockUtils;

/**
 * Base adapter implementation for Bukkit API compatibility.
 * Provides default implementations for methods that work across multiple Minecraft versions.
 * Version-specific implementations extend this class to provide specialized behavior.
 */
public class BukkitAdapter implements BukkitInterface {

    /** The currently active adapter instance */
    public static BukkitInterface ADAPTER;

    // Version constants for Bukkit implementations
    public static final int BUKKIT_V1_13 = 13;
    public static final int BUKKIT_V1_14 = 14;
    public static final int BUKKIT_V1_15 = 15;
    public static final int BUKKIT_V1_16 = 16;
    public static final int BUKKIT_V1_17 = 17;
    public static final int BUKKIT_V1_18 = 18;
    public static final int BUKKIT_V1_19 = 19;
    public static final int BUKKIT_V1_20 = 20;
    public static final int BUKKIT_V1_21 = 21;

    /**
     * Initializes the appropriate Bukkit adapter based on the server version.
     * This method should be called during plugin initialization.
     */
    public static void loadAdapter() {
        switch (ConfigHandler.SERVER_VERSION) {
            case BUKKIT_V1_13:
            case BUKKIT_V1_14:
            case BUKKIT_V1_15:
            case BUKKIT_V1_16:
                ADAPTER = new BukkitAdapter();
                break;
            case BUKKIT_V1_17:
                ADAPTER = new Bukkit_v1_17();
                break;
            case BUKKIT_V1_18:
                ADAPTER = new Bukkit_v1_18();
                break;
            case BUKKIT_V1_19:
                ADAPTER = new Bukkit_v1_19();
                break;
            case BUKKIT_V1_20:
                ADAPTER = new Bukkit_v1_20();
                break;
            case BUKKIT_V1_21:
            default:
                ADAPTER = new Bukkit_v1_21();
                break;
        }
    }

    // -------------------- Basic data conversion methods --------------------

    @Override
    public String parseLegacyName(String name) {
        return name;
    }

    @Override
    public int getLegacyBlockId(Material material) {
        return -1;
    }

    // -------------------- Entity methods --------------------

    @Override
    public boolean getEntityMeta(LivingEntity entity, List<Object> info) {
        return false;
    }

    @Override
    public boolean setEntityMeta(Entity entity, Object value, int count) {
        return false;
    }

    @Override
    public void getWolfVariant(org.bukkit.entity.Wolf wolf, List<Object> info) {
        // Base implementation does nothing - Wolf variants only exist in 1.21+
    }

    @Override
    public void setWolfVariant(org.bukkit.entity.Wolf wolf, Object value) {
        // Base implementation does nothing - Wolf variants only exist in 1.21+
    }

    @Override
    public EntityType getEntityType(Material material) {
        switch (material) {
            case END_CRYSTAL:
                return EntityType.valueOf("ENDER_CRYSTAL");
            default:
                return EntityType.UNKNOWN;
        }
    }

    // -------------------- Item handling methods --------------------

    @Override
    public boolean getItemMeta(ItemMeta itemMeta, List<Map<String, Object>> list, List<List<Map<String, Object>>> metadata, int slot) {
        return false;
    }

    @Override
    public boolean setItemMeta(Material rowType, ItemStack itemstack, List<Map<String, Object>> map) {
        return false;
    }

    @Override
    public Material getPlantSeeds(Material material) {
        switch (material) {
            case WHEAT:
                return Material.WHEAT_SEEDS;
            case PUMPKIN_STEM:
                return Material.PUMPKIN_SEEDS;
            case MELON_STEM:
                return Material.MELON_SEEDS;
            case BEETROOTS:
                return Material.BEETROOT_SEEDS;
            default:
                return material;
        }
    }

    @Override
    public ItemStack adjustIngredient(MerchantRecipe recipe, ItemStack itemStack) {
        return null;
    }

    @Override
    public ItemStack getArrowMeta(Arrow arrow, ItemStack itemStack) {
        PotionData data = arrow.getBasePotionData();
        if (data.getType() != PotionType.valueOf("UNCRAFTABLE")) {
            itemStack = new ItemStack(Material.TIPPED_ARROW);
            PotionMeta meta = (PotionMeta) itemStack.getItemMeta();
            meta.setBasePotionData(data);
            for (PotionEffect effect : arrow.getCustomEffects()) {
                meta.addCustomEffect(effect, false);
            }
            itemStack.setItemMeta(meta);
        }

        return itemStack;
    }

    // -------------------- Block methods --------------------

    @Override
    public boolean isAttached(Block block, Block scanBlock, BlockData blockData, int scanMin) {
        if (blockData instanceof Directional && blockData instanceof FaceAttachable) {
            Directional directional = (Directional) blockData;
            FaceAttachable faceAttachable = (FaceAttachable) blockData;

            boolean scanButton = false;
            switch (faceAttachable.getAttachedFace()) {
                case WALL:
                    scanButton = (scanMin < 5 && scanBlock.getRelative(directional.getFacing().getOppositeFace()).getLocation().equals(block.getLocation()));
                    break;
                case FLOOR:
                    scanButton = (scanMin == 5);
                    break;
                case CEILING:
                    scanButton = (scanMin == 6);
                    break;
                default:
                    break;
            }

            return scanButton;
        }

        return true; // unvalidated attachments default to true
    }

    @Override
    public int getMinHeight(World world) {
        return 0;
    }

    @Override
    public Material getBucketContents(Material material) {
        return Material.AIR;
    }

    @Override
    public boolean isInvisible(Material material) {
        return BlockUtils.isAir(material);
    }

    // -------------------- Special block type checkers --------------------

    @Override
    public boolean isItemFrame(Material material) {
        return material == Material.ITEM_FRAME;
    }

    @Override
    public Material getFrameType(Entity entity) {
        return Material.ITEM_FRAME;
    }

    @Override
    public Material getFrameType(EntityType type) {
        return type == EntityType.ITEM_FRAME ? Material.ITEM_FRAME : null;
    }

    @Override
    public Class<?> getFrameClass(Material material) {
        return ItemFrame.class;
    }

    @Override
    public boolean isDecoratedPot(Material material) {
        return false;
    }

    @Override
    public boolean isSuspiciousBlock(Material material) {
        return false;
    }

    @Override
    public boolean isSign(Material material) {
        return Tag.SIGNS.isTagged(material);
    }

    @Override
    public boolean isChiseledBookshelf(Material material) {
        return false;
    }

    @Override
    public boolean isBookshelfBook(Material material) {
        return false;
    }

    @Override
    public ItemStack getChiseledBookshelfBook(BlockState blockState, PlayerInteractEvent event) {
        return null;
    }

    // -------------------- Sign handling methods --------------------

    @Override
    public String getLine(Sign sign, int line) {
        if (line < 4) {
            return sign.getLine(line);
        }
        else {
            return "";
        }
    }

    @Override
    public void setLine(Sign sign, int line, String string) {
        if (string == null) {
            string = "";
        }

        if (line < 4) {
            sign.setLine(line, string);
        }
    }

    @Override
    public boolean isSignFront(SignChangeEvent event) {
        return true;
    }

    @Override
    public boolean isGlowing(Sign sign, boolean isFront) {
        return false;
    }

    @Override
    public boolean isWaxed(Sign sign) {
        return false;
    }

    @Override
    public void setGlowing(Sign sign, boolean isFront, boolean isGlowing) {
        // Base implementation does nothing
    }

    @Override
    public void setColor(Sign sign, boolean isFront, int color) {
        if (!isFront) {
            return;
        }

        sign.setColor(DyeColor.getByColor(Color.fromRGB(color)));
    }

    @Override
    public void setWaxed(Sign sign, boolean isWaxed) {
        // Base implementation does nothing
    }

    @Override
    public int getColor(Sign sign, boolean isFront) {
        if (isFront) {
            return sign.getColor().getColor().asRGB();
        }

        return 0;
    }

    // -------------------- Registry methods --------------------

    @Override
    public Object getRegistryKey(Object value) {
        return value;
    }

    @Override
    public Object getRegistryValue(String key, Object tClass) {
        return null;
    }
}
