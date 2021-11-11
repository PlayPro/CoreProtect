package net.coreprotect;

import java.sql.Connection;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import net.coreprotect.database.lookup.ChestTransactionLookupAPI;
import net.coreprotect.utility.APIUtil;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import net.coreprotect.config.Config;
import net.coreprotect.consumer.Queue;
import net.coreprotect.database.Database;
import net.coreprotect.database.Lookup;
import net.coreprotect.database.Rollback;
import net.coreprotect.database.lookup.BlockLookupAPI;
import net.coreprotect.language.Phrase;
import net.coreprotect.listener.player.InventoryChangeListener;
import net.coreprotect.utility.Chat;
import net.coreprotect.utility.Util;
import org.bukkit.inventory.Inventory;

public class CoreProtectAPI extends Queue {

    private static class ParseResult {

        protected final String[] parse;

        public ParseResult(String[] data) {
            parse = data;
        }

        public int getActionId() {
            return Integer.parseInt(parse[7]);
        }

        public String getActionString() {
            String res;
            switch(getActionId()) {
                case 0: res = "break"; break;
                case 1: res = "place"; break;
                case 2: res = "click"; break;
                case 3: res = "kill"; break;
                default: res = "unknown"; break;
            }
            return res;
        }

        public int getData() {
            return Integer.parseInt(parse[6]);
        }

        @Deprecated
        public String getPlayer() {
            return getEntity();
        }

        public String getEntity() {
            return parse[1];
        }

        @Deprecated
        public int getTime() {
            return Integer.parseInt(parse[0]);
        }

        public long getTimeLong() {
            return Long.parseLong(parse[0]);
        }

        public long getTimestamp() {
            return getTimeLong() * 1000L;
        }

        public Material getType() {
            int actionID = this.getActionId();
            int type = Integer.parseInt(parse[5]);
            String typeName;

            if (actionID == 3) {
                typeName = Util.getEntityType(type).name();
            } else {
                typeName = Util.getType(type).name().toLowerCase(Locale.ROOT);
                typeName = Util.nameFilter(typeName, this.getData());
            }

            return Util.getType(typeName);
        }

        public Block getBlock() {
            return getLocation().getBlock();
        }

        public boolean isRolledBack() {
            return Integer.parseInt(parse[8]) == 1;
        }

        public int getX() {
            return Integer.parseInt(parse[2]);
        }

        public int getY() {
            return Integer.parseInt(parse[3]);
        }

        public int getZ() {
            return Integer.parseInt(parse[4]);
        }

        public Location getLocation() {
            return new Location(getWorld(), getX(), getY(), getZ());
        }

        public String getWorldName() {
            return Util.getWorldName(Integer.parseInt(parse[9]));
        }

        public World getWorld() {
            return Bukkit.getWorld(Util.getWorldName(Integer.parseInt(parse[9])));
        }
    }

    public static class BlockLookupResults extends ParseResult {

        public BlockLookupResults(String[] data) {
            super(data);
        }

        public BlockData getBlockData() {
            String blockData = parse[11];
            if (blockData.length() == 0) {
                return getType().createBlockData();
            }
            return Bukkit.getServer().createBlockData(blockData);
        }

    }

    public static class ContainerLookupResults extends ParseResult {

        public ContainerLookupResults(String[] data) {
            super(data);
        }

        @Override
        public String getActionString() {
            String res;
            switch(getActionId()) {
                case 0: res = "added"; break;
                case 1: res = "removed"; break;
                default: res = "unknown"; break;
            }
            return res;
        }

        public int getAmount() {
            return Integer.parseInt(parse[12]);
        }

        public Inventory getInventory() {
            return Util.getContainerInventory(getBlock().getState(), true);
        }

    }

    public int APIVersion() {
        return 8;
    }

    public List<String[]> blockLookup(Block block, long time) {
        if (Config.getGlobal().API_ENABLED && (Config.getConfig(block.getWorld()).BLOCK_MOVEMENT
                || Config.getConfig(block.getWorld()).BLOCK_BREAK
                || Config.getConfig(block.getWorld()).BLOCK_PLACE
                || Config.getConfig(block.getWorld()).BLOCK_IGNITE
                || Config.getConfig(block.getWorld()).BLOCK_BURN))
            return BlockLookupAPI.performLookup(block, time);
        return null;
    }

    public List<String[]> blockLookup(Block block) {
        if (Config.getGlobal().API_ENABLED && (Config.getConfig(block.getWorld()).BLOCK_MOVEMENT
                || Config.getConfig(block.getWorld()).BLOCK_BREAK
                || Config.getConfig(block.getWorld()).BLOCK_PLACE
                || Config.getConfig(block.getWorld()).BLOCK_IGNITE
                || Config.getConfig(block.getWorld()).BLOCK_BURN))
            return BlockLookupAPI.performLookup(block);
        return null;
    }

