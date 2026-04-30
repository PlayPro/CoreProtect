package net.coreprotect.command.parser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;

import net.coreprotect.CoreProtect;
import net.coreprotect.command.TabHandler;
import net.coreprotect.utility.Util;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Tag;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;

import net.coreprotect.language.Phrase;
import net.coreprotect.model.BlockGroup;
import net.coreprotect.utility.BlockTypeUtils;
import net.coreprotect.utility.Chat;
import net.coreprotect.utility.Color;
import net.coreprotect.utility.EntityUtils;
import net.coreprotect.utility.MaterialUtils;

/**
 * Parser for material and entity related command arguments
 */
public class MaterialParser {

    private static final Map<String, Set<Material>> BUILTIN_TAGS = Util.make(new HashMap<>(), map -> {
        map.put("#button", BlockGroup.BUTTONS);
        map.put("#container", BlockGroup.CONTAINERS);
        map.put("#door", BlockGroup.DOORS);
        map.put("#natural", BlockGroup.NATURAL_BLOCKS);
        map.put("#pressure_plate", BlockGroup.PRESSURE_PLATES);
        map.put("#shulker_box", BlockGroup.SHULKER_BOXES);
    });

    // builtin tags + configured tags
    private static final Map<String, Set<Material>> ALL_TAGS = Util.make(new HashMap<>(), map -> map.putAll(BUILTIN_TAGS));

