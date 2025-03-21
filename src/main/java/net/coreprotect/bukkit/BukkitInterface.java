package net.coreprotect.bukkit;

import java.util.List;
import java.util.Map;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Interface for Bukkit API compatibility across different Minecraft versions.
 * Each method provides version-specific implementations to handle differences
 * between Minecraft/Bukkit API versions.
 */
public interface BukkitInterface {

    // --------------------------------------------------------------------------
    // Block-related methods
    // --------------------------------------------------------------------------

    /**
     * Checks if a block is attached to another block.
     * 
     * @param block
     *            The base block
     * @param scanBlock
     *            The block to check for attachment
     * @param blockData
     *            The block data
     * @param scanMin
     *            The minimum scan value
     * @return true if the block is attached, false otherwise
     */
    boolean isAttached(Block block, Block scanBlock, BlockData blockData, int scanMin);

    /**
     * Gets the minimum height of a world.
     * 
     * @param world
     *            The world
     * @return The minimum height
     */
    int getMinHeight(World world);

    /**
     * Gets the legacy block ID for a material.
     * 
     * @param material
     *            The material
     * @return The legacy block ID, or -1 if not applicable
     */
    int getLegacyBlockId(Material material);

    /**
     * Gets the contents of a bucket material.
     * 
     * @param material
     *            The bucket material
     * @return The material inside the bucket, or AIR if not applicable
     */
    Material getBucketContents(Material material);

    // --------------------------------------------------------------------------
    // Material type checking methods
    // --------------------------------------------------------------------------

    /**
     * Checks if a material is an item frame.
     * 
     * @param material
     *            The material to check
     * @return true if the material is an item frame, false otherwise
     */
    boolean isItemFrame(Material material);

    /**
     * Checks if a material is invisible.
     * 
     * @param material
     *            The material to check
     * @return true if the material is invisible, false otherwise
     */
    boolean isInvisible(Material material);

    /**
     * Checks if a material is a decorated pot.
     * 
     * @param material
     *            The material to check
     * @return true if the material is a decorated pot, false otherwise
     */
    boolean isDecoratedPot(Material material);

    /**
     * Checks if a material is a suspicious block.
     * 
     * @param material
     *            The material to check
     * @return true if the material is a suspicious block, false otherwise
     */
    boolean isSuspiciousBlock(Material material);

    /**
     * Checks if a material is a sign.
     * 
     * @param material
     *            The material to check
     * @return true if the material is a sign, false otherwise
     */
    boolean isSign(Material material);

    /**
     * Checks if a material is a chiseled bookshelf.
     * 
     * @param material
     *            The material to check
     * @return true if the material is a chiseled bookshelf, false otherwise
     */
    boolean isChiseledBookshelf(Material material);

    /**
     * Checks if a material is a bookshelf book.
     * 
     * @param material
     *            The material to check
     * @return true if the material is a bookshelf book, false otherwise
     */
    boolean isBookshelfBook(Material material);

    /**
     * Gets the seeds material for a plant material.
     * 
     * @param material
     *            The plant material
     * @return The seeds material
     */
    Material getPlantSeeds(Material material);

    // --------------------------------------------------------------------------
    // Item and inventory methods
    // --------------------------------------------------------------------------

    /**
     * Adjusts an ingredient in a merchant recipe.
     * 
     * @param recipe
     *            The merchant recipe
     * @param itemStack
     *            The item stack
     * @return The adjusted item stack, or null if not applicable
     */
    ItemStack adjustIngredient(MerchantRecipe recipe, ItemStack itemStack);

    /**
     * Gets metadata from an item meta.
     * 
     * @param itemMeta
     *            The item meta
     * @param list
     *            The list to populate with metadata
     * @param metadata
     *            The metadata list to populate
     * @param slot
     *            The slot
     * @return true if metadata was extracted, false otherwise
     */
    boolean getItemMeta(ItemMeta itemMeta, List<Map<String, Object>> list, List<List<Map<String, Object>>> metadata, int slot);

    /**
     * Sets metadata on an item stack.
     * 
     * @param rowType
     *            The material type
     * @param itemstack
     *            The item stack
     * @param map
     *            The metadata map
     * @return true if metadata was set, false otherwise
     */
    boolean setItemMeta(Material rowType, ItemStack itemstack, List<Map<String, Object>> map);

    /**
     * Gets a book from a chiseled bookshelf.
     * 
     * @param blockState
     *            The block state
     * @param event
     *            The player interact event
     * @return The book item stack, or null if not applicable
     */
    ItemStack getChiseledBookshelfBook(BlockState blockState, PlayerInteractEvent event);

    /**
     * Gets arrow metadata for an item stack.
     * 
     * @param arrow
     *            The arrow entity
     * @param itemStack
     *            The item stack
     * @return The item stack with arrow metadata
     */
    ItemStack getArrowMeta(Arrow arrow, ItemStack itemStack);

