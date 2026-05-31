package net.coreprotect.paper;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import com.destroystokyo.paper.profile.ProfileProperty;

import net.coreprotect.utility.VersionUtils;

final class SkullSkin {

    static final String PROFILE_PROPERTY_PREFIX = "profile:";

    private static final String SIGNED_PROFILE_STORAGE_VERSION = "24.1";
    private static final String TEXTURES_PROPERTY = "textures";

    private SkullSkin() {
        throw new IllegalStateException("Utility class");
    }

    static boolean isTexturesProperty(ProfileProperty property) {
        return property != null && TEXTURES_PROPERTY.equals(property.getName()) && property.getValue() != null && property.getValue().length() > 0;
    }

    static String getStorageValue(ProfileProperty property) {
        if (!isTexturesProperty(property)) {
            return null;
        }

        String value = PROFILE_PROPERTY_PREFIX + property.getValue();
        if (shouldStoreSignature() && property.getSignature() != null && property.getSignature().length() > 0) {
            value = value + ":" + property.getSignature();
        }

        return value;
    }

    private static boolean shouldStoreSignature() {
        try {
            return !VersionUtils.newVersion(VersionUtils.getPluginVersion(), SIGNED_PROFILE_STORAGE_VERSION);
        }
        catch (Exception e) {
            return true;
        }
    }

    static ProfileProperty getTexturesProperty(String skin) {
        if (skin == null || !skin.startsWith(PROFILE_PROPERTY_PREFIX)) {
            return null;
        }

        String value = skin.substring(PROFILE_PROPERTY_PREFIX.length());
        if (value.length() == 0) {
            return null;
        }

        int signatureIndex = value.indexOf(':');
        if (signatureIndex < 0) {
            return new ProfileProperty(TEXTURES_PROPERTY, value);
        }

        String signature = value.substring(signatureIndex + 1);
        value = value.substring(0, signatureIndex);
        if (signature.length() == 0) {
            return new ProfileProperty(TEXTURES_PROPERTY, value);
        }

        return new ProfileProperty(TEXTURES_PROPERTY, value, signature);
    }

    static String getSkinUrl(String skin) {
        if (skin == null || skin.length() == 0) {
            return null;
        }

        if (!skin.startsWith(PROFILE_PROPERTY_PREFIX)) {
            return skin;
        }

        ProfileProperty property = getTexturesProperty(skin);
        if (property == null) {
            return null;
        }

        try {
            String json = new String(Base64.getDecoder().decode(property.getValue()), StandardCharsets.UTF_8);
            String key = "\"url\":\"";
            int urlStart = json.indexOf(key);
            if (urlStart < 0) {
                return null;
            }

            urlStart += key.length();
            int urlEnd = json.indexOf('"', urlStart);
            if (urlEnd < 0) {
                return null;
            }

            return json.substring(urlStart, urlEnd).replace("\\/", "/");
        }
        catch (IllegalArgumentException e) {
            return null;
        }
    }

    static String getTexturesPropertyValue(String skinUrl) {
        String json = "{\"textures\":{\"SKIN\":{\"url\":\"" + skinUrl.replace("\\", "\\\\").replace("\"", "\\\"") + "\"}}}";
        return Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

}
