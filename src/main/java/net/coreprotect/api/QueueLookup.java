package net.coreprotect.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;

import net.coreprotect.config.Config;
import net.coreprotect.consumer.Consumer;
import net.coreprotect.consumer.Queue;
import net.coreprotect.consumer.QueuePayload;
import net.coreprotect.consumer.process.Process;
import net.coreprotect.model.action.LookupActions;
import net.coreprotect.utility.ErrorReporter;
import net.coreprotect.utility.MaterialUtils;
import net.coreprotect.utility.StringUtils;
import net.coreprotect.utility.WorldUtils;

/**
 * Provides API methods for looking up block-related actions in the processing queue.
 * This class allows for retrieving actions that have not yet been saved to the database.
 */
public class QueueLookup extends Queue {

    /**
     * Private constructor to prevent instantiation.
     * This is a utility class with static methods only.
     */
    private QueueLookup() {
        throw new IllegalStateException("API class");
    }

    /**
     * Performs a lookup of block-related actions in the processing queue for the specified block.
     * This allows retrieving actions that have not yet been committed to the database.
     * 
     * @param block
     *            The block to look up in the processing queue
     * @return List of results in a String array format, empty list if API is disabled or no results found
     */
    public static List<String[]> performLookup(Block block) {
        List<String[]> result = new ArrayList<>();

        if (!Config.getGlobal().API_ENABLED) {
            return result;
        }

        World world = block == null ? null : block.getWorld();
        if (world == null) {
            return result;
        }

        try {
            int worldId = WorldUtils.findHistoricalWorldId(world.getName());
            if (worldId < 0) {
                return result;
            }

            String time = Long.toString(System.currentTimeMillis() / 1000L);
            for (PendingAction entry : snapshotBlockActions()) {
                Object payload = QueuePayload.primary(entry.payload);
                int action = (int) entry.data[1];
                if (action == Process.STRUCTURE_GROWTH) {
                    List<BlockState> states = QueuePayload.detail(entry.payload);
                    if (states != null) {
                        int replacedCount = (int) entry.data[6];
                        for (int index = 0; index < states.size(); index++) {
                            BlockState state = states.get(index);
                            if (matches(state, block, world)) {
                                BlockData data = state.getBlockData();
                                int resultAction = index < replacedCount ? LookupActions.BLOCK_BREAK : LookupActions.BLOCK_PLACE;
                                result.add(toLookupRow(block, worldId, time, entry.user, resultAction, state.getType(), 0, data == null ? null : data.getAsString()));
                            }
                        }
                    }
                }
                else if (matches(payload, block, world)) {
                    int resultAction = action == Process.PLAYER_INTERACTION ? LookupActions.INTERACTION
                            : action == Process.BLOCK_PLACE ? LookupActions.BLOCK_PLACE : LookupActions.BLOCK_BREAK;
                    result.add(toLookupRow(block, worldId, time, entry.user, resultAction, (Material) entry.data[2], (int) entry.data[3], (String) entry.data[7]));
                }
            }

            Collections.reverse(result);
        }
        catch (Exception e) {
            ErrorReporter.report(e);
        }

        return result;
    }

    private static List<PendingAction> snapshotBlockActions() {
        List<PendingAction> actions = new ArrayList<>();
        synchronized (Queue.class) {
            synchronized (Consumer.consumer_id) {
                int active = Consumer.currentConsumer;
                for (int buffer : new int[] { 1 - active, active }) {
                    List<Object[]> rows = Consumer.consumer.get(buffer);
                    if (rows == null || rows.isEmpty()) {
                        continue;
                    }
                    Map<Integer, String[]> users = Consumer.consumerUsers.get(buffer);
                    Map<Integer, Object> objects = Consumer.consumerObjects.get(buffer);
                    for (Object[] row : rows) {
                        int action = (int) row[1];
                        if (action != Process.BLOCK_BREAK && action != Process.BLOCK_PLACE && action != Process.NATURAL_BLOCK_BREAK
                                && action != Process.STRUCTURE_GROWTH && action != Process.PLAYER_INTERACTION) {
                            continue;
                        }
                        int id = (int) row[0];
                        String[] user = users.get(id);
                        Object object = objects.get(id);
                        if (user != null && object != null) {
                            actions.add(new PendingAction(row, user[0], object));
                        }
                    }
                }
            }
        }
        return actions;
    }

    private static boolean matches(Object payload, Block block, World world) {
        if (payload instanceof BlockState) {
            BlockState state = (BlockState) payload;
            return world.equals(state.getWorld()) && block.getX() == state.getX() && block.getY() == state.getY() && block.getZ() == state.getZ();
        }
        if (payload instanceof Location) {
            Location location = (Location) payload;
            return world.equals(location.getWorld()) && block.getX() == location.getBlockX() && block.getY() == location.getBlockY() && block.getZ() == location.getBlockZ();
        }
        return false;
    }

    private static String[] toLookupRow(Block block, int worldId, String time, String user, int action, Material type, int data, String blockData) {
        int typeId = MaterialUtils.getBlockId(type == null ? Material.AIR.name() : type.name(), false);
        return StringUtils.toStringArray(new String[] { time, user, String.valueOf(block.getX()), String.valueOf(block.getY()),
                String.valueOf(block.getZ()), String.valueOf(typeId), String.valueOf(data), String.valueOf(action), "0", String.valueOf(worldId), blockData });
    }

    private static final class PendingAction {
        private final Object[] data;
        private final String user;
        private final Object payload;

        private PendingAction(Object[] data, String user, Object payload) {
            this.data = data;
            this.user = user;
            this.payload = payload;
        }
    }
}
