package io.github.michelbr84.flapforge.render;

import io.github.michelbr84.flapforge.core.MathUtil;
import io.github.michelbr84.flapforge.core.Playfield;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.util.Objects;
import java.util.Random;

/**
 * The scrolling world backdrop of a run (D18, plan section 5 cosmetic rows): sky gradient, the
 * parallax bands of the world's {@link WorldStyle} and the 42 px ground strip.
 *
 * <p>Upstream drew one 253x84 image tiled across the window and moved it by {@code GAME_SPEED}
 * (4 px per 30 Hz frame), wrapping at the strip width. Flapforge keeps the same motion —
 * {@code SCROLL_SPEED} (120 px/s = 2 px/tick, the obstacle speed) and a {@value #STRIP_WIDTH} px
 * wrap — but generates the strip: the ground band carries a repeating tuft/dirt pattern of that
 * period, so the wrap is visible exactly where upstream's was. The hill bands scroll at
 * {@value #HILL_FAR_PARALLAX} and {@value #HILL_NEAR_PARALLAX} of the ground speed.
 *
 * <p>M7 adds the four other styles (D18): canyon (layered mesas under a dust haze), factory
 * (chimneys, girders, rising embers), storm (cloud banks, rain streaks and a distant flicker)
 * and void (floating shards over a slow star field, no grass). The {@link WorldStyle#HILLS}
 * path is the M1 code, untouched, so Green Fields draws exactly what it drew before; the other
 * styles precompute their shapes once and draw them through the context transform. Their
 * decorations (rain, stars, embers, the flicker) are render-side animation driven by a seeded
 * {@link Random} of their own (D12: a frame never touches the run's streams; the seed makes a
 * screenshot reproducible) and they honour {@code settings.reduceFlashing}: with it on the
 * storm's flicker is a faint tint and the embers never reach full brightness.
 *
 * <p>The renderer is advanced one simulation tick at a time by {@link #tick(double, boolean)} and
 * keeps the previous offset so {@link #render} can interpolate with the frame alpha (E30.g).
 * A frozen tick (the run is {@code DYING} or {@code FINISHED}) leaves both offsets untouched, so
 * the ground stops the moment the bird dies, as upstream's {@code GameBackground.draw} did by
 * returning before {@code movement()}. Nothing is allocated per frame.
 */
public final class BackgroundRenderer {

    /** Wrap period of the ground pattern: the width of upstream's background strip. */
    public static final int STRIP_WIDTH = 253;
    /** Wrap period of the far hill band. */
    public static final int HILL_FAR_PERIOD = 210;
    /** Wrap period of the near hill band. */
    public static final int HILL_NEAR_PERIOD = 280;
    /** Fraction of the ground speed at which the far hills scroll. */
    public static final double HILL_FAR_PARALLAX = 0.25;
    /** Fraction of the ground speed at which the near hills scroll. */
    public static final double HILL_NEAR_PARALLAX = 0.5;
    /** Height of the darker line along the top of the ground strip. */
    public static final int GROUND_EDGE_H = 4;
    /** Rain streaks the storm keeps in the air. */
    public static final int RAIN_COUNT = 64;
    /** Stars of the void sky. */
    public static final int STAR_COUNT = 90;
    /** Embers rising through the forge. */
    public static final int EMBER_COUNT = 16;
    /** Ticks a storm flicker lights the far cloud bank. */
    public static final int FLICKER_TICKS = 4;
    /** Shortest gap between two storm flickers, in ticks. */
    public static final int FLICKER_MIN_GAP = 240;
    /** Peak alpha of the storm flicker with {@code reduceFlashing} off. */
    public static final double FLICKER_ALPHA = 0.28;
    /** Peak alpha of the storm flicker with {@code reduceFlashing} on: a tint, not a flash. */
    public static final double FLICKER_ALPHA_REDUCED = 0.07;

    private static final double FAR_HILL_Y = 452;
    private static final double FAR_HILL_W = 268;
    private static final double FAR_HILL_H = 210;
    private static final double NEAR_HILL_Y = 508;
    private static final double NEAR_HILL_W = 330;
    private static final double NEAR_HILL_H = 180;

