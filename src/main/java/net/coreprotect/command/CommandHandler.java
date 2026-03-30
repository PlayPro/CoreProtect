package net.coreprotect.command;

import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import net.coreprotect.config.ConfigHandler;
import net.coreprotect.language.Phrase;
import net.coreprotect.thread.NetworkHandler;
import net.coreprotect.utility.Chat;
import net.coreprotect.utility.Color;
import net.coreprotect.utility.Extensions;
import net.coreprotect.utility.VersionUtils;

public class CommandHandler implements CommandExecutor {
    private static CommandHandler instance;
    private static ConcurrentHashMap<String, Boolean> versionAlert = new ConcurrentHashMap<>();

    public static CommandHandler getInstance() {
        if (instance == null) {
            instance = new CommandHandler();
        }
        return instance;
    }

    @Override
    public boolean onCommand(CommandSender user, Command command, String commandLabel, String[] argumentArray) {
        String commandName = command.getName().toLowerCase(Locale.ROOT);

        if (commandName.equals("core") || commandName.equals("coreprotect") || commandName.equals("co")) {
            int resultc = argumentArray.length;
            if (resultc > -1) {
                String corecommand = "help";
                if (resultc > 0) {
                    corecommand = argumentArray[0].toLowerCase(Locale.ROOT);
                }
                boolean permission = false;
                if (!permission) {
                    if (user.hasPermission("coreprotect.rollback") && (corecommand.equals("rollback") || corecommand.equals("rb") || corecommand.equals("ro") || corecommand.equals("apply") || corecommand.equals("cancel"))) {
                        permission = true;
                    }
                    else if (user.hasPermission("coreprotect.restore") && (corecommand.equals("restore") || corecommand.equals("rs") || corecommand.equals("re") || corecommand.equals("undo") || corecommand.equals("apply") || corecommand.equals("cancel"))) {
                        permission = true;
                    }
                    else if (user.hasPermission("coreprotect.inspect") && (corecommand.equals("i") || corecommand.equals("inspect") || corecommand.equals("inspector"))) {
                        permission = true;
                    }
                    else if (user.hasPermission("coreprotect.help") && corecommand.equals("help")) {
                        permission = true;
                    }
                    else if (user.hasPermission("coreprotect.purge") && corecommand.equals("purge")) {
                        permission = true;
                    }
                    else if (user.hasPermission("coreprotect.lookup") && (corecommand.equals("l") || corecommand.equals("lookup") || corecommand.equals("page") || corecommand.equals("near"))) {
                        permission = true;
                    }
                    else if (user.hasPermission("coreprotect.lookup.near") && corecommand.equals("near")) {
                        permission = true;
                    }
                    else if (user.hasPermission("coreprotect.teleport") && (corecommand.equals("tp") || corecommand.equals("teleport"))) {
                        permission = true;
                    }
                    else if (user.hasPermission("coreprotect.reload") && corecommand.equals("reload")) {
                        permission = true;
                    }
                    else if (user.hasPermission("coreprotect.status") && (corecommand.equals("status") || corecommand.equals("stats") || corecommand.equals("version"))) {
                        permission = true;
                    }
                    else if (user.hasPermission("coreprotect.consumer") && corecommand.equals("consumer")) {
                        permission = true;
                    }
                    else if (user.hasPermission("coreprotect.networking") && corecommand.equals("network-debug")) {
                        permission = true;
                    }
                }

                if (corecommand.equals("rollback") || corecommand.equals("restore") || corecommand.equals("rb") || corecommand.equals("rs") || corecommand.equals("ro") || corecommand.equals("re")) {
                    RollbackRestoreCommand.runCommand(user, command, permission, argumentArray, null, 0, 0);
                }
                else if (corecommand.equals("apply")) {
                    ApplyCommand.runCommand(user, command, permission, argumentArray);
                }
                else if (corecommand.equals("cancel")) {
                    CancelCommand.runCommand(user, command, permission, argumentArray);
                }
                else if (corecommand.equals("undo")) {
                    UndoCommand.runCommand(user, command, permission, argumentArray);
                }
                else if (corecommand.equals("help")) {
                    HelpCommand.runCommand(user, permission, argumentArray);
                }
                else if (corecommand.equals("purge")) {
                    PurgeCommand.runCommand(user, permission, argumentArray);
                }
                else if (corecommand.equals("inspect") || corecommand.equals("i")) {
                    InspectCommand.runCommand(user, permission, argumentArray);
                }
                else if (corecommand.equals("lookup") || corecommand.equals("l") || corecommand.equals("page")) {
                    LookupCommand.runCommand(user, command, permission, argumentArray);
                }
                else if (corecommand.equals("near")) {
                    LookupCommand.runCommand(user, command, permission, new String[] { "near", "r:5x5" });
                }
                else if (corecommand.equals("teleport") || corecommand.equals("tp")) {
                    TeleportCommand.runCommand(user, permission, argumentArray);
                }
                else if (corecommand.equals("status") || corecommand.equals("stats") || corecommand.equals("version")) {
                    StatusCommand.runCommand(user, permission, argumentArray);
                }
                else if (corecommand.equals("reload")) {
                    ReloadCommand.runCommand(user, permission, argumentArray);
                }
                else if (corecommand.equals("consumer")) {
                    ConsumerCommand.runCommand(user, permission, argumentArray);
                }
                else if (corecommand.equals("network-debug")) {
                    NetworkDebugCommand.runCommand(user, permission, argumentArray);
                }
                else if (corecommand.equals("migrate-db")) {
                    if (!VersionUtils.validDonationKey()) {
                        Chat.sendMessage(user, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.DONATION_KEY_REQUIRED));
                    }
                    else {
                        Extensions.runDatabaseMigration(corecommand, user, argumentArray);
                    }
                }
                else {
                    Chat.sendMessage(user, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.COMMAND_NOT_FOUND, Color.WHITE, "/co " + corecommand));
                }
            }
            else {
                Chat.sendMessage(user, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.MISSING_PARAMETERS, Color.WHITE, "/co <parameters>"));
            }

            if (user.isOp() && versionAlert.get(user.getName()) == null) {
                String latestVersion = NetworkHandler.latestVersion();
                String latestEdgeVersion = NetworkHandler.latestEdgeVersion();
                if (latestVersion != null || latestEdgeVersion != null) {
                    versionAlert.put(user.getName(), true);
                    class updateAlert implements Runnable {
                        @Override
                        public void run() {
                            try {
                                Thread.sleep(5000);
                                Chat.sendMessage(user, Color.WHITE + "----- " + Color.DARK_AQUA + Phrase.build(Phrase.UPDATE_HEADER, "CoreProtect" + (VersionUtils.isCommunityEdition() ? " " + ConfigHandler.COMMUNITY_EDITION : "")) + Color.WHITE + " -----");
                                if (latestVersion != null) {
                                    Chat.sendMessage(user, Color.DARK_AQUA + Phrase.build(Phrase.UPDATE_NOTICE, Color.WHITE, "CoreProtect CE v" + latestVersion));
                                    Chat.sendMessage(user, Color.DARK_AQUA + Phrase.build(Phrase.LINK_DOWNLOAD, Color.WHITE, "www.coreprotect.net/download/"));
                                }
                                else if (!VersionUtils.isCommunityEdition()) {
                                    Chat.sendMessage(user, Color.DARK_AQUA + Phrase.build(Phrase.UPDATE_NOTICE, Color.WHITE, "CoreProtect v" + latestEdgeVersion));
                                    Chat.sendMessage(user, Color.DARK_AQUA + Phrase.build(Phrase.LINK_DOWNLOAD, Color.WHITE, "www.coreprotect.net/latest/"));
                                }
                            }
                            catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    }
                    (new Thread(new updateAlert())).start();
                }
            }

            return true;
        }

        return false;
    }
}
