package net.coreprotect.api.results;

import net.coreprotect.utility.Util;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public class ContainerLookupResult extends ParseResult {

    public ContainerLookupResult(String[] data) {
        super(data);
    }

    @Override
    public String getActionString() {
        String res;
        switch(getActionId()) {
            case 0:
                res = "removed";
                break;
            case 1:
                res = "added";
                break;
            default:
                res = "unknown";
        }
        return res;
    }

    public ItemStack getItem() {
        return new ItemStack(getType(), getAmount());
    }

    public int getAmount() {
        return Integer.parseInt(parse[12]);
    }

    public Inventory getInventory() {
        return Util.getContainerInventory(getBlock().getState(), true);
    }

}