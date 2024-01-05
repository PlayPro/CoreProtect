package net.coreprotect.paper;

import org.bukkit.Bukkit;
import org.bukkit.block.Sign;
import org.bukkit.block.Skull;
import org.bukkit.block.sign.Side;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

public class Paper_v1_20 extends Paper_v1_17 implements PaperInterface {

    @Override
    public String getLine(Sign sign, int line) {
        // https://docs.adventure.kyori.net/serializer/
        if (line < 4) {
            return LegacyComponentSerializer.legacySection().serialize(sign.getSide(Side.FRONT).line(line));
        }
        else {
            return LegacyComponentSerializer.legacySection().serialize(sign.getSide(Side.BACK).line(line - 4));
        }
    }

    @Override
    public String getSkullOwner(Skull skull) {
        return skull.getPlayerProfile().getName();
    }

    @Override
    public void setSkullOwner(Skull skull, String owner) {
        skull.setPlayerProfile(Bukkit.createProfile(owner));
    }

}
