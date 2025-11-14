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
    private static final String[] HELP = new String[] { "inspect", "rollback", "restore", "lookup", "purge", "teleport", "status", "params", "users", "time", "radius", "action", "include", "exclude" };
    private static final String[] PARAMS = new String[] { "user:", "time:", "radius:", "action:", "include:", "exclude:", "#container" };
    private static final String[] ACTIONS = new String[] { "block", "+block", "-block", "click", "kill", "+container", "-container", "container", "chat", "command", "+inventory", "-inventory", "inventory", "item", "+item", "-item", "sign", "session", "+session", "-session", "username" };
    private static final String[] NUMBERS = new String[] { "0", "1", "2", "3", "4", "5", "6", "7", "8", "9" };
    private static final String[] TIMES = new String[] { "w", "d", "h", "m", "s" };
    private static ArrayList<String> materials = null;

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player) || args.length == 0) {
            return null;
        }

        if (args.length == 1) {
            return getFirstLevelCompletions(sender, args[0]);
        }

        String argument0 = args[0].toLowerCase(Locale.ROOT);
        String currentArg = args[args.length - 1].toLowerCase(Locale.ROOT).trim();
        String lastArg = args.length > 1 ? args[args.length - 2].toLowerCase(Locale.ROOT).trim() : "";

        ParamState paramState = getParamState(args);

        // Handle param-specific completions
        if (isActionParam(lastArg, currentArg) && hasLookupPermission(sender)) {
            return handleActionParamCompletions(currentArg, lastArg);
        }
        else if (isUserParam(lastArg, currentArg) && hasLookupPermission(sender)) {
            return handleUserParamCompletions(currentArg, lastArg);
        }
        else if (isTimeParam(lastArg, currentArg) && hasTimePermission(sender)) {
            return handleTimeParamCompletions(currentArg, lastArg);
        }
        else if (isPageParam(lastArg, currentArg) && hasPagePermission(sender)) {
            return handlePageParamCompletions(currentArg, lastArg);
        }
        else if (isRadiusParam(lastArg, currentArg) && hasRadiusPermission(sender)) {
            return handleRadiusParamCompletions(currentArg, lastArg, argument0);
        }
        else if (isMaterialParam(lastArg, currentArg) && hasLookupPermission(sender)) {
            return handleMaterialParamCompletions(currentArg, lastArg);
        }
        else if (args.length == 2) {
            return handleSecondArgCompletions(sender, argument0, args[1], paramState);
        }
        else if (args.length == 3 && argument0.equals("purge") && sender.hasPermission("coreprotect.purge")) {
            return handlePurgeThirdArgCompletions(args[1], args[2]);
        }
        else if (hasLookupCommand(argument0, sender) && (!argument0.equals("l") && !argument0.equals("lookup") || !paramState.hasPage)) {
            return handleGenericLookupCompletions(argument0, currentArg, paramState);
        }

        return Arrays.asList("");
    }

    private List<String> getFirstLevelCompletions(CommandSender sender, String argument) {
        String arg = argument.toLowerCase(Locale.ROOT);
        List<String> completions = new ArrayList<>();

        addCompletionIfPermitted(sender, "coreprotect.help", "help", completions);
        addCompletionIfPermitted(sender, "coreprotect.inspect", "inspect", completions);
        addCompletionIfPermitted(sender, "coreprotect.rollback", "rollback", completions);
        addCompletionIfPermitted(sender, "coreprotect.restore", "restore", completions);
        addCompletionIfPermitted(sender, "coreprotect.lookup", "lookup", completions);
        addCompletionIfPermitted(sender, "coreprotect.purge", "purge", completions);
        addCompletionIfPermitted(sender, "coreprotect.reload", "reload", completions);
        addCompletionIfPermitted(sender, "coreprotect.status", "status", completions);
        addCompletionIfPermitted(sender, "coreprotect.lookup.near", "near", completions);
        addCompletionIfPermitted(sender, "coreprotect.restore", "undo", completions);

        return StringUtil.copyPartialMatches(arg, completions, new ArrayList<>(completions.size()));
    }

    private void addCompletionIfPermitted(CommandSender sender, String permission, String completion, List<String> completions) {
        if (sender.hasPermission(permission)) {
            completions.add(completion);
        }
    }

    private boolean hasLookupPermission(CommandSender sender) {
        return sender.hasPermission("coreprotect.lookup") || sender.hasPermission("coreprotect.rollback") || sender.hasPermission("coreprotect.restore");
    }

    private boolean hasTimePermission(CommandSender sender) {
        return hasLookupPermission(sender) || sender.hasPermission("coreprotect.purge");
    }

    private boolean hasRadiusPermission(CommandSender sender) {
        return hasTimePermission(sender);
    }

    private boolean hasPagePermission(CommandSender sender) {
        return sender.hasPermission("coreprotect.lookup") || sender.hasPermission("coreprotect.lookup.near") || sender.hasPermission("coreprotect.inspect");
    }

    private boolean hasLookupCommand(String cmd, CommandSender sender) {
        return (sender.hasPermission("coreprotect.lookup") && (cmd.equals("l") || cmd.equals("lookup"))) || (sender.hasPermission("coreprotect.rollback") && (cmd.equals("rollback") || cmd.equals("rb") || cmd.equals("ro"))) || (sender.hasPermission("coreprotect.restore") && (cmd.equals("restore") || cmd.equals("rs") || cmd.equals("re")));
    }

    private boolean isActionParam(String lastArg, String currentArg) {
        return lastArg.equals("a:") || lastArg.equals("action:") || currentArg.startsWith("a:") || currentArg.startsWith("action:");
    }

    private boolean isUserParam(String lastArg, String currentArg) {
        return lastArg.equals("u:") || lastArg.equals("user:") || lastArg.equals("users:") || lastArg.equals("p:") || currentArg.startsWith("u:") || currentArg.startsWith("user:") || currentArg.startsWith("users:") || currentArg.startsWith("p:");
    }

    private boolean isTimeParam(String lastArg, String currentArg) {
        return lastArg.equals("t:") || lastArg.equals("time:") || currentArg.startsWith("t:") || currentArg.startsWith("time:");
    }

    private boolean isPageParam(String lastArg, String currentArg) {
        return lastArg.equals("page:") || currentArg.startsWith("page:");
    }

    private boolean isRadiusParam(String lastArg, String currentArg) {
        return lastArg.equals("r:") || lastArg.equals("radius:") || currentArg.startsWith("r:") || currentArg.startsWith("radius:");
    }

    private boolean isMaterialParam(String lastArg, String currentArg) {
        return lastArg.equals("i:") || lastArg.equals("include:") || lastArg.equals("item:") || lastArg.equals("items:") || lastArg.equals("b:") || lastArg.equals("block:") || lastArg.equals("blocks:") || currentArg.startsWith("i:") || currentArg.startsWith("include:") || currentArg.startsWith("item:") || currentArg.startsWith("items:") || currentArg.startsWith("b:") || currentArg.startsWith("block:") || currentArg.startsWith("blocks:") || lastArg.equals("e:") || lastArg.equals("exclude:") || currentArg.startsWith("e:") || currentArg.startsWith("exclude:");
    }

    private static class ParamState {
        boolean hasUser;
        boolean hasAction;
        boolean hasInclude;
        boolean hasExclude;
        boolean hasRadius;
        boolean hasTime;
        boolean hasContainer;
        boolean hasCount;
        boolean hasPreview;
        boolean hasPage;
        boolean validContainer;
        boolean pageLookup;
    }

    private ParamState getParamState(String[] args) {
        ParamState state = new ParamState();

        if (ConfigHandler.lookupType.get(args[0]) != null && ConfigHandler.lookupPage.get(args[0]) != null) {
            state.pageLookup = true;
        }

        for (int i = 1; i < args.length; i++) {
            String arg = args[i].toLowerCase(Locale.ROOT);
            if (arg.equals("#container")) {
                state.hasContainer = true;
            }
            else if (arg.equals("#count") || arg.equals("#sum")) {
                state.hasCount = true;
            }
            else if (arg.equals("#preview")) {
                state.hasPreview = true;
            }
            else if ((!arg.contains(":") && !args[i - 1].contains(":") && args.length > (i + 1)) || arg.contains("u:") || arg.contains("user:") || arg.contains("users:") || arg.contains("p:")) {
                state.hasUser = true;
            }
            else if (arg.contains("page:")) {
                state.hasPage = true;
            }
            else if (arg.contains("a:") || arg.contains("action:")) {
                state.hasAction = true;
            }
            else if (arg.contains("i:") || arg.contains("include:") || arg.contains("item:") || arg.contains("items:") || arg.contains("b:") || arg.contains("block:") || arg.contains("blocks:")) {
                state.hasInclude = true;
            }
            else if (arg.contains("t:") || arg.contains("time:")) {
                state.hasTime = true;
            }
            else if (arg.contains("e:") || arg.contains("exclude:")) {
                state.hasExclude = true;
            }
            else if (arg.contains("r:") || arg.contains("radius:")) {
                state.hasRadius = true;
            }
        }

        if (!state.hasContainer) {
            if (ConfigHandler.lookupType.get(args[0]) != null) {
                int lookupType = ConfigHandler.lookupType.get(args[0]);
                if (lookupType == 1) {
                    state.validContainer = true;
                }
                else if (lookupType == 5) {
                    if (ConfigHandler.lookupUlist.get(args[0]).contains("#container")) {
                        state.validContainer = true;
                    }
                }
            }
        }

        return state;
    }

    private List<String> handleActionParamCompletions(String currentArg, String lastArg) {
        String arg = "";
        String filter = lastArg;

        if (currentArg.contains(":")) {
            String[] split = currentArg.split(":", 2);
            filter = split[0] + ":";
            if (split.length > 1) {
                arg = split[1];
            }
        }

        List<String> completions = new ArrayList<>(Arrays.asList(ACTIONS));
        for (int index = 0; index < completions.size(); index++) {
            completions.set(index, filter + completions.get(index));
        }
        return StringUtil.copyPartialMatches(filter + arg, completions, new ArrayList<>(completions.size()));
    }

    private List<String> handleUserParamCompletions(String currentArg, String lastArg) {
        if (lastArg.equals("u:") || lastArg.equals("user:") || lastArg.equals("users:") || lastArg.equals("p:")) {
            return null;
        }

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

    private List<String> handleTimeParamCompletions(String currentArg, String lastArg) {
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

    private List<String> handlePageParamCompletions(String currentArg, String lastArg) {
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

        return null;
    }

    private List<String> handleRadiusParamCompletions(String currentArg, String lastArg, String baseCommand) {
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

        if (!baseCommand.equals("purge") && arg.chars().allMatch(Character::isDigit)) {
            List<String> completions = new ArrayList<>(Arrays.asList(NUMBERS));
            if (arg.length() < 2) {
                for (int index = 0; index < completions.size(); index++) {
                    completions.set(index, filter + arg + completions.get(index));
                }
            }
            return StringUtil.copyPartialMatches(filter + arg, completions, new ArrayList<>(completions.size()));
        }
        else if (baseCommand.equals("purge") || arg.startsWith("#")) {
            ArrayList<String> params = new ArrayList<>();
            params.add("#global");
            if (!baseCommand.equals("purge") && Bukkit.getServer().getPluginManager().getPlugin("WorldEdit") != null) {
                params.add("#worldedit");
            }
            List<World> worlds = Bukkit.getServer().getWorlds();
            for (World world : worlds) {
                params.add("#" + world.getName());
            }
            List<String> completions = new ArrayList<>(params);
            for (int index = 0; index < completions.size(); index++) {
                completions.set(index, filter + completions.get(index));
            }
            return StringUtil.copyPartialMatches(filter + arg, completions, new ArrayList<>(completions.size()));
        }

        return null;
    }

    private List<String> handleMaterialParamCompletions(String currentArg, String lastArg) {
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

        initializeMaterialsIfNeeded();

        List<String> completions = new ArrayList<>(materials);
        for (int index = 0; index < completions.size(); index++) {
            completions.set(index, filter + completions.get(index));
        }
        return StringUtil.copyPartialMatches(filter + arg, completions, new ArrayList<>(completions.size()));
    }

    private void initializeMaterialsIfNeeded() {
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
            for (String tag : CommandParser.getTags().keySet()) {
                materialList.add(tag);
            }

            materials = new ArrayList<>(materialList);
        }
    }

    private List<String> handleSecondArgCompletions(CommandSender sender, String cmd0, String cmd1, ParamState paramState) {
        String argument0 = cmd0.toLowerCase(Locale.ROOT);
        String argument1 = cmd1.toLowerCase(Locale.ROOT);

        if (argument0.equals("help") && sender.hasPermission("coreprotect.help")) {
            List<String> completions = new ArrayList<>(Arrays.asList(HELP));
            return StringUtil.copyPartialMatches(argument1, completions, new ArrayList<>(completions.size()));
        }
        else if (argument0.equals("purge") && sender.hasPermission("coreprotect.purge")) {
            List<String> completions = new ArrayList<>(Arrays.asList("t:", "r:", "i:"));
            return StringUtil.copyPartialMatches(argument1, completions, new ArrayList<>(completions.size()));
        }
        else if (hasLookupCommand(argument0, sender)) {
            List<String> completions = new ArrayList<>(filterParams(true, argument0, argument1, paramState));
            completions.addAll(Bukkit.getOnlinePlayers().stream().filter(player -> player.getName().toLowerCase(Locale.ROOT).startsWith(argument1)).map(Player::getName).collect(Collectors.toList()));
            return StringUtil.copyPartialMatches(argument1, completions, new ArrayList<>(completions.size()));
        }

        return null;
    }

    private List<String> handlePurgeThirdArgCompletions(String arg1, String arg2) {
        String argument1 = arg1.toLowerCase(Locale.ROOT);
        String argument2 = arg2.toLowerCase(Locale.ROOT);

        if (argument1.startsWith("t:")) {
            List<String> completions = new ArrayList<>(Arrays.asList("r:", "i:"));
            return StringUtil.copyPartialMatches(argument2, completions, new ArrayList<>(completions.size()));
        }
        else if (argument1.startsWith("r:") || argument1.startsWith("i:")) {
            List<String> completions = new ArrayList<>(Arrays.asList("t:"));
            return StringUtil.copyPartialMatches(argument2, completions, new ArrayList<>(completions.size()));
        }
        return Arrays.asList("");
    }

    private List<String> handleGenericLookupCompletions(String cmd, String currentArg, ParamState paramState) {
        ArrayList<String> params = filterParams(false, cmd, currentArg, paramState);
        List<String> completions = new ArrayList<>(params);
        return StringUtil.copyPartialMatches(currentArg, completions, new ArrayList<>(completions.size()));
    }

    private ArrayList<String> filterParams(boolean firstParam, String lastArgument, String argument, ParamState state) {
        ArrayList<String> params = new ArrayList<>();
        for (String param : PARAMS) {
            if (param.equals("user:") && !state.hasUser) {
                params.add(param);
            }
            else if (param.equals("action:") && !state.hasAction) {
                params.add(param);
            }
            else if (param.equals("include:") && !state.hasInclude) {
                params.add(param);
            }
            else if (param.equals("exclude:") && !state.hasExclude) {
                params.add(param);
            }
            else if (param.equals("radius:") && !state.hasRadius) {
                params.add(param);
            }
            else if (param.equals("time:") && !state.hasTime) {
                params.add(param);
            }
            else if (param.equals("#container") && !state.hasContainer && !state.hasRadius && state.validContainer) {
                params.add(param);
            }
        }
        if (firstParam && state.pageLookup && (lastArgument.equals("l") || lastArgument.equals("lookup"))) {
            params.add("page:");
        }
        else if (!firstParam && argument.startsWith("#")) {
            if (!state.hasCount) {
                params.add("#count");
            }
            if (!state.hasPreview) {
                params.add("#preview");
            }
        }

        return params;
    }
}
