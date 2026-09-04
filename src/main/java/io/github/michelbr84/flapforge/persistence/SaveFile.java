package io.github.michelbr84.flapforge.persistence;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import io.github.michelbr84.flapforge.progression.PlayerProfile;

/**
 * The envelope around a saved profile (§4 "SaveData v1", D15):
 * {@code {version, appVersion, contentVersion, savedAtEpochMs, profile}}.
 *
 * <p>{@code version} is the schema version — the only field the loader reads before it decides
 * whether it can read the rest at all — and every change to the persisted shape from
 * {@code v0.1.0} on raises it and ships a {@link Migration}. {@code appVersion} and
 * {@code contentVersion} are diagnostics: they say which build and which content the file was last
 * written by, so a bug report and a {@code SaveInspector} dump can be matched to a release without
 * being load-bearing.
 *
 * <p>The envelope is encoded by hand rather than by reflection so the key order in the file is the
 * documented one and never depends on a JSON library's field ordering.
 *
 * @param version the schema version of the file
 * @param appVersion the game version that wrote it
 * @param contentVersion the content version that wrote it
 * @param savedAtEpochMs when it was written, from the injected time source
 * @param profile the saved profile
 */
public record SaveFile(int version, String appVersion, int contentVersion, long savedAtEpochMs,
        PlayerProfile profile) {

    /** The schema version this build reads and writes. */
    public static final int VERSION = 1;
    /** {@code appVersion} written when the build does not know its own version. */
    public static final String UNKNOWN_APP_VERSION = "unknown";
    /** The content version this build ships. */
    public static final int CONTENT_VERSION = 1;

    /** Key of the schema version. */
    public static final String KEY_VERSION = "version";
    /** Key of the game version. */
    public static final String KEY_APP_VERSION = "appVersion";
    /** Key of the content version. */
    public static final String KEY_CONTENT_VERSION = "contentVersion";
    /** Key of the write timestamp. */
    public static final String KEY_SAVED_AT = "savedAtEpochMs";
    /** Key of the profile. */
    public static final String KEY_PROFILE = "profile";

    /**
     * Replaces a null profile and a null version string, so the envelope is always writable.
     *
     * @param version the schema version
     * @param appVersion the game version
     * @param contentVersion the content version
     * @param savedAtEpochMs the write timestamp
     * @param profile the profile
     */
    public SaveFile {
        appVersion = appVersion == null || appVersion.isBlank()
                ? UNKNOWN_APP_VERSION : appVersion;
        profile = profile == null ? new PlayerProfile() : profile;
    }

    /**
     * An envelope for the current schema version.
     *
     * @param profile the profile to wrap
     * @param appVersion the game version
     * @param contentVersion the content version
     * @param savedAtEpochMs the write timestamp
     * @return the envelope
     */
    public static SaveFile of(PlayerProfile profile, String appVersion, int contentVersion,
            long savedAtEpochMs) {
        return new SaveFile(VERSION, appVersion, contentVersion, savedAtEpochMs, profile);
    }

    /**
     * Encodes the envelope, in the documented key order.
     *
     * @return the tree
     */
    public JsonObject toJson() {
        JsonObject root = new JsonObject();
        root.addProperty(KEY_VERSION, version);
        root.addProperty(KEY_APP_VERSION, appVersion);
        root.addProperty(KEY_CONTENT_VERSION, contentVersion);
        root.addProperty(KEY_SAVED_AT, savedAtEpochMs);
        root.add(KEY_PROFILE, JsonCodec.toTree(profile));
        return root;
    }

    /**
     * Binds a tree that has already been migrated to the current version.
     *
     * @param root the tree
     * @return the envelope; the profile is bound but <em>not</em> normalised
     * @throws JsonParseException when {@code profile} does not fit {@link PlayerProfile}
     */
    public static SaveFile fromJson(JsonObject root) {
        PlayerProfile profile = JsonCodec.fromTree(root.get(KEY_PROFILE), PlayerProfile.class);
        return new SaveFile(versionOf(root), stringOf(root, KEY_APP_VERSION, UNKNOWN_APP_VERSION),
                intOf(root, KEY_CONTENT_VERSION, CONTENT_VERSION),
                longOf(root, KEY_SAVED_AT), profile == null ? new PlayerProfile() : profile);
    }

    /**
     * The schema version a tree declares.
     *
     * <p>A file with no {@code version} at all is read as the current version: §4's rule for the
     * persisted files is "missing keys default", and a hand-edited save that dropped the line is
     * far more likely than a file from a build that never wrote one. A version that is present but
     * is not a number cannot have been written by any build, so it reads as 0 and the file is
     * treated as unreadable.
     *
     * @param root the tree
     * @return the version
     */
    public static int versionOf(JsonObject root) {
        JsonElement value = root.get(KEY_VERSION);
        if (value == null) {
            return VERSION;
        }
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            return 0;
        }
        try {
            return value.getAsInt();
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String stringOf(JsonObject root, String key, String fallback) {
        JsonElement value = root.get(key);
        return value != null && value.isJsonPrimitive() ? value.getAsString() : fallback;
    }

    private static int intOf(JsonObject root, String key, int fallback) {
        JsonElement value = root.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            return fallback;
        }
        try {
            return value.getAsInt();
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static long longOf(JsonObject root, String key) {
        JsonElement value = root.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            return 0;
        }
        try {
            return value.getAsLong();
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
