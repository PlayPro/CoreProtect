package net.coreprotect.utility;

import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.config.Configurator;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Banner;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.CommandBlock;
import org.bukkit.block.Jukebox;
import org.bukkit.block.ShulkerBox;
import org.bukkit.block.banner.Pattern;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.serialization.ConfigurationSerialization;
import org.bukkit.configuration.serialization.DelegateDeserialization;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.BlockInventoryHolder;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.io.BukkitObjectOutputStream;

import net.coreprotect.CoreProtect;
import net.coreprotect.bukkit.BukkitAdapter;
import net.coreprotect.config.Config;
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.consumer.Queue;
import net.coreprotect.database.rollback.Rollback;
import net.coreprotect.language.Phrase;
import net.coreprotect.model.BlockGroup;
import net.coreprotect.thread.CacheHandler;
import net.coreprotect.thread.NetworkHandler;
import net.coreprotect.thread.Scheduler;
import net.coreprotect.utility.serialize.ItemMetaHandler;
import net.coreprotect.worldedit.CoreProtectEditSessionEvent;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;

public class Util extends Queue {

    public static final java.util.regex.Pattern tagParser = java.util.regex.Pattern.compile(Chat.COMPONENT_TAG_OPEN + "(.+?)" + Chat.COMPONENT_TAG_CLOSE + "|(.+?)", java.util.regex.Pattern.DOTALL);
    private static final String NAMESPACE = "minecraft:";

    private Util() {
        throw new IllegalStateException("Utility class");
    }

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
        else if (isCommunityEdition()) {
            name = name + " " + ConfigHandler.COMMUNITY_EDITION;
        }

