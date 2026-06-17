package net.coreprotect.paper;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.MerchantRecipe;

import net.coreprotect.model.entity.VillagerReputationData;

public class PaperHandler extends PaperAdapter {
    private volatile boolean supportsSnapshotHolderLookup = true;

    @Override
    public boolean isStopping(Server server) {
        return server.isStopping();
    }

    @Override
    public void teleportAsync(Entity entity, Location location) {
        entity.teleportAsync(location);
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
    public List<Object> getVillagerReputations(Villager villager) {
        List<Object> reputations = new ArrayList<>();
        try {
            Method getReputations = villager.getClass().getMethod("getReputations");
            Object reputationMap = getReputations.invoke(villager);
            if (!(reputationMap instanceof Map<?, ?>)) {
                return reputations;
            }

            Class<?> reputationTypeClass = Class.forName("com.destroystokyo.paper.entity.villager.ReputationType");
            Method hasReputationSet = Class.forName("com.destroystokyo.paper.entity.villager.Reputation").getMethod("hasReputationSet", reputationTypeClass);
            Method getReputation = Class.forName("com.destroystokyo.paper.entity.villager.Reputation").getMethod("getReputation", reputationTypeClass);
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) reputationMap).entrySet()) {
                if (!(entry.getKey() instanceof UUID) || entry.getValue() == null) {
                    continue;
                }

                List<Object> reputationData = new ArrayList<>();
                List<Object> values = new ArrayList<>();
                for (Object type : reputationTypeClass.getEnumConstants()) {
                    if ((Boolean) hasReputationSet.invoke(entry.getValue(), type)) {
                        List<Object> value = new ArrayList<>();
                        value.add(((Enum<?>) type).name());
                        value.add(getReputation.invoke(entry.getValue(), type));
                        values.add(value);
                    }
                }

                if (!values.isEmpty()) {
                    reputationData.add(entry.getKey().toString());
                    reputationData.add(values);
                    reputations.add(reputationData);
                }
            }
        }
        catch (Exception e) {
        }

        return reputations;
    }

    @Override
    public boolean setVillagerReputations(Villager villager, List<?> reputations) {
        try {
            Class<?> reputationClass = Class.forName("com.destroystokyo.paper.entity.villager.Reputation");
            Class<?> reputationTypeClass = Class.forName("com.destroystokyo.paper.entity.villager.ReputationType");
            Method setReputation = reputationClass.getMethod("setReputation", reputationTypeClass, int.class);
            Map<UUID, Object> result = new HashMap<>();

            for (Object reputationObject : reputations) {
                VillagerReputationData data = VillagerReputationData.parse(reputationObject);
                if (data == null) {
                    continue;
                }

                Object reputation = reputationClass.getConstructor().newInstance();
                for (VillagerReputationData.Value value : data.values()) {
                    Object type = parseEnumValue(reputationTypeClass, value.type());
                    if (type != null) {
                        setReputation.invoke(reputation, type, value.amount());
                    }
                }

                result.put(data.uuid(), reputation);
            }

            villager.getClass().getMethod("setReputations", Map.class).invoke(villager, result);
            return true;
        }
        catch (Exception e) {
            return false;
        }
    }

    @Override
    public Object getVillagerRestocksToday(Villager villager) {
        return invokeNoArgumentMethod(villager, "getRestocksToday");
    }

    @Override
    public void setVillagerRestocksToday(Villager villager, Object value) {
        invokeIntSetter(villager, "setRestocksToday", value);
    }

    @Override
    public void addMerchantRecipeMeta(MerchantRecipe recipe, List<Object> recipeData) {
        Object ignoreDiscounts = invokeNoArgumentMethod(recipe, "shouldIgnoreDiscounts");
        if (ignoreDiscounts instanceof Boolean) {
            recipeData.add(ignoreDiscounts);
        }
    }

    @Override
    public void setMerchantRecipeMeta(MerchantRecipe recipe, List<?> recipeData) {
        if (recipeData.size() > 8 && recipeData.get(8) instanceof Boolean) {
            invokeBooleanSetter(recipe, "setIgnoreDiscounts", recipeData.get(8));
        }
        else if (recipeData.size() > 7 && recipeData.get(7) instanceof Boolean) {
            invokeBooleanSetter(recipe, "setIgnoreDiscounts", recipeData.get(7));
        }
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static Object parseEnumValue(Class<?> enumClass, String value) {
        try {
            return Enum.valueOf((Class<? extends Enum>) enumClass.asSubclass(Enum.class), value);
        }
        catch (Exception e) {
            return null;
        }
    }

    private static Object invokeNoArgumentMethod(Object target, String methodName) {
        try {
            return target.getClass().getMethod(methodName).invoke(target);
        }
        catch (Exception e) {
            return null;
        }
    }

    private static void invokeIntSetter(Object target, String methodName, Object value) {
        if (value instanceof Number) {
            try {
                target.getClass().getMethod(methodName, int.class).invoke(target, ((Number) value).intValue());
            }
            catch (Exception e) {
            }
        }
    }

    private static void invokeBooleanSetter(Object target, String methodName, Object value) {
        if (value instanceof Boolean) {
            try {
                target.getClass().getMethod(methodName, boolean.class).invoke(target, value);
            }
            catch (Exception e) {
            }
        }
    }

}