    /* Ground decoration inside one 253 px tile: {x, width} of the dirt dashes and tuft bases. */
    private static final double[] DIRT = {12, 46, 96, 30, 148, 62, 226, 20};
    private static final double[] TUFTS = {8, 34, 61, 97, 132, 158, 191, 219, 240};

    /* Periods of the styled bands. */
    private static final int MESA_FAR_PERIOD = 300;
    private static final int MESA_NEAR_PERIOD = 360;
    private static final int HAZE_PERIOD = 420;
    private static final int SKYLINE_PERIOD = 320;
    private static final int GIRDER_PERIOD = 200;
    private static final int CLOUD_FAR_PERIOD = 300;
    private static final int CLOUD_NEAR_PERIOD = 340;
    private static final int SHARD_FAR_PERIOD = 330;
    private static final int SHARD_NEAR_PERIOD = 380;
    private static final double HAZE_PARALLAX = 0.15;
    private static final double CLOUD_FAR_PARALLAX = 0.2;
    private static final double CLOUD_NEAR_PARALLAX = 0.4;
    private static final double SHARD_FAR_PARALLAX = 0.3;
    private static final double SHARD_NEAR_PARALLAX = 0.6;
    private static final double STAR_PARALLAX = 0.05;
    private static final double RAIN_FALL_PER_TICK = 7.5;
    private static final double RAIN_DRIFT_PER_TICK = 1.6;
    private static final int RAIN_LENGTH = 14;
    private static final int RAIN_SLANT = 4;
    private static final double EMBER_RISE_PER_TICK = 0.45;
    private static final int TWINKLE_PERIOD = 96;
    private static final int BOB_PERIOD = 180;
    /* Ground decoration of the styled strips, in one 253 px tile. */
    private static final double[] CRACKS = {20, 30, 88, 18, 150, 44, 214, 26};
    private static final double[] PEBBLES = {40, 122, 170, 236};
    private static final double[] SEAMS = {0, 63, 126, 189};
    private static final double[] RIVETS = {14, 46, 77, 109, 140, 172, 203, 235};
    private static final double[] PUDDLES = {30, 60, 140, 40, 210, 34};

    private final Rectangle2D.Double rect = new Rectangle2D.Double();
    private final Ellipse2D.Double oval = new Ellipse2D.Double();
    private final Random decor = new Random(0x5EEDL);
    private final double[] rainX = new double[RAIN_COUNT];
    private final double[] rainY = new double[RAIN_COUNT];
    private final double[] rainSpeed = new double[RAIN_COUNT];
    private final double[] starX = new double[STAR_COUNT];
    private final double[] starY = new double[STAR_COUNT];
    private final int[] starPhase = new int[STAR_COUNT];
    private final int[] starSize = new int[STAR_COUNT];
    private final double[] emberX = new double[EMBER_COUNT];
    private final double[] emberY = new double[EMBER_COUNT];
    private final int[] emberPhase = new int[EMBER_COUNT];
    private final Color[] alphaCache = new Color[3 * 17];
    private final int[] alphaCacheRgb = new int[3];
    private final Color[] mixCache = new Color[3];
    private final int[] mixCacheRgb = {-1, -1, -1};
    private Path2D.Double mesaFar;
    private Path2D.Double mesaNear;
    private Path2D.Double skyline;
    private Path2D.Double girders;
    private Path2D.Double cloudFar;
    private Path2D.Double cloudNear;
    private Path2D.Double shardFar;
    private Path2D.Double shardNear;
    private WorldStyle style = WorldStyle.HILLS;
    private boolean reduceFlashing = true;
    private double distance;
    private double prevDistance;
    private double animClock;
    private double prevAnimClock;
    private int flickerTicks;
    private int nextFlickerIn;

    /** Creates a backdrop scrolled to its start position, in the hills style. */
    public BackgroundRenderer() {
        seedDecor();
        nextFlickerIn = FLICKER_MIN_GAP + decor.nextInt(FLICKER_MIN_GAP);
    }

    /**
     * Picks the parallax style (M7). The hills are the default and the M1 look.
     *
     * @param newStyle the style
     */
    public void setStyle(WorldStyle newStyle) {
        this.style = Objects.requireNonNull(newStyle, "style");
    }

    /**
     * The style in use.
     *
     * @return the style
     */
    public WorldStyle style() {
        return style;
    }

