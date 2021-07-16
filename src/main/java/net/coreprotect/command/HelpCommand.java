package net.coreprotect.command;

import java.util.Locale;

import org.bukkit.command.CommandSender;

import net.coreprotect.language.Phrase;
import net.coreprotect.language.Selector;
import net.coreprotect.utility.Chat;
import net.coreprotect.utility.Color;

public class HelpCommand {
    protected static void runCommand(CommandSender player, boolean permission, String[] args) {
        int resultc = args.length;
        if (permission) {
            if (resultc > 1) {
                String helpcommand_original = args[1];
                String helpcommand = args[1].toLowerCase(Locale.ROOT);
                helpcommand = helpcommand.replaceAll("[^a-zA-Z]", "");
                Chat.sendMessage(player, Color.WHITE + "----- " + Color.DARK_AQUA + Phrase.build(Phrase.HELP_HEADER, "CoreProtect") + Color.WHITE + " -----");
                switch (helpcommand) {
                    case "help":
                        Chat.sendMessage(player, Color.DARK_AQUA + "/co help " + Color.WHITE + "- " + Phrase.build(Phrase.HELP_LIST));
                        break;
                    case "inspect":
                    case "inspector":
                    case "in":
                        Chat.sendMessage(player, Color.DARK_AQUA + Phrase.build(Phrase.HELP_INSPECT_1));
                        Chat.sendMessage(player, "* " + Phrase.build(Phrase.HELP_INSPECT_2));
                        Chat.sendMessage(player, "* " + Phrase.build(Phrase.HELP_INSPECT_3));
                        Chat.sendMessage(player, "* " + Phrase.build(Phrase.HELP_INSPECT_4));
                        Chat.sendMessage(player, "* " + Phrase.build(Phrase.HELP_INSPECT_5));
                        Chat.sendMessage(player, "* " + Phrase.build(Phrase.HELP_INSPECT_6));
                        Chat.sendMessage(player, Color.GREY + Color.ITALIC + Phrase.build(Phrase.HELP_INSPECT_7));
                        break;
                    case "params":
                    case "param":
                    case "parameters":
                    case "parameter":
                        Chat.sendMessage(player, Color.DARK_AQUA + "/co lookup " + Color.GREY + "<params> " + Color.WHITE + "- " + Phrase.build(Phrase.HELP_PARAMS_1, Selector.FIRST));
                        Chat.sendMessage(player, Color.DARK_AQUA + "| " + Color.GREY + "u:<users> " + Color.WHITE + "- " + Phrase.build(Phrase.HELP_PARAMS_2, Selector.FIRST));
                        Chat.sendMessage(player, Color.DARK_AQUA + "| " + Color.GREY + "t:<time> " + Color.WHITE + "- " + Phrase.build(Phrase.HELP_PARAMS_3, Selector.FIRST));
                        Chat.sendMessage(player, Color.DARK_AQUA + "| " + Color.GREY + "r:<radius> " + Color.WHITE + "- " + Phrase.build(Phrase.HELP_PARAMS_4, Selector.FIRST));
                        Chat.sendMessage(player, Color.DARK_AQUA + "| " + Color.GREY + "a:<action> " + Color.WHITE + "- " + Phrase.build(Phrase.HELP_PARAMS_5, Selector.FIRST));
                        Chat.sendMessage(player, Color.DARK_AQUA + "| " + Color.GREY + "i:<include> " + Color.WHITE + "- " + Phrase.build(Phrase.HELP_PARAMS_6, Selector.FIRST));
                        Chat.sendMessage(player, Color.DARK_AQUA + "| " + Color.GREY + "e:<exclude> " + Color.WHITE + "- " + Phrase.build(Phrase.HELP_PARAMS_7, Selector.FIRST));
                        Chat.sendMessage(player, Color.GREY + Color.ITALIC + Phrase.build(Phrase.HELP_PARAMETER, "/co help <param>"));
                        break;
                    case "rollback":
                    case "rollbacks":
                    case "rb":
                    case "ro":
                        Chat.sendMessage(player, Color.DARK_AQUA + "/co rollback " + Color.GREY + "<params> " + Color.WHITE + "- " + Phrase.build(Phrase.HELP_PARAMS_1, Selector.SECOND));
                        Chat.sendMessage(player, Color.DARK_AQUA + "| " + Color.GREY + "u:<users> " + Color.WHITE + "- " + Phrase.build(Phrase.HELP_PARAMS_2, Selector.SECOND));
                        Chat.sendMessage(player, Color.DARK_AQUA + "| " + Color.GREY + "t:<time> " + Color.WHITE + "- " + Phrase.build(Phrase.HELP_PARAMS_3, Selector.SECOND));
                        Chat.sendMessage(player, Color.DARK_AQUA + "| " + Color.GREY + "r:<radius> " + Color.WHITE + "- " + Phrase.build(Phrase.HELP_PARAMS_4, Selector.SECOND));
                        Chat.sendMessage(player, Color.DARK_AQUA + "| " + Color.GREY + "a:<action> " + Color.WHITE + "- " + Phrase.build(Phrase.HELP_PARAMS_5, Selector.SECOND));
                        Chat.sendMessage(player, Color.DARK_AQUA + "| " + Color.GREY + "i:<include> " + Color.WHITE + "- " + Phrase.build(Phrase.HELP_PARAMS_6, Selector.SECOND));
                        Chat.sendMessage(player, Color.DARK_AQUA + "| " + Color.GREY + "e:<exclude> " + Color.WHITE + "- " + Phrase.build(Phrase.HELP_PARAMS_7, Selector.SECOND));
                        Chat.sendMessage(player, Color.GREY + Color.ITALIC + Phrase.build(Phrase.HELP_PARAMETER, "/co help <param>"));
                        break;
                    case "restore":
                    case "restores":
                    case "re":
                    case "rs":
                        Chat.sendMessage(player, Color.DARK_AQUA + "/co restore " + Color.GREY + "<params> " + Color.WHITE + "- " + Phrase.build(Phrase.HELP_PARAMS_1, Selector.THIRD));
                        Chat.sendMessage(player, Color.DARK_AQUA + "| " + Color.GREY + "u:<users> " + Color.WHITE + "- " + Phrase.build(Phrase.HELP_PARAMS_2, Selector.THIRD));
                        Chat.sendMessage(player, Color.DARK_AQUA + "| " + Color.GREY + "t:<time> " + Color.WHITE + "- " + Phrase.build(Phrase.HELP_PARAMS_3, Selector.THIRD));
                        Chat.sendMessage(player, Color.DARK_AQUA + "| " + Color.GREY + "r:<radius> " + Color.WHITE + "- " + Phrase.build(Phrase.HELP_PARAMS_4, Selector.THIRD));
                        Chat.sendMessage(player, Color.DARK_AQUA + "| " + Color.GREY + "a:<action> " + Color.WHITE + "- " + Phrase.build(Phrase.HELP_PARAMS_5, Selector.THIRD));
                        Chat.sendMessage(player, Color.DARK_AQUA + "| " + Color.GREY + "i:<include> " + Color.WHITE + "- " + Phrase.build(Phrase.HELP_PARAMS_6, Selector.THIRD));
                        Chat.sendMessage(player, Color.DARK_AQUA + "| " + Color.GREY + "e:<exclude> " + Color.WHITE + "- " + Phrase.build(Phrase.HELP_PARAMS_7, Selector.THIRD));
                        Chat.sendMessage(player, Color.GREY + Color.ITALIC + Phrase.build(Phrase.HELP_PARAMETER, "/co help <param>"));
                        break;
                    case "lookup":
                    case "lookups":
                    case "l":
                        Chat.sendMessage(player, Color.DARK_AQUA + "/co lookup <params>");
                        Chat.sendMessage(player, Color.DARK_AQUA + "/co l <params> " + Color.WHITE + "- " + Phrase.build(Phrase.HELP_LOOKUP_1));
                        Chat.sendMessage(player, Color.DARK_AQUA + "/co lookup <page> " + Color.WHITE + "- " + Phrase.build(Phrase.HELP_LOOKUP_2));
                        Chat.sendMessage(player, Color.GREY + Color.ITALIC + Phrase.build(Phrase.HELP_PARAMETER, "/co help params"));
                        break;
                    case "purge":
                    case "purges":
                        Chat.sendMessage(player, Color.DARK_AQUA + "/co purge t:<time> " + Color.WHITE + "- " + Phrase.build(Phrase.HELP_PURGE_1));
                        Chat.sendMessage(player, Color.GREY + Color.ITALIC + "" + Phrase.build(Phrase.HELP_PURGE_2, "/co purge t:30d"));
                        break;
                    case "reload":
                        Chat.sendMessage(player, Color.DARK_AQUA + "/co reload " + Color.WHITE + "- " + Phrase.build(Phrase.HELP_RELOAD_COMMAND));
                        break;
                    case "status":
                        Chat.sendMessage(player, Color.DARK_AQUA + "/co status " + Color.WHITE + "- " + Phrase.build(Phrase.HELP_STATUS));
                        break;
                    case "teleport":
                        Chat.sendMessage(player, Color.DARK_AQUA + "/co teleport <world> <x> <y> <z> " + Color.WHITE + "- " + Phrase.build(Phrase.HELP_TELEPORT));
                        break;
                    case "u":
                    case "user":
                    case "users":
                    case "uuser":
                    case "uusers":
                        Chat.sendMessage(player, Color.DARK_AQUA + "/co lookup u:<users> " + Color.WHITE + "- " + Phrase.build(Phrase.HELP_USER_1));
                        Chat.sendMessage(player, Color.GREY + Color.ITALIC + Phrase.build(Phrase.HELP_USER_2));
                        break;
                    case "t":
                    case "time":
                    case "ttime":
                        Chat.sendMessage(player, Color.DARK_AQUA + "/co lookup t:<time> " + Color.WHITE + "- " + Phrase.build(Phrase.HELP_TIME_1));
                        Chat.sendMessage(player, Color.GREY + Color.ITALIC + Phrase.build(Phrase.HELP_TIME_2));
                        break;
                    case "r":
                    case "radius":
                    case "rradius":
                        Chat.sendMessage(player, Color.DARK_AQUA + "/co lookup r:<radius> " + Color.WHITE + "- " + Phrase.build(Phrase.HELP_RADIUS_1));
                        Chat.sendMessage(player, Color.GREY + Color.ITALIC + Phrase.build(Phrase.HELP_RADIUS_2));
                        break;
                    case "a":
                    case "action":
                    case "actions":
                    case "aaction":
                        Chat.sendMessage(player, Color.DARK_AQUA + "/co lookup a:<action> " + Color.WHITE + "- " + Phrase.build(Phrase.HELP_ACTION_1));
                        Chat.sendMessage(player, Color.GREY + Color.ITALIC + Phrase.build(Phrase.HELP_ACTION_2));
                        break;
                    case "i":
                    case "include":
                    case "iinclude":
                    case "b":
                    case "block":
                    case "blocks":
                    case "bblock":
                    case "bblocks":
                        Chat.sendMessage(player, Color.DARK_AQUA + "/co lookup i:<include> " + Color.WHITE + "- " + Phrase.build(Phrase.HELP_INCLUDE_1));
                        Chat.sendMessage(player, Color.GREY + Color.ITALIC + Phrase.build(Phrase.HELP_INCLUDE_2));
                        Chat.sendMessage(player, Color.GREY + Color.ITALIC + Phrase.build(Phrase.LINK_WIKI_BLOCK, "https://coreprotect.net/wiki-blocks"));
                        Chat.sendMessage(player, Color.GREY + Color.ITALIC + Phrase.build(Phrase.LINK_WIKI_ENTITY, "https://coreprotect.net/wiki-entities"));
                        break;
                    case "e":
                    case "exclude":
                    case "eexclude":
                        Chat.sendMessage(player, Color.DARK_AQUA + "/co lookup e:<exclude> " + Color.WHITE + "- " + Phrase.build(Phrase.HELP_EXCLUDE_1));
                        Chat.sendMessage(player, Color.GREY + Color.ITALIC + Phrase.build(Phrase.HELP_EXCLUDE_2));
                        Chat.sendMessage(player, Color.GREY + Color.ITALIC + Phrase.build(Phrase.LINK_WIKI_BLOCK, "https://coreprotect.net/wiki-blocks"));
                        break;
                    default:
                        Chat.sendMessage(player, Color.WHITE + Phrase.build(Phrase.HELP_NO_INFO, Color.WHITE, "/co help " + helpcommand_original));
                        break;
                }
            }
            else {
                Chat.sendMessage(player, Color.WHITE + "----- " + Color.DARK_AQUA + Phrase.build(Phrase.HELP_HEADER, "CoreProtect") + Color.WHITE + " -----");
                Chat.sendMessage(player, Color.DARK_AQUA + "/co help " + Color.GREY + "<command> " + Color.WHITE + "- " + Phrase.build(Phrase.HELP_COMMAND));
                Chat.sendMessage(player, Color.DARK_AQUA + "/co " + Color.GREY + "inspect " + Color.WHITE + "- " + Phrase.build(Phrase.HELP_INSPECT_COMMAND));
                Chat.sendMessage(player, Color.DARK_AQUA + "/co " + Color.GREY + "rollback " + Color.DARK_AQUA + "<params> " + Color.WHITE + "- " + Phrase.build(Phrase.HELP_ROLLBACK_COMMAND));
                Chat.sendMessage(player, Color.DARK_AQUA + "/co " + Color.GREY + "restore " + Color.DARK_AQUA + "<params> " + Color.WHITE + "- " + Phrase.build(Phrase.HELP_RESTORE_COMMAND));
                Chat.sendMessage(player, Color.DARK_AQUA + "/co " + Color.GREY + "lookup " + Color.DARK_AQUA + "<params> " + Color.WHITE + "- " + Phrase.build(Phrase.HELP_LOOKUP_COMMAND));
                Chat.sendMessage(player, Color.DARK_AQUA + "/co " + Color.GREY + "purge " + Color.DARK_AQUA + "<params> " + Color.WHITE + "- " + Phrase.build(Phrase.HELP_PURGE_COMMAND));
                Chat.sendMessage(player, Color.DARK_AQUA + "/co " + Color.GREY + "reload " + Color.WHITE + "- " + Phrase.build(Phrase.HELP_RELOAD_COMMAND));
                Chat.sendMessage(player, Color.DARK_AQUA + "/co " + Color.GREY + "status " + Color.WHITE + "- " + Phrase.build(Phrase.HELP_STATUS_COMMAND));
            }
        }
        else {
            Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.NO_PERMISSION));
        }
    }
}
