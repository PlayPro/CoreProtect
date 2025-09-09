package net.coreprotect.bukkit;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;

/**
 * Bukkit adapter implementation for Minecraft 1.18.
 * Provides version-specific implementations for the BukkitInterface
 * to handle features introduced in the 1.18 update.
 */
public class Bukkit_v1_18 extends Bukkit_v1_17 {

    /**
     * Flag to track whether the MerchantRecipe.adjust() method is available.
     * This is initialized on first use and prevents repeated reflection checks.
     * - null: Not yet checked
     * - true: Method exists and should be used
     * - false: Method doesn't exist or failed, fallback to parent implementation
     */
    private Boolean hasAdjustMethod = null;

    /**
     * Adjusts an ingredient in a merchant recipe for version 1.18+.
     * This handles changes to the MerchantRecipe API introduced in Bukkit 1.18.1.
     *
     * @param recipe
     *            The merchant recipe
     * @param itemStack
     *            The item stack to adjust
     * @return The adjusted item stack, or null if adjustment not supported or fails
     */
    @Override
    public ItemStack adjustIngredient(MerchantRecipe recipe, ItemStack itemStack) {
        if (itemStack == null) {
            return null;
        }

        try {
            // First-time detection of adjust method availability
            if (hasAdjustMethod == null) {
                hasAdjustMethod = true;
                // Test if the adjust method exists using reflection
                MerchantRecipe.class.getMethod("adjust", ItemStack.class); // Bukkit 1.18.1+
            }
            // Skip if we've already determined the method isn't available
            else if (Boolean.FALSE.equals(hasAdjustMethod)) {
                return super.adjustIngredient(recipe, itemStack);
            }

            // Create a clone to avoid modifying the original itemStack
            ItemStack adjustedStack = itemStack.clone();
            recipe.adjust(adjustedStack);
            return adjustedStack;
        }
        catch (Exception e) {
            // Method doesn't exist or failed, mark it for future calls
            hasAdjustMethod = false;
            return super.adjustIngredient(recipe, itemStack);
        }
    }

}
