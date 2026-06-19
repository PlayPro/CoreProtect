package net.coreprotect.listener.world;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.PortalCreateEvent;

import net.coreprotect.config.Config;
import net.coreprotect.consumer.Queue;
import net.coreprotect.database.Lookup;
import net.coreprotect.utility.BlockUtils;

import java.util.Locale;
import java.util.UUID;

public final class PortalCreateListener extends Queue implements Listener {

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPortalCreate(PortalCreateEvent event) {
        World world = event.getWorld();
        if (!Config.getConfig(world).PORTALS) {
            return;
        }

        String user = "#portal";
        for (BlockState block : event.getBlocks()) {
            Material type = block.getType();
            if (type == Material.NETHER_PORTAL || type == Material.FIRE) {
                String resultData = Lookup.whoPlacedCache(block);
                if (resultData.length() > 0) {
                    user = resultData;
                    break;
                }
            }
        }

        if (user.equals("#portal")) {
            // Find a more specific user, the check above only works for the fire creation cause, not the nether pair or end platform ones.

            final Entity causingEntity = switch (event.getEntity()) {
                case Player p -> p;
                case Projectile projectile when projectile.getShooter() instanceof Entity entity ->
                        entity;
                case Item item -> {
                    final UUID thrower = item.getThrower();
                    if (thrower != null) {
                        if (Bukkit.getServer().getPlayer(thrower) instanceof Player player) {
                            yield player;
                        } else if (event.getWorld().getEntity(thrower) instanceof Entity entity) {
                            yield entity;
                        }
                    }
                    yield event.getEntity();
                }
                case null, default -> event.getEntity();
            };

            if (causingEntity instanceof Player player) {
                user = player.getName();
            } else if (causingEntity != null) {
                user = "#" + causingEntity.getType().name().toLowerCase(Locale.ROOT);
            }
        }

        for (BlockState blockState : event.getBlocks()) {
            Material type = blockState.getType();
            BlockState oldBlock = blockState.getBlock().getState();
            if (oldBlock.equals(blockState)) {
                continue;
            }

            if (BlockUtils.isAir(type)) {
                Queue.queueBlockBreak(user, oldBlock, oldBlock.getType(), oldBlock.getBlockData().getAsString(), 0);
            }
            else {
                Queue.queueBlockPlace(user, blockState, oldBlock.getType(), oldBlock, type, -1, 0, blockState.getBlockData().getAsString());
            }
        }
    }
}
