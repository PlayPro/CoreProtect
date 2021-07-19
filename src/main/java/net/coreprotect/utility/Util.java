package net.coreprotect.utility;

import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import org.bukkit.Art;
import org.bukkit.Bukkit;
import org.bukkit.DyeColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.attribute.Attributable;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Banner;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.CommandBlock;
import org.bukkit.block.ShulkerBox;
import org.bukkit.block.banner.Pattern;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.serialization.ConfigurationSerialization;
import org.bukkit.configuration.serialization.DelegateDeserialization;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.AbstractVillager;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.Cat;
import org.bukkit.entity.ChestedHorse;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Enderman;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Fox;
import org.bukkit.entity.Horse;
import org.bukkit.entity.Horse.Style;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Llama;
import org.bukkit.entity.MushroomCow;
import org.bukkit.entity.Painting;
import org.bukkit.entity.Panda;
import org.bukkit.entity.Panda.Gene;
import org.bukkit.entity.Parrot;
import org.bukkit.entity.Parrot.Variant;
import org.bukkit.entity.Phantom;
import org.bukkit.entity.Pig;
import org.bukkit.entity.Player;
import org.bukkit.entity.Raider;
import org.bukkit.entity.Sheep;
import org.bukkit.entity.Slime;
import org.bukkit.entity.Spellcaster;
import org.bukkit.entity.Spellcaster.Spell;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.TropicalFish;
import org.bukkit.entity.Villager;
import org.bukkit.entity.Villager.Profession;
import org.bukkit.entity.Wolf;
import org.bukkit.entity.Zombie;
import org.bukkit.entity.ZombieVillager;
import org.bukkit.inventory.BlockInventoryHolder;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.io.BukkitObjectOutputStream;

import net.coreprotect.CoreProtect;
import net.coreprotect.bukkit.BukkitAdapter;
import net.coreprotect.config.Config;
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.consumer.Queue;
import net.coreprotect.database.Rollback;
import net.coreprotect.language.Phrase;
import net.coreprotect.model.BlockGroup;
import net.coreprotect.thread.CacheHandler;
import net.coreprotect.utility.serialize.ItemMetaHandler;
import net.coreprotect.worldedit.CoreProtectEditSessionEvent;

public class Util extends Queue {

    public static final java.util.regex.Pattern tagParser = java.util.regex.Pattern.compile(Chat.COMPONENT_TAG_OPEN + "(.+?)" + Chat.COMPONENT_TAG_CLOSE + "|(.+?)", java.util.regex.Pattern.DOTALL);
    private static final String NAMESPACE = "minecraft:";

    public static String getPluginVersion() {
        String version = CoreProtect.getInstance().getDescription().getVersion();
        if (version.contains("-")) {
            version = version.split("-")[0];
        }

        return version;
    }

    public static Integer[] getInternalPluginVersion() {
        int major = ConfigHandler.EDITION_VERSION;
        int minor = 0;
        int revision = 0;

        String pluginVersion = getPluginVersion();
        if (pluginVersion.contains(".")) {
            String[] versionSplit = pluginVersion.split("\\.");
            minor = Integer.parseInt(versionSplit[0]);
            revision = Integer.parseInt(versionSplit[1]);
        }
        else {
            minor = Integer.parseInt(pluginVersion);
        }

        return new Integer[] { major, minor, revision };
    }

    public static String getPluginName() {
        String name = CoreProtect.getInstance().getDescription().getName();
        String branch = ConfigHandler.EDITION_BRANCH;

        if (branch.startsWith("-edge")) {
            name = name + " " + branch.substring(1, 2).toUpperCase() + branch.substring(2, 5);
        }

        return name;
    }

    public static int getBlockId(Material material) {
        if (material == null) {
            material = Material.AIR;
        }
        return getBlockId(material.name(), true);
    }

    public static String getCoordinates(String command, int worldId, int x, int y, int z, boolean displayWorld, boolean italic) {
        StringBuilder message = new StringBuilder(Chat.COMPONENT_TAG_OPEN + Chat.COMPONENT_COMMAND);

        StringBuilder worldDisplay = new StringBuilder();
        if (displayWorld) {
            worldDisplay.append("/" + Util.getWorldName(worldId));
        }

        // command
        message.append("|/" + command + " teleport wid:" + worldId + " " + (x + 0.50) + " " + y + " " + (z + 0.50) + "|");

        // chat output
        message.append(Color.GREY + (italic ? Color.ITALIC : "") + "(x" + x + "/y" + y + "/z" + z + worldDisplay.toString() + ")");

        return message.append(Chat.COMPONENT_TAG_CLOSE).toString();
    }

    public static String getPageNavigation(String command, int page, int totalPages) {
        StringBuilder message = new StringBuilder();

        // back arrow
        String backArrow = "";
        if (page > 1) {
            backArrow = "◀ ";
            backArrow = Chat.COMPONENT_TAG_OPEN + Chat.COMPONENT_COMMAND + "|/" + command + " l " + (page - 1) + "|" + backArrow + Chat.COMPONENT_TAG_CLOSE;
        }

        // next arrow
        String nextArrow = " ";
        if (page < totalPages) {
            nextArrow = " ▶ ";
            nextArrow = Chat.COMPONENT_TAG_OPEN + Chat.COMPONENT_COMMAND + "|/" + command + " l " + (page + 1) + "|" + nextArrow + Chat.COMPONENT_TAG_CLOSE;
        }

        return message.append(Color.WHITE + backArrow + Phrase.build(Phrase.LOOKUP_PAGE, page + "/" + totalPages) + nextArrow).toString();
    }

    public static String getTimeSince(int logTime, int currentTime, boolean component) {
        StringBuilder message = new StringBuilder();
        double timeSince = currentTime - (logTime + 0.00);

        // minutes
        timeSince = timeSince / 60;
        if (timeSince < 60.0) {
            message.append(Phrase.build(Phrase.LOOKUP_TIME, new DecimalFormat("0.00").format(timeSince) + "/m"));
        }

        // hours
        if (message.length() == 0) {
            timeSince = timeSince / 60;
            if (timeSince < 24.0) {
                message.append(Phrase.build(Phrase.LOOKUP_TIME, new DecimalFormat("0.00").format(timeSince) + "/h"));
            }
        }

        // days
        if (message.length() == 0) {
            timeSince = timeSince / 24;
            message.append(Phrase.build(Phrase.LOOKUP_TIME, new DecimalFormat("0.00").format(timeSince) + "/d"));
        }

        if (component) {
            Date logDate = new Date(logTime * 1000L);
            String formattedTimestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss z").format(logDate);

            return Chat.COMPONENT_TAG_OPEN + Chat.COMPONENT_POPUP + "|" + Color.GREY + formattedTimestamp + "|" + Color.GREY + message.toString() + Chat.COMPONENT_TAG_CLOSE;
        }

        return message.toString();
    }

    public static String capitalize(String string, boolean allWords) {
        if (string == null || string.isEmpty()) {
            return string;
        }

        if (string.length() <= 1) {
            return string.toUpperCase(Locale.ROOT);
        }

        string = string.toLowerCase(Locale.ROOT);

        if (allWords) {
            StringBuilder builder = new StringBuilder();
            for (String substring : string.split(" ")) {
                if (substring.length() >= 3 && !substring.equals("and") && !substring.equals("the")) {
                    substring = substring.substring(0, 1).toUpperCase(Locale.ROOT) + substring.substring(1);
                }
                if (builder.length() > 0) {
                    builder.append(" ");
                }
                builder.append(substring);
            }

            return builder.toString();
        }

        return string.substring(0, 1).toUpperCase(Locale.ROOT) + string.substring(1);
    }

    public static int getBlockId(String name, boolean internal) {
        int id = -1;

        name = name.toLowerCase(Locale.ROOT).trim();
        if (!name.contains(":")) {
            name = NAMESPACE + name;
        }

        if (ConfigHandler.materials.get(name) != null) {
            id = ConfigHandler.materials.get(name);
        }
        else if (internal) {
            int mid = ConfigHandler.materialId + 1;
            ConfigHandler.materials.put(name, mid);
            ConfigHandler.materialsReversed.put(mid, name);
            ConfigHandler.materialId = mid;
            Queue.queueMaterialInsert(mid, name);
            id = ConfigHandler.materials.get(name);
        }

        return id;
    }

