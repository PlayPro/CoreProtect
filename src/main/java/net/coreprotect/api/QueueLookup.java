package net.coreprotect.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;

import net.coreprotect.config.Config;
import net.coreprotect.consumer.Consumer;
import net.coreprotect.consumer.Queue;
import net.coreprotect.consumer.process.Process;
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

        if (block == null) {
            return result;
        }

        try {
            // Determine total count of actions in the consumer queues
            int consumerCount = calculateConsumerCount();

            if (consumerCount == 0) {
                return result;
            }

            // Get data from the current consumer
            int currentConsumer = Consumer.currentConsumer;
            ArrayList<Object[]> consumerData = Consumer.consumer.get(currentConsumer);
            Map<Integer, String[]> users = Consumer.consumerUsers.get(currentConsumer);
            Map<Integer, Object> consumerObject = Consumer.consumerObjects.get(currentConsumer);

            // Current block location for comparison with actions in the queue
            Location blockLocation = block.getLocation();

            // Check for block actions in the processing queue
            ListIterator<Object[]> iterator = consumerData.listIterator();
            while (iterator.hasNext()) {
                Object[] data = iterator.next();
                int id = (int) data[0];
                int action = (int) data[1];

                // Only process block break and place actions
                if (action != Process.BLOCK_BREAK && action != Process.BLOCK_PLACE) {
                    continue;
                }

                String[] userData = users.get(id);
                Object objectData = consumerObject.get(id);

                // Verify the action pertains to the requested block
                if (isActionForBlock(userData, objectData, blockLocation)) {
                    Material blockType = (Material) data[2];
                    int legacyData = (int) data[3];
                    String blockData = (String) data[7];
                    String user = userData[0];
                    BlockState blockState = (BlockState) objectData;
                    Location location = blockState.getLocation();
                    int worldId = WorldUtils.getWorldId(location.getWorld().getName());
                    int resultType = MaterialUtils.getBlockId(blockType);
                    int time = (int) (System.currentTimeMillis() / 1000L);

                    String[] lookupData = new String[] { String.valueOf(time), user, String.valueOf(location.getBlockX()), String.valueOf(location.getBlockY()), String.valueOf(location.getBlockZ()), String.valueOf(resultType), String.valueOf(legacyData), String.valueOf(action), "0", String.valueOf(worldId), blockData };

                    result.add(StringUtils.toStringArray(lookupData));
                }
            }

            // Reverse the result list to match database lookup order (most recent first)
            Collections.reverse(result);
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        return result;
    }

    /**
     * Calculates the total count of actions in the consumer queues.
     * 
     * @return The total count of actions in the consumer queues
     */
    private static int calculateConsumerCount() {
        int currentConsumerSize = Process.getCurrentConsumerSize();
        if (currentConsumerSize == 0) {
            return Consumer.getConsumerSize(0) + Consumer.getConsumerSize(1);
        }
        else {
            int consumerId = (Consumer.currentConsumer == 1) ? 1 : 0;
            return Consumer.getConsumerSize(consumerId) + currentConsumerSize;
        }
    }

    /**
     * Determines if an action in the queue pertains to the specified block location.
     * 
     * @param userData
     *            User data associated with the action
     * @param objectData
     *            Object data associated with the action
     * @param blockLocation
     *            Location of the block being looked up
     * @return true if the action pertains to the specified block, false otherwise
     */
    private static boolean isActionForBlock(String[] userData, Object objectData, Location blockLocation) {
        return userData != null && objectData != null && (objectData instanceof BlockState) && ((BlockState) objectData).getLocation().equals(blockLocation);
    }
}