    /**
     * Turns the accessibility damping on or off ({@code settings.reduceFlashing}): the storm's
     * flicker becomes a faint tint and the embers stay dim.
     *
     * @param value {@code true} to damp bright transients
     */
    public void setReduceFlashing(boolean value) {
        this.reduceFlashing = value;
    }

    /**
     * Whether bright transients are damped.
     *
     * @return {@code true} when damping
     */
    public boolean isReduceFlashing() {
        return reduceFlashing;
    }

    /**
     * Whether the storm's distant flicker is lit on this tick (tests).
     *
     * @return {@code true} during a flicker
     */
    public boolean isFlickering() {
        return flickerTicks > 0;
    }

    /**
     * Lights the storm flicker now (tests and the smoke capture); a no-op in other styles.
     */
    public void flickerNow() {
        if (style == WorldStyle.STORM) {
            flickerTicks = FLICKER_TICKS;
        }
    }

    /**
     * Advances the backdrop by one simulation tick.
     *
     * @param scrollPerTick the world scroll of this tick in px (normally
     *     {@code SCROLL_SPEED / TICK_RATE})
     * @param frozen {@code true} while the run is dying or finished; the backdrop stops and the
     *     interpolation state is settled so the render shows no motion
     */
    public void tick(double scrollPerTick, boolean frozen) {
        prevDistance = distance;
        if (!frozen) {
            distance += scrollPerTick;
        }
        // The decorations keep moving on a frozen tick: rain falls and stars twinkle over a dead
        // bird, as the clouds keep drifting (CloudLayer), only the ground stops.
        prevAnimClock = animClock;
        animClock += 1;
        if (flickerTicks > 0) {
            flickerTicks--;
        } else if (style == WorldStyle.STORM && --nextFlickerIn <= 0) {
            flickerTicks = FLICKER_TICKS;
            nextFlickerIn = FLICKER_MIN_GAP + decor.nextInt(2 * FLICKER_MIN_GAP);
        }
    }

    /** Puts the backdrop back at its start position (a new run). */
    public void reset() {
        distance = 0;
        prevDistance = 0;
        animClock = 0;
        prevAnimClock = 0;
        flickerTicks = 0;
    }

    /**
     * Distance scrolled since the run started, in px.
     *
     * @return the distance
     */
    public double distance() {
        return distance;
    }

    /**
     * Draws sky, the style's bands and the ground.
     *
     * @param g the context in logical coordinates
     * @param alpha the interpolation factor in {@code [0, 1)}
     * @param palette the world palette
     */
    public void render(Graphics2D g, double alpha, WorldPalette palette) {
        double d = MathUtil.lerp(prevDistance, distance, alpha);

        Paint oldPaint = g.getPaint();
        g.setPaint(ProceduralArt.skyPaint(palette));
        // From the visible top rather than row 0: on tall windows (portrait phones) the rows
        // above the playfield show more sky — the acyclic gradient clamps to its top colour.
        rect.setFrame(0, Overscan.top(), Playfield.WIDTH,
                Playfield.GROUND_Y - Overscan.top());
        g.fill(rect);
        g.setPaint(oldPaint);

        switch (style) {
            case CANYON:
                renderCanyon(g, alpha, d, palette);
                break;
            case FACTORY:
                renderFactory(g, alpha, d, palette);
                break;
            case STORM:
                renderStorm(g, alpha, d, palette);
                break;
            case VOID:
                renderVoid(g, alpha, d, palette);
                break;
            case HILLS:
            default:
                renderHills(g, d, palette);
                break;
        }
    }

    // ------------------------------------------------------------------ hills (M1, unchanged)

    private void renderHills(Graphics2D g, double d, WorldPalette palette) {
        hills(g, ProceduralArt.color(palette, ProceduralArt.Tone.HILL_FAR),
                wrap(d * HILL_FAR_PARALLAX, HILL_FAR_PERIOD), HILL_FAR_PERIOD, FAR_HILL_Y,
                FAR_HILL_W, FAR_HILL_H);
        hills(g, ProceduralArt.color(palette, ProceduralArt.Tone.HILL_NEAR),
                wrap(d * HILL_NEAR_PARALLAX, HILL_NEAR_PERIOD), HILL_NEAR_PERIOD, NEAR_HILL_Y,
                NEAR_HILL_W, NEAR_HILL_H);

        ground(g, palette, wrap(d, STRIP_WIDTH));
    }

