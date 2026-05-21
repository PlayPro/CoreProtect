package net.coreprotect.paper;

import java.net.URI;
import java.net.URL;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.block.Sign;
import org.bukkit.block.Skull;
import org.bukkit.block.sign.Side;
import org.bukkit.profile.PlayerTextures;

import com.destroystokyo.paper.profile.PlayerProfile;

import net.coreprotect.config.Config;
import net.coreprotect.utility.ErrorReporter;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

public class Paper_v1_20 extends Paper_v1_17 {

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
        PlayerProfile playerProfile = skull.getPlayerProfile();
        if (playerProfile == null) {
            return null;
        }

        String owner = playerProfile.getName();
        if (playerProfile.getId() != null) {
            owner = playerProfile.getId().toString();
        }
        else if (Config.getGlobal().MYSQL && owner != null && owner.length() > 255) {
            return owner.substring(0, 255);
        }

        return owner;
    }

    @Override
    public void setSkullOwner(Skull skull, String owner) {
        if (owner == null || owner.length() == 0) {
            return;
        }

        if (owner.length() >= 32 && owner.contains("-")) {
            skull.setPlayerProfile(Bukkit.createProfile(UUID.fromString(owner)));
        }
        else {
            skull.setPlayerProfile(Bukkit.createProfile(owner));
        }
    }

    @Override
    public String getSkullSkin(Skull skull) {
        PlayerProfile playerProfile = skull.getPlayerProfile();
        if (playerProfile == null) {
            return null;
        }

        URL skin = playerProfile.getTextures().getSkin();
        if (skin == null) {
            return null;
        }

        return skin.toString();
    }

    @Override
    public void setSkullSkin(Skull skull, String skin) {
        try {
            if (skin == null || skin.length() == 0) {
                return;
            }

            String skinUrl = SkullSkin.getSkinUrl(skin);
            if (skinUrl == null) {
                return;
            }

            PlayerProfile playerProfile = skull.getPlayerProfile();
            if (playerProfile == null) {
                playerProfile = Bukkit.createProfile(UUID.randomUUID());
            }

            PlayerTextures textures = playerProfile.getTextures();
            textures.setSkin(URI.create(skinUrl).toURL());
            playerProfile.setTextures(textures);
            skull.setPlayerProfile(playerProfile);
        }
        catch (Exception e) {
            ErrorReporter.report(e);
        }
    }

}
