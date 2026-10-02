package net.coreprotect.model.entity;

import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;

import net.coreprotect.utility.ItemUtils;

public final class EntityContainerTransaction {

    private static final int MAX_IDENTITY_RESOLUTION_ATTEMPTS = 3;

    private final UUID entityUuid;
    private final EntityInteractionOrigin origin;
    private final Location currentLocation;
    private final ItemStack[] oldContents;
    private final ItemStack[] newContents;
    private final int time;
    private final int identityResolutionAttempts;
    private final boolean identityPromotion;

    public EntityContainerTransaction(UUID entityUuid, Location currentLocation, ItemStack[] oldContents, ItemStack[] newContents) {
        this(entityUuid, null, currentLocation, oldContents, newContents, (int) (System.currentTimeMillis() / 1000L), 0, false);
    }

    public EntityContainerTransaction(UUID entityUuid, EntityInteractionOrigin origin, Location currentLocation, ItemStack[] oldContents, ItemStack[] newContents) {
        this(entityUuid, origin, currentLocation, oldContents, newContents, (int) (System.currentTimeMillis() / 1000L), 0, false);
    }

    private EntityContainerTransaction(UUID entityUuid, EntityInteractionOrigin origin, Location currentLocation, ItemStack[] oldContents, ItemStack[] newContents, int time, int identityResolutionAttempts, boolean identityPromotion) {
        if (entityUuid == null || currentLocation == null || currentLocation.getWorld() == null || oldContents == null || newContents == null) {
            throw new IllegalArgumentException("Invalid entity container transaction");
        }
        this.entityUuid = entityUuid;
        this.origin = origin;
        this.currentLocation = currentLocation.clone();
        this.oldContents = ItemUtils.getContainerState(oldContents);
        this.newContents = ItemUtils.getContainerState(newContents);
        this.time = time;
        this.identityResolutionAttempts = identityResolutionAttempts;
        this.identityPromotion = identityPromotion;
    }

    public UUID getEntityUuid() {
        return entityUuid;
    }

    public EntityInteractionOrigin getOrigin() {
        return origin;
    }

    public Location getCurrentLocation() {
        return currentLocation.clone();
    }

    public ItemStack[] getOldContents() {
        return ItemUtils.getContainerState(oldContents);
    }

    public ItemStack[] getNewContents() {
        return ItemUtils.getContainerState(newContents);
    }

    public int getTime() {
        return time;
    }

    public boolean hasIdentityPromotion() {
        return identityPromotion;
    }

    public EntityContainerTransaction withIdentityPromotion(boolean promotion) {
        if (identityPromotion == promotion) {
            return this;
        }
        return new EntityContainerTransaction(entityUuid, origin, currentLocation, oldContents, newContents, time, identityResolutionAttempts, promotion);
    }

    public EntityContainerTransaction retry() {
        if (!canRetry()) {
            return null;
        }
        return new EntityContainerTransaction(entityUuid, origin, currentLocation, oldContents, newContents, time, identityResolutionAttempts + 1, identityPromotion);
    }

    public boolean canRetry() {
        return identityResolutionAttempts < MAX_IDENTITY_RESOLUTION_ATTEMPTS;
    }
}