    private void hills(Graphics2D g, Color color, double offset, double period, double y,
            double w, double h) {
        // Subpixel positions, not fillOval(int, ...): the far band moves half a pixel per tick, so
        // snapping to the logical pixel grid would make it step visibly at any viewport scale.
        g.setColor(color);
        for (double x = -offset - period; x < Playfield.WIDTH + period; x += period) {
            oval.setFrame(x, y, w, h);
            g.fill(oval);
        }
    }

    private void ground(Graphics2D g, WorldPalette palette, double offset) {
        Color base = ProceduralArt.color(palette, ProceduralArt.Tone.GROUND);
        Color edge = ProceduralArt.color(palette, ProceduralArt.Tone.GROUND_EDGE);
        Color grass = ProceduralArt.color(palette, ProceduralArt.Tone.HILL_NEAR);

        g.setColor(base);
        // Down to the visible bottom rather than row 640: the rows below the playfield read as
        // plain earth (the tufts, dashes and edge band keep their fixed rows).
        rect.setFrame(0, Playfield.GROUND_Y, Playfield.WIDTH,
                Overscan.bottom() - Playfield.GROUND_Y);
        g.fill(rect);
        g.setColor(edge);
        rect.setFrame(0, Playfield.GROUND_Y, Playfield.WIDTH, GROUND_EDGE_H);
        g.fill(rect);

        // Grass tufts straddling the ground line, one set per strip: they poke above the edge
        // band into the sky, which is what makes the wrap readable while the ground scrolls.
        g.setColor(grass);
        for (double tile = -offset - STRIP_WIDTH; tile < Playfield.WIDTH;
                tile += STRIP_WIDTH) {
            for (double t : TUFTS) {
                rect.setFrame(tile + t, Playfield.GROUND_Y - 5, 7, 9);
                g.fill(rect);
            }
        }

        // Dirt dashes lower in the band, same period, so the wrap reads like upstream's strip.
        g.setColor(edge);
        for (double tile = -offset - STRIP_WIDTH; tile < Playfield.WIDTH;
                tile += STRIP_WIDTH) {
            for (int i = 0; i < DIRT.length; i += 2) {
                rect.setFrame(tile + DIRT[i], Playfield.GROUND_Y + 18, DIRT[i + 1], 5);
                g.fill(rect);
            }
        }
    }

    // ------------------------------------------------------------------ canyon

    private void renderCanyon(Graphics2D g, double alpha, double d, WorldPalette palette) {
        if (mesaFar == null) {
            mesaFar = mesas(MESA_FAR_PERIOD, 396, 60, 598);
            mesaNear = mesas(MESA_NEAR_PERIOD, 476, 44, 598);
        }
        tiled(g, ProceduralArt.color(palette, ProceduralArt.Tone.HILL_FAR), mesaFar,
                wrap(d * HILL_FAR_PARALLAX, MESA_FAR_PERIOD), MESA_FAR_PERIOD);
        tiled(g, ProceduralArt.color(palette, ProceduralArt.Tone.HILL_NEAR), mesaNear,
                wrap(d * HILL_NEAR_PARALLAX, MESA_NEAR_PERIOD), MESA_NEAR_PERIOD);
        // Dust haze: a low band of fog with three slow drifting plumes in it.
        Color haze = alpha(palette.fog(), 0.22, 0);
        g.setColor(haze);
        rect.setFrame(0, 500, Playfield.WIDTH, Playfield.GROUND_Y - 500);
        g.fill(rect);
        double offset = wrap(d * HAZE_PARALLAX, HAZE_PERIOD);
        for (double x = -offset - HAZE_PERIOD; x < Playfield.WIDTH + HAZE_PERIOD;
                x += HAZE_PERIOD) {
            oval.setFrame(x + 40, 520, 220, 60);
            g.fill(oval);
            oval.setFrame(x + 220, 545, 180, 50);
            g.fill(oval);
        }
        groundBase(g, palette);
        Color edge = ProceduralArt.color(palette, ProceduralArt.Tone.GROUND_EDGE);
        g.setColor(edge);
        double tileOffset = wrap(d, STRIP_WIDTH);
        for (double tile = -tileOffset - STRIP_WIDTH; tile < Playfield.WIDTH;
                tile += STRIP_WIDTH) {
            for (int i = 0; i < CRACKS.length; i += 2) {
                rect.setFrame(tile + CRACKS[i], Playfield.GROUND_Y + 14, CRACKS[i + 1], 2);
                g.fill(rect);
                rect.setFrame(tile + CRACKS[i] + 6, Playfield.GROUND_Y + 27, CRACKS[i + 1] * 0.6,
                        2);
                g.fill(rect);
            }
            for (double p : PEBBLES) {
                oval.setFrame(tile + p, Playfield.GROUND_Y + 6, 6, 4);
                g.fill(oval);
            }
        }
    }

