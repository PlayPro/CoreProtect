package net.coreprotect.model.action;

import java.util.Collection;

public enum EntityActionFilter {
    DEFAULT(false, false, false),
    NONE(true, false, false),
    ALIASED(true, true, false),
    SPAWNED(true, false, true),
    ALL(true, true, true);

    private final boolean explicit;
    private final boolean aliased;
    private final boolean spawned;

    EntityActionFilter(boolean explicit, boolean aliased, boolean spawned) {
        this.explicit = explicit;
        this.aliased = aliased;
        this.spawned = spawned;
    }

    public boolean includesPlacedSpawn(Collection<Integer> actions, boolean includeByDefault) {
        if (!explicit) {
            return includesExactAction(actions, LookupActions.ENTITY_SPAWN, includeByDefault);
        }
        return aliased && actions.contains(LookupActions.BLOCK_PLACE);
    }

    public boolean includesSpawnedEntity(Collection<Integer> actions, boolean includeByDefault) {
        if (!explicit) {
            return includesExactAction(actions, LookupActions.ENTITY_SPAWN, includeByDefault);
        }
        return spawned && actions.contains(LookupActions.ENTITY_SPAWN);
    }

    public boolean includesVehicleKill(Collection<Integer> actions, boolean includeByDefault) {
        if (!explicit) {
            return includesExactAction(actions, LookupActions.ENTITY_KILL, includeByDefault);
        }
        return aliased && actions.contains(LookupActions.BLOCK_BREAK);
    }

    public boolean includesKilledEntity(Collection<Integer> actions, boolean includeByDefault) {
        if (!explicit) {
            return includesExactAction(actions, LookupActions.ENTITY_KILL, includeByDefault);
        }
        return actions.contains(LookupActions.ENTITY_KILL);
    }

    public boolean includesAnySpawn(Collection<Integer> actions, boolean includeByDefault) {
        return includesPlacedSpawn(actions, includeByDefault) || includesSpawnedEntity(actions, includeByDefault);
    }

    public boolean includesAnyKill(Collection<Integer> actions, boolean includeByDefault) {
        return includesVehicleKill(actions, includeByDefault) || includesKilledEntity(actions, includeByDefault);
    }

    public boolean includesAnyEntity(Collection<Integer> actions, boolean includeByDefault) {
        return includesAnySpawn(actions, includeByDefault) || includesAnyKill(actions, includeByDefault);
    }

    public EntityActionFilter merge(EntityActionFilter other) {
        if (this == DEFAULT) {
            return other;
        }
        if (other == DEFAULT || this == other) {
            return this;
        }
        return from(aliased || other.aliased, spawned || other.spawned);
    }

    private static EntityActionFilter from(boolean aliased, boolean spawned) {
        if (aliased && spawned) {
            return ALL;
        }
        if (aliased) {
            return ALIASED;
        }
        if (spawned) {
            return SPAWNED;
        }
        return NONE;
    }

    private static boolean includesExactAction(Collection<Integer> actions, int action, boolean includeByDefault) {
        return actions.contains(action) || (actions.isEmpty() && includeByDefault);
    }
}
