package net.coreprotect.api;

import net.coreprotect.config.Config;
import net.coreprotect.consumer.Consumer;
import net.coreprotect.consumer.Queue;
import net.coreprotect.consumer.process.Process;
import net.coreprotect.utility.ErrorReporter;
import net.coreprotect.utility.MaterialUtils;
import net.coreprotect.utility.StringUtils;
import net.coreprotect.utility.WorldUtils;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;

import java.util.*;

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

        if (block == null) {
            return result;
        }

        try {
            List<Object[]> matches = new ArrayList<>();
            synchronized (Consumer.consumer_id) {
                int currentConsumer = Consumer.currentConsumer;
                Map<Integer, String[]> users = Consumer.consumerUsers.get(currentConsumer);
                Map<Integer, Object> consumerObject = Consumer.consumerObjects.get(currentConsumer);
                for (Object[] data : Consumer.consumer.get(currentConsumer)) {
                    int action = (int) data[1];
                    if (action != Process.BLOCK_BREAK && action != Process.BLOCK_PLACE) {
                        continue;
                    }

                    int id = (int) data[0];
                    String[] userData = users.get(id);
                    Object objectData = consumerObject.get(id);
                    if (isActionForBlock(userData, objectData, block)) {
                        matches.add(new Object[]{data, userData, objectData});
                    }
                }
            }

            for (Object[] match : matches) {
                Object[] data = (Object[]) match[0];
                String[] userData = (String[]) match[1];
                int action = (int) data[1];
                Material blockType = (Material) data[2];
                int legacyData = (int) data[3];
                String blockData = (String) data[7];
                String user = userData[0];
                BlockState blockState = (BlockState) match[2];
                Location location = blockState.getLocation();
                int worldId = WorldUtils.getWorldId(location.getWorld().getName());
                int resultType = MaterialUtils.getBlockId(blockType);
                int time = (int) (System.currentTimeMillis() / 1000L);

                String[] lookupData = new String[]{String.valueOf(time), user, String.valueOf(location.getBlockX()), String.valueOf(location.getBlockY()), String.valueOf(location.getBlockZ()), String.valueOf(resultType), String.valueOf(legacyData), String.valueOf(action), "0", String.valueOf(worldId), blockData};

                result.add(StringUtils.toStringArray(lookupData));
            }

            // Reverse the result list to match database lookup order (most recent first)
            Collections.reverse(result);
        }
        catch (Exception e) {
            ErrorReporter.report(e);
        }

        return result;
    }

    /**
     * Determines if an action in the queue pertains to the specified block location.
     * 
     * @param userData
     *            User data associated with the action
     * @param objectData
     *            Object data associated with the action
     * @param block
     *            The block being looked up
     * @return true if the action pertains to the specified block, false otherwise
     */
    private static boolean isActionForBlock(String[] userData, Object objectData, Block block) {
        if (userData == null || !(objectData instanceof BlockState)) {
            return false;
        }

        BlockState state = (BlockState) objectData;
        return state.getX() == block.getX() && state.getY() == block.getY() && state.getZ() == block.getZ() && Objects.equals(state.getWorld(), block.getWorld());
    }
}