    // --------------------------------------------------------------------------
    // Entity methods
    // --------------------------------------------------------------------------

    /**
     * Gets metadata from a living entity.
     * 
     * @param entity
     *            The living entity
     * @param info
     *            The list to populate with entity metadata
     * @return true if metadata was extracted, false otherwise
     */
    boolean getEntityMeta(LivingEntity entity, List<Object> info);

    /**
     * Sets metadata on an entity.
     * 
     * @param entity
     *            The entity
     * @param value
     *            The metadata value
     * @param count
     *            The count
     * @return true if metadata was set, false otherwise
     */
    boolean setEntityMeta(Entity entity, Object value, int count);

    /**
     * Gets the wolf variant and adds it to the info list.
     * Only implemented in Minecraft 1.21+.
     * 
     * @param wolf
     *            The wolf entity
     * @param info
     *            The list to add the variant information to
     */
    void getWolfVariant(org.bukkit.entity.Wolf wolf, List<Object> info);

    /**
     * Sets the wolf variant from the provided value.
     * Only implemented in Minecraft 1.21+.
     * 
     * @param wolf
     *            The wolf entity
     * @param value
     *            The variant value to set
     */
    void setWolfVariant(org.bukkit.entity.Wolf wolf, Object value);

    /**
     * Gets the frame type for an entity.
     * 
     * @param entity
     *            The entity
     * @return The frame material type
     */
    Material getFrameType(Entity entity);

    /**
     * Gets the frame type for an entity type.
     * 
     * @param type
     *            The entity type
     * @return The frame material type
     */
    Material getFrameType(EntityType type);

    /**
     * Gets the entity type for a material.
     * 
     * @param material
     *            The material
     * @return The entity type
     */
    EntityType getEntityType(Material material);

    /**
     * Gets the frame class for a material.
     * 
     * @param material
     *            The material
     * @return The frame class
     */
    Class<?> getFrameClass(Material material);

    // --------------------------------------------------------------------------
    // Sign methods
    // --------------------------------------------------------------------------

    /**
     * Checks if a sign is glowing.
     * 
     * @param sign
     *            The sign
     * @param isFront
     *            Whether to check the front side
     * @return true if the sign is glowing, false otherwise
     */
    boolean isGlowing(Sign sign, boolean isFront);

    /**
     * Checks if a sign is waxed.
     * 
     * @param sign
     *            The sign
     * @return true if the sign is waxed, false otherwise
     */
    boolean isWaxed(Sign sign);

    /**
     * Sets whether a sign is glowing.
     * 
     * @param sign
     *            The sign
     * @param isFront
     *            Whether to set the front side
     * @param isGlowing
     *            Whether the sign should be glowing
     */
    void setGlowing(Sign sign, boolean isFront, boolean isGlowing);

    /**
     * Sets the color of a sign.
     * 
     * @param sign
     *            The sign
     * @param isFront
     *            Whether to set the front side
     * @param color
     *            The color RGB value
     */
    void setColor(Sign sign, boolean isFront, int color);

    /**
     * Sets whether a sign is waxed.
     * 
     * @param sign
     *            The sign
     * @param isWaxed
     *            Whether the sign should be waxed
     */
    void setWaxed(Sign sign, boolean isWaxed);

    /**
     * Gets the color of a sign.
     * 
     * @param sign
     *            The sign
     * @param isFront
     *            Whether to get the front side color
     * @return The color RGB value
     */
    int getColor(Sign sign, boolean isFront);

    /**
     * Gets a line of text from a sign.
     * 
     * @param sign
     *            The sign
     * @param line
     *            The line number (0-based)
     * @return The text on the line
     */
    String getLine(Sign sign, int line);

    /**
     * Sets a line of text on a sign.
     * 
     * @param sign
     *            The sign
     * @param line
     *            The line number (0-based)
     * @param string
     *            The text to set
     */
    void setLine(Sign sign, int line, String string);

    /**
     * Checks if a sign change event is for the front side of the sign.
     * 
     * @param event
     *            The sign change event
     * @return true if the event is for the front side, false otherwise
     */
    boolean isSignFront(SignChangeEvent event);

    // --------------------------------------------------------------------------
    // Registry methods
    // --------------------------------------------------------------------------

    /**
     * Gets the registry key for a value.
     * 
     * @param value
     *            The value
     * @return The registry key
     */
    Object getRegistryKey(Object value);

    /**
     * Gets a registry value by key and class.
     * 
     * @param key
     *            The key
     * @param tClass
     *            The class
     * @return The registry value
     */
    Object getRegistryValue(String key, Object tClass);

    /**
     * Parses a legacy material name.
     * 
     * @param name
     *            The legacy name
     * @return The parsed name
     */
    String parseLegacyName(String name);
}
