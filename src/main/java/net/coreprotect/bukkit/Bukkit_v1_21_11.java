package net.coreprotect.bukkit;

import java.util.List;

import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.ZombieNautilus;

/**
 * Bukkit adapter implementation for Minecraft 1.21.11.
 */
public class Bukkit_v1_21_11 extends Bukkit_v1_21_5 {

    @Override
    public boolean getEntityMeta(LivingEntity entity, List<Object> info) {
        if (entity instanceof ZombieNautilus) {
            ZombieNautilus nautilus = (ZombieNautilus) entity;
            info.add(getRegistryKey(nautilus.getVariant()));
            return true;
        }

        return super.getEntityMeta(entity, info);
    }

    @Override
    public boolean setEntityMeta(Entity entity, Object value, int count) {
        if (entity instanceof ZombieNautilus && count == 0) {
            ZombieNautilus.Variant variant = resolveRegistryValue(this, value, ZombieNautilus.Variant.class);
            if (variant == null) {
                return false;
            }
            ZombieNautilus nautilus = (ZombieNautilus) entity;
            nautilus.setVariant(variant);
            return true;
        }

        return super.setEntityMeta(entity, value, count);
    }

}
