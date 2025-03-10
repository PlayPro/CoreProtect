package net.coreprotect.worldedit;

import java.util.Locale;

import org.bukkit.DyeColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.Bisected.Half;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import com.sk89q.jnbt.CompoundTag;
import com.sk89q.jnbt.NBTUtils;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.bukkit.WorldEditPlugin;
import com.sk89q.worldedit.extension.platform.Actor;
import com.sk89q.worldedit.extent.Extent;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.world.block.BaseBlock;
import com.sk89q.worldedit.world.block.BlockStateHolder;

import net.coreprotect.config.Config;
import net.coreprotect.consumer.Queue;
import net.coreprotect.utility.BlockUtils;
import net.coreprotect.utility.EntityUtils;

public class WorldEditLogger extends Queue {

    public static WorldEditPlugin getWorldEdit(Server server) {
        Plugin plugin = server.getPluginManager().getPlugin("WorldEdit");
        if (plugin == null || !(plugin instanceof WorldEditPlugin)) {
            return null;
        }

        return (WorldEditPlugin) plugin;
    }

    protected static BaseBlock getBaseBlock(Extent extent, BlockVector3 position, Location location, Material oldType, com.sk89q.worldedit.world.block.BlockState oldBlock) {
        if (oldType == Material.SPAWNER || (Config.getConfig(location.getWorld()).SIGN_TEXT && net.coreprotect.bukkit.BukkitAdapter.ADAPTER.isSign(oldType))) {
            return extent.getFullBlock(position);
        }

        return null;
    }

