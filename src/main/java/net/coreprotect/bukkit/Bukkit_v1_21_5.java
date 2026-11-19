package net.coreprotect.bukkit;

import java.util.List;
import java.util.Set;

import org.bukkit.Material;
import org.bukkit.entity.Chicken;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Pig;

import net.coreprotect.model.BlockGroup;

/**
 * Bukkit adapter implementation for Minecraft 1.21.5.
 */
public class Bukkit_v1_21_5 extends Bukkit_v1_21 {

    public Bukkit_v1_21_5() {
        Set<Material> groundPlants = Set.of(Material.BUSH, Material.FIREFLY_BUSH, Material.SHORT_DRY_GRASS, Material.TALL_DRY_GRASS, Material.LEAF_LITTER);
        BlockGroup.TRACK_TOP.addAll(groundPlants);
        BlockGroup.NON_ATTACHABLE.addAll(groundPlants);
    }

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
            Chicken.Variant variant = resolveRegistryValue(this, value, Chicken.Variant.class);
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
            Pig.Variant variant = resolveRegistryValue(this, value, Pig.Variant.class);
            if (variant == null) {
                return false;
            }
            Pig pig = (Pig) entity;
            pig.setVariant(variant);
            return true;
        }

        return super.setEntityMeta(entity, value, count);
    }

}
