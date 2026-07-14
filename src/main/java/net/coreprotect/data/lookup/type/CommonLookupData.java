package net.coreprotect.data.lookup.type;

import net.coreprotect.database.lookup.PlayerLookup;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

/**
 * Lookup data common to block/item/container lookups
 */
@ApiStatus.Experimental
public record CommonLookupData(
        long rowId,
        long time,
        int userId,
        int x,
        int y,
        int z,
        int type,
        int data,
        int action,
        int rolledBack,
        int worldId,
        int amount,
        @Nullable String metadata,
        @Nullable String blockData,
        @Nullable Integer table,
        int version,
        int entitySpawnRowId
) {
    public String playerName() {
        return PlayerLookup.playerName(this.userId);
    }
}
