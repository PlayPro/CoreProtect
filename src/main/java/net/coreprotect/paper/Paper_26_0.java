package net.coreprotect.paper;

import java.util.UUID;

import org.bukkit.block.Skull;

import com.destroystokyo.paper.profile.ProfileProperty;

import io.papermc.paper.datacomponent.item.ResolvableProfile;
import net.coreprotect.config.Config;
import net.coreprotect.utility.ErrorReporter;

public class Paper_26_0 extends Paper_v1_20 {

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
        if (owner == null || owner.length() == 0) {
            return;
        }

        ResolvableProfile.Builder builder = copyProfile(skull.getProfile(), true);
        if (owner.length() >= 32 && owner.contains("-")) {
            builder.uuid(UUID.fromString(owner));
        }
        else if (owner.length() <= 16) {
            builder.name(owner);
        }
        else {
            super.setSkullOwner(skull, owner);
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
                String skinUrl = SkullSkin.getSkinUrl(skin);
                if (skinUrl == null) {
                    return;
                }

                property = new ProfileProperty("textures", SkullSkin.getTexturesPropertyValue(skinUrl));
            }

            ResolvableProfile.Builder builder = copyProfile(skull.getProfile(), false);
            builder.addProperty(property);
            skull.setProfile(builder.build());
        }
        catch (Exception e) {
            ErrorReporter.report(e);
        }
    }

    private ResolvableProfile.Builder copyProfile(ResolvableProfile profile, boolean includeSkin) {
        ResolvableProfile.Builder builder = ResolvableProfile.resolvableProfile();
        if (profile == null) {
            return builder;
        }

        if (profile.uuid() != null) {
            builder.uuid(profile.uuid());
        }
        if (profile.name() != null) {
            builder.name(profile.name());
        }
        if (profile.skinPatch() != null) {
            builder.skinPatch(profile.skinPatch());
        }

        for (ProfileProperty property : profile.properties()) {
            if (includeSkin || !SkullSkin.isTexturesProperty(property)) {
                builder.addProperty(property);
            }
        }

        return builder;
    }

}
