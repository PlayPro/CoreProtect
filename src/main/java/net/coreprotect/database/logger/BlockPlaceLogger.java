package net.coreprotect.database.logger;

import java.sql.PreparedStatement;
import java.util.List;
import java.util.Locale;

import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Bed;
import org.bukkit.block.data.type.Door;
import org.bukkit.block.data.type.Door.Hinge;

import net.coreprotect.CoreProtect;
import net.coreprotect.bukkit.BukkitAdapter;
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.database.statement.BlockStatement;
import net.coreprotect.database.statement.UserStatement;
import net.coreprotect.event.CoreProtectPreLogEvent;
import net.coreprotect.model.BlockGroup;
import net.coreprotect.thread.CacheHandler;
import net.coreprotect.utility.Util;

public class BlockPlaceLogger {

    private BlockPlaceLogger() {
        throw new IllegalStateException("Database class");
    }

    public static void log(PreparedStatement preparedStmt, int batchCount, String user, BlockState block, int replacedType, int replacedData, Material forceType, int forceData, boolean force, List<Object> meta, String blockData, String replaceBlockData) {
        try {
            if (user == null || ConfigHandler.blacklist.get(user.toLowerCase(Locale.ROOT)) != null) {
                return;
            }
            Material type = block.getType();
            if (blockData == null && (forceType == null || (!forceType.equals(Material.WATER)) && (!forceType.equals(Material.LAVA)))) {
                blockData = block.getBlockData().getAsString();
                if (blockData.equals("minecraft:air")) {
                    blockData = null;
                }
            }
            int data = 0;
            if (forceType != null && force) {
                type = forceType;
                if (BukkitAdapter.ADAPTER.isItemFrame(type) || type.equals(Material.SPAWNER) || type.equals(Material.PAINTING) || type.equals(Material.SKELETON_SKULL) || type.equals(Material.SKELETON_WALL_SKULL) || type.equals(Material.WITHER_SKELETON_SKULL) || type.equals(Material.WITHER_SKELETON_WALL_SKULL) || type.equals(Material.ZOMBIE_HEAD) || type.equals(Material.ZOMBIE_WALL_HEAD) || type.equals(Material.PLAYER_HEAD) || type.equals(Material.PLAYER_WALL_HEAD) || type.equals(Material.CREEPER_HEAD) || type.equals(Material.CREEPER_WALL_HEAD) || type.equals(Material.DRAGON_HEAD) || type.equals(Material.DRAGON_WALL_HEAD) || type.equals(Material.ARMOR_STAND) || type.equals(Material.END_CRYSTAL)) {
                    data = forceData; // mob spawner, skull
                }
                else if (user.startsWith("#")) {
                    data = forceData;
                }
            }
            else if (forceType != null && !type.equals(forceType)) {
                type = forceType;
                data = forceData;
            }

            if (type.equals(Material.AIR) || type.equals(Material.CAVE_AIR)) {
                return;
            }

            CoreProtectPreLogEvent event = new CoreProtectPreLogEvent(user);
            CoreProtect.getInstance().getServer().getPluginManager().callEvent(event);
            if (!event.getUser().equals(user)) {
                user = event.getUser();
            }

            int userId = UserStatement.getId(preparedStmt, user, true);
            int wid = Util.getWorldId(block.getWorld().getName());
            int time = (int) (System.currentTimeMillis() / 1000L);
            int x = block.getX();
            int y = block.getY();
            int z = block.getZ();
            int dx = x;
            int dy = y;
            int dz = z;
            Material doubletype = type;
            int doubledata = data;
            int logdouble = 0;

            if (user.length() > 0) {
                CacheHandler.lookupCache.put("" + x + "." + y + "." + z + "." + wid + "", new Object[] { time, user, type });
            }

            String doubleBlockData = null;
            if (type.name().endsWith("_BED") || type == Material.IRON_DOOR || BlockGroup.DOORS.contains(type)) { // properly log double blocks (doors/beds)
                BlockData blockStateBlockData = block.getBlockData();
                if (blockStateBlockData instanceof Bed) {
                    Bed bed = (Bed) blockStateBlockData;
                    Bed.Part bedPart = bed.getPart();
                    BlockFace face = bed.getFacing();

                    int bedData = 1;
                    switch (face) {
                        case WEST:
                            dx = ((bedPart == Bed.Part.HEAD) ? (x + 1) : (x - 1));
                            bedData = 2;
                            break;
                        case EAST:
                            dx = ((bedPart == Bed.Part.HEAD) ? (x - 1) : (x + 1));
                            bedData = 3;
                            break;
                        case SOUTH:
                            dz = ((bedPart == Bed.Part.HEAD) ? (z - 1) : (z + 1));
                            bedData = 4;
                            break;
                        default:
                            dz = ((bedPart == Bed.Part.HEAD) ? (z + 1) : (z - 1));
                            break;
                    }

                    if (bedPart == Bed.Part.HEAD) {
                        data = 4 + bedData;
                        doubledata = bedData;
                        bed.setPart(Bed.Part.FOOT);
                        doubleBlockData = bed.getAsString();
                    }
                    else {
                        data = bedData;
                        doubledata = 4 + bedData;
                        bed.setPart(Bed.Part.HEAD);
                        doubleBlockData = bed.getAsString();
                    }
                }
                else if (blockStateBlockData instanceof Door) {
                    Door door = (Door) blockStateBlockData;
                    BlockFace face = door.getFacing();
                    Hinge hinge = door.getHinge();
                    switch (face) {
                        case EAST:
                            data = 0;
                            break;
                        case SOUTH:
                            data = 1;
                            break;
                        case WEST:
                            data = 2;
                            break;
                        default:
                            data = 3;
                            break;
                    }
                    if (hinge.equals(Hinge.RIGHT)) {
                        data = data + 4;
                    }
                    if (data < 8) {
                        dy = y + 1;
                        doubledata = data + 8;
                    }
                }
                logdouble = 1;
            }

            int internalType = Util.getBlockId(type.name(), true);
            int internalDoubleType = Util.getBlockId(doubletype.name(), true);

            if (replacedType > 0 && Util.getType(replacedType) != Material.AIR && Util.getType(replacedType) != Material.CAVE_AIR) {
                BlockStatement.insert(preparedStmt, batchCount, time, userId, wid, x, y, z, replacedType, replacedData, null, replaceBlockData, 0, 0);
            }

            BlockStatement.insert(preparedStmt, batchCount, time, userId, wid, x, y, z, internalType, data, meta, blockData, 1, 0);
            if (logdouble == 1) {
                BlockStatement.insert(preparedStmt, batchCount, time, userId, wid, dx, dy, dz, internalDoubleType, doubledata, null, doubleBlockData, 1, 0);
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

}
