package jrecord;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.gson.JsonParser;
import io.github.michelbr84.flapforge.content.JsonName;
import io.github.michelbr84.flapforge.content.StrictBinder;
import io.github.michelbr84.flapforge.content.defs.BirdDef;
import io.github.michelbr84.flapforge.content.defs.PaletteDef;
import io.github.michelbr84.flapforge.content.defs.TierDef;
import io.github.michelbr84.flapforge.gameplay.TickFact;
import io.github.michelbr84.flapforge.gameplay.run.RunConfig;
import io.github.michelbr84.flapforge.gameplay.spec.BossSpec;
import io.github.michelbr84.flapforge.input.RawInput;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.Test;

/**
 * The record table and the {@code jrecord} shim (M10 hotfix), on the JVM where records are
 * real, so the table can be checked against the platform's own answer: every entry of
 * {@code META-INF/flapforge-records.properties} loads, names real fields, in the order the
 * canonical constructor takes (which is what the desugared record on ART still has); the
 * table covers every {@code record} declared in the transformed tree; the components the shim
 * hands out follow the declaration order — never the alphabetical dex order that would swap
 * two same-typed components — and match the platform's type, generic type and annotation; a
 * record absent from the table binds through the platform fallback; a table entry without
 * its field is a loud error; and the transformed {@code StrictBinder} calls the shim and not
 * the platform (the T4 rules).
 */
public class RecordsTest {

    private static final String TRANSFORMED_ROOT = "android/build/transformed/java";
    private static final String TRANSFORMED_BINDER =
            TRANSFORMED_ROOT + "/io/github/michelbr84/flapforge/content/StrictBinder.java";
    private static final String ANDROID_BUILD_FILE = "android/build.gradle";
    /** A record declaration at the start of a line, with the modifiers a record may carry. */
    private static final Pattern RECORD_DECLARATION = Pattern.compile(
            "(?m)^[ \\t]*(?:(?:public|private|protected|static|final|sealed|non-sealed)\\s+)*"
                    + "record\\s+([A-Z]\\w*)\\s*[(<]");
    /** The tree declares 128 files with records (M10 hotfix census); the table lists more. */
    private static final int MIN_RECORDS = 128;

    /** A record the build-time table cannot know: the platform fallback binds it. */
    record Probe(String first, String second, List<Integer> numbers) {
    }

    // ---------------------------------------------------------------- the table

    @Test
    public void tableIsOnTheClasspathAndCoversEveryRecordOfTheTransformedTree()
            throws Exception {
        Map<String, List<String>> table = table();
        assertTrue("at least " + MIN_RECORDS + " records, found " + table.size(),
                table.size() >= MIN_RECORDS);

        // Every "record X(" of the transformed tree, by simple name, against the table's
        // classes by their own simple name: no record is missed, none is invented.
        List<String> declared = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(repoRoot().resolve(TRANSFORMED_ROOT))) {
            for (Path file : walk.filter(p -> p.toString().endsWith(".java"))
                    .collect(Collectors.toList())) {
                Matcher m = RECORD_DECLARATION.matcher(read(file));
                while (m.find()) {
                    declared.add(m.group(1));
                }
            }
        }
        List<String> listed = new ArrayList<>();
        for (String name : table.keySet()) {
            listed.add(Class.forName(name).getSimpleName());
        }
        Collections.sort(declared);
        Collections.sort(listed);
        assertEquals("the table lists exactly the records the transformed tree declares",
                declared, listed);

