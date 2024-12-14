package net.coreprotect.command;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import net.coreprotect.config.ConfigHandler;

public class TabHandler implements TabCompleter {

    // private static String[] COMMANDS = new String[] { "help", "inspect", "rollback", "restore", "lookup", "purge", "reload", "status", "near", "undo" }; // max 10!
    private static String[] HELP = new String[] { "inspect", "rollback", "restore", "lookup", "purge", "teleport", "status", "params", "users", "time", "radius", "action", "include", "exclude" };
    private static String[] PARAMS = new String[] { "user:", "time:", "radius:", "action:", "include:", "exclude:", "#container" };
    private static String[] ACTIONS = new String[] { "block", "+block", "-block", "click", "kill", "+container", "-container", "container", "chat", "command", "+inventory", "-inventory", "inventory", "item", "+item", "-item", "sign", "session", "+session", "-session", "username" };
    private static String[] NUMBERS = new String[] { "0", "1", "2", "3", "4", "5", "6", "7", "8", "9" };
    private static String[] TIMES = new String[] { "w", "d", "h", "m", "s" };
    private static ArrayList<String> materials = null;

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player) || args.length == 0) {
            return null;
        }
        if (args.length == 1) {
            String argument = args[0].toLowerCase(Locale.ROOT);
            List<String> completions = new ArrayList<>();
            if (sender.hasPermission("coreprotect.help")) {
                completions.add("help");
            }
            if (sender.hasPermission("coreprotect.inspect")) {
                completions.add("inspect");
            }
            if (sender.hasPermission("coreprotect.rollback")) {
                completions.add("rollback");
            }
            if (sender.hasPermission("coreprotect.restore")) {
                completions.add("restore");
            }
            if (sender.hasPermission("coreprotect.lookup")) {
                completions.add("lookup");
            }
            if (sender.hasPermission("coreprotect.purge")) {
                completions.add("purge");
            }
            if (sender.hasPermission("coreprotect.reload")) {
                completions.add("reload");
            }
            if (sender.hasPermission("coreprotect.status")) {
                completions.add("status");
            }
            if (sender.hasPermission("coreprotect.lookup.near")) {
                completions.add("near");
            }
            if (sender.hasPermission("coreprotect.restore")) {
                completions.add("undo");
            }

            return StringUtil.copyPartialMatches(argument, completions, new ArrayList<>(completions.size()));
        }
        else if (args.length > 1) {
            String argument0 = args[0].toLowerCase(Locale.ROOT);
            String argument1 = args[1].toLowerCase(Locale.ROOT);
            String currentArg = args[args.length - 1].toLowerCase(Locale.ROOT).trim();
            String lastArg = args[args.length - 2].toLowerCase(Locale.ROOT).trim();

            boolean hasUser = false;
            boolean hasAction = false;
            boolean hasInclude = false;
            boolean hasExclude = false;
            boolean hasRadius = false;
            boolean hasTime = false;
            boolean hasContainer = false;
            boolean hasCount = false;
            boolean hasPreview = false;
            boolean hasPage = false;
            boolean validContainer = false;
            boolean pageLookup = false;

            if (ConfigHandler.lookupType.get(sender.getName()) != null && ConfigHandler.lookupPage.get(sender.getName()) != null) {
                pageLookup = true;
            }

            for (int i = 1; i < args.length; i++) {
                String arg = args[i].toLowerCase(Locale.ROOT);
                if (arg.equals("#container")) {
                    hasContainer = true;
                }
                else if (arg.equals("#count") || arg.equals("#sum")) {
                    hasCount = true;
                }
                else if (arg.equals("#preview")) {
                    hasPreview = true;
                }
                else if ((!arg.contains(":") && !args[i - 1].contains(":") && args.length > (i + 1)) || arg.contains("u:") || arg.contains("user:") || arg.contains("users:") || arg.contains("p:")) {
                    hasUser = true;
                }
                else if (arg.contains("page:")) {
                    hasPage = true;
                }
                else if (arg.contains("a:") || arg.contains("action:")) {
                    hasAction = true;
                }
                else if (arg.contains("i:") || arg.contains("include:") || arg.contains("item:") || arg.contains("items:") || arg.contains("b:") || arg.contains("block:") || arg.contains("blocks:")) {
                    hasInclude = true;
                }
                else if (arg.contains("t:") || arg.contains("time:")) {
                    hasTime = true;
                }
                else if (arg.contains("e:") || arg.contains("exclude:")) {
                    hasExclude = true;
                }
                else if (arg.contains("r:") || arg.contains("radius:")) {
                    hasRadius = true;
                }
            }

            if (!hasContainer) {
                if (ConfigHandler.lookupType.get(sender.getName()) != null) {
                    int lookupType = ConfigHandler.lookupType.get(sender.getName());
                    if (lookupType == 1) {
                        validContainer = true;
                    }
                    else if (lookupType == 5) {
                        if (ConfigHandler.lookupUlist.get(sender.getName()).contains("#container")) {
                            validContainer = true;
                        }
                    }
                }
            }

            if ((lastArg.equals("a:") || lastArg.equals("action:")) && (sender.hasPermission("coreprotect.lookup") || sender.hasPermission("coreprotect.rollback") || sender.hasPermission("coreprotect.restore"))) {
                List<String> completions = new ArrayList<>(Arrays.asList(ACTIONS));
                return StringUtil.copyPartialMatches(currentArg, completions, new ArrayList<>(completions.size()));
            }
            else if ((currentArg.startsWith("a:") || currentArg.startsWith("action:")) && (sender.hasPermission("coreprotect.lookup") || sender.hasPermission("coreprotect.rollback") || sender.hasPermission("coreprotect.restore"))) {
                String arg = "";
                String[] split = currentArg.split(":", 2);
                String filter = split[0] + ":";
                if (split.length > 1) {
                    arg = split[1];
                }

                List<String> completions = new ArrayList<>(Arrays.asList(ACTIONS));
                for (int index = 0; index < completions.size(); index++) {
                    completions.set(index, filter + completions.get(index));
                }
                return StringUtil.copyPartialMatches(filter + arg, completions, new ArrayList<>(completions.size()));
            }
            else if ((lastArg.equals("u:") || lastArg.equals("user:") || lastArg.equals("users:") || lastArg.equals("p:")) && (sender.hasPermission("coreprotect.lookup") || sender.hasPermission("coreprotect.rollback") || sender.hasPermission("coreprotect.restore"))) {
                return null;
            }
            else if ((currentArg.startsWith("u:") || currentArg.startsWith("user:") || currentArg.startsWith("users:") || currentArg.startsWith("p:")) && (sender.hasPermission("coreprotect.lookup") || sender.hasPermission("coreprotect.rollback") || sender.hasPermission("coreprotect.restore"))) {
                String arg = "";
                String[] split = currentArg.split(":", 2);
                String filter = split[0] + ":";
                if (split.length > 1) {
                    arg = split[1];
                }

                List<String> completions = Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList());
                for (int index = 0; index < completions.size(); index++) {
                    completions.set(index, filter + completions.get(index));
                }

                return StringUtil.copyPartialMatches(filter + arg, completions, new ArrayList<>(completions.size()));
            }
            else if ((lastArg.equals("t:") || lastArg.equals("time:") || currentArg.startsWith("t:") || currentArg.startsWith("time:")) && (sender.hasPermission("coreprotect.lookup") || sender.hasPermission("coreprotect.rollback") || sender.hasPermission("coreprotect.restore") || sender.hasPermission("coreprotect.purge"))) {
                String filter = lastArg;
                String arg = "";
                if (currentArg.contains(":")) {
                    String[] split = currentArg.split(":", 2);
                    filter = split[0] + ":";
                    if (split.length > 1) {
                        arg = split[1];
                    }
                }
                else {
                    filter = "";
                    arg = currentArg;
                }

                List<String> completions = new ArrayList<>();
                if (arg.chars().allMatch(Character::isDigit)) {
                    boolean addNumbers = true;
                    if (currentArg.length() > 0) {
                        char lastChar = currentArg.charAt(currentArg.length() - 1);
                        if (Character.isDigit(lastChar)) {
                            completions.addAll(Arrays.asList(TIMES));
                            addNumbers = false;
                        }
                    }
                    if (addNumbers) {
                        completions.addAll(Arrays.asList(NUMBERS));
                    }

                }

                completions = new ArrayList<>(completions);
                for (int index = 0; index < completions.size(); index++) {
                    completions.set(index, filter + arg + completions.get(index));
                }

                return StringUtil.copyPartialMatches(filter + arg, completions, new ArrayList<>(completions.size()));
            }
            else if ((lastArg.equals("page:") || currentArg.startsWith("page:")) && (sender.hasPermission("coreprotect.lookup") || sender.hasPermission("coreprotect.lookup.near") || sender.hasPermission("coreprotect.inspect"))) {
                String filter = lastArg;
                String arg = "";
                if (currentArg.contains(":")) {
                    String[] split = currentArg.split(":", 2);
                    filter = split[0] + ":";
                    if (split.length > 1) {
                        arg = split[1];
                    }
                }
                else {
                    filter = "";
                    arg = currentArg;
                }

                if (arg.chars().allMatch(Character::isDigit)) {
                    List<String> completions = new ArrayList<>(Arrays.asList(NUMBERS));
                    if (arg.length() < 1) {
                        for (int index = 0; index < completions.size(); index++) {
                            completions.set(index, filter + arg + completions.get(index));
                        }
                        if (arg.length() == 0) {
                            completions.remove(0);
                        }
                    }
                    return StringUtil.copyPartialMatches(filter + arg, completions, new ArrayList<>(completions.size()));
                }
            }
            else if ((lastArg.equals("r:") || lastArg.equals("radius:") || currentArg.startsWith("r:") || currentArg.startsWith("radius:")) && (sender.hasPermission("coreprotect.lookup") || sender.hasPermission("coreprotect.rollback") || sender.hasPermission("coreprotect.restore") || sender.hasPermission("coreprotect.purge"))) {
                String filter = lastArg;
                String arg = "";
                if (currentArg.contains(":")) {
                    String[] split = currentArg.split(":", 2);
                    filter = split[0] + ":";
                    if (split.length > 1) {
                        arg = split[1];
                    }
                }
                else {
                    filter = "";
                    arg = currentArg;
                }

                if (!argument0.equals("purge") && arg.chars().allMatch(Character::isDigit)) {
                    List<String> completions = new ArrayList<>(Arrays.asList(NUMBERS));
                    if (arg.length() < 2) {
                        for (int index = 0; index < completions.size(); index++) {
                            completions.set(index, filter + arg + completions.get(index));
                        }
                    }
                    return StringUtil.copyPartialMatches(filter + arg, completions, new ArrayList<>(completions.size()));
                }
                else if (argument0.equals("purge") || arg.startsWith("#")) {
                    ArrayList<String> params = new ArrayList<>();
                    params.add("#global");
                    if (!argument0.equals("purge") && sender.getServer().getPluginManager().getPlugin("WorldEdit") != null) {
                        params.add("#worldedit");
                    }
                    List<World> worlds = sender.getServer().getWorlds();
                    for (World world : worlds) {
                        params.add("#" + world.getName());
                    }
                    List<String> completions = new ArrayList<>(params);
                    for (int index = 0; index < completions.size(); index++) {
                        completions.set(index, filter + completions.get(index));
                    }
                    return StringUtil.copyPartialMatches(filter + arg, completions, new ArrayList<>(completions.size()));
                }
            }
            else if ((sender.hasPermission("coreprotect.lookup") || sender.hasPermission("coreprotect.rollback") || sender.hasPermission("coreprotect.restore")) && (lastArg.equals("i:") || lastArg.equals("include:") || lastArg.equals("item:") || lastArg.equals("items:") || lastArg.equals("b:") || lastArg.equals("block:") || lastArg.equals("blocks:") || currentArg.startsWith("i:") || currentArg.startsWith("include:") || currentArg.startsWith("item:") || currentArg.startsWith("items:") || currentArg.startsWith("b:") || currentArg.startsWith("block:") || currentArg.startsWith("blocks:") || lastArg.equals("e:") || lastArg.equals("exclude:") || currentArg.startsWith("e:") || currentArg.startsWith("exclude:"))) {
                String filter = lastArg;
                String arg = "";
                if (currentArg.contains(":")) {
                    String[] split = currentArg.split(":", 2);
                    filter = split[0] + ":";
                    if (split.length > 1) {
                        arg = split[1];
                    }
                }
                else {
                    filter = "";
                    arg = currentArg;
                }

                if (materials == null) {
                    List<Material> addList = Arrays.asList(Material.ARMOR_STAND);
                    List<Material> excludeList = Arrays.asList();
                    Set<String> materialList = new HashSet<>();

                    Material[] materialValues = Material.values();
                    for (Material material : materialValues) {
                        if (material.isBlock() || material.isItem()) {
                            materialList.add(material.name().toLowerCase(Locale.ROOT));
                        }
                    }
                    for (Material exclude : excludeList) {
                        materialList.remove(exclude.name().toLowerCase(Locale.ROOT));
                    }
                    for (Material add : addList) {
                        materialList.add(add.name().toLowerCase(Locale.ROOT));
                    }

                    // add custom tags
                    for (String tag : CommandHandler.getTags().keySet()) {
                        materialList.add(tag);
                    }

                    materials = new ArrayList<>(materialList);
                }

                List<String> completions = new ArrayList<>(materials);
                for (int index = 0; index < completions.size(); index++) {
                    completions.set(index, filter + completions.get(index));
                }
                return StringUtil.copyPartialMatches(filter + arg, completions, new ArrayList<>(completions.size()));

            }
            else if (args.length == 2) {
                if (argument0.equals("help") && sender.hasPermission("coreprotect.help")) {
                    List<String> completions = new ArrayList<>(Arrays.asList(HELP));
                    return StringUtil.copyPartialMatches(argument1, completions, new ArrayList<>(completions.size()));
                }
                else if (argument0.equals("purge") && sender.hasPermission("coreprotect.purge")) {
                    List<String> completions = new ArrayList<>(Arrays.asList("t:", "r:", "i:"));
                    return StringUtil.copyPartialMatches(argument1, completions, new ArrayList<>(completions.size()));
                }
                else if ((sender.hasPermission("coreprotect.lookup") && (argument0.equals("l") || argument0.equals("lookup"))) || (sender.hasPermission("coreprotect.rollback") && (argument0.equals("rollback") || argument0.equals("rb") || argument0.equals("ro"))) || (sender.hasPermission("coreprotect.restore") && (argument0.equals("restore") || argument0.equals("rs") || argument0.equals("re")))) {
                    List<String> completions = new ArrayList<>(filterParams(true, argument0, argument1, hasUser, hasAction, hasInclude, hasExclude, hasRadius, hasTime, hasContainer, hasCount, hasPreview, pageLookup, validContainer));
                    completions.addAll(Bukkit.getOnlinePlayers().stream().filter(player -> player.getName().toLowerCase(Locale.ROOT).startsWith(argument1)).map(Player::getName).collect(Collectors.toList()));
                    return StringUtil.copyPartialMatches(argument1, completions, new ArrayList<>(completions.size()));
                }
            }
            else if (args.length == 3 && argument0.equals("purge") && sender.hasPermission("coreprotect.purge")) {
                if (argument1.startsWith("t:")) {
                    List<String> completions = new ArrayList<>(Arrays.asList("r:", "i:"));
                    return StringUtil.copyPartialMatches(args[2].toLowerCase(Locale.ROOT), completions, new ArrayList<>(completions.size()));
                }
                else if (argument1.startsWith("r:") || argument1.startsWith("i:")) {
                    List<String> completions = new ArrayList<>(Arrays.asList("t:"));
                    return StringUtil.copyPartialMatches(args[2].toLowerCase(Locale.ROOT), completions, new ArrayList<>(completions.size()));
                }
                return Arrays.asList("");
            }
            else if ((sender.hasPermission("coreprotect.lookup") && (argument0.equals("l") || argument0.equals("lookup"))) || (sender.hasPermission("coreprotect.rollback") && (argument0.equals("rollback") || argument0.equals("rb") || argument0.equals("ro"))) || (sender.hasPermission("coreprotect.restore") && (argument0.equals("restore") || argument0.equals("rs") || argument0.equals("re")))) {
                if ((!argument0.equals("l") && !argument0.equals("lookup")) || !hasPage) {
                    ArrayList<String> params = filterParams(false, argument0, currentArg, hasUser, hasAction, hasInclude, hasExclude, hasRadius, hasTime, hasContainer, hasCount, hasPreview, pageLookup, validContainer);
                    List<String> completions = new ArrayList<>(params);
                    return StringUtil.copyPartialMatches(currentArg, completions, new ArrayList<>(completions.size()));
                }
            }
        }

        return Arrays.asList("");
    }

    private ArrayList<String> filterParams(boolean firstParam, String lastArgument, String argument, boolean hasUser, boolean hasAction, boolean hasInclude, boolean hasExclude, boolean hasRadius, boolean hasTime, boolean hasContainer, boolean hasCount, boolean hasPreview, boolean pageLookup, boolean validContainer) {
        ArrayList<String> params = new ArrayList<>();
        for (String param : PARAMS) {
            if (param.equals("user:") && !hasUser) {
                params.add(param);
            }
            else if (param.equals("action:") && !hasAction) {
                params.add(param);
            }
            else if (param.equals("include:") && !hasInclude) {
                params.add(param);
            }
            else if (param.equals("exclude:") && !hasExclude) {
                params.add(param);
            }
            else if (param.equals("radius:") && !hasRadius) {
                params.add(param);
            }
            else if (param.equals("time:") && !hasTime) {
                params.add(param);
            }
            else if (param.equals("#container") && !hasContainer && !hasRadius && validContainer) {
                params.add(param);
            }
        }
        if (firstParam && pageLookup && (lastArgument.equals("l") || lastArgument.equals("lookup"))) {
            params.add("page:");
        }
        else if (!firstParam && argument.startsWith("#")) {
            if (!hasCount) {
                params.add("#count");
            }
            if (!hasPreview) {
                params.add("#preview");
            }
        }

        return params;
    }
}