    protected static void postProcess(Extent extent, Actor actor, BlockVector3 position, Location location, BlockStateHolder<?> blockStateHolder, BaseBlock baseBlock, Material oldType, com.sk89q.worldedit.world.block.BlockState oldBlockState, ItemStack[] containerContents) {
        BlockData oldBlockData = BukkitAdapter.adapt(oldBlockState);
        BlockData newBlockData = BukkitAdapter.adapt(blockStateHolder.toImmutableState());
        Material newType = newBlockData.getMaterial();

        String oldBlockDataString = oldBlockData.getAsString();
        String newBlockDataString = newBlockData.getAsString();
        BlockState oldBlock = new WorldEditBlockState(location, oldType, oldBlockData);
        BlockState newBlock = new WorldEditBlockState(location, newType, newBlockData);

        int oldBlockExtraData = 0;
        int newBlockExtraData = -1;

        if (!oldType.equals(newType) || !oldBlockDataString.equals(newBlockDataString)) {
            try {
                if (baseBlock != null && baseBlock.hasNbtData()) {
                    if (Config.getConfig(location.getWorld()).SIGN_TEXT && net.coreprotect.bukkit.BukkitAdapter.ADAPTER.isSign(oldType)) {
                        CompoundTag compoundTag = baseBlock.getNbtData();
                        if (!compoundTag.containsKey("front_text")) {
                            String line1 = getSignText(compoundTag.getString("Text1"));
                            String line2 = getSignText(compoundTag.getString("Text2"));
                            String line3 = getSignText(compoundTag.getString("Text3"));
                            String line4 = getSignText(compoundTag.getString("Text4"));
                            int color = DyeColor.valueOf(baseBlock.getNbtData().getString("Color").toUpperCase()).getColor().asRGB();
                            int colorSecondary = 0;
                            boolean frontGlowing = (compoundTag.getInt("GlowingText") == 1 ? true : false);
                            boolean backGlowing = false;
                            boolean isWaxed = false;
                            boolean isFront = true;

                            Queue.queueSignText(actor.getName(), location, 0, color, colorSecondary, frontGlowing, backGlowing, isWaxed, isFront, line1, line2, line3, line4, "", "", "", "", 5);
                        }
                    }
                    if (oldType == Material.SPAWNER) {
                        String mobType = getMobType(baseBlock);
                        if (mobType != null) {
                            try {
                                EntityType entityType = EntityType.valueOf(mobType);
                                oldBlockExtraData = EntityUtils.getSpawnerType(entityType);
                            }
                            catch (IllegalArgumentException exception) {
                                // mobType isn't a valid enum (EntityType.class)
                            }
                        }
                    }
                }
                if (containerContents != null) {
                    Queue.queueContainerBreak(actor.getName(), location, oldType, containerContents);
                }
            }
            catch (Exception e) {
                e.printStackTrace();
            }

            if (newType.equals(Material.SKELETON_SKULL) || newType.equals(Material.SKELETON_WALL_SKULL) || newType.equals(Material.WITHER_SKELETON_SKULL) || newType.equals(Material.WITHER_SKELETON_WALL_SKULL) || newType.equals(Material.ZOMBIE_HEAD) || newType.equals(Material.ZOMBIE_WALL_HEAD) || newType.equals(Material.PLAYER_HEAD) || newType.equals(Material.PLAYER_WALL_HEAD) || newType.equals(Material.CREEPER_HEAD) || newType.equals(Material.CREEPER_WALL_HEAD) || newType.equals(Material.DRAGON_HEAD) || newType.equals(Material.DRAGON_WALL_HEAD)) {
                // skull
                Queue.queueBlockPlaceDelayed(actor.getName(), location, newType, newBlockDataString, oldBlock, 0);
            }
            else if ((oldType.equals(Material.AIR) || oldType.equals(Material.CAVE_AIR)) && (!newType.equals(Material.AIR) && !newType.equals(Material.CAVE_AIR))) {
                // placed a block
                Queue.queueBlockPlace(actor.getName(), newBlock, newType, null, newType, newBlockExtraData, 0, newBlockDataString);
            }
            else if ((!oldType.equals(Material.AIR) && !oldType.equals(Material.CAVE_AIR)) && (!newType.equals(Material.AIR) && !newType.equals(Material.CAVE_AIR))) {
                // replaced a block
                Waterlogged waterlogged = BlockUtils.checkWaterlogged(newBlockData, oldBlock);
                if (waterlogged != null) {
                    newBlockDataString = waterlogged.getAsString();
                    oldBlock = null;
                }
                if (oldBlock != null) {
                    Queue.queueBlockBreak(actor.getName(), oldBlock, oldBlock.getType(), oldBlockDataString, null, oldBlockExtraData, 0);
                }
                Queue.queueBlockPlace(actor.getName(), newBlock, newType, null, newType, newBlockExtraData, 0, newBlockDataString);
            }
            else if ((!oldType.equals(Material.AIR) && !oldType.equals(Material.CAVE_AIR)) && (newType.equals(Material.AIR) || newType.equals(Material.CAVE_AIR))) {
                // removed a block
                Queue.queueBlockBreak(actor.getName(), oldBlock, oldBlock.getType(), oldBlockDataString, null, oldBlockExtraData, 0);

                if (oldBlockData instanceof Waterlogged) {
                    Waterlogged waterlogged = (Waterlogged) oldBlockData;
                    if (waterlogged.isWaterlogged()) {
                        Queue.queueBlockPlace(actor.getName(), newBlock, newType, null, Material.WATER, -1, 0, null);
                    }
                }
                else if (oldBlockData instanceof Bisected) {
                    Bisected bisected = (Bisected) oldBlockData;
                    Location bisectLocation = location.clone();
                    if (bisected.getHalf() == Half.TOP) {
                        bisectLocation.setY(bisectLocation.getY() - 1);
                    }
                    else {
                        bisectLocation.setY(bisectLocation.getY() + 1);
                    }

                    int worldMaxHeight = location.getWorld().getMaxHeight();
                    int worldMinHeight = net.coreprotect.bukkit.BukkitAdapter.ADAPTER.getMinHeight(location.getWorld());
                    if (bisectLocation.getBlockY() >= worldMinHeight && bisectLocation.getBlockY() < worldMaxHeight) {
                        BlockState bisectBlock = location.getWorld().getBlockAt(bisectLocation).getState();
                        Queue.queueBlockBreak(actor.getName(), bisectBlock, bisectBlock.getType(), bisectBlock.getBlockData().getAsString(), null, 0, 0);
                    }
                }

            }
        }
    }

    private static String getMobType(BaseBlock fullBlock) {
        String mobType = null;
        try {
            CompoundTag compoundTag = NBTUtils.getChildTag(fullBlock.getNbtData().getValue(), "SpawnData", CompoundTag.class);
            mobType = compoundTag.getString("id").toUpperCase(Locale.ROOT);
            if (mobType.contains("MINECRAFT:")) {
                String[] mobTypeSplit = mobType.split(":");
                mobType = mobTypeSplit[1];
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        return mobType;
    }

    private static String getSignText(String line) {
        String result = "";

        if (!line.startsWith("{\"text\":\"")) {
            return line;
        }

        try {
            JSONObject json = (JSONObject) new JSONParser().parse(line);
            return (String) json.get("text");
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        return result;
    }
}