    /**
     * One tile of flat-topped mesas: three plateaus of different widths and heights whose
     * slopes meet the ground line.
     */
    private static Path2D.Double mesas(int period, double topY, double relief, double baseY) {
        Path2D.Double path = new Path2D.Double();
        double[] tops = {topY + relief * 0.4, topY, topY + relief};
        double[] lefts = {0, period * 0.34, period * 0.66};
        double[] widths = {period * 0.34, period * 0.32, period * 0.34};
        for (int i = 0; i < 3; i++) {
            double l = lefts[i];
            double w = widths[i];
            double t = tops[i];
            path.moveTo(l - 12, baseY);
            path.lineTo(l + w * 0.12, t + 10);
            path.lineTo(l + w * 0.2, t);
            path.lineTo(l + w * 0.78, t);
            path.lineTo(l + w * 0.9, t + 14);
            path.lineTo(l + w + 12, baseY);
            path.closePath();
        }
        return path;
    }

    // ------------------------------------------------------------------ factory

    private void renderFactory(Graphics2D g, double alpha, double d, WorldPalette palette) {
        if (skyline == null) {
            skyline = factorySkyline(SKYLINE_PERIOD);
            girders = girderLattice(GIRDER_PERIOD, 468, 556);
        }
        Color far = mixed(WorldPalette.mix(palette.letterbox(), palette.pipe(), 0.35), 0);
        tiled(g, far, skyline, wrap(d * HILL_FAR_PARALLAX, SKYLINE_PERIOD), SKYLINE_PERIOD);
        // Embers rise behind the girders and fade in and out on a triangle wave (no trig).
        double clock = MathUtil.lerp(prevAnimClock, animClock, alpha);
        double cap = reduceFlashing ? ParticleSystem.REDUCED_PEAK_ALPHA : 0.95;
        for (int i = 0; i < EMBER_COUNT; i++) {
            double y = Playfield.GROUND_Y - wrap(emberY[i] + clock * EMBER_RISE_PER_TICK, 560);
            double glow = triangle(clock + emberPhase[i], 70) * cap;
            if (glow < 0.05) {
                continue;
            }
            g.setColor(alpha(palette.accent(), glow, 1));
            double r = 1.5 + glow * 1.5;
            oval.setFrame(emberX[i] - r, y - r, 2 * r, 2 * r);
            g.fill(oval);
        }
        Color near = ProceduralArt.color(palette, ProceduralArt.Tone.PIPE_EDGE);
        tiled(g, near, girders, wrap(d * HILL_NEAR_PARALLAX, GIRDER_PERIOD), GIRDER_PERIOD);
        groundBase(g, palette);
        // Metal plates: seams and rivets in the ground tone's shadow, a warm glow along the edge.
        Color edge = ProceduralArt.color(palette, ProceduralArt.Tone.GROUND_EDGE);
        double tileOffset = wrap(d, STRIP_WIDTH);
        g.setColor(edge);
        for (double tile = -tileOffset - STRIP_WIDTH; tile < Playfield.WIDTH;
                tile += STRIP_WIDTH) {
            for (double s : SEAMS) {
                rect.setFrame(tile + s, Playfield.GROUND_Y + GROUND_EDGE_H, 2,
                        Playfield.GROUND_HEIGHT - GROUND_EDGE_H);
                g.fill(rect);
            }
            for (double r : RIVETS) {
                oval.setFrame(tile + r, Playfield.GROUND_Y + 12, 4, 4);
                g.fill(oval);
                oval.setFrame(tile + r, Playfield.GROUND_Y + 30, 4, 4);
                g.fill(oval);
            }
        }
        g.setColor(alpha(palette.accent(), 0.35, 2));
        rect.setFrame(0, Playfield.GROUND_Y + GROUND_EDGE_H, Playfield.WIDTH, 2);
        g.fill(rect);
    }

