package net.coreprotect.api;

import java.util.List;

/**
 * Applies rollbacks and restores to the virtual containers of one type, registered with
 * CoreProtectAPI.registerVirtualContainer.
 */
public interface VirtualContainerHandler {

    /**
     * Applies changes to a virtual container. CoreProtect calls this on the server thread (on Folia, the global region
     * thread) with the changes of one container in the order they must be applied. Apply them in order and stop at
     * the first change that can't be applied, for example an item that is no longer in the container.
     *
     * @param id
     *            The container within its type, as passed to logVirtualContainerTransaction
     * @param changes
     *            The changes to apply
     * @return How many leading changes were applied. Only those are marked as rolled back or restored, so running the
     *         command again retries the rest.
     */
    int apply(String id, List<VirtualContainerChange> changes);
}