        return name;
    }

    public static CentralProcessor getProcessorInfo() {
        CentralProcessor result = null;
        try {
            Class.forName("com.sun.jna.Platform");
            if (System.getProperty("os.name").startsWith("Windows") && !System.getProperty("sun.arch.data.model").equals("64")) {
                Class.forName("com.sun.jna.platform.win32.Win32Exception");
            }
            else if (System.getProperty("os.name").toLowerCase().contains("android") || System.getProperty("java.runtime.name").toLowerCase().contains("android")) {
                return null;
            }
            Configurator.setLevel("oshi.hardware.common.AbstractCentralProcessor", Level.OFF);
            SystemInfo systemInfo = new SystemInfo();
            result = systemInfo.getHardware().getProcessor();
        }
        catch (Exception e) {
            // unable to read processor information
        }

        return result;
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
        DecimalFormat decimalFormat = new DecimalFormat("#.##", new DecimalFormatSymbols(Locale.ROOT));
        message.append("|/" + command + " teleport wid:" + worldId + " " + decimalFormat.format(x + 0.50) + " " + y + " " + decimalFormat.format(z + 0.50) + "|");

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

        StringBuilder pagination = new StringBuilder();
        if (totalPages > 1) {
            pagination.append(Color.GREY + "(");
            if (page > 3) {
                pagination.append(Color.WHITE + Chat.COMPONENT_TAG_OPEN + Chat.COMPONENT_COMMAND + "|/" + command + " l " + 1 + "|" + "1 " + Chat.COMPONENT_TAG_CLOSE);
                if (page > 4 && totalPages > 7) {
                    pagination.append(Color.GREY + "... ");
                }
                else {
                    pagination.append(Color.GREY + "| ");
                }
            }

            int displayStart = (page - 2) < 1 ? 1 : (page - 2);
            int displayEnd = (page + 2) > totalPages ? totalPages : (page + 2);
            if (page > 999 || (page > 101 && totalPages > 99999)) { // limit to max 5 page numbers
                displayStart = (displayStart + 1) < displayEnd ? (displayStart + 1) : displayStart;
                displayEnd = (displayEnd - 1) > displayStart ? (displayEnd - 1) : displayEnd;
                if (displayStart > (totalPages - 3)) {
                    displayStart = (totalPages - 3) < 1 ? 1 : (totalPages - 3);
                }
            }
            else { // display at least 7 page numbers
                if (displayStart > (totalPages - 5)) {
                    displayStart = (totalPages - 5) < 1 ? 1 : (totalPages - 5);
                }
                if (displayEnd < 6) {
                    displayEnd = 6 > totalPages ? totalPages : 6;
                }
            }

            if (page > 99999) { // limit to max 3 page numbers
                displayStart = (displayStart + 1) < displayEnd ? (displayStart + 1) : displayStart;
                displayEnd = (displayEnd - 1) >= displayStart ? (displayEnd - 1) : displayEnd;
                if (page == (totalPages - 1)) {
                    displayEnd = totalPages - 1;
                }
                if (displayStart < displayEnd) {
                    displayStart = displayEnd;
                }
            }

            if (page > 3 && displayStart == 1) {
                displayStart = 2;
            }

            for (int displayPage = displayStart; displayPage <= displayEnd; displayPage++) {
                if (page != displayPage) {
                    pagination.append(Color.WHITE + Chat.COMPONENT_TAG_OPEN + Chat.COMPONENT_COMMAND + "|/" + command + " l " + displayPage + "|" + displayPage + (displayPage < totalPages ? " " : "") + Chat.COMPONENT_TAG_CLOSE);
                }
                else {
                    pagination.append(Color.WHITE + Color.UNDERLINE + displayPage + Color.RESET + (displayPage < totalPages ? " " : ""));
                }
                if (displayPage < displayEnd) {
                    pagination.append(Color.GREY + "| ");
                }
            }

            if (displayEnd < totalPages) {
                if (displayEnd < (totalPages - 1)) {
                    pagination.append(Color.GREY + "... ");
                }
                else {
                    pagination.append(Color.GREY + "| ");
                }
                if (page != totalPages) {
                    pagination.append(Color.WHITE + Chat.COMPONENT_TAG_OPEN + Chat.COMPONENT_COMMAND + "|/" + command + " l " + totalPages + "|" + totalPages + Chat.COMPONENT_TAG_CLOSE);
                }
                else {
                    pagination.append(Color.WHITE + Color.UNDERLINE + totalPages);
                }
            }

            pagination.append(Color.GREY + ")");
        }

        return message.append(Color.WHITE + backArrow + Color.DARK_AQUA + Phrase.build(Phrase.LOOKUP_PAGE, Color.WHITE + page + "/" + totalPages) + nextArrow + pagination).toString();
    }

    public static String getTimeSince(long resultTime, long currentTime, boolean component) {
        StringBuilder message = new StringBuilder();
        double timeSince = currentTime - (resultTime + 0.00);
        if (timeSince < 0.00) {
            timeSince = 0.00;
        }

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
            Date logDate = new Date(resultTime * 1000L);
            String formattedTimestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss z").format(logDate);

            return Chat.COMPONENT_TAG_OPEN + Chat.COMPONENT_POPUP + "|" + Color.GREY + formattedTimestamp + "|" + Color.GREY + message.toString() + Chat.COMPONENT_TAG_CLOSE;
        }

        return message.toString();
    }

    public static String getEnchantments(byte[] metadata, int type, int amount) {
        if (metadata == null) {
            return "";
        }

        ItemStack item = new ItemStack(Util.getType(type), amount);
        item = (ItemStack) Rollback.populateItemStack(item, metadata)[2];
        String displayName = item.hasItemMeta() && item.getItemMeta().hasDisplayName() ? item.getItemMeta().getDisplayName() : "";
        StringBuilder message = new StringBuilder(Color.ITALIC + displayName + Color.GREY);

        List<String> enchantments = ItemMetaHandler.getEnchantments(item, displayName);
        for (String enchantment : enchantments) {
            if (message.length() > 0) {
                message.append("\n");
            }
            message.append(enchantment);
        }

        if (!displayName.isEmpty()) {
            message.insert(0, enchantments.isEmpty() ? Color.WHITE : Color.AQUA);
        }
        else if (!enchantments.isEmpty()) {
            String name = Util.capitalize(item.getType().name().replace("_", " "), true);
            message.insert(0, Color.AQUA + Color.ITALIC + name);
        }

        return message.toString();
    }

    public static String createTooltip(String phrase, String tooltip) {
        if (tooltip.isEmpty()) {
            return phrase;
        }

        StringBuilder message = new StringBuilder(Chat.COMPONENT_TAG_OPEN + Chat.COMPONENT_POPUP);

        // tooltip
        message.append("|" + tooltip.replace("|", Chat.COMPONENT_PIPE) + "|");

        // chat output
        message.append(phrase);

        return message.append(Chat.COMPONENT_TAG_CLOSE).toString();
    }

    public static String hoverCommandFilter(String string) {
        StringBuilder command = new StringBuilder();

        String[] data = string.toLowerCase().split(" ");
        if (data.length > 2) {
            if (data[1].equals("l")) {
                data[1] = "page";
            }

            if (data[2].startsWith("wid:")) {
                String nameWid = data[2].replaceFirst("wid:", "");
                if (nameWid.length() > 0 && nameWid.equals(nameWid.replaceAll("[^0-9]", ""))) {
                    nameWid = Util.getWorldName(Integer.parseInt(nameWid));
                    if (nameWid.length() > 0) {
                        data[2] = nameWid;
                    }
                }
            }

            if (data[1].equals("teleport") && data.length > 5) {
                data[3] = Integer.toString((int) (Double.parseDouble(data[3]) - 0.50));
                data[4] = Integer.toString(Integer.parseInt(data[4]));
                data[5] = Integer.toString((int) (Double.parseDouble(data[5]) - 0.50));
            }
        }

        for (String s : data) {
            if (s.isEmpty()) {
                continue;
            }

            if (command.length() > 0) {
                command.append(" ");
            }

            command.append(s);
        }

        return command.toString();
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
        if (material != null && (material.equals(Material.ARMOR_STAND) || BukkitAdapter.ADAPTER.isItemFrame(material))) {
            return;
        }
        try {
            int c1 = 0;
            for (ItemStack o1 : items) {
                if (o1 != null && o1.getAmount() > 0) {
                    int c2 = 0;
                    for (ItemStack o2 : items) {
                        if (o2 != null && c2 > c1 && o1.isSimilar(o2) && !Util.isAir(o1.getType())) { // Ignores amount
                            int namount = o1.getAmount() + o2.getAmount();
                            o1.setAmount(namount);
                            o2.setAmount(0);
                        }
                        c2++;
                    }
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
            else if (!string.contains(":") && (material == Material.PAINTING || BukkitAdapter.ADAPTER.isItemFrame(material))) {
                int id = getBlockdataId(string, true);
                if (id > -1) {
                    string = Integer.toString(id);
                }
                else {
                    return result;
                }
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

                    if (material == Material.PAINTING || BukkitAdapter.ADAPTER.isItemFrame(material)) {
                        result = String.join(",", blockDataArray);
                    }
                    else {
                        result = NAMESPACE + material.name().toLowerCase(Locale.ROOT) + "[" + String.join(",", blockDataArray) + "]";
                    }
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
        catch (Exception e) { // only display exception on development branch
            if (!ConfigHandler.EDITION_BRANCH.contains("-dev")) {
                e.printStackTrace();
            }
        }

        return result;
    }

    public static ItemMeta deserializeItemMeta(Class<? extends ItemMeta> itemMetaClass, Map<String, Object> args) {
        try {
            DelegateDeserialization delegate = itemMetaClass.getAnnotation(DelegateDeserialization.class);
            return (ItemMeta) ConfigurationSerialization.deserializeObject(args, delegate.value());
        }
        catch (Exception e) { // only display exception on development branch
            if (!ConfigHandler.EDITION_BRANCH.contains("-dev")) {
                e.printStackTrace();
            }
        }

        return null;
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
        ItemStack[] result = array == null ? null : array.clone();
        if (result == null) {
            return result;
        }

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

    public static ItemStack[] sortContainerState(ItemStack[] array) {
        if (array == null) {
            return null;
        }

        ItemStack[] sorted = new ItemStack[array.length];
        Map<String, ItemStack> map = new HashMap<>();
        for (ItemStack itemStack : array) {
            if (itemStack == null) {
                continue;
            }

            map.put(itemStack.toString(), itemStack);
        }

        ArrayList<String> sortedKeys = new ArrayList<>(map.keySet());
        Collections.sort(sortedKeys);

        int i = 0;
        for (String key : sortedKeys) {
            sorted[i] = map.get(key);
            i++;
        }

        return sorted;
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

    /* return true if item can be added to container */
    public static boolean canAddContainer(ItemStack[] container, ItemStack item, int forceMaxStack) {
        for (ItemStack containerItem : container) {
            if (containerItem == null || containerItem.getType() == Material.AIR) {
                return true;
            }

            int maxStackSize = containerItem.getMaxStackSize();
            if (forceMaxStack > 0 && (forceMaxStack < maxStackSize || maxStackSize == -1)) {
                maxStackSize = forceMaxStack;
            }

            if (maxStackSize == -1) {
                maxStackSize = 1;
            }

            if (containerItem.isSimilar(item) && containerItem.getAmount() < maxStackSize) {
                return true;
            }
        }

        return false;
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

    public static int setPlayerArmor(PlayerInventory inventory, ItemStack itemStack) {
        String itemName = itemStack.getType().name();
        boolean isHelmet = (itemName.endsWith("_HELMET") || itemName.endsWith("_HEAD") || itemName.endsWith("_SKULL") || itemName.endsWith("_PUMPKIN"));
        boolean isChestplate = (itemName.endsWith("_CHESTPLATE"));
        boolean isLeggings = (itemName.endsWith("_LEGGINGS"));
        boolean isBoots = (itemName.endsWith("_BOOTS"));

        if (isHelmet && inventory.getHelmet() == null) {
            inventory.setHelmet(itemStack);
            return 3;
        }
        else if (isChestplate && inventory.getChestplate() == null) {
            inventory.setChestplate(itemStack);
            return 2;
        }
        else if (isLeggings && inventory.getLeggings() == null) {
            inventory.setLeggings(itemStack);
            return 1;
        }
        else if (isBoots && inventory.getBoots() == null) {
            inventory.setBoots(itemStack);
            return 0;
        }

        return -1;
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

                if (type == Material.ARMOR_STAND) {
                    LivingEntity entity = (LivingEntity) container;
                    EntityEquipment equipment = Util.getEntityEquipment(entity);
                    if (equipment != null) {
                        contents = getArmorStandContents(equipment);
                    }
                }
                else if (type == Material.ITEM_FRAME) {
                    ItemFrame entity = (ItemFrame) container;
                    contents = Util.getItemFrameItem(entity);
                }
                else if (type == Material.JUKEBOX) {
                    Jukebox blockState = (Jukebox) ((Block) container).getState();
                    contents = Util.getJukeboxItem(blockState);
                }
                else {
                    Block block = (Block) container;
                    Inventory inventory = Util.getContainerInventory(block.getState(), true);
                    if (inventory != null) {
                        contents = inventory.getContents();
                    }
                }

                if (type == Material.ARMOR_STAND || type == Material.ITEM_FRAME) {
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

    public static ItemStack[] getItemFrameItem(ItemFrame entity) {
        ItemStack[] contents = null;
        try {
            contents = new ItemStack[] { entity.getItem() };
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        return contents;
    }

    public static ItemStack[] getJukeboxItem(Jukebox blockState) {
        ItemStack[] contents = null;
        try {
            contents = new ItemStack[] { blockState.getRecord() };
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        return contents;
    }

    public static int getEntityId(EntityType type) {
        if (type == null) {
            return -1;
        }

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
        switch (type.name()) {
            case "ARMOR_STAND":
                return Material.ARMOR_STAND;
            case "ITEM_FRAME":
                return Material.ITEM_FRAME;
            case "END_CRYSTAL":
            case "ENDER_CRYSTAL":
                return Material.END_CRYSTAL;
            case "ENDER_PEARL":
                return Material.ENDER_PEARL;
            case "POTION":
            case "SPLASH_POTION":
                return Material.SPLASH_POTION;
            case "EXPERIENCE_BOTTLE":
            case "THROWN_EXP_BOTTLE":
                return Material.EXPERIENCE_BOTTLE;
            case "TRIDENT":
                return Material.TRIDENT;
            case "FIREWORK_ROCKET":
            case "FIREWORK":
                return Material.FIREWORK_ROCKET;
            case "EGG":
                return Material.EGG;
            case "SNOWBALL":
                return Material.SNOWBALL;
            case "WIND_CHARGE":
                return Material.valueOf("WIND_CHARGE");
            default:
                return BukkitAdapter.ADAPTER.getFrameType(type);
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

    public static boolean passableBlock(Block block) {
        return block.isPassable();
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

            if (material == null) {
                material = Material.getMaterial(name, true);
            }
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
                    long value = Long.parseLong(version.replaceAll("[^0-9]", ""));
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
                        long value = Long.parseLong(((version.split("-"))[0]).replaceAll("[^0-9]", ""));
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

    // This filter is only used for a:inventory
    public static Material itemFilter(Material material, boolean blockTable) {
        if (material == null || (!blockTable && material.isItem())) {
            return material;
        }

        material = BukkitAdapter.ADAPTER.getPlantSeeds(material);
        if (material.name().contains("WALL_")) {
            material = Material.valueOf(material.name().replace("WALL_", ""));
        }

        return material;
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

    public static boolean isFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
        }
        catch (Exception e) {
            return false;
        }

        return true;
    }

    public static boolean isCommunityEdition() {
        return !isBranch("edge") && !isBranch("coreprotect") && !validDonationKey();
    }

    public static boolean isBranch(String branch) {
        return ConfigHandler.EDITION_BRANCH.contains("-" + branch);
    }

    public static boolean validDonationKey() {
        return NetworkHandler.donationKey() != null;
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

    public static Map<Integer, Object> serializeItemStackLegacy(ItemStack itemStack, String faceData, int slot) {
        Map<Integer, Object> result = new HashMap<>();
        Map<String, Object> itemMap = serializeItemStack(itemStack, faceData, slot);
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

    public static Map<String, Object> serializeItemStack(ItemStack itemStack, String faceData, int slot) {
        Map<String, Object> itemMap = new HashMap<>();
        if (itemStack != null && !itemStack.getType().equals(Material.AIR)) {
            ItemStack item = itemStack.clone();
            List<List<Map<String, Object>>> metadata = ItemMetaHandler.serialize(item, null, faceData, slot);
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
            result = (ItemStack) populatedStack[2];
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
                    Map<Integer, Object> itemMap = serializeItemStackLegacy(itemStack, null, slot);
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

    public static void sendBlockChange(Player player, Location location, BlockData blockData) {
        player.sendBlockChange(location, blockData);
    }

    public static BlockData createBlockData(Material material) {
        try {
            BlockData result = material.createBlockData();
            if (result instanceof Waterlogged) {
                ((Waterlogged) result).setWaterlogged(false);
            }
            return result;
        }
        catch (Exception e) {
            return null;
        }
    }

    public static void prepareTypeAndData(Map<Block, BlockData> map, Block block, Material type, BlockData blockData, boolean update) {
        if (blockData == null) {
            blockData = createBlockData(type);
        }

        if (!update) {
            setTypeAndData(block, type, blockData, update);
            map.remove(block);
        }
        else {
            map.put(block, blockData);
        }
    }

    public static void setTypeAndData(Block block, Material type, BlockData blockData, boolean update) {
        if (blockData == null && type != null) {
            blockData = createBlockData(type);
        }

        if (blockData != null) {
            block.setBlockData(blockData, update);
        }
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
        Scheduler.runTask(CoreProtect.getInstance(), () -> {
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
        }, block.getLocation());
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

    public static String getWidIndex(String queryTable) {
        String index = "";
        boolean isMySQL = Config.getGlobal().MYSQL;
        if (isMySQL) {
            index = "USE INDEX(wid) ";
        }
        else {
            switch (queryTable) {
                case "block":
                    index = "INDEXED BY block_index ";
                    break;
                case "container":
                    index = "INDEXED BY container_index ";
                    break;
                case "item":
                    index = "INDEXED BY item_index ";
                    break;
                case "sign":
                    index = "INDEXED BY sign_index ";
                    break;
                case "chat":
                    index = "INDEXED BY chat_wid_index ";
                    break;
                case "command":
                    index = "INDEXED BY command_wid_index ";
                    break;
                case "session":
                    index = "INDEXED BY session_index ";
                    break;
                default:
                    break;
            }
        }

        return index;
    }

    public static int rolledBack(int rolledBack, boolean isInventory) {
        switch (rolledBack) {
            case 1: // just block rolled back
                return isInventory ? 0 : 1;
            case 2: // just inventory rolled back
                return isInventory ? 1 : 0;
            case 3: // block and inventory rolled back
                return 1;
            default: // no rollbacks
                return 0;
        }
    }

    public static int toggleRolledBack(int rolledBack, boolean isInventory) {
        switch (rolledBack) {
            case 1: // just block rolled back
                return isInventory ? 3 : 0;
            case 2: // just inventory rolled back
                return isInventory ? 0 : 3;
            case 3: // block and inventory rolled back
                return isInventory ? 1 : 2;
            default: // no rollbacks
                return isInventory ? 2 : 1;
        }
    }

    public static int getSignData(boolean frontGlowing, boolean backGlowing) {
        if (frontGlowing && backGlowing) {
            return 3;
        }
        else if (backGlowing) {
            return 2;
        }
        else if (frontGlowing) {
            return 1;
        }

        return 0;
    }

    public static boolean isSideGlowing(boolean isFront, int data) {
        return ((isFront && (data == 1 || data == 3)) || (!isFront && (data == 2 || data == 3)));
    }

}
