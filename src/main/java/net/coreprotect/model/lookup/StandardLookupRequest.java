package net.coreprotect.model.lookup;

import java.util.List;
import java.util.Map;

import org.bukkit.Location;

import net.coreprotect.model.action.EntityActionFilter;

public final class StandardLookupRequest {
    public final Filters filters;
    public final Scope scope;
    public final int page;
    public final int pageSize;
    public final LookupOutputMode outputMode;
    public final LookupRollbackState rollbackState;

    public StandardLookupRequest(Filters filters, Scope scope, int page, int pageSize, LookupOutputMode outputMode, LookupRollbackState rollbackState) {
        this.filters = filters;
        this.scope = scope;
        this.page = page;
        this.pageSize = pageSize;
        this.outputMode = outputMode;
        this.rollbackState = rollbackState;
    }

    public StandardLookupRequest forPage(int page, int pageSize) {
        return new StandardLookupRequest(filters, scope, page, pageSize, outputMode, rollbackState);
    }

    public StandardLookupRequest forSession(int resolvedWorldId) {
        Scope resolvedScope = new Scope(scope.location, scope.x, scope.y, scope.z, scope.worldId, resolvedWorldId, null,
                scope.startTime, scope.endTime, scope.radius);
        LookupOutputMode savedMode = outputMode == LookupOutputMode.COUNT ? LookupOutputMode.DETAIL : outputMode;
        return new StandardLookupRequest(filters, resolvedScope, page, pageSize, savedMode, rollbackState);
    }

    public static final class Filters {
        public final List<String> users;
        public final List<Object> includedBlocks;
        public final Map<Object, Boolean> excludedBlocks;
        public final List<String> excludedUsers;
        public final List<Integer> actions;
        public final EntityActionFilter entityActions;
        public final List<String> messages;

        public Filters(List<String> users, List<Object> includedBlocks, Map<Object, Boolean> excludedBlocks, List<String> excludedUsers,
                List<Integer> actions, EntityActionFilter entityActions, List<String> messages) {
            this.users = List.copyOf(users);
            this.includedBlocks = List.copyOf(includedBlocks);
            this.excludedBlocks = Map.copyOf(excludedBlocks);
            this.excludedUsers = List.copyOf(excludedUsers);
            this.actions = List.copyOf(actions);
            this.entityActions = entityActions;
            this.messages = List.copyOf(messages);
        }
    }

    public static final class Scope {
        private final Location location;
        public final int x;
        public final int y;
        public final int z;
        public final int worldId;
        public final int explicitWorldId;
        public final String pendingWorldName;
        public final long startTime;
        public final long endTime;
        private final Integer[] radius;
        public Scope(Location location, int x, int y, int z, int worldId, int explicitWorldId, String pendingWorldName,
                long startTime, long endTime, Integer[] radius) {
            this.location = location == null ? null : location.clone();
            this.x = x;
            this.y = y;
            this.z = z;
            this.worldId = worldId;
            this.explicitWorldId = explicitWorldId;
            this.pendingWorldName = pendingWorldName;
            this.startTime = startTime;
            this.endTime = endTime;
            this.radius = radius == null ? null : radius.clone();
        }

        public Location location() {
            return location == null ? null : location.clone();
        }

        public Integer[] radius() {
            return radius == null ? null : radius.clone();
        }
    }
}
