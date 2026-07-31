package net.coreprotect.paper;

import java.util.List;

import org.bukkit.entity.Chicken;
import org.bukkit.entity.Cow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Pig;

import net.coreprotect.bukkit.BukkitAdapter;

public class Paper_v26_1 extends Paper_26_0 {

    @Override
    public boolean getEntityMeta(LivingEntity entity, List<Object> info) {
        if (entity instanceof Chicken) {
            Chicken chicken = (Chicken) entity;
            info.add(BukkitAdapter.ADAPTER.getRegistryKey(chicken.getSoundVariant()));
            return true;
        }
        else if (entity instanceof Cow) {
            Cow cow = (Cow) entity;
            info.add(BukkitAdapter.ADAPTER.getRegistryKey(cow.getSoundVariant()));
            return true;
        }
        else if (entity instanceof Pig) {
            Pig pig = (Pig) entity;
            info.add(BukkitAdapter.ADAPTER.getRegistryKey(pig.getSoundVariant()));
            return true;
        }

        return super.getEntityMeta(entity, info);
    }

    @Override
    public boolean setEntityMeta(Entity entity, Object value, int count) {
        if (entity instanceof Chicken && count == 1) {
            Chicken.SoundVariant variant = getChickenSoundVariant(value);
            if (variant == null) {
                return false;
            }
            Chicken chicken = (Chicken) entity;
            chicken.setSoundVariant(variant);
            return true;
        }
        else if (entity instanceof Cow && count == 1) {
            Cow.SoundVariant variant = getCowSoundVariant(value);
            if (variant == null) {
                return false;
            }
            Cow cow = (Cow) entity;
            cow.setSoundVariant(variant);
            return true;
        }
        else if (entity instanceof Pig && count == 2) {
            Pig.SoundVariant variant = getPigSoundVariant(value);
            if (variant == null) {
                return false;
            }
            Pig pig = (Pig) entity;
            pig.setSoundVariant(variant);
            return true;
        }

        return super.setEntityMeta(entity, value, count);
    }

    private Chicken.SoundVariant getChickenSoundVariant(Object value) {
        Object variant = value instanceof String ? BukkitAdapter.ADAPTER.getRegistryValue((String) value, Chicken.SoundVariant.class) : value;
        return variant instanceof Chicken.SoundVariant ? (Chicken.SoundVariant) variant : null;
    }

    private Cow.SoundVariant getCowSoundVariant(Object value) {
        Object variant = value instanceof String ? BukkitAdapter.ADAPTER.getRegistryValue((String) value, Cow.SoundVariant.class) : value;
        return variant instanceof Cow.SoundVariant ? (Cow.SoundVariant) variant : null;
    }

    private Pig.SoundVariant getPigSoundVariant(Object value) {
        Object variant = value instanceof String ? BukkitAdapter.ADAPTER.getRegistryValue((String) value, Pig.SoundVariant.class) : value;
        return variant instanceof Pig.SoundVariant ? (Pig.SoundVariant) variant : null;
    }

}
