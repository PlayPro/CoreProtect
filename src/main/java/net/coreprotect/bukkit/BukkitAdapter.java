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
import net.coreprotect.utility.Util;

public class BukkitAdapter implements BukkitInterface {

    public static BukkitInterface ADAPTER;
    public static final int BUKKIT_V1_13 = 13;
    public static final int BUKKIT_V1_14 = 14;
    public static final int BUKKIT_V1_15 = 15;
    public static final int BUKKIT_V1_16 = 16;
    public static final int BUKKIT_V1_17 = 17;
    public static final int BUKKIT_V1_18 = 18;
    public static final int BUKKIT_V1_19 = 19;
    public static final int BUKKIT_V1_20 = 20;
    public static final int BUKKIT_V1_21 = 21;

    public static void loadAdapter() {
        switch (ConfigHandler.SERVER_VERSION) {
            case BUKKIT_V1_13:
            case BUKKIT_V1_14:
            case BUKKIT_V1_15:
                BukkitAdapter.ADAPTER = new BukkitAdapter();
                break;
            case BUKKIT_V1_16:
                BukkitAdapter.ADAPTER = new Bukkit_v1_16();
                break;
            case BUKKIT_V1_17:
                BukkitAdapter.ADAPTER = new Bukkit_v1_17();
                break;
            case BUKKIT_V1_18:
                BukkitAdapter.ADAPTER = new Bukkit_v1_18();
                break;
            case BUKKIT_V1_19:
                BukkitAdapter.ADAPTER = new Bukkit_v1_19();
                break;
            case BUKKIT_V1_20:
                BukkitAdapter.ADAPTER = new Bukkit_v1_20();
                break;
            case BUKKIT_V1_21:
            default:
                BukkitAdapter.ADAPTER = new Bukkit_v1_21();
                break;
        }
    }

    @Override
    public String parseLegacyName(String name) {
        return name;
    }

    @Override
    public int getLegacyBlockId(Material material) {
        return -1;
    }

    @Override
    public boolean getEntityMeta(LivingEntity entity, List<Object> info) {
        return false;
    }

    @Override
    public boolean setEntityMeta(Entity entity, Object value, int count) {
        return false;
    }

    @Override
    public boolean getItemMeta(ItemMeta itemMeta, List<Map<String, Object>> list, List<List<Map<String, Object>>> metadata, int slot) {
        return false;
    }

    @Override
    public boolean setItemMeta(Material rowType, ItemStack itemstack, List<Map<String, Object>> map) {
        return false;
    }

    @Override
    public boolean isAttached(Block block, Block scanBlock, BlockData blockData, int scanMin) {
        if (blockData instanceof Directional) {
            return (scanMin < 5 && scanBlock.getRelative(((Directional) blockData).getFacing().getOppositeFace()).getLocation().equals(block.getLocation()));
        }

        return true; // unvalidated attachments default to true
    }

    @Override
    public boolean isWall(BlockData blockData) {
        return false;
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
    public boolean isItemFrame(Material material) {
        return (material == Material.ITEM_FRAME);
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
    public boolean isGlowing(Sign sign, boolean isFront) {
        return false;
    }

    @Override
    public boolean isWaxed(Sign sign) {
        return false;
    }

    @Override
    public boolean isInvisible(Material material) {
        return Util.isAir(material);
    }

    @Override
    public ItemStack adjustIngredient(MerchantRecipe recipe, ItemStack itemStack) {
        return null;
    }

    @Override
    public void setGlowing(Sign sign, boolean isFront, boolean isGlowing) {
        return;
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
        return;
    }

    @Override
    public int getColor(Sign sign, boolean isFront) {
        if (isFront) {
            return sign.getColor().getColor().asRGB();
        }

        return 0;
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
            default:
        }

        return material;
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

    @Override
    public EntityType getEntityType(Material material) {
        switch (material) {
            case END_CRYSTAL:
                return EntityType.valueOf("ENDER_CRYSTAL");
            default:
                return EntityType.UNKNOWN;
        }
    }

    @Override
    public Object getRegistryKey(Object value) {
        return value;
    }

    @Override
    public Object getRegistryValue(String key, Object tClass) {
        return null;
    }

}