    public static int getBlockdataId(String data, boolean internal) {
        int id = -1;
        data = data.toLowerCase(Locale.ROOT).trim();

        if (ConfigHandler.blockdata.get(data) != null) {
            id = ConfigHandler.blockdata.get(data);
        }
        else if (internal) {
            int bid = ConfigHandler.blockdataId + 1;
            ConfigHandler.blockdata.put(data, bid);
            ConfigHandler.blockdataReversed.put(bid, data);
            ConfigHandler.blockdataId = bid;
            Queue.queueBlockDataInsert(bid, data);
            id = ConfigHandler.blockdata.get(data);
        }

        return id;
    }

    public static String getBlockDataString(int id) {
        // Internal ID pulled from DB
        String blockdata = "";
        if (ConfigHandler.blockdataReversed.get(id) != null) {
            blockdata = ConfigHandler.blockdataReversed.get(id);
        }
        return blockdata;
    }

    public static String getBlockName(int id) {
        String name = "";
        if (ConfigHandler.materialsReversed.get(id) != null) {
            name = ConfigHandler.materialsReversed.get(id);
        }
        return name;
    }

    public static String getBlockNameShort(int id) {
        String name = getBlockName(id);
        if (name.contains(":")) {
            name = name.split(":")[1];
        }

        return name;
    }

