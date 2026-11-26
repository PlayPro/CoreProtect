package net.coreprotect.paper;

import java.net.URL;
import java.util.UUID;

import org.bukkit.block.Skull;

import com.destroystokyo.paper.profile.ProfileProperty;

import io.papermc.paper.datacomponent.item.ResolvableProfile;
import net.coreprotect.config.Config;
import net.coreprotect.utility.ErrorReporter;
import net.coreprotect.utility.SkullProfileUtils;

public class Paper_v26_0 extends Paper_v1_20 {

    @Override
    public String getSkullOwner(Skull skull) {
        ResolvableProfile profile = skull.getProfile();
        if (profile == null) {
            return null;
        }

        UUID uuid = profile.uuid();
        if (uuid != null) {
            return uuid.toString();
        }

        String owner = profile.name();
        if (Config.getGlobal().MYSQL && owner != null && owner.length() > 255) {
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

        ResolvableProfile.Builder builder = copyProfile(skull.getProfile(), true);
        if (uuid != null) {
            builder.uuid(uuid);
        }
        else if (!setProfileName(builder, owner)) {
            return;
        }

        skull.setProfile(builder.build());
    }

    @Override
    public String getSkullSkin(Skull skull) {
        ResolvableProfile profile = skull.getProfile();
        if (profile != null) {
            for (ProfileProperty property : profile.properties()) {
                String skin = SkullSkin.getStorageValue(property);
                if (skin != null) {
                    return skin;
                }
            }
        }

        return super.getSkullSkin(skull);
    }

    @Override
    public void setSkullSkin(Skull skull, String skin) {
        try {
            if (skin == null || skin.length() == 0) {
                return;
            }

            ProfileProperty property = SkullSkin.getTexturesProperty(skin);
            if (property == null) {
                URL skinUrl = SkullProfileUtils.getSkinUrl(skin);
                if (skinUrl == null) {
                    return;
                }

                property = new ProfileProperty("textures", SkullSkin.getTexturesPropertyValue(skinUrl.toString()));
            }

            ResolvableProfile.Builder builder = copyProfile(skull.getProfile(), false);
            builder.addProperty(property);
            skull.setProfile(builder.build());
        }
        catch (Exception e) {
            ErrorReporter.report(e);
        }
    }

    private ResolvableProfile.Builder copyProfile(ResolvableProfile profile, boolean replacingOwner) {
        ResolvableProfile.Builder builder = ResolvableProfile.resolvableProfile();
        if (profile == null) {
            return builder;
        }

        if (!replacingOwner) {
            if (profile.uuid() != null) {
                builder.uuid(profile.uuid());
            }
            setProfileName(builder, profile.name());
        }
        if (profile.skinPatch() != null) {
            builder.skinPatch(profile.skinPatch());
        }

        for (ProfileProperty property : profile.properties()) {
            if (replacingOwner || !SkullSkin.isTexturesProperty(property)) {
                builder.addProperty(property);
            }
        }

        return builder;
    }

    private static boolean setProfileName(ResolvableProfile.Builder builder, String name) {
        if (!SkullProfileUtils.isOwnerName(name)) {
            return false;
        }

        try {
            builder.name(name);
            return true;
        }
        catch (IllegalArgumentException e) {
            return false;
        }
    }

}
