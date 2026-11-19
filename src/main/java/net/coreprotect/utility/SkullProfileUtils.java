package net.coreprotect.utility;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

public final class SkullProfileUtils {

    public static final String PROFILE_PROPERTY_PREFIX = "profile:";

    private SkullProfileUtils() {
        throw new IllegalStateException("Utility class");
    }

    public static UUID parseOwnerUuid(String owner) {
        if (owner == null || owner.length() != 36) {
            return null;
        }

        try {
            UUID uuid = UUID.fromString(owner);
            return uuid.toString().equalsIgnoreCase(owner) ? uuid : null;
        }
        catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static boolean isOwnerName(String owner) {
        return owner != null && owner.length() <= 16 && !owner.isBlank();
    }

    public static String getTextureValue(String skin) {
        if (skin == null || !skin.startsWith(PROFILE_PROPERTY_PREFIX)) {
            return null;
        }

        int start = PROFILE_PROPERTY_PREFIX.length();
        int signatureIndex = skin.indexOf(':', start);
        String value = signatureIndex < 0 ? skin.substring(start) : skin.substring(start, signatureIndex);
        return value.isBlank() ? null : value;
    }

    public static URL getSkinUrl(String skin) {
        if (skin == null || skin.isBlank()) {
            return null;
        }

        try {
            String url = skin.startsWith(PROFILE_PROPERTY_PREFIX) ? decodeSkinUrl(getTextureValue(skin)) : skin;
            return url == null ? null : URI.create(url).toURL();
        }
        catch (IllegalArgumentException | JsonParseException | MalformedURLException e) {
            return null;
        }
    }

    private static String decodeSkinUrl(String value) {
        if (value == null) {
            return null;
        }

        String json = new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
        JsonElement root = new JsonParser().parse(json);
        JsonElement skin = getChild(getChild(root, "textures"), "SKIN");
        JsonElement url = getChild(skin, "url");
        return url != null && url.isJsonPrimitive() && url.getAsJsonPrimitive().isString() ? url.getAsString() : null;
    }

    private static JsonElement getChild(JsonElement parent, String name) {
        JsonObject object = parent != null && parent.isJsonObject() ? parent.getAsJsonObject() : null;
        return object == null ? null : object.get(name);
    }

}
