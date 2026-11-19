package net.coreprotect.paper;

import java.net.URL;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.block.Sign;
import org.bukkit.block.Skull;
import org.bukkit.block.sign.Side;
import org.bukkit.entity.Player;
import org.bukkit.profile.PlayerTextures;

import com.destroystokyo.paper.profile.PlayerProfile;

import net.coreprotect.config.Config;
import net.coreprotect.utility.ErrorReporter;
import net.coreprotect.utility.SkullProfileUtils;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

public class Paper_v1_20 extends Paper_v1_19 {

    private volatile boolean missingSignInteractableSide;

    @Override
    public Boolean getSignInteractionSide(Sign sign, Player player) {
        Boolean front = super.getSignInteractionSide(sign, player);
        if (front != null) {
            return front;
        }

        if (missingSignInteractableSide) {
            return null;
        }

        Side side;
        try {
            side = sign.getInteractableSideFor(player);
        }
        catch (NoSuchMethodError e) {
            missingSignInteractableSide = true;
            return null;
        }
        return side == null ? null : side == Side.FRONT;
    }

    @Override
    public String getLine(Sign sign, int line) {
        if (line < 0 || line >= 8) {
            return "";
        }
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
        UUID uuid = SkullProfileUtils.parseOwnerUuid(owner);
        if (uuid == null && !SkullProfileUtils.isOwnerName(owner)) {
            return;
        }

        try {
            PlayerProfile profile = uuid == null ? Bukkit.createProfile(owner) : Bukkit.createProfile(uuid);
            skull.setPlayerProfile(profile);
        }
        catch (IllegalArgumentException ignored) {
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
            URL skinUrl = SkullProfileUtils.getSkinUrl(skin);
            if (skinUrl == null) {
                return;
            }

            PlayerProfile playerProfile = skull.getPlayerProfile();
            if (playerProfile == null) {
                playerProfile = Bukkit.createProfile(UUID.randomUUID());
            }

            PlayerTextures textures = playerProfile.getTextures();
            textures.setSkin(skinUrl);
            playerProfile.setTextures(textures);
            skull.setPlayerProfile(playerProfile);
        }
        catch (Exception e) {
            ErrorReporter.report(e);
        }
    }

}
