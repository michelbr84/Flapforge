package io.github.michelbr84.flapforge.support;

import com.google.gson.JsonElement;
import io.github.michelbr84.flapforge.content.ContentLoader;
import io.github.michelbr84.flapforge.content.GameContent;
import io.github.michelbr84.flapforge.content.RunFactory;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads the frozen content fixture in {@code src/test/resources/fixtures/content_frozen} — a copy
 * of the shipped {@code data/*.json} taken when the golden run was recorded.
 *
 * <p>Tests that must not change when the shipped balance changes (the golden run above all) read
 * the frozen copy; tests that check what actually ships read {@link GameContent#load()}. The two
 * are deliberately <em>not</em> tied together (D12): the shipped files evolve with the balance,
 * the fixture only moves when the golden run is re-recorded on purpose.
 * {@code ContentIntegrityTest.theFrozenFixtureValidatesAndStillResolvesToTheClassicTable} keeps
 * the frozen copy honest on its own terms.
 */
public final class TestContent {

    /** Classpath directory of the frozen fixture. */
    public static final String DIR = "/fixtures/content_frozen/";

    private static GameContent frozen;

    private TestContent() {
    }

    /**
     * The frozen content, bound and validated once per JVM.
     *
     * @return the content
     */
    public static synchronized GameContent frozen() {
        if (frozen == null) {
            frozen = GameContent.fromJson(frozenJson());
        }
        return frozen;
    }

    /**
     * A run factory over the frozen content.
     *
     * @return the factory
     */
    public static RunFactory frozenFactory() {
        return new RunFactory(frozen());
    }

    /**
     * The parsed frozen files keyed by base name.
     *
     * @return the trees
     */
    public static Map<String, JsonElement> frozenJson() {
        Map<String, JsonElement> out = new LinkedHashMap<>();
        for (String name : files()) {
            out.put(name, ContentLoader.parse(name, read(name)));
        }
        return out;
    }

    /**
     * The raw text of one frozen file.
     *
     * @param name the base name, for example {@code birds}
     * @return the file content
     */
    public static String read(String name) {
        return readResource(DIR + name + ".json");
    }

    private static String readResource(String resource) {
        try (InputStream in = TestContent.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("Missing fixture " + resource);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read " + resource, e);
        }
    }

    /** Classpath directory of the deliberately broken fixtures (M4). */
    public static final String BAD_DIR = "/fixtures/content_bad/";

    /**
     * The shipped content files, parsed.
     *
     * @return the trees keyed by base name
     */
    public static Map<String, JsonElement> shippedJson() {
        return ContentLoader.loadAll(ContentLoader.FILES);
    }

    /**
     * The shipped content with one file swapped for a broken fixture.
     *
     * <p>Each {@code fixtures/content_bad/*.json} file is a copy of one shipped file with a
     * single defect, so a validator rule can be pinned to its own message and pointer without a
     * whole second content set going stale beside the real one.
     *
     * @param file the base name the fixture replaces, for example {@code upgrades}
     * @param fixture the fixture name, without the {@code .json}
     * @return the trees keyed by base name
     */
    public static Map<String, JsonElement> shippedWith(String file, String fixture) {
        Map<String, JsonElement> files = new LinkedHashMap<>(shippedJson());
        files.put(file, ContentLoader.parse(file, readBad(fixture)));
        return files;
    }

    /**
     * The raw text of one broken fixture.
     *
     * @param name the fixture name, without the {@code .json}
     * @return the file content
     */
    public static String readBad(String name) {
        return readResource(BAD_DIR + name + ".json");
    }

    /**
     * The base names of the frozen files.
     *
     * <p>The fixture stays the M3 file set: it is the content the golden run was recorded
     * against, and it doubles as the "a milestone's data validates on its own" case of E19 —
     * cross-references into files it does not carry are not checked
     * ({@code GameContent.has}). The M4 files are exercised through the shipped content and
     * through {@code fixtures/content_bad/*}.
     *
     * @return the names
     */
    public static List<String> files() {
        return ContentLoader.M3_FILES;
    }
}
