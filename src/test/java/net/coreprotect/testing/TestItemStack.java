package net.coreprotect.testing;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/** Minimal value stack for tests that do not have a Bukkit item factory. */
public final class TestItemStack extends ItemStack {
    private final Material material;
    private int amount;

    public TestItemStack(Material material, int amount) {
        this.material = material;
        this.amount = amount;
    }

    @Override
    public Material getType() {
        return material;
    }

    @Override
    public int getAmount() {
        return amount;
    }

    @Override
    public void setAmount(int amount) {
        this.amount = amount;
    }

    @Override
    public int getMaxStackSize() {
        return material == Material.SADDLE ? 1 : 64;
    }

    @Override
    public boolean isSimilar(ItemStack other) {
        return other != null && material == other.getType();
    }

    @Override
    public TestItemStack clone() {
        return new TestItemStack(material, amount);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ItemStack && isSimilar((ItemStack) other) && amount == ((ItemStack) other).getAmount();
    }

    @Override
    public int hashCode() {
        return material.hashCode() * 31 + amount;
    }
}
