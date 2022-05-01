package net.coreprotect.command;

import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginDescriptionFile;

import net.coreprotect.CoreProtect;
import net.coreprotect.config.Config;
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.consumer.Consumer;
import net.coreprotect.consumer.process.Process;
import net.coreprotect.language.Phrase;
import net.coreprotect.language.Selector;
import net.coreprotect.patch.Patch;
import net.coreprotect.thread.NetworkHandler;
import net.coreprotect.utility.Chat;
import net.coreprotect.utility.Color;

public class StatusCommand {
    private static ConcurrentHashMap<String, Boolean> alert = new ConcurrentHashMap<>();

    protected static void runCommand(CommandSender player, boolean permission, String[] args) {
        if (!permission) {
            Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.NO_PERMISSION));
            return;
        }

        class BasicThread implements Runnable {
            @Override
            public void run() {
                try {
                    CoreProtect instance = CoreProtect.getInstance();
                    PluginDescriptionFile pdfFile = instance.getDescription();

                    String versionCheck = "";
                    if (Config.getGlobal().CHECK_UPDATES) {
                        String latestVersion = NetworkHandler.latestVersion();
                        if (latestVersion != null) {
                            versionCheck = " (" + Phrase.build(Phrase.LATEST_VERSION, "v" + latestVersion) + ")";
                        }
                    }

                    Chat.sendMessage(player, Color.WHITE + "----- " + Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "-----");
                    Chat.sendMessage(player, Color.DARK_AQUA + Phrase.build(Phrase.STATUS_VERSION, Color.WHITE, ConfigHandler.EDITION_NAME + " v" + pdfFile.getVersion() + ".") + versionCheck);

                    String donationKey = NetworkHandler.donationKey();
                    if (donationKey != null) {
                        Chat.sendMessage(player, Color.DARK_AQUA + Phrase.build(Phrase.STATUS_LICENSE, Color.WHITE, Phrase.build(Phrase.VALID_DONATION_KEY)) + " (" + donationKey + ")");
                    }
                    else {
                        Chat.sendMessage(player, Color.DARK_AQUA + Phrase.build(Phrase.STATUS_LICENSE, Color.WHITE, Phrase.build(Phrase.INVALID_DONATION_KEY)) + Color.GREY + Color.ITALIC + " (" + Phrase.build(Phrase.CHECK_CONFIG) + ")");
                    }

                    /*
                        CoreProtect show RAM usage
                        Items processed (since server start)
                        Items processed (last 60 minutes)
                     */

                    // Using MySQL/SQLite (Database Size: 587MB)

                    String firstVersion = Patch.getFirstVersion();
                    if (firstVersion.length() > 0) {
                        firstVersion = " (" + Phrase.build(Phrase.FIRST_VERSION, firstVersion) + ")";
                    }

                    String typeDatabase = Config.getGlobal().TYPE_DATABASE.toLowerCase(Locale.ROOT);
                    Chat.sendMessage(player, Color.DARK_AQUA + Phrase.build(Phrase.STATUS_DATABASE, Color.WHITE, typeDatabase) + firstVersion);

                    if (ConfigHandler.worldeditEnabled) {
                        Chat.sendMessage(player, Color.DARK_AQUA + Phrase.build(Phrase.STATUS_INTEGRATION, Color.WHITE, "WorldEdit", Selector.FIRST));
                    }
                    else if (instance.getServer().getPluginManager().getPlugin("WorldEdit") != null) {
                        Chat.sendMessage(player, Color.DARK_AQUA + Phrase.build(Phrase.STATUS_INTEGRATION, Color.WHITE, "WorldEdit", Selector.SECOND));
                    }

                    try {
                        int consumerCount = 0;
                        int currentConsumerSize = Process.getCurrentConsumerSize();
                        if (currentConsumerSize == 0) {
                            consumerCount = Consumer.getConsumerSize(0) + Consumer.getConsumerSize(1);
                        }
                        else {
                            int consumerId = (Consumer.currentConsumer == 1) ? 1 : 0;
                            consumerCount = Consumer.getConsumerSize(consumerId) + currentConsumerSize;
                        }

                        if (consumerCount >= 1 && (player instanceof Player)) {
                            if (Config.getConfig(((Player) player).getWorld()).PLAYER_COMMANDS) {
                                consumerCount = consumerCount - 1;
                            }
                        }

                        Chat.sendMessage(player, Color.DARK_AQUA + Phrase.build(Phrase.STATUS_CONSUMER, Color.WHITE, String.format("%,d", consumerCount), (consumerCount == 1 ? Selector.FIRST : Selector.SECOND)));
                    }
                    catch (Exception e) {
                        e.printStackTrace();
                    }

                    // Functions.sendMessage(player, Color.DARK_AQUA + "Website: " + Color.WHITE + "www.coreprotect.net/updates/");

                    // Functions.sendMessage(player, Color.DARK_AQUA + Phrase.build(Phrase.LINK_DISCORD, Color.WHITE + "www.coreprotect.net/discord/").replaceFirst(":", ":" + Color.WHITE));
                    Chat.sendMessage(player, Color.DARK_AQUA + Phrase.build(Phrase.LINK_DISCORD, Color.WHITE, "www.coreprotect.net/discord/"));
                    Chat.sendMessage(player, Color.DARK_AQUA + Phrase.build(Phrase.LINK_PATREON, Color.WHITE, "www.patreon.com/coreprotect/"));

                    if (player.isOp() && alert.get(player.getName()) == null) {
                        alert.put(player.getName(), true);

                        if (instance.getServer().getPluginManager().getPlugin("CoreEdit") == null) {
                            Thread.sleep(500);
                            /*
                            Functions.sendMessage(player, Color.WHITE + "----- " + Color.DARK_AQUA + "Recommended Plugin " + Color.WHITE + "-----");
                            Functions.sendMessage(player, Color.DARK_AQUA + "Notice: " + Color.WHITE + "Enjoy CoreProtect? Check out CoreEdit!");
                            Functions.sendMessage(player, Color.DARK_AQUA + "Download: " + Color.WHITE + "www.coreedit.net/download/");
                            */
                        }
                    }
                }
                catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        Runnable runnable = new BasicThread();
        Thread thread = new Thread(runnable);
        thread.start();
    }
}
