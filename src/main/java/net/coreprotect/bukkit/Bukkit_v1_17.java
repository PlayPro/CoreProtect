package net.coreprotect.bukkit;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.bukkit.Chunk;
import org.bukkit.DyeColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
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
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.inventory.meta.ItemMeta;

import net.coreprotect.model.BlockGroup;
import net.coreprotect.utility.BlockUtils;
import net.coreprotect.utility.ItemUtils;
import net.coreprotect.utility.MaterialUtils;

/**
 * Bukkit adapter implementation for Minecraft 1.17.
 * Provides version-specific implementations for the BukkitInterface
 * to handle features introduced in the 1.17 update.
 */
public class Bukkit_v1_17 extends BukkitAdapter {

    private static final boolean HAS_GLOWING_SIGN_UPDATE = hasGlowingSignUpdate();

    /**
     * Initializes the Bukkit_v1_17 adapter with 1.17-specific block groups.
     * Sets up collections of blocks with similar behavior for efficient handling.
     */
    public Bukkit_v1_17() {
        initializeBlockGroups();
    }

    /**
     * Initializes all the block groups for Minecraft 1.17.
     * This includes new blocks like amethyst, candles, pointed dripstone, etc.
     */
    private void initializeBlockGroups() {
        BlockGroup.TRACK_ANY.addAll(List.of(Material.SMALL_AMETHYST_BUD, Material.MEDIUM_AMETHYST_BUD, Material.LARGE_AMETHYST_BUD, Material.AMETHYST_CLUSTER, Material.GLOW_LICHEN));

        BlockGroup.TRACK_TOP.addAll(List.of(Material.AZALEA, Material.FLOWERING_AZALEA, Material.SMALL_DRIPLEAF, Material.BIG_DRIPLEAF));

        BlockGroup.TRACK_TOP_BOTTOM.addAll(List.of(Material.POINTED_DRIPSTONE, Material.BIG_DRIPLEAF_STEM));

        BlockGroup.TRACK_BOTTOM.addAll(List.of(Material.CAVE_VINES, Material.CAVE_VINES_PLANT, Material.HANGING_ROOTS));

        BlockGroup.VINES.add(Material.CAVE_VINES);

        BlockGroup.CANDLES.addAll(Tag.CANDLES.getValues());
        BlockGroup.CANDLES.addAll(Tag.CANDLE_CAKES.getValues());

        BlockGroup.AMETHYST.addAll(List.of(Material.SMALL_AMETHYST_BUD, Material.MEDIUM_AMETHYST_BUD, Material.LARGE_AMETHYST_BUD, Material.AMETHYST_CLUSTER));

        BlockGroup.UPDATE_STATE.addAll(List.of(Material.SMALL_AMETHYST_BUD, Material.MEDIUM_AMETHYST_BUD, Material.LARGE_AMETHYST_BUD, Material.AMETHYST_CLUSTER, Material.CAVE_VINES, Material.CAVE_VINES_PLANT, Material.GLOW_LICHEN, Material.LIGHT, Material.LAVA_CAULDRON));

        BlockGroup.NON_ATTACHABLE.addAll(List.of(Material.CAVE_VINES, Material.CAVE_VINES_PLANT, Material.LIGHT, Material.SMALL_DRIPLEAF, Material.BIG_DRIPLEAF, Material.BIG_DRIPLEAF_STEM, Material.GLOW_LICHEN, Material.HANGING_ROOTS));

        BlockGroup.VERTICAL_TOP_BOTTOM.addAll(List.of(Material.BIG_DRIPLEAF_STEM, Material.POINTED_DRIPSTONE));
        BlockGroup.VERTICAL_BOTTOM.addAll(List.of(Material.CAVE_VINES, Material.CAVE_VINES_PLANT));
    }

    @Override
    public String parseLegacyName(String name) {
        switch (name) {
            case "GRASS_PATH":
                return "DIRT_PATH";
            default:
                return name;
        }
    }

    @Override
    public int getLegacyBlockId(Material material) {
        switch (material) {
            case DIRT_PATH:
                return MaterialUtils.getBlockId("GRASS_PATH", false);
            default:
                return -1;
        }
    }

