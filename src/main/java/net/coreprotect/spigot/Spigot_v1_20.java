package net.coreprotect.spigot;

import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.event.block.SignChangeEvent;

public class Spigot_v1_20 extends Spigot_v1_16 implements SpigotInterface {

    @Override
    public String getLine(Sign sign, int line) {
        if (line < 4) {
            return sign.getSide(Side.FRONT).getLine(line);
        }
        else {
            return sign.getSide(Side.BACK).getLine(line - 4);
        }
    }

    @Override
    public void setLine(Sign sign, int line, String string) {
        if (string == null) {
            string = "";
        }

        if (line < 4) {
            sign.getSide(Side.FRONT).setLine(line, string);
        }
        else {
            sign.getSide(Side.BACK).setLine(line - 4, string);
        }
    }

    @Override
    public boolean isSignFront(SignChangeEvent event) {
        return event.getSide().equals(Side.FRONT);
    }

}
