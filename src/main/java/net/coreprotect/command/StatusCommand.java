package net.coreprotect.command;

import java.util.Locale;

import org.bukkit.World;
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
import net.coreprotect.utility.SystemUtils;
import net.coreprotect.utility.VersionUtils;
import net.coreprotect.utility.ErrorReporter;

public class StatusCommand {

    protected static void runCommand(CommandSender player, boolean permission, String[] args) {
        if (!permission) {
            Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.NO_PERMISSION));
            return;
        }

        CoreProtect instance = CoreProtect.getInstance();
        PluginDescriptionFile description = instance.getDescription();
        boolean worldEditEnabled = ConfigHandler.worldeditEnabled;
        boolean worldEditInstalled = instance.getServer().getPluginManager().getPlugin("WorldEdit") != null;
        World world = player instanceof Player ? ((Player) player).getWorld() : null;
        boolean includesSenderCommand = world != null && Config.getConfig(world).PLAYER_COMMANDS;
        new Thread(() -> sendStatus(player, description, worldEditEnabled, worldEditInstalled, includesSenderCommand)).start();
    }

    private static void sendStatus(CommandSender player, PluginDescriptionFile description, boolean worldEditEnabled, boolean worldEditInstalled, boolean includesSenderCommand) {
        try {
            String versionCheck = "";
            if (Config.getGlobal().CHECK_UPDATES) {
                String latestVersion = NetworkHandler.latestVersion();
                String latestEdgeVersion = NetworkHandler.latestEdgeVersion();
                if (latestVersion != null) {
                    versionCheck = " (" + Phrase.build(Phrase.LATEST_VERSION, "v" + latestVersion) + ")";
                }
                else if (latestEdgeVersion != null && !VersionUtils.isCommunityEdition()) {
                    versionCheck = " (" + Phrase.build(Phrase.LATEST_VERSION, "v" + latestEdgeVersion) + ")";
                }
            }

            Chat.sendMessage(player, Color.WHITE + "----- " + Color.DARK_AQUA + "CoreProtect" + (VersionUtils.isCommunityEdition() ? " " + ConfigHandler.COMMUNITY_EDITION : "") + Color.WHITE + " -----");
            Chat.sendMessage(player, Color.DARK_AQUA + Phrase.build(Phrase.STATUS_VERSION, Color.WHITE, ConfigHandler.EDITION_NAME + " v" + description.getVersion() + ".") + versionCheck);

            String donationKey = NetworkHandler.donationKey();
            if (donationKey != null) {
                Chat.sendMessage(player, Color.DARK_AQUA + Phrase.build(Phrase.STATUS_LICENSE, Color.WHITE, Phrase.build(Phrase.VALID_DONATION_KEY)) + " (" + donationKey + ")");
            }
            else {
                Chat.sendMessage(player, Color.DARK_AQUA + Phrase.build(Phrase.STATUS_LICENSE, Color.WHITE, Phrase.build(Phrase.INVALID_DONATION_KEY)) + Color.GREY + Color.ITALIC + " (" + Phrase.build(Phrase.CHECK_CONFIG) + ")");
            }

            String firstVersion = ConfigHandler.databaseType.isClickHouse() ? "" : Patch.getFirstVersion();
            if (firstVersion.length() > 0) {
                firstVersion = " (" + Phrase.build(Phrase.FIRST_VERSION, firstVersion) + ")";
            }
            String databaseName = ConfigHandler.databaseType.getDisplayName();
            if (Consumer.isPersistenceHalted()) {
                databaseName += " " + Color.RED + Phrase.build(Phrase.STATUS_DATABASE_STATE, Selector.FIRST) + Color.WHITE;
            }
            else if (!ConfigHandler.databaseReachable) {
                databaseName += " " + Color.RED + Phrase.build(Phrase.STATUS_DATABASE_STATE, Selector.SECOND) + Color.WHITE;
            }
            Chat.sendMessage(player, Color.DARK_AQUA + Phrase.build(Phrase.STATUS_DATABASE, Color.WHITE, databaseName) + firstVersion);

            if (worldEditEnabled) {
                Chat.sendMessage(player, Color.DARK_AQUA + Phrase.build(Phrase.STATUS_INTEGRATION, Color.WHITE, "WorldEdit", Selector.FIRST));
            }
            else if (worldEditInstalled) {
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

                if (consumerCount >= 1 && includesSenderCommand) {
                    consumerCount--;
                }

                Chat.sendMessage(player, Color.DARK_AQUA + Phrase.build(Phrase.STATUS_CONSUMER, Color.WHITE, String.format("%,d", consumerCount), (consumerCount == 1 ? Selector.FIRST : Selector.SECOND)));
            }
            catch (Exception e) {
                ErrorReporter.report(e);
            }

            long autoPurgeRowsPurged = ConfigHandler.autoPurgeRowsPurged.get();
            Chat.sendMessage(player, Color.DARK_AQUA + Phrase.build(Phrase.STATUS_AUTO_PURGE, Color.WHITE, String.format("%,d", autoPurgeRowsPurged), (autoPurgeRowsPurged == 1 ? Selector.FIRST : Selector.SECOND)));

            try {
                String cpuInfo = "x" + Runtime.getRuntime().availableProcessors() + " " + Phrase.build(Phrase.CPU_CORES);
                if (ConfigHandler.processorInfo != null) {
                    String modelName = ConfigHandler.processorInfo.getName();
                    if (modelName.contains(" CPU")) {
                        String[] split = modelName.split(" CPU")[0].split(" ");
                        modelName = split[split.length - 1];
                    }
                    else if (modelName.contains(" Processor")) {
                        String[] split = modelName.split(" Processor")[0].split(" ");
                        modelName = split[split.length - 1];
                    }

                    String cpuSpeed = String.valueOf(ConfigHandler.processorInfo.getMaxFrequency());
                    double speedVal = Long.valueOf(cpuSpeed) / 1000000000.0;

                    // Fix for Apple Silicon processors reporting 0 GHz
                    if (speedVal < 0.01 && SystemUtils.isAppleSilicon()) {
                        Double appleSiliconSpeed = SystemUtils.getAppleSiliconSpeed();
                        if (appleSiliconSpeed != null) {
                            speedVal = appleSiliconSpeed;
                        }
                    }

                    cpuSpeed = String.format("%.2f", speedVal);
                    if (speedVal >= 0.01) {
                        cpuInfo = "x" + Runtime.getRuntime().availableProcessors() + " " + cpuSpeed + "GHz " + modelName + ".";
                    }
                }

                Runtime runtime = Runtime.getRuntime();
                String usedRAM = formatHeapBytes(runtime.totalMemory() - runtime.freeMemory());
                String totalRAM = formatHeapBytes(runtime.maxMemory());
                String systemInformation = Phrase.build(Phrase.RAM_STATS, usedRAM, totalRAM);
                if (cpuInfo.length() > 0) {
                    systemInformation = cpuInfo + " (" + systemInformation + ")";
                }

                Chat.sendMessage(player, Color.DARK_AQUA + Phrase.build(Phrase.STATUS_SYSTEM, Color.WHITE, systemInformation));
            }
            catch (Exception e) {
                ErrorReporter.report(e);
            }

            Chat.sendMessage(player, Color.DARK_AQUA + Phrase.build(Phrase.LINK_DISCORD, Color.WHITE, "www.coreprotect.net/discord/"));
            Chat.sendMessage(player, Color.DARK_AQUA + Phrase.build(Phrase.LINK_PATREON, Color.WHITE, "www.patreon.com/coreprotect/"));
        }
        catch (Exception e) {
            ErrorReporter.report(e);
        }
    }

    static String formatHeapBytes(long bytes) {
        return String.format(Locale.ROOT, "%.2f", bytes / 1_073_741_824.0);
    }
}