    public static void mergeItems(Material material, ItemStack[] items) {
        if (material != null && material.equals(Material.ARMOR_STAND)) {
            return;
        }
        try {
            int c1 = 0;
            for (ItemStack o1 : items) {
                int c2 = 0;
                for (ItemStack o2 : items) {
                    if (o1 != null && o2 != null && c2 > c1 && o1.isSimilar(o2) && !Util.isAir(o1.getType())) { // Ignores amount
                        int namount = o1.getAmount() + o2.getAmount();
                        o1.setAmount(namount);
                        o2.setAmount(0);
                    }
                    c2++;
                }
                c1++;
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static Integer[] convertArray(String[] array) {
        List<Integer> list = new ArrayList<>();
        for (String item : array) {
            list.add(Integer.parseInt(item));
        }
        return list.toArray(new Integer[list.size()]);
    }

    public static byte[] stringToByteData(String string, int type) {
        byte[] result = null;
        if (string != null) {
            Material material = Util.getType(type);
            if (material == null) {
                return result;
            }

            if (material.isBlock() && !createBlockData(material).getAsString().equals(string) && string.startsWith(NAMESPACE + material.name().toLowerCase(Locale.ROOT) + "[") && string.endsWith("]")) {
                String substring = string.substring(material.name().length() + 11, string.length() - 1);
                String[] blockDataSplit = substring.split(",");
                ArrayList<String> blockDataArray = new ArrayList<>();
                for (String data : blockDataSplit) {
                    int id = getBlockdataId(data, true);
                    if (id > -1) {
                        blockDataArray.add(Integer.toString(id));
                    }
                }
                string = String.join(",", blockDataArray);
            }
            else {
                return result;
            }

            result = string.getBytes(StandardCharsets.UTF_8);
        }

        return result;
    }

    public static String byteDataToString(byte[] data, int type) {
        String result = "";
        if (data != null) {
            Material material = Util.getType(type);
            if (material == null) {
                return result;
            }

            result = new String(data, StandardCharsets.UTF_8);
            if (result.length() > 0) {
                if (result.matches("\\d+")) {
                    result = result + ",";
                }
                if (result.contains(",")) {
                    String[] blockDataSplit = result.split(",");
                    ArrayList<String> blockDataArray = new ArrayList<>();
                    for (String blockData : blockDataSplit) {
                        String block = getBlockDataString(Integer.parseInt(blockData));
                        if (block.length() > 0) {
                            blockDataArray.add(block);
                        }
                    }
                    result = NAMESPACE + material.name().toLowerCase(Locale.ROOT) + "[" + String.join(",", blockDataArray) + "]";
                }
                else {
                    result = "";
                }
            }
        }

        return result;
    }

    public static byte[] convertByteData(Object data) {
        byte[] result = null;
        if (data == null) {
            return result;
        }

        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            BukkitObjectOutputStream oos = new BukkitObjectOutputStream(bos);
            oos.writeObject(data);
            oos.flush();
            oos.close();
            bos.close();
            result = bos.toByteArray();
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        return result;
    }

    public static ItemMeta deserializeItemMeta(Class<? extends ItemMeta> itemMetaClass, Map<String, Object> args) {
        DelegateDeserialization delegate = itemMetaClass.getAnnotation(DelegateDeserialization.class);
        return (ItemMeta) ConfigurationSerialization.deserializeObject(args, delegate.value());
    }

    public static <K, V extends Comparable<? super V>> SortedSet<Map.Entry<K, V>> entriesSortedByValues(Map<K, V> map) {
        SortedSet<Map.Entry<K, V>> sortedEntries = new TreeSet<>((e1, e2) -> {
            int res = e1.getValue().compareTo(e2.getValue());
            return res != 0 ? res : 1;
        });
        sortedEntries.addAll(map.entrySet());
        return sortedEntries;
    }

    public static Waterlogged checkWaterlogged(BlockData blockData, BlockState blockReplacedState) {
        if (blockReplacedState.getType().equals(Material.WATER) && blockData instanceof Waterlogged) {
            if (blockReplacedState.getBlockData().equals(Material.WATER.createBlockData())) {
                Waterlogged waterlogged = (Waterlogged) blockData;
                waterlogged.setWaterlogged(true);
                return waterlogged;
            }
        }
        return null;
    }

    public static ItemStack[] getContainerState(ItemStack[] array) {
        ItemStack[] result = array.clone();

        int count = 0;
        for (ItemStack itemStack : array) {
            ItemStack clonedItem = null;
            if (itemStack != null) {
                clonedItem = itemStack.clone();
            }
            result[count] = clonedItem;
            count++;
        }

        return result;
    }

    /* return true if ItemStack[] contents are identical */
    public static boolean compareContainers(ItemStack[] oldContainer, ItemStack[] newContainer) {
        if (oldContainer.length != newContainer.length) {
            return false;
        }

        for (int i = 0; i < oldContainer.length; i++) {
            ItemStack oldItem = oldContainer[i];
            ItemStack newItem = newContainer[i];

            if (oldItem == null && newItem == null) {
                continue;
            }

            if (oldItem == null || !oldItem.equals(newItem)) {
                return false;
            }
        }

        return true;
    }

    /* return true if newContainer contains new items */
    public static boolean addedContainer(ItemStack[] oldContainer, ItemStack[] newContainer) {
        if (oldContainer.length != newContainer.length) {
            return false;
        }

        for (int i = 0; i < oldContainer.length; i++) {
            ItemStack oldItem = oldContainer[i];
            ItemStack newItem = newContainer[i];

            if (oldItem == null && newItem == null) {
                continue;
            }

            if (oldItem != null && newItem == null) {
                return false;
            }

            if (oldItem == null) {
                return true;
            }

            if (!newItem.equals(oldItem)) {
                return (newItem.isSimilar(oldItem) && newItem.getAmount() > oldItem.getAmount());
            }
        }

        return false;
    }

    public static int getArtId(Art art) {
        return art.getId();
    }

    public static int getArtId(String name, boolean internal) {
        int id = -1;
        name = name.toLowerCase(Locale.ROOT).trim();

        if (ConfigHandler.art.get(name) != null) {
            id = ConfigHandler.art.get(name);
        }
        else if (internal) {
            int artID = ConfigHandler.artId + 1;
            ConfigHandler.art.put(name, artID);
            ConfigHandler.artReversed.put(artID, name);
            ConfigHandler.artId = artID;
            Queue.queueArtInsert(artID, name);
            id = ConfigHandler.art.get(name);
        }

        return id;
    }

    public static String getArtName(int id) {
        // Internal ID pulled from DB
        String artname = "";
        if (ConfigHandler.artReversed.get(id) != null) {
            artname = ConfigHandler.artReversed.get(id);
        }
        return artname;
    }

    public static int getBlockFace(BlockFace rotation) {
        switch (rotation) {
            case NORTH:
                return 0;
            case NORTH_NORTH_EAST:
                return 1;
            case NORTH_EAST:
                return 2;
            case EAST_NORTH_EAST:
                return 3;
            case EAST:
                return 4;
            case EAST_SOUTH_EAST:
                return 5;
            case SOUTH_EAST:
                return 6;
            case SOUTH_SOUTH_EAST:
                return 7;
            case SOUTH:
                return 8;
            case SOUTH_SOUTH_WEST:
                return 9;
            case SOUTH_WEST:
                return 10;
            case WEST_SOUTH_WEST:
                return 11;
            case WEST:
                return 12;
            case WEST_NORTH_WEST:
                return 13;
            case NORTH_WEST:
                return 14;
            case NORTH_NORTH_WEST:
                return 15;
            default:
                throw new IllegalArgumentException("Invalid BlockFace rotation: " + rotation);
        }
    }

    public static BlockFace getBlockFace(int rotation) {
        switch (rotation) {
            case 0:
                return BlockFace.NORTH;
            case 1:
                return BlockFace.NORTH_NORTH_EAST;
            case 2:
                return BlockFace.NORTH_EAST;
            case 3:
                return BlockFace.EAST_NORTH_EAST;
            case 4:
                return BlockFace.EAST;
            case 5:
                return BlockFace.EAST_SOUTH_EAST;
            case 6:
                return BlockFace.SOUTH_EAST;
            case 7:
                return BlockFace.SOUTH_SOUTH_EAST;
            case 8:
                return BlockFace.SOUTH;
            case 9:
                return BlockFace.SOUTH_SOUTH_WEST;
            case 10:
                return BlockFace.SOUTH_WEST;
            case 11:
                return BlockFace.WEST_SOUTH_WEST;
            case 12:
                return BlockFace.WEST;
            case 13:
                return BlockFace.WEST_NORTH_WEST;
            case 14:
                return BlockFace.NORTH_WEST;
            case 15:
                return BlockFace.NORTH_NORTH_WEST;
            default:
                throw new AssertionError(rotation);
        }
    }

    public static ItemStack[] getArmorStandContents(EntityEquipment equipment) {
        ItemStack[] contents = new ItemStack[6];
        if (equipment != null) {
            // 0: BOOTS, 1: LEGGINGS, 2: CHESTPLATE, 3: HELMET
            ItemStack[] armorContent = equipment.getArmorContents();
            System.arraycopy(armorContent, 0, contents, 0, 4);
            contents[4] = equipment.getItemInMainHand();
            contents[5] = equipment.getItemInOffHand();
        }
        else {
            Arrays.fill(contents, new ItemStack(Material.AIR));
        }

        return contents;
    }

    public static ItemStack[] getContainerContents(Material type, Object container, Location location) {
        ItemStack[] contents = null;
        if (Config.getConfig(location.getWorld()).ITEM_TRANSACTIONS && BlockGroup.CONTAINERS.contains(type)) {
            try {
                // container may be null if called from within WorldEdit logger
                if (container == null) {
                    container = location.getBlock();
                }

                if (type.equals(Material.ARMOR_STAND)) {
                    LivingEntity entity = (LivingEntity) container;
                    EntityEquipment equipment = Util.getEntityEquipment(entity);
                    if (equipment != null) {
                        contents = getArmorStandContents(equipment);
                    }
                }
                else {
                    Block block = (Block) container;
                    Inventory inventory = Util.getContainerInventory(block.getState(), true);
                    if (inventory != null) {
                        contents = inventory.getContents();
                    }
                }
                if (type.equals(Material.ARMOR_STAND)) {
                    boolean hasItem = false;
                    for (ItemStack item : contents) {
                        if (item != null && !item.getType().equals(Material.AIR)) {
                            hasItem = true;
                            break;
                        }
                    }
                    if (!hasItem) {
                        contents = null;
                    }
                }

                if (contents != null) {
                    contents = Util.getContainerState(contents);
                }
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }

        return contents;
    }

    public static Inventory getContainerInventory(BlockState blockState, boolean singleBlock) {
        Inventory inventory = null;
        try {
            if (blockState instanceof BlockInventoryHolder) {
                if (singleBlock) {
                    List<Material> chests = Arrays.asList(Material.CHEST, Material.TRAPPED_CHEST);
                    Material type = blockState.getType();
                    if (chests.contains(type)) {
                        inventory = ((Chest) blockState).getBlockInventory();
                    }
                }
                if (inventory == null) {
                    inventory = ((BlockInventoryHolder) blockState).getInventory();
                }
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        return inventory;
    }

    public static EntityEquipment getEntityEquipment(LivingEntity entity) {
        EntityEquipment equipment = null;
        try {
            equipment = entity.getEquipment();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        return equipment;
    }

    public static int getEntityId(EntityType type) {
        return getEntityId(type.name(), true);
    }

    public static int getEntityId(String name, boolean internal) {
        int id = -1;
        name = name.toLowerCase(Locale.ROOT).trim();

        if (ConfigHandler.entities.get(name) != null) {
            id = ConfigHandler.entities.get(name);
        }
        else if (internal) {
            int entityID = ConfigHandler.entityId + 1;
            ConfigHandler.entities.put(name, entityID);
            ConfigHandler.entitiesReversed.put(entityID, name);
            ConfigHandler.entityId = entityID;
            Queue.queueEntityInsert(entityID, name);
            id = ConfigHandler.entities.get(name);
        }

        return id;
    }

    public static Material getEntityMaterial(EntityType type) {
        switch (type) {
            case ARMOR_STAND:
                return Material.ARMOR_STAND;
            case ENDER_CRYSTAL:
                return Material.END_CRYSTAL;
            default:
                return null;
        }
    }

    public static String getEntityName(int id) {
        // Internal ID pulled from DB
        String entityName = "";
        if (ConfigHandler.entitiesReversed.get(id) != null) {
            entityName = ConfigHandler.entitiesReversed.get(id);
        }
        return entityName;
    }

    public static EntityType getEntityType(int id) {
        // Internal ID pulled from DB
        EntityType entitytype = null;
        if (ConfigHandler.entitiesReversed.get(id) != null) {
            String name = ConfigHandler.entitiesReversed.get(id);
            if (name.contains(NAMESPACE)) {
                name = name.split(":")[1];
            }
            entitytype = EntityType.valueOf(name.toUpperCase(Locale.ROOT));
        }
        return entitytype;
    }

    public static EntityType getEntityType(String name) {
        // Name entered by user
        EntityType type = null;
        name = name.toLowerCase(Locale.ROOT).trim();
        if (name.contains(NAMESPACE)) {
            name = (name.split(":"))[1];
        }

        if (ConfigHandler.entities.get(name) != null) {
            type = EntityType.valueOf(name.toUpperCase(Locale.ROOT));
        }

        return type;
    }

    public static int getHangingDelay(Map<String, Integer> hangingDelay, int wid, int x, int y, int z) {
        String token = wid + "." + x + "." + y + "." + z;
        int delay = 0;
        if (hangingDelay.get(token) != null) {
            delay = hangingDelay.get(token) + 1;
        }
        hangingDelay.put(token, delay);
        return delay;
    }

    public static int getItemStackHashCode(ItemStack item) {
        try {
            return item.hashCode();
        }
        catch (Exception exception) {
            return -1;
        }
    }

    public static int getMaterialId(Material material) {
        return getBlockId(material.name(), true);
    }

    public static int getSpawnerType(EntityType type) {
        int result = Util.getEntityId(type);
        if (result == -1) {
            result = 0; // default to pig
        }

        return result;
    }

    public static EntityType getSpawnerType(int type) {
        EntityType result = Util.getEntityType(type);
        if (result == null) {
            result = EntityType.PIG;
        }

        return result;
    }

    public static boolean isAir(Material type) {
        return (type == Material.AIR || type == Material.CAVE_AIR || type == Material.VOID_AIR);
    }

    public static boolean solidBlock(Material type) {
        return type.isSolid();
    }

    public static Material getType(Block block) {
        // Temp code
        return block.getType();
    }

    public static Material getType(int id) {
        // Internal ID pulled from DB
        Material material = null;
        if (ConfigHandler.materialsReversed.get(id) != null && id > 0) {
            String name = ConfigHandler.materialsReversed.get(id).toUpperCase(Locale.ROOT);
            if (name.contains(NAMESPACE.toUpperCase(Locale.ROOT))) {
                name = name.split(":")[1];
            }

            name = BukkitAdapter.ADAPTER.parseLegacyName(name);
            material = Material.getMaterial(name);
        }

        return material;
    }

    public static Material getType(String name) {
        // Name entered by user
        Material material = null;
        name = name.toUpperCase(Locale.ROOT).trim();
        if (!name.startsWith("#")) {
            if (name.contains(NAMESPACE.toUpperCase(Locale.ROOT))) {
                name = name.split(":")[1];
            }

            name = BukkitAdapter.ADAPTER.parseLegacyName(name);
            material = Material.matchMaterial(name);
        }

        return material;
    }

    public static int getWorldId(String name) {
        int id = -1;
        try {
            if (ConfigHandler.worlds.get(name) == null) {
                int wid = ConfigHandler.worldId + 1;
                ConfigHandler.worlds.put(name, wid);
                ConfigHandler.worldsReversed.put(wid, name);
                ConfigHandler.worldId = wid;
                Queue.queueWorldInsert(wid, name);
            }
            id = ConfigHandler.worlds.get(name);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        return id;
    }

    public static String getWorldName(int id) {
        String name = "";
        try {
            if (ConfigHandler.worldsReversed.get(id) != null) {
                name = ConfigHandler.worldsReversed.get(id);
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        return name;
    }

    public static boolean iceBreakCheck(BlockState block, String user, Material type) {
        if (type.equals(Material.ICE)) { // Ice block
            int unixtimestamp = (int) (System.currentTimeMillis() / 1000L);
            int wid = Util.getWorldId(block.getWorld().getName());
            CacheHandler.lookupCache.put("" + block.getX() + "." + block.getY() + "." + block.getZ() + "." + wid + "", new Object[] { unixtimestamp, user, Material.WATER });
            return true;
        }
        return false;
    }

    public static boolean listContains(Set<Material> list, Material value) {
        boolean result = false;
        for (Material list_value : list) {
            if (list_value.equals(value)) {
                result = true;
                break;
            }
        }
        return result;
    }

    public static void loadWorldEdit() {
        try {
            boolean validVersion = true;
            String version = Bukkit.getServer().getPluginManager().getPlugin("WorldEdit").getDescription().getVersion();
            if (version.contains(";") || version.contains("+")) {
                if (version.contains("-beta-")) {
                    version = version.split(";")[0];
                    version = version.split("-beta-")[1];
                    int value = Integer.parseInt(version.replaceAll("[^0-9]", ""));
                    if (value < 6) {
                        validVersion = false;
                    }
                }
                else {
                    if (version.contains("+")) {
                        version = version.split("\\+")[1];
                    }
                    else {
                        version = version.split(";")[1];
                    }

                    if (version.contains("-")) {
                        int value = Integer.parseInt(((version.split("-"))[0]).replaceAll("[^0-9]", ""));
                        if (value > 0 && value < 4268) {
                            validVersion = false;
                        }
                    }
                }
            }
            else if (version.contains(".")) {
                String[] worldEditVersion = version.split("-|\\.");
                if (worldEditVersion.length >= 2) {
                    worldEditVersion[0] = worldEditVersion[0].replaceAll("[^0-9]", "");
                    worldEditVersion[1] = worldEditVersion[1].replaceAll("[^0-9]", "");
                    if (worldEditVersion[0].length() == 0 || worldEditVersion[1].length() == 0 || Util.newVersion(worldEditVersion[0] + "." + worldEditVersion[1], "7.1")) {
                        validVersion = false;
                    }
                }
            }
            else if (version.equals("unspecified")) { // FAWE
                validVersion = false;
                Plugin fawe = Bukkit.getServer().getPluginManager().getPlugin("FastAsyncWorldEdit");
                if (fawe != null) {
                    String apiVersion = Bukkit.getServer().getPluginManager().getPlugin("WorldEdit").getDescription().getAPIVersion();
                    String faweVersion = fawe.getDescription().getVersion();
                    double apiDouble = Double.parseDouble(apiVersion);
                    double faweDouble = Double.parseDouble(faweVersion);
                    if (apiDouble >= 1.13 && faweDouble >= 1.0) {
                        validVersion = true;
                    }
                }
            }
            else {
                validVersion = false;
            }

            if (validVersion) {
                CoreProtectEditSessionEvent.register();
            }
            else {
                Chat.console(Phrase.build(Phrase.INTEGRATION_VERSION, "WorldEdit"));
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void unloadWorldEdit() {
        try {
            CoreProtectEditSessionEvent.unregister();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static int matchWorld(String name) {
        int id = -1;
        try {
            // Parse wid:# parameter used internally for /co tp click events
            if (name.startsWith("wid:")) {
                String nameWid = name.replaceFirst("wid:", "");
                if (nameWid.length() > 0 && nameWid.equals(nameWid.replaceAll("[^0-9]", ""))) {
                    nameWid = Util.getWorldName(Integer.parseInt(nameWid));
                    if (nameWid.length() > 0) {
                        name = nameWid;
                    }
                }
            }

            // Determine closest match on world name
            String result = "";
            name = name.replaceFirst("#", "").toLowerCase(Locale.ROOT).trim();
            for (World world : Bukkit.getServer().getWorlds()) {
                String worldName = world.getName();
                if (worldName.toLowerCase(Locale.ROOT).equals(name)) {
                    result = world.getName();
                    break;
                }
                else if (worldName.toLowerCase(Locale.ROOT).endsWith(name)) {
                    result = world.getName();
                }
                else if (worldName.toLowerCase(Locale.ROOT).replaceAll("[^a-zA-Z0-9]", "").endsWith(name)) {
                    result = world.getName();
                }
            }

            if (result.length() > 0) {
                id = getWorldId(result);
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        return id;
    }

    // This theoretically initializes the component code, to prevent gson adapter errors
    public static void sendConsoleComponentStartup(ConsoleCommandSender consoleSender, String string) {
        Chat.sendComponent(consoleSender, Color.RESET + "[CoreProtect] " + string + Chat.COMPONENT_TAG_OPEN + Chat.COMPONENT_POPUP + "| | " + Chat.COMPONENT_TAG_CLOSE);
    }

    public static void performSafeTeleport(Player player, Location location, boolean enforceTeleport) {
        try {
            Set<Material> unsafeBlocks = new HashSet<>(Arrays.asList(Material.LAVA));
            unsafeBlocks.addAll(BlockGroup.FIRE);

            int worldHeight = location.getWorld().getMaxHeight();
            int playerX = location.getBlockX();
            int playerY = location.getBlockY();
            int playerZ = location.getBlockZ();
            int checkY = playerY - 1;
            boolean safeBlock = false;
            boolean placeSafe = false;
            boolean alert = false;

            while (!safeBlock) {
                int above = checkY + 1;
                if (above > worldHeight) {
                    above = worldHeight;
                }

                Block block1 = location.getWorld().getBlockAt(playerX, checkY, playerZ);
                Block block2 = location.getWorld().getBlockAt(playerX, above, playerZ);
                Material type1 = block1.getType();
                Material type2 = block2.getType();

                if (!Util.solidBlock(type1) && !Util.solidBlock(type2)) {
                    if (unsafeBlocks.contains(type1)) {
                        placeSafe = true;
                    }
                    else {
                        safeBlock = true;
                        if (placeSafe) {
                            int below = checkY - 1;
                            Block blockBelow = location.getWorld().getBlockAt(playerX, below, playerZ);

                            if (checkY < worldHeight && unsafeBlocks.contains(blockBelow.getType())) {
                                alert = true;
                                block1.setType(Material.DIRT);
                                checkY++;
                            }
                        }
                    }
                }

                if (checkY >= worldHeight || player.getGameMode() == GameMode.SPECTATOR) {
                    safeBlock = true;

                    if (checkY < worldHeight) {
                        checkY++;
                    }
                }

                if (safeBlock && (checkY > playerY || enforceTeleport)) {
                    if (checkY > worldHeight) {
                        checkY = worldHeight;
                    }

                    double oldY = location.getY();
                    location.setY(checkY);
                    player.teleport(location);

                    if (!enforceTeleport) {
                        // Only send a message if the player was moved by at least 1 block
                        if (location.getY() >= (oldY + 1.00)) {
                            Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.TELEPORTED_SAFETY));
                        }
                    }
                    else {
                        Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.TELEPORTED, "x" + playerX + "/y" + checkY + "/z" + playerZ + "/" + location.getWorld().getName()));
                    }
                    if (alert) {
                        Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + Color.ITALIC + "- " + Phrase.build(Phrase.DIRT_BLOCK));
                    }
                }

                checkY++;
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static String nameFilter(String name, int data) {
        if (name.equals("stone")) {
            switch (data) {
                case 1:
                    name = "granite";
                    break;
                case 2:
                    name = "polished_granite";
                    break;
                case 3:
                    name = "diorite";
                    break;
                case 4:
                    name = "polished_diorite";
                    break;
                case 5:
                    name = "andesite";
                    break;
                case 6:
                    name = "polished_andesite";
                    break;
                default:
                    name = "stone";
                    break;
            }
        }

        return name;
    }

    public static ItemStack newItemStack(Material type, int amount) {
        return new ItemStack(type, amount);
    }

    public static ItemStack newItemStack(Material type, int amount, short data) {
        return new ItemStack(type, amount, data);
    }

    public static boolean isSpigot() {
        try {
            Class.forName("org.spigotmc.SpigotConfig");
        }
        catch (Exception e) {
            return false;
        }

        return true;
    }

    public static boolean isPaper() {
        try {
            Class.forName("com.destroystokyo.paper.PaperConfig");
        }
        catch (Exception e) {
            return false;
        }

        return true;
    }

    public static String getBranch() {
        String branch = "";
        try {
            InputStreamReader reader = new InputStreamReader(CoreProtect.getInstance().getClass().getResourceAsStream("/plugin.yml"));
            branch = YamlConfiguration.loadConfiguration(reader).getString("branch");
            reader.close();

            if (branch == null || branch.equals("${project.branch}")) {
                branch = "";
            }
            if (branch.startsWith("-")) {
                branch = branch.substring(1);
            }
            if (branch.length() > 0) {
                branch = "-" + branch;
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        return branch;
    }

    public static boolean newVersion(Integer[] oldVersion, Integer[] currentVersion) {
        if (oldVersion[0] < currentVersion[0]) {
            // Major version
            return true;
        }
        else if (oldVersion[0].equals(currentVersion[0]) && oldVersion[1] < currentVersion[1]) {
            // Minor version
            return true;
        }
        else if (oldVersion.length < 3 && currentVersion.length >= 3 && oldVersion[0].equals(currentVersion[0]) && oldVersion[1].equals(currentVersion[1]) && 0 < currentVersion[2]) {
            // Revision version (#.# vs #.#.#)
            return true;
        }
        else if (oldVersion.length >= 3 && currentVersion.length >= 3 && oldVersion[0].equals(currentVersion[0]) && oldVersion[1].equals(currentVersion[1]) && oldVersion[2] < currentVersion[2]) {
            // Revision version (#.#.# vs #.#.#)
            return true;
        }

        return false;
    }

    public static boolean newVersion(Integer[] oldVersion, String currentVersion) {
        String[] currentVersionSplit = currentVersion.split("\\.");
        return newVersion(oldVersion, convertArray(currentVersionSplit));
    }

    public static boolean newVersion(String oldVersion, Integer[] currentVersion) {
        String[] oldVersionSplit = oldVersion.split("\\.");
        return newVersion(convertArray(oldVersionSplit), currentVersion);
    }

    public static boolean newVersion(String oldVersion, String currentVersion) {
        if (!oldVersion.contains(".") || !currentVersion.contains(".")) {
            return false;
        }

        String[] oldVersionSplit = oldVersion.split("\\.");
        String[] currentVersionSplit = currentVersion.split("\\.");
        return newVersion(convertArray(oldVersionSplit), convertArray(currentVersionSplit));
    }

    public static Map<Integer, Object> serializeItemStackLegacy(ItemStack itemStack, int slot) {
        Map<Integer, Object> result = new HashMap<>();
        Map<String, Object> itemMap = serializeItemStack(itemStack, slot);
        if (itemMap.size() > 1) {
            result.put(0, itemMap.get("0"));
            result.put(1, itemMap.get("1"));
        }

        return result;
    }

    public static ItemStack unserializeItemStackLegacy(Object value) {
        ItemStack result = null;
        if (value instanceof Map) {
            Map<String, Object> newMap = new HashMap<>();
            @SuppressWarnings("unchecked")
            Map<Integer, Object> itemMap = (Map<Integer, Object>) value;
            newMap.put("0", itemMap.get(0));
            newMap.put("1", itemMap.get(1));
            result = unserializeItemStack(newMap);
        }

        return result;
    }

    public static Map<String, Object> serializeItemStack(ItemStack itemStack, int slot) {
        Map<String, Object> itemMap = new HashMap<>();
        if (itemStack != null && !itemStack.getType().equals(Material.AIR)) {
            ItemStack item = itemStack.clone();
            List<List<Map<String, Object>>> metadata = ItemMetaHandler.seralize(item, null, slot);
            item.setItemMeta(null);
            itemMap.put("0", item.serialize());
            itemMap.put("1", metadata);
        }

        return itemMap;
    }

    public static ItemStack unserializeItemStack(Object value) {
        ItemStack result = null;
        if (value instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> itemMap = (Map<String, Object>) value;
            @SuppressWarnings("unchecked")
            ItemStack item = ItemStack.deserialize((Map<String, Object>) itemMap.get("0"));
            @SuppressWarnings("unchecked")
            List<List<Map<String, Object>>> metadata = (List<List<Map<String, Object>>>) itemMap.get("1");

            Object[] populatedStack = Rollback.populateItemStack(item, metadata);
            result = (ItemStack) populatedStack[1];
        }

        return result;
    }

    public static List<Object> processMeta(BlockState block) {
        List<Object> meta = new ArrayList<>();
        try {
            if (block instanceof CommandBlock) {
                CommandBlock commandBlock = (CommandBlock) block;
                String command = commandBlock.getCommand();
                if (command.length() > 0) {
                    meta.add(command);
                }
            }
            else if (block instanceof Banner) {
                Banner banner = (Banner) block;
                meta.add(banner.getBaseColor());
                List<Pattern> patterns = banner.getPatterns();
                for (Pattern pattern : patterns) {
                    meta.add(pattern.serialize());
                }
            }
            else if (block instanceof ShulkerBox) {
                ShulkerBox shulkerBox = (ShulkerBox) block;
                ItemStack[] inventory = shulkerBox.getSnapshotInventory().getStorageContents();
                int slot = 0;
                for (ItemStack itemStack : inventory) {
                    Map<Integer, Object> itemMap = serializeItemStackLegacy(itemStack, slot);
                    if (itemMap.size() > 0) {
                        meta.add(itemMap);
                    }
                    slot++;
                }
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        if (meta.isEmpty()) {
            meta = null;
        }
        return meta;
    }

    public static void removeHanging(final BlockState block, int delay) {
        Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(CoreProtect.getInstance(), () -> {
            try {
                for (Entity e : block.getChunk().getEntities()) {
                    if (e instanceof ItemFrame || e instanceof Painting) {
                        Location el = e.getLocation();
                        if (el.getBlockX() == block.getX() && el.getBlockY() == block.getY() && el.getBlockZ() == block.getZ()) {
                            e.remove();
                        }
                    }
                }
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }, delay);
    }

    public static void sendBlockChange(Player player, Location location, BlockData blockData) {
        player.sendBlockChange(location, blockData);
    }

    public static BlockData createBlockData(Material material) {
        BlockData result = material.createBlockData();
        if (result instanceof Waterlogged) {
            ((Waterlogged) result).setWaterlogged(false);
        }

        return result;
    }

    public static void setTypeAndData(Block block, Material type, BlockData blockData) {
        if (blockData == null) {
            blockData = createBlockData(type);
        }

        block.setBlockData(blockData);
    }

    public static void setTypeAndData(Block block, Material type, BlockData blockData, boolean update) {
        if (blockData == null) {
            blockData = createBlockData(type);
        }

        block.setBlockData(blockData, update);
    }

    public static void spawnEntity(final BlockState block, final EntityType type, final List<Object> list) {
        if (type == null) {
            return;
        }
        Bukkit.getServer().getScheduler().runTask(CoreProtect.getInstance(), () -> {
            try {
                Location location = block.getLocation();
                location.setX(location.getX() + 0.50);
                location.setZ(location.getZ() + 0.50);
                Entity entity = block.getLocation().getWorld().spawnEntity(location, type);

                if (list.isEmpty()) {
                    return;
                }

                @SuppressWarnings("unchecked")
                List<Object> age = (List<Object>) list.get(0);
                @SuppressWarnings("unchecked")
                List<Object> tame = (List<Object>) list.get(1);
                @SuppressWarnings("unchecked")
                List<Object> data = (List<Object>) list.get(2);

                if (list.size() >= 5) {
                    entity.setCustomNameVisible((Boolean) list.get(3));
                    entity.setCustomName((String) list.get(4));
                }

                int unixtimestamp = (int) (System.currentTimeMillis() / 1000L);
                int wid = Util.getWorldId(block.getWorld().getName());
                String token = "" + block.getX() + "." + block.getY() + "." + block.getZ() + "." + wid + "." + type.name() + "";
                CacheHandler.entityCache.put(token, new Object[] { unixtimestamp, entity.getEntityId() });

                if (entity instanceof Ageable) {
                    int count = 0;
                    Ageable ageable = (Ageable) entity;
                    for (Object value : age) {
                        if (count == 0) {
                            int set = (Integer) value;
                            ageable.setAge(set);
                        }
                        else if (count == 1) {
                            boolean set = (Boolean) value;
                            ageable.setAgeLock(set);
                        }
                        else if (count == 2) {
                            boolean set = (Boolean) value;
                            if (set) {
                                ageable.setAdult();
                            }
                            else {
                                ageable.setBaby();
                            }
                        }
                        else if (count == 3) {
                            boolean set = (Boolean) value;
                            ageable.setBreed(set);
                        }
                        else if (count == 4 && value != null) {
                            // deprecated
                            double set = (Double) value;
                            ageable.setMaxHealth(set);
                        }
                        count++;
                    }
                }
                if (entity instanceof Tameable) {
                    int count = 0;
                    Tameable tameable = (Tameable) entity;
                    for (Object value : tame) {
                        if (count == 0) {
                            boolean set = (Boolean) value;
                            tameable.setTamed(set);
                        }
                        else if (count == 1) {
                            String set = (String) value;
                            if (set.length() > 0) {
                                Player owner = Bukkit.getServer().getPlayer(set);
                                if (owner == null) {
                                    OfflinePlayer offlinePlayer = Bukkit.getServer().getOfflinePlayer(set);
                                    if (offlinePlayer != null) {
                                        tameable.setOwner(offlinePlayer);
                                    }
                                }
                                else {
                                    tameable.setOwner(owner);
                                }
                            }
                        }
                        count++;
                    }
                }
                if (entity instanceof Attributable && list.size() >= 6) {
                    Attributable attributable = (Attributable) entity;
                    @SuppressWarnings("unchecked")
                    List<Object> attributes = (List<Object>) list.get(5);
                    for (Object value : attributes) {
                        @SuppressWarnings("unchecked")
                        List<Object> attributeData = (List<Object>) value;
                        Attribute attribute = (Attribute) attributeData.get(0);
                        Double baseValue = (Double) attributeData.get(1);
                        @SuppressWarnings("unchecked")
                        List<Object> attributeModifiers = (List<Object>) attributeData.get(2);

                        AttributeInstance entityAttribute = attributable.getAttribute(attribute);
                        if (entityAttribute != null) {
                            entityAttribute.setBaseValue(baseValue);
                            for (AttributeModifier modifier : entityAttribute.getModifiers()) {
                                entityAttribute.removeModifier(modifier);
                            }
                            for (Object modifier : attributeModifiers) {
                                @SuppressWarnings("unchecked")
                                Map<String, Object> serializedModifier = (Map<String, Object>) modifier;
                                entityAttribute.addModifier(AttributeModifier.deserialize(serializedModifier));
                            }
                        }
                    }
                }

                if (entity instanceof LivingEntity && list.size() >= 7) {
                    LivingEntity livingEntity = (LivingEntity) entity;
                    @SuppressWarnings("unchecked")
                    List<Object> details = (List<Object>) list.get(6);
                    int count = 0;
                    for (Object value : details) {
                        if (count == 0) {
                            boolean set = (Boolean) value;
                            livingEntity.setRemoveWhenFarAway(set);
                        }
                        else if (count == 1) {
                            boolean set = (Boolean) value;
                            livingEntity.setCanPickupItems(set);
                        }
                        count++;
                    }
                }

                int count = 0;
                for (Object value : data) {
                    if (entity instanceof Creeper) {
                        Creeper creeper = (Creeper) entity;
                        if (count == 0) {
                            boolean set = (Boolean) value;
                            creeper.setPowered(set);
                        }
                    }
                    else if (entity instanceof Enderman) {
                        Enderman enderman = (Enderman) entity;
                        if (count == 1) {
                            String blockDataString = (String) value;
                            BlockData blockData = Bukkit.getServer().createBlockData(blockDataString);
                            enderman.setCarriedBlock(blockData);
                        }
                    }
                    else if (entity instanceof IronGolem) {
                        IronGolem irongolem = (IronGolem) entity;
                        if (count == 0) {
                            boolean set = (Boolean) value;
                            irongolem.setPlayerCreated(set);
                        }
                    }
                    else if (entity instanceof Cat) {
                        Cat cat = (Cat) entity;
                        if (count == 0) {
                            Cat.Type set = (Cat.Type) value;
                            cat.setCatType(set);
                        }
                        else if (count == 1) {
                            DyeColor set = (DyeColor) value;
                            cat.setCollarColor(set);
                        }
                    }
                    else if (entity instanceof Fox) {
                        Fox fox = (Fox) entity;
                        if (count == 0) {
                            Fox.Type set = (Fox.Type) value;
                            fox.setFoxType(set);
                        }
                        else if (count == 1) {
                            boolean set = (Boolean) value;
                            fox.setSitting(set);
                        }
                    }
                    else if (entity instanceof Panda) {
                        Panda panda = (Panda) entity;
                        if (count == 0) {
                            Gene set = (Gene) value;
                            panda.setMainGene(set);
                        }
                        else if (count == 1) {
                            Gene set = (Gene) value;
                            panda.setHiddenGene(set);
                        }
                    }
                    else if (entity instanceof Pig) {
                        Pig pig = (Pig) entity;
                        if (count == 0) {
                            boolean set = (Boolean) value;
                            pig.setSaddle(set);
                        }
                    }
                    else if (entity instanceof Sheep) {
                        Sheep sheep = (Sheep) entity;
                        if (count == 0) {
                            boolean set = (Boolean) value;
                            sheep.setSheared(set);
                        }
                        else if (count == 1) {
                            DyeColor set = (DyeColor) value;
                            sheep.setColor(set);
                        }
                    }
                    else if (entity instanceof MushroomCow) {
                        MushroomCow mushroomCow = (MushroomCow) entity;
                        if (count == 0) {
                            MushroomCow.Variant set = (MushroomCow.Variant) value;
                            mushroomCow.setVariant(set);
                        }
                    }
                    else if (entity instanceof Slime) {
                        Slime slime = (Slime) entity;
                        if (count == 0) {
                            int set = (Integer) value;
                            slime.setSize(set);
                        }
                    }
                    else if (entity instanceof Parrot) {
                        Parrot parrot = (Parrot) entity;
                        if (count == 0) {
                            Variant set = (Variant) value;
                            parrot.setVariant(set);
                        }
                    }
                    else if (entity instanceof TropicalFish) {
                        TropicalFish tropicalFish = (TropicalFish) entity;
                        if (count == 0) {
                            DyeColor set = (DyeColor) value;
                            tropicalFish.setBodyColor(set);
                        }
                        else if (count == 1) {
                            TropicalFish.Pattern set = (TropicalFish.Pattern) value;
                            tropicalFish.setPattern(set);
                        }
                        else if (count == 2) {
                            DyeColor set = (DyeColor) value;
                            tropicalFish.setPatternColor(set);
                        }
                    }
                    else if (entity instanceof Phantom) {
                        Phantom phantom = (Phantom) entity;
                        if (count == 0) {
                            int set = (Integer) value;
                            phantom.setSize(set);
                        }
                    }
                    else if (entity instanceof AbstractVillager) {
                        AbstractVillager abstractVillager = (AbstractVillager) entity;
                        if (count == 0) {
                            if (abstractVillager instanceof Villager) {
                                Villager villager = (Villager) abstractVillager;
                                Profession set = (Profession) value;
                                villager.setProfession(set);
                            }
                        }
                        else if (count == 1) {
                            if (abstractVillager instanceof Villager && value instanceof Villager.Type) {
                                Villager villager = (Villager) abstractVillager;
                                Villager.Type set = (Villager.Type) value;
                                villager.setVillagerType(set);
                            }
                        }
                        else if (count == 2) {
                            List<MerchantRecipe> merchantRecipes = new ArrayList<>();
                            @SuppressWarnings("unchecked")
                            List<Object> set = (List<Object>) value;
                            for (Object recipes : set) {
                                @SuppressWarnings("unchecked")
                                List<Object> recipe = (List<Object>) recipes;
                                @SuppressWarnings("unchecked")
                                List<Object> itemMap = (List<Object>) recipe.get(0);
                                @SuppressWarnings("unchecked")
                                ItemStack result = ItemStack.deserialize((Map<String, Object>) itemMap.get(0));
                                @SuppressWarnings("unchecked")
                                List<List<Map<String, Object>>> metadata = (List<List<Map<String, Object>>>) itemMap.get(1);
                                Object[] populatedStack = Rollback.populateItemStack(result, metadata);
                                result = (ItemStack) populatedStack[1];
                                int uses = (int) recipe.get(1);
                                int maxUses = (int) recipe.get(2);
                                boolean experienceReward = (boolean) recipe.get(3);
                                List<ItemStack> merchantIngredients = new ArrayList<>();
                                @SuppressWarnings("unchecked")
                                List<Object> ingredients = (List<Object>) recipe.get(4);
                                for (Object ingredient : ingredients) {
                                    @SuppressWarnings("unchecked")
                                    List<Object> ingredientMap = (List<Object>) ingredient;
                                    @SuppressWarnings("unchecked")
                                    ItemStack item = ItemStack.deserialize((Map<String, Object>) ingredientMap.get(0));
                                    @SuppressWarnings("unchecked")
                                    List<List<Map<String, Object>>> itemMetaData = (List<List<Map<String, Object>>>) ingredientMap.get(1);
                                    populatedStack = Rollback.populateItemStack(item, itemMetaData);
                                    item = (ItemStack) populatedStack[1];
                                    merchantIngredients.add(item);
                                }
                                MerchantRecipe merchantRecipe = new MerchantRecipe(result, uses, maxUses, experienceReward);
                                if (recipe.size() > 6) {
                                    int villagerExperience = (int) recipe.get(5);
                                    float priceMultiplier = (float) recipe.get(6);
                                    merchantRecipe = new MerchantRecipe(result, uses, maxUses, experienceReward, villagerExperience, priceMultiplier);
                                }
                                merchantRecipe.setIngredients(merchantIngredients);
                                merchantRecipes.add(merchantRecipe);
                            }
                            if (!merchantRecipes.isEmpty()) {
                                abstractVillager.setRecipes(merchantRecipes);
                            }
                        }
                        else {
                            Villager villager = (Villager) abstractVillager;

                            if (count == 3) {
                                int set = (int) value;
                                villager.setVillagerLevel(set);
                            }
                            else if (count == 4) {
                                int set = (int) value;
                                villager.setVillagerExperience(set);
                            }
                        }
                    }
                    else if (entity instanceof Raider) {
                        Raider raider = (Raider) entity;
                        if (count == 0) {
                            boolean set = (Boolean) value;
                            raider.setPatrolLeader(set);
                        }

                        if (entity instanceof Spellcaster && count == 1) {
                            Spellcaster spellcaster = (Spellcaster) entity;
                            Spell set = (Spell) value;
                            spellcaster.setSpell(set);
                        }
                    }
                    else if (entity instanceof Wolf) {
                        Wolf wolf = (Wolf) entity;
                        if (count == 0) {
                            boolean set = (Boolean) value;
                            wolf.setSitting(set);
                        }
                        else if (count == 1) {
                            DyeColor set = (DyeColor) value;
                            wolf.setCollarColor(set);
                        }
                    }
                    else if (entity instanceof ZombieVillager) {
                        ZombieVillager zombieVillager = (ZombieVillager) entity;
                        if (count == 0) {
                            boolean set = (Boolean) value;
                            zombieVillager.setBaby(set);
                        }
                        else if (count == 1) {
                            Profession set = (Profession) value;
                            zombieVillager.setVillagerProfession(set);
                        }
                    }
                    else if (entity instanceof Zombie) {
                        Zombie zombie = (Zombie) entity;
                        if (count == 0) {
                            boolean set = (Boolean) value;
                            zombie.setBaby(set);
                        }
                    }
                    else if (entity instanceof AbstractHorse) {
                        AbstractHorse abstractHorse = (AbstractHorse) entity;
                        if (count == 0 && value != null) {
                            // deprecated
                            boolean set = (Boolean) value;
                            if (entity instanceof ChestedHorse) {
                                ChestedHorse chestedHorse = (ChestedHorse) entity;
                                chestedHorse.setCarryingChest(set);
                            }
                        }
                        else if (count == 1 && value != null) {
                            // deprecated
                            org.bukkit.entity.Horse.Color set = (org.bukkit.entity.Horse.Color) value;
                            if (entity instanceof Horse) {
                                Horse horse = (Horse) entity;
                                horse.setColor(set);
                            }
                        }
                        else if (count == 2) {
                            int set = (Integer) value;
                            abstractHorse.setDomestication(set);
                        }
                        else if (count == 3) {
                            double set = (Double) value;
                            abstractHorse.setJumpStrength(set);
                        }
                        else if (count == 4) {
                            int set = (Integer) value;
                            abstractHorse.setMaxDomestication(set);
                        }
                        else if (count == 5 && value != null) {
                            // deprecated
                            Style set = (Style) value;
                            Horse horse = (Horse) entity;
                            horse.setStyle(set);
                        }
                        if (entity instanceof Horse) {
                            Horse horse = (Horse) entity;
                            if (count == 8) {
                                if (value != null) {
                                    @SuppressWarnings("unchecked")
                                    ItemStack set = ItemStack.deserialize((Map<String, Object>) value);
                                    horse.getInventory().setSaddle(set);
                                }
                            }
                            else if (count == 9) {
                                org.bukkit.entity.Horse.Color set = (org.bukkit.entity.Horse.Color) value;
                                horse.setColor(set);
                            }
                            else if (count == 10) {
                                Style set = (Style) value;
                                horse.setStyle(set);
                            }
                            else if (count == 11) {
                                if (value != null) {
                                    @SuppressWarnings("unchecked")
                                    ItemStack set = ItemStack.deserialize((Map<String, Object>) value);
                                    horse.getInventory().setArmor(set);
                                }
                            }
                            else if (count == 12 && value != null) {
                                @SuppressWarnings("unchecked")
                                org.bukkit.Color set = org.bukkit.Color.deserialize((Map<String, Object>) value);
                                ItemStack armor = horse.getInventory().getArmor();
                                if (armor != null) {
                                    ItemMeta itemMeta = armor.getItemMeta();
                                    if (itemMeta instanceof LeatherArmorMeta) {
                                        LeatherArmorMeta leatherArmorMeta = (LeatherArmorMeta) itemMeta;
                                        leatherArmorMeta.setColor(set);
                                        armor.setItemMeta(leatherArmorMeta);
                                    }
                                }
                            }
                        }
                        else if (entity instanceof ChestedHorse) {
                            if (count == 7) {
                                ChestedHorse chestedHorse = (ChestedHorse) entity;
                                boolean set = (Boolean) value;
                                chestedHorse.setCarryingChest(set);
                            }
                            if (entity instanceof Llama) {
                                Llama llama = (Llama) entity;
                                if (count == 8) {
                                    if (value != null) {
                                        @SuppressWarnings("unchecked")
                                        ItemStack set = ItemStack.deserialize((Map<String, Object>) value);
                                        llama.getInventory().setDecor(set);
                                    }
                                }
                                else if (count == 9) {
                                    Llama.Color set = (Llama.Color) value;
                                    llama.setColor(set);
                                }
                            }
                        }
                    }
                    else {
                        BukkitAdapter.ADAPTER.setEntityMeta(entity, value, count);
                    }
                    count++;
                }
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public static void spawnHanging(final BlockState blockstate, final Material rowType, final int rowData, int delay) {
        Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(CoreProtect.getInstance(), () -> {
            try {
                Block block = blockstate.getBlock();
                int x = block.getX();
                int y = block.getY();
                int z = block.getZ();

                for (Entity e : block.getChunk().getEntities()) {
                    if ((BukkitAdapter.ADAPTER.isItemFrame(rowType) && e instanceof ItemFrame) || (rowType.equals(Material.PAINTING) && e instanceof Painting)) {
                        Location el = e.getLocation();
                        if (el.getBlockX() == x && el.getBlockY() == y && el.getBlockZ() == z) {
                            e.remove();
                            break;
                        }
                    }
                }

                Block c1 = block.getWorld().getBlockAt((x + 1), y, z);
                Block c2 = block.getWorld().getBlockAt((x - 1), y, z);
                Block c3 = block.getWorld().getBlockAt(x, y, (z + 1));
                Block c4 = block.getWorld().getBlockAt(x, y, (z - 1));

                BlockFace faceSet = null;
                if (!BlockGroup.NON_ATTACHABLE.contains(c1.getType())) {
                    faceSet = BlockFace.WEST;
                    block = c1;
                }
                else if (!BlockGroup.NON_ATTACHABLE.contains(c2.getType())) {
                    faceSet = BlockFace.EAST;
                    block = c2;
                }
                else if (!BlockGroup.NON_ATTACHABLE.contains(c3.getType())) {
                    faceSet = BlockFace.NORTH;
                    block = c3;
                }
                else if (!BlockGroup.NON_ATTACHABLE.contains(c4.getType())) {
                    faceSet = BlockFace.SOUTH;
                    block = c4;
                }

                BlockFace face = null;
                if (!solidBlock(Util.getType(block.getRelative(BlockFace.EAST)))) {
                    face = BlockFace.EAST;
                }
                else if (!solidBlock(Util.getType(block.getRelative(BlockFace.NORTH)))) {
                    face = BlockFace.NORTH;
                }
                else if (!solidBlock(Util.getType(block.getRelative(BlockFace.WEST)))) {
                    face = BlockFace.WEST;
                }
                else if (!solidBlock(Util.getType(block.getRelative(BlockFace.SOUTH)))) {
                    face = BlockFace.SOUTH;
                }

                if (faceSet != null && face != null) {
                    if (rowType.equals(Material.PAINTING)) {
                        String name = Util.getArtName(rowData);
                        Art painting = Art.getByName(name.toUpperCase(Locale.ROOT));
                        int height = painting.getBlockHeight();
                        int width = painting.getBlockWidth();
                        int paintingX = x;
                        int paintingY = y;
                        int paintingZ = z;
                        if (height != 1 || width != 1) {
                            if (height > 1) {
                                if (height != 3) {
                                    paintingY = paintingY - 1;
                                }
                            }
                            if (width > 1) {
                                if (faceSet.equals(BlockFace.WEST)) {
                                    paintingZ--;
                                }
                                else if (faceSet.equals(BlockFace.SOUTH)) {
                                    paintingX--;
                                }
                            }
                        }
                        Block spawnBlock = block.getRelative(face);
                        Util.setTypeAndData(spawnBlock, Material.AIR, null, true);
                        Painting hanging = null;
                        try {
                            hanging = block.getWorld().spawn(spawnBlock.getLocation(), Painting.class);
                        }
                        catch (Exception e) {
                        }
                        if (hanging != null) {
                            hanging.teleport(block.getWorld().getBlockAt(paintingX, paintingY, paintingZ).getLocation());
                            hanging.setFacingDirection(faceSet, true);
                            hanging.setArt(painting, true);
                        }
                    }
                    else if (BukkitAdapter.ADAPTER.isItemFrame(rowType)) {
                        try {
                            Block spawnBlock = block.getRelative(face);
                            Util.setTypeAndData(spawnBlock, Material.AIR, null, true);
                            Class itemFrame = BukkitAdapter.ADAPTER.getFrameClass(rowType);
                            Entity entity = block.getWorld().spawn(spawnBlock.getLocation(), itemFrame);
                            if (entity instanceof ItemFrame) {
                                ItemFrame hanging = (ItemFrame) entity;
                                hanging.teleport(block.getWorld().getBlockAt(x, y, z).getLocation());
                                hanging.setFacingDirection(faceSet, true);

                                Material type = Util.getType(rowData);
                                if (type != null) {
                                    ItemStack istack = new ItemStack(type, 1);
                                    hanging.setItem(istack);
                                }
                            }
                        }
                        catch (Exception e) {
                        }
                    }
                }
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }, delay);
    }

    public static boolean successfulQuery(Connection connection, String query) {
        boolean result = false;
        try {
            PreparedStatement preparedStmt = connection.prepareStatement(query);
            ResultSet resultSet = preparedStmt.executeQuery();
            if (resultSet.isBeforeFirst()) {
                result = true;
            }
            resultSet.close();
            preparedStmt.close();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }

    public static String[] toStringArray(String[] array) {
        int size = array.length;
        if (size == 11) {
            String time = array[0];
            String user = array[1];
            String x = array[2];
            String y = array[3];
            String z = array[4];
            String type = array[5];
            String data = array[6];
            String action = array[7];
            String rolledBack = array[8];
            String wid = array[9];
            String blockData = array[10];
            return new String[] { time, user, x, y, z, type, data, action, rolledBack, wid, "", "", blockData };
        }

        return null;
    }

    public static void updateBlock(final BlockState block) {
        Bukkit.getServer().getScheduler().runTask(CoreProtect.getInstance(), () -> {
            try {
                if (block.getBlockData() instanceof Waterlogged) {
                    Block currentBlock = block.getBlock();
                    if (currentBlock.getType().equals(block.getType())) {
                        block.setBlockData(currentBlock.getBlockData());
                    }
                }
                block.update();
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    public static void updateInventory(Player player) {
        player.updateInventory();
    }

    public static boolean checkWorldEdit() {
        boolean result = false;
        for (World world : Bukkit.getServer().getWorlds()) {
            if (Config.getConfig(world).WORLDEDIT) {
                result = true;
                break;
            }
        }

        return result;
    }

}
