package awt;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.graphics.Bitmap;

import awt.geom.Ellipse2D;
import awt.geom.Rectangle2D;
import awt.image.BufferedImage;

import io.github.michelbr84.flapforge.persistence.SavePaths;
import io.github.michelbr84.flapforge.render.Accessibility;
import io.github.michelbr84.flapforge.render.BackgroundRenderer;
import io.github.michelbr84.flapforge.render.Fonts;
import io.github.michelbr84.flapforge.render.ProceduralArt;
import io.github.michelbr84.flapforge.render.TextPainter;
import io.github.michelbr84.flapforge.render.WorldPalette;
import io.github.michelbr84.flapforge.render.WorldStyle;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import jimageio.ImageIO;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

/**
 * Golden render fidelity of the whole shim stack (M10): the game's own drawing code —
 * {@code ProceduralArt}, {@code TextPainter}, {@code Fonts}, transformed onto the {@code awt.*}
 * shims — draws seven scenes into shim {@link BufferedImage}s under Robolectric's native
 * graphics, and each is compared against the PNG the desktop Java2D pipeline rendered for the
 * identical scene code ({@code android/tools/GoldenRender.java}; references under
 * {@code src/test/resources/golden/}, see its README). Transforms, paths (single and
 * multi-subpath, where the fill rule shows), arcs, round rectangles, gradients, strokes, clips,
 * text and every {@code drawImage} form are exercised through the calls the game actually
 * makes, not through synthetic shapes.
 *
 * <p>Metrics. Shape scenes: the fraction of pixels whose largest premultiplied channel
 * difference exceeds {@value #CHANNEL_TOLERANCE}/255 (anti-aliasing and rasteriser differences
 * stay far below that) and the mean absolute channel difference, against per-scene limits of
 * {@link #SHAPE_LIMITS}. Text: Java2D and Skia rasterise glyphs differently — Skia's grey
 * antialiasing is lighter — so each line box of {@link #TEXT_LINE_BOXES} is compared by glyph
 * footprint (pixels differing from the background by more than {@value #TEXT_INK_THRESHOLD}/255,
 * which both rasterisers agree on) and by the footprint's bounding box, against
 * {@link #TEXT_LINE_LIMITS}. The thresholds are at least twice the values measured on a green
 * run and were checked to fail under deliberate shim mutations (arc sweep sign, fill rule,
 * gradient end points, stroke width, text baseline, antialiasing, subimage offset, the ellipse
 * outline wound against the rectangle, a gradient inheriting the previous fill's alpha).
 *
 * <p>The scene code between the {@code GOLDEN SCENES} markers is a byte-for-byte copy of the
 * generator's, with {@code java.awt.} rewritten to {@code awt.} (rule T3 of the source
 * transform); {@link #sceneSourceIsSharedWithTheDesktopGenerator} reads both files and fails
 * when the copies drift. What the shims drew is written to {@code android/build/golden-actual/}
 * (or {@code $FLAPFORGE_GOLDEN_OUT}) next to a {@code <scene>.diff.png} heat map — grey for
 * small differences, red over the tolerance — and the measured metrics are printed, so a
 * failure can be looked at.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class GoldenRenderTest {

    /** A shape-scene pixel differs when a premultiplied channel is off by more than this. */
    static final int CHANNEL_TOLERANCE = 40;
    /**
     * A text pixel belongs to the glyph footprint when a channel differs from the background by
     * more than this. Kept low on purpose: at 40/255 the count measures the antialiasing gamma
     * (Skia draws the same outlines about 15% lighter than Java2D), not the glyph shapes.
     */
    static final int TEXT_INK_THRESHOLD = 10;

    /**
     * Per-scene limits: {@code {max fraction of pixels over CHANNEL_TOLERANCE, max mean absolute
     * channel difference}}. Measured on a green run (Robolectric 4.16): icon128 0.0010 / 0.53,
     * icon64 0.0032 / 0.94, composition 0.0001 / 0.33, background 0.0000 / 0.38, images 0.0065 /
     * 0.93, worlds 0.0001 / 0.29 — the images scene's share is the nearest-neighbour half-size
     * blit, where Skia and Java2D pick the other source pixel of each pair, and the bilinear
     * edges; the worlds scene's few pixels are the antialiased bottom edge of the black cloud
     * bank against the white sky, against which the storm holes of an ellipse wound the wrong
     * way measured 1603 pixels and a hills sky at the previous fill's alpha 9085.
     */
    private static final Map<String, double[]> SHAPE_LIMITS = new LinkedHashMap<>();

    static {
        SHAPE_LIMITS.put("icon128", new double[] {0.0025, 1.1});
        SHAPE_LIMITS.put("icon64", new double[] {0.007, 1.9});
        SHAPE_LIMITS.put("composition", new double[] {0.0006, 0.7});
        SHAPE_LIMITS.put("background", new double[] {0.0005, 0.8});
        SHAPE_LIMITS.put("images", new double[] {0.013, 1.9});
        SHAPE_LIMITS.put("worlds", new double[] {0.0003, 0.8});
    }

    /**
     * Per line of {@code TEXT_LINE_BOXES}: {@code {minimum footprint ratio shim/desktop, maximum
     * ratio, bounding-box tolerance in px}}. The regular-weight lines measured 0.978, 0.995 and
     * 0.981 with every box edge within 2 px. Line 0 is the derived {@code BOLD} face: Java2D
     * emboldens it algorithmically (the bundled variable font ships one Regular instance), and
     * what the shim side draws depends on the runtime — {@code Typeface.create(BOLD)} under
     * Robolectric's native runtime draws the regular weight (measured ratio 0.68, the run 10 px
     * narrower), while a device applies synthetic bold to a file-loaded Regular face, which is
     * the desktop-parity outcome (ratio near 1). The line therefore pins a floor under the
     * regular weight and a ceiling just over the emboldened one: a thinner or missing title fails
     * on every runtime, and a runtime that emboldens does not turn the fidelity test red. The
     * measured ratio is printed either way, so the Robolectric gap stays visible in the log.
     */
    private static final double[][] TEXT_LINE_LIMITS = {
        {0.56, 1.10, 12},
        {0.90, 1.10, 3},
        {0.90, 1.10, 3},
        {0.90, 1.10, 3},
    };

    /** The generator source, relative to the repository root. */
    private static final String GENERATOR_SOURCE = "android/tools/GoldenRender.java";
    /** This file, relative to the repository root. */
    private static final String TEST_SOURCE = "android/src/test/java/awt/GoldenRenderTest.java";
    /** Where the shim renderings go when {@code FLAPFORGE_GOLDEN_OUT} is not set. */
    private static final String DEFAULT_OUT = "android/build/golden-actual";

    /** The three profile files no test may touch (the DesktopProfileGuard contract). */
    private static final List<String> GUARDED_FILES = List.of(SavePaths.SAVE_FILE,
            SavePaths.SAVE_BACKUP_FILE, SavePaths.SETTINGS_FILE);

    private Map<String, String> profileBefore;

    @Before
    public void initShimsAndGuardProfile() throws IOException {
        Shims.init(RuntimeEnvironment.getApplication());
        profileBefore = profileFingerprint();
    }

    @After
    public void restoreStaticStateAndCheckProfile() throws IOException {
        // Other tests in this sandbox expect the logical family and smoothing on.
        Fonts.install(null);
        ProceduralArt.setSmoothing(true);
        assertEquals("the desktop profile under ~/.flapforge must not change", profileBefore,
                profileFingerprint());
    }

    // ---------------------------------------------------------------- (a) icons

    @Test
    public void icon128MatchesTheDesktopRendering() throws Exception {
        assertShapeScene("icon128");
    }

    @Test
    public void icon64MatchesTheDesktopRendering() throws Exception {
        assertShapeScene("icon64");
    }

    // ---------------------------------------------------------------- (b) composition

    @Test
    public void compositionMatchesTheDesktopRendering() throws Exception {
        assertShapeScene("composition");
    }

    // ---------------------------------------------------------------- (d) background

    @Test
    public void backgroundMatchesTheDesktopRendering() throws Exception {
        assertShapeScene("background");
    }

    // ---------------------------------------------------------------- (e) images

    @Test
    public void imagesMatchTheDesktopRendering() throws Exception {
        assertShapeScene("images");
    }

    // ---------------------------------------------------------------- (f) styled worlds

    @Test
    public void worldsMatchTheDesktopRendering() throws Exception {
        assertShapeScene("worlds");
    }

    // ---------------------------------------------------------------- (c) text

    @Test
    public void textLinesMatchTheDesktopFootprintAndBounds() throws Exception {
        BufferedImage expected = reference("text");
        BufferedImage actual = renderScene("text", loadUiFont());
        Diff diff = compare(expected, actual);
        save("text", actual, diff);
        System.out.println("golden text: " + diff);
        int background = 0xFF000000 | TEXT_BACKGROUND_RGB;
        List<String> failures = new ArrayList<>();
        for (int i = 0; i < TEXT_LINE_BOXES.length; i++) {
            int[] box = TEXT_LINE_BOXES[i];
            double[] limits = TEXT_LINE_LIMITS[i];
            Ink exp = ink(expected, box, background);
            Ink act = ink(actual, box, background);
            double ratio = exp.count == 0 ? Double.NaN : act.count / (double) exp.count;
            System.out.println(String.format(
                    "golden text line %d: desktop %s, shim %s, footprint ratio %.3f "
                            + "(expected %.2f .. %.2f, bounds +/- %d px)",
                    i, exp, act, ratio, limits[0], limits[1], (int) limits[2]));
            if (exp.count == 0) {
                failures.add("line " + i + ": the reference has no ink in " + boxText(box));
                continue;
            }
            if (ratio < limits[0] || ratio > limits[1]) {
                failures.add(String.format("line %d: footprint %d vs desktop %d (ratio %.3f, "
                        + "expected %.2f .. %.2f)", i, act.count, exp.count, ratio, limits[0],
                        limits[1]));
            }
            if (act.count == 0) {
                failures.add("line " + i + ": no ink at all in " + boxText(box));
                continue;
            }
            int[] e = exp.bounds();
            int[] a = act.bounds();
            for (int k = 0; k < 4; k++) {
                if (Math.abs(e[k] - a[k]) > limits[2]) {
                    failures.add("line " + i + ": footprint bounds " + boxText(a)
                            + " vs desktop " + boxText(e) + " (tolerance " + (int) limits[2]
                            + " px)");
                    break;
                }
            }
        }
        if (!failures.isEmpty()) {
            fail("text scene differs from the desktop rendering (" + diff + "; see "
                    + outputDir() + "): " + String.join("; ", failures));
        }
    }

    // ---------------------------------------------------------------- shared scene source

    @Test
    public void sceneSourceIsSharedWithTheDesktopGenerator() throws IOException {
        Path root = repoRoot();
        String generator = sceneRegion(readSource(root.resolve(GENERATOR_SOURCE)),
                GENERATOR_SOURCE);
        String here = sceneRegion(readSource(root.resolve(TEST_SOURCE)), TEST_SOURCE);
        assertTrue("the region carries the scene renderer",
                generator.contains("static BufferedImage renderScene("));
        assertTrue("the region carries every scene",
                generator.contains("\"icon128\", \"icon64\", \"composition\", \"text\", "
                        + "\"background\", \"images\", \"worlds\""));
        assertEquals("the scene code of " + GENERATOR_SOURCE + " (after the transform rules) "
                + "and of " + TEST_SOURCE + " must be identical", forward(generator), here);
    }

    // ---------------------------------------------------------------- helpers

    private void assertShapeScene(String scene) throws Exception {
        double[] limits = SHAPE_LIMITS.get(scene);
        assertNotNull("limits for " + scene, limits);
        BufferedImage expected = reference(scene);
        BufferedImage actual = renderScene(scene, loadUiFont());
        Diff diff = compare(expected, actual);
        save(scene, actual, diff);
        System.out.println("golden " + scene + ": " + diff);
        List<String> failures = new ArrayList<>();
        if (diff.fractionOver() > limits[0]) {
            failures.add(String.format("%.5f of the pixels differ by more than %d/255 "
                    + "(limit %.5f)", diff.fractionOver(), CHANNEL_TOLERANCE, limits[0]));
        }
        if (diff.meanAbs() > limits[1]) {
            failures.add(String.format("mean channel difference %.3f (limit %.3f)",
                    diff.meanAbs(), limits[1]));
        }
        if (!failures.isEmpty()) {
            fail(scene + " differs from the desktop rendering (" + diff + "; see "
                    + outputDir() + "): " + String.join("; ", failures));
        }
    }

    /** The desktop reference of a scene, decoded through the game's image path. */
    private static BufferedImage reference(String scene) throws IOException {
        String resource = "/golden/" + scene + ".png";
        try (InputStream in = GoldenRenderTest.class.getResourceAsStream(resource)) {
            assertNotNull("reference on the test classpath: " + resource, in);
            BufferedImage image = ImageIO.read(in);
            assertNotNull("decodable reference: " + resource, image);
            return image;
        }
    }

    /** Pixel comparison of one scene: counts, sums and a heat map. */
    private static final class Diff {
        final int width;
        final int height;
        final int over;
        final long sumAbs;
        final int maxAbs;
        final Bitmap heat;

        Diff(int width, int height, int over, long sumAbs, int maxAbs, Bitmap heat) {
            this.width = width;
            this.height = height;
            this.over = over;
            this.sumAbs = sumAbs;
            this.maxAbs = maxAbs;
            this.heat = heat;
        }

        double fractionOver() {
            return over / (double) (width * height);
        }

        double meanAbs() {
            return sumAbs / (4.0 * width * height);
        }

        @Override
        public String toString() {
            return String.format("%dx%d, %d pixels over %d/255 (fraction %.5f), mean abs channel "
                    + "difference %.4f, max %d", width, height, over, CHANNEL_TOLERANCE,
                    fractionOver(), meanAbs(), maxAbs);
        }
    }

    private static Diff compare(BufferedImage expected, BufferedImage actual) {
        assertEquals("width", expected.getWidth(), actual.getWidth());
        assertEquals("height", expected.getHeight(), actual.getHeight());
        int w = expected.getWidth();
        int h = expected.getHeight();
        Bitmap heat = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        int over = 0;
        long sumAbs = 0;
        int maxAbs = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int e = expected.getRGB(x, y);
                int a = actual.getRGB(x, y);
                int delta = 0;
                for (int c = 0; c < 4; c++) {
                    int d = Math.abs(premultipliedChannel(e, c) - premultipliedChannel(a, c));
                    sumAbs += d;
                    delta = Math.max(delta, d);
                }
                maxAbs = Math.max(maxAbs, delta);
                if (delta > CHANNEL_TOLERANCE) {
                    over++;
                    heat.setPixel(x, y, 0xFFFF0000);
                } else {
                    int grey = Math.min(255, delta * 6);
                    heat.setPixel(x, y, 0xFF000000 | (grey << 16) | (grey << 8) | grey);
                }
            }
        }
        return new Diff(w, h, over, sumAbs, maxAbs, heat);
    }

    /**
     * Channel {@code c} (0 alpha, then red, green, blue) of a non-premultiplied ARGB value,
     * premultiplied by its alpha: what the pixel contributes when composited, so the colour of
     * a nearly transparent edge pixel does not count as a difference.
     */
    private static int premultipliedChannel(int argb, int c) {
        int alpha = argb >>> 24;
        if (c == 0) {
            return alpha;
        }
        int value = (argb >> (24 - 8 * c)) & 0xFF;
        return (value * alpha + 127) / 255;
    }

    /** Glyph footprint (pixels differing from the background) inside one text line box. */
    private static final class Ink {
        int count;
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = -1;
        int maxY = -1;

        int[] bounds() {
            return new int[] {minX, minY, maxX + 1, maxY + 1};
        }

        @Override
        public String toString() {
            return count + " footprint pixels" + (count == 0 ? "" : " in " + boxText(bounds()));
        }
    }

    private static Ink ink(BufferedImage image, int[] box, int background) {
        Ink ink = new Ink();
        for (int y = box[1]; y < box[3]; y++) {
            for (int x = box[0]; x < box[2]; x++) {
                int p = image.getRGB(x, y);
                int delta = 0;
                for (int c = 0; c < 4; c++) {
                    delta = Math.max(delta, Math.abs(premultipliedChannel(p, c)
                            - premultipliedChannel(background, c)));
                }
                if (delta > TEXT_INK_THRESHOLD) {
                    ink.count++;
                    ink.minX = Math.min(ink.minX, x);
                    ink.minY = Math.min(ink.minY, y);
                    ink.maxX = Math.max(ink.maxX, x);
                    ink.maxY = Math.max(ink.maxY, y);
                }
            }
        }
        return ink;
    }

    private static String boxText(int[] box) {
        return "[" + box[0] + "," + box[1] + " - " + box[2] + "," + box[3] + ")";
    }

    /** Writes the shim rendering and the heat map for a reviewer. */
    private static void save(String scene, BufferedImage actual, Diff diff) throws IOException {
        Path dir = outputDir();
        Files.createDirectories(dir);
        writePng(dir.resolve(scene + ".png"), actual.bitmap());
        writePng(dir.resolve(scene + ".diff.png"), diff.heat);
    }

    private static void writePng(Path file, Bitmap bitmap) throws IOException {
        try (OutputStream out = Files.newOutputStream(file)) {
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                throw new IOException("PNG encoding failed for " + file);
            }
        }
    }

    private static Path outputDir() {
        String override = System.getenv("FLAPFORGE_GOLDEN_OUT");
        if (override != null && !override.isBlank()) {
            return Path.of(override);
        }
        return repoRoot().resolve(DEFAULT_OUT);
    }

    /** The repository root: the directory holding the generator, at or above the working dir. */
    private static Path repoRoot() {
        Path dir = Path.of(System.getProperty("user.dir", ".")).toAbsolutePath().normalize();
        for (Path p = dir; p != null; p = p.getParent()) {
            if (Files.isRegularFile(p.resolve(GENERATOR_SOURCE))) {
                return p;
            }
        }
        throw new IllegalStateException(GENERATOR_SOURCE + " not found at or above " + dir);
    }

    private static String readSource(Path file) throws IOException {
        return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
    }

    /** The text between the two scene markers of a source file, which must occur once each. */
    private static String sceneRegion(String source, String name) {
        // Assembled at runtime so the literals here do not count as the markers themselves.
        String begin = "// ==== GOLDEN SCENES " + "BEGIN ====";
        String end = "// ==== GOLDEN SCENES " + "END ====";
        int b = source.indexOf(begin);
        int e = source.indexOf(end);
        assertTrue(name + " carries the BEGIN marker exactly once",
                b >= 0 && source.lastIndexOf(begin) == b);
        assertTrue(name + " carries the END marker exactly once after BEGIN",
                e > b && source.lastIndexOf(end) == e);
        return source.substring(b + begin.length(), e);
    }

    /** Rule T3 of android/build.gradle: the shimmed package prefix, class or subpackage tail. */
    private static final Pattern T3_FORWARD =
            Pattern.compile("java\\.awt\\.(?=[A-Z]|geom\\.|image\\.|event\\.)");

    /** The forward rules T1, T2, T3 of android/build.gradle, in that order. */
    private static String forward(String text) {
        String out = text.replace("javax.sound.sampled.", "jssound.");
        out = out.replace("javax.imageio.", "jimageio.");
        return T3_FORWARD.matcher(out).replaceAll("awt.");
    }

    /** MD5 of each guarded file under {@code ~/.flapforge}, or "absent"; empty when no dir. */
    private static Map<String, String> profileFingerprint() throws IOException {
        Map<String, String> fingerprint = new LinkedHashMap<>();
        Path dir = Path.of(System.getProperty("user.home", ".")).resolve(SavePaths.DOT_DIR_NAME);
        if (!Files.isDirectory(dir)) {
            return fingerprint;
        }
        for (String name : GUARDED_FILES) {
            Path file = dir.resolve(name);
            fingerprint.put(name, Files.isRegularFile(file) ? md5(file) : "absent");
        }
        return fingerprint;
    }

    private static String md5(Path file) throws IOException {
        try {
            byte[] digest = MessageDigest.getInstance("MD5").digest(Files.readAllBytes(file));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    // ==== GOLDEN SCENES BEGIN ====
    // Everything down to the END marker is byte-identical in android/tools/GoldenRender.java
    // (java.awt) and android/src/test/java/awt/GoldenRenderTest.java (awt shims);
    // GoldenRenderTest.sceneSourceIsSharedWithTheDesktopGenerator proves it. Only simple type
    // names are used here, so the same text compiles against either package.

    /** Classpath location of the bundled OFL font ({@code AssetManager.ASSET_ROOT} + path). */
    static final String FONT_RESOURCE = "/assets/fonts/Nunito-VariableFont_wght.ttf";

    /** The scenes, in the order the generator writes them. */
    static final String[] SCENE_NAMES = {
        "icon128", "icon64", "composition", "text", "background", "images", "worlds"
    };

    /** Width of every non-icon scene. */
    static final int SCENE_W = 256;
    /** Height of the composition scene. */
    static final int COMPOSITION_H = 256;
    /** Height of the text, background and images scenes. */
    static final int STRIP_H = 128;
    /** Height of the worlds scene: a row of scaled backdrops over two rows of 1:1 windows. */
    static final int WORLDS_H = 384;

    /** The palettes of the three styled worlds drawn by {@link #worlds()} ({@code worlds.json}). */
    static final WorldPalette STORM_SKY = new WorldPalette(0x1E2633, 0x40566E, 0x2A3340,
            0x59708C, 0xF2E06B, 0x8FA3B7, 0x12171F);
    static final WorldPalette IRON_FORGE = new WorldPalette(0x3A2E33, 0x6E4A3C, 0x4A3B33,
            0x8A6A3A, 0xE2571F, 0x7A6258, 0x241C1A);
    static final WorldPalette THE_VOID = new WorldPalette(0x120E1C, 0x2A1F3D, 0x1A1526,
            0x4B3A70, 0x9B7BE8, 0x3B2E5A, 0x08060D);
    /**
     * High-contrast palettes for the 1:1 windows of {@link #worlds()}: a blueprint (white sky,
     * black letterbox, white pipe, so the girder lattice is mid grey over a white sky and a
     * darker skyline), ink (white sky, black pipe, so the far mesas are mid grey and the near
     * ones near black) and a squall (white sky, black fog and letterbox, so the far cloud bank
     * is mid grey, the near bank black and the rain black). Where a tile overlaps itself, an
     * even-odd fill would punch a hole straight through to the contrasting colour behind; where
     * the cloud strip crosses a single ellipse, an ellipse wound against the rectangle would.
     */
    static final WorldPalette BLUEPRINT = new WorldPalette(0xFFFFFF, 0xFFFFFF, 0x2B2B2B,
            0xFFFFFF, 0xFF3B30, 0xFFFFFF, 0x000000);
    static final WorldPalette INK = new WorldPalette(0xFFFFFF, 0xFFFFFF, 0x2B2B2B, 0x000000,
            0xFF3B30, 0xFFFFFF, 0x000000);
    static final WorldPalette SQUALL = new WorldPalette(0xFFFFFF, 0xFFFFFF, 0x2B2B2B, 0x000000,
            0xFF3B30, 0x000000, 0x000000);

    /**
     * The four text lines of the {@code text} scene as {@code {x0, y0, x1, y1}} boxes (right and
     * bottom exclusive), top to bottom: bold 28 centred, regular 16 left, outlined 20 centred,
     * regular 12 right-aligned. Each box holds one line's ink and nothing of its neighbours.
     */
    static final int[][] TEXT_LINE_BOXES = {
        {0, 4, 256, 50},
        {0, 50, 256, 78},
        {0, 78, 256, 110},
        {0, 110, 256, 128},
    };

    /** Background of the text scene: the Green Fields sky just above the ground. */
    static final int TEXT_BACKGROUND_RGB = 0x8FDDE3;

    /**
     * Loads the bundled UI font the way {@code AssetManager.loadFont} does: resource stream,
     * buffered, one base face at size 1 that {@code Fonts} derives every size from.
     */
    static Font loadUiFont() throws Exception {
        try (InputStream in = Fonts.class.getResourceAsStream(FONT_RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("bundled font not on the classpath: "
                        + FONT_RESOURCE);
            }
            return Font.createFont(Font.TRUETYPE_FONT, new BufferedInputStream(in));
        }
    }

    /**
     * Puts every piece of static render state the scenes read into its shipped default, so the
     * pixels depend on nothing another caller (a test, a settings screen) left behind.
     */
    static void resetRenderState(Font uiFont) {
        Accessibility.setPalette("none");
        Accessibility.setHighContrast(false);
        ProceduralArt.setSmoothing(true);
        ProceduralArt.invalidatePalettes();
        Fonts.setTextScale(1.0);
        Fonts.install(uiFont);
    }

    /**
     * Renders one scene into a fresh {@code TYPE_INT_ARGB} image.
     *
     * @param scene one of {@link #SCENE_NAMES}
     * @param uiFont the base face from {@link #loadUiFont()}
     * @return the rendered image
     */
    static BufferedImage renderScene(String scene, Font uiFont) {
        resetRenderState(uiFont);
        switch (scene) {
            case "icon128":
                return ProceduralArt.icon(128);
            case "icon64":
                return ProceduralArt.icon(64);
            case "composition":
                return composition();
            case "text":
                return text();
            case "background":
                return background();
            case "images":
                return images();
            case "worlds":
                return worlds();
            default:
                throw new IllegalArgumentException("unknown scene " + scene);
        }
    }

    /**
     * UI chrome and sprites on a sky tone: a panel, a button in each {@code ButtonState}, the
     * bird in two wing phases and the two other poses, four shop portraits (their archetype
     * marks are strokes under the scale transform), three coin spins and an anvil — round
     * rectangles, translucent fills, strokes, ellipses, polygons under translate/scale/rotate.
     */
    static BufferedImage composition() {
        WorldPalette palette = WorldPalette.GREEN_FIELDS;
        BufferedImage image = new BufferedImage(SCENE_W, COMPOSITION_H,
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            ProceduralArt.prepare(g);
            g.setColor(ProceduralArt.color(palette, ProceduralArt.Tone.SKY_BOTTOM));
            g.fillRect(0, 0, SCENE_W, COMPOSITION_H);
            ProceduralArt.panel(g, 8, 8, 240, 240);
            ProceduralArt.button(g, 20, 20, 100, 30,
                    ProceduralArt.ButtonState.of(true, false, false));
            ProceduralArt.button(g, 136, 20, 100, 30,
                    ProceduralArt.ButtonState.of(true, false, true));
            ProceduralArt.button(g, 20, 58, 100, 30,
                    ProceduralArt.ButtonState.of(true, true, false));
            ProceduralArt.button(g, 136, 58, 100, 30,
                    ProceduralArt.ButtonState.of(false, true, true));
            // Playfield.SPRITE_W wide, like BirdRenderer: wing frames 0 and 3 of 8, then the
            // rising and dead poses (which fix their own phase).
            ProceduralArt.drawBird(g, 40, 116, 39, 0.0, palette, ProceduralArt.BirdPose.NORMAL);
            ProceduralArt.drawBird(g, 98, 116, 39, 3 / 8.0, palette,
                    ProceduralArt.BirdPose.NORMAL);
            ProceduralArt.drawBird(g, 156, 116, 39, 0.0, palette, ProceduralArt.BirdPose.UP);
            ProceduralArt.drawBird(g, 214, 116, 39, 0.0, palette, ProceduralArt.BirdPose.DEAD);
            // Shop portraits: the four archetypes whose mark is stroked (plate, halo, sparks,
            // speed lines), each in its own silhouette stretch.
            ProceduralArt.drawBirdPortrait(g, 40, 168, 36, 0.0, 0xF5C542, 0xC0501A, 0x1C2A2C,
                    0xE8562A, "guardian");
            ProceduralArt.drawBirdPortrait(g, 98, 168, 36, 0.25, 0x8FDDE3, 0x2E6B72, 0x1C2A2C,
                    0xF5C542, "mystic");
            ProceduralArt.drawBirdPortrait(g, 156, 168, 36, 0.5, 0xDED895, 0x6E4A2A, 0x1C2A2C,
                    0xE8562A, "forge");
            ProceduralArt.drawBirdPortrait(g, 214, 168, 36, 0.75, 0xF4F8F8, 0x5FBF3A, 0x1C2A2C,
                    0xF5C542, "swift");
            // Face on, a third of a turn (highlight on the other face) and nearly edge on.
            Ellipse2D.Double scratch = new Ellipse2D.Double();
            ProceduralArt.drawCoin(g, scratch, 36, 210, 14, ProceduralArt.coinSpin(0));
            ProceduralArt.drawCoin(g, scratch, 72, 210, 14, ProceduralArt.coinSpin(16));
            ProceduralArt.drawCoin(g, scratch, 108, 210, 14, ProceduralArt.coinSpin(11));
            ProceduralArt.drawAnvil(g, 190, 196, 90, ProceduralArt.letterboxColor(palette));
        } finally {
            g.dispose();
        }
        return image;
    }

    /**
     * Four lines through {@code TextPainter} in the bundled font at four sizes and every
     * alignment, one per box of {@link #TEXT_LINE_BOXES}: the title (derived bold), a
     * Portuguese score line (accents), an outlined prompt and a right-aligned version tag.
     */
    static BufferedImage text() {
        BufferedImage image = new BufferedImage(SCENE_W, STRIP_H, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            TextPainter.prepare(g);
            g.setColor(new Color(TEXT_BACKGROUND_RGB));
            g.fillRect(0, 0, SCENE_W, STRIP_H);
            g.setFont(Fonts.bold(28));
            g.setColor(ProceduralArt.TEXT_DARK);
            TextPainter.drawCentered(g, "Flapforge", 128, 38);
            g.setFont(Fonts.regular(16));
            TextPainter.draw(g, "Pontuação 1234", 12, 70);
            g.setFont(Fonts.regular(20));
            TextPainter.drawOutlined(g, "Toque para voar", 128, 100, TextPainter.Align.CENTER,
                    ProceduralArt.TEXT_LIGHT, ProceduralArt.TEXT_DARK, 1);
            g.setFont(Fonts.regular(12));
            g.setColor(ProceduralArt.TEXT_DARK);
            TextPainter.drawRight(g, "v0.1.0", 244, 122);
        } finally {
            g.dispose();
        }
        return image;
    }

    /**
     * The whole 420x640 world backdrop at one fifth scale on the left (the sky gradient's end
     * points go through the transform, then clouds, hill ovals and the ground strip), the
     * cached sky paint at 1:1 on the right with both cloud silhouettes at two sizes, the last
     * one running off the edge.
     */
    static BufferedImage background() {
        WorldPalette palette = WorldPalette.GREEN_FIELDS;
        BufferedImage image = new BufferedImage(SCENE_W, STRIP_H, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            ProceduralArt.prepare(g);
            g.scale(0.2, 0.2);
            ProceduralArt.fillBackground(g, palette);
            g.scale(5, 5);
            Paint oldPaint = g.getPaint();
            g.setPaint(ProceduralArt.skyPaint(palette));
            g.fillRect(84, 0, SCENE_W - 84, STRIP_H);
            g.setPaint(oldPaint);
            Ellipse2D.Double scratch = new Ellipse2D.Double();
            g.setColor(ProceduralArt.color(palette, ProceduralArt.Tone.CLOUD));
            ProceduralArt.drawCloud(g, scratch, 96, 8, 48, 33, 0);
            ProceduralArt.drawCloud(g, scratch, 156, 12, 40, 32, 1);
            ProceduralArt.drawCloud(g, scratch, 100, 52, 96, 66, 0);
            ProceduralArt.drawCloud(g, scratch, 200, 60, 80, 64, 1);
        } finally {
            g.dispose();
        }
        return image;
    }

    /**
     * Every {@code drawImage} form the game uses, with the 48 px icon as the sprite: natural
     * size, scaled up (bilinear), a source region, a subimage view at 1:1, a subimage under a
     * doubled context transform, a scaled subimage, then a half-size copy with nearest-neighbour
     * interpolation (smoothing off) and one with bilinear (smoothing back on).
     */
    static BufferedImage images() {
        BufferedImage icon = ProceduralArt.icon(48);
        BufferedImage image = new BufferedImage(SCENE_W, STRIP_H, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            ProceduralArt.prepare(g);
            g.setColor(ProceduralArt.TEXT_DARK);
            g.fillRect(0, 0, SCENE_W, STRIP_H);
            g.drawImage(icon, 8, 8, null);
            g.drawImage(icon, 64, 8, 96, 96, null);
            g.drawImage(icon, 8, 64, 32, 88, 12, 12, 36, 36, null);
            g.drawImage(icon.getSubimage(24, 0, 24, 24), 36, 64, null);
            g.scale(2, 2);
            g.drawImage(icon.getSubimage(0, 0, 24, 24), 84, 4, null);
            g.scale(0.5, 0.5);
            g.drawImage(icon.getSubimage(24, 24, 24, 24), 168, 64, 48, 48, null);
            ProceduralArt.setSmoothing(false);
            ProceduralArt.prepare(g);
            g.drawImage(icon, 224, 8, 24, 24, null);
            ProceduralArt.setSmoothing(true);
            ProceduralArt.prepare(g);
            g.drawImage(icon, 224, 40, 24, 24, null);
        } finally {
            g.dispose();
        }
        return image;
    }

    /**
     * {@code BackgroundRenderer} through every styled band. Top row: Storm Sky, Iron Forge and
     * The Void at one fifth scale in their {@code worlds.json} palettes (cloud banks, rain and
     * the distant flicker; skyline, girders and embers; star field and shards). Middle row: two
     * 1:1 windows under the high-contrast palettes, each through its own context, on the
     * factory girder lattice (rails, posts and braces appended into one non-zero path that
     * overlaps itself) and on the canyon mesas (three plateaus whose feet overlap, in one
     * path), where the fill rule is visible at full size. Bottom row: two 1:1 windows drawn
     * through ONE shared context — the storm cloud banks under the squall palette (five
     * ellipses and a rectangle strip appended into one non-zero path per bank: the strip
     * crossing a single ellipse must stay filled, which is where an {@code Ellipse2D} wound
     * against {@code Rectangle2D} punches holes), then the Green Fields hill tops under their
     * sky, whose gradient is the first paint after the storm's translucent puddle fill on the
     * same context (a gradient that inherited that fill's alpha would come out at a third of
     * its strength over the transparent image).
     */
    static BufferedImage worlds() {
        BufferedImage image = new BufferedImage(SCENE_W, WORLDS_H, BufferedImage.TYPE_INT_ARGB);
        WorldStyle[] styles = {WorldStyle.STORM, WorldStyle.FACTORY, WorldStyle.VOID};
        WorldPalette[] palettes = {STORM_SKY, IRON_FORGE, THE_VOID};
        for (int i = 0; i < styles.length; i++) {
            worldSlot(image, styles[i], palettes[i], i * 86, 0, 84, 128, 0.2, 0, 0);
        }
        worldSlot(image, WorldStyle.FACTORY, BLUEPRINT, 0, 128, 128, 128, 1.0, -40, -440);
        worldSlot(image, WorldStyle.CANYON, INK, 128, 128, 128, 128, 1.0, -100, -470);
        Graphics2D shared = image.createGraphics();
        try {
            ProceduralArt.prepare(shared);
            sharedWindow(shared, WorldStyle.STORM, SQUALL, 0, 256, 128, 128, 0, -440);
            sharedWindow(shared, WorldStyle.HILLS, WorldPalette.GREEN_FIELDS, 128, 256, 128,
                    128, -150, -400);
        } finally {
            shared.dispose();
        }
        return image;
    }

    /**
     * A backdrop of the given style ticked 40 times, so the rain, embers and stars are
     * mid-animation, with the flicker lit; {@code render} at alpha 0.5 draws it between two
     * ticks.
     */
    static BackgroundRenderer midAnimation(WorldStyle style) {
        BackgroundRenderer backdrop = new BackgroundRenderer();
        backdrop.setStyle(style);
        backdrop.setReduceFlashing(false);
        for (int tick = 0; tick < 40; tick++) {
            backdrop.tick(2.0, false);
        }
        backdrop.flickerNow();
        return backdrop;
    }

    /**
     * Renders one mid-animation backdrop into a {@code clipRect} slot of the image under the
     * slot's scale and a playfield offset, through a context of its own — the way the host
     * opens one per frame.
     */
    static void worldSlot(BufferedImage image, WorldStyle style, WorldPalette palette, int left,
            int top, int w, int h, double scale, double dx, double dy) {
        BackgroundRenderer backdrop = midAnimation(style);
        Graphics2D g = image.createGraphics();
        try {
            ProceduralArt.prepare(g);
            g.clipRect(left, top, w, h);
            g.translate(left, top);
            g.scale(scale, scale);
            g.translate(dx, dy);
            backdrop.render(g, 0.5, palette);
        } finally {
            g.dispose();
        }
    }

    /**
     * Renders one mid-animation backdrop at 1:1 into a window of a context the caller shares
     * between windows — the way the screen stack draws every screen and overlay of a frame
     * through one context, so the paint state one backdrop leaves behind is what the next one
     * starts from. The clip is replaced rather than intersected and the translation is undone
     * afterwards, so the context is back at the identity transform for the next window.
     */
    static void sharedWindow(Graphics2D g, WorldStyle style, WorldPalette palette, int left,
            int top, int w, int h, double dx, double dy) {
        BackgroundRenderer backdrop = midAnimation(style);
        g.setClip(new Rectangle2D.Double(left, top, w, h));
        g.translate(left + dx, top + dy);
        backdrop.render(g, 0.5, palette);
        g.translate(-(left + dx), -(top + dy));
    }
    // ==== GOLDEN SCENES END ====
}
