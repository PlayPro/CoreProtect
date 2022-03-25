package net.coreprotect.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;

import net.coreprotect.config.Config;
import net.coreprotect.consumer.Consumer;
import net.coreprotect.consumer.Queue;
import net.coreprotect.consumer.process.Process;
import net.coreprotect.utility.Util;

public class QueueLookup extends Queue {

    private QueueLookup() {
        throw new IllegalStateException("API class");
    }

    public static List<String[]> performLookup(Block block) {
        List<String[]> result = new ArrayList<>();
        if (!Config.getGlobal().API_ENABLED) {
            return result;
        }

        try {
            int consumerCount = 0;
            int currentConsumerSize = Process.getCurrentConsumerSize();
            if (currentConsumerSize == 0) {
                consumerCount = Consumer.getConsumerSize(0) + Consumer.getConsumerSize(1);
            }
            else {
                int consumerId = (Consumer.currentConsumer == 1) ? 1 : 0;
                consumerCount = Consumer.getConsumerSize(consumerId) + currentConsumerSize;
            }

            if (consumerCount == 0) {
                return result;
            }

            int currentConsumer = Consumer.currentConsumer;
            ArrayList<Object[]> consumerData = Consumer.consumer.get(currentConsumer);
            Map<Integer, String[]> users = Consumer.consumerUsers.get(currentConsumer);
            Map<Integer, Object> consumerObject = Consumer.consumerObjects.get(currentConsumer);

            Location oldLocation = block.getLocation();
            for (Object[] data : consumerData) {
                int id = (int) data[0];
                int action = (int) data[1];
                if (action != Process.BLOCK_BREAK && action != Process.BLOCK_PLACE) {
                    continue;
                }

                String[] userData = users.get(id);
                Object objectData = consumerObject.get(id);
                if (userData != null && objectData != null && (objectData instanceof BlockState) && ((BlockState) objectData).getLocation().equals(oldLocation)) {
                    Material blockType = (Material) data[2];
                    int legacyData = (int) data[3];
                    String blockData = (String) data[7];
                    String user = userData[0];
                    BlockState blockState = (BlockState) objectData;
                    Location location = blockState.getLocation();
                    int wid = Util.getWorldId(location.getWorld().getName());
                    int resultType = Util.getBlockId(blockType);
                    int time = (int) (System.currentTimeMillis() / 1000L);

                    String[] lookupData = new String[] { String.valueOf(time), user, String.valueOf(location.getBlockX()), String.valueOf(location.getBlockY()), String.valueOf(location.getBlockZ()), String.valueOf(resultType), String.valueOf(legacyData), String.valueOf(action), "0", String.valueOf(wid), blockData };
                    String[] lineData = Util.toStringArray(lookupData);
                    result.add(lineData);
                }
            }

            Collections.reverse(result);
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        return result;
    }

}
