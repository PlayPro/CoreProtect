package net.coreprotect.database;

import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.command.CommandSender;

import net.coreprotect.consumer.Consumer;
import net.coreprotect.consumer.Queue;

public class Lookup extends Queue {

    public static long countLookupRows(Statement statement, CommandSender user, List<String> checkUuids, List<String> checkUsers, List<Object> restrictList, Map<Object, Boolean> excludeList, List<String> excludeUserList, List<Integer> actionList, Location location, Integer[] radius, Long[] rowData, long startTime, long endTime, boolean restrictWorld, boolean lookup) {
        Long rows = 0L;

        try {
            while (Consumer.isPaused) {
                Thread.sleep(1);
            }
            Consumer.isPaused = true;

            ResultSet results = LookupRaw.rawLookupResultSet(statement, user, checkUuids, checkUsers, restrictList, excludeList, excludeUserList, actionList, location, radius, null, startTime, endTime, -1, -1, restrictWorld, lookup, true);
            while (results.next()) {
                int resultTable = results.getInt("tbl");
                long count = results.getLong("count");
                rowData[resultTable] = count;
                rows += count;
            }
            results.close();
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        Consumer.isPaused = false;

        return rows;
    }

    public static List<String[]> performLookup(Statement statement, CommandSender user, List<String> checkUuids, List<String> checkUsers, List<Object> restrictList, Map<Object, Boolean> excludeList, List<String> excludeUserList, List<Integer> actionList, Location location, Integer[] radius, long startTime, long endTime, boolean restrictWorld, boolean lookup) {
        List<String[]> newList = new ArrayList<>();

        try {
            List<Object[]> lookupList = LookupRaw.performLookupRaw(statement, user, checkUuids, checkUsers, restrictList, excludeList, excludeUserList, actionList, location, radius, null, startTime, endTime, -1, -1, restrictWorld, lookup);
            newList = LookupConverter.convertRawLookup(statement, lookupList);
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        return newList;
    }

    public static List<String[]> performPartialLookup(Statement statement, CommandSender user, List<String> checkUuids, List<String> checkUsers, List<Object> restrictList, Map<Object, Boolean> excludeList, List<String> excludeUserList, List<Integer> actionList, Location location, Integer[] radius, Long[] rowData, long startTime, long endTime, int limitOffset, int limitCount, boolean restrictWorld, boolean lookup) {
        List<String[]> newList = new ArrayList<>();

        try {
            List<Object[]> lookupList = LookupRaw.performLookupRaw(statement, user, checkUuids, checkUsers, restrictList, excludeList, excludeUserList, actionList, location, radius, rowData, startTime, endTime, limitOffset, limitCount, restrictWorld, lookup);
            newList = LookupConverter.convertRawLookup(statement, lookupList);
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        return newList;
    }

    public static String whoPlaced(Statement statement, BlockState block) {
        return BlockLookup.whoPlaced(statement, block);
    }

    public static String whoPlacedCache(Block block) {
        return BlockLookup.whoPlacedCache(block);
    }

    public static String whoPlacedCache(BlockState block) {
        return BlockLookup.whoPlacedCache(block);
    }

    public static String whoRemovedCache(BlockState block) {
        return BlockLookup.whoRemovedCache(block);
    }

    // Maintain backward compatibility
    protected static List<Object[]> performLookupRaw(Statement statement, CommandSender user, List<String> checkUuids, List<String> checkUsers, List<Object> restrictList, Map<Object, Boolean> excludeList, List<String> excludeUserList, List<Integer> actionList, Location location, Integer[] radius, Long[] rowData, long startTime, long endTime, int limitOffset, int limitCount, boolean restrictWorld, boolean lookup) {
        return LookupRaw.performLookupRaw(statement, user, checkUuids, checkUsers, restrictList, excludeList, excludeUserList, actionList, location, radius, rowData, startTime, endTime, limitOffset, limitCount, restrictWorld, lookup);
    }

    // Maintain backward compatibility
    private static ResultSet rawLookupResultSet(Statement statement, CommandSender user, List<String> checkUuids, List<String> checkUsers, List<Object> restrictList, Map<Object, Boolean> excludeList, List<String> excludeUserList, List<Integer> actionList, Location location, Integer[] radius, Long[] rowData, long startTime, long endTime, int limitOffset, int limitCount, boolean restrictWorld, boolean lookup, boolean count) {
        return LookupRaw.rawLookupResultSet(statement, user, checkUuids, checkUsers, restrictList, excludeList, excludeUserList, actionList, location, radius, rowData, startTime, endTime, limitOffset, limitCount, restrictWorld, lookup, count);
    }
}
