package net.coreprotect.api.result;

import java.util.Locale;
import java.util.function.Supplier;

import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.EntityType;

import net.coreprotect.model.action.LookupActions;
import net.coreprotect.utility.BlockUtils;
import net.coreprotect.utility.EntityUtils;
import net.coreprotect.utility.MaterialUtils;
import net.coreprotect.utility.StringUtils;

final class BlockResultValues {
    private BlockResultValues() {
    }

    static Material material(int type, int legacyData) {
        Material material = MaterialUtils.getType(type);
        return material == null ? null : MaterialUtils.getType(StringUtils.nameFilter(material.name().toLowerCase(Locale.ROOT), legacyData));
    }

    static BlockData blockData(int type, String data, Supplier<Material> material) {
        BlockData resolved = BlockUtils.parseBlockDataFromString(data);
        if (resolved != null) {
            return resolved;
        }
        Material fallback = material.get();
        return fallback != null ? BlockUtils.createBlockData(fallback) : BlockUtils.createBlockData(type);
    }

    static EntityType entityType(int type, int action) {
        return action == LookupActions.ENTITY_KILL && type == 0 ? EntityType.PLAYER : EntityUtils.getEntityType(type);
    }

    static boolean isRolledBack(int state) {
        return state == 1 || state == 3;
    }
}
