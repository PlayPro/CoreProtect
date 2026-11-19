package net.coreprotect.paper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Firework;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.MerchantRecipe;

import com.destroystokyo.paper.entity.villager.Reputation;
import com.destroystokyo.paper.entity.villager.ReputationType;

import net.coreprotect.bukkit.BukkitAdapter;
import net.coreprotect.model.entity.VillagerReputationData;

public class PaperHandler extends PaperAdapter {
    private volatile boolean supportsSnapshotHolderLookup = true;
    private volatile boolean supportsSnapshotStateLookup = true;
    private volatile boolean supportsAverageTickTime = true;
    private volatile boolean supportsVillagerReputations = true;
    private volatile boolean supportsVillagerRestocks = true;
    private volatile boolean supportsTradeDiscounts = true;

    @Override
    public LivingEntity getFireworkRider(Firework firework) {
        LivingEntity rider = super.getFireworkRider(firework);
        return rider == null ? firework.getBoostedEntity() : rider;
    }

    @Override
    public boolean isStopping(Server server) {
        return server.isStopping();
    }

    @Override
    public double getAverageTickTime(Server server) {
        if (supportsAverageTickTime) {
            try {
                return server.getAverageTickTime();
            }
            catch (LinkageError | UnsupportedOperationException ignored) {
                supportsAverageTickTime = false;
            }
        }

        return -1.0D;
    }

    @Override
    public void teleportAsync(Entity entity, Location location) {
        entity.teleportAsync(location);
    }

    @Override
    public void prefetchChunk(World world, int chunkX, int chunkZ) {
        if (!world.isChunkLoaded(chunkX, chunkZ)) {
            world.getChunkAtAsync(chunkX, chunkZ);
        }
    }

    @Override
    public InventoryHolder getHolder(Inventory holder, boolean useSnapshot) {
        if (supportsSnapshotHolderLookup) {
            try {
                return holder.getHolder(useSnapshot);
            }
            catch (LinkageError ignored) {
                supportsSnapshotHolderLookup = false;
            }
        }

        return holder.getHolder();
    }

    @Override
    public BlockState getBlockState(Block block, boolean useSnapshot) {
        if (supportsSnapshotStateLookup) {
            try {
                return block.getState(useSnapshot);
            }
            catch (LinkageError ignored) {
                supportsSnapshotStateLookup = false;
            }
        }

        return block.getState();
    }

    @Override
    public List<Object> getVillagerReputations(Villager villager) {
        List<Object> reputations = new ArrayList<>();
        if (!supportsVillagerReputations) {
            return reputations;
        }

        try {
            for (Map.Entry<UUID, Reputation> entry : villager.getReputations().entrySet()) {
                if (entry.getKey() == null || entry.getValue() == null) {
                    continue;
                }
                List<Object> values = new ArrayList<>();
                for (ReputationType type : ReputationType.values()) {
                    if (entry.getValue().hasReputationSet(type)) {
                        values.add(List.of(type.name(), entry.getValue().getReputation(type)));
                    }
                }
                if (!values.isEmpty()) {
                    reputations.add(List.of(entry.getKey().toString(), values));
                }
            }
        }
        catch (LinkageError e) {
            supportsVillagerReputations = false;
        }

        return reputations;
    }

    @Override
    public boolean setVillagerReputations(Villager villager, List<?> reputations) {
        if (!supportsVillagerReputations) {
            return false;
        }

        try {
            Map<UUID, Reputation> result = new HashMap<>();
            for (Object reputationObject : reputations) {
                VillagerReputationData data = VillagerReputationData.parse(reputationObject);
                if (data == null) {
                    continue;
                }

                Reputation reputation = new Reputation();
                for (VillagerReputationData.Value value : data.values()) {
                    ReputationType type = (ReputationType) BukkitAdapter.getLegacyEnumValue(ReputationType.class, value.type());
                    if (type != null) {
                        reputation.setReputation(type, value.amount());
                    }
                }
                result.put(data.uuid(), reputation);
            }

            villager.setReputations(result);
            return true;
        }
        catch (LinkageError e) {
            supportsVillagerReputations = false;
            return false;
        }
    }

    @Override
    public Object getVillagerRestocksToday(Villager villager) {
        if (supportsVillagerRestocks) {
            try {
                return villager.getRestocksToday();
            }
            catch (LinkageError e) {
                supportsVillagerRestocks = false;
            }
        }
        return null;
    }

    @Override
    public void setVillagerRestocksToday(Villager villager, Object value) {
        if (supportsVillagerRestocks && value instanceof Number) {
            try {
                villager.setRestocksToday(((Number) value).intValue());
            }
            catch (LinkageError e) {
                supportsVillagerRestocks = false;
            }
        }
    }

    @Override
    public void addMerchantRecipeMeta(MerchantRecipe recipe, List<Object> recipeData) {
        if (supportsTradeDiscounts) {
            try {
                recipeData.add(recipe.shouldIgnoreDiscounts());
            }
            catch (LinkageError e) {
                supportsTradeDiscounts = false;
            }
        }
    }

    @Override
    public void setMerchantRecipeMeta(MerchantRecipe recipe, List<?> recipeData) {
        if (!supportsTradeDiscounts || recipeData.size() <= 7) {
            return;
        }

        Object value = recipeData.size() > 8 && recipeData.get(8) instanceof Boolean ? recipeData.get(8) : recipeData.get(7);
        if (value instanceof Boolean) {
            try {
                recipe.setIgnoreDiscounts((Boolean) value);
            }
            catch (LinkageError e) {
                supportsTradeDiscounts = false;
            }
        }
    }
}
