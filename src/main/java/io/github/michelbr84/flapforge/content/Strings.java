package io.github.michelbr84.flapforge.content;

import com.google.gson.JsonElement;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * The active string table (D25). {@code data/strings/en.json} is the source of truth; another
 * language is laid over it key by key, so a translation that is missing a key falls back to
 * English instead of showing a raw key to the player.
 *
 * <p>Placeholders are positional and plain: {@code {0}}, {@code {1}}, … are replaced by the
 * arguments of {@link #format(StringKey, Object...)} with nothing else touched (E30.h). This is
 * deliberately <em>not</em> {@code MessageFormat}: an apostrophe would start a quoted section
 * there ("That's {0}" would lose the placeholder) and a number would be grouped by locale
 * ("1234" would become "1,234"), which is wrong for a score.
 *
 * <p>An unknown key returns the key itself — the UI stays usable — and is recorded in
 * {@link #missingKeys()} so a test can fail on it instead of a player finding it.
 */
public final class Strings {

    /** Classpath directory holding the string files. */
    public static final String DIR = "/data/strings/";
    /** The language every other language falls back to. */
    public static final String SOURCE_LANGUAGE = "en";
    /** The languages that ship. */
    public static final List<String> LANGUAGES = List.of("en", "pt_BR");
    /** Suffix of the key holding a content entry's display name. */
    public static final String NAME_SUFFIX = ".name";
    /** Suffix of the key holding a content entry's description. */
    public static final String DESC_SUFFIX = ".desc";

    private static volatile Strings active;

    private final Set<String> missing = new LinkedHashSet<>();
    private Map<String, String> table;
    private String language;

    private Strings(String language, Map<String, String> table) {
        this.language = language;
        this.table = table;
    }

    /**
     * Loads a language over the English source table.
     *
     * @param language {@code en}, {@code pt_BR}, or any language whose file ships; a language
     *     without a file falls back to English entirely
     * @return the table
     * @throws ContentException when {@code en.json} is missing or malformed
     */
    public static Strings load(String language) {
        String lang = language == null || language.isBlank() ? SOURCE_LANGUAGE : language;
        return new Strings(lang, merged(lang));
    }

    /**
     * Builds a table from tables the caller supplies: {@code overlay} wins key by key and
     * {@code source} is the fallback. Used by tests and tools; the game loads from the classpath.
     *
     * @param language the name to report as {@link #language()}
     * @param source the fallback table (the role {@code en.json} plays)
     * @param overlay the preferred table, may be {@code null}
     * @return the table
     */
    public static Strings of(String language, Map<String, String> source,
            Map<String, String> overlay) {
        Objects.requireNonNull(source, "source");
        Map<String, String> merged = new LinkedHashMap<>(source);
        if (overlay != null) {
            merged.putAll(overlay);
        }
        return new Strings(language == null ? SOURCE_LANGUAGE : language,
                Collections.unmodifiableMap(merged));
    }

    /**
     * The table the presentation layer is currently drawing with; English on first use.
     *
     * @return the active table
     */
    public static Strings active() {
        Strings current = active;
        if (current == null) {
            synchronized (Strings.class) {
                current = active;
                if (current == null) {
                    current = load(SOURCE_LANGUAGE);
                    active = current;
                }
            }
        }
        return current;
    }

    /**
     * Makes a table the active one.
     *
     * @param strings the table
     */
    public static void use(Strings strings) {
        active = Objects.requireNonNull(strings, "strings");
    }

    /**
     * Reads one string file without any fallback (the validator and the tools).
     *
     * @param language the language
     * @return key to text, in file order
     * @throws ContentException when the file is missing, malformed or holds a non-string value
     */
    public static Map<String, String> tableOf(String language) {
        Objects.requireNonNull(language, "language");
        String file = fileOf(language);
        String text = read(language);
        if (text == null) {
            throw new ContentException("String file not found on the classpath",
                    List.of(file + "#: missing resource " + DIR + language + ".json"));
        }
        JsonElement root = ContentLoader.parse("strings/" + language, text);
        if (!root.isJsonObject()) {
            throw new ContentException("Malformed string file",
                    List.of(file + "#: the file must be a flat JSON object"));
        }
        Map<String, String> out = new LinkedHashMap<>();
        List<String> errors = new ArrayList<>();
        for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject().entrySet()) {
            JsonElement value = entry.getValue();
            if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
                out.put(entry.getKey(), value.getAsString());
            } else {
                errors.add(file + "#/" + entry.getKey() + ": value must be a string");
            }
        }
        if (!errors.isEmpty()) {
            throw new ContentException("Malformed string file", errors);
        }
        return Collections.unmodifiableMap(out);
    }

    /**
     * Whether a language file ships.
     *
     * @param language the language
     * @return {@code true} when the resource exists
     */
    public static boolean exists(String language) {
        return language != null && read(language) != null;
    }

    /**
     * The label of a string file in error messages.
     *
     * @param language the language
     * @return {@code strings/&lt;language&gt;.json}
     */
    public static String fileOf(String language) {
        return "strings/" + language + ".json";
    }

    /**
     * Swaps the active table of this instance (Settings changes the language live).
     *
     * @param language the new language
     * @return {@code this}
     */
    public Strings reload(String language) {
        String lang = language == null || language.isBlank() ? SOURCE_LANGUAGE : language;
        this.table = merged(lang);
        this.language = lang;
        this.missing.clear();
        return this;
    }

    /**
     * The language this table was loaded for.
     *
     * @return the language
     */
    public String language() {
        return language;
    }

    /**
     * The text of a code-referenced key.
     *
     * @param key the key
     * @return the text, or the key itself when it is missing
     */
    public String get(StringKey key) {
        Objects.requireNonNull(key, "key");
        return text(key.key());
    }

    /**
     * The text of a code-referenced key with {@code {0}}-style arguments substituted.
     *
     * @param key the key
     * @param args the arguments
     * @return the formatted text
     */
    public String format(StringKey key, Object... args) {
        return substitute(get(key), args);
    }

    /**
     * The text of a raw key (content-derived keys and tools).
     *
     * @param key the key
     * @return the text, or the key itself when it is missing
     */
    public String text(String key) {
        Objects.requireNonNull(key, "key");
        String value = table.get(key);
        if (value == null) {
            missing.add(key);
            return key;
        }
        return value;
    }

    /**
     * The text of a raw key with {@code {0}}-style arguments substituted.
     *
     * @param key the key
     * @param args the arguments
     * @return the formatted text
     */
    public String format(String key, Object... args) {
        return substitute(text(key), args);
    }

    /**
     * The display name of a content entry: {@code &lt;kind&gt;.&lt;id&gt;.name}.
     *
     * @param kind the kind, for example {@code bird}
     * @param id the entry id; a cosmetic uses {@code &lt;bird&gt;.&lt;palette&gt;}
     * @return the name, or the key itself when it is missing
     */
    public String name(String kind, String id) {
        return text(nameKey(kind, id));
    }

    /**
     * The description of a content entry: {@code &lt;kind&gt;.&lt;id&gt;.desc}.
     *
     * @param kind the kind, for example {@code bird}
     * @param id the entry id
     * @return the description, or the key itself when it is missing
     */
    public String desc(String kind, String id) {
        return text(descKey(kind, id));
    }

    /**
     * Whether a key resolves.
     *
     * @param key the key
     * @return {@code true} when the table holds it
     */
    public boolean has(String key) {
        return table.containsKey(key);
    }

    /**
     * Every key that was asked for and not found, in the order they were first asked for.
     *
     * @return an unmodifiable set
     */
    public Set<String> missingKeys() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(missing));
    }

    /**
     * Forgets the recorded misses.
     */
    public void clearMissing() {
        missing.clear();
    }

    /**
     * The whole table, for the language chooser and the tools.
     *
     * @return key to text, English overlaid by the active language
     */
    public Map<String, String> asMap() {
        return table;
    }

    /**
     * The name key of a content entry.
     *
     * @param kind the kind
     * @param id the id
     * @return {@code &lt;kind&gt;.&lt;id&gt;.name}
     */
    public static String nameKey(String kind, String id) {
        return kind + '.' + id + NAME_SUFFIX;
    }

    /**
     * The description key of a content entry.
     *
     * @param kind the kind
     * @param id the id
     * @return {@code &lt;kind&gt;.&lt;id&gt;.desc}
     */
    public static String descKey(String kind, String id) {
        return kind + '.' + id + DESC_SUFFIX;
    }

    /**
     * Replaces {@code {0}}, {@code {1}}, … with the arguments. Everything else — apostrophes,
     * braces without a number, an index with no argument — is left exactly as it is (E30.h).
     *
     * @param pattern the text
     * @param args the arguments
     * @return the substituted text
     */
    public static String substitute(String pattern, Object... args) {
        if (pattern == null || args == null || args.length == 0 || pattern.indexOf('{') < 0) {
            return pattern;
        }
        StringBuilder out = new StringBuilder(pattern.length() + 16);
        int i = 0;
        while (i < pattern.length()) {
            char c = pattern.charAt(i);
            if (c != '{') {
                out.append(c);
                i++;
                continue;
            }
            int close = pattern.indexOf('}', i + 1);
            int index = close < 0 ? -1 : indexOf(pattern, i + 1, close);
            if (index < 0 || index >= args.length) {
                out.append(c);
                i++;
                continue;
            }
            out.append(String.valueOf(args[index]));
            i = close + 1;
        }
        return out.toString();
    }

    /** Parses the digits between {@code from} (inclusive) and {@code to} (exclusive), or -1. */
    private static int indexOf(String pattern, int from, int to) {
        if (to <= from || to - from > 4) {
            return -1;
        }
        int value = 0;
        for (int i = from; i < to; i++) {
            char c = pattern.charAt(i);
            if (c < '0' || c > '9') {
                return -1;
            }
            value = value * 10 + (c - '0');
        }
        return value;
    }

    private static Map<String, String> merged(String language) {
        Map<String, String> out = new LinkedHashMap<>(tableOf(SOURCE_LANGUAGE));
        if (!SOURCE_LANGUAGE.equals(language) && exists(language)) {
            out.putAll(tableOf(language));
        }
        return Collections.unmodifiableMap(out);
    }

    private static String read(String language) {
        String resource = DIR + language + ".json";
        try (InputStream in = Strings.class.getResourceAsStream(resource)) {
            if (in == null) {
                return null;
            }
            try (InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                StringBuilder text = new StringBuilder();
                char[] buffer = new char[4096];
                int read;
                while ((read = reader.read(buffer)) != -1) {
                    text.append(buffer, 0, read);
                }
                return text.toString();
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read " + resource, e);
        }
    }

    @Override
    public String toString() {
        return "Strings{" + language + ", " + table.size() + " keys}";
    }
}
