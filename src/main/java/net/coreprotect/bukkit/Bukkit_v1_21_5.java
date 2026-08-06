package net.coreprotect.bukkit;

import java.util.List;

import org.bukkit.entity.Chicken;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Pig;

/**
 * Bukkit adapter implementation for Minecraft 1.21.5.
 */
public class Bukkit_v1_21_5 extends Bukkit_v1_21 {

    @Override
    public boolean getEntityMeta(LivingEntity entity, List<Object> info) {
        if (entity instanceof Chicken) {
            Chicken chicken = (Chicken) entity;
            info.add(getRegistryKey(chicken.getVariant()));
            return true;
        }
        else if (entity.getType() == EntityType.COW) {
            return BukkitAdapter.getRegistryVariant(this, entity, info, "getVariant");
        }
        else if (entity instanceof Pig) {
            Pig pig = (Pig) entity;
            info.add(getRegistryKey(pig.getVariant()));
            return true;
        }

        return super.getEntityMeta(entity, info);
    }

    @Override
    public boolean setEntityMeta(Entity entity, Object value, int count) {
        if (entity instanceof Chicken && count == 0) {
            Chicken.Variant variant = getChickenVariant(value);
            if (variant == null) {
                return false;
            }
            Chicken chicken = (Chicken) entity;
            chicken.setVariant(variant);
            return true;
        }
        else if (entity.getType() == EntityType.COW && count == 0) {
            return BukkitAdapter.setRegistryVariant(this, entity, value, "getVariant", "setVariant");
        }
        else if (entity instanceof Pig && count == 1) {
            Pig.Variant variant = getPigVariant(value);
            if (variant == null) {
                return false;
            }
            Pig pig = (Pig) entity;
            pig.setVariant(variant);
            return true;
        }

        return super.setEntityMeta(entity, value, count);
    }

    private Chicken.Variant getChickenVariant(Object value) {
        Object variant = value instanceof String ? getRegistryValue((String) value, Chicken.Variant.class) : value;
        return variant instanceof Chicken.Variant ? (Chicken.Variant) variant : null;
    }

    private Pig.Variant getPigVariant(Object value) {
        Object variant = value instanceof String ? getRegistryValue((String) value, Pig.Variant.class) : value;
        return variant instanceof Pig.Variant ? (Pig.Variant) variant : null;
    }

}
