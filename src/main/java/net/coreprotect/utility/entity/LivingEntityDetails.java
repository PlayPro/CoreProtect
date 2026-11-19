package net.coreprotect.utility.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;

import net.coreprotect.bukkit.BukkitAdapter;
import net.coreprotect.utility.BlockUtils;

public final class LivingEntityDetails {

    private LivingEntityDetails() {
        throw new IllegalStateException("Utility class");
    }

    public static void trimTrailingNulls(List<?> values) {
        while (!values.isEmpty() && values.get(values.size() - 1) == null) {
            values.remove(values.size() - 1);
        }
    }

    public static List<Object> serialize(LivingEntity entity) {
        List<Object> details = new ArrayList<>();
        details.add(entity.getRemoveWhenFarAway());
        details.add(entity.getCanPickupItems());
        if (entity instanceof Mob) {
            details.add(((Mob) entity).isAware());
        }
        Object body = serializeBody(entity.getEquipment());
        if (body != null) {
            if (!(entity instanceof Mob)) {
                details.add(null);
            }
            details.add(body);
        }
        return details;
    }

    public static void restore(LivingEntity entity, List<?> details) {
        if (details.size() > 0) {
            entity.setRemoveWhenFarAway((Boolean) details.get(0));
        }
        if (details.size() > 1) {
            entity.setCanPickupItems((Boolean) details.get(1));
        }
        if (details.size() > 2 && entity instanceof Mob) {
            ((Mob) entity).setAware((Boolean) details.get(2));
        }
        if (details.size() > 3) {
            restoreBody(entity.getEquipment(), details.get(3));
        }
    }

    public static Object serializeBody(EntityEquipment equipment) {
        if (equipment == null || !BukkitAdapter.ADAPTER.hasBodyItemSlot() || equipment.getHolder() instanceof Player) {
            return null;
        }
        ItemStack item = BukkitAdapter.ADAPTER.getBodyItem(equipment);
        return item == null || BlockUtils.isAir(item.getType()) ? Boolean.FALSE : item.serialize();
    }

    public static void restoreBody(EntityEquipment equipment, Object body) {
        if (equipment == null || !BukkitAdapter.ADAPTER.hasBodyItemSlot() || equipment.getHolder() instanceof Player) {
            return;
        }
        ItemStack item;
        if (body instanceof Map<?, ?>) {
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) body;
            item = ItemStack.deserialize(data);
        }
        else if (Boolean.FALSE.equals(body)) {
            item = null;
        }
        else {
            return;
        }
        BukkitAdapter.ADAPTER.setBodyItem(equipment, item);
    }

}
