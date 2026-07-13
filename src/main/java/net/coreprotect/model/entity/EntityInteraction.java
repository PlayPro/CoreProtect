package net.coreprotect.model.entity;

import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.entity.EntityType;

public final class EntityInteraction {

    private static final int MAX_IDENTITY_RESOLUTION_ATTEMPTS = 3;

    private final UUID entityUuid;
    private final EntityType entityType;
    private final EntityInteractionOrigin origin;
    private final Location currentLocation;
    private final EntityInteractionAction action;
    private final byte[] metadata;
    private final int identityResolutionAttempts;

    public EntityInteraction(UUID entityUuid, EntityType entityType, EntityInteractionOrigin origin, Location currentLocation, EntityInteractionAction action, byte[] metadata) {
        this(entityUuid, entityType, origin, currentLocation, action, metadata, 0);
    }

    private EntityInteraction(UUID entityUuid, EntityType entityType, EntityInteractionOrigin origin, Location currentLocation, EntityInteractionAction action, byte[] metadata, int identityResolutionAttempts) {
        if (entityUuid == null || entityType == null || origin == null || currentLocation == null || currentLocation.getWorld() == null || action == null) {
            throw new IllegalArgumentException("Invalid entity interaction");
        }
        this.entityUuid = entityUuid;
        this.entityType = entityType;
        this.origin = origin;
        this.currentLocation = currentLocation.clone();
        this.action = action;
        this.metadata = metadata == null ? null : metadata.clone();
        this.identityResolutionAttempts = identityResolutionAttempts;
    }

    public UUID getEntityUuid() {
        return entityUuid;
    }

    public EntityType getEntityType() {
        return entityType;
    }

    public EntityInteractionOrigin getOrigin() {
        return origin;
    }

    public Location getCurrentLocation() {
        return currentLocation.clone();
    }

    public EntityInteractionAction getAction() {
        return action;
    }

    public byte[] getMetadata() {
        return metadata == null ? null : metadata.clone();
    }

    public EntityInteraction retry() {
        if (!canRetry()) {
            return null;
        }
        return new EntityInteraction(entityUuid, entityType, origin, currentLocation, action, metadata, identityResolutionAttempts + 1);
    }

    public boolean canRetry() {
        return identityResolutionAttempts < MAX_IDENTITY_RESOLUTION_ATTEMPTS;
    }
}
