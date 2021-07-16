package net.coreprotect.bukkit;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.bukkit.Material;
import org.bukkit.block.Sign;
import org.bukkit.entity.Bee;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SuspiciousStewMeta;
import org.bukkit.potion.PotionEffect;

public class Bukkit_v1_15 extends BukkitAdapter implements BukkitInterface {

    @Override
    public boolean getEntityMeta(LivingEntity entity, List<Object> info) {
        if (entity instanceof Bee) {
            Bee bee = (Bee) entity;
            info.add(bee.getAnger());
            info.add(bee.hasNectar());
            info.add(bee.hasStung());
        }
        else {
            return false;
        }

        return true;
    }

    @Override
    public boolean setEntityMeta(Entity entity, Object value, int count) {
        if (entity instanceof Bee) {
            Bee bee = (Bee) entity;
            if (count == 0) {
                int set = (int) value;
                bee.setAnger(set);
            }
            else if (count == 1) {
                boolean set = (Boolean) value;
                bee.setHasNectar(set);
            }
            else if (count == 2) {
                boolean set = (Boolean) value;
                bee.setHasStung(set);
            }
        }
        else {
            return false;
        }

        return true;
    }

    @Override
    public boolean getItemMeta(ItemMeta itemMeta, List<Map<String, Object>> list, List<List<Map<String, Object>>> metadata, int slot) {
        if (itemMeta instanceof SuspiciousStewMeta) {
            SuspiciousStewMeta meta = (SuspiciousStewMeta) itemMeta;
            SuspiciousStewMeta subMeta = meta.clone();
            meta.clearCustomEffects();
            list.add(meta.serialize());
            metadata.add(list);

            if (subMeta.hasCustomEffects()) {
                for (PotionEffect effect : subMeta.getCustomEffects()) {
                    list = new ArrayList<>();
                    list.add(effect.serialize());
                    metadata.add(list);
                }
            }
        }
        else {
            return false;
        }

        return true;
    }

    @Override
    public boolean setItemMeta(Material rowType, ItemStack itemstack, List<Map<String, Object>> map) {
        if ((rowType == Material.SUSPICIOUS_STEW)) {
            for (Map<String, Object> suspiciousStewData : map) {
                SuspiciousStewMeta meta = (SuspiciousStewMeta) itemstack.getItemMeta();
                PotionEffect effect = new PotionEffect(suspiciousStewData);
                meta.addCustomEffect(effect, true);
                itemstack.setItemMeta(meta);
            }
        }
        else {
            return false;
        }

        return true;
    }

    @Override
    public void sendSignChange(Player player, Sign sign) {
        player.sendSignChange(sign.getLocation(), sign.getLines(), sign.getColor());
    }

}