    public List<String[]> containerLookup(Block block, long time) {
        if (Config.getGlobal().API_ENABLED && Config.getConfig(block.getWorld()).ITEM_TRANSACTIONS)
            return ChestTransactionLookupAPI.performLookup(block, time);
        return null;
    }

    public List<String[]> containerLookup(Block block) {
        if (Config.getGlobal().API_ENABLED && Config.getConfig(block.getWorld()).ITEM_TRANSACTIONS)
            return ChestTransactionLookupAPI.performLookup(block);
        return null;
    }

    public List<BlockLookupResults> blockLookupParsed(Block block, long time) {
        return blockLookup(block, time).stream().map(BlockLookupResults::new).toList();
    }

    public List<BlockLookupResults> blockLookupParsed(Block block) {
        return blockLookup(block).stream().map(BlockLookupResults::new).toList();
    }

    public List<ContainerLookupResults> containerLookupParsed(Block block, long time) {
        return containerLookup(block, time).stream().map(ContainerLookupResults::new).toList();
    }

    public List<ContainerLookupResults> containerLookupParsed(Block block) {
        return containerLookup(block).stream().map(ContainerLookupResults::new).toList();
    }

    public BlockLookupResults parseBlockLookupResults(String[] results) {
        return new BlockLookupResults(results);
    }

    public ContainerLookupResults parseContainerLookupResult(String[] results) {
        return new ContainerLookupResults(results);
    }

    public boolean hasPlaced(String user, Block block, long time, int offset) {
        // Determine if a user has placed a block at this location in the last # of seconds.
        boolean match = false;

        if (Config.getGlobal().API_ENABLED) {
            long timestamp = System.currentTimeMillis() / 1000L;
            long offsetTime = timestamp - offset;
            List<String[]> check = blockLookup(block, time);

            for (String[] value : check) {
                BlockLookupResults result = parseBlockLookupResults(value);
                if (user.equalsIgnoreCase(result.getEntity()) && result.getActionId() == 1 && result.getTimestamp() <= offsetTime) {
                    match = true;
                    break;
                }
            }
        }

        return match;
    }

    public boolean hasRemoved(String user, Block block, long time, int offset) {
        // Determine if a user has removed a block at this location in the last # of seconds.
        boolean match = false;

        if (Config.getGlobal().API_ENABLED) {
            long timestamp = System.currentTimeMillis() / 1000L;
            long offsetTime = timestamp - offset;
            List<String[]> check = blockLookup(block, time);

            for (String[] value : check) {
                BlockLookupResults result = parseBlockLookupResults(value);
                if (user.equalsIgnoreCase(result.getEntity()) && result.getActionId() == 0 && result.getTimestamp() <= offsetTime) {
                    match = true;
                    break;
                }
            }
        }

        return match;
    }

    public boolean isEnabled() {
        return Config.getGlobal().API_ENABLED;
    }

    public boolean logChat(Player player, String message) {
        if (Config.getGlobal().API_ENABLED && player != null && Config.getConfig(player.getWorld()).PLAYER_MESSAGES
                && message != null && message.length() > 0 && !message.startsWith("/")) {
             long timestamp = System.currentTimeMillis() / 1000L;

             Queue.queuePlayerChat(player, message, timestamp);
             return true;
        }
        return false;
    }

    public boolean logCommand(Player player, String command) {
        if (Config.getGlobal().API_ENABLED && player != null && Config.getConfig(player.getWorld()).PLAYER_COMMANDS
                && command != null && command.startsWith("/")) {
            long timestamp = System.currentTimeMillis() / 1000L;

            Queue.queuePlayerCommand(player, command, timestamp);
            return true;
        }

        return false;
    }

    public boolean logInteraction(String user, Location location) {
        if (Config.getGlobal().API_ENABLED && user != null && location != null && user.length() > 0) {
            Queue.queuePlayerInteraction(user, location.getBlock().getState());
            return true;
        }
        return false;
    }

    public boolean logContainerTransaction(String user, Location location) {
        if (Config.getGlobal().API_ENABLED) {
            return InventoryChangeListener.inventoryTransaction(user, location, null);
        }
        return false;
    }

    public boolean logPlacement(String user, Location location, Material type, BlockData blockData) {
        if (Config.getGlobal().API_ENABLED && user != null && location != null && user.length() > 0) {
            Block block = location.getBlock();
            BlockState blockState = block.getState();
            String blockDataString = null;

            if (blockData != null) {
                blockDataString = blockData.getAsString();
            }

            Queue.queueBlockPlace(user, blockState, block.getType(), null, type, -1, 0, blockDataString);
            return true;
        }
        return false;
    }