    /** One tile of the factory skyline: blocks of three heights with two chimney stacks. */
    private static Path2D.Double factorySkyline(int period) {
        Path2D.Double path = new Path2D.Double();
        double base = Playfield.GROUND_Y;
        block(path, 0, 470, period * 0.28, base);
        block(path, period * 0.26, 430, period * 0.22, base);
        block(path, period * 0.5, 490, period * 0.3, base);
        block(path, period * 0.78, 452, period * 0.22, base);
        // Chimneys: a stack with a wider cap.
        block(path, period * 0.33, 372, 14, 432);
        block(path, period * 0.31, 366, 18, 376);
        block(path, period * 0.86, 398, 12, 454);
        block(path, period * 0.845, 392, 16, 402);
        return path;
    }

    private static void block(Path2D.Double path, double x, double top, double w, double bottom) {
        path.moveTo(x, bottom);
        path.lineTo(x, top);
        path.lineTo(x + w, top);
        path.lineTo(x + w, bottom);
        path.closePath();
    }

    /** One tile of girder lattice: two rails, posts and crossed braces, as thin filled quads. */
    private static Path2D.Double girderLattice(int period, double top, double bottom) {
        Path2D.Double path = new Path2D.Double();
        double rail = 4;
        block(path, 0, top, period, top + rail);
        block(path, 0, bottom - rail, period, bottom);
        double bay = period / 2.0;
        for (int i = 0; i < 2; i++) {
            double x = i * bay;
            block(path, x, top, 4, bottom);
            brace(path, x, top + rail, x + bay, bottom - rail);
            brace(path, x + bay, top + rail, x, bottom - rail);
        }
        return path;
    }

    private static void brace(Path2D.Double path, double x0, double y0, double x1, double y1) {
        double t = 2.2;
        path.moveTo(x0 - t, y0);
        path.lineTo(x0 + t, y0);
        path.lineTo(x1 + t, y1);
        path.lineTo(x1 - t, y1);
        path.closePath();
    }

    // ------------------------------------------------------------------ storm

    private void renderStorm(Graphics2D g, double alpha, double d, WorldPalette palette) {
        if (cloudFar == null) {
            cloudFar = cloudBank(CLOUD_FAR_PERIOD, 372, 96);
            cloudNear = cloudBank(CLOUD_NEAR_PERIOD, 438, 88);
        }
        Color farBank = mixed(WorldPalette.mix(palette.fog(), palette.skyTop(), 0.45), 1);
        Color nearBank = mixed(WorldPalette.mix(palette.fog(), palette.letterbox(), 0.4), 2);
        tiled(g, farBank, cloudFar, wrap(d * CLOUD_FAR_PARALLAX, CLOUD_FAR_PERIOD),
                CLOUD_FAR_PERIOD);
        if (flickerTicks > 0) {
            // A distant flash lights the far bank from behind: a tint with reduceFlashing on.
            double peak = reduceFlashing ? FLICKER_ALPHA_REDUCED : FLICKER_ALPHA;
            g.setColor(alpha(0xFFFFFF, peak * flickerTicks / FLICKER_TICKS, 0));
            rect.setFrame(0, 330, Playfield.WIDTH, 170);
            g.fill(rect);
        }
        tiled(g, nearBank, cloudNear, wrap(d * CLOUD_NEAR_PARALLAX, CLOUD_NEAR_PERIOD),
                CLOUD_NEAR_PERIOD);
        // Rain: short slanted streaks falling on their own clock and drifting with the scroll.
        double clock = MathUtil.lerp(prevAnimClock, animClock, alpha);
        g.setColor(alpha(palette.fog(), 0.42, 1));
        for (int i = 0; i < RAIN_COUNT; i++) {
            double y = wrap(rainY[i] + clock * RAIN_FALL_PER_TICK * rainSpeed[i], Playfield.GROUND_Y + 20)
                    - 20;
            double x = wrap(rainX[i] - clock * RAIN_DRIFT_PER_TICK - d * 0.2,
                    Playfield.WIDTH + 40) - 20;
            int ix = (int) Math.round(x);
            int iy = (int) Math.round(y);
            g.drawLine(ix, iy, ix - RAIN_SLANT, iy + RAIN_LENGTH);
        }
        groundBase(g, palette);
        // Wet rock: thin puddle highlights that catch the sky.
        g.setColor(alpha(palette.fog(), 0.3, 2));
        double tileOffset = wrap(d, STRIP_WIDTH);
        for (double tile = -tileOffset - STRIP_WIDTH; tile < Playfield.WIDTH;
                tile += STRIP_WIDTH) {
            for (int i = 0; i < PUDDLES.length; i += 2) {
                oval.setFrame(tile + PUDDLES[i], Playfield.GROUND_Y + 16, PUDDLES[i + 1], 6);
                g.fill(oval);
            }
        }
    }

