package net.coreprotect.paper;

import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.block.Sign;
import org.bukkit.block.Skull;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import net.coreprotect.bukkit.BukkitAdapter;
import net.coreprotect.config.ConfigHandler;

public class PaperAdapter implements PaperInterface {

    public static PaperInterface ADAPTER;
    public static final int PAPER_UNAVAILABLE = 0;
    public static final int PAPER_V1_13 = BukkitAdapter.BUKKIT_V1_13;
    public static final int PAPER_V1_14 = BukkitAdapter.BUKKIT_V1_14;
    public static final int PAPER_V1_15 = BukkitAdapter.BUKKIT_V1_15;
    public static final int PAPER_V1_16 = BukkitAdapter.BUKKIT_V1_16;
    public static final int PAPER_V1_17 = BukkitAdapter.BUKKIT_V1_17;
    public static final int PAPER_V1_18 = BukkitAdapter.BUKKIT_V1_18;
    public static final int PAPER_V1_19 = BukkitAdapter.BUKKIT_V1_19;
    public static final int PAPER_V1_20 = BukkitAdapter.BUKKIT_V1_20;
    public static final int PAPER_V1_21 = BukkitAdapter.BUKKIT_V1_21;

    public static void loadAdapter() {
        int paperVersion = ConfigHandler.SERVER_VERSION;
        if (!ConfigHandler.isPaper) {
            paperVersion = PAPER_UNAVAILABLE;
        }

        switch (paperVersion) {
            case PAPER_UNAVAILABLE:
                PaperAdapter.ADAPTER = new PaperAdapter();
                break;
            case PAPER_V1_13:
            case PAPER_V1_14:
            case PAPER_V1_15:
                PaperAdapter.ADAPTER = new PaperHandler();
                break;
            case PAPER_V1_16:
                PaperAdapter.ADAPTER = new Paper_v1_16();
                break;
            case PAPER_V1_17:
            case PAPER_V1_18:
            case PAPER_V1_19:
                PaperAdapter.ADAPTER = new Paper_v1_17();
                break;
            case PAPER_V1_20:
            case PAPER_V1_21:
            default:
                PaperAdapter.ADAPTER = new Paper_v1_20();
                break;
        }
    }

    @Override
    public InventoryHolder getHolder(Inventory holder, boolean useSnapshot) {
        return holder.getHolder();
    }

    @Override
    public boolean isStopping(Server server) {
        return false;
    }

    @Override
    public String getLine(Sign sign, int line) {
        return BukkitAdapter.ADAPTER.getLine(sign, line);
    }

    @Override
    public void teleportAsync(Entity entity, Location location) {
        entity.teleport(location);
    }

    @Override
    public String getSkullOwner(Skull skull) {
        return skull.getOwningPlayer().getUniqueId().toString();
    }

    @Override
    public void setSkullOwner(Skull skull, String owner) {
        if (owner != null && owner.length() >= 32 && owner.contains("-")) {
            skull.setOwningPlayer(Bukkit.getOfflinePlayer(UUID.fromString(owner)));
        }
    }

    @Override
    public String getSkullSkin(Skull skull) {
        return null;
    }

    @Override
    public void setSkullSkin(Skull skull, String skin) {
        return;
    }

}