    @Override
    public boolean getEntityMeta(LivingEntity entity, List<Object> info) {
        if (entity instanceof Axolotl) {
            Axolotl axolotl = (Axolotl) entity;
            info.add(axolotl.getVariant());
            return true;
        }
        else if (entity instanceof Goat) {
            Goat goat = (Goat) entity;
            info.add(goat.isScreaming());
            return true;
        }

        return super.getEntityMeta(entity, info);
    }

    @Override
    public boolean setEntityMeta(Entity entity, Object value, int count) {
        if (entity instanceof Axolotl) {
            Axolotl axolotl = (Axolotl) entity;
            if (count == 0) {
                try {
                    org.bukkit.entity.Axolotl.Variant variant = value instanceof String
                            ? org.bukkit.entity.Axolotl.Variant.valueOf((String) value)
                            : (org.bukkit.entity.Axolotl.Variant) value;
                    axolotl.setVariant(variant);
                    return true;
                }
                catch (IllegalArgumentException | ClassCastException exception) {
                    return false;
                }
            }
        }
        else if (entity instanceof Goat) {
            Goat goat = (Goat) entity;
            if (count == 0 && value instanceof Boolean) {
                goat.setScreaming((Boolean) value);
                return true;
            }
        }

        return super.setEntityMeta(entity, value, count);
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
                    Map<String, Object> itemMap = ItemUtils.serializeItemStack(itemStack, null, slot);
                    if (itemMap.size() > 0) {
                        list.add(itemMap);
                    }
                }
                metadata.add(list);
            }
            return true;
        }

        return super.getItemMeta(itemMeta, list, metadata, slot);
    }

    @Override
    public boolean isBundle(Material material) {
        return BlockGroup.BUNDLES.contains(material);
    }


    @Override
    public boolean setItemMeta(Material rowType, ItemStack itemstack, List<Map<String, Object>> map) {
        if (BukkitAdapter.ADAPTER.isBundle(rowType)) {
            ItemMeta itemMeta = itemstack.getItemMeta();
            if (!(itemMeta instanceof BundleMeta)) {
                return false;
            }
            BundleMeta meta = (BundleMeta) itemMeta;
            for (Map<String, Object> itemData : map) {
                ItemStack itemStack = ItemUtils.unserializeItemStack(itemData);
                if (itemStack != null && !BlockUtils.isAir(itemStack.getType())) {
                    meta.addItem(itemStack);
                }
            }
            itemstack.setItemMeta(meta);
            return true;
        }

        return super.setItemMeta(rowType, itemstack, map);
    }

    @Override
    public boolean isAttached(Block block, Block scanBlock, BlockData blockData, int scanMin) {
        if (blockData instanceof PointedDripstone) {
            PointedDripstone pointedDripstone = (PointedDripstone) blockData;
            BlockFace blockFace = pointedDripstone.getVerticalDirection();
            boolean adjacent = scanBlock.getRelative(blockFace.getOppositeFace()).getLocation().equals(block.getLocation());
            return adjacent;
        }

        return super.isAttached(block, scanBlock, blockData, scanMin);
    }

    @Override
    public int getMinHeight(World world) {
        return world.getMinHeight();
    }

    @Override
    public Material getBucketContents(Material material) {
        return (material == Material.POWDER_SNOW_BUCKET) ? Material.POWDER_SNOW : Material.AIR;
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
    public void sendSignUpdate(Player player, Location location, Sign sign) {
        if (HAS_GLOWING_SIGN_UPDATE) {
            player.sendSignChange(location, sign.getLines(), sign.getColor(), sign.isGlowingText());
        }
        else {
            super.sendSignUpdate(player, location, sign);
        }
    }

    private static boolean hasGlowingSignUpdate() {
        try {
            Player.class.getMethod("sendSignChange", Location.class, String[].class, DyeColor.class, boolean.class);
            return true;
        }
        catch (NoSuchMethodException e) {
            return false;
        }
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

    @Override
    public Material getPlantSeeds(Material material) {
        switch (material) {
            case CAVE_VINES:
            case CAVE_VINES_PLANT:
                return Material.GLOW_BERRIES;
            default:
                return super.getPlantSeeds(material);
        }
    }

    @Override
    public boolean isChunkEntitiesLoaded(Chunk chunk) {
        return chunk.isEntitiesLoaded();
    }
}