    @Deprecated
    public boolean logPlacement(String user, Location location, Material type, byte data) {
        if (Config.getGlobal().API_ENABLED && user != null && location != null && user.length() > 0) {
            Queue.queueBlockPlace(user, location.getBlock().getState(), location.getBlock().getType(), null, type, data, 1, null);
            return true;
        }

        return false;
    }

    public boolean logRemoval(String user, Location location, Material type, BlockData blockData) {
        if (Config.getGlobal().API_ENABLED && user != null && location != null && user.length() > 0) {
            String blockDataString = null;

            if (blockData != null) {
                blockDataString = blockData.getAsString();
            }

            Block block = location.getBlock();
            Database.containerBreakCheck(user, block.getType(), block, null, location);
            Queue.queueBlockBreak(user, location.getBlock().getState(), type, blockDataString, 0);
            return true;
        }
        return false;
    }

    @Deprecated
    public boolean logRemoval(String user, Location location, Material type, byte data) {
        if (Config.getGlobal().API_ENABLED && user != null && location != null && user.length() > 0) {
            Queue.queueBlockBreak(user, location.getBlock().getState(), type, type.createBlockData().getAsString(), data);
            return true;
        }
        return false;
    }

    public List<String[]> performLookup(long time, List<String> restrictUsers, List<String> excludeUsers, List<Object> restrictBlocks, List<Object> excludeBlocks, List<Integer> actionList, int radius, Location radiusLocation) {
        if (Config.getGlobal().API_ENABLED) {
            return processData(time, radius, radiusLocation, APIUtil.parseList(restrictBlocks), APIUtil.parseList(excludeBlocks), restrictUsers, excludeUsers, actionList, 0, 1, -1, -1, false);
        }
        return null;
    }

    @Deprecated
    public List<String[]> performLookup(String user, long time, int radius, Location location, List<Object> restrict, List<Object> exclude) {
        if (Config.getGlobal().API_ENABLED) {
            return processData(user, time, radius, location, APIUtil.parseList(restrict), APIUtil.parseList(exclude), 0, 1, -1, -1, false);
        }
        return null;
    }

    public List<String[]> performPartialLookup(long time, List<String> restrictUsers, List<String> excludeUsers, List<Object> restrictBlocks, List<Object> excludeBlocks, List<Integer> actionList, int radius, Location radiusLocation, int limitOffset, int limitCount) {
        if (Config.getGlobal().API_ENABLED) {
            return processData(time, radius, radiusLocation, APIUtil.parseList(restrictBlocks), APIUtil.parseList(excludeBlocks), restrictUsers, excludeUsers, actionList, 0, 1, limitOffset, limitCount, true);
        }
        return null;
    }

    @Deprecated
    public List<String[]> performPartialLookup(String user, long time, int radius, Location location, List<Object> restrict, List<Object> exclude, int limitOffset, int limitCount) {
        if (Config.getGlobal().API_ENABLED) {
            return processData(user, time, radius, location, APIUtil.parseList(restrict), APIUtil.parseList(exclude), 0, 1, limitOffset, limitCount, true);
        }
        return null;
    }

    public void performPurge(long time) {
        Server server = Bukkit.getServer();
        server.dispatchCommand(server.getConsoleSender(), "co purge t:" + time + "s");
    }

    public List<String[]> performRestore(long time, List<String> restrictUsers, List<String> excludeUsers, List<Object> restrictBlocks, List<Object> excludeBlocks, List<Integer> actionList, int radius, Location radiusLocation) {
        if (Config.getGlobal().API_ENABLED) {
            return processData(time, radius, radiusLocation, APIUtil.parseList(restrictBlocks), APIUtil.parseList(excludeBlocks), restrictUsers, excludeUsers, actionList, 1, 2, -1, -1, false);
        }
        return null;
    }

    @Deprecated
    public List<String[]> performRestore(String user, long time, int radius, Location location, List<Object> restrict, List<Object> exclude) {
        if (Config.getGlobal().API_ENABLED) {
            return processData(user, time, radius, location, APIUtil.parseList(restrict), APIUtil.parseList(exclude), 1, 2, -1, -1, false);
        }
        return null;
    }

