package net.coreprotect.database;

import java.sql.Statement;
import java.util.Collections;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;

import net.coreprotect.CoreProtect;
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.consumer.Queue;
import net.coreprotect.language.Phrase;
import net.coreprotect.model.BlockGroup;
import net.coreprotect.utility.Chat;
import net.coreprotect.utility.Util;

public class ContainerRollback extends Queue {

    public static void performContainerRollbackRestore(Statement statement, CommandSender user, List<String> checkUuids, List<String> checkUsers, String timeString, List<Object> restrictList, List<Object> excludeList, List<String> excludeUserList, List<Integer> actionList, final Location location, Integer[] radius, long checkTime, boolean restrictWorld, boolean lookup, boolean verbose, final int rollbackType) {
        try {
            long startTime = System.currentTimeMillis();

            final List<Object[]> lookupList = Lookup.performLookupRaw(statement, user, checkUuids, checkUsers, restrictList, excludeList, excludeUserList, actionList, location, radius, null, checkTime, -1, -1, restrictWorld, lookup);
            if (rollbackType == 1) {
                Collections.reverse(lookupList);
            }

            String userString = "#server";
            if (user != null) {
                userString = user.getName();
            }

            Queue.queueContainerRollbackUpdate(userString, location, lookupList, rollbackType); // Perform update transaction in consumer

            final String finalUserString = userString;
            ConfigHandler.rollbackHash.put(userString, new int[] { 0, 0, 0, 0 });

            Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(CoreProtect.getInstance(), new Runnable() {
                @Override
                public void run() {
                    try {
                        int[] rollbackHashData = ConfigHandler.rollbackHash.get(finalUserString);
                        int itemCount = rollbackHashData[0];
                        // int blockCount = rollbackHashData[1];
                        int entityCount = rollbackHashData[2];
                        Block block = location.getBlock();

                        if (!block.getWorld().isChunkLoaded(block.getChunk())) {
                            block.getWorld().getChunkAt(block.getLocation());
                        }
                        Object container = null;
                        Material type = block.getType();

                        if (BlockGroup.CONTAINERS.contains(type)) {
                            container = Util.getContainerInventory(block.getState(), false);
                        }
                        else {
                            for (Entity entity : block.getChunk().getEntities()) {
                                if (entity instanceof ArmorStand) {
                                    if (entity.getLocation().getBlockX() == location.getBlockX() && entity.getLocation().getBlockY() == location.getBlockY() && entity.getLocation().getBlockZ() == location.getBlockZ()) {
                                        type = Material.ARMOR_STAND;
                                        container = Util.getEntityEquipment((LivingEntity) entity);
                                    }
                                }
                            }
                        }

                        int modifyCount = 0;
                        if (container != null) {
                            for (Object[] lookupRow : lookupList) {
                                // int unixtimestamp = (int) (System.currentTimeMillis() / 1000L);
                                // int rowId = lookupRow[0];
                                // int rowTime = (Integer)lookupRow[1];
                                // int rowUserId = (Integer)lookupRow[2];
                                // int rowX = (Integer)lookupRow[3];
                                // int rowY = (Integer)lookupRow[4];
                                // int rowZ = (Integer)lookupRow[5];
                                int rowTypeRaw = (Integer) lookupRow[6];
                                int rowData = (Integer) lookupRow[7];
                                int rowAction = (Integer) lookupRow[8];
                                int rowRolledBack = (Integer) lookupRow[9];
                                // int rowWid = (Integer)lookupRow[10];
                                int rowAmount = (Integer) lookupRow[11];
                                byte[] rowMetadata = (byte[]) lookupRow[12];
                                Material rowType = Util.getType(rowTypeRaw);

                                if ((rollbackType == 0 && rowRolledBack == 0) || (rollbackType == 1 && rowRolledBack == 1)) {
                                    modifyCount = modifyCount + rowAmount;
                                    int action = 0;

                                    if (rollbackType == 0 && rowAction == 0) {
                                        action = 1;
                                    }

                                    if (rollbackType == 1 && rowAction == 1) {
                                        action = 1;
                                    }

                                    ItemStack itemstack = new ItemStack(rowType, rowAmount, (short) rowData);
                                    Object[] populatedStack = Rollback.populateItemStack(itemstack, rowMetadata);
                                    int slot = (Integer) populatedStack[0];
                                    itemstack = (ItemStack) populatedStack[1];

                                    Rollback.modifyContainerItems(type, container, slot, itemstack, action);
                                }
                            }
                        }

                        ConfigHandler.rollbackHash.put(finalUserString, new int[] { itemCount, modifyCount, entityCount, 1 });
                    }
                    catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }, 0);

            int[] rollbackHashData = ConfigHandler.rollbackHash.get(finalUserString);
            int next = rollbackHashData[3];
            int sleepTime = 0;

            while (next == 0) {
                sleepTime = sleepTime + 5;
                Thread.sleep(5);
                rollbackHashData = ConfigHandler.rollbackHash.get(finalUserString);
                next = rollbackHashData[3];
                if (sleepTime > 300000) {
                    Chat.console(Phrase.build(Phrase.ROLLBACK_ABORTED));
                    break;
                }
            }

            rollbackHashData = ConfigHandler.rollbackHash.get(finalUserString);
            int blockCount = rollbackHashData[1];
            long endTime = System.currentTimeMillis();
            double totalSeconds = (endTime - startTime) / 1000.0;

            if (user != null) {
                int file = -1;
                if (blockCount > 0) {
                    file = 1;
                }
                int itemCount = 0;
                int entityCount = 0;

                Rollback.finishRollbackRestore(user, location, checkUsers, restrictList, excludeList, excludeUserList, actionList, timeString, file, totalSeconds, itemCount, blockCount, entityCount, rollbackType, radius, verbose, restrictWorld, 0);
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

}