    /** One tile of cloud bank: overlapping ellipses along a band, heavier at the bottom. */
    private static Path2D.Double cloudBank(int period, double top, double height) {
        Path2D.Double path = new Path2D.Double();
        double[] xs = {0, 0.18, 0.4, 0.58, 0.8};
        double[] ws = {0.42, 0.36, 0.4, 0.34, 0.4};
        double[] ys = {0.2, 0.0, 0.25, 0.05, 0.18};
        for (int i = 0; i < xs.length; i++) {
            path.append(new Ellipse2D.Double(xs[i] * period, top + ys[i] * height,
                    ws[i] * period, height * (1 - ys[i]) + 40), false);
        }
        path.append(new Rectangle2D.Double(-10, top + height * 0.7, period + 20, 60), false);
        return path;
    }

    // ------------------------------------------------------------------ void

    private void renderVoid(Graphics2D g, double alpha, double d, WorldPalette palette) {
        if (shardFar == null) {
            shardFar = shards(SHARD_FAR_PERIOD, 0);
            shardNear = shards(SHARD_NEAR_PERIOD, 1);
        }
        double clock = MathUtil.lerp(prevAnimClock, animClock, alpha);
        // Stars: a slow parallax field that twinkles on a triangle wave.
        double starOffset = wrap(d * STAR_PARALLAX, Playfield.WIDTH);
        for (int i = 0; i < STAR_COUNT; i++) {
            double twinkle = 0.35 + 0.65 * triangle(clock + starPhase[i], TWINKLE_PERIOD);
            g.setColor(alpha(WorldPalette.lighten(palette.fog(), 0.6), twinkle, 0));
            int x = (int) wrap(starX[i] - starOffset, Playfield.WIDTH);
            g.fillRect(x, (int) starY[i], starSize[i], starSize[i]);
        }
        double bob = (triangle(clock, BOB_PERIOD) - 0.5) * 12;
        g.translate(0, bob);
        tiled(g, ProceduralArt.color(palette, ProceduralArt.Tone.HILL_FAR), shardFar,
                wrap(d * SHARD_FAR_PARALLAX, SHARD_FAR_PERIOD), SHARD_FAR_PERIOD);
        g.translate(0, -2 * bob);
        tiled(g, ProceduralArt.color(palette, ProceduralArt.Tone.HILL_NEAR), shardNear,
                wrap(d * SHARD_NEAR_PARALLAX, SHARD_NEAR_PERIOD), SHARD_NEAR_PERIOD);
        g.translate(0, bob);
        groundBase(g, palette);
        // No grass in the void: a faint seam of the accent runs along the edge instead.
        g.setColor(alpha(palette.accent(), 0.32, 2));
        rect.setFrame(0, Playfield.GROUND_Y + GROUND_EDGE_H + 2, Playfield.WIDTH, 1.5);
        g.fill(rect);
    }

    /** One tile of floating shards: irregular pentagons hanging at different heights. */
    private static Path2D.Double shards(int period, int variant) {
        Path2D.Double path = new Path2D.Double();
        double[][] centres = variant == 0
                ? new double[][] {{0.12, 300}, {0.45, 380}, {0.8, 330}, {0.62, 470}}
                : new double[][] {{0.2, 440}, {0.7, 400}, {0.5, 520}};
        double size = variant == 0 ? 26 : 40;
        for (double[] c : centres) {
            double cx = c[0] * period;
            double cy = c[1];
            path.moveTo(cx, cy - size);
            path.lineTo(cx + size * 0.7, cy - size * 0.2);
            path.lineTo(cx + size * 0.45, cy + size * 0.9);
            path.lineTo(cx - size * 0.5, cy + size * 0.7);
            path.lineTo(cx - size * 0.75, cy - size * 0.3);
            path.closePath();
        }
        return path;
    }

