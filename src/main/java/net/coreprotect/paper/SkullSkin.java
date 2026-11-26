package net.coreprotect.paper;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import com.destroystokyo.paper.profile.ProfileProperty;

import net.coreprotect.utility.SkullProfileUtils;
import net.coreprotect.utility.VersionUtils;

final class SkullSkin {

    private static final String SIGNED_PROFILE_STORAGE_VERSION = "24.1";
    private static final String TEXTURES_PROPERTY = "textures";

    private SkullSkin() {
        throw new IllegalStateException("Utility class");
    }

    static boolean isTexturesProperty(ProfileProperty property) {
        return property != null && TEXTURES_PROPERTY.equals(property.getName());
    }

    static String getStorageValue(ProfileProperty property) {
        if (!isTexturesProperty(property) || property.getValue() == null || property.getValue().isBlank()) {
            return null;
        }

        String value = SkullProfileUtils.PROFILE_PROPERTY_PREFIX + property.getValue();
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
        String value = SkullProfileUtils.getTextureValue(skin);
        if (value == null) {
            return null;
        }

        int signatureStart = SkullProfileUtils.PROFILE_PROPERTY_PREFIX.length() + value.length() + 1;
        return signatureStart >= skin.length() ? new ProfileProperty(TEXTURES_PROPERTY, value) : new ProfileProperty(TEXTURES_PROPERTY, value, skin.substring(signatureStart));
    }

    static String getTexturesPropertyValue(String skinUrl) {
        String json = "{\"textures\":{\"SKIN\":{\"url\":\"" + skinUrl.replace("\\", "\\\\").replace("\"", "\\\"") + "\"}}}";
        return Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

}
