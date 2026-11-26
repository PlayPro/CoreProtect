package net.coreprotect.listener.player.inspector;

import java.util.List;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import net.coreprotect.database.lookup.SignMessageLookup;
import net.coreprotect.utility.Chat;

public class SignInspector extends BaseInspector {

    public void performSignLookup(Player player, Location location) {
        startInspection(player, () -> withStatement(player, (connection, statement) -> {
            List<String> results = SignMessageLookup.performLookup(null, statement, location, player, 1, 7);
            Chat.sendSignLookupComponents(player, results);
        }));
    }
}
