package io.github.michelbr84.flapforge.content;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonSyntaxException;
import com.google.gson.ToNumberPolicy;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Reads {@code data/&lt;name&gt;.json} from the classpath into a {@link JsonElement} tree (D10).
 * Nothing is bound here: {@link StrictBinder} turns the tree into records, so a syntax error and
 * a schema error are reported separately.
 *
 * <p>The {@link Gson} instance uses {@link ToNumberPolicy#LONG_OR_DOUBLE}: a JSON number without
 * a fraction or exponent is a {@code long}, everything else is a {@code double}. The binder
 * applies the same rule when it converts a primitive, so {@code 9007199254740993} survives into a
 * {@code long} component instead of being rounded through {@code double}.
 */
public final class ContentLoader {

    /** Classpath directory holding the shipped content files. */
    public static final String DATA_DIR = "/data/";

    /** The content files milestone M1 ships (base names, without the {@code .json} suffix). */
    public static final List<String> M1_FILES = List.of("birds", "difficulty");

    /** The content files milestone M3 ships: {@link #M1_FILES} plus the economy (§4). */
    public static final List<String> M3_FILES = List.of("birds", "difficulty", "economy");

    /** The content files the game currently loads (the latest milestone's set). */
    public static final List<String> FILES = M3_FILES;

    private static final Gson GSON = new GsonBuilder()
            .setObjectToNumberStrategy(ToNumberPolicy.LONG_OR_DOUBLE)
            .setNumberToNumberStrategy(ToNumberPolicy.LONG_OR_DOUBLE)
            .create();

    private ContentLoader() {
    }

    /**
     * The shared, immutable Gson instance (number policy {@code LONG_OR_DOUBLE}).
     *
     * @return the instance
     */
    public static Gson gson() {
        return GSON;
    }

    /**
     * Reads one shipped content file.
     *
     * @param name the base name, for example {@code birds}
     * @return the parsed tree
     * @throws ContentException when the resource is missing or is not valid JSON
     */
    public static JsonElement load(String name) {
        Objects.requireNonNull(name, "name");
        String resource = DATA_DIR + name + ".json";
        try (InputStream in = ContentLoader.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new ContentException("Content file not found on the classpath",
                        List.of(fileOf(name) + "#: missing resource " + resource));
            }
            return parse(name, new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read " + resource, e);
        }
    }

    /**
     * Reads every file of a milestone into a map keyed by base name.
     *
     * @param names the base names
     * @return the trees, in the order given
     * @throws ContentException when a file is missing or malformed
     */
    public static Map<String, JsonElement> loadAll(List<String> names) {
        Map<String, JsonElement> out = new LinkedHashMap<>();
        for (String name : names) {
            out.put(name, load(name));
        }
        return out;
    }

    /**
     * Parses JSON from a reader (fixtures, tests and tools).
     *
     * @param name the base name used in error locations
     * @param reader the source; closed by the caller
     * @return the parsed tree
     * @throws ContentException when the text is not valid JSON
     */
    public static JsonElement parse(String name, Reader reader) {
        try {
            JsonElement root = GSON.fromJson(reader, JsonElement.class);
            if (root == null || root.isJsonNull()) {
                throw new ContentException("Empty content file",
                        List.of(fileOf(name) + "#: the file is empty"));
            }
            return root;
        } catch (JsonSyntaxException e) {
            throw new ContentException("Malformed content file",
                    List.of(fileOf(name) + "#: " + e.getMessage()));
        }
    }

    /**
     * Parses JSON from a string (fixtures, tests and tools).
     *
     * @param name the base name used in error locations
     * @param json the JSON text
     * @return the parsed tree
     * @throws ContentException when the text is not valid JSON
     */
    public static JsonElement parse(String name, String json) {
        return parse(name, new java.io.StringReader(json));
    }

    /**
     * The file label used in error locations.
     *
     * @param name the base name
     * @return {@code name + ".json"}
     */
    public static String fileOf(String name) {
        return name + ".json";
    }
}