        // A few by name, nested and top-level, so a renamed key format is caught in words.
        for (String known : new String[] {
                "io.github.michelbr84.flapforge.input.RawInput$KeyDown",
                "io.github.michelbr84.flapforge.input.RawInput$KeyUp",
                "io.github.michelbr84.flapforge.input.RawInput$MouseDown",
                "io.github.michelbr84.flapforge.input.RawInput$Wheel",
                "io.github.michelbr84.flapforge.input.RawInput$FocusLost",
                "io.github.michelbr84.flapforge.input.RawInput$CloseRequested",
                "io.github.michelbr84.flapforge.input.RawInput$FullscreenToggled",
                "io.github.michelbr84.flapforge.gameplay.TickFact$BossWarning",
                "io.github.michelbr84.flapforge.gameplay.TickFact$BossCleared",
                "io.github.michelbr84.flapforge.progression.DailyChallenge$Pick",
                "io.github.michelbr84.flapforge.ui.screens.ShopScreen$Offer",
                "io.github.michelbr84.flapforge.content.defs.BirdDef",
                "io.github.michelbr84.flapforge.content.defs.PaletteDef",
                "io.github.michelbr84.flapforge.content.defs.TierDef",
                "io.github.michelbr84.flapforge.content.defs.HitboxDef",
                "io.github.michelbr84.flapforge.content.defs.StatModifierDef",
                "io.github.michelbr84.flapforge.content.defs.UnlockConditionDef",
                "io.github.michelbr84.flapforge.content.defs.SpriteDef",
                "io.github.michelbr84.flapforge.gameplay.spec.BossSpec",
                "io.github.michelbr84.flapforge.gameplay.run.RunConfig",
                "io.github.michelbr84.flapforge.app.LaunchOptions",
        }) {
            assertTrue("table lists " + known, table.containsKey(known));
        }
        assertEquals("a zero-component record has an empty value", List.of(),
                table.get("io.github.michelbr84.flapforge.input.RawInput$CloseRequested"));
    }

    @Test
    public void everyEntryNamesRealFieldsInCanonicalConstructorOrder() throws Exception {
        Map<String, List<String>> table = table();
        int checked = 0;
        for (Map.Entry<String, List<String>> entry : table.entrySet()) {
            Class<?> type = Class.forName(entry.getKey());
            List<String> names = entry.getValue();
            assertTrue(entry.getKey() + " is a record on the JVM", type.isRecord());

            // The platform's own order — what the JVM's canonical constructor takes and what
            // the desugared class on ART still takes.
            List<String> platform = new ArrayList<>();
            for (java.lang.reflect.RecordComponent rc : type.getRecordComponents()) {
                platform.add(rc.getName());
            }
            assertEquals(entry.getKey() + ": the table's order is the declaration order",
                    platform, names);

            Class<?>[] paramTypes = new Class<?>[names.size()];
            for (int i = 0; i < paramTypes.length; i++) {
                paramTypes[i] = type.getDeclaredField(names.get(i)).getType();
            }
            assertNotNull(entry.getKey() + ": canonical constructor in table order",
                    type.getDeclaredConstructor(paramTypes));

            // What the shim hands the binder for this class.
            RecordComponent[] shim = Records.components(type);
            assertEquals(names.size(), shim.length);
            for (int i = 0; i < shim.length; i++) {
                assertEquals(names.get(i), shim[i].getName());
                assertSame(paramTypes[i], shim[i].getType());
            }
            assertTrue(Records.isRecord(type));
            checked++;
        }
        assertEquals(table.size(), checked);
    }

    // ---------------------------------------------------------------- the shim

    @Test
    public void componentsFollowTheDeclarationOrderNotTheAlphabeticalOne() {
        // Records with repeated component types: an alphabetical (dex) order would put two
        // same-typed components in the wrong constructor slots without any type error.
        assertNames(BirdDef.class, "id", "archetype", "passiveSlots", "baseStats", "hitbox",
                "effects", "rampEffects", "synergyEffects", "passiveAbilities", "palettes",
                "shape", "sprite", "unlock");
        assertNames(PaletteDef.class, "id", "body", "wing", "eye", "accent", "unlock");
        assertNames(BossSpec.class, "id", "worldId", "atGate", "warningTicks", "patterns",
                "surviveTicks");
        assertNames(TickFact.BossWarning.class, "bossId", "worldId", "warningTicks");
        assertNames(RawInput.KeyDown.class, "code", "whenMs");
        assertNames(RawInput.CloseRequested.class);
        RecordComponent[] run = Records.components(RunConfig.class);
        assertEquals("seed", run[0].getName());
        assertEquals("birdId", run[1].getName());
        assertEquals("paletteId", run[2].getName());
        assertEquals("worldId", run[3].getName());
        assertEquals("tierId", run[4].getName());
    }

    @Test
    public void componentsMatchThePlatformInTypeGenericTypeAndAnnotation() {
        for (Class<?> type : new Class<?>[] {BirdDef.class, TierDef.class, RunConfig.class}) {
            java.lang.reflect.RecordComponent[] platform = type.getRecordComponents();
            RecordComponent[] shim = Records.components(type);
            assertEquals(platform.length, shim.length);
            for (int i = 0; i < shim.length; i++) {
                assertEquals(platform[i].getName(), shim[i].getName());
                assertSame(platform[i].getType(), shim[i].getType());
                assertEquals(platform[i].getGenericType(), shim[i].getGenericType());
                JsonName platformName = platform[i].getAnnotation(JsonName.class);
                JsonName shimName = shim[i].getAnnotation(JsonName.class);
                assertEquals(platformName == null ? null : platformName.value(),
                        shimName == null ? null : shimName.value());
            }
        }
        // TierDef's "default" key is the reason JsonName exists (and targets FIELD too).
        RecordComponent[] tier = Records.components(TierDef.class);
        assertEquals("defaultTier", tier[1].getName());
        assertEquals("default", tier[1].getAnnotation(JsonName.class).value());
        assertSame(boolean.class, tier[1].getType());
        assertEquals("java.util.List<io.github.michelbr84.flapforge.content.defs.StatModifierDef>",
                tier[2].getGenericType().getTypeName());
    }

    @Test
    public void componentsReturnsAFreshArrayEveryCall() {
        RecordComponent[] first = Records.components(BirdDef.class);
        RecordComponent[] second = Records.components(BirdDef.class);
        assertNotSame(first, second);
        assertArrayEquals(names(first), names(second));
        first[0] = null;
        assertEquals("id", Records.components(BirdDef.class)[0].getName());
    }

    @Test
    public void nonRecordsAreNotRecords() {
        for (Class<?> type : new Class<?>[] {String.class, int.class, boolean.class,
                Integer.class, List.class, Map.class, Object.class, RawInput.class,
                io.github.michelbr84.flapforge.content.defs.BirdArchetype.class}) {
            assertFalse(type.getName(), Records.isRecord(type));
        }
        try {
            Records.components(String.class);
            fail("components of a non-record");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("java.lang.String"));
        }
    }

    @Test
    public void aRecordAbsentFromTheTableBindsThroughThePlatformFallback() throws Exception {
        assertFalse("the build-time table cannot know a test-only record",
                table().containsKey(Probe.class.getName()));
        assertTrue(Records.isRecord(Probe.class));
        assertNames(Probe.class, "first", "second", "numbers");

        // End to end through the transformed binder, which calls the shim (rule T4).
        Probe bound = StrictBinder.bindStrict(Probe.class, JsonParser.parseString(
                "{\"first\": \"a\", \"second\": \"b\", \"numbers\": [1, 2, 3]}"), "probe.json");
        assertEquals(new Probe("a", "b", List.of(1, 2, 3)), bound);
    }

    @Test
    public void aTableEntryWhoseFieldIsMissingIsALoudError() {
        try {
            Records.fromNames(Probe.class, List.of("first", "nope"));
            fail("a missing field must not be a silent null");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("'nope'"));
            assertTrue(expected.getMessage(),
                    expected.getMessage().contains(Probe.class.getName()));
        }
    }

    // ---------------------------------------------------------------- the transform

    @Test
    public void transformedBinderCallsTheShimAndNotThePlatform() throws IOException {
        String binder = read(repoRoot().resolve(TRANSFORMED_BINDER));
        assertTrue("T4a", binder.contains("import jrecord.RecordComponent;"));
        assertTrue("T4b", binder.contains("jrecord.Records.isRecord(raw)"));
        assertTrue("T4c", binder.contains("jrecord.Records.components(raw)"));
        assertFalse("no platform RecordComponent",
                binder.contains("java.lang.reflect.RecordComponent"));
        assertFalse("no platform isRecord", binder.contains("raw.isRecord()"));
        assertFalse("no platform getRecordComponents",
                binder.contains("raw.getRecordComponents()"));
        assertEquals("T4 touches StrictBinder only: three call sites", 3,
                binder.split("jrecord\\.", -1).length - 1);
    }

    // ---------------------------------------------------------------- helpers

    private static void assertNames(Class<?> type, String... expected) {
        assertArrayEquals(type.getName(), expected, names(Records.components(type)));
    }

    private static String[] names(RecordComponent[] components) {
        return Arrays.stream(components).map(RecordComponent::getName).toArray(String[]::new);
    }

    /** The table as the shim reads it: sorted, {@code name -> component names}. */
    static Map<String, List<String>> table() throws IOException {
        Properties properties = new Properties();
        try (InputStream in = RecordsTest.class.getResourceAsStream(Records.TABLE_RESOURCE)) {
            assertNotNull(Records.TABLE_RESOURCE + " is on the test classpath", in);
            try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                properties.load(reader);
            }
        }
        Map<String, List<String>> table = new TreeMap<>();
        for (String name : properties.stringPropertyNames()) {
            String value = properties.getProperty(name).trim();
            table.put(name, value.isEmpty() ? List.of() : List.of(value.split(",")));
        }
        return table;
    }

    private static Path repoRoot() {
        Path dir = Path.of(System.getProperty("user.dir", ".")).toAbsolutePath().normalize();
        for (Path p = dir; p != null; p = p.getParent()) {
            if (Files.isRegularFile(p.resolve(ANDROID_BUILD_FILE))) {
                return p;
            }
        }
        throw new IllegalStateException(ANDROID_BUILD_FILE + " not found at or above " + dir);
    }

    private static String read(Path file) throws IOException {
        return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
    }
}