    public List<String[]> performRollback(long time, List<String> restrictUsers, List<String> excludeUsers, List<Object> restrictBlocks, List<Object> excludeBlocks, List<Integer> actionList, int radius, Location radiusLocation) {
        if (Config.getGlobal().API_ENABLED) {
            return processData(time, radius, radiusLocation, APIUtil.parseList(restrictBlocks), APIUtil.parseList(excludeBlocks), restrictUsers, excludeUsers, actionList, 0, 2, -1, -1, false);
        }
        return null;
    }

    @Deprecated
    public List<String[]> performRollback(String user, long time, int radius, Location location, List<Object> restrict, List<Object> exclude) {
        if (Config.getGlobal().API_ENABLED) {
            return processData(user, time, radius, location, APIUtil.parseList(restrict), APIUtil.parseList(exclude), 0, 2, -1, -1, false);
        }
        return null;
    }

    private List<String[]> processData(long time, int radius, Location location, List<Object> restrictBlocks, List<Object> excludeBlocks, List<String> restrictUsers, List<String> excludeUsers, List<Integer> actionList, int action, int lookup, int offset, int rowCount, boolean useLimit) {
        // You need to either specify time/radius or time/user
        List<String[]> result = new ArrayList<>();
        List<String> uuids = new ArrayList<>();

        if (restrictUsers == null) {
            restrictUsers = new ArrayList<>();
        }

        if (excludeUsers == null) {
            excludeUsers = new ArrayList<>();
        }

        if (actionList == null) {
            actionList = new ArrayList<>();
        }

        if (actionList.isEmpty() && !restrictBlocks.isEmpty()) {
            boolean addedMaterial = false;
            boolean addedEntity = false;

            for (Object argBlock : restrictBlocks) {
                if (argBlock instanceof Material && !addedMaterial) {
                    actionList.add(0);
                    actionList.add(1);
                    addedMaterial = true;
                }
                else if (argBlock instanceof EntityType && !addedEntity) {
                    actionList.add(3);
                    addedEntity = true;
                }
            }
        }

        if (actionList.isEmpty()) {
            actionList.add(0);
            actionList.add(1);
        }

        actionList.removeIf(actionListItem -> actionListItem > 3);

        if (restrictUsers.isEmpty()) {
            restrictUsers.add("#global");
        }

        long timestamp = System.currentTimeMillis() / 1000L;
        long timePeriod = timestamp - time;

        if (radius < 1) {
            radius = -1;
        }

        if (restrictUsers.contains("#global") && radius == -1) {
            return null;
        }

        if (radius > -1 && location == null) {
            return null;
        }

        try {
            Connection connection = Database.getConnection(false, 1000);
            if (connection != null) {
                Statement statement = connection.createStatement();
                boolean restrictWorld = radius > 0;

                if (location == null) {
                    restrictWorld = false;
                }

                Integer[] argRadius = null;
                if (location != null && radius > 0) {
                    int xMin = location.getBlockX() - radius;
                    int xMax = location.getBlockX() + radius;
                    int zMin = location.getBlockZ() - radius;
                    int zMax = location.getBlockZ() + radius;
                    argRadius = new Integer[] { radius, xMin, xMax, -1, -1, zMin, zMax, 0 };
                }

                if (lookup == 1) {
                    if (location != null) {
                        restrictWorld = true;
                    }

                    if (useLimit) {
                        result = Lookup.performPartialLookup(statement, null, uuids, restrictUsers, restrictBlocks, excludeBlocks, excludeUsers, actionList, location, argRadius, timePeriod, offset, rowCount, restrictWorld, true);
                    }
                    else {
                        result = Lookup.performLookup(statement, null, uuids, restrictUsers, restrictBlocks, excludeBlocks, excludeUsers, actionList, location, argRadius, timePeriod, restrictWorld, true);
                    }
                }
                else {
                    if (!Bukkit.isPrimaryThread()) {
                        boolean verbose = false;
                        result = Rollback.performRollbackRestore(statement, null, uuids, restrictUsers, null, restrictBlocks, excludeBlocks, excludeUsers, actionList, location, argRadius, timePeriod, restrictWorld, false, verbose, action, 0);
                    }
                }

                statement.close();
                connection.close();
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        return result;
    }

    @Deprecated
    private List<String[]> processData(String user, long time, int radius, Location location, List<Object> restrictBlocks, List<Object> excludeBlocks, int action, int lookup, int offset, int rowCount, boolean useLimit) {
        ArrayList<String> restrictUsers = new ArrayList<>();
        if (user != null) {
            restrictUsers.add(user);
        }

        return processData(time, radius, location, restrictBlocks, excludeBlocks, restrictUsers, null, null, action, lookup, offset, rowCount, useLimit);
    }

    public void testAPI() {
        Chat.console(Phrase.build(Phrase.API_TEST));
    }

}
