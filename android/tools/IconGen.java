import io.github.michelbr84.flapforge.render.ProceduralArt;
import io.github.michelbr84.flapforge.render.WorldPalette;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.imageio.ImageIO;

/**
 * Generates the Android launcher icon resources from the desktop procedural icon (M10).
 *
 * <p>The master is {@link ProceduralArt#icon(int)} — the vector shape the desktop window and
 * {@code ./gradlew iconExport} render — so the launcher icon cannot drift from the desktop one.
 * The tool needs {@code java.desktop}, which the Android project cannot see, so it is not part
 * of any Gradle build: run it by hand from the repository root whenever
 * {@code ProceduralArt.drawIcon} or the Green Fields palette changes, then commit the output.
 *
 * <pre>
 * ./gradlew --offline classes
 * javac -d build/icongen -cp build/classes/java/main android/tools/IconGen.java
 * java -Djava.awt.headless=true -cp build/icongen:build/classes/java/main IconGen
 * </pre>
 *
 * <p>An optional argument names the {@code res} directory (default
 * {@code android/src/main/res}). Per density (mdpi 1x, hdpi 1.5x, xhdpi 2x, xxhdpi 3x,
 * xxxhdpi 4x) the tool writes, under {@code mipmap-<density>/}:
 *
 * <ul>
 *   <li>{@code ic_launcher_foreground.png} — the adaptive-icon foreground layer: a 108 dp canvas
 *       (108/162/216/324/432 px) with the tile scaled to the 66 dp safe zone and centred,
 *       transparent elsewhere, so no launcher mask crops it;</li>
 *   <li>{@code ic_launcher.png} — the legacy 48 dp icon (48/72/96/144/192 px): the full tile;</li>
 *   <li>{@code ic_launcher_round.png} — the legacy round icon: the tile cut to the inscribed
 *       disc, which is what the adaptive icon shows under a circular mask.</li>
 * </ul>
 *
 * <p>It also writes {@code values/colors.xml} with {@code ic_launcher_background}, the adaptive
 * icon's background layer: {@link WorldPalette#GREEN_FIELDS}{@code .skyTop()}, the top of the
 * sky gradient the tile is painted with. {@code mipmap-anydpi-v26/ic_launcher.xml} and
 * {@code ic_launcher_round.xml} are static and reference these resources.
 */
public final class IconGen {

    /** Density buckets, mdpi first. */
    private static final List<String> DENSITIES = List.of("mdpi", "hdpi", "xhdpi", "xxhdpi",
            "xxxhdpi");
    /** Pixels per dp of each bucket, in {@link #DENSITIES} order. */
    private static final double[] SCALES = {1, 1.5, 2, 3, 4};
    /** Side of an adaptive-icon layer (dp). */
    private static final int ADAPTIVE_DP = 108;
    /** Diameter of the adaptive icon's safe zone (dp): the tile is scaled to it. */
    private static final int SAFE_ZONE_DP = 66;
    /** Side of a legacy launcher icon (dp). */
    private static final int LEGACY_DP = 48;
    /** Output directory when no argument is given (relative to the repository root). */
    private static final String DEFAULT_RES = "android/src/main/res";

    private IconGen() {
    }

    /**
     * Writes the icon resources.
     *
     * @param args optionally the {@code res} directory
     * @throws IOException when a file cannot be written
     */
    public static void main(String[] args) throws IOException {
        Path res = Path.of(args.length > 0 ? args[0] : DEFAULT_RES);
        int background = WorldPalette.GREEN_FIELDS.skyTop();
        for (int i = 0; i < DENSITIES.size(); i++) {
            Path dir = res.resolve("mipmap-" + DENSITIES.get(i));
            Files.createDirectories(dir);
            int adaptive = px(ADAPTIVE_DP, SCALES[i]);
            write(dir.resolve("ic_launcher_foreground.png"), foreground(adaptive));
            int legacy = px(LEGACY_DP, SCALES[i]);
            write(dir.resolve("ic_launcher.png"), ProceduralArt.icon(legacy));
            write(dir.resolve("ic_launcher_round.png"), round(legacy, background));
        }
        Path colors = res.resolve("values").resolve("colors.xml");
        Files.createDirectories(colors.getParent());
        Files.writeString(colors, colorsXml(background), StandardCharsets.UTF_8);
        System.out.println("wrote " + colors + " (ic_launcher_background " + hex(background) + ")");
    }

    /** Pixels of {@code dp} at {@code scale}. */
    private static int px(int dp, double scale) {
        return (int) Math.round(dp * scale);
    }

    /**
     * The adaptive foreground layer: the tile scaled to the safe zone, centred on a transparent
     * canvas. The side is nudged by a pixel when needed so the offset stays integral.
     */
    private static BufferedImage foreground(int canvas) {
        int side = (int) Math.round(canvas * SAFE_ZONE_DP / (double) ADAPTIVE_DP);
        if ((canvas - side) % 2 != 0) {
            side++;
        }
        int offset = (canvas - side) / 2;
        BufferedImage image = new BufferedImage(canvas, canvas, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            ProceduralArt.prepare(g);
            ProceduralArt.drawIcon(g, offset, offset, side);
        } finally {
            g.dispose();
        }
        return image;
    }

    /**
     * The legacy round icon: an anti-aliased disc of the background colour, then the tile
     * composited {@code SrcIn} so the disc's soft edge cuts it (a clip would leave a jagged rim).
     */
    private static BufferedImage round(int size, int background) {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(new Color(background));
            g.fill(new Ellipse2D.Double(0, 0, size, size));
            g.setComposite(AlphaComposite.SrcIn);
            g.drawImage(ProceduralArt.icon(size), 0, 0, null);
        } finally {
            g.dispose();
        }
        return image;
    }

    /** The {@code values/colors.xml} text. */
    private static String colorsXml(int background) {
        return "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                + "<!-- Generated by android/tools/IconGen.java; do not edit by hand. -->\n"
                + "<resources>\n"
                + "    <!-- Adaptive launcher icon background layer: WorldPalette.GREEN_FIELDS"
                + ".skyTop, the top\n"
                + "         of the sky gradient ProceduralArt.drawIcon paints the icon tile"
                + " with. -->\n"
                + "    <color name=\"ic_launcher_background\">" + hex(background) + "</color>\n"
                + "</resources>\n";
    }

    /** {@code #RRGGBB} of an {@code 0xRRGGBB} colour. */
    private static String hex(int rgb) {
        return String.format("#%06X", rgb & 0xFFFFFF);
    }

    /** Writes {@code image} as PNG and reports it. */
    private static void write(Path file, BufferedImage image) throws IOException {
        if (!ImageIO.write(image, "png", file.toFile())) {
            throw new IOException("no PNG writer for " + file);
        }
        System.out.println("wrote " + file + " (" + image.getWidth() + "x" + image.getHeight()
                + ")");
    }
}
