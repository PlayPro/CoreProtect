package net.coreprotect.model.entity;

import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;

import net.coreprotect.utility.ItemUtils;

public final class EntityContainerTransaction {

    private static final int MAX_IDENTITY_RESOLUTION_ATTEMPTS = 3;

    private final UUID entityUuid;
    private final Location currentLocation;
    private final ItemStack[] oldContents;
    private final ItemStack[] newContents;
    private final int identityResolutionAttempts;

    public EntityContainerTransaction(UUID entityUuid, Location currentLocation, ItemStack[] oldContents, ItemStack[] newContents) {
        this(entityUuid, currentLocation, oldContents, newContents, 0);
    }

    private EntityContainerTransaction(UUID entityUuid, Location currentLocation, ItemStack[] oldContents, ItemStack[] newContents, int identityResolutionAttempts) {
        if (entityUuid == null || currentLocation == null || currentLocation.getWorld() == null || oldContents == null || newContents == null) {
            throw new IllegalArgumentException("Invalid entity container transaction");
        }
        this.entityUuid = entityUuid;
        this.currentLocation = currentLocation.clone();
        this.oldContents = ItemUtils.getContainerState(oldContents);
        this.newContents = ItemUtils.getContainerState(newContents);
        this.identityResolutionAttempts = identityResolutionAttempts;
    }

    public UUID getEntityUuid() {
        return entityUuid;
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

    public EntityContainerTransaction retry() {
        if (!canRetry()) {
            return null;
        }
        return new EntityContainerTransaction(entityUuid, currentLocation, oldContents, newContents, identityResolutionAttempts + 1);
    }

    public boolean canRetry() {
        return identityResolutionAttempts < MAX_IDENTITY_RESOLUTION_ATTEMPTS;
    }
}
