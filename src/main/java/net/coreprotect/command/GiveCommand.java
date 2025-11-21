package net.coreprotect.command;

import net.coreprotect.language.Phrase;
import net.coreprotect.utility.Chat;
import net.coreprotect.utility.ChatMessage;
import net.coreprotect.utility.Color;
import net.coreprotect.utility.ItemUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class GiveCommand {
  public static void runCommand(CommandSender sender, Command command, boolean permission, String[] args) {
    if (!permission) {
      Chat.sendMessage(sender, new ChatMessage(Phrase.build(Phrase.NO_PERMISSION)).build());
      return;
    }

    Integer itemId = CommandParser.parseGivableItemId(args);
    if (itemId == null) {
      Chat.sendMessage(sender, new ChatMessage(Phrase.build(Phrase.MISSING_PARAMETERS, Color.WHITE, "/" + command.getName() + " give <itemId>")).build());
      return;
    }

    ItemStack item = ItemUtils.getGivableItem(itemId);
    if (item == null) {
      Chat.sendMessage(sender, new ChatMessage(Phrase.build(Phrase.INVALID_ITEM_ID)).build());
      return;
    }

    if (!(sender instanceof Player)) {
      Chat.sendMessage(sender, new ChatMessage(Phrase.build(Phrase.ACTION_NOT_SUPPORTED)).build());
      return;
    }

    Player player = (Player) sender;
    player.getInventory().addItem(item);
  }
}
