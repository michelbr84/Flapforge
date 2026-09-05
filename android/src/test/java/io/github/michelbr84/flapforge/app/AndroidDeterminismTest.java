package io.github.michelbr84.flapforge.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import awt.Shims;
import io.github.michelbr84.flapforge.input.InputQueue;
import io.github.michelbr84.flapforge.persistence.SavePaths;
import io.github.michelbr84.flapforge.render.FrameRenderer;
import io.github.michelbr84.flapforge.render.Viewport;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/**
 * Determinism proof of the Android port (M10, D12): the transformed game, compiled against the
 * {@code awt}/{@code jssound}/{@code jimageio} shims and run under Robolectric, plays the same
 * headless run as the desktop and prints the same line.
 *
 * <p>The launch is the real one — {@code GameApplication.start} with
 * {@code --headless-run 3000 --seed 42 --home <scratch>} — against a {@link GameHost} whose every
 * method fails the test: the headless branch must never ask for a window, a presenter, a bridge
 * or a refresh rate. The published line
 * {@code hash=eaaa01685261a433 ticks=3000 gates=36 points=36} is the artefact the desktop CI
 * compares across operating systems and JDKs; it moves with the shipped balance on purpose, and
 * when it does the constant here moves with it. A second seed is compared against the desktop
 * fat jar itself ({@code build/libs/flapforge-<version>-all.jar}, run as a subprocess) whenever
 * the root build has produced one; the Android CI job has none, so that case is skipped there.
 *
 * <p>The headless launch reads no profile and writes none (it must depend on the seed and the
 * shipped data alone): the desktop run leaves {@code --home} untouched, and so must this one.
 * The scratch home is also installed as the {@link SavePaths} override before the launch, so a
 * regression that made the headless path write anything lands in the scratch directory rather
 * than in the developer's {@code ~/.flapforge}, which is fingerprinted around each run through
 * the activity tests' {@code DesktopProfileGuard}. That guard is package-private in the
 * {@code android} test package while this test has to sit in {@code app} (the package of the
 * launch it drives), hence the reflective call.
 *
 * <p>No {@code GraphicsMode}: a headless run renders nothing, so nothing here needs a real
 * canvas.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class AndroidDeterminismTest {

    /** The line the desktop CI publishes for {@code --headless-run 3000 --seed 42} (D12). */
    static final String PUBLISHED_LINE = "hash=eaaa01685261a433 ticks=3000 gates=36 points=36";
    /** The summary line the headless branch prints before the determinism line. */
    static final String PUBLISHED_SUMMARY =
            "headless-run frames=3000 ticks=3000 presents=3000 seed=42";
    static final int FRAMES = 3000;
    static final long PUBLISHED_SEED = 42L;
    /** A seed the CI does not publish: its line is read off the desktop fat jar instead. */
    static final long OTHER_SEED = 7L;
    static final String HASH_PREFIX = "hash=";
    static final String SUMMARY_PREFIX = "headless-run ";
    /** The root build's fat jar, relative to the repository root (the android module's parent). */
    static final String FAT_JAR = "build/libs/flapforge-" + AppVersion.version() + "-all.jar";
    static final long FAT_JAR_TIMEOUT_S = 60L;
    static final String GUARD_CLASS = "io.github.michelbr84.flapforge.android.DesktopProfileGuard";

    /**
     * The host a headless launch is started with: every method is a test failure, because the
     * headless branch has no window to build and nothing to present. It stays installed as the
     * application's static host for the rest of the sandbox; the next windowed launch replaces
     * it, as {@code GameApplication.start} always does.
     */
    private static final GameHost UNTOUCHABLE_HOST = new GameHost() {
        @Override
        public AppWindow createWindow(String title, Integer requestedScale, boolean fullscreen) {
            throw untouchable();
        }

        @Override
        public FramePresenter createPresenter(AppWindow window, Viewport viewport,
                FrameRenderer renderer) {
            throw untouchable();
        }

        @Override
        public InputBridge createInputBridge(InputQueue queue) {
            throw untouchable();
        }

        @Override
        public int displayRefreshRateHz() {
            throw untouchable();
        }

        private AssertionError untouchable() {
            return new AssertionError("headless run must not touch the host");
        }
    };

    @Rule
    public final TemporaryFolder temp = new TemporaryFolder();

    private Path previousOverride;

    /** Same order as {@code MainActivity.onCreate}: shims first, then the save route. */
    @Before
    public void bootstrapLikeTheActivity() {
        Shims.init(RuntimeEnvironment.getApplication());
        previousOverride = SavePaths.overrideDir();
    }

    /** The override is sandbox-wide static state; the next test class gets it back as it was. */
    @After
    public void restoreSaveRoute() {
        SavePaths.override(previousOverride);
    }

    @Test
    public void headlessRunPrintsThePublishedDeterminismLine() throws Exception {
        Map<String, String> desktopBefore = desktopProfileFingerprint();
        Path home = scratchHome("home-" + PUBLISHED_SEED);

        List<String> lines = headlessRun(PUBLISHED_SEED, home);

        assertTrue("the published line, whole, in " + lines, lines.contains(PUBLISHED_LINE));
        assertEquals("exactly one determinism line in " + lines, 1,
                lines.stream().filter(line -> line.startsWith(HASH_PREFIX)).count());
        assertTrue("the summary line, whole, in " + lines, lines.contains(PUBLISHED_SUMMARY));
        assertNothingWritten(home);
        assertEquals("the desktop profile is untouched", desktopBefore,
                desktopProfileFingerprint());
    }

    @Test
    public void headlessRunMatchesTheDesktopFatJarForAnotherSeed() throws Exception {
        Path jar = locateFatJar();
        assumeTrue("desktop fat jar not built (" + FAT_JAR + " under the repository root); "
                + "the root build produces it", jar != null);
        Map<String, String> desktopBefore = desktopProfileFingerprint();
        Path desktopHome = scratchHome("desktop-home-" + OTHER_SEED);
        Path home = scratchHome("home-" + OTHER_SEED);

        List<String> desktopLines = desktopHeadlessRun(jar, OTHER_SEED, desktopHome);
        List<String> lines = headlessRun(OTHER_SEED, home);

        String desktopLine = hashLine(desktopLines);
        String androidLine = hashLine(lines);
        assertEquals("the Android port plays seed " + OTHER_SEED + " as the desktop does",
                desktopLine, androidLine);
        assertEquals("both summaries agree", summaryLine(desktopLines), summaryLine(lines));
        assertNotEquals("seed " + OTHER_SEED + " is not the published run", PUBLISHED_LINE,
                androidLine);
        assertNothingWritten(home);
        assertNothingWritten(desktopHome);
        assertEquals("the desktop profile is untouched", desktopBefore,
                desktopProfileFingerprint());
    }

    /**
     * Starts the headless launch on the untouchable host with {@code System.out} captured, and
     * returns what it printed, line by line.
     */
    private static List<String> headlessRun(long seed, Path home) throws Exception {
        LaunchOptions options = LaunchOptions.parse(new String[] {
                "--headless-run", Integer.toString(FRAMES), "--seed", Long.toString(seed),
                "--home", home.toString()});
        assertTrue(options.headless());
        assertEquals(home, options.home().toAbsolutePath().normalize());
        SavePaths.override(home);
        assertEquals(home, SavePaths.profileDir());

        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        PrintStream original = System.out;
        GameApplication app;
        try (PrintStream sink = new PrintStream(captured, true, StandardCharsets.UTF_8)) {
            System.setOut(sink);
            try {
                app = GameApplication.start(options, UNTOUCHABLE_HOST);
            } finally {
                System.setOut(original);
            }
        }
        assertNotNull("the headless launch wired a context", app.context());
        assertNull("a headless launch has no loop thread", app.loopThread());
        return captured.toString(StandardCharsets.UTF_8).lines().collect(Collectors.toList());
    }

    /** Runs the desktop fat jar for the same frames and seed, returning its output lines. */
    private static List<String> desktopHeadlessRun(Path jar, long seed, Path home)
            throws Exception {
        boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT)
                .contains("win");
        Path java = Path.of(System.getProperty("java.home"), "bin", windows ? "java.exe" : "java");
        Process process = new ProcessBuilder(java.toString(), "-jar", jar.toString(),
                "--headless-run", Integer.toString(FRAMES), "--seed", Long.toString(seed),
                "--home", home.toString())
                .redirectErrorStream(true)
                .start();
        String output;
        try {
            output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            assertTrue("the fat jar finished within " + FAT_JAR_TIMEOUT_S + " s",
                    process.waitFor(FAT_JAR_TIMEOUT_S, TimeUnit.SECONDS));
        } finally {
            process.destroyForcibly();
        }
        assertEquals("fat jar exit code, output:\n" + output, 0, process.exitValue());
        return output.lines().collect(Collectors.toList());
    }

    private static String hashLine(List<String> lines) {
        return onlyLine(lines, HASH_PREFIX);
    }

    private static String summaryLine(List<String> lines) {
        return onlyLine(lines, SUMMARY_PREFIX);
    }

    private static String onlyLine(List<String> lines, String prefix) {
        List<String> matching = lines.stream().filter(line -> line.startsWith(prefix))
                .collect(Collectors.toList());
        assertEquals("exactly one '" + prefix + "' line in " + lines, 1, matching.size());
        return matching.get(0);
    }

    /** A fresh, empty directory that is never inside the desktop profile directory. */
    private Path scratchHome(String name) throws IOException {
        Path home = temp.newFolder(name).toPath().toAbsolutePath().normalize();
        Path desktop = Path.of(System.getProperty("user.home", "."))
                .resolve(SavePaths.DOT_DIR_NAME).toAbsolutePath().normalize();
        assertFalse("scratch home outside the desktop profile", home.startsWith(desktop));
        return home;
    }

    /** The desktop run leaves {@code --home} empty; so must the Android one. */
    private static void assertNothingWritten(Path home) throws IOException {
        try (Stream<Path> walk = Files.walk(home)) {
            List<String> written = walk.filter(path -> !path.equals(home))
                    .map(path -> home.relativize(path).toString())
                    .sorted()
                    .collect(Collectors.toList());
            assertEquals("a headless run writes nothing under " + home, List.of(), written);
        }
    }

    /**
     * The root build's fat jar, looked up from the test's working directory upwards (Gradle
     * runs unit tests in the android module, whose parent is the repository root).
     *
     * @return the jar, or {@code null} when the root build has not produced one
     */
    private static Path locateFatJar() {
        Path dir = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        for (int up = 0; up < 3 && dir != null; up++, dir = dir.getParent()) {
            Path jar = dir.resolve(FAT_JAR);
            if (Files.isRegularFile(jar)) {
                return jar;
            }
        }
        return null;
    }

    /** {@code DesktopProfileGuard.fingerprint()} from the android test package. */
    private static Map<String, String> desktopProfileFingerprint() throws Exception {
        Class<?> guard = Class.forName(GUARD_CLASS, true,
                AndroidDeterminismTest.class.getClassLoader());
        Method fingerprint = guard.getDeclaredMethod("fingerprint");
        fingerprint.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, String> result = (Map<String, String>) fingerprint.invoke(null);
        return result;
    }
}
