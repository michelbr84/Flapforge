package io.github.michelbr84.flapforge.persistence;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.google.gson.ToNumberPolicy;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * The one JSON configuration the persisted files use (D15): numbers bind with
 * {@link ToNumberPolicy#LONG_OR_DOUBLE} (an integral literal stays a {@code long}), output is
 * pretty-printed so a player can read and hand-edit a save, and HTML escaping is off so
 * {@code &amp;}, {@code &lt;} and quotes survive a round trip unchanged.
 *
 * <p>{@link #overlay(JsonObject, JsonObject)} implements the forward-compatible write rule (E22):
 * the file that was read is kept as a tree and the freshly serialised state is laid over it, so a
 * key written by a newer version of the game — at any depth — survives being loaded and saved by
 * this one. Merging recurses only into objects that <em>both</em> sides have; arrays and every
 * other value are replaced wholesale. Nodes that model a map or a list of ids
 * ({@code profile.upgrades}, {@code profile.unlocked}, …) must also be replaced wholesale, or a
 * removed entry would come back to life; those paths are named through
 * {@link #overlay(JsonObject, JsonObject, Set)}.
 */
public final class JsonCodec {

    private static final Gson GSON = new GsonBuilder()
            .setObjectToNumberStrategy(ToNumberPolicy.LONG_OR_DOUBLE)
            .setNumberToNumberStrategy(ToNumberPolicy.LONG_OR_DOUBLE)
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();

    private JsonCodec() {
    }

    /**
     * The shared, immutable Gson instance.
     *
     * @return the instance
     */
    public static Gson gson() {
        return GSON;
    }

    /**
     * Parses text into an object tree.
     *
     * @param text the JSON text
     * @return the object, or {@code null} when the text is empty, not JSON, or not an object
     */
    public static JsonObject parseObject(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            JsonElement root = GSON.fromJson(text, JsonElement.class);
            return root != null && root.isJsonObject() ? root.getAsJsonObject() : null;
        } catch (JsonSyntaxException e) {
            return null;
        }
    }

    /**
     * Serialises a value to a tree.
     *
     * @param value the value
     * @return the tree ({@code JsonNull} for {@code null})
     */
    public static JsonElement toTree(Object value) {
        return GSON.toJsonTree(value);
    }

    /**
     * Serialises a value to an object tree.
     *
     * @param value the value; must serialise to a JSON object
     * @return the object
     */
    public static JsonObject toObject(Object value) {
        JsonElement tree = toTree(value);
        if (!tree.isJsonObject()) {
            throw new IllegalArgumentException(
                    "value does not serialise to a JSON object: " + value);
        }
        return tree.getAsJsonObject();
    }

    /**
     * Binds a tree to a type.
     *
     * @param tree the tree, may be {@code null}
     * @param type the target type
     * @param <T> the target type
     * @return the bound value, or {@code null}
     * @throws JsonSyntaxException when the tree does not fit the type
     */
    public static <T> T fromTree(JsonElement tree, Class<T> type) {
        Objects.requireNonNull(type, "type");
        return tree == null ? null : GSON.fromJson(tree, type);
    }

    /**
     * Renders a tree as pretty-printed UTF-8 text.
     *
     * @param tree the tree
     * @return the text, ending without a newline
     */
    public static String toJson(JsonElement tree) {
        return GSON.toJson(tree);
    }

    /**
     * Lays {@code fresh} over {@code original} (E22).
     *
     * @param original the tree that was read from disk; may be {@code null} or empty
     * @param fresh the freshly serialised state
     * @return a new tree; neither argument is modified
     */
    public static JsonObject overlay(JsonObject original, JsonObject fresh) {
        return overlay(original, fresh, Set.of());
    }

    /**
     * Lays {@code fresh} over {@code original}, replacing the named paths wholesale (E22).
     *
     * @param original the tree that was read from disk; may be {@code null} or empty
     * @param fresh the freshly serialised state
     * @param replaceWholesale dotted paths of map-typed or list-typed nodes that must not be
     *     merged, for example {@code profile.upgrades}
     * @return a new tree; neither argument is modified
     */
    public static JsonObject overlay(JsonObject original, JsonObject fresh,
            Set<String> replaceWholesale) {
        Objects.requireNonNull(fresh, "fresh");
        Set<String> replace = replaceWholesale == null ? Set.of() : replaceWholesale;
        JsonObject base = original == null ? new JsonObject() : original;
        return merge(base, fresh, replace, "");
    }

    private static JsonObject merge(JsonObject original, JsonObject fresh, Set<String> replace,
            String path) {
        JsonObject out = original.deepCopy();
        for (Map.Entry<String, JsonElement> entry : fresh.entrySet()) {
            String key = entry.getKey();
            String childPath = path.isEmpty() ? key : path + '.' + key;
            JsonElement freshValue = entry.getValue();
            JsonElement oldValue = out.get(key);
            boolean mergeable = !replace.contains(childPath)
                    && oldValue != null && oldValue.isJsonObject() && freshValue.isJsonObject();
            if (mergeable) {
                out.add(key, merge(oldValue.getAsJsonObject(), freshValue.getAsJsonObject(),
                        replace, childPath));
            } else {
                out.add(key, freshValue.deepCopy());
            }
        }
        return out;
    }
}
