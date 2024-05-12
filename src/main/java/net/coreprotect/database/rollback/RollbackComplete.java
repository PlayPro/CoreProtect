package net.coreprotect.database.rollback;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EntityType;

import net.coreprotect.language.Phrase;
import net.coreprotect.language.Selector;
import net.coreprotect.utility.Chat;
import net.coreprotect.utility.Color;

public class RollbackComplete {

    public static void output(CommandSender user, Location location, List<String> checkUsers, List<Object> restrictList, Map<Object, Boolean> excludeList, List<String> excludeUserList, List<Integer> actionList, String timeString, Integer chunkCount, Double seconds, Integer itemCount, Integer blockCount, Integer entityCount, int rollbackType, Integer[] radius, boolean verbose, boolean restrictWorld, int preview) {
        try {
            if (preview == 2) {
                Chat.sendMessage(user, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.PREVIEW_CANCELLED));
                return;
            }

            Chat.sendMessage(user, "-----");

            StringBuilder usersBuilder = new StringBuilder();
            for (String value : checkUsers) {
                if (usersBuilder.length() == 0) {
                    usersBuilder = usersBuilder.append("" + value + "");
                }
                else {
                    usersBuilder.append(", ").append(value);
                }
            }
            String users = usersBuilder.toString();

            if (users.equals("#global") && restrictWorld) {
                users = "#" + location.getWorld().getName();
            }

            if (preview > 0) {
                Chat.sendMessage(user, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.ROLLBACK_COMPLETED, users, Selector.THIRD)); // preview
            }
            else if (rollbackType == 0) {
                Chat.sendMessage(user, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.ROLLBACK_COMPLETED, users, Selector.FIRST)); // rollback
            }
            else if (rollbackType == 1) {
                Chat.sendMessage(user, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.ROLLBACK_COMPLETED, users, Selector.SECOND)); // restore
            }

            if (preview == 1 || rollbackType == 0 || rollbackType == 1) {
                Chat.sendMessage(user, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.ROLLBACK_TIME, timeString));
            }

            if (radius != null) {
                int worldedit = radius[7];
                if (worldedit == 0) {
                    Integer rollbackRadius = radius[0];
                    Chat.sendMessage(user, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.ROLLBACK_RADIUS, rollbackRadius.toString(), (rollbackRadius == 1 ? Selector.FIRST : Selector.SECOND)));
                }
                else {
                    Chat.sendMessage(user, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.ROLLBACK_SELECTION, "#worldedit"));
                }
            }

            if (restrictWorld && radius == null) {
                if (location != null) {
                    Chat.sendMessage(user, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.ROLLBACK_WORLD_ACTION, location.getWorld().getName(), Selector.FIRST));
                }
            }

            if (actionList.contains(4) && actionList.contains(11)) {
                if (actionList.contains(0)) {
                    Chat.sendMessage(user, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.ROLLBACK_WORLD_ACTION, "+inventory", Selector.SECOND));
                }
                else if (actionList.contains(1)) {
                    Chat.sendMessage(user, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.ROLLBACK_WORLD_ACTION, "-inventory", Selector.SECOND));
                }
                else {
                    Chat.sendMessage(user, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.ROLLBACK_WORLD_ACTION, "inventory", Selector.SECOND));
                }
            }
            else if (actionList.contains(4)) {
                if (actionList.contains(0)) {
                    Chat.sendMessage(user, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.ROLLBACK_WORLD_ACTION, "-container", Selector.SECOND));
                }
                else if (actionList.contains(1)) {
                    Chat.sendMessage(user, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.ROLLBACK_WORLD_ACTION, "+container", Selector.SECOND));
                }
                else {
                    Chat.sendMessage(user, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.ROLLBACK_WORLD_ACTION, "container", Selector.SECOND));
                }
            }
            else if (actionList.contains(0) && actionList.contains(1)) {
                Chat.sendMessage(user, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.ROLLBACK_WORLD_ACTION, "block", Selector.SECOND));
            }
            else if (actionList.contains(0)) {
                Chat.sendMessage(user, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.ROLLBACK_WORLD_ACTION, "-block", Selector.SECOND));
            }
            else if (actionList.contains(1)) {
                Chat.sendMessage(user, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.ROLLBACK_WORLD_ACTION, "+block", Selector.SECOND));
            }
            else if (actionList.contains(3)) {
                Chat.sendMessage(user, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.ROLLBACK_WORLD_ACTION, "kill", Selector.SECOND));
            }

            if (restrictList.size() > 0) {
                StringBuilder restrictTargets = new StringBuilder();
                boolean material = false;
                boolean item = false;
                boolean entity = false;

                int targetCount = 0;
                for (Object restrictTarget : restrictList) {
                    String targetName = "";

                    if (restrictTarget instanceof Material) {
                        targetName = ((Material) restrictTarget).name().toLowerCase(Locale.ROOT);
                        item = (!item ? !(((Material) restrictTarget).isBlock()) : item);
                        material = true;
                    }
                    else if (restrictTarget instanceof EntityType) {
                        targetName = ((EntityType) restrictTarget).name().toLowerCase(Locale.ROOT);
                        entity = true;
                    }

                    if (targetCount == 0) {
                        restrictTargets = restrictTargets.append("" + targetName + "");
                    }
                    else {
                        restrictTargets.append(", ").append(targetName);
                    }

                    targetCount++;
                }

                String targetType = Selector.THIRD;
                if (material && !item && !entity) {
                    targetType = Selector.FIRST;
                }
                else if (material && item && !entity) {
                    targetType = Selector.THIRD;
                }
                else if (entity && !material) {
                    targetType = Selector.SECOND;
                }

                Chat.sendMessage(user, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.ROLLBACK_INCLUDE, restrictTargets.toString(), Selector.FIRST, targetType, (targetCount == 1 ? Selector.FIRST : Selector.SECOND))); // include
            }

            if (excludeList.size() > 0) {
                StringBuilder excludeTargets = new StringBuilder();
                boolean material = false;
                boolean item = false;
                boolean entity = false;

                int excludeCount = 0;
                for (Map.Entry<Object, Boolean> entry : excludeList.entrySet()) {
                    Object excludeTarget = entry.getKey();
                    Boolean excludeTargetInternal = entry.getValue();

                    // don't display default block excludes
                    if (Boolean.TRUE.equals(excludeTargetInternal)) {
                        continue;
                    }

                    // don't display that excluded water/fire/farmland in inventory rollbacks
                    if (actionList.contains(4) && actionList.contains(11)) {
                        if (excludeTarget.equals(Material.FIRE) || excludeTarget.equals(Material.WATER) || excludeTarget.equals(Material.FARMLAND)) {
                            continue;
                        }
                    }

                    String targetName = "";
                    if (excludeTarget instanceof Material) {
                        targetName = ((Material) excludeTarget).name().toLowerCase(Locale.ROOT);
                        item = (!item ? !(((Material) excludeTarget).isBlock()) : item);
                        material = true;
                    }
                    else if (excludeTarget instanceof EntityType) {
                        targetName = ((EntityType) excludeTarget).name().toLowerCase(Locale.ROOT);
                        entity = true;
                    }

                    if (excludeCount == 0) {
                        excludeTargets = excludeTargets.append("" + targetName + "");
                    }
                    else {
                        excludeTargets.append(", ").append(targetName);
                    }

                    excludeCount++;
                }

                String targetType = Selector.THIRD;
                if (material && !item && !entity) {
                    targetType = Selector.FIRST;
                }
                else if (material && item && !entity) {
                    targetType = Selector.THIRD;
                }
                else if (entity && !material) {
                    targetType = Selector.SECOND;
                }

                if (excludeCount > 0) {
                    Chat.sendMessage(user, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.ROLLBACK_INCLUDE, excludeTargets.toString(), Selector.SECOND, targetType, (excludeCount == 1 ? Selector.FIRST : Selector.SECOND))); // exclude
                }
            }

            if (excludeUserList.size() > 0) {
                StringBuilder excludeUsers = new StringBuilder();

                int excludeCount = 0;
                for (String excludeUser : excludeUserList) {
                    // don't display that excluded #hopper in inventory rollbacks
                    if (actionList.contains(4) && actionList.contains(11)) {
                        if (excludeUser.equals("#hopper")) {
                            continue;
                        }
                    }

                    if (excludeCount == 0) {
                        excludeUsers = excludeUsers.append("" + excludeUser + "");
                    }
                    else {
                        excludeUsers.append(", ").append(excludeUser);
                    }

                    excludeCount++;
                }

                if (excludeCount > 0) {
                    Chat.sendMessage(user, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.ROLLBACK_EXCLUDED_USERS, excludeUsers.toString(), (excludeCount == 1 ? Selector.FIRST : Selector.SECOND)));
                }
            }

            StringBuilder modifiedData = new StringBuilder();
            Integer modifyCount = 0;
            if (actionList.contains(5)) {
                modifiedData = modifiedData.append(Phrase.build(Phrase.AMOUNT_ITEM, NumberFormat.getInstance().format(blockCount), (blockCount == 1 ? Selector.FIRST : Selector.SECOND)));
                modifyCount++;
            }
            else {
                if (itemCount > 0 || actionList.contains(4)) {
                    modifiedData = modifiedData.append(Phrase.build(Phrase.AMOUNT_ITEM, NumberFormat.getInstance().format(itemCount), (itemCount == 1 ? Selector.FIRST : Selector.SECOND)));
                    modifyCount++;
                }

                if (entityCount > 0) {
                    if (modifyCount > 0) {
                        modifiedData.append(", ");
                    }
                    modifiedData.append(Phrase.build(Phrase.AMOUNT_ENTITY, NumberFormat.getInstance().format(entityCount), (entityCount == 1 ? Selector.FIRST : Selector.SECOND)));
                    modifyCount++;
                }

                if (blockCount > 0 || !actionList.contains(4) || preview > 0) {
                    if (modifyCount > 0) {
                        modifiedData.append(", ");
                    }
                    modifiedData.append(Phrase.build(Phrase.AMOUNT_BLOCK, NumberFormat.getInstance().format(blockCount), (blockCount == 1 ? Selector.FIRST : Selector.SECOND)));
                    modifyCount++;
                }
            }

            StringBuilder modifiedDataVerbose = new StringBuilder();
            if (verbose && preview == 0 && !actionList.contains(11)) {
                if (chunkCount > -1 && modifyCount < 3) {
                    if (modifyCount > 0) {
                        modifiedData.append(", ");
                    }
                    modifiedData.append(Phrase.build(Phrase.AMOUNT_CHUNK, NumberFormat.getInstance().format(chunkCount), (chunkCount == 1 ? Selector.FIRST : Selector.SECOND)));
                    modifyCount++;
                }
                else if (chunkCount > 1) {
                    modifiedDataVerbose.append(Phrase.build(Phrase.AMOUNT_CHUNK, NumberFormat.getInstance().format(chunkCount), (chunkCount == 1 ? Selector.FIRST : Selector.SECOND)));
                }
            }

            Chat.sendMessage(user, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.ROLLBACK_MODIFIED, modifiedData.toString(), (preview == 0 ? Selector.FIRST : Selector.SECOND)));
            if (modifiedDataVerbose.length() > 0) {
                Chat.sendMessage(user, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.ROLLBACK_MODIFIED, modifiedDataVerbose.toString(), (preview == 0 ? Selector.FIRST : Selector.SECOND)));
            }

            if (preview == 0) {
                BigDecimal decimalSeconds = new BigDecimal(seconds).setScale(1, RoundingMode.HALF_EVEN);
                Chat.sendMessage(user, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.ROLLBACK_LENGTH, decimalSeconds.stripTrailingZeros().toPlainString(), (decimalSeconds.doubleValue() == 1 ? Selector.FIRST : Selector.SECOND)));
            }

            Chat.sendMessage(user, "-----");
            if (preview > 0) {
                Chat.sendMessage(user, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.PLEASE_SELECT, "/co apply", "/co cancel"));
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

}
