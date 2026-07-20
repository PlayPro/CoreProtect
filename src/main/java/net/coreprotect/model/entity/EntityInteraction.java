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
    private final int time;
    private final int identityResolutionAttempts;
    private final boolean identityPromotion;

    public EntityInteraction(UUID entityUuid, EntityType entityType, EntityInteractionOrigin origin, Location currentLocation, EntityInteractionAction action, byte[] metadata) {
        this(entityUuid, entityType, origin, currentLocation, action, metadata, (int) (System.currentTimeMillis() / 1000L));
    }

    public EntityInteraction(UUID entityUuid, EntityType entityType, EntityInteractionOrigin origin, Location currentLocation, EntityInteractionAction action, byte[] metadata, int time) {
        this(entityUuid, entityType, origin, currentLocation, action, metadata, time, 0, false);
    }

    private EntityInteraction(UUID entityUuid, EntityType entityType, EntityInteractionOrigin origin, Location currentLocation, EntityInteractionAction action, byte[] metadata, int time, int identityResolutionAttempts, boolean identityPromotion) {
        if (entityUuid == null || entityType == null || origin == null || currentLocation == null || currentLocation.getWorld() == null || action == null) {
            throw new IllegalArgumentException("Invalid entity interaction");
        }
        this.entityUuid = entityUuid;
        this.entityType = entityType;
        this.origin = origin;
        this.currentLocation = currentLocation.clone();
        this.action = action;
        this.metadata = metadata == null ? null : metadata.clone();
        this.time = time;
        this.identityResolutionAttempts = identityResolutionAttempts;
        this.identityPromotion = identityPromotion;
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

    public int getTime() {
        return time;
    }

    public boolean hasIdentityPromotion() {
        return identityPromotion;
    }

    public EntityInteraction withAction(EntityInteractionAction action) {
        if (this.action == action) {
            return this;
        }
        return new EntityInteraction(entityUuid, entityType, origin, currentLocation, action, metadata, time, identityResolutionAttempts, identityPromotion);
    }

    public EntityInteraction withIdentityPromotion(boolean promotion) {
        if (identityPromotion == promotion) {
            return this;
        }
        return new EntityInteraction(entityUuid, entityType, origin, currentLocation, action, metadata, time, identityResolutionAttempts, promotion);
    }

    public EntityInteraction retry() {
        if (!canRetry()) {
            return null;
        }
        return new EntityInteraction(entityUuid, entityType, origin, currentLocation, action, metadata, time, identityResolutionAttempts + 1, identityPromotion);
    }

    public boolean canRetry() {
        return identityResolutionAttempts < MAX_IDENTITY_RESOLUTION_ATTEMPTS;
    }
}