    /**
     * Parse restricted materials and entities from command arguments
     * 
     * @param player
     *            The command sender
     * @param inputArguments
     *            The command arguments
     * @param argAction
     *            The list of actions to include
     * @return A list of restricted materials and entities
     */
    public static List<Object> parseRestricted(CommandSender player, String[] inputArguments, List<Integer> argAction) {
        String[] argumentArray = inputArguments.clone();
        List<Object> restricted = new ArrayList<>();
        int count = 0;
        int next = 0;
        for (String argument : argumentArray) {
            if (count > 0) {
                argument = argument.trim().toLowerCase(Locale.ROOT);
                argument = argument.replaceAll("\\\\", "");
                argument = argument.replaceAll("'", "");

                if (argument.equals("i:") || argument.equals("include:") || argument.equals("item:") || argument.equals("items:") || argument.equals("b:") || argument.equals("block:") || argument.equals("blocks:")) {
                    next = 4;
                }
                else if (next == 4 || argument.startsWith("i:") || argument.startsWith("include:") || argument.startsWith("item:") || argument.startsWith("items:") || argument.startsWith("b:") || argument.startsWith("block:") || argument.startsWith("blocks:")) {
                    argument = argument.replaceAll("include:", "");
                    argument = argument.replaceAll("i:", "");
                    argument = argument.replaceAll("items:", "");
                    argument = argument.replaceAll("item:", "");
                    argument = argument.replaceAll("blocks:", "");
                    argument = argument.replaceAll("block:", "");
                    argument = argument.replaceAll("b:", "");
                    if (argument.contains(",")) {
                        String[] i2 = argument.split(",");
                        for (String i3 : i2) {
                            if (!checkTags(i3, restricted)) {
                                Material i3_material = MaterialUtils.getType(i3);
                                if (i3_material != null && (i3_material.isBlock() || argAction.contains(4))) {
                                    restricted.add(i3_material);
                                }
                                else if (!argAction.contains(4) && BlockTypeUtils.hasBlockType(i3)) {
                                    restricted.add(BlockTypeUtils.normalizeKey(i3));
                                }
                                else {
                                    EntityType i3_entity = EntityUtils.getEntityType(i3);
                                    if (i3_entity != null) {
                                        restricted.add(i3_entity);
                                    }
                                    else if (i3_material != null) {
                                        restricted.add(i3_material);
                                    }
                                    else {
                                        Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.INVALID_INCLUDE, i3));
                                        // Functions.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.MISSING_PARAMETERS, "/co help include"));
                                        return null;
                                    }
                                }
                            }
                        }
                        if (argument.endsWith(",")) {
                            next = 4;
                        }
                        else {
                            next = 0;
                        }
                    }
                    else {
                        if (!checkTags(argument, restricted)) {
                            Material material = MaterialUtils.getType(argument);
                            if (material != null && (material.isBlock() || argAction.contains(4))) {
                                restricted.add(material);
                            }
                            else if (!argAction.contains(4) && BlockTypeUtils.hasBlockType(argument)) {
                                restricted.add(BlockTypeUtils.normalizeKey(argument));
                            }
                            else {
                                EntityType entityType = EntityUtils.getEntityType(argument);
                                if (entityType != null) {
                                    restricted.add(entityType);
                                }
                                else if (material != null) {
                                    restricted.add(material);
                                }
                                else {
                                    Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.INVALID_INCLUDE, argument));
                                    // Functions.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.MISSING_PARAMETERS, "/co help include"));
                                    return null;
                                }
                            }
                        }
                        next = 0;
                    }
                }
                else {
                    next = 0;
                }
            }
            count++;
        }
        return restricted;
    }

    /**
     * Parse excluded materials and entities from command arguments
     * 
     * @param player
     *            The command sender
     * @param inputArguments
     *            The command arguments
     * @param argAction
     *            The list of actions to include
     * @return A map of excluded materials and entities
     */
    public static Map<Object, Boolean> parseExcluded(CommandSender player, String[] inputArguments, List<Integer> argAction) {
        String[] argumentArray = inputArguments.clone();
        Map<Object, Boolean> excluded = new HashMap<>();
        int count = 0;
        int next = 0;
        for (String argument : argumentArray) {
            if (count > 0) {
                argument = argument.trim().toLowerCase(Locale.ROOT);
                argument = argument.replaceAll("\\\\", "");
                argument = argument.replaceAll("'", "");

                if (argument.equals("e:") || argument.equals("exclude:")) {
                    next = 5;
                }
                else if (next == 5 || argument.startsWith("e:") || argument.startsWith("exclude:")) {
                    argument = argument.replaceAll("exclude:", "");
                    argument = argument.replaceAll("e:", "");
                    if (argument.contains(",")) {
                        String[] i2 = argument.split(",");
                        for (String i3 : i2) {
                            if (!checkTags(i3, excluded)) {
                                Material i3_material = MaterialUtils.getType(i3);
                                if (i3_material != null && (i3_material.isBlock() || argAction.contains(4))) {
                                    excluded.put(i3_material, false);
                                }
                                else if (!argAction.contains(4) && BlockTypeUtils.hasBlockType(i3)) {
                                    excluded.put(BlockTypeUtils.normalizeKey(i3), false);
                                }
                                else {
                                    EntityType i3_entity = EntityUtils.getEntityType(i3);
                                    if (i3_entity != null) {
                                        excluded.put(i3_entity, false);
                                    }
                                    else if (i3_material != null) {
                                        excluded.put(i3_material, false);
                                    }
                                }
                            }
                        }
                        if (argument.endsWith(",")) {
                            next = 5;
                        }
                        else {
                            next = 0;
                        }
                    }
                    else {
                        if (!checkTags(argument, excluded)) {
                            Material iMaterial = MaterialUtils.getType(argument);
                            if (iMaterial != null && (iMaterial.isBlock() || argAction.contains(4))) {
                                excluded.put(iMaterial, false);
                            }
                            else if (!argAction.contains(4) && BlockTypeUtils.hasBlockType(argument)) {
                                excluded.put(BlockTypeUtils.normalizeKey(argument), false);
                            }
                            else {
                                EntityType iEntity = EntityUtils.getEntityType(argument);
                                if (iEntity != null) {
                                    excluded.put(iEntity, false);
                                }
                                else if (iMaterial != null) {
                                    excluded.put(iMaterial, false);
                                }
                            }
                        }
                        next = 0;
                    }
                }
                else {
                    next = 0;
                }
            }
            count++;
        }
        return excluded;
    }

    /**
     * Get a map of block tags and their associated materials
     * 
     * @return A map of block tags and their associated materials
     */
    public static Map<String, Set<Material>> getTags() {
        return ALL_TAGS;
    }

    /**
     * Check if an argument matches a block tag
     * 
     * @param argument
     *            The argument to check
     * @return true if the argument matches a block tag
     */
    public static boolean checkTags(String argument) {
        return getTags().containsKey(argument);
    }

    /**
     * Check if an argument matches a block tag and add the associated materials to the list
     * 
     * @param argument
     *            The argument to check
     * @param list
     *            The list to add the associated materials to
     * @return true if the argument matches a block tag
     */
    public static boolean checkTags(String argument, Map<Object, Boolean> list) {
        for (Entry<String, Set<Material>> entry : getTags().entrySet()) {
            String tag = entry.getKey();
            Set<Material> materials = entry.getValue();

            if (argument.equals(tag)) {
                for (Material block : materials) {
                    list.put(block, false);
                }

                return true;
            }
        }

        return false;
    }

    /**
     * Check if an argument matches a block tag and add the associated materials to the list
     * 
     * @param argument
     *            The argument to check
     * @param list
     *            The list to add the associated materials to
     * @return true if the argument matches a block tag
     */
    public static boolean checkTags(String argument, List<Object> list) {
        for (Entry<String, Set<Material>> entry : getTags().entrySet()) {
            String tag = entry.getKey();
            Set<Material> materials = entry.getValue();

            if (argument.equals(tag)) {
                list.addAll(materials);
                return true;
            }
        }

        return false;
    }

    /**
     * Check if a string represents a block or entity
     * 
     * @param argument
     *            The string to check
     * @return true if the string represents a block or entity
     */
    public static boolean isBlockOrEntity(String argument) {
        boolean isBlock = false;
        if (checkTags(argument)) {
            isBlock = true;
        }
        else {
            Material material = MaterialUtils.getType(argument);
            if (material != null) {
                isBlock = true;
            }
            else if (BlockTypeUtils.hasBlockType(argument)) {
                isBlock = true;
            }
            else {
                EntityType entityType = EntityUtils.getEntityType(argument);
                if (entityType != null) {
                    isBlock = true;
                }
            }
        }
        return isBlock;
    }

    public static void loadConfiguredTags(CoreProtect plugin) {
        ALL_TAGS.clear();
        ALL_TAGS.putAll(BUILTIN_TAGS);

        final Path customTagsFile = plugin.getDataPath().resolve("custom-tags.yml");
        final YamlConfiguration configuration = new YamlConfiguration();

        configuration.addDefault("_version", 1);
        configuration.addDefault("groups.example", "dirt, stone");

        configuration.options().setHeader(List.of(
                "This file allows custom tags to be created, to be used inside include or exclude arguments inside lookup or rollback commands.",
                "",
                "You can use any item type or block name, item tags and block tags are also supported by putting a # before the name, such as #beds. See the below links for a full list of tags.",
                "https://minecraft.wiki/w/Item_tag_(Java_Edition) & https://minecraft.wiki/w/Block_tag_(Java_Edition)"
        ));

        configuration.options().copyDefaults(true);

        boolean successfullyRead = true;
        if (Files.exists(customTagsFile)) {
            try {
                configuration.load(customTagsFile.toFile());
            } catch (IOException | InvalidConfigurationException e) {
                plugin.getSLF4JLogger().warn("Failed to read custom-tags.yml", e);
                successfullyRead = false;
            }
        }

        ConfigurationSection groupsSection = configuration.getConfigurationSection("groups");
        if (groupsSection == null) {
            groupsSection = configuration.createSection("groups");
        }

        for (final String key : groupsSection.getKeys(false)) {
            final String value = groupsSection.getString(key, "");
            if ("example".equals(key) || value.isEmpty()) {
                continue;
            }

            Set<Material> materials = new HashSet<>();
            for (String split : value.split(",")) {
                split = split.trim().toLowerCase(Locale.ROOT);

                if (split.startsWith("#")) {
                    final NamespacedKey namespacedKey = NamespacedKey.fromString(split.substring(1));
                    if (namespacedKey == null) {
                        continue;
                    }

                    final Tag<Material> tag = Optional.ofNullable(plugin.getServer().getTag(Tag.REGISTRY_BLOCKS, namespacedKey, Material.class)).orElseGet(() -> plugin.getServer().getTag(Tag.REGISTRY_ITEMS, namespacedKey, Material.class));
                    if (tag == null) {
                        plugin.getSLF4JLogger().warn("Tag with name {} for custom tag {} does not exist", split.substring(1), key);
                        continue;
                    }

                    materials.addAll(tag.getValues());
                } else {
                    final NamespacedKey namespacedKey = NamespacedKey.fromString(split);
                    if (namespacedKey == null) {
                        continue;
                    }

                    final Material material = Registry.MATERIAL.get(namespacedKey);
                    if (material == null) {
                        plugin.getSLF4JLogger().warn("No material found with name {} for custom tag {}", split, key);
                        continue;
                    }

                    materials.add(material);
                }
            }

            ALL_TAGS.put("#" + key, materials);
        }

        if (successfullyRead) {
            try {
                configuration.save(customTagsFile.toFile());
            } catch (IOException e) {
                plugin.getSLF4JLogger().warn("Failed to save custom-tags.yml", e);
            }
        }

        TabHandler.materials = null; // force cached material list to be recalculated
    }
}
