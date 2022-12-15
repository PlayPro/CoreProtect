package net.coreprotect.bukkit;

import java.util.List;
import java.util.Map;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.inventory.meta.ItemMeta;

public interface BukkitInterface {

    public ItemStack adjustIngredient(MerchantRecipe recipe, ItemStack itemStack);

    public Material getBucketContents(Material material);

    public Material getFrameType(Entity entity);

    public Material getFrameType(EntityType type);

    public Class<?> getFrameClass(Material material);

    public String parseLegacyName(String name);

    public boolean getEntityMeta(LivingEntity entity, List<Object> info);

    public boolean setEntityMeta(Entity entity, Object value, int count);

    public boolean getItemMeta(ItemMeta itemMeta, List<Map<String, Object>> list, List<List<Map<String, Object>>> metadata, int slot);

    public boolean setItemMeta(Material rowType, ItemStack itemstack, List<Map<String, Object>> map);

    public boolean isAttached(Block block, Block scanBlock, BlockData blockData, int scanMin);

    public boolean isWall(BlockData blockData);

    public boolean isItemFrame(Material material);

    public boolean isGlowing(Sign sign);

    public boolean isInvisible(Material material);

    public int getMinHeight(World world);

    public int getLegacyBlockId(Material material);

    public void setGlowing(Sign sign, boolean b);

}
