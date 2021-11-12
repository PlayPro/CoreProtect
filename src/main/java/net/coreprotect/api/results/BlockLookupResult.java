package net.coreprotect.api.results;

import net.coreprotect.api.CoreProtectAPI;
import net.coreprotect.config.Config;
import org.bukkit.Bukkit;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

public class BlockLookupResult extends ParseResult {

    public BlockLookupResult(String[] data) {
        super(data);
    }

    public BlockData getBlockData() {
        String blockData = parse[12];
        if (blockData.length() == 0) return getType().createBlockData();
        return Bukkit.getServer().createBlockData(blockData);
    }

    public boolean hasRemoved(String entity) {
        return entity.equalsIgnoreCase(getEntity()) && getActionId() == 0;
    }

    public boolean hasRemoved(LivingEntity entity) {
        if (entity instanceof Player) {
            Player player = (Player) entity;
            return Config.getGlobal().API_ENABLED && player.getName().equalsIgnoreCase(getEntity()) && getActionId() == 0;
        }
        return hasPlaced("#" + entity.getType().name().toLowerCase());
    }

    public boolean hasPlaced(String entity) {
        return entity.equalsIgnoreCase(getEntity()) && getActionId() == 1;
    }

    public boolean hasPlaced(LivingEntity entity) {
        if (entity instanceof Player) {
            Player player = (Player) entity;
            return Config.getGlobal().API_ENABLED && player.getName().equalsIgnoreCase(getEntity()) && getActionId() == 1;
        }
        return hasPlaced("#" + entity.getType().name().toLowerCase());
    }

}
