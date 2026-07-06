package net.coreprotect.paper.listener;

import org.bukkit.entity.CopperGolem;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import io.papermc.paper.event.entity.ItemTransportingEntityValidateTargetEvent;

/** Paper 26.2+ target-event bridge, kept separate for 26.1.x compatibility. */
public final class CopperGolemTargetListener implements Listener {

    private final CopperGolemChestListener delegate;

    public CopperGolemTargetListener(CopperGolemChestListener delegate) {
        this.delegate = delegate;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onValidateTarget(ItemTransportingEntityValidateTargetEvent event) {
        if (event.isAllowed() && event.getEntity() instanceof CopperGolem) {
            delegate.captureTarget((CopperGolem) event.getEntity(), event.getBlock().getState());
        }
    }
}