    // ------------------------------------------------------------------ shared helpers

    private void groundBase(Graphics2D g, WorldPalette palette) {
        g.setColor(ProceduralArt.color(palette, ProceduralArt.Tone.GROUND));
        // Down to the visible bottom rather than row 640, as in ground(): the extension below
        // the playfield reads as plain earth in every style.
        rect.setFrame(0, Playfield.GROUND_Y, Playfield.WIDTH,
                Overscan.bottom() - Playfield.GROUND_Y);
        g.fill(rect);
        g.setColor(ProceduralArt.color(palette, ProceduralArt.Tone.GROUND_EDGE));
        rect.setFrame(0, Playfield.GROUND_Y, Playfield.WIDTH, GROUND_EDGE_H);
        g.fill(rect);
    }

    /** Fills one precomputed tile across the playfield, translating the context per copy. */
    private static void tiled(Graphics2D g, Color color, Path2D.Double tile, double offset,
            double period) {
        g.setColor(color);
        for (double x = -offset - period; x < Playfield.WIDTH + period; x += period) {
            g.translate(x, 0);
            g.fill(tile);
            g.translate(-x, 0);
        }
    }

    private void seedDecor() {
        for (int i = 0; i < RAIN_COUNT; i++) {
            rainX[i] = decor.nextDouble() * (Playfield.WIDTH + 40);
            rainY[i] = decor.nextDouble() * Playfield.GROUND_Y;
            rainSpeed[i] = 0.8 + decor.nextDouble() * 0.5;
        }
        for (int i = 0; i < STAR_COUNT; i++) {
            starX[i] = decor.nextDouble() * Playfield.WIDTH;
            starY[i] = decor.nextDouble() * 520;
            starPhase[i] = decor.nextInt(TWINKLE_PERIOD);
            starSize[i] = 1 + decor.nextInt(2);
        }
        for (int i = 0; i < EMBER_COUNT; i++) {
            emberX[i] = decor.nextDouble() * Playfield.WIDTH;
            emberY[i] = decor.nextDouble() * 560;
            emberPhase[i] = decor.nextInt(70);
        }
    }

    /**
     * An opaque mixed colour from a per-slot cache: the mix is an integer, so the colour object
     * is rebuilt only when the palette changes, never per frame.
     *
     * @param rgb the mixed colour
     * @param slot which of the three cache slots the caller owns
     * @return the colour
     */
    private Color mixed(int rgb, int slot) {
        if (mixCacheRgb[slot] != rgb || mixCache[slot] == null) {
            mixCacheRgb[slot] = rgb;
            mixCache[slot] = new Color(rgb);
        }
        return mixCache[slot];
    }

    /**
     * A translucent colour from a small per-slot cache, quantised to 16 alpha steps, so the
     * decorations allocate nothing once warm.
     *
     * @param rgb the colour
     * @param a the alpha in {@code [0, 1]}
     * @param slot which of the three cache slots the caller owns
     * @return the colour
     */
    private Color alpha(int rgb, double a, int slot) {
        int step = MathUtil.clamp((int) Math.round(a * 16), 0, 16);
        if (alphaCacheRgb[slot] != rgb) {
            alphaCacheRgb[slot] = rgb;
            for (int i = 0; i < 17; i++) {
                alphaCache[slot * 17 + i] = null;
            }
        }
        Color cached = alphaCache[slot * 17 + step];
        if (cached == null) {
            cached = new Color((rgb & 0xFFFFFF) | (Math.min(255, step * 16) << 24), true);
            alphaCache[slot * 17 + step] = cached;
        }
        return cached;
    }

    /**
     * A triangle wave in {@code [0, 1]} with the given period, so no trigonometry runs per frame.
     */
    private static double triangle(double t, double period) {
        double p = wrap(t, period) / period;
        return p < 0.5 ? p * 2 : 2 - p * 2;
    }

    private static double wrap(double value, double period) {
        double m = value % period;
        return m < 0 ? m + period : m;
    }
}
