package net.coreprotect.bukkit;

import java.util.List;
import java.util.Map;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.inventory.meta.ItemMeta;

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

    public static void loadAdapter() {
        switch (ConfigHandler.SERVER_VERSION) {
            case BUKKIT_V1_13:
            case BUKKIT_V1_14:
                BukkitAdapter.ADAPTER = new BukkitAdapter();
                break;
            case BUKKIT_V1_15:
                BukkitAdapter.ADAPTER = new Bukkit_v1_15();
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
            default:
                BukkitAdapter.ADAPTER = new Bukkit_v1_19();
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
    public void sendSignChange(Player player, Sign sign) {
        return;
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
    public boolean isGlowing(Sign sign) {
        return false;
    }

    @Override
    public void setGlowing(Sign sign, boolean set) {
        return;
    }

    @Override
    public boolean isInvisible(Material material) {
        return Util.isAir(material);
    }

    @Override
    public ItemStack adjustIngredient(MerchantRecipe recipe, ItemStack itemStack) {
        return null;
    }

}
