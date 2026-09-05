import io.github.michelbr84.flapforge.render.Accessibility;
import io.github.michelbr84.flapforge.render.BackgroundRenderer;
import io.github.michelbr84.flapforge.render.Fonts;
import io.github.michelbr84.flapforge.render.ProceduralArt;
import io.github.michelbr84.flapforge.render.TextPainter;
import io.github.michelbr84.flapforge.render.WorldPalette;
import io.github.michelbr84.flapforge.render.WorldStyle;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/**
 * Renders the golden reference images the Android shim fidelity test compares against (M10).
 *
 * <p>The scenes are the game's own drawing code — {@code ProceduralArt}, {@code TextPainter},
 * {@code Fonts} — run through the desktop Java2D pipeline, which is the behaviour the
 * {@code awt.*} shims must reproduce. {@code android/src/test/java/awt/GoldenRenderTest.java}
 * draws the same scenes through the transformed classes over the shims and compares the pixels
 * against the PNGs this tool writes. The scene code between the {@code GOLDEN SCENES} markers
 * is a byte-for-byte copy in both files (the test file has {@code java.awt.*} rewritten to
 * {@code awt.*}, rule T3 of the source transform); the test asserts the two copies are
 * identical after the transform rules are applied to this file, so neither copy can drift.
 *
 * <p>The tool needs {@code java.desktop}, which the Android project cannot see, so it is not
 * part of any Gradle build. Regenerate the references from the repository root whenever a
 * scene or the drawing code it exercises changes (the desktop rendering is deterministic on a
 * given JDK; the checked-in set was rendered with OpenJDK 17):
 *
 * <pre>
 * ./gradlew --offline classes
 * javac -d build/goldenrender -cp build/classes/java/main android/tools/GoldenRender.java
 * java -Djava.awt.headless=true \
 *     -cp build/goldenrender:build/classes/java/main:build/resources/main \
 *     GoldenRender android/src/test/resources/golden
 * </pre>
 *
 * <p>The one argument names the output directory (default
 * {@code android/src/test/resources/golden}); one {@code <scene>.png} is written per scene of
 * {@link #SCENE_NAMES}. {@code build/resources/main} must be on the classpath: the text scene
 * installs the bundled OFL font exactly the way {@code AssetManager.loadFont} does.
 */
public final class GoldenRender {

    /** Output directory when no argument is given (relative to the repository root). */
    private static final String DEFAULT_OUT = "android/src/test/resources/golden";

    private GoldenRender() {
    }

    /**
     * Writes every scene as a PNG.
     *
     * @param args optionally the output directory
     * @throws Exception when the font cannot be loaded or a file cannot be written
     */
    public static void main(String[] args) throws Exception {
        Path out = Path.of(args.length > 0 ? args[0] : DEFAULT_OUT);
        Files.createDirectories(out);
        Font uiFont = loadUiFont();
        for (String scene : SCENE_NAMES) {
            BufferedImage image = renderScene(scene, uiFont);
            Path file = out.resolve(scene + ".png");
            if (!ImageIO.write(image, "png", file.toFile())) {
                throw new IOException("no PNG writer for " + file);
            }
            System.out.println("wrote " + file + " (" + image.getWidth() + "x"
                    + image.getHeight() + ", " + Files.size(file) + " bytes)");
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
