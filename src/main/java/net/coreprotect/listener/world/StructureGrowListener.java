package net.coreprotect.listener.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.TreeType;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.StructureGrowEvent;

import net.coreprotect.config.Config;
import net.coreprotect.consumer.Queue;

public final class StructureGrowListener extends Queue implements Listener {

    @EventHandler(priority = EventPriority.MONITOR)
    protected void onStructureGrow(StructureGrowEvent event) {
        // Event that is called when an organic structure attempts to grow (Sapling -> Tree), (Mushroom -> Huge Mushroom), naturally or using bonemeal.
        TreeType treeType = event.getSpecies();
        String user = "#tree";
        int tree = 1;

        // Skip logging for bad event calls
        if (treeType == null || event.isFromBonemeal()) {
            return;
        }

        List<BlockState> blocks = event.getBlocks();
        if (blocks.size() <= 4) {
            for (BlockState block : blocks) {
                if (Tag.SAPLINGS.isTagged(block.getType()) || block.getType().equals(Material.BROWN_MUSHROOM) || block.getType().equals(Material.RED_MUSHROOM)) {
                    return;
                }
            }
        }

        if (treeType.name().toLowerCase(Locale.ROOT).contains("mushroom")) {
            user = "#mushroom";
            tree = 0;
        }

        if (!event.isCancelled()) {
            World world = event.getWorld();
            if ((tree == 1 && Config.getConfig(world).TREE_GROWTH) || (tree == 0 && Config.getConfig(world).MUSHROOM_GROWTH)) {
                Player player = event.getPlayer();
                Location location = event.getLocation();
                if (player != null) {
                    user = player.getName();
                }

                List<BlockState> structureBlocks = new ArrayList<>(blocks);
                structureBlocks.removeIf(replacedBlock -> replacedBlock.getY() > location.getBlockY());
                for (int i = 0; i < structureBlocks.size(); i++) {
                    BlockState replacedBlock = structureBlocks.get(i);
                    structureBlocks.set(i, replacedBlock.getBlock().getState());
                }

                int replacedListSize = structureBlocks.size();
                structureBlocks.addAll(blocks);

                Queue.queueStructureGrow(user, world.getBlockAt(location).getState(), structureBlocks, replacedListSize);
            }
        }
    }
}
