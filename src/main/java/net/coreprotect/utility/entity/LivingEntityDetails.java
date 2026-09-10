package net.coreprotect.utility.entity;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;

public final class LivingEntityDetails {

    private LivingEntityDetails() {
        throw new IllegalStateException("Utility class");
    }

    public static List<Object> serialize(LivingEntity entity) {
        List<Object> details = new ArrayList<>();
        details.add(entity.getRemoveWhenFarAway());
        details.add(entity.getCanPickupItems());
        if (entity instanceof Mob) {
            details.add(((Mob) entity).isAware());
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
    }

}
