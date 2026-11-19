package net.coreprotect.command;

import java.util.Locale;

import org.bukkit.command.CommandSender;

import net.coreprotect.command.parser.LocationParser;
import net.coreprotect.language.Phrase;
import net.coreprotect.language.Selector;
import net.coreprotect.utility.Chat;
import net.coreprotect.utility.Color;

public class HelpCommand {
    protected static void runCommand(CommandSender player, boolean permission, String label, String[] args) {
        int resultc = args.length;
        String root = "/" + label;
        if (permission) {
            if (resultc > 1) {
                String helpcommand_original = args[1];
                String helpcommand = args[1].toLowerCase(Locale.ROOT);
                helpcommand = helpcommand.replaceAll("[^a-zA-Z]", "");
                Chat.sendMessage(player, Color.WHITE + "----- " + Color.DARK_AQUA + Phrase.build(Phrase.HELP_HEADER, "CoreProtect") + Color.WHITE + " -----");
                if (helpcommand.equals("help")) {
                    Chat.sendMessage(player, Color.DARK_AQUA + root + " help " + Color.WHITE + "- " + Phrase.build(Phrase.HELP_LIST));
                }
                else if (helpcommand.equals("inspect") || helpcommand.equals("inspector") || helpcommand.equals("in") || (helpcommand.equals("i") && !helpcommand_original.contains(":"))) {
                    Chat.sendMessage(player, Color.DARK_AQUA + Phrase.build(Phrase.HELP_INSPECT_1));
                    Chat.sendMessage(player, "* " + Phrase.build(Phrase.HELP_INSPECT_2));
                    Chat.sendMessage(player, "* " + Phrase.build(Phrase.HELP_INSPECT_3));
                    Chat.sendMessage(player, "* " + Phrase.build(Phrase.HELP_INSPECT_4));
                    Chat.sendMessage(player, "* " + Phrase.build(Phrase.HELP_INSPECT_5));
                    Chat.sendMessage(player, "* " + Phrase.build(Phrase.HELP_INSPECT_6));
                    Chat.sendMessage(player, Color.GREY + Color.ITALIC + Phrase.build(Phrase.HELP_INSPECT_7).replace("/co i", root + " i"));
                }
                else if (helpcommand.equals("params") || helpcommand.equals("param") || helpcommand.equals("parameters") || helpcommand.equals("parameter")) {
                    sendParamsHelp(player, root, "lookup", Selector.FIRST, true);
                }
                else if (helpcommand.equals("rollback") || helpcommand.equals("rollbacks") || helpcommand.equals("rb") || helpcommand.equals("ro")) {
                    sendParamsHelp(player, root, "rollback", Selector.SECOND, false);
                }
                else if (helpcommand.equals("restore") || helpcommand.equals("restores") || helpcommand.equals("re") || helpcommand.equals("rs")) {
                    sendParamsHelp(player, root, "restore", Selector.THIRD, false);
                }
                else if (helpcommand.equals("lookup") || helpcommand.equals("lookups") || helpcommand.equals("l")) {
                    Chat.sendMessage(player, Color.DARK_AQUA + root + " lookup <params>");
                    Chat.sendMessage(player, Color.DARK_AQUA + root + " l <params> " + Color.WHITE + "- " + Phrase.build(Phrase.HELP_LOOKUP_1));
                    Chat.sendMessage(player, Color.DARK_AQUA + root + " lookup <page[:rows]> " + Color.WHITE + "- " + Phrase.build(Phrase.HELP_LOOKUP_2));
                    Chat.sendMessage(player, Color.GREY + Color.ITALIC + Phrase.build(Phrase.HELP_PARAMETER, root + " help params"));
                }
                else if (helpcommand.equals("page")) {
                    Chat.sendMessage(player, Color.DARK_AQUA + root + " page <page[:rows]> " + Color.WHITE + "- " + Phrase.build(Phrase.HELP_LOOKUP_2));
                }
                else if (helpcommand.equals("near")) {
                    Chat.sendMessage(player, Color.DARK_AQUA + root + " near " + Color.WHITE + "- " + Phrase.build(Phrase.HELP_NEAR_COMMAND));
                }
                else if (helpcommand.equals("purge") || helpcommand.equals("purges")) {
                    Chat.sendMessage(player, Color.DARK_AQUA + root + " purge " + Color.GREY + "<params> " + Color.WHITE + "- " + Phrase.build(Phrase.HELP_PURGE_1));
                    Chat.sendMessage(player, Color.DARK_AQUA + "| " + Color.GREY + "t:<time> " + Color.WHITE + "- " + Phrase.build(Phrase.HELP_PURGE_3));
                    Chat.sendMessage(player, Color.DARK_AQUA + "| " + Color.GREY + "r:<world/radius> " + Color.WHITE + "- " + Phrase.build(Phrase.HELP_PURGE_4));
                    Chat.sendMessage(player, Color.DARK_AQUA + "| " + Color.GREY + "i:<include> " + Color.WHITE + "- " + Phrase.build(Phrase.HELP_PURGE_5));
                    Chat.sendMessage(player, Color.DARK_AQUA + "| " + Color.GREY + "e:<exclude> " + Color.WHITE + "- " + Phrase.build(Phrase.HELP_PURGE_6));
                    Chat.sendMessage(player, Color.DARK_AQUA + "| " + Color.GREY + "a:kill " + Color.WHITE + "- " + Phrase.build(Phrase.HELP_PURGE_7));
                    Chat.sendMessage(player, Color.GREY + Color.ITALIC + Phrase.build(Phrase.HELP_PURGE_2, root + " purge t:30d"));
                }
                else if (helpcommand.equals("reload")) {
                    Chat.sendMessage(player, Color.DARK_AQUA + root + " reload " + Color.WHITE + "- " + Phrase.build(Phrase.HELP_RELOAD_COMMAND));
                }
                else if (helpcommand.equals("status") || helpcommand.equals("stats") || helpcommand.equals("version")) {
                    Chat.sendMessage(player, Color.DARK_AQUA + root + " status " + Color.WHITE + "- " + Phrase.build(Phrase.HELP_STATUS));
                }
                else if (helpcommand.equals("teleport") || helpcommand.equals("tp")) {
                    Chat.sendMessage(player, Color.DARK_AQUA + root + " teleport <world> <x> <y> <z> " + Color.WHITE + "- " + Phrase.build(Phrase.HELP_TELEPORT));
                }
                else if (LocationParser.stripCoordinatePrefix(helpcommand + ":") != null) {
                    Chat.sendMessage(player, Color.DARK_AQUA + root + " lookup r:<radius> c:<x,y,z> " + Color.WHITE + "- " + Phrase.build(Phrase.HELP_COORDINATES));
                }
                else if (helpcommand.equals("u") || helpcommand.equals("user") || helpcommand.equals("users") || helpcommand.equals("uuser") || helpcommand.equals("uusers")) {
                    Chat.sendMessage(player, Color.DARK_AQUA + root + " lookup u:<users> " + Color.WHITE + "- " + Phrase.build(Phrase.HELP_USER_1));
                    Chat.sendMessage(player, Color.GREY + Color.ITALIC + Phrase.build(Phrase.HELP_USER_2));
                }
                else if (helpcommand.equals("t") || helpcommand.equals("time") || helpcommand.equals("ttime")) {
                    Chat.sendMessage(player, Color.DARK_AQUA + root + " lookup t:<time> " + Color.WHITE + "- " + Phrase.build(Phrase.HELP_TIME_1));
                    Chat.sendMessage(player, Color.GREY + Color.ITALIC + Phrase.build(Phrase.HELP_TIME_2));
                }
                else if (helpcommand.equals("r") || helpcommand.equals("radius") || helpcommand.equals("rradius")) {
                    Chat.sendMessage(player, Color.DARK_AQUA + root + " lookup r:<radius> " + Color.WHITE + "- " + Phrase.build(Phrase.HELP_RADIUS_1));
                    Chat.sendMessage(player, Color.GREY + Color.ITALIC + Phrase.build(Phrase.HELP_RADIUS_2));
                }
                else if (helpcommand.equals("a") || helpcommand.equals("action") || helpcommand.equals("actions") || helpcommand.equals("aaction")) {
                    Chat.sendMessage(player, Color.DARK_AQUA + root + " lookup a:<action> " + Color.WHITE + "- " + Phrase.build(Phrase.HELP_ACTION_1));
                    Chat.sendMessage(player, Color.GREY + Color.ITALIC + Phrase.build(Phrase.HELP_ACTION_2));
                }
                else if (helpcommand.equals("f") || helpcommand.equals("filter") || helpcommand.equals("ffilter")) {
                    Chat.sendMessage(player, Color.DARK_AQUA + root + " lookup a:<chat|command|sign> f:<filter> " + Color.WHITE + "- " + Phrase.build(Phrase.HELP_FILTER_1));
                    Chat.sendMessage(player, Color.GREY + Color.ITALIC + Phrase.build(Phrase.HELP_FILTER_2));
                }
                else if (helpcommand.equals("i") || helpcommand.equals("include") || helpcommand.equals("iinclude") || helpcommand.equals("b") || helpcommand.equals("block") || helpcommand.equals("blocks") || helpcommand.equals("bblock") || helpcommand.equals("bblocks")) {
                    Chat.sendMessage(player, Color.DARK_AQUA + root + " lookup i:<include> " + Color.WHITE + "- " + Phrase.build(Phrase.HELP_INCLUDE_1));
                    Chat.sendMessage(player, Color.GREY + Color.ITALIC + Phrase.build(Phrase.HELP_INCLUDE_2));
                    Chat.sendMessage(player, Color.GREY + Color.ITALIC + Phrase.build(Phrase.LINK_WIKI_BLOCK, "https://coreprotect.net/wiki-blocks"));
                    Chat.sendMessage(player, Color.GREY + Color.ITALIC + Phrase.build(Phrase.LINK_WIKI_ENTITY, "https://coreprotect.net/wiki-entities"));
                }
                else if (helpcommand.equals("e") || helpcommand.equals("exclude") || helpcommand.equals("eexclude")) {
                    Chat.sendMessage(player, Color.DARK_AQUA + root + " lookup e:<exclude> " + Color.WHITE + "- " + Phrase.build(Phrase.HELP_EXCLUDE_1));
                    Chat.sendMessage(player, Color.GREY + Color.ITALIC + Phrase.build(Phrase.HELP_EXCLUDE_2));
                    Chat.sendMessage(player, Color.GREY + Color.ITALIC + Phrase.build(Phrase.LINK_WIKI_BLOCK, "https://coreprotect.net/wiki-blocks"));
                }
                else {
                    Chat.sendMessage(player, Color.WHITE + Phrase.build(Phrase.HELP_NO_INFO, Color.WHITE, root + " help " + helpcommand_original));
                }
            }
            else {
                Chat.sendMessage(player, Color.WHITE + "----- " + Color.DARK_AQUA + Phrase.build(Phrase.HELP_HEADER, "CoreProtect") + Color.WHITE + " -----");
                Chat.sendMessage(player, Color.DARK_AQUA + root + " help " + Color.GREY + "<command> " + Color.WHITE + "- " + Phrase.build(Phrase.HELP_COMMAND));
                Chat.sendMessage(player, Color.DARK_AQUA + root + " " + Color.GREY + "inspect " + Color.WHITE + "- " + Phrase.build(Phrase.HELP_INSPECT_COMMAND));
                Chat.sendMessage(player, Color.DARK_AQUA + root + " " + Color.GREY + "rollback " + Color.DARK_AQUA + "<params> " + Color.WHITE + "- " + Phrase.build(Phrase.HELP_ROLLBACK_COMMAND));
                Chat.sendMessage(player, Color.DARK_AQUA + root + " " + Color.GREY + "restore " + Color.DARK_AQUA + "<params> " + Color.WHITE + "- " + Phrase.build(Phrase.HELP_RESTORE_COMMAND));
                Chat.sendMessage(player, Color.DARK_AQUA + root + " " + Color.GREY + "lookup " + Color.DARK_AQUA + "<params> " + Color.WHITE + "- " + Phrase.build(Phrase.HELP_LOOKUP_COMMAND));
                Chat.sendMessage(player, Color.DARK_AQUA + root + " " + Color.GREY + "purge " + Color.DARK_AQUA + "<params> " + Color.WHITE + "- " + Phrase.build(Phrase.HELP_PURGE_COMMAND));
                Chat.sendMessage(player, Color.DARK_AQUA + root + " " + Color.GREY + "reload " + Color.WHITE + "- " + Phrase.build(Phrase.HELP_RELOAD_COMMAND));
                Chat.sendMessage(player, Color.DARK_AQUA + root + " " + Color.GREY + "status " + Color.WHITE + "- " + Phrase.build(Phrase.HELP_STATUS_COMMAND));
            }
        }
        else {
            Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.NO_PERMISSION));
        }
    }

    private static void sendParamsHelp(CommandSender player, String root, String command, String selector, boolean includeFilter) {
        Chat.sendMessage(player, Color.DARK_AQUA + root + " " + command + " " + Color.GREY + "<params> " + Color.WHITE + "- " + Phrase.build(Phrase.HELP_PARAMS_1, selector));
        Chat.sendMessage(player, Color.DARK_AQUA + "| " + Color.GREY + "u:<users> " + Color.WHITE + "- " + Phrase.build(Phrase.HELP_PARAMS_2, selector));
        Chat.sendMessage(player, Color.DARK_AQUA + "| " + Color.GREY + "t:<time> " + Color.WHITE + "- " + Phrase.build(Phrase.HELP_PARAMS_3, selector));
        Chat.sendMessage(player, Color.DARK_AQUA + "| " + Color.GREY + "r:<radius> " + Color.WHITE + "- " + Phrase.build(Phrase.HELP_PARAMS_4, selector));
        Chat.sendMessage(player, Color.DARK_AQUA + "| " + Color.GREY + "c:<x,y,z> " + Color.WHITE + "- " + Phrase.build(Phrase.HELP_COORDINATES));
        Chat.sendMessage(player, Color.DARK_AQUA + "| " + Color.GREY + "a:<action> " + Color.WHITE + "- " + Phrase.build(Phrase.HELP_PARAMS_5, selector));
        Chat.sendMessage(player, Color.DARK_AQUA + "| " + Color.GREY + "i:<include> " + Color.WHITE + "- " + Phrase.build(Phrase.HELP_PARAMS_6, selector));
        Chat.sendMessage(player, Color.DARK_AQUA + "| " + Color.GREY + "e:<exclude> " + Color.WHITE + "- " + Phrase.build(Phrase.HELP_PARAMS_7, selector));
        if (includeFilter) {
            Chat.sendMessage(player, Color.DARK_AQUA + "| " + Color.GREY + "f:<filter> " + Color.WHITE + "- " + Phrase.build(Phrase.HELP_FILTER_1));
        }
        Chat.sendMessage(player, Color.GREY + Color.ITALIC + Phrase.build(Phrase.HELP_PARAMETER, root + " help <param>"));
    }
}
