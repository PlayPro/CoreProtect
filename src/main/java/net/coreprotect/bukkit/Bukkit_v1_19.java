package net.coreprotect.bukkit;

import net.coreprotect.model.BlockGroup;
import org.bukkit.Material;
import org.bukkit.entity.*;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

public class Bukkit_v1_19 extends Bukkit_v1_18 implements BukkitInterface {

    public Bukkit_v1_19() {
        BlockGroup.SCULK = new HashSet<>(Arrays.asList(Material.SCULK, Material.SCULK_VEIN, Material.SCULK_SENSOR, Material.SCULK_SHRIEKER));
    }

    @Override
    public boolean getEntityMeta(LivingEntity entity, List<Object> info) {
        if (entity instanceof Frog) {
            Frog frog = (Frog) entity;
            info.add(frog.getVariant());
        }
        else if (entity instanceof Goat) {
            Goat goat = (Goat) entity;
            info.add(goat.isScreaming());
            info.add(goat.hasLeftHorn());
            info.add(goat.hasRightHorn());
        }
        else if (super.getEntityMeta(entity, info)) {
            return true;
        }
        else {
            return false;
        }

        return true;
    }

    @Override
    public boolean setEntityMeta(Entity entity, Object value, int count) {
        if (entity instanceof Frog) {
            Frog frog = (Frog) entity;
            if (count == 0) {
                Frog.Variant set = (Frog.Variant) value;
                frog.setVariant(set);
            }
        }
        else if (entity instanceof Goat) {
            Goat goat = (Goat) entity;
            boolean set = (Boolean) value;
            if (count == 0) {
                goat.setScreaming(set);
            }
            else if (count == 1) {
                goat.setLeftHorn(set);
            }
            else if (count == 2) {
                goat.setRightHorn(set);
            }
        }
        else if (super.setEntityMeta(entity, value, count)) {
            return true;
        }
        else {
            return false;
        }

        return true;
    }
}
